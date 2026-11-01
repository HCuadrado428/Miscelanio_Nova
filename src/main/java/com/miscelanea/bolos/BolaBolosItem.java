package com.miscelanea.bolos;

import com.miscelanea.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Bola de bolos: mantén clic derecho para coger fuerza y suelta para tirarla. Si te mueves de lado al soltar, lleva efecto. */
public class BolaBolosItem extends Item {
    public BolaBolosItem(Properties p) {
        super(p);
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.SPEAR;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack st, Level l, LivingEntity e, int quedan) {
        if (!(e instanceof Player p) || l.isClientSide) return;
        int t = getUseDuration(st) - quedan;
        float fuerza = Math.min(1, t / 25F);
        if (fuerza < 0.15F) return;
        Vec3 mira = p.getLookAngle();
        Vec3 dir = new Vec3(mira.x, 0, mira.z).normalize();
        BolaBolos b = new BolaBolos(ModEntities.BOLA_BOLOS.get(), l);
        b.setPos(p.getX() + dir.x * 0.6, p.getY() + 0.05, p.getZ() + dir.z * 0.6);
        b.setDeltaMovement(dir.scale(0.22 + 0.36 * fuerza));
        b.efecto = p.isShiftKeyDown() ? (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1) : 0;
        BoleraBlockEntity be = BolaBolos.buscarBolera(l, p.position(), dir);
        l.addFreshEntity(b);
        if (be != null && !be.lanzamiento(p, b.getId())) {
            p.displayClientMessage(Component.translatable("miscelanea.bolos.ocupada", be.nombre), true);
            b.discard();
            return;
        }
        if (!p.getAbilities().instabuild) st.shrink(1);
    }
}
