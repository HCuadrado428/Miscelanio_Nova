package com.miscelanea.ocarina;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Ocarina: mantén clic derecho para llevártela a la boca y toca con las teclas 1-5. */
public class OcarinaItem extends Item implements GeoItem {
    public static final int[] COLORES = {0x4AA3FF, 0x5BD16A, 0xF5D04A, 0xFF9A3C, 0xFF5A6A};
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ocarina.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OcarinaItem(Properties p) {
        super(p);
    }

    public static boolean tocando(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof OcarinaItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    public static MutableComponent notas(int[] n) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < n.length; i++) {
            if (i > 0) c.append(" ");
            c.append(Component.literal(Integer.toString(n[i])).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLORES[n[i] - 1])).withBold(true)));
        }
        return c;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.ocarina.ayuda", Component.keybind("key.hotbar.1"), Component.keybind("key.hotbar.5")).withStyle(ChatFormatting.GRAY));
        for (Cancion c : Cancion.values())
            lista.add(Component.translatable(c.clave()).withStyle(ChatFormatting.GOLD).append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(notas(c.notas)));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("ocarina", false);
                return r;
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.player.LocalPlayer pl,
                                                   net.minecraft.world.entity.HumanoidArm brazo, ItemStack st, float pt, float equip, float swing) {
                return com.miscelanea.client.ocarina.OcarinaCliente.manoPrimeraPersona(ps, pl, brazo, st, pt, equip);
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "ocarina", 0, s -> s.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
