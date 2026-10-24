package com.miscelanea.medigun;

import com.miscelanea.client.medigun.MochilaMedicaRenderer;
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

/** Mochila médica (hueco del pecho): dos depósitos y un vial que se llena con la ÜberCarga de la medigun. */
public class MochilaMedicaItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mochila_medica.idle");
    private static final RawAnimation CURANDO = RawAnimation.begin().thenLoop("animation.mochila_medica.curando");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MochilaMedicaItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    public static boolean lleva(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.MOCHILA_MEDICA.get());
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
                if (this.renderer == null) this.renderer = new MochilaMedicaRenderer();
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "mochila_medica", 4, state -> {
            Entity e = state.getData(DataTickets.ENTITY);
            if (e instanceof LivingEntity le && le.isUsingItem() && le.getUseItem().getItem() instanceof MedigunItem
                    && le.getUseItem().hasTag() && le.getUseItem().getTag().getBoolean(Medigun.CURANDO)) {
                return state.setAndContinue(CURANDO);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
