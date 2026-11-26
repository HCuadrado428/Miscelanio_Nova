package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LA CENTRÍFUGA. El brazo con la silla gira alrededor del eje y el sujeto va montado en la silla.
 * <ul>
 *   <li>Manejo (el puesto de mando, el bloque de detrás): clic derecho = arrancar o parar; agachado y clic con la
 *       mano vacía de un científico con el sujeto fuera = cambiar el nivel; con sujeto dentro, agachado = sacarlo
 *       (como todos los aparatos). El nivel extremo pide pulsar dos veces seguidas (confirmación).</li>
 *   <li>Niveles: 0 suave (20 vueltas por minuto: mareo y risas), 1 intenso (45: mareo, vista a rachas y desmayo si no
 *       aguanta), 2 extremo (80: sale disparado y se hace daño).</li>
 *   <li>El sujeto (jugador) tiene un QTE de alternar para aguantar cuando llega a toda velocidad. Si lo aguanta,
 *       termina sin más (y la nota es mejor); si no, sufre el castigo del nivel.</li>
 *   <li>Al acabar se apunta la ficha en el cuaderno del científico (g máximas, tiempo aguantado y qué pasó) y, si
 *       lleva un tubo de ensayo, se llena con una muestra de mareo.</li>
 * </ul>
 */
public class CentrifugaBlockEntity extends AparatoBlockEntity implements ConsolaLab {
    /** Radio (en bloques) al que va la silla. */
    public static final double RADIO = 1.6;
    /** Grados por tick de cada nivel: 20, 45 y 80 vueltas por minuto. */
    public static final float[] VELOCIDAD = {6F, 13.5F, 24F};
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "centrifuga"));

    private int nivel;
    private boolean girando;
    private float angulo, velocidad;
    private int tiempo, aguante, qteTick;
    private float gMax;
    private boolean qteHecho, resistio, terminado;
    private long confirmaExtremo = -1000;
    /** Cliente: ángulo del tick anterior (para suavizar al dibujar). */
    public float anguloAnt;

    public CentrifugaBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.CENTRIFUGA_BE.get(), p, s);
    }

    // ------------------------------------------------------------------ geometría
    public float angulo() { return angulo; }
    public float velocidad() { return velocidad; }
    public boolean girando() { return girando; }
    public int nivel() { return nivel; }

    /** Dónde está la silla (del sujeto) con el brazo a ese ángulo (grados; el brazo gira como Axis.YP). */
    private Vec3 silla(double ang) {
        double a = Math.toRadians(ang);
        return new Vec3(worldPosition.getX() + 0.5 + RADIO * Math.cos(a), worldPosition.getY() + 0.42, worldPosition.getZ() + 0.5 - RADIO * Math.sin(a));
    }

    @Override
    public int tipo() {
        return AparatosVictimas.SILLA;
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return silla(angulo);
    }

    @Nullable
    @Override
    public Vec3 sitioMovil(LivingEntity v) {
        return silla(angulo);
    }

    @Override
    public boolean sentado() {
        return true;
    }

    /** Mirando hacia donde va (tangente), como si fuera en un tiovivo. */
    @Override
    public float yawVictima() {
        return (float) (-angulo + 180);
    }

    /** g (aceleración centrífuga en gravedades) a esta velocidad angular (grados por tick). */
    public static double g(float gradosPorTick) {
        double w = Math.toRadians(gradosPorTick) * 20;
        return w * w * RADIO / 9.81;
    }

    // ------------------------------------------------------------------ manejo
    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!LabConfig.estaciones()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.apagada").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!anexo) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.ayuda"), true);
            return;
        }
        // agachado: siguiente nivel sin abrir el panel; sin agacharse, el panel se abre en el cliente
        if (p.isShiftKeyDown()) cambiarNivel(sl, p);
    }

    // ------------------------------------------------------------------ consola (panel) y mensajes
    @Override
    protected boolean agachadoSaca(boolean anexo) {
        return !anexo;   // en la consola, agachado cambia el nivel; en la cabina, saca al sujeto
    }

    @Override
    protected String claveMensajes() {
        return "lab";
    }

    @Override
    public int ajuste() {
        return nivel;
    }

    @Override
    public boolean enMarcha() {
        return girando;
    }

    @Override
    public void ponerAjuste(ServerPlayer p, int valor) {
        nivel = Math.max(0, Math.min(ConsolaLab.nivelMaximo(), valor));
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.nivel_elegido", Component.translatable("laboratorio.miscelanea.nivel." + nivel),
                Math.round(VELOCIDAD[nivel] * 20 / 6F)).withStyle(nivel == 2 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
        sonido(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
        marcar();
    }

    @Override
    public void arrancarDesdeConsola(ServerPlayer p) {
        if (!girando && level instanceof ServerLevel sl) arrancar(sl, p);
    }

    @Override
    public void pararDesdeConsola(ServerPlayer p) {
        if (girando && level instanceof ServerLevel sl) parar(sl, p, true);
    }

    /** Para las pruebas: fija el nivel sin pasar por la confirmación. */
    public void fijarNivel(int n) {
        nivel = Math.max(0, Math.min(2, n));
        marcar();
    }

    private void cambiarNivel(ServerLevel sl, Player p) {
        int max = LabConfig.nivelMax();
        int siguiente = (nivel + 1) % (max + 1);
        if (siguiente == 2 && sl.getGameTime() - confirmaExtremo > 100) {
            confirmaExtremo = sl.getGameTime();
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.extremo.confirmar").withStyle(ChatFormatting.RED), true);
            return;
        }
        nivel = siguiente;
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.nivel_elegido", Component.translatable("laboratorio.miscelanea.nivel." + nivel),
                Math.round(VELOCIDAD[nivel] * 20 / 6F)).withStyle(nivel == 2 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
        sonido(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
        marcar();
    }

    private boolean espacioLibre(ServerLevel sl) {
        Direction an = frente().getOpposite();
        BlockPos anexo = worldPosition.relative(an);
        for (BlockPos b : BlockPos.betweenClosed(worldPosition.offset(-2, 1, -2), worldPosition.offset(2, 2, 2))) {
            if (b.equals(anexo) || b.equals(anexo.above())) continue;
            if (!sl.getBlockState(b).getCollisionShape(sl, b).isEmpty()) return false;
        }
        return true;
    }

    public void arrancar(ServerLevel sl, Player p) {
        if (!ocupado()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.sin_sujeto").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (nivel > LabConfig.nivelMax()) nivel = LabConfig.nivelMax();
        if (!espacioLibre(sl)) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.espacio").withStyle(ChatFormatting.RED), true);
            return;
        }
        girando = true;
        terminado = false;
        tiempo = aguante = qteTick = 0;
        gMax = 0;
        qteHecho = resistio = false;
        captor = p.getUUID();
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.arranca").withStyle(ChatFormatting.YELLOW), true);
        sonido(SoundEvents.PISTON_EXTEND, 1.0F, 0.6F);
        marcar();
    }

    /** Para el brazo (frena solo). {@code manual}: lo ha parado el científico (si no, ha terminado el experimento). */
    public void parar(ServerLevel sl, @Nullable Player quien, boolean manual) {
        girando = false;
        if (manual && quien != null) quien.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.para").withStyle(ChatFormatting.GRAY), true);
        marcar();
    }

    // ------------------------------------------------------------------ física
    @Override
    protected void tickAparato(ServerLevel sl) {
        float objetivo = girando ? VELOCIDAD[Math.min(nivel, 2)] : 0F;
        float cambio = girando ? 0.25F : 0.5F;
        if (velocidad < objetivo) velocidad = Math.min(objetivo, velocidad + cambio);
        else if (velocidad > objetivo) velocidad = Math.max(objetivo, velocidad - cambio);
        angulo = (angulo + velocidad) % 360F;
        if (velocidad > 0.01F && sl.getGameTime() % 8 == 0) marcar();
        if (!girando && velocidad == 0F && !terminado && tiempo > 0) {
            terminado = true;
            cerrarFicha(sl, resistio ? "aguanto" : "paro");
        }
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (!girando) return;
        tiempo++;
        gMax = Math.max(gMax, (float) g(velocidad));
        if (velocidad < VELOCIDAD[Math.min(nivel, 2)] - 0.01F) return;   // aún acelerando
        aguante++;
        // efectos del giro
        if (v.tickCount % 20 == 0) {
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
            if (nivel >= 1 && (v.tickCount / 20) % 3 == 0) v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false, false));
        }
        // el QTE de aguantar, una sola vez cuando llega a toda velocidad
        if (!qteHecho && aguante > 20) {
            qteHecho = true;
            if (v instanceof ServerPlayer vp) {
                if (Qte.activo(vp)) Qte.cancelar(vp);
                float dif = 0.35F + 0.2F * nivel;
                Qte.iniciar(vp, QteTipo.ALTERNAR, QteParams.de(dif).titulo(Component.translatable("qte.miscelanea.centrifuga")), (pl, r) -> {
                    resistio = r.exito();
                    if (r.exito()) {
                        if (pl != null) pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.aguantas").withStyle(ChatFormatting.GREEN), true);
                        if (level instanceof ServerLevel s2) parar(s2, null, false);
                    } else {
                        fallo(sl, v);
                    }
                });
            } else {
                // un mob no puede pulsar: aguanta un rato según el nivel y luego le pasa lo del nivel
                qteTick = 60 + 40 * (2 - nivel);
            }
        }
        if (qteTick > 0 && --qteTick == 0) fallo(sl, v);
    }

    /** Lo que le pasa al sujeto que no aguanta, según el nivel. */
    private void fallo(ServerLevel sl, LivingEntity v) {
        if (terminado || !girando) return;
        resistio = false;
        terminado = true;
        cerrarFicha(sl, nivel == 0 ? "mareo" : nivel == 1 ? "desmayo" : "disparado");
        girando = false;
        if (nivel == 0) {
            // suave: se marea y se ríe
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
            if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.mareo").withStyle(ChatFormatting.YELLOW), true);
        } else if (nivel == 1) {
            // intenso: se desmaya un rato
            v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.centrifuga.desmayo").withStyle(ChatFormatting.DARK_PURPLE), true);
        } else {
            // extremo: sale disparado por la tangente
            Vec3 pos = v.position();
            double a = Math.toRadians(angulo);
            Vec3 tangente = new Vec3(-Math.sin(a), 0, -Math.cos(a)).normalize();
            java.util.UUID quien = captor;   // sacar() lo borra, y el golpe es suyo (para el mensaje de muerte)
            sacar(true);
            v.setDeltaMovement(tangente.x * 1.8, 0.7, tangente.z * 1.8);
            v.hurtMarked = true;
            if (LabConfig.danoExtremo() > 0) Aparatos.danar(v, DANO, LabConfig.danoExtremo(), quien);
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        marcar();
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        girando = false;
    }

    /** Apunta la ficha del experimento en el cuaderno de quien lo ha hecho y le da una muestra de mareo. */
    private void cerrarFicha(ServerLevel sl, String resultado) {
        if (captor == null) return;
        Player cientifico = sl.getServer().getPlayerList().getPlayer(captor);
        LivingEntity v = victimaEntidad();
        if (cientifico == null) return;
        Map<String, String> lecturas = new LinkedHashMap<>();
        lecturas.put("g", String.format(java.util.Locale.ROOT, "%.1f g", gMax));
        lecturas.put("aguante", (aguante / 20) + " s");
        lecturas.put("resultado", resultado);
        if (v != null) lecturas.put("pulso", Experimentos.pulso(v) + " ppm");
        Experimentos.anotar(cientifico, "centrifuga", v instanceof Player pl ? pl : null, nivel, lecturas);
        if (Experimentos.darMuestra(cientifico, LabRegistro.MUESTRA_MAREO.get()))
            cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.muestra_obtenida", Component.translatable("item.miscelanea.muestra_mareo")), true);
    }

    // ------------------------------------------------------------------ guardar / cliente
    @Override
    protected void guardar(CompoundTag t) {
        t.putInt("Nivel", nivel);
        t.putBoolean("Girando", girando);
        t.putFloat("Angulo", angulo);
        t.putFloat("Velocidad", velocidad);
    }

    @Override
    protected void cargar(CompoundTag t) {
        nivel = t.getInt("Nivel");
        girando = t.getBoolean("Girando");
        angulo = t.getFloat("Angulo");
        velocidad = t.getFloat("Velocidad");
    }

    /** Cliente: el brazo sigue girando entre una sincronización y la siguiente. */
    @Override
    protected void tickVisual() {
        anguloAnt = angulo;
        angulo = (angulo + velocidad) % 360F;
        if (girando && velocidad < VELOCIDAD[Math.min(nivel, 2)]) velocidad = Math.min(VELOCIDAD[Math.min(nivel, 2)], velocidad + 0.25F);
        else if (!girando && velocidad > 0) velocidad = Math.max(0, velocidad - 0.5F);
    }
}
