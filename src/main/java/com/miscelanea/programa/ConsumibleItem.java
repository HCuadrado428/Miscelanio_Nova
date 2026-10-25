package com.miscelanea.programa;

import com.miscelanea.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Consumibles de la prueba: medicina grande, adrenalina, recargador de rig y pastilla de reaparición. */
public class ConsumibleItem extends Item {
    public enum Tipo { MEDICINA_GRANDE, ADRENALINA, RECARGADOR, PASTILLA }

    private final Tipo tipo;

    public ConsumibleItem(Properties p, Tipo tipo) {
        super(p);
        this.tipo = tipo;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return tipo == Tipo.MEDICINA_GRANDE ? 40 : 24;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return tipo == Tipo.PASTILLA ? UseAnim.EAT : UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p instanceof ServerPlayer sp && tipo != Tipo.PASTILLA) Acciones.poner(sp, Acciones.Accion.INYECTAR, getUseDuration(p.getItemInHand(hand)));
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity e) {
        if (!level.isClientSide && e instanceof ServerPlayer p) {
            switch (tipo) {
                case MEDICINA_GRANDE -> p.setHealth(p.getMaxHealth());
                case ADRENALINA -> p.addEffect(new MobEffectInstance(ModEffects.ADRENALINA.get(), 200, 0, false, false, true));
                case RECARGADOR -> Rigs.recargar(p);
                case PASTILLA -> {
                    if (Prueba.dentro(p)) Prueba.vidaExtra(p);
                }
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), tipo == Tipo.PASTILLA ? SoundEvents.GENERIC_EAT : SoundEvents.HONEY_DRINK,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
            if (!p.getAbilities().instabuild) s.shrink(1);
        }
        return s;
    }
}
