package com.miscelanea.dream;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Anillo de medianoche (solo de creativo, para rol): banda de metal oscuro con una gema púrpura y roja que late.
 * Mantén clic derecho 3 s (chispas de grieta rojas girando, un zumbido que sube, la pantalla se funde a negro) y te
 * lleva a la Medianoche; desde allí, otra vez, de vuelta adonde estabas. 10 s de espera entre viajes.
 */
public class AnilloMedianocheItem extends Item {
    /** Ticks que hay que mantener el clic. */
    public static final int CARGA = 60;

    public AnilloMedianocheItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        p.startUsingItem(h);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return CARGA;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level l, LivingEntity e, ItemStack st, int quedan) {
        if (l.isClientSide)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.dream.AnilloCliente.cargando(e, 1.0F - quedan / (float) CARGA));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        if (!l.isClientSide && e instanceof ServerPlayer sp) Medianoche.viajar(sp);
        return st;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        for (int i = 1; i <= 3; i++)
            tip.add(Component.translatable("item.miscelanea.anillo_medianoche.lore" + i).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tip.add(Component.empty());
        boolean alli = l != null && Medianoche.enMedianoche(l);
        tip.add(Component.translatable(alli ? "item.miscelanea.anillo_medianoche.uso_vuelta" : "item.miscelanea.anillo_medianoche.uso_ida")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand h, ItemStack st) {
                return com.miscelanea.client.dream.AnilloCliente.pose(e, h, st);
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.player.LocalPlayer pl,
                                                   net.minecraft.world.entity.HumanoidArm brazo, ItemStack s, float pt, float equip, float swing) {
                return com.miscelanea.client.dream.AnilloCliente.manoPrimeraPersona(ps, pl, brazo, s, pt, equip);
            }
        });
    }
}
