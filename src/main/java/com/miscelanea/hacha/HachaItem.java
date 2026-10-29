package com.miscelanea.hacha;

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

/** Hacha Leviatán (God of War): cámara al hombro, combos, lanzar y llamar, escudo, rúnicos y escarcha. */
public class HachaItem extends Item implements GeoItem {
    public static final String LANZADA = "Lanzada", ESCARCHA = "EscarchaHasta", HELADA = "Helada", CD_LIGERO = "CdLigero", CD_PESADO = "CdPesado";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.hacha_leviatan.idle");
    private static final RawAnimation HIELO = RawAnimation.begin().thenLoop("animation.hacha_leviatan.escarcha");
    private static final RawAnimation OCULTA = RawAnimation.begin().thenLoop("animation.hacha_leviatan.lanzada");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HachaItem(Properties p) {
        super(p);
    }

    public static boolean lanzada(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(LANZADA);
    }

    public static void ponerLanzada(ItemStack s, boolean v) {
        s.getOrCreateTag().putBoolean(LANZADA, v);
    }

    public static boolean escarcha(ItemStack s, Level level) {
        return s.hasTag() && s.getTag().getLong(ESCARCHA) > level.getGameTime();
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        for (int i = 1; i <= 6; i++)
            lista.add(Component.translatable("item.miscelanea.hacha_leviatan.ayuda" + i).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (!level.isClientSide && s.hasTag()) {
            boolean helada = escarcha(s, level);
            if (helada != s.getTag().getBoolean(HELADA)) s.getTag().putBoolean(HELADA, helada);
        }
        // si el hacha lanzada ya no existe (se descargó el chunk, etc.) vuelve sola
        if (!level.isClientSide && lanzada(s) && e instanceof net.minecraft.server.level.ServerPlayer p && Leviatan.lanzada(p) == null
                && level.getGameTime() % 20 == 0) {
            ponerLanzada(s, false);
        }
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
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("hacha_leviatan", true);
                return r;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "hacha", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && lanzada(s)) return state.setAndContinue(OCULTA);
            if (s != null && s.hasTag() && s.getTag().getBoolean(HELADA)) return state.setAndContinue(HIELO);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
