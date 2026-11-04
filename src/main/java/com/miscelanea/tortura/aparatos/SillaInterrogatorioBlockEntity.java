package com.miscelanea.tortura.aparatos;

import com.miscelanea.armas.TemblorPacket;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Silla de interrogatorio ("By the Book"): la víctima va sentada y atada; el interrogador usa el MALETÍN (rueda de
 * herramientas, {@link Herramienta}) y el monitor cardíaco de la mesita enseña las pulsaciones.
 * <ul>
 *   <li>Cada herramienta sube las pulsaciones; bajan solas poco a poco hacia las de reposo.</li>
 *   <li>Desde {@link #RIESGO} LPM puede pararse el corazón (más probable cuanto más alto) y a {@link #PARO_SEGURO} se para
 *       seguro: línea plana; el interrogador tiene {@link #PARO_MAX} ticks (8 s) para la ADRENALINA o la víctima muere
 *       (con su propio mensaje).</li>
 *   <li>La víctima (jugador) puede confesar por el chat (sin mecánica) o AGUANTAR: SALTAR empieza un MACHACAR que, si lo
 *       supera, le baja las pulsaciones. Los mobs reaccionan al azar; los aldeanos a veces "confiesan" (sueltan una
 *       esmeralda).</li>
 * </ul>
 * Medidas del modelo (mesita en x -16..0, monitor) en scratchpad/gen_tortura5.py (S_*); las usa SillaInterrogatorioRenderer.
 */
public class SillaInterrogatorioBlockEntity extends AparatoBlockEntity {
    /** Altura del asiento (bloques), como la silla eléctrica. */
    public static final double ASIENTO = 9 / 16.0;
    public static final float RIESGO = 168F, PARO_SEGURO = 200F, MAXIMO = 235F;
    public static final int PARO_MAX = 160, ENFRIAR = 22, ENFRIAR_AGUANTAR = 110;
    private static final float REPOSO_JUGADOR = 76F;

    // ---- servidor (se guarda y se sincroniza)
    private float bpm = REPOSO_JUGADOR, reposo = REPOSO_JUGADOR;
    private int paro, enfriar, aguantar, chispas, usos, ultima = -1;
    private float bpmEnviado;
    @Nullable private UUID quien;

    // ---- cliente
    public float bpmVis = REPOSO_JUGADOR, bpmVisO = REPOSO_JUGADOR;
    /** Ticks del cliente (con este reloj se apuntan los latidos). */
    public int reloj;
    private double fase;
    /** Últimos latidos (tick del cliente en que pasó el pico); circular. */
    public final float[] latidosT = new float[24];
    public int latidos;
    public int paroCli;
    /** Tick del cliente del último uso de una herramienta (y cuál). */
    public int usoT = -1000, usoHerr = -1;

    public SillaInterrogatorioBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.INTERROGATORIO_BE.get(), p, s);
        for (int i = 0; i < latidosT.length; i++) latidosT[i] = -10000F;
    }

    @Override
    public int tipo() {
        return AparatosVictimas.INTERROGATORIO;
    }

    public float bpm() {
        return bpm;
    }

    public int paro() {
        return paro;
    }

    public boolean enParo() {
        return paro > 0;
    }

    /** Posición de la mesita (anexo). */
    public BlockPos mesa() {
        return worldPosition.relative(frente().getCounterClockWise());
    }

    /** Del espacio del modelo (px, mirando al norte) al mundo. */
    public Vec3 mundo(double mx, double my, double mz) {
        Direction f = frente(), der = f.getClockWise(), atras = f.getOpposite();
        double dx = mx / 16 - 0.5, dz = mz / 16 - 0.5;
        return new Vec3(worldPosition.getX() + 0.5 + der.getStepX() * dx + atras.getStepX() * dz, worldPosition.getY() + my / 16,
                worldPosition.getZ() + 0.5 + der.getStepZ() * dx + atras.getStepZ() * dz);
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        Direction f = frente();
        Vec3 atras = new Vec3(-f.getStepX(), 0, -f.getStepZ()).scale(0.08);
        double baja = v instanceof Player ? 0.58 : Math.min(0.58, 0.58 * v.getBbHeight() / 1.8);
        return centro().add(atras).add(0, ASIENTO - baja, 0);
    }

    @Override
    public boolean sentado() {
        return true;
    }

    @Override
    public float giroCabeza() {
        return 80F;
    }

    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!ocupado()) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.vacia").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        p.displayClientMessage(Component.translatable(p.getMainHandItem().getItem() instanceof MaletinItem
                ? "msg.miscelanea.interrogatorio.usa_rueda" : "msg.miscelanea.interrogatorio.usa_maletin").withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ meter / sacar
    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        RandomSource r = v.getRandom();
        reposo = v instanceof Player ? REPOSO_JUGADOR : 66F + r.nextFloat() * 26F;
        bpm = reposo + 14F;   // nervioso
        paro = 0;
        enfriar = 0;
        aguantar = 0;
        chispas = 0;
        this.quien = quien == null ? null : quien.getUUID();
        if (v instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.dentro_ayuda",
                    Component.keybind("key.jump")).withStyle(ChatFormatting.GRAY), false);
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        paro = 0;
        chispas = 0;
        bpm = reposo = REPOSO_JUGADOR;
        if (v instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.BLINDNESS);
            Qte.cancelar(sp);
        }
    }

    // ------------------------------------------------------------------ herramientas
    /** El interrogador ha elegido una herramienta en la rueda (ya comprobado: no es la víctima y está cerca). */
    public void usarHerramienta(ServerPlayer p, Herramienta h) {
        if (!(level instanceof ServerLevel sl)) return;
        LivingEntity v = victimaEntidad();
        if (v == null || !v.isAlive()) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.vacia").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (paro > 0 && h != Herramienta.ADRENALINA) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.necesita_adrenalina").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (enfriar > 0) return;
        enfriar = ENFRIAR;
        quien = p.getUUID();
        RandomSource r = v.getRandom();
        p.swing(InteractionHand.MAIN_HAND, true);
        Vec3 boca = v.getEyePosition().add(v.getLookAngle().scale(0.25)).add(0, -0.12, 0);
        float sube = h.sube + r.nextFloat() * h.azar;
        switch (h) {
            case PINZAS -> {
                chispas = 26;
                AparatosRed.alrededor(v, new AparatoEfectoPacket(v.getId(), AparatoEfectoPacket.DESCARGA, 26 | (4 << 16)));
                TemblorPacket.enviar(sl, v.getEyePosition(), 5, 0.45F, 18);
                sl.playSound(null, v.getX(), v.getY() + 1, v.getZ(), AparatosRegistro.INTERROGATORIO_PINZAS.get(), SoundSource.BLOCKS, 1F,
                        0.9F + r.nextFloat() * 0.2F);
                Aparatos.danar(v, AparatosRegistro.DANO_INTERROGATORIO, 2.0F, quien);
                Aparatos.herida(v, r.nextBoolean() ? Parte.TORSO : (r.nextBoolean() ? Parte.BRAZO_DER : Parte.BRAZO_IZQ), Tipo.QUEMADURA, 1);
            }
            case LLAVE -> {
                Parte parte = new Parte[]{Parte.PIERNA_DER, Parte.PIERNA_IZQ, Parte.BRAZO_DER, Parte.BRAZO_IZQ}[r.nextInt(4)];
                sl.playSound(null, v.getX(), v.getY() + 0.6, v.getZ(), AparatosRegistro.INTERROGATORIO_LLAVE.get(), SoundSource.BLOCKS, 1.1F,
                        0.85F + r.nextFloat() * 0.25F);
                TemblorPacket.enviar(sl, v.position(), 4, 0.35F, 8);
                Vec3 g = parte.esPierna() ? v.position().add(0, 0.55, 0).add(v.getLookAngle().scale(0.35)) : v.position().add(0, 0.95, 0);
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, g.x, g.y, g.z, 3, 0.15, 0.1, 0.15, 0.1);
                sl.sendParticles(ParticleTypes.CRIT, g.x, g.y, g.z, 8, 0.15, 0.1, 0.15, 0.25);
                Aparatos.danar(v, AparatosRegistro.DANO_INTERROGATORIO, 3.0F, quien);
                Aparatos.herida(v, parte, Tipo.MORATON, 2);
            }
            case ALICATES -> {
                sl.playSound(null, boca.x, boca.y, boca.z, AparatosRegistro.INTERROGATORIO_DIENTE.get(), SoundSource.BLOCKS, 1F, 0.9F + r.nextFloat() * 0.2F);
                if (v instanceof Player || v instanceof AbstractVillager || v instanceof AbstractIllager || v instanceof Witch)
                    sl.playSound(null, boca.x, boca.y, boca.z, AparatosRegistro.INTERROGATORIO_GRITO.get(), SoundSource.PLAYERS, 1.2F,
                            v instanceof Player ? 0.95F + r.nextFloat() * 0.1F : 1.05F + r.nextFloat() * 0.15F);
                sl.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.04F, 0.03F), 0.8F), boca.x, boca.y, boca.z, 6, 0.05, 0.05, 0.05, 0.0);
                ItemEntity diente = new ItemEntity(sl, boca.x, boca.y, boca.z, new ItemStack(AparatosRegistro.DIENTE.get()));
                Vec3 m = v.getLookAngle().scale(0.12);
                diente.setDeltaMovement(m.x + (r.nextDouble() - 0.5) * 0.05, 0.18, m.z + (r.nextDouble() - 0.5) * 0.05);
                diente.setPickUpDelay(20);
                sl.addFreshEntity(diente);
                Aparatos.danar(v, AparatosRegistro.DANO_INTERROGATORIO, 2.0F, quien);
                Aparatos.herida(v, Parte.CABEZA, Tipo.CORTE, 1);
            }
            case ADRENALINA -> {
                Vec3 pecho = v.position().add(0, v.getBbHeight() * 0.62, 0).add(v.getLookAngle().scale(0.2));
                sl.playSound(null, pecho.x, pecho.y, pecho.z, AparatosRegistro.INTERROGATORIO_JERINGA.get(), SoundSource.BLOCKS, 1F, 1F);
                if (paro > 0) {
                    reanimar(sl, v, p);
                    sube = 0F;
                } else {
                    Aparatos.danar(v, AparatosRegistro.DANO_INTERROGATORIO, 1.0F, quien);
                    Aparatos.herida(v, Parte.TORSO, Tipo.RASGUNO, 1);
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false));
                }
            }
        }
        if (v instanceof ServerPlayer sp) AparatosRed.a(sp, new InterrogaEfectoPacket(sp.getId(), InterrogaEfectoPacket.GOLPE, h.ordinal()));
        if (v instanceof Mob) sube = reaccionMob(sl, v, p, sube, r);
        bpm = Math.min(MAXIMO, bpm + sube);
        usos++;
        ultima = h.ordinal();
        if (v.isAlive() && paro == 0 && bpm >= PARO_SEGURO) pararCorazon(sl, v);
        else if (bpm >= RIESGO && paro == 0)
            p.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.cuidado", Math.round(bpm)).withStyle(ChatFormatting.GOLD), true);
        sincronizar();
    }

    /** Los mobs reaccionan al azar: aguantan, se ponen histéricos o (los aldeanos) confiesan. Devuelve lo que sube. */
    private float reaccionMob(ServerLevel sl, LivingEntity v, ServerPlayer p, float sube, RandomSource r) {
        if (v instanceof AbstractVillager && r.nextFloat() < 0.2F) {
            ItemEntity e = new ItemEntity(sl, v.getX(), v.getEyeY() - 0.3, v.getZ(), new ItemStack(Items.EMERALD));
            e.setDeltaMovement((r.nextDouble() - 0.5) * 0.1, 0.25, (r.nextDouble() - 0.5) * 0.1);
            sl.addFreshEntity(e);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getEyeY() + 0.3, v.getZ(), 10, 0.3, 0.25, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.ENCHANT, v.getX(), v.getEyeY() + 0.2, v.getZ(), 30, 0.4, 0.3, 0.4, 0.6);
            sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), net.minecraft.sounds.SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1F, 0.8F);
            p.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.confiesa_" + r.nextInt(6), v.getDisplayName())
                    .withStyle(ChatFormatting.GREEN), false);
            return sube * 0.4F;
        }
        float k = r.nextFloat();
        if (k < 0.25F) {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getEyeY() + 0.3, v.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            return sube * 0.55F;   // aguanta
        }
        if (k < 0.45F) {
            sl.sendParticles(ParticleTypes.SPLASH, v.getX(), v.getEyeY(), v.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
            return sube * 1.35F;   // histérico (sudores)
        }
        return sube;
    }

    private void pararCorazon(ServerLevel sl, LivingEntity v) {
        paro = PARO_MAX;
        bpm = 0F;
        chispas = 0;
        if (v instanceof ServerPlayer sp) {
            Qte.cancelar(sp);
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, PARO_MAX + 30, 0, false, false));
            sp.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.paro_tu").withStyle(ChatFormatting.DARK_RED), true);
        }
        if (v instanceof Mob m) m.getNavigation().stop();
        ServerPlayer c = interrogador(sl);
        if (c != null)
            c.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.paro", v.getDisplayName(), PARO_MAX / 20)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        sincronizar();
    }

    private void reanimar(ServerLevel sl, LivingEntity v, ServerPlayer p) {
        paro = 0;
        bpm = 150F + v.getRandom().nextFloat() * 12F;
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), AparatosRegistro.INTERROGATORIO_JADEO.get(), v instanceof Player ? SoundSource.PLAYERS
                : SoundSource.NEUTRAL, 1.3F, v instanceof Player ? 1F : 1.15F);
        TemblorPacket.enviar(sl, v.getEyePosition(), 4, 0.6F, 14);
        if (v instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.BLINDNESS);
            AparatosRed.a(sp, new InterrogaEfectoPacket(sp.getId(), InterrogaEfectoPacket.DESPERTAR, 30));
            sp.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.reanimado_tu").withStyle(ChatFormatting.GOLD), true);
        }
        p.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.reanimado", v.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
    }

    @Nullable
    private ServerPlayer interrogador(ServerLevel sl) {
        UUID u = quien != null ? quien : captor;
        return u == null ? null : sl.getServer().getPlayerList().getPlayer(u);
    }

    // ------------------------------------------------------------------ la víctima aguanta
    /** La víctima (jugador) ha pulsado SALTAR: intenta calmarse (MACHACAR). */
    void aguantar(ServerPlayer v) {
        if (!esVictima(v) || paro > 0 || aguantar > 0 || Qte.activo(v)) return;
        aguantar = ENFRIAR_AGUANTAR;
        float dif = Mth.clamp(0.25F + (bpm - reposo) / 220F, 0.2F, 0.8F);
        Qte.iniciar(v, QteTipo.MACHACAR, QteParams.de(dif).titulo(Component.translatable("qte.miscelanea.titulo.interrogatorio_aguantar"))
                .ancla(v.position(), 4), (pl, r) -> {
            if (pl == null || isRemoved() || !esVictima(pl) || !pl.isAlive()) return;
            if (r.exito()) {
                bpm = Math.max(reposo, bpm - (r == QteResultado.PERFECTO ? 38F : 28F));
                pl.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.aguantas").withStyle(ChatFormatting.AQUA), true);
                marcar();
            } else if (r == QteResultado.FALLO) {
                bpm = Math.min(MAXIMO, bpm + 6F);
                pl.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.no_aguantas").withStyle(ChatFormatting.RED), true);
                marcar();
            }
        });
    }

    /** Cinemática de muerte (Muertes): el corazón sigue parado (monitor plano) sin volver a contar ni cegar. */
    public void paroPorMuerte(boolean inicio) {
        if (paro < 2) paro = 2;
        if (inicio) sincronizar();
    }

    // ------------------------------------------------------------------ tick
    @Override
    protected void tickAparato(ServerLevel sl) {
        if (enfriar > 0) enfriar--;
        if (aguantar > 0) aguantar--;
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        RandomSource r = v.getRandom();
        if (chispas > 0) {
            chispas--;
            if (chispas % 2 == 0)
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 3, v.getBbWidth() * 0.4,
                        v.getBbHeight() * 0.25, v.getBbWidth() * 0.4, 0.2);
        }
        if (paro > 0) {
            paro--;
            if (paro % 20 == 0 && v instanceof ServerPlayer sp && !sp.hasEffect(MobEffects.BLINDNESS))
                sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, paro + 30, 0, false, false));
            if (paro % 40 == 20) {
                ServerPlayer c = interrogador(sl);
                if (c != null)
                    c.displayClientMessage(Component.translatable("msg.miscelanea.interrogatorio.paro", v.getDisplayName(), Math.max(1, paro / 20))
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
            if (paro == 0) {
                // nadie le puso la adrenalina a tiempo
                Aparatos.matar(v, AparatosRegistro.DANO_PARO, quien != null ? quien : captor);
                if (!v.isAlive() || com.miscelanea.tortura.muertes.Muertes.en(v)) return;
                bpm = 40F;   // (por si algo le ha salvado: tótem...)
                sincronizar();
            }
            return;
        }
        // las pulsaciones vuelven poco a poco a las de reposo
        if (bpm > reposo) bpm = Math.max(reposo, bpm - Math.max(0.03F, (bpm - reposo) * 0.0055F));
        else bpm = Math.min(reposo, bpm + 0.05F);
        // con el corazón desbocado puede pararse
        if (v.tickCount % 20 == 0 && bpm >= RIESGO && r.nextFloat() < (bpm - RIESGO) / (PARO_SEGURO - RIESGO) * 0.22F) {
            pararCorazon(sl, v);
            return;
        }
        // un mob se revuelve o se calma de vez en cuando
        if (v instanceof Mob && v.tickCount % 50 == 0 && r.nextFloat() < 0.3F) bpm = Mth.clamp(bpm + (r.nextFloat() - 0.4F) * 8F, reposo, MAXIMO);
        if (Math.abs(bpm - bpmEnviado) > 1.5F && v.tickCount % 10 == 0) marcar();
    }

    @Override
    protected void sincronizar() {
        bpmEnviado = bpm;
        super.sincronizar();
    }

    // ------------------------------------------------------------------ cliente
    @Override
    protected void tickVisual() {
        reloj++;
        bpmVisO = bpmVis;
        // (en la cinemática de muerte el monitor se queda plano)
        if (paroCli > 0 && !(victimaId >= 0 && com.miscelanea.tortura.muertes.Muertes.enId(victimaId))) paroCli--;
        float obj = ocupado() ? bpm : 0F;
        bpmVis += (obj - bpmVis) * 0.12F;
        if (!ocupado() || paroCli > 0 || bpm <= 1F) {
            fase = 0.0;
            if (ocupado() && paroCli > 0 && level != null && reloj % 20 == 0) {
                Vec3 m = monitor();
                level.playLocalSound(m.x, m.y, m.z, AparatosRegistro.MONITOR_PLANO.get(), SoundSource.BLOCKS, 0.55F, 1F, false);
            }
            return;
        }
        fase += bpmVis / 60.0 / 20.0;
        if (fase >= 1.0) {
            fase -= 1.0;
            latidosT[latidos % latidosT.length] = reloj - (float) (fase * 20.0 * 60.0 / Math.max(1F, bpmVis));
            latidos++;
            if (level != null) {
                Vec3 m = monitor();
                level.playLocalSound(m.x, m.y, m.z, AparatosRegistro.MONITOR_PITIDO.get(), SoundSource.BLOCKS, 0.35F,
                        bpmVis >= RIESGO ? 1.12F : 1F, false);
            }
        }
    }

    /** Centro de la pantalla del monitor (mundo). Coordenadas = gen_tortura5.py (S_MON_*). */
    public Vec3 monitor() {
        return mundo(MON_X, MON_Y, MON_Z - 0.6);
    }

    /** Pantalla del monitor en px del modelo: centro (x, y), cara de delante (z), medio ancho y medio alto. */
    public static final float MON_X = -7.5F, MON_Y = 18.25F, MON_Z = 6.9F, MON_MX = 3.9F, MON_MY = 2.45F;

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5);
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void guardar(CompoundTag t) {
        t.putFloat("Bpm", bpm);
        t.putFloat("Reposo", reposo);
        t.putInt("Paro", paro);
        t.putInt("Enfriar", enfriar);
        t.putInt("Aguantar", aguantar);
        t.putInt("Usos", usos);
        t.putInt("Ultima", ultima);
        if (quien != null) t.putUUID("Quien", quien);
    }

    @Override
    protected void cargar(CompoundTag t) {
        bpm = t.contains("Bpm") ? t.getFloat("Bpm") : REPOSO_JUGADOR;
        reposo = t.contains("Reposo") ? t.getFloat("Reposo") : REPOSO_JUGADOR;
        paro = t.getInt("Paro");
        enfriar = t.getInt("Enfriar");
        aguantar = t.getInt("Aguantar");
        int u = t.getInt("Usos");
        ultima = t.getInt("Ultima");
        quien = t.hasUUID("Quien") ? t.getUUID("Quien") : null;
        if (level != null && level.isClientSide) {
            if (u != usos && reloj > 0) {
                usoT = reloj;
                usoHerr = ultima;
            }
            paroCli = paro;
        }
        usos = u;
    }
}
