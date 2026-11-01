package com.miscelanea.feria;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Pone la vagoneta del tren de la bruja sobre un raíl. */
public class VagonetaBrujaItem extends Item {
    public VagonetaBrujaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level l = c.getLevel();
        BlockPos pos = c.getClickedPos();
        BlockState s = l.getBlockState(pos);
        if (!s.is(BlockTags.RAILS)) return InteractionResult.FAIL;
        if (!l.isClientSide) {
            RailShape forma = s.getBlock() instanceof BaseRailBlock r ? r.getRailDirection(s, l, pos, null) : RailShape.NORTH_SOUTH;
            double sube = forma.isAscending() ? 0.5 : 0;
            VagonetaBruja v = new VagonetaBruja(ModEntities.VAGONETA_BRUJA.get(), l);
            v.setPos(pos.getX() + 0.5, pos.getY() + 0.0625 + sube, pos.getZ() + 0.5);
            l.addFreshEntity(v);
            if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
