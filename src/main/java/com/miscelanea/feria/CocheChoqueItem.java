package com.miscelanea.feria;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Coloca un coche de choque donde haces clic (de un color al azar si el objeto no trae uno). */
public class CocheChoqueItem extends Item {
    public CocheChoqueItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level l = c.getLevel();
        if (l.isClientSide) return InteractionResult.SUCCESS;
        BlockPos p = c.getClickedPos().relative(c.getClickedFace());
        CocheChoque coche = new CocheChoque(ModEntities.COCHE_CHOQUE.get(), l);
        coche.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, c.getRotation(), 0);
        var tag = c.getItemInHand().getTag();
        coche.color(tag != null && tag.contains("color") ? tag.getInt("color") : l.random.nextInt(4));
        if (!l.noCollision(coche, coche.getBoundingBox())) return InteractionResult.FAIL;
        l.addFreshEntity(coche);
        if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
