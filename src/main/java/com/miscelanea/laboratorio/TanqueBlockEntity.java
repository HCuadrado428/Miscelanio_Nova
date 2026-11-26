package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.efectos.EfectosRegistro;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * EL TANQUE DE PRIVACIÓN SENSORIAL. Una cabina oscura donde el sujeto no ve nada y empieza a oír cosas que no están
 * (los sonidos solo los oye él). Cada nivel añade lo suyo:
 * <ul>
 *   <li>0 suave: oscuridad y ruidos falsos de vez en cuando;</li>
 *   <li>1 intenso: además, el miedo y las alucinaciones (el efecto de fantasma);</li>
 *   <li>2 extremo: ruidos seguidos, miedo fuerte y, si no aguanta, se vuelve loco y se hace daño.</li>
 * </ul>
 * Manejo (el panel de detrás): clic derecho = arrancar y parar; agachado = cambiar el nivel (el extremo pide pulsar dos
 * veces). A los 15 segundos de empezar hay un QTE de secuencia ("distinguir lo real"): si lo aguanta, termina bien;
 * si no, sufre el castigo del nivel. Apunta la ficha (alucinaciones oídas, aguante, resultado) y da una muestra de miedo
 * si el científico lleva un tubo de ensayo.
 */
public class TanqueBlockEntity extends AparatoBlockEntity {
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "tanque"));
    /** Ticks hasta el QTE, según el nivel (a más nivel, antes). */
    private static final int[] HASTA_QTE = {300, 240, 160};

    private int nivel;
    private boolean encendido, qteHecho, resistio, terminado;
    private int tiempo, ruidos, proximoRuido;
    private long confirma = -1000;

    public TanqueBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.TANQUE_BE.get(), p, s);
    }

    public boolean encendido() { return encendido; }
    public int nivel() { return nivel; }

    @Override
    public int tipo() {
        return AparatosVictimas.DONCELLA;   // de pie y quieto (el cristal es oscuro)
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
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.tanque.ayuda"), true);
            return;
        }
        if (p.isShiftKeyDown()) cambiarNivel(sl, p);
        else if (encendido) apagar(p, true);
        else encender(sl, p);
    }

    /** Para las pruebas: fija el nivel sin la confirmación. */
    public void fijarNivel(int n) {
        nivel = Math.max(0, Math.min(2, n));
        marcar();
    }

    private void cambiarNivel(ServerLevel sl, Player p) {
        int siguiente = (nivel + 1) % (LabConfig.nivelMax() + 1);
        if (siguiente == 2 && sl.getGameTime() - confirma > 100) {
            confirma = sl.getGameTime();
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.extremo.confirmar").withStyle(ChatFormatting.RED), true);
            return;
        }
        nivel = siguiente;
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.tanque.nivel", Component.translatable("laboratorio.miscelanea.nivel." + nivel))
                .withStyle(nivel == 2 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
        sonido(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
        marcar();
    }

    public void encender(ServerLevel sl, Player p) {
        if (!ocupado()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.sin_sujeto").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (nivel > LabConfig.nivelMax()) nivel = LabConfig.nivelMax();
        encendido = true;
        terminado = false;
        qteHecho = resistio = false;
        tiempo = ruidos = 0;
        proximoRuido = 60;
        captor = p.getUUID();
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.tanque.arranca").withStyle(ChatFormatting.YELLOW), true);
        sonido(SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.5F);
        marcar();
    }

    public void apagar(@Nullable Player quien, boolean manual) {
        if (encendido && !terminado && tiempo > 0 && level instanceof ServerLevel sl) {
            terminado = true;
            cerrarFicha(sl, resistio ? "aguanto" : "paro");
        }
        encendido = false;
        if (manual && quien != null) quien.displayClientMessage(Component.translatable("laboratorio.miscelanea.tanque.para").withStyle(ChatFormatting.GRAY), true);
        marcar();
    }

    // ------------------------------------------------------------------ el experimento
    @Override
    protected void tickAparato(ServerLevel sl) {
        if (encendido && sl.getGameTime() % 10 == 0) {
            Vec3 c = centro();
            sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 1.3, c.z, 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    private static final SoundEvent[] RUIDOS = {SoundEvents.ZOMBIE_AMBIENT, SoundEvents.WARDEN_HEARTBEAT, SoundEvents.ENDERMAN_STARE,
            SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.CREEPER_PRIMED, SoundEvents.ZOMBIE_STEP, SoundEvents.PHANTOM_AMBIENT};

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (!encendido) return;
        tiempo++;
        if (v.tickCount % 20 == 0) {
            v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
            v.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, false));
            if (nivel >= 1) {
                v.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), 60, nivel - 1, false, false, true));
                v.addEffect(new MobEffectInstance(EfectosRegistro.FANTASMA.get(), 60, 0, false, false, true));
            }
        }
        // ruidos que no están (solo los oye el sujeto)
        if (--proximoRuido <= 0) {
            ruidos++;
            proximoRuido = nivel == 2 ? 30 + sl.random.nextInt(40) : nivel == 1 ? 60 + sl.random.nextInt(80) : 100 + sl.random.nextInt(140);
            if (v instanceof ServerPlayer vp) vp.playNotifySound(RUIDOS[sl.random.nextInt(RUIDOS.length)], SoundSource.AMBIENT, 0.9F, 0.7F + sl.random.nextFloat() * 0.5F);
        }
        // el QTE de distinguir lo real
        if (!qteHecho && tiempo >= HASTA_QTE[nivel]) {
            qteHecho = true;
            if (v instanceof ServerPlayer vp) {
                if (Qte.activo(vp)) Qte.cancelar(vp);
                Qte.iniciar(vp, QteTipo.SECUENCIA, QteParams.de(0.3F + 0.2F * nivel).titulo(Component.translatable("qte.miscelanea.tanque")), (pl, r) -> {
                    resistio = r.exito();
                    if (r.exito()) {
                        if (pl != null) pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.tanque.aguantas").withStyle(ChatFormatting.GREEN), true);
                        apagar(null, false);
                    } else {
                        castigo(sl, v);
                    }
                });
            } else {
                castigoEn = sl.getGameTime() + 100;
            }
        }
        if (castigoEn > 0 && sl.getGameTime() >= castigoEn) {
            castigoEn = 0;
            castigo(sl, v);
        }
    }

    private long castigoEn;

    private void castigo(ServerLevel sl, LivingEntity v) {
        if (terminado) return;
        terminado = true;
        resistio = false;
        cerrarFicha(sl, nivel == 2 ? "loco" : nivel == 1 ? "panico" : "mareo");
        encendido = false;
        v.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), 400, nivel, false, false, true));
        if (nivel >= 1) v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
        if (nivel == 2 && LabConfig.danoExtremo() > 0) Aparatos.danar(v, DANO, LabConfig.danoExtremo() / 2F, captor);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.tanque.castigo" + nivel).withStyle(ChatFormatting.DARK_PURPLE), true);
        marcar();
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        encendido = false;
    }

    private void cerrarFicha(ServerLevel sl, String resultado) {
        if (captor == null) return;
        Player cientifico = sl.getServer().getPlayerList().getPlayer(captor);
        LivingEntity v = victimaEntidad();
        if (cientifico == null) return;
        Map<String, String> lecturas = new LinkedHashMap<>();
        lecturas.put("ruidos", String.valueOf(ruidos));
        lecturas.put("aguante", (tiempo / 20) + " s");
        lecturas.put("resultado", resultado);
        if (v != null) lecturas.put("miedo", Math.round(Experimentos.miedo(v) * 100) + " %");
        Experimentos.anotar(cientifico, "tanque", v instanceof Player pl ? pl : null, nivel, lecturas);
        if (Experimentos.darMuestra(cientifico, LabRegistro.MUESTRA_MIEDO.get()))
            cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.muestra_obtenida", Component.translatable("item.miscelanea.muestra_miedo")), true);
    }

    @Override
    protected void guardar(CompoundTag t) {
        t.putInt("Nivel", nivel);
        t.putBoolean("Encendido", encendido);
    }

    @Override
    protected void cargar(CompoundTag t) {
        nivel = t.getInt("Nivel");
        encendido = t.getBoolean("Encendido");
    }
}
