package com.miscelanea.armas;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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

/**
 * Mazo de Wemmbu: el mazo de la 1.21 traído a esta versión con lo que lo hizo famoso en el Unstable SMP.
 * Golpe en caída: cuanto más caes, más daño (4 por bloque los 3 primeros, 2 los 5 siguientes y 1 el resto), no te
 * haces daño al caer y empuja a los de alrededor. Ráfaga de viento: tras un golpe en caída sales disparado hacia
 * arriba para encadenar otro. Clic derecho: carga de viento que empuja y, tirada a tus pies, te lanza al aire.
 */
public class MazoItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mazo_wemmbu.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Multimap<Attribute, AttributeModifier> atributos;

    public MazoItem(Properties p) {
        super(p);
        atributos = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Mazo", 6.0, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Mazo", -3.4, AttributeModifier.Operation.ADDITION)).build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? atributos : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean canAttackBlock(BlockState s, Level l, BlockPos p, Player pl) {
        return !pl.isCreative();
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }

    /** Daño extra del golpe en caída según la altura (como el mazo de la 1.21). */
    public static float extraCaida(float caida) {
        if (caida <= 1.5F) return 0;
        float d = Math.min(caida, 3) * 4;
        if (caida > 3) d += Math.min(caida - 3, 5) * 2;
        if (caida > 8) d += caida - 8;
        return d;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 mira = p.getViewVector(1);
            VientoEntity v = new VientoEntity(level, p);
            v.setPos(p.getX() + mira.x * 0.6, p.getEyeY() - 0.1 + mira.y * 0.6, p.getZ() + mira.z * 0.6);
            v.setDeltaMovement(mira.scale(1.5));
            level.addFreshEntity(v);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VIENTO_LANZA.get(), SoundSource.PLAYERS, 1F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        }
        p.getCooldowns().addCooldown(this, 10);
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable("item.miscelanea.mazo_wemmbu.ayuda1").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.mazo_wemmbu.ayuda2").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("mazo_wemmbu", true);
                return r;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mazo", 0, s -> s.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
