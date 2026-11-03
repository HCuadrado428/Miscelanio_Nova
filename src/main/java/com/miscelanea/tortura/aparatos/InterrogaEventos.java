package com.miscelanea.tortura.aparatos;

import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos del gancho para colgar: clic derecho con la mano vacía al colgado = empujarlo (Shift = bajarlo y soltarlo) y
 * los golpes lo balancean. La limpieza (morir, desconectarse, cambiar de dimensión) ya la hace {@link AparatosEventos}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class InterrogaEventos {
    private InterrogaEventos() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        if (e.isCanceled() || e.getHand() != InteractionHand.MAIN_HAND || !(e.getTarget() instanceof LivingEntity t)) return;
        Player p = e.getEntity();
        if (Aparatos.en(p) || !p.getMainHandItem().isEmpty()) return;
        if (p.level().isClientSide) {
            if (Aparatos.en(t) && AparatosVictimas.tipo(t.getId()) == AparatosVictimas.COLGADO) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!(Aparatos.de(t) instanceof GanchoBlockEntity g)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (p.isShiftKeyDown()) {
            if (g.puedeManejar(p)) {
                g.sacar(true);
                p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.sacado", t.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            } else p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        g.empujar(p.position(), 1.0);
        p.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Un golpe al colgado lo balancea (en la dirección del golpe). */
    @SubscribeEvent
    public static void golpe(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        Entity a = e.getSource().getDirectEntity();
        if (a == null || a == v || !v.getPersistentData().contains(Aparatos.CLAVE)) return;
        if (Aparatos.de(v) instanceof GanchoBlockEntity g) g.empujar(a.position(), 0.7);
    }
}
