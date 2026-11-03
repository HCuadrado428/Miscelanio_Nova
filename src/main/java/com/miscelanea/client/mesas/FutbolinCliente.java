package com.miscelanea.client.mesas;

import com.miscelanea.Miscelanea;
import com.miscelanea.mesas.FutbolinBlockEntity;
import com.miscelanea.mesas.FutbolinMandoPacket;
import com.miscelanea.mesas.Visibles;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mandos del futbolín: mientras juegas, adelante/atrás empujan y tiran de las barras, el clic izquierdo (o saltar) chuta y
 * agacharse suelta la mesa. El personaje no se mueve mientras tanto.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class FutbolinCliente {
    public static FutbolinBlockEntity jugando;
    private static boolean chut;
    private static int ultimo = -1, cuenta;

    private FutbolinCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        jugando = null;
        if (p == null || mc.level == null) return;
        for (FutbolinBlockEntity be : Visibles.FUTBOLIN)
            if (!be.isRemoved() && be.getLevel() == mc.level && be.lado(p.getUUID()) >= 0 && p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 49) jugando = be;
        if (jugando == null) {
            ultimo = -1;
            return;
        }
        if (mc.screen != null) return;
        int m = 0;
        if (mc.options.keyUp.isDown()) m |= 1;
        if (mc.options.keyDown.isDown()) m |= 2;
        if (chut || mc.options.keyJump.isDown()) m |= 4;
        chut = false;
        if (mc.options.keyShift.isDown()) {
            ModNetwork.CHANNEL.sendToServer(new FutbolinMandoPacket(jugando.getBlockPos(), 8));
            jugando.jug[jugando.lado(p.getUUID())] = null;
            jugando = null;
            p.displayClientMessage(Component.translatable("futbolin.miscelanea.suelta").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (m != ultimo || ++cuenta >= 10) {
            ModNetwork.CHANNEL.sendToServer(new FutbolinMandoPacket(jugando.getBlockPos(), m));
            ultimo = m;
            cuenta = 0;
        }
        // los controles se ven en el panel de HUD de la mesa (PanelJuego)
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

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        if (jugando == null || !e.isAttack()) return;
        chut = true;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
}
