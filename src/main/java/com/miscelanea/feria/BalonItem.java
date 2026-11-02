package com.miscelanea.feria;

import com.miscelanea.registry.ModEntities;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Balón de canastas: mantén clic derecho para coger fuerza y suelta para lanzar (no se gasta). */
public class BalonItem extends Item {
    public BalonItem(Properties p) {
        super(p);
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack st, Level l, LivingEntity e, int quedan) {
        if (!(e instanceof Player p) || l.isClientSide) return;
        float fuerza = Math.min(1, (getUseDuration(st) - quedan) / 20F);
        if (fuerza < 0.1F) return;
        Vec3 mira = p.getLookAngle();
        BalonEntity b = new BalonEntity(ModEntities.BALON.get(), l);
        b.setPos(p.getX() + mira.x * 0.4, p.getEyeY() - 0.2, p.getZ() + mira.z * 0.4);
        b.setDeltaMovement(mira.scale(0.35 + 0.55 * fuerza));
        MaquinaAltaBlockEntity be = cercana(l, p);
        b.distancia = be != null ? Math.sqrt(p.distanceToSqr(be.mundo(0, 0, 0))) : 0;
        l.addFreshEntity(b);
        p.getCooldowns().addCooldown(this, 8);
    }

    static MaquinaAltaBlockEntity cercana(Level l, Player p) {
        net.minecraft.core.BlockPos c = p.blockPosition();
        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 3, 8)))
            if (l.getBlockEntity(q) instanceof MaquinaAltaBlockEntity be && be.tipo() == MaquinaAltaBlock.CANASTA && p.getUUID().equals(be.jugador)) return be;
        return null;
    }
}
