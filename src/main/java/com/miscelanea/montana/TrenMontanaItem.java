package com.miscelanea.montana;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Pone un tren de 2 vagones en una vía de montaña rusa (agachado sobre un tren, le añade un vagón). */
public class TrenMontanaItem extends Item {
    public TrenMontanaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level l = c.getLevel();
        Via.Pieza p = Via.buscar(l, c.getClickedPos());
        if (p == null) return InteractionResult.PASS;
        // no se pone encima de otro tren
        if (!l.getEntitiesOfClass(TrenMontana.class, new net.minecraft.world.phys.AABB(p.pos()).inflate(0.5)).isEmpty()) return InteractionResult.FAIL;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        TrenMontana t = new TrenMontana(com.miscelanea.registry.ModEntities.TREN_MONTANA.get(), l);
        t.colocar(p.pos(), 0.5, 2);
        // que quepa el segundo vagón detrás: si no hay vía detrás, se adelanta
        if (Via.anterior(l, p) == null) {
            Via.Sitio s = Via.avanzar(l, new Via.Sitio(p, 0.5), TrenMontana.SEPARACION, null);
            t.colocar(s.pieza().pos(), s.t(), 2);
        }
        l.addFreshEntity(t);
        if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack st, @javax.annotation.Nullable Level l, java.util.List<net.minecraft.network.chat.Component> t, net.minecraft.world.item.TooltipFlag f) {
        t.add(net.minecraft.network.chat.Component.translatable("montana.miscelanea.ayuda.tren").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
