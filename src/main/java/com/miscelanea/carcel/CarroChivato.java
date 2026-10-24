package com.miscelanea.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * El chivato atado a una silla con ruedas sobre raíles.
 * - Mantén clic derecho para empujarlo; si lo sueltas, rueda hacia atrás hasta el último punto seguro
 *   (cada reja abierta por la que pasa es un punto seguro). Hace ruido y atrae a los monstruos.
 * - Las rejas cerradas le cortan el paso.
 * - Al llegar junto a un Terminal de ejecución queda enganchado: hay que mantener las Palancas de descarga
 *   hasta llenar la barra. Entonces se le electrocuta y el terminal da señal de redstone.
 * - Creativo: Shift + clic derecho lo reinicia (vivo y sin progreso). Golpearlo en creativo lo quita.
 */
public class CarroChivato extends AbstractMinecart implements GeoEntity {
    public static final int VIVO = 0, ENGANCHADO = 1, ELECTROCUTANDO = 2, MUERTO = 3;

    private static final EntityDataAccessor<Integer> ESTADO = SynchedEntityData.defineId(CarroChivato.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PROGRESO = SynchedEntityData.defineId(CarroChivato.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EMPUJADO = SynchedEntityData.defineId(CarroChivato.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation NERVIOSO = RawAnimation.begin().thenLoop("animation.carro_chivato.nervioso");
    private static final RawAnimation EMPUJE = RawAnimation.begin().thenLoop("animation.carro_chivato.empuje");
    private static final RawAnimation DESCARGA = RawAnimation.begin().thenLoop("animation.carro_chivato.electrocutado");
    private static final RawAnimation MUERTO_ANIM = RawAnimation.begin().thenLoop("animation.carro_chivato.muerto");

    private static final double VEL_EMPUJE = 0.11;
    private static final double VEL_RETROCESO = 0.05;
    private static final int DURACION_DESCARGA = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent barra = new ServerBossEvent(
            Component.translatable("bossbar." + Miscelanea.MOD_ID + ".ejecucion"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    private Vec3 checkpoint;
    private Vec3 empuje = Vec3.ZERO;
    private int empujadoHasta;
    private int timerDescarga;
    private BlockPos terminal;

    public CarroChivato(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ESTADO, VIVO);
        entityData.define(PROGRESO, 0.0F);
        entityData.define(EMPUJADO, false);
    }

    public int estado() {
        return entityData.get(ESTADO);
    }

    public float progreso() {
        return entityData.get(PROGRESO);
    }

    @Override
    public Type getMinecartType() {
        return Type.RIDEABLE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.CARRO_CHIVATO.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.CARRO_CHIVATO.get());
    }

    // ------------------------------------------------------------------ interacción

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isCreative() && player.isShiftKeyDown()) {
            if (!level().isClientSide) reiniciar();
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (estado() != VIVO) return InteractionResult.PASS;
        if (!level().isClientSide) {
            Vec3 dir = position().subtract(player.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 1.0E-4) empuje = dir.normalize();
            empujadoHasta = tickCount + 6;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void reiniciar() {
        entityData.set(ESTADO, VIVO);
        entityData.set(PROGRESO, 0.0F);
        checkpoint = position();
        terminal = null;
        barra.removeAllPlayers();
    }

    /** Lo llaman las palancas de descarga. */
    public void sumarProgreso(float cantidad) {
        if (estado() != ENGANCHADO) return;
        float p = Math.min(100.0F, progreso() + cantidad);
        entityData.set(PROGRESO, p);
        if (p >= 100.0F) {
            entityData.set(ESTADO, ELECTROCUTANDO);
            timerDescarga = 0;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player p && p.isCreative()) {
            if (!level().isClientSide) discard();
            return true;
        }
        return false; // indestructible en supervivencia
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
        // nadie lo mueve chocando con él: solo empujándolo con clic derecho
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        if (!level().isClientSide) tickServidor((ServerLevel) level());
        super.tick();
    }

    private void tickServidor(ServerLevel level) {
        if (checkpoint == null) checkpoint = position();
        int estado = estado();

        if (estado == VIVO) {
            boolean empujado = tickCount < empujadoHasta;
            entityData.set(EMPUJADO, empujado);
            if (empujado) {
                setDeltaMovement(empuje.scale(VEL_EMPUJE));
                if (tickCount % 10 == 0) {
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_STEP, SoundSource.NEUTRAL, 1.0F, 0.6F);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.NEUTRAL, 0.8F, 0.5F);
                }
                if (tickCount % 20 == 0) Ruido.alertar(level, position(), 18, this);
            } else if (position().distanceTo(checkpoint) > 0.35) {
                // Sin nadie empujando, rueda hacia atrás hasta el último punto seguro
                Vec3 atras = checkpoint.subtract(position()).multiply(1, 0, 1).normalize();
                setDeltaMovement(atras.scale(VEL_RETROCESO));
            } else {
                setDeltaMovement(Vec3.ZERO);
            }

            // Puntos seguros: rejas abiertas cercanas; enganche: terminal de ejecución cercano
            BlockPos base = blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 2, 2))) {
                BlockState st = level.getBlockState(p);
                if (st.getBlock() instanceof TerminalBlock && Vec3.atCenterOf(p).distanceTo(position()) < 2.2) {
                    entityData.set(ESTADO, ENGANCHADO);
                    terminal = p.immutable();
                    checkpoint = position();
                    setDeltaMovement(Vec3.ZERO);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
                    break;
                }
                if (st.getBlock() instanceof RejaBlock && st.getValue(RejaBlock.ABIERTA)
                        && Vec3.atCenterOf(p).multiply(1, 0, 1).distanceTo(position().multiply(1, 0, 1)) < 1.0) {
                    if (checkpoint.distanceTo(position()) > 0.5) {
                        checkpoint = position();
                        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.4F); // clic
                    }
                }
            }
            return;
        }

        setDeltaMovement(Vec3.ZERO);
        if (estado == ENGANCHADO) {
            barra.setProgress(progreso() / 100.0F);
            if (tickCount % 20 == 0) actualizarBarra(level);
            // La barra baja muy poco a poco si nadie tira de las palancas
            if (tickCount % 10 == 0 && progreso() > 0) entityData.set(PROGRESO, Math.max(0.0F, progreso() - 0.5F));
            if (tickCount % 40 == 0) level.playSound(null, getX(), getY(), getZ(), ModSounds.CHISPAZO.get(), SoundSource.NEUTRAL, 0.3F, 0.6F);
        } else if (estado == ELECTROCUTANDO) {
            timerDescarga++;
            barra.setProgress(1.0F);
            Vec3 c = position().add(0, 1.3, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0.3);
            if (timerDescarga % 5 == 0) level.playSound(null, c.x, c.y, c.z, ModSounds.DESCARGA.get(), SoundSource.NEUTRAL, 1.2F, 0.7F + random.nextFloat() * 0.3F);
            if (timerDescarga == 1) level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.6F, 1.4F);
            if (timerDescarga >= DURACION_DESCARGA) {
                entityData.set(ESTADO, MUERTO);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.3, 0.5, 0.3, 0.03);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 1.0F, 0.8F);
                if (terminal != null) TerminalBlock.activar(level, terminal);
                for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48))) {
                    sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title." + Miscelanea.MOD_ID + ".chivato")));
                    sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                }
                barra.removeAllPlayers();
            }
        }
    }

    private void actualizarBarra(ServerLevel level) {
        for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32))) {
            barra.addPlayer(sp);
        }
        for (ServerPlayer sp : new java.util.ArrayList<>(barra.getPlayers())) {
            if (sp.distanceTo(this) > 40 || sp.level() != level) barra.removePlayer(sp);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        barra.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ guardado

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Estado", estado());
        tag.putFloat("Progreso", progreso());
        if (checkpoint != null) {
            tag.putDouble("CpX", checkpoint.x);
            tag.putDouble("CpY", checkpoint.y);
            tag.putDouble("CpZ", checkpoint.z);
        }
        if (terminal != null) tag.putLong("Terminal", terminal.asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int e = tag.getInt("Estado");
        entityData.set(ESTADO, e == ELECTROCUTANDO ? ENGANCHADO : e);
        entityData.set(PROGRESO, tag.getFloat("Progreso"));
        if (tag.contains("CpX")) checkpoint = new Vec3(tag.getDouble("CpX"), tag.getDouble("CpY"), tag.getDouble("CpZ"));
        if (tag.contains("Terminal")) terminal = BlockPos.of(tag.getLong("Terminal"));
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "chivato", 4, state -> switch (estado()) {
            case ELECTROCUTANDO -> state.setAndContinue(DESCARGA);
            case MUERTO -> state.setAndContinue(MUERTO_ANIM);
            default -> state.setAndContinue(entityData.get(EMPUJADO) ? EMPUJE : NERVIOSO);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
