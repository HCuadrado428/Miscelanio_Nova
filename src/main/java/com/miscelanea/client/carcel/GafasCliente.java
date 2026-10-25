package com.miscelanea.client.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.GafasItem;
import com.miscelanea.network.GafasPacket;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Tecla N para las gafas y el filtro verde con grano y viñeta. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class GafasCliente {
    public static final KeyMapping TECLA = new KeyMapping("key." + Miscelanea.MOD_ID + ".gafas",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories." + Miscelanea.MOD_ID);

    private GafasCliente() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) return;
        while (TECLA.consumeClick()) {
            if (Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof GafasItem) {
                ModNetwork.CHANNEL.sendToServer(new GafasPacket());
            }
        }
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        var casco = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(casco.getItem() instanceof GafasItem) || !GafasItem.encendidas(casco)) return;

        RenderSystem.enableBlend();
        // Tinte verde
        g.fill(0, 0, w, h, 0x3A1FFF2A);
        // Líneas de barrido
        for (int y = (int) (mc.level.getGameTime() % 4); y < h; y += 4) g.fill(0, y, w, y + 1, 0x14000000);
        // Grano
        var r = mc.level.random;
        for (int i = 0; i < 220; i++) {
            int x = r.nextInt(w), y = r.nextInt(h);
            g.fill(x, y, x + 1, y + 1, r.nextBoolean() ? 0x30D8FFD0 : 0x30000000);
        }
        // Viñeta de los dos oculares (bordes oscuros escalonados)
        for (int i = 0; i < 12; i++) {
            int a = (int) (0xC0 * (1 - i / 12.0F));
            int m = i * Math.max(2, w / 160);
            int col = ((a / 6) << 24) | 0x020802;
            g.fill(0, 0, w, m, col);
            g.fill(0, h - m, w, h, col);
            g.fill(0, 0, m * 2, h, col);
            g.fill(w - m * 2, 0, w, h, col);
        }
        // Batería: barrita en la esquina, sin texto (en el Programa la pinta su propia interfaz)
        if (com.miscelanea.client.programa.ProgramaHud.enPrograma()) {
            RenderSystem.disableBlend();
            return;
        }
        int b = GafasItem.bateria(casco);
        int largo = 40, x0 = w - largo - 10, y0 = h - 14;
        g.fill(x0 - 1, y0 - 1, x0 + largo + 1, y0 + 4, 0x80000000);
        int lleno = largo * b / GafasItem.BATERIA_MAX;
        g.fill(x0, y0, x0 + lleno, y0 + 3, b < GafasItem.BATERIA_MAX / 5 && (mc.level.getGameTime() / 8) % 2 == 0 ? 0xFFFF5040 : 0xFF8CFF6A);
        RenderSystem.disableBlend();
    }
}
