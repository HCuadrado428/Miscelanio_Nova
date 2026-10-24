package com.miscelanea.nucleo;

import com.miscelanea.client.nucleo.NucleoRenderer;
import com.miscelanea.registry.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Núcleo de error incrustado en el pecho (solo creativo).
 * Shift + clic derecho con la mano vacía: aliento del núcleo (ver {@link Aliento}).
 */
public class NucleoItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.nucleo.idle");
    private static final RawAnimation CARGA = RawAnimation.begin().thenLoop("animation.nucleo.carga");
    private static final RawAnimation DISPARO = RawAnimation.begin().thenLoop("animation.nucleo.disparo");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NucleoItem(ArmorMaterial material, Properties properties) {
        super(material, Type.CHESTPLATE, properties);
    }

    public static boolean lleva(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.NUCLEO.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl
                && (!stack.hasTag() || !stack.getTag().contains("GeckoLibID"))) {
            GeoItem.getOrAssignId(stack, sl);
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                                   EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (this.renderer == null) this.renderer = new NucleoRenderer();
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "nucleo", 3, state -> {
            Entity e = state.getData(DataTickets.ENTITY);
            if (e != null) {
                float f = com.miscelanea.client.nucleo.NucleoCliente.fase(e.getId(), 0.0F);
                if (f >= 1.0F) return state.setAndContinue(DISPARO);
                if (f >= 0.0F) return state.setAndContinue(CARGA);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
