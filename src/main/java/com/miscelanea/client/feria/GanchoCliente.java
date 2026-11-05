package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.miscelanea.feria.GanchoPacket;
import com.miscelanea.feria.MaquinaAltaBlockEntity;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mandos de la máquina de gancho: WASD mueve la garra (en este cliente, sin esperar al servidor) y espacio la suelta. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class GanchoCliente {
    static BlockPos pos;
    static boolean activo;
    static float x, z, ix, iz, ex = 99, ez = 99;
    static boolean salto;
    static int ticks;

    private GanchoCliente() {}

    public static void abrir(BlockPos p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(p) instanceof MaquinaAltaBlockEntity be)) return;
        pos = p;
        activo = true;
        x = be.gx;
        z = be.gz;
        ticks = 0;
    }

    /** Posición de la garra si la maneja este cliente; null si no. */
    static float[] local(BlockPos p) {
        return activo && p.equals(pos) ? new float[]{x, z} : null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !activo) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.level.getBlockEntity(pos) instanceof MaquinaAltaBlockEntity be)
                || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 25) {
            activo = false;
            return;
        }
        ticks++;
        if (ticks > 10 && (be.fase != 1 || !be.activo)) {
            activo = false;
            return;
        }
        float v = 0.012F;
        x = Math.max(-MaquinaAltaBlockEntity.LIMITE, Math.min(MaquinaAltaBlockEntity.LIMITE, x + ix * v));
        z = Math.max(-MaquinaAltaBlockEntity.LIMITE, Math.min(MaquinaAltaBlockEntity.LIMITE, z + iz * v));
        if (ticks % 2 == 0 && (Math.abs(x - ex) > 1e-3 || Math.abs(z - ez) > 1e-3)) {
            ex = x;
            ez = z;
            ModNetwork.CHANNEL.sendToServer(new GanchoPacket(GanchoPacket.MOVER, pos, Math.round(x * 1000), Math.round(z * 1000)));
        }
        if (salto && ticks > 5) {
            ModNetwork.CHANNEL.sendToServer(new GanchoPacket(GanchoPacket.MOVER, pos, Math.round(x * 1000), Math.round(z * 1000)));
            ModNetwork.CHANNEL.sendToServer(new GanchoPacket(GanchoPacket.SOLTAR, pos, 0, 0));
            activo = false;
        }
    }

    @SubscribeEvent
    public static void mover(MovementInputUpdateEvent e) {
        if (!activo) return;
        var in = e.getInput();
        ix = in.leftImpulse;
        iz = in.forwardImpulse;
        salto = in.jumping;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        if (!activo) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof MaquinaAltaBlockEntity be)) return;
        var g = e.getGuiGraphics();
        int W = g.guiWidth(), H = g.guiHeight();
        long quedan = Math.max(0, (MaquinaAltaBlockEntity.MOVER - (mc.level.getGameTime() - be.inicio)) / 20);
        var o = mc.options;
        String s = I18n.get("miscelanea.gancho.mandos", quedan, o.keyUp.getTranslatedKeyMessage().getString() + o.keyLeft.getTranslatedKeyMessage().getString()
                + o.keyDown.getTranslatedKeyMessage().getString() + o.keyRight.getTranslatedKeyMessage().getString(), o.keyJump.getTranslatedKeyMessage().getString());
        int w = mc.font.width(s) + 16;
        g.fill(W / 2 - w / 2, H - 78, W / 2 + w / 2, H - 62, 0xC0301040);
        g.fill(W / 2 - w / 2, H - 78, W / 2 + w / 2, H - 77, 0xFFF0A0D0);
        g.drawCenteredString(mc.font, s, W / 2, H - 74, quedan <= 5 && (mc.level.getGameTime() / 5) % 2 == 0 ? 0xFFFF6060 : 0xFFFFFFFF);
    }
}
