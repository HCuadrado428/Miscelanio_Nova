package com.miscelanea.llave;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Llave Espada (Kingdom Hearts). Mientras la llevas: cámara libre en tercera persona, animaciones y movimientos de Sora.
 * Clic izq: combo (en el aire, combo aéreo). Clic der: Strike Raid / abrir cerraduras. R: comando. Z: cambiar comando. V: fijar.
 */
public class LlaveEspadaItem extends Item implements GeoItem {
    private static final String LANZADA = "Lanzada", LANZADA_T = "LanzadaT";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.llave_espada.idle");
    private static final RawAnimation OCULTA = RawAnimation.begin().thenLoop("animation.llave_espada.lanzada");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LlaveEspadaItem(Properties p) {
        super(p);
    }

    public static boolean lanzada(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(LANZADA);
    }

    public static void ponerLanzada(ItemStack s, boolean v) {
        s.getOrCreateTag().putBoolean(LANZADA, v);
        s.getTag().putInt(LANZADA_T, v ? 120 : 0);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide || !lanzada(s)) return;
        int t = s.getTag().getInt(LANZADA_T) - 1;
        s.getTag().putInt(LANZADA_T, t);
        if (t <= 0) ponerLanzada(s, false);   // por si la llave se perdió
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        for (int i = 1; i <= 5; i++)
            lista.add(Component.translatable("item.miscelanea.llave_espada.ayuda" + i).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("llave_espada", true);
                return r;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "llave", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            return state.setAndContinue(s != null && lanzada(s) ? OCULTA : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
