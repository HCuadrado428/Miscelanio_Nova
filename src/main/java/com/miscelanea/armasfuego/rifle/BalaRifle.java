package com.miscelanea.armasfuego.rifle;

import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Bala del rifle: proyectil de verdad (6 bloques por tick, cae un poco con la distancia salvo conteniendo la
 * respiración). Más daño en la cabeza; la mitad del daño se salta la armadura. Con Atravesar pasa por varios mobs y por
 * bloques blandos (hojas, cristal, lana). La trazadora deja estela y hace brillar a lo que da; la de cuerda tiende una
 * tirolina entre el tirador y el bloque donde da.
 */
public class BalaRifle extends Projectile {
    private static final EntityDataAccessor<Byte> TIPO = SynchedEntityData.defineId(BalaRifle.class, EntityDataSerializers.BYTE);
    private static final DustParticleOptions ESTELA = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.15F), 0.9F);

    private float dano = Rifle.DANO;
    private int atraviesa;
    private boolean blandos, recto, silencio;
    private int tirolinero;
    @Nullable
    private Vec3 inicio;
    @Nullable
    private BlockPos anclaInicio;
    private int vida;
    private final Set<Integer> golpeados = new HashSet<>();
    private final Set<Long> cruzados = new HashSet<>();

    public BalaRifle(EntityType<? extends BalaRifle> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    public void preparar(int tipo, float dano, int atraviesa, boolean blandos, boolean recto, int tirolinero, boolean silencio,
                         Vec3 inicio, @Nullable BlockPos anclaInicio) {
        entityData.set(TIPO, (byte) tipo);
        this.dano = dano;
        this.atraviesa = atraviesa;
        this.blandos = blandos;
        this.recto = recto;
        this.tirolinero = tirolinero;
        this.silencio = silencio;
        this.inicio = inicio;
        this.anclaInicio = anclaInicio;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TIPO, (byte) 0);
    }

    public int tipo() {
        return entityData.get(TIPO);
    }

    public double largoCuerda() {
        return Rifle.CUERDA_MAX + Rifle.CUERDA_NIVEL * tirolinero;
    }

    @Override
    public void tick() {
        // sin Projectile.tick(): ese lanza su propio PROJECTILE_SHOOT (el Silenciador no podría callarlo); el ruido del
        // disparo ya lo hace el tirador
        baseTick();
        if (level().isClientSide) {
            // en el cliente la posición la manda el servidor cada tick; aquí solo la estela de la trazadora
            // (el primer tramo no: sale de los ojos del tirador y con el zoom le taparía la mira)
            if (tipo() == Rifle.TRAZADORA && tickCount > 1) {
                Vec3 a = new Vec3(xo, yo, zo), b = position();
                for (int i = 0; i < 4; i++) {
                    Vec3 q = a.lerp(b, i / 4.0);
                    level().addParticle(ESTELA, q.x, q.y, q.z, 0, 0, 0);
                }
            }
            return;
        }
        Vec3 v = getDeltaMovement();
        Vec3 desde = position();
        if (impactar(desde, v)) return;
        Vec3 hasta = desde.add(v);
        setPos(hasta.x, hasta.y, hasta.z);
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(v.y, h)));
        if (!recto) v = v.add(0, -Rifle.GRAVEDAD, 0);
        setDeltaMovement(v.scale(0.997));
        // la cuerda no llega más lejos: se suelta y cae
        if (tipo() == Rifle.CUERDA && inicio != null && inicio.distanceTo(hasta) > largoCuerda()) {
            soltarCuerda(hasta);
            discard();
            return;
        }
        if (++vida > 100 || hasta.y < level().getMinBuildHeight() - 32) {
            if (tipo() == Rifle.CUERDA) soltarCuerda(hasta);
            discard();
        }
    }

    private void soltarCuerda(Vec3 q) {
        if (getOwner() instanceof ServerPlayer p && p.getAbilities().instabuild) return;
        ItemEntity e = new ItemEntity(level(), q.x, q.y, q.z, new ItemStack(ModItems.BALA_CUERDA.get()));
        level().addFreshEntity(e);
    }

    // ------------------------------------------------------------------ choques

    /** Recorre el tramo de este tick: mobs (los atraviesa si puede) y bloques. True si la bala ha acabado. */
    private boolean impactar(Vec3 desde, Vec3 v) {
        Vec3 hasta = desde.add(v);
        for (int vuelta = 0; vuelta < 12; vuelta++) {
            BlockHitResult bh = trazarBloques(desde, hasta);
            Vec3 fin = bh.getType() == HitResult.Type.MISS ? hasta : bh.getLocation();
            Entity mejor = null;
            Vec3 punto = null;
            double d0 = Double.MAX_VALUE;
            AABB caja = new AABB(desde, fin).inflate(1.0);
            for (Entity e : level().getEntities(this, caja, this::puedeDar)) {
                Optional<Vec3> o = e.getBoundingBox().inflate(0.12).clip(desde, fin);
                if (o.isPresent()) {
                    double d = desde.distanceToSqr(o.get());
                    if (d < d0) {
                        d0 = d;
                        mejor = e;
                        punto = o.get();
                    }
                }
            }
            if (mejor != null) {
                golpeados.add(mejor.getId());
                darA(mejor, punto, v);
                if (atraviesa > 1 && tipo() != Rifle.CUERDA) {
                    atraviesa--;
                    desde = punto;
                    continue;
                }
                if (tipo() == Rifle.CUERDA) soltarCuerda(punto);
                discard();
                return true;
            }
            if (bh.getType() != HitResult.Type.MISS) {
                darBloque(bh, v);
                discard();
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean puedeDar(Entity e) {
        if (!e.isAlive() || e.isSpectator() || !e.isPickable() || e instanceof BalaRifle || golpeados.contains(e.getId())) return false;
        Entity dueno = getOwner();
        if (dueno != null && (e == dueno || e == dueno.getVehicle()) && vida < 3) return false;
        return true;
    }

    /** Bloques blandos que Atravesar cruza sin pararse. */
    public static boolean blando(BlockState st) {
        return st.is(BlockTags.LEAVES) || st.is(BlockTags.WOOL) || st.is(BlockTags.WOOL_CARPETS) || st.is(Tags.Blocks.GLASS)
                || st.is(Tags.Blocks.GLASS_PANES) || st.is(BlockTags.IMPERMEABLE);
    }

    private BlockHitResult trazarBloques(Vec3 desde, Vec3 hasta) {
        ClipContext ctx = new ClipContext(desde, hasta, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
        Level l = level();
        return BlockGetter.traverseBlocks(desde, hasta, ctx, (c, pos) -> {
            BlockState st = l.getBlockState(pos);
            if (st.isAir()) return null;
            if (blandos && blando(st)) {
                if (cruzados.add(pos.asLong()) && l instanceof ServerLevel sl) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
                    sl.playSound(null, pos, st.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 1.4F);
                }
                return null;
            }
            VoxelShape sh = c.getBlockShape(st, l, pos);
            return sh.isEmpty() ? null : sh.clip(desde, hasta, pos);
        }, c -> {
            Vec3 d = desde.subtract(hasta);
            return BlockHitResult.miss(hasta, Direction.getNearest(d.x, d.y, d.z), BlockPos.containing(hasta));
        });
    }

    private void darA(Entity e, Vec3 punto, Vec3 v) {
        Entity dueno = getOwner();
        boolean cabeza = e instanceof LivingEntity && punto.y >= e.getEyeY() - 0.22;
        float d = dano * (cabeza ? Rifle.CABEZA : 1F) * (tipo() == Rifle.CUERDA ? 0.4F : 1F);
        boolean entra = RifleDano.golpear(e, this, dueno, d);
        ServerLevel sl = (ServerLevel) level();
        if (entra && e instanceof LivingEntity le) {
            Vec3 h = new Vec3(v.x, 0, v.z);
            if (h.lengthSqr() > 1e-6) le.knockback(0.35, -h.x, -h.z);
            if (tipo() == Rifle.TRAZADORA) le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false), dueno);
        }
        if (cabeza) {
            sl.sendParticles(ParticleTypes.CRIT, punto.x, punto.y, punto.z, 10, 0.1, 0.1, 0.1, 0.3);
            // el "ding" de acertar en la cabeza, solo para quien dispara
            if (dueno instanceof ServerPlayer sp) sp.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7F, 1.3F);
        }
        if (!silencio) level().gameEvent(this, GameEvent.PROJECTILE_LAND, punto);
    }

    private void darBloque(BlockHitResult bh, Vec3 v) {
        ServerLevel sl = (ServerLevel) level();
        BlockPos pos = bh.getBlockPos();
        BlockState st = sl.getBlockState(pos);
        Vec3 q = bh.getLocation();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), q.x, q.y, q.z, 8, 0.05, 0.05, 0.05, 0.08);
        sl.playSound(null, pos, st.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 1.2F);
        if (!silencio) sl.gameEvent(this, GameEvent.PROJECTILE_LAND, q);
        if (tipo() == Rifle.TRAZADORA) {
            sl.sendParticles(ParticleTypes.GLOW, q.x, q.y, q.z, 14, 0.25, 0.25, 0.25, 0.02);
            sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y, q.z, 6, 0.2, 0.2, 0.2, 0.5);
        }
        if (tipo() == Rifle.CUERDA) {
            Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            Vec3 fin = q.add(n.scale(0.12));
            boolean ok = inicio != null && getOwner() instanceof ServerPlayer p
                    && Tirolinas.crear(sl, p, inicio, anclaInicio, fin, pos, tirolinero, largoCuerda());
            if (!ok) soltarCuerda(fin);
        }
    }

    // ------------------------------------------------------------------ varios

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
