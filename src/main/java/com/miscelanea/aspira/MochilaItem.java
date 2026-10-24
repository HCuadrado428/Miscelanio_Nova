package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.MochilaRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** La mochila del Aspirafantasmas 3000: modelo 3D en la espalda, depósito de capturas y tanque de pociones. */
public class MochilaItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mochila.idle");
    private static final RawAnimation ASPIRAR = RawAnimation.begin().thenLoop("animation.mochila.aspirar");
    private static final RawAnimation SOPLAR_ANIM = RawAnimation.begin().thenLoop("animation.mochila.soplar");
    private static final RawAnimation LANZALLAMAS_ANIM = RawAnimation.begin().thenLoop("animation.mochila.lanzallamas");
    private static final RawAnimation SOBRECALENTADA = RawAnimation.begin().thenLoop("animation.mochila.sobrecalentada");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MochilaItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String p = "tooltip." + Miscelanea.MOD_ID + ".";
        List<Component> capturados = Deposito.nombresCapturados(stack);
        tooltip.add(Component.translatable(p + "deposito", capturados.size(), Deposito.CAPACIDAD).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Component c : capturados) {
            tooltip.add(Component.literal("  • ").append(c).withStyle(ChatFormatting.GRAY));
        }
        ItemStack tanque = Deposito.tanque(stack);
        if (!tanque.isEmpty()) {
            tooltip.add(Component.translatable(p + "tanque", Math.round(Deposito.carga(stack))).withStyle(ChatFormatting.DARK_AQUA));
            List<Component> efectos = new ArrayList<>();
            PotionUtils.addPotionTooltip(tanque, efectos, 0.25F);
            for (Component c : efectos) tooltip.add(Component.literal("  ").append(c));
        }
        List<Reserva.Entrada> reserva = Reserva.leer(stack);
        if (!reserva.isEmpty()) {
            tooltip.add(Component.translatable(p + "reserva").withStyle(ChatFormatting.GREEN));
            for (Reserva.Entrada r : reserva) {
                Component nombre = Component.translatable(r.efecto().getDescriptionId());
                if (r.amplificador() > 0) {
                    nombre = Component.empty().append(nombre).append(" ")
                            .append(Component.translatable("potion.potency." + r.amplificador()));
                }
                Component tiempo = r.efecto().isInstantenous()
                        ? Component.literal("x" + r.ticks())
                        : net.minecraft.world.effect.MobEffectUtil.formatDuration(
                                new net.minecraft.world.effect.MobEffectInstance(r.efecto(), r.ticks()), 1.0F);
                tooltip.add(Component.literal("  • ").append(nombre).append(" (").append(tiempo).append(")")
                        .withStyle(ChatFormatting.BLUE));
            }
        }
        tooltip.add(Component.translatable(p + "soltar").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ============================================================ GeckoLib

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                                   EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (this.renderer == null) this.renderer = new MochilaRenderer();
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "turbina", 5, state -> {
            Entity entity = state.getData(DataTickets.ENTITY);
            if (entity instanceof LivingEntity le) {
                ItemStack boquilla = boquillaEnMano(le);
                if (!boquilla.isEmpty()) {
                    if (Calor.sobrecalentada(boquilla)) return state.setAndContinue(SOBRECALENTADA);
                    if (AspirafantasmasItem.activa(boquilla)) {
                        return state.setAndContinue(switch (AspirafantasmasItem.modoActivo(boquilla)) {
                            case SOPLAR, SOPLIDO -> SOPLAR_ANIM;
                            case LANZALLAMAS -> LANZALLAMAS_ANIM;
                            default -> ASPIRAR;
                        });
                    }
                }
            }
            return state.setAndContinue(IDLE);
        }));
    }

    private static ItemStack boquillaEnMano(LivingEntity le) {
        if (le.getMainHandItem().getItem() instanceof AspirafantasmasItem) return le.getMainHandItem();
        if (le.getOffhandItem().getItem() instanceof AspirafantasmasItem) return le.getOffhandItem();
        return ItemStack.EMPTY;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
