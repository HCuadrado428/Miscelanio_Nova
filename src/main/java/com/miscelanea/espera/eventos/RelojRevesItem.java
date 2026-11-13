package com.miscelanea.espera.eventos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Reloj al revés: las agujas van hacia atrás y, al usarlo, tú también: vuelves a donde estabas hace 10 segundos (en el
 * mismo mundo; si no hace tanto que llegaste, a donde llegaste). Cada 15 s. Funciona en todas partes, pero solo se
 * encuentra en el Espera. El camino lo apunta {@link EsperaExtras} (medio segundo cada vez).
 */
public class RelojRevesItem extends Item {
    public static final int ESPERA = 300;

    public RelojRevesItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (l.isClientSide) return InteractionResultHolder.success(st);
        if (!(p instanceof ServerPlayer sp)) return InteractionResultHolder.pass(st);
        return usar(sp) ? InteractionResultHolder.consume(st) : InteractionResultHolder.fail(st);
    }

    /** Hace lo del reloj (también para la prueba). */
    public static boolean usar(ServerPlayer sp) {
        EsperaExtras.Huella h = EsperaExtras.haceDiez(sp);
        if (h == null || h.pos().distanceToSqr(sp.position()) < 1) {
            sp.displayClientMessage(Component.translatable("item.miscelanea.reloj_reves.nada"), true);
            return false;
        }
        var l = sp.serverLevel();
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
        l.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
        EsperaExtras.soltar(sp);
        sp.fallDistance = 0;
        sp.teleportTo(l, h.pos().x, h.pos().y, h.pos().z, h.yaw(), h.pitch());
        sp.setDeltaMovement(0, 0, 0);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, h.pos().x, h.pos().y + 1, h.pos().z, 40, 0.3, 0.6, 0.3, 0.05);
        // el tic-tac al revés: tres notas que bajan
        for (int i = 0; i < 3; i++)
            l.playSound(null, h.pos().x, h.pos().y, h.pos().z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5F, 1.4F - i * 0.25F);
        sp.getCooldowns().addCooldown(ExtrasRegistro.RELOJ.get(), ESPERA);
        EsperaExtras.olvidarCamino(sp);
        Logros.dar(sp, "reloj", "usar");
        sp.displayClientMessage(Component.translatable("item.miscelanea.reloj_reves.vuelta"), true);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.reloj_reves.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
