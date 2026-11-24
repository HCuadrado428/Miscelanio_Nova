package com.miscelanea.rave;

import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Caramelos de fiesta de la rave: chucherías mágicas (inventadas) que dan un "subidón" con efectos del juego durante un rato
 * y, al acabarse, un "bajón" de medio minuto (más lento y más flojo). Si te comes otro con el bajón puesto, te mareas.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Caramelos {
    private Caramelos() {}

    /** Efecto sin lógica propia (el subidón solo marca; la visión de colores la dibuja el cliente). */
    public static class Efecto extends MobEffect {
        public Efecto(MobEffectCategory c, int color) {
            super(c, color);
        }
    }

    public static MobEffect bajon() {
        return new Efecto(MobEffectCategory.HARMFUL, 0x5A5A70)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6d1f0c0e-3a57-4f0e-9b1a-7a1d2c5e9a01", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL)
                .addAttributeModifier(Attributes.ATTACK_DAMAGE, "6d1f0c0e-3a57-4f0e-9b1a-7a1d2c5e9a02", -1.0, AttributeModifier.Operation.ADDITION);
    }

    public static final int BAJON_TICKS = 600;

    /** Al acabarse el subidón llega el bajón. */
    @SubscribeEvent
    public static void expira(MobEffectEvent.Expired e) {
        MobEffectInstance i = e.getEffectInstance();
        LivingEntity v = e.getEntity();
        if (i == null || v.level().isClientSide || i.getEffect() != RaveRegistro.SUBIDON.get()) return;
        v.addEffect(new MobEffectInstance(RaveRegistro.BAJON.get(), BAJON_TICKS));
    }

    public static class CarameloItem extends Item {
        private final int tipo;

        public CarameloItem(int tipo) {
            super(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1F).alwaysEat().fast().build()));
            this.tipo = tipo;
        }

        /** Los efectos del caramelo (también lo llaman las pruebas). */
        public static void tomar(LivingEntity v, int tipo) {
            if (v.hasEffect(RaveRegistro.BAJON.get())) {
                v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 240));
                if (v instanceof Player p) p.displayClientMessage(Component.translatable("rave.miscelanea.caramelo.empacho"), true);
                return;
            }
            switch (tipo) {
                case 0 -> {   // caramelo corazón: a bailar
                    v.addEffect(new MobEffectInstance(RaveRegistro.SUBIDON.get(), 900));
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 900, 1));
                    v.addEffect(new MobEffectInstance(MobEffects.JUMP, 900));
                }
                case 1 -> {   // piruleta arcoíris: todo de colores
                    v.addEffect(new MobEffectInstance(RaveRegistro.SUBIDON.get(), 1200));
                    v.addEffect(new MobEffectInstance(RaveRegistro.COLORES.get(), 1200));
                    v.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200));
                }
                case 2 -> {   // polvo de estrellas: flotar y brillar
                    v.addEffect(new MobEffectInstance(RaveRegistro.SUBIDON.get(), 600));
                    v.addEffect(new MobEffectInstance(RaveRegistro.COLORES.get(), 600, 1));
                    v.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50));
                    v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600));
                    v.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600));
                }
                default -> {  // chicle eléctrico: manos rápidas
                    v.addEffect(new MobEffectInstance(RaveRegistro.SUBIDON.get(), 900));
                    v.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 900, 1));
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 900));
                }
            }
        }

        @Override
        public ItemStack finishUsingItem(ItemStack s, Level l, LivingEntity v) {
            if (!l.isClientSide) tomar(v, tipo);
            return super.finishUsingItem(s, l, v);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
            t.add(Component.translatable(getDescriptionId() + ".ayuda").withStyle(ChatFormatting.GRAY));
            t.add(Component.translatable("rave.miscelanea.caramelo.bajon").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
