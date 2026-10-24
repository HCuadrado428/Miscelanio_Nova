package com.miscelanea.porra;

import com.miscelanea.client.porra.BateriaRenderer;
import com.miscelanea.registry.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
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
 * Generador de motor a la espalda (id interno "bateria"). Alimenta la porra: cada descarga gasta carga.
 * Se recarga sola poco a poco si no se usa, y muy rápido bajo una tormenta.
 */
public class BateriaItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bateria.idle");
    private static final RawAnimation DESCARGA = RawAnimation.begin().thenLoop("animation.bateria.descarga");

    public static final float MAX = 100.0F;
    private static final String GASTO = "Gasto";
    private static final String ULTIMO_USO = "UltimoUso";
    /** Ticks que se mantiene la animación de descarga (se sincroniza por NBT). */
    public static final String DESCARGANDO = "Descargando";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BateriaItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    // ------------------------------------------------------------------ carga

    public static ItemStack de(LivingEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(ModItems.BATERIA.get()) ? chest : ItemStack.EMPTY;
    }

    /** Una batería recién fabricada viene llena (se guarda lo gastado, no lo que queda). */
    public static float carga(ItemStack bateria) {
        if (bateria.isEmpty()) return 0.0F;
        return MAX - (bateria.hasTag() ? bateria.getTag().getFloat(GASTO) : 0.0F);
    }

    public static boolean tiene(LivingEntity e, float cantidad) {
        return carga(de(e)) >= cantidad;
    }

    /** Gasta carga de la batería que lleva puesta. Devuelve false si no hay suficiente. */
    public static boolean gastar(LivingEntity e, float cantidad) {
        ItemStack b = de(e);
        if (carga(b) < cantidad) return false;
        b.getOrCreateTag().putFloat(GASTO, Math.min(MAX, b.getTag().getFloat(GASTO) + cantidad));
        b.getTag().putLong(ULTIMO_USO, e.level().getGameTime());
        b.getTag().putInt(DESCARGANDO, 6);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!stack.getTag().contains("GeckoLibID") && level instanceof net.minecraft.server.level.ServerLevel sl) {
            GeoItem.getOrAssignId(stack, sl);
        }
        int d = stack.getTag().getInt(DESCARGANDO);
        if (d > 0) {
            if (d == 1) stack.getTag().remove(DESCARGANDO);
            else stack.getTag().putInt(DESCARGANDO, d - 1);
        }
        // Humo del tubo de escape mientras el generador está puesto
        if (entity instanceof LivingEntity le && de(le) == stack && level instanceof net.minecraft.server.level.ServerLevel sl
                && level.getGameTime() % (d > 0 ? 3 : 14) == 0) {
            float yaw = le.yBodyRot * Mth.DEG_TO_RAD;
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);   // derecha
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);    // delante
            double x = le.getX() + rx * 0.19 - fx * 0.38;
            double z = le.getZ() + rz * 0.19 - fz * 0.38;
            double y = le.getY() + (le.isCrouching() ? 1.35 : 1.62);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 0, 0.0, 1.0, 0.0, 0.05);
        }
        float gasto = stack.getTag().getFloat(GASTO);
        if (gasto <= 0.0F) return;
        // Recarga: 1 por segundo tras 3 s sin usarla; bajo una tormenta, 1 por tick
        boolean tormenta = level.isThundering() && level.canSeeSky(entity.blockPosition());
        if (!tormenta && level.getGameTime() - stack.getTag().getLong(ULTIMO_USO) < 60) return;
        if (!tormenta && level.getGameTime() % 20 != 0) return;
        gasto -= 1.0F;
        if (gasto <= 0.0F) stack.getTag().remove(GASTO);
        else stack.getTag().putFloat(GASTO, gasto);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return carga(stack) < MAX;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * carga(stack) / MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.55F, 0.3F + 0.7F * carga(stack) / MAX, 1.0F);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                                   EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (this.renderer == null) this.renderer = new BateriaRenderer();
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "bateria", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag() && s.getTag().getInt(DESCARGANDO) > 0) return state.setAndContinue(DESCARGA);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
