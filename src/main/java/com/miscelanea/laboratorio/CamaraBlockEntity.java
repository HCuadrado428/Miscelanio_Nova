package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * LA CÁMARA DE TEMPERATURA. Una cabina de cristal con el sujeto dentro y un termostato (el bloque de detrás) con siete
 * posiciones: -3, -2, -1 (frío), 0 (apagada), +1, +2, +3 (calor). La temperatura de la cabina sube o baja despacio
 * hasta la del dial; el nivel del experimento es el valor absoluto del dial (1 suave, 2 intenso, 3 extremo).
 * <ul>
 *   <li>Manejo: clic derecho en el termostato = arrancar o parar; agachado = siguiente posición del dial (las de
 *       +-3 piden pulsar dos veces).</li>
 *   <li>Frío: escarcha en la pantalla (la del vanilla), lentitud y daño de frío; calor: sudor, debilidad, mareo y daño;
 *       en el extremo, además, se congela del todo o se prende fuego.</li>
 *   <li>El sujeto tiene un QTE (mantener la barra en la zona) cuando el dial es de +-2 o más. Si lo aguanta, el
 *       experimento termina bien; si no, se lleva un buen castigo.</li>
 *   <li>Al acabar se apunta la ficha (temperatura, aguante y resultado) y, con un tubo de ensayo, se saca una muestra de
 *       frío o de calor.</li>
 * </ul>
 */
public class CamaraBlockEntity extends AparatoBlockEntity implements ConsolaLab {
    /** Temperatura (ºC) de cada posición del dial, de -3 a +3. */
    public static final float[] TEMPERATURA = {-40F, -20F, -5F, 20F, 45F, 70F, 110F};
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "camara_temperatura"));

    private int dial;
    private boolean encendida;
    private float temp = 20F;
    private int aguante, tiempo;
    private float tempExtrema = 20F;
    private boolean qteHecho, resistio, terminado;
    private long confirma = -1000;

    public CamaraBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.CAMARA_BE.get(), p, s);
    }

    public int dial() { return dial; }
    public boolean encendida() { return encendida; }
    public float temp() { return temp; }

    @Override
    public int tipo() {
        return AparatosVictimas.DONCELLA;   // de pie y quieto, como en la doncella (el cristal es transparente)
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5);
    }

    @Override
    public boolean sentado() {
        return false;
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
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.ayuda"), true);
            return;
        }
        // agachado: siguiente posición del dial sin abrir el panel; sin agacharse, el panel se abre en el cliente
        if (p.isShiftKeyDown()) moverDial(sl, p);
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
        return dial;
    }

    @Override
    public boolean conDial() {
        return true;
    }

    @Override
    public boolean enMarcha() {
        return encendida;
    }

    @Override
    public void ponerAjuste(ServerPlayer p, int valor) {
        int max = ConsolaLab.nivelMaximo() + 1;   // nivel máximo 0..2 -> dial máximo 1..3
        dial = Math.max(-max, Math.min(max, valor));
        avisarDial(p);
        marcar();
    }

    @Override
    public void arrancarDesdeConsola(ServerPlayer p) {
        if (!encendida && level instanceof ServerLevel sl) encender(sl, p);
    }

    @Override
    public void pararDesdeConsola(ServerPlayer p) {
        if (encendida) apagar(p, true);
    }

    private void avisarDial(Player p) {
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.dial", Math.round(TEMPERATURA[dial + 3]))
                .withStyle(dial < 0 ? ChatFormatting.AQUA : dial > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        sonido(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F + dial * 0.1F);
    }

    /** Para las pruebas: fija el dial sin pasar por la confirmación. */
    public void fijarDial(int d) {
        dial = Math.max(-3, Math.min(3, d));
        marcar();
    }

    private void moverDial(ServerLevel sl, Player p) {
        int max = LabConfig.nivelMax() + 1;   // nivel máximo 0..2 -> dial máximo 1..3
        int siguiente = dial + 1;
        if (siguiente > max) siguiente = -max;
        if (Math.abs(siguiente) == 3 && sl.getGameTime() - confirma > 100) {
            confirma = sl.getGameTime();
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.extremo.confirmar").withStyle(ChatFormatting.RED), true);
            return;
        }
        dial = siguiente;
        avisarDial(p);
        marcar();
    }

    public void encender(ServerLevel sl, Player p) {
        if (!ocupado()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.sin_sujeto").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (dial == 0) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.dial0").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!terminado && tiempo > 0) {   // la vuelta anterior no se cerró: se apunta antes de empezar otra
            terminado = true;
            cerrarFicha(sl, "paro");
        }
        encendida = true;
        terminado = false;
        aguante = tiempo = 0;
        tempExtrema = temp;
        qteHecho = resistio = false;
        captor = p.getUUID();
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.arranca").withStyle(ChatFormatting.YELLOW), true);
        sonido(SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.7F);
        marcar();
    }

    public void apagar(@Nullable Player quien, boolean manual) {
        encendida = false;
        castigoEn = 0;
        if (manual && victimaEntidad() instanceof ServerPlayer vp && Qte.activo(vp)) Qte.cancelar(vp);
        if (manual && quien != null) quien.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.para").withStyle(ChatFormatting.GRAY), true);
        marcar();
    }

    // ------------------------------------------------------------------ física
    @Override
    protected void tickAparato(ServerLevel sl) {
        float objetivo = encendida ? TEMPERATURA[dial + 3] : 20F;
        float paso = encendida ? 0.5F : 0.8F;
        if (temp < objetivo) temp = Math.min(objetivo, temp + paso);
        else if (temp > objetivo) temp = Math.max(objetivo, temp - paso);
        if (Math.abs(temp - 20F) > Math.abs(tempExtrema - 20F)) tempExtrema = temp;
        Vec3 c = centro();
        if (sl.getGameTime() % 4 == 0) {
            if (temp < 0) sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.7, c.z, 3, 0.25, 0.3, 0.25, 0.01);
            else if (temp > 45) sl.sendParticles(temp > 90 ? ParticleTypes.FLAME : ParticleTypes.SMOKE, c.x, c.y + 0.3, c.z, 2, 0.2, 0.2, 0.2, 0.01);
        }
        if (sl.getGameTime() % 10 == 0 && Math.abs(temp - 20F) > 1F) marcar();
        if (!encendida && !terminado && tiempo > 0 && Math.abs(temp - 20F) < 1F) {
            terminado = true;
            cerrarFicha(sl, resistio ? "aguanto" : "paro");
        }
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (Math.abs(temp - 20F) < 3F && !encendida) return;
        tiempo++;
        boolean frio = temp < 0;
        boolean calor = temp > 40;
        int fuerza = !(frio || calor) ? 0 : fuerza(temp);
        if (fuerza == 0) return;
        aguante++;
        int elegido = Math.abs(dial);
        // el nivel suave no tiene QTE: termina bien a los 15 segundos
        if (encendida && elegido == 1 && aguante >= 300 && !terminado) {
            resistio = true;
            terminado = true;
            cerrarFicha(sl, "aguanto");
            encendida = false;
            if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.aguantas").withStyle(ChatFormatting.GREEN), true);
            marcar();
            return;
        }
        if (frio) v.setTicksFrozen(Math.min(139, v.getTicksFrozen() + 6));
        if (v.tickCount % 20 == 0) {
            if (frio) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, fuerza - 1, false, false, true));
                if (fuerza >= 2) Aparatos.danar(v, DANO, fuerza == 2 ? 1F : 2F, captor);
            } else {
                v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
                if (fuerza >= 2) v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, true));
                if (fuerza >= 2) Aparatos.danar(v, DANO, fuerza == 2 ? 1F : 2F, captor);
                if (fuerza == 3) v.setSecondsOnFire(3);
            }
        }
        // el QTE de aguantar: una vez, cuando la cabina llega a la temperatura del dial (solo de +-2 en adelante); antes saltaba
        // a medio calentar y el castigo era el del nivel de por debajo
        if (encendida && !qteHecho && elegido >= 2 && fuerza >= elegido && aguante > 40) {
            qteHecho = true;
            if (v instanceof ServerPlayer vp) {
                if (Qte.activo(vp)) Qte.cancelar(vp);
                Qte.iniciar(vp, QteTipo.BARRA_ZONA, QteParams.de(0.3F + 0.2F * elegido).titulo(Component.translatable("qte.miscelanea.camara")), (pl, r) -> {
                    if (!encendida || terminado || !esVictima(v)) return;   // la han parado o lo han sacado entretanto
                    resistio = r.exito();
                    if (r.exito()) {
                        if (pl != null) pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.camara.aguantas").withStyle(ChatFormatting.GREEN), true);
                        apagar(null, false);
                    } else {
                        castigo(sl, v, elegido, frio);
                    }
                });
            } else {
                // un mob no puede pulsar: a los 5 segundos del extremo se lleva el castigo
                castigoEn = sl.getGameTime() + 100;
            }
        }
        if (castigoEn > 0 && sl.getGameTime() >= castigoEn) {
            castigoEn = 0;
            castigo(sl, v, elegido, frio);
        }
    }

    private long castigoEn;

    /** Lo que le pasa al que no aguanta. */
    private void castigo(ServerLevel sl, LivingEntity v, int fuerza, boolean frio) {
        if (terminado || !encendida || !esVictima(v)) return;
        resistio = false;
        terminado = true;
        cerrarFicha(sl, fuerza >= 3 ? (frio ? "congelado" : "tostado") : "castigo");
        encendida = false;
        if (fuerza >= 3) {
            Aparatos.danar(v, DANO, 10F, captor);
            if (frio) v.setTicksFrozen(139);
            else v.setSecondsOnFire(6);
        } else {
            Aparatos.danar(v, DANO, 3F, captor);
        }
        v.addEffect(new MobEffectInstance(frio ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.WEAKNESS, 200, 1));
        marcar();
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        terminado = true;
        encendida = false;
        castigoEn = 0;
        if (v instanceof ServerPlayer vp && Qte.activo(vp)) Qte.cancelar(vp);
    }

    /**
     * Lo fuerte que es la temperatura que hace dentro (0 a 3), con los mismos escalones en frío que en calor: los de las
     * posiciones del dial (-5 / 45, -20 / 70, -40 / 110). Antes el frío iba un escalón por debajo: -20 contaba como suave.
     */
    static int fuerza(float t) {
        if (t <= -38F || t >= 108F) return 3;
        if (t <= -18F || t >= 68F) return 2;
        if (t < 0F || t > 40F) return 1;
        return 0;
    }

    /** Apunta la ficha en el cuaderno de quien la maneja y, con un tubo de ensayo, saca una muestra. */
    private void cerrarFicha(ServerLevel sl, String resultado) {
        if (captor == null) return;
        Player cientifico = sl.getServer().getPlayerList().getPlayer(captor);
        LivingEntity v = victimaEntidad();
        if (cientifico == null) return;
        Map<String, String> lecturas = new LinkedHashMap<>();
        lecturas.put("temperatura", Math.round(tempExtrema) + " °C");
        lecturas.put("aguante", (aguante / 20) + " s");
        lecturas.put("resultado", resultado);
        if (v != null) lecturas.put("pulso", Experimentos.pulso(v) + " ppm");
        Experimentos.anotar(cientifico, "camara", v instanceof Player pl ? pl : null, Math.max(0, Math.abs(dial) - 1), lecturas);
        var muestra = tempExtrema < 20F ? LabRegistro.MUESTRA_FRIO.get() : LabRegistro.MUESTRA_CALOR.get();
        if (Experimentos.darMuestra(cientifico, muestra))
            cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.muestra_obtenida", Component.translatable(muestra.getDescriptionId())), true);
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void guardar(CompoundTag t) {
        t.putInt("Dial", dial);
        t.putBoolean("Encendida", encendida);
        t.putFloat("Temp", temp);
    }

    @Override
    protected void cargar(CompoundTag t) {
        dial = t.getInt("Dial");
        encendida = t.getBoolean("Encendida");
        temp = t.getFloat("Temp");
    }
}
