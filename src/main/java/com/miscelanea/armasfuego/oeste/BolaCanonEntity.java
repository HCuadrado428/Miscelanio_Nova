package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bolaño del cañón de mano: vuela en parábola y estalla donde da. Si es un bloque que cae (arena, grava, yunque...),
 * no estalla: cae donde da y se queda, como un bloque que cae vanilla. El saltarín (de slime) rebota tres veces antes.
 */
public class BolaCanonEntity extends ThrowableItemProjectile {
    private int rebotes;
    public BolaCanonEntity(EntityType<? extends BolaCanonEntity> t, Level l) {
        super(t, l);
    }

    public BolaCanonEntity(EntityType<? extends BolaCanonEntity> t, LivingEntity tirador, Level l) {
        super(t, tirador, l);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    protected float getGravity() {
        return 0.035F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount > 1 && getDeltaMovement().lengthSqr() > 1e-4) level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        if (!level().isClientSide && tickCount > 200 && !isNoGravity()) discard();
        if (level().isClientSide && saltarin() && tickCount % 3 == 0) level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
    }

    private boolean fuegos() {
        return getItem().is(com.miscelanea.registry.ModItems.BOLANO_FUEGOS.get());
    }

    private boolean incendiario() {
        return getItem().is(com.miscelanea.registry.ModItems.BOLANO_INCENDIARIO.get());
    }

    private boolean saltarin() {
        return getItem().is(com.miscelanea.registry.ModItems.BOLANO_SALTARIN.get());
    }

    /** El bloque que cae que se ha disparado (o null si es un bolaño). */
    @Nullable
    private BlockState bloque() {
        return getItem().getItem() instanceof BlockItem bi && bi.getBlock() instanceof FallingBlock ? bi.getBlock().defaultBlockState() : null;
    }

    private static boolean yunque(@Nullable BlockState st) {
        return st != null && st.getBlock() instanceof AnvilBlock;
    }

    /** Se queda donde da (como un bloque que cae) si lo dejan la config y la protección del sitio; si no, cae como objeto. */
    private void colocar(ServerLevel l, BlockState st, HitResult r) {
        BlockPos pos = r instanceof BlockHitResult bh ? bh.getBlockPos().relative(bh.getDirection()) : BlockPos.containing(r.getLocation());
        if (!libre(l, pos)) pos = pos.above();
        if (EquilibrioArmas.canonColoca() && libre(l, pos) && permitido(l, pos)) {
            FallingBlockEntity fb = FallingBlockEntity.fall(l, pos, st);
            if (yunque(st)) fb.setHurtsEntities(2.0F, 40);
            l.playSound(null, pos, st.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
            if (getOwner() instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.logros.LogrosMiscelanea.dar(sp, "canon_arena");
        } else {
            spawnAtLocation(new ItemStack(st.getBlock()));
        }
    }

    /** Aire o algo que se puede quitar sin líquido (no borra el agua). */
    private static boolean libre(ServerLevel l, BlockPos pos) {
        BlockState st = l.getBlockState(pos);
        return l.isInWorldBounds(pos) && (st.isAir() || st.canBeReplaced() && st.getFluidState().isEmpty());
    }

    /** La protección: lo que pueda construir el que dispara (zona de aparición, aventura, permisos de otros mods). */
    private boolean permitido(ServerLevel l, BlockPos pos) {
        if (!(getOwner() instanceof net.minecraft.server.level.ServerPlayer p)) return l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (!p.mayBuild() || !l.mayInteract(p, pos)) return false;
        return !ForgeEventFactory.onBlockPlace(p, BlockSnapshot.create(l.dimension(), l, pos), Direction.UP);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        BlockState st = bloque();
        if (st != null && r.getEntity() instanceof LivingEntity v && level() instanceof ServerLevel) {
            // el bloque, de lleno: el yunque, con daño de yunque («¡bip, bip!»)
            if (yunque(st)) {
                v.hurt(Balistica.fuente(v, DamageTypes.FALLING_ANVIL, this, getOwner()), EquilibrioArmas.canonDano() * EquilibrioArmas.canonYunque());
                if (getOwner() instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.logros.LogrosMiscelanea.dar(sp, "canon_yunque");
            } else Balistica.herir(v, getOwner(), EquilibrioArmas.canonDano() * EquilibrioArmas.canonBloque(), 0.2F, CanonManoItem.DANO, false);
            return;
        }
        if (r.getEntity() instanceof LivingEntity v && level() instanceof ServerLevel) {
            Balistica.herir(v, getOwner(), EquilibrioArmas.canonDano() * (fuegos() ? 0.35F : 1F), 0.3F, CanonManoItem.DANO, false);
            if (incendiario()) v.setSecondsOnFire(6);
        }
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (!(level() instanceof ServerLevel l)) return;
        Vec3 q = r.getLocation();
        BlockState st = bloque();
        if (st != null) {
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), q.x, q.y, q.z, 20, 0.3, 0.3, 0.3, 0.15);
            l.playSound(null, q.x, q.y, q.z, yunque(st) ? net.minecraft.sounds.SoundEvents.ANVIL_LAND : st.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.2F, 0.9F);
            l.gameEvent(this, GameEvent.PROJECTILE_LAND, q);
            colocar(l, st, r);
            discard();
            return;
        }
        if (saltarin() && rebotes < 3 && r instanceof BlockHitResult bh) {
            // el saltarín rebota (Angry Birds): pierde un poco cada vez
            Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))).scale(0.75));
            setPos(q.add(n.scale(0.1)));
            rebotes++;
            l.playSound(null, q.x, q.y, q.z, net.minecraft.sounds.SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.2F, 0.8F + rebotes * 0.15F);
            l.sendParticles(ParticleTypes.ITEM_SLIME, q.x, q.y, q.z, 8, 0.2, 0.1, 0.2, 0.05);
            if (rebotes == 3 && getOwner() instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.logros.LogrosMiscelanea.dar(sp, "canon_rebote");
            return;
        }
        Entity directo = r instanceof EntityHitResult eh ? eh.getEntity() : null;
        // estallido: un tercio del daño alrededor y un empujón (sin romper bloques salvo que lo diga la config)
        float alrededor = EquilibrioArmas.canonDano() / 3F * (fuegos() ? 0.35F : 1F);
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(2.5), e -> e.isAlive() && e != directo)) {
            double d = v.position().distanceTo(q);
            if (d > 2.5) continue;
            float k = (float) (1 - d / 2.5);
            Balistica.herir(v, getOwner(), alrededor * k, 0.0F, CanonManoItem.DANO, false);
            if (incendiario()) v.setSecondsOnFire(4);
            Vec3 emp = v.position().subtract(q).normalize().scale(0.6 * k);
            v.push(emp.x, 0.25 * k, emp.z);
            v.hurtMarked = true;
        }
        if (fuegos()) {
            // el bolaño de feria: una palmera de fuegos artificiales de colores (casi sin daño)
            for (int i = 0; i < 5; i++)
                l.sendParticles(ParticleTypes.FIREWORK, q.x, q.y + 0.5, q.z, 14, 0.15, 0.15, 0.15, 0.25);
            l.sendParticles(ParticleTypes.FLASH, q.x, q.y + 0.5, q.z, 1, 0, 0, 0, 0);
            l.playSound(null, q.x, q.y, q.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3.0F, 1.0F);
            l.playSound(null, q.x, q.y, q.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 2.0F, 1.0F);
            if (getOwner() instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.logros.LogrosMiscelanea.dar(sp, "canon_fuegos");
        } else if (EquilibrioArmas.canonRompe()) {
            l.explode(this, q.x, q.y, q.z, 1.6F, Level.ExplosionInteraction.TNT);
        } else {
            l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 6, 0.3, 0.2, 0.3, 0.02);
            if (r instanceof BlockHitResult bh)
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(bh.getBlockPos())), q.x, q.y, q.z, 16, 0.3, 0.3, 0.3, 0.15);
            l.playSound(null, q.x, q.y, q.z, ModSounds.CANON_DISPARO.get(), SoundSource.PLAYERS, 2.0F, 1.3F);
            l.gameEvent(this, GameEvent.EXPLODE, q);
            if (incendiario()) l.sendParticles(ParticleTypes.FLAME, q.x, q.y + 0.3, q.z, 30, 0.8, 0.3, 0.8, 0.02);
        }
        discard();
    }
}
