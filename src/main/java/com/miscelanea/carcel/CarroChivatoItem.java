package com.miscelanea.carcel;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Coloca el carro del chivato sobre un raíl. */
public class CarroChivatoItem extends Item {
    public CarroChivatoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!level.getBlockState(pos).is(BlockTags.RAILS)) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            CarroChivato c = new CarroChivato(ModEntities.CARRO_CHIVATO.get(), level);
            c.setPos(pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5);
            if (ctx.getPlayer() != null) c.setYRot(ctx.getPlayer().getYRot());
            level.addFreshEntity(c);
            if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
