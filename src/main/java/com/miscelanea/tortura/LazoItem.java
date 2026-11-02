package com.miscelanea.tortura;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Lazo (Red Dead Redemption 2). Mantén clic derecho para voltearlo sobre la cabeza y suelta para lanzarlo (más carga =
 * más lejos, hasta 16 bloques). Si enlaza a alguien empieza el tira y afloja: mantén clic derecho para tirar de la cuerda.
 * Derribado: clic derecho sobre él para maniatarlo. Shift + clic derecho con el lazo (creativo): lo sueltas al momento.
 * (La lógica está en {@link Lazo}; el uso sobre entidades lo gestiona {@link TorturaEventos}.)
 */
public class LazoItem extends ObjetoTortura {
    public LazoItem(Properties p) {
        super(p, 3);
    }

    /** ¿Está volteando (o tirando de) un lazo? */
    public static boolean usando(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof LazoItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        // en el servidor: si el lazo ya va por el aire no se voltea otro (con el tira y afloja sí: es "tirar")
        if (!level.isClientSide && Lazo.lanzado(p) && Lazo.tiraDe(p) == null) return InteractionResultHolder.fail(st);
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack st, int quedan) {
        if (level.isClientSide || !(e instanceof Player p)) return;
        int usado = getUseDuration(st) - quedan;
        if (Lazo.tiraDe(p) != null) return;   // tirando de la cuerda: el crujido lo pone el tira y afloja
        // silbido de la cuerda girando: más seguido (y agudo) cuanto más carga
        float carga = Math.min(1F, usado / (float) Lazo.CARGA_TICKS);
        int cada = carga >= 1 ? 7 : 10;
        if (usado % cada == 2)
            level.playSound(null, p.getX(), p.getY() + 2.0, p.getZ(), TorturaRegistro.LAZO_GIRAR.get(), SoundSource.PLAYERS,
                    0.45F + carga * 0.35F, 0.85F + carga * 0.3F + p.getRandom().nextFloat() * 0.08F);
    }

    @Override
    public void releaseUsing(ItemStack st, Level level, LivingEntity e, int quedan) {
        if (level.isClientSide || !(e instanceof ServerPlayer p)) return;
        Lazo.lanzar(p, getUseDuration(st) - quedan);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.player.LocalPlayer pl,
                                                   net.minecraft.world.entity.HumanoidArm brazo, ItemStack s, float pt, float equip, float swing) {
                return com.miscelanea.client.tortura.LazoCliente.manoPrimeraPersona(ps, pl, brazo, s, pt, equip);
            }
        });
    }
}
