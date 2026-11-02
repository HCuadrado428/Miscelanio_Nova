package com.miscelanea.dardos;

import com.miscelanea.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Dardo en vuelo. Si da en una diana se clava (y vuelve a tu mano); si no, cae al suelo. */
public class Dardo extends ThrowableItemProjectile {
    public Dardo(EntityType<? extends Dardo> t, Level l) {
        super(t, l);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DARDO.get();
    }

    @Override
    protected float getGravity() {
        return 0.012F;
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide) return;
        Player p = getOwner() instanceof Player pl ? pl : null;
        boolean creativo = p != null && p.getAbilities().instabuild;
        // solo se clava si da en la cara de la diana (no por detrás ni por los cantos)
        if (level().getBlockEntity(r.getBlockPos()) instanceof DianaDardosBlockEntity be && r.getDirection() == be.facing()) {
            be.clavar(p, r.getLocation());
            if (p != null && !creativo && !p.getInventory().add(new ItemStack(ModItems.DARDO.get())))
                p.drop(new ItemStack(ModItems.DARDO.get()), false);
        } else if (!creativo) spawnAtLocation(new ItemStack(ModItems.DARDO.get()), 0.1F);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        r.getEntity().hurt(damageSources().thrown(this, getOwner()), 1F);
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (r.getType() == HitResult.Type.ENTITY && !level().isClientSide) {
            // en creativo el dardo no se gastó, así que tampoco se suelta
            if (!(getOwner() instanceof Player pl && pl.getAbilities().instabuild)) spawnAtLocation(new ItemStack(ModItems.DARDO.get()), 0.1F);
            discard();
        }
    }
}
