package com.miscelanea.client.mesas;

import com.miscelanea.Miscelanea;
import com.miscelanea.mesas.AirHockeyBlockEntity;
import com.miscelanea.mesas.AirHockeyMandoPacket;
import com.miscelanea.mesas.Visibles;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mandos del air hockey: WASD mueve tu mazo por tu mitad de la mesa (visto desde tu portería) y agacharse suelta la mesa.
 * El personaje no se mueve mientras tanto.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class AirHockeyCliente {
    public static AirHockeyBlockEntity jugando;
    private static int ultimo = -1, cuenta;

    private AirHockeyCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        jugando = null;
        if (p == null || mc.level == null) return;
        for (AirHockeyBlockEntity be : Visibles.AIRHOCKEY)
            if (!be.isRemoved() && be.getLevel() == mc.level && be.lado(p.getUUID()) >= 0 && p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 49) jugando = be;
        if (jugando == null) {
            ultimo = -1;
            return;
        }
        int m = 0;
        if (mc.screen != null) {
            // con el chat o la pausa abiertos se sigue avisando al servidor (sin mover) para no perder la mesa
            if (mc.player.tickCount % 10 == 0) ModNetwork.CHANNEL.sendToServer(new AirHockeyMandoPacket(jugando.getBlockPos(), 0));
            return;
        }
        if (mc.options.keyUp.isDown()) m |= 1;
        if (mc.options.keyDown.isDown()) m |= 2;
        if (mc.options.keyLeft.isDown()) m |= 4;
        if (mc.options.keyRight.isDown()) m |= 8;
        if (mc.options.keyShift.isDown()) {
            ModNetwork.CHANNEL.sendToServer(new AirHockeyMandoPacket(jugando.getBlockPos(), 16));
            jugando.jug[jugando.lado(p.getUUID())] = null;
            jugando = null;
            p.displayClientMessage(Component.translatable("airhockey.miscelanea.suelta").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (m != ultimo || ++cuenta >= 10) {
            ModNetwork.CHANNEL.sendToServer(new AirHockeyMandoPacket(jugando.getBlockPos(), m));
            ultimo = m;
            cuenta = 0;
        }
        if (mc.level.getGameTime() % 20 == 0) p.displayClientMessage(Component.translatable("airhockey.miscelanea.ayuda").withStyle(ChatFormatting.AQUA), true);
    }

    @SubscribeEvent
    public static void mover(MovementInputUpdateEvent e) {
        if (jugando == null) return;
        var in = e.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }
}
