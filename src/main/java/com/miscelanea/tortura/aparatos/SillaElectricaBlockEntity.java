package com.miscelanea.tortura.aparatos;

import com.miscelanea.armas.TemblorPacket;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Silla eléctrica. En el cuadro de mando (el bloque de al lado): rueda del ratón mirándolo (o clic derecho en la parte
 * de arriba, el dial) para el voltaje (0..{@link #MAX}, {@link #VOLTIOS} V cada paso); clic derecho abajo (la palanca)
 * para dar la descarga. La descarga dura más y hace más daño cuanto más voltaje; desde 6 deja quemaduras en la skin
 * (sobre todo en la cabeza, bajo el casco) y al máximo mata (con su propio mensaje).
 * <p>
 * La víctima (jugador) juega PULSO tras cada descarga: el electrocardiograma corre y hay que pulsar SALTAR en cada
 * latido; si pierde el pulso, se desmaya (pantalla a negro) unos segundos y las descargas le hacen más daño.
 */
public class SillaElectricaBlockEntity extends AparatoBlockEntity {
    public static final int MAX = 10, VOLTIOS = 250;
    /** Altura del cojín (bloques). */
    public static final double ASIENTO = 9 / 16.0;
    private int voltaje = 3, descarga, descargaV, palanca, desmayo;
    @Nullable private UUID quien;
    /** Cliente. */
    public float dialVis, dialVisO, palancaVis, palancaVisO;
    public int descargaCliente;

    public SillaElectricaBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.SILLA_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.SILLA;
    }

    public int voltaje() {
        return voltaje;
    }

    public int descarga() {
        return descarga;
    }

    public int descargaV() {
        return descargaV;
    }

    public int palanca() {
        return palanca;
    }

    /** Posición del cuadro (anexo). */
    public BlockPos cuadro() {
        return worldPosition.relative(frente().getCounterClockWise());
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        Direction f = frente();
        Vec3 atras = new Vec3(-f.getStepX(), 0, -f.getStepZ()).scale(0.09);
        double baja = v instanceof Player ? 0.58 : Math.min(0.58, 0.58 * v.getBbHeight() / 1.8);
        return centro().add(atras).add(0, ASIENTO - baja, 0);
    }

    @Override
    public boolean sentado() {
        return true;
    }

    @Override
    public float giroCabeza() {
        return 75F;
    }

    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!anexo) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.silla.usa_cuadro").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (ocupado() && !puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        // en coordenadas del modelo (mirando al norte; el cuadro ocupa x -16..0): la palanca está a la derecha del frente
        if (xModelo(hit.getLocation()) < -6.6) {
            voltaje = voltaje >= MAX ? 0 : voltaje + 1;
            avisarVoltaje(p);
        } else descargar(p);
    }

    /** X (píxeles) del punto en el espacio del modelo, que se diseña mirando al norte con la silla en x 0..16. */
    private double xModelo(Vec3 punto) {
        double dx = punto.x - worldPosition.getX() - 0.5, dz = punto.z - worldPosition.getZ() - 0.5;
        double mx = switch (frente()) {
            case EAST -> dz;
            case SOUTH -> -dx;
            case WEST -> -dz;
            default -> dx;
        };
        return (mx + 0.5) * 16;
    }

    public void cambiarVoltaje(Player p, int d) {
        if (ocupado() && !puedeManejar(p)) return;
        int antes = voltaje;
        voltaje = Math.max(0, Math.min(MAX, voltaje + d));
        if (voltaje != antes) avisarVoltaje(p);
    }

    private void avisarVoltaje(Player p) {
        sonido(AparatosRegistro.SILLA_DIAL.get(), 0.7F, 0.8F + voltaje * 0.06F);
        ChatFormatting c = voltaje >= MAX ? ChatFormatting.DARK_RED : voltaje >= 6 ? ChatFormatting.RED : voltaje >= 3 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
        p.displayClientMessage(Component.translatable(voltaje >= MAX ? "msg.miscelanea.silla.voltaje_max" : "msg.miscelanea.silla.voltaje",
                voltaje * VOLTIOS).withStyle(c), true);
        marcar();
    }

    private void descargar(Player p) {
        if (!(level instanceof ServerLevel sl) || palanca > 0 || descarga > 0) return;
        palanca = 26;
        sonido(AparatosRegistro.SILLA_PALANCA.get(), 1F, 1F);
        marcar();
        if (voltaje == 0) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.silla.sin_voltaje").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 casco = centro().add(0, 1.75, 0);
        LivingEntity v = victimaEntidad();
        if (v == null) {
            // de vacío: solo chispas en el casco
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, casco.x, casco.y, casco.z, 10 + voltaje * 3, 0.25, 0.15, 0.25, 0.3);
            sl.playSound(null, casco.x, casco.y, casco.z, AparatosRegistro.SILLA_DESCARGA.get(), SoundSource.BLOCKS, 0.5F + voltaje * 0.05F, 1.3F);
            return;
        }
        descarga = 16 + voltaje * 5;
        descargaV = voltaje;
        quien = p.getUUID();
        AparatosRed.alrededor(v, new AparatoEfectoPacket(v.getId(), AparatoEfectoPacket.DESCARGA, descarga | (voltaje << 16)));
        TemblorPacket.enviar(sl, v.getEyePosition(), 6 + voltaje, 0.4F + voltaje * 0.12F, Math.min(40, descarga));
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), AparatosRegistro.SILLA_DESCARGA.get(), SoundSource.BLOCKS, 0.8F + voltaje * 0.08F,
                1.15F - voltaje * 0.03F);
        if (v instanceof ServerPlayer sp && desmayo <= 0) {
            com.miscelanea.tortura.qte.Qte.cancelar(sp);
            AparatosRed.a(sp, new AparatoJuegoPacket(AparatoJuegoPacket.PULSO, worldPosition, sp.getRandom().nextLong() | 1L, voltaje / (float) MAX));
        }
        p.displayClientMessage(Component.translatable("msg.miscelanea.silla.descarga", voltaje * VOLTIOS, v.getDisplayName())
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** La víctima ha perdido el pulso en su minijuego. */
    void desmayoPulso(ServerPlayer p) {
        if (desmayo > 0) return;
        desmayo = 100;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 110, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 110, 3, false, false));
        AparatosRed.a(p, new AparatoEfectoPacket(p.getId(), AparatoEfectoPacket.DESMAYO, 90));
        p.displayClientMessage(Component.translatable("msg.miscelanea.silla.te_desmayas").withStyle(ChatFormatting.DARK_GRAY), true);
        if (captor != null && level instanceof ServerLevel sl) {
            ServerPlayer c = sl.getServer().getPlayerList().getPlayer(captor);
            if (c != null) c.displayClientMessage(Component.translatable("msg.miscelanea.silla.desmayado", p.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    @Override
    protected void tickAparato(ServerLevel sl) {
        if (palanca > 0 && --palanca == 0) marcar();
        if (!ocupado() && descarga > 0) {
            descarga = 0;
            marcar();
        }
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (desmayo > 0) desmayo--;
        if (descarga <= 0) return;
        descarga--;
        RandomSource r = v.getRandom();
        int V = descargaV;
        // chispas por todo el cuerpo y el casco; humo desde 6; llamitas desde 9
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 2 + V / 2, v.getBbWidth() * 0.5,
                v.getBbHeight() * 0.35, v.getBbWidth() * 0.5, 0.25);
        if (descarga % 3 == 0)
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getEyeY() + 0.25, v.getZ(), 3 + V, 0.22, 0.08, 0.22, 0.35);
        if (V >= 6 && descarga % 4 == 0)
            sl.sendParticles(V >= 8 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, v.getX(), v.getEyeY() + 0.2, v.getZ(), 1 + V / 4, 0.15, 0.1, 0.15, 0.01);
        if (V >= 9 && descarga % 6 == 0)
            sl.sendParticles(ParticleTypes.SMALL_FLAME, v.getX(), v.getEyeY() + 0.3, v.getZ(), 2, 0.15, 0.05, 0.15, 0.01);
        if (descarga % 20 == 10)
            sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), AparatosRegistro.SILLA_DESCARGA.get(), SoundSource.BLOCKS, 0.6F + V * 0.06F, 1.2F - V * 0.03F);
        if (descarga % 4 == 0) {
            float dano = V * 0.16F * (desmayo > 0 ? 1.5F : 1F);
            Aparatos.danar(v, AparatosRegistro.DANO_SILLA, dano, quien);
        }
        if (V >= 6 && descarga % 10 == 5) {
            Parte parte = r.nextFloat() < 0.4F ? Parte.CABEZA : Parte.V[r.nextInt(Parte.V.length)];
            Aparatos.herida(v, parte, Tipo.QUEMADURA, 1 + (V >= 8 ? 1 : 0) + (V >= MAX ? 1 : 0));
        }
        if (descarga == 0) {
            if (V >= MAX && v.isAlive()) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, v.getX(), v.getY() + 1, v.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
                Aparatos.matar(v, AparatosRegistro.DANO_SILLA, quien);
            }
            marcar();
        }
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        descarga = 0;
        desmayo = 0;
        if (v instanceof ServerPlayer sp) AparatosRed.a(sp, new AparatoJuegoPacket(AparatoJuegoPacket.PARAR, worldPosition, 0, 0F));
    }

    @Override
    protected void tickVisual() {
        dialVisO = dialVis;
        dialVis += (voltaje / (float) MAX - dialVis) * 0.25F;
        palancaVisO = palancaVis;
        float obj = palanca > 0 ? 1F : 0F;
        palancaVis += (obj - palancaVis) * (obj > palancaVis ? 0.6F : 0.15F);
        if (descargaCliente > 0) descargaCliente--;
    }

    @Override
    protected void guardar(CompoundTag t) {
        t.putInt("Voltaje", voltaje);
        t.putInt("Descarga", descarga);
        t.putInt("DescargaV", descargaV);
        t.putInt("Palanca", palanca);
        t.putInt("Desmayo", desmayo);
        if (quien != null) t.putUUID("Quien", quien);
    }

    @Override
    protected void cargar(CompoundTag t) {
        voltaje = t.contains("Voltaje") ? t.getInt("Voltaje") : 3;
        int d = t.getInt("Descarga");
        if (d > descarga) descargaCliente = d;
        descarga = d;
        descargaV = t.getInt("DescargaV");
        palanca = t.getInt("Palanca");
        desmayo = t.getInt("Desmayo");
        quien = t.hasUUID("Quien") ? t.getUUID("Quien") : null;
    }
}
