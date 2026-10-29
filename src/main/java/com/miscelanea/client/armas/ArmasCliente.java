package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.EscopetaItem;
import com.miscelanea.armas.GanchoPacket;
import com.miscelanea.armas.LanzacohetesItem;
import com.miscelanea.armas.Municion;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clic izquierdo con la superescopeta = gancho (mientras lo mantienes). Y el contador de munición junto a la mira. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ArmasCliente {
    private static boolean pulsado;

    private ArmasCliente() {}

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        var p = Minecraft.getInstance().player;
        if (p == null || !e.isAttack() || !(p.getMainHandItem().getItem() instanceof EscopetaItem)) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (!pulsado) {
            pulsado = true;
            ModNetwork.CHANNEL.sendToServer(new GanchoPacket(true));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !pulsado) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyAttack.isDown() || !(mc.player.getMainHandItem().getItem() instanceof EscopetaItem)) {
            pulsado = false;
            if (mc.player != null) ModNetwork.CHANNEL.sendToServer(new GanchoPacket(false));
        }
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ItemStack s = mc.player.getMainHandItem();
        boolean creativo = mc.player.getAbilities().instabuild;
        int x = w / 2 + 12, y = h / 2 + 8;
        if (s.getItem() instanceof EscopetaItem) {
            String n = creativo ? "∞" : String.valueOf(Municion.cuenta(mc.player, ModItems.CARTUCHOS.get()));
            g.drawString(mc.font, "▮▮ " + n, x, y, 0xFFE04A3A, true);
            int espera = s.hasTag() ? s.getTag().getInt(EscopetaItem.ESPERA) : 0;
            boolean fuera = s.hasTag() && s.getTag().getBoolean(EscopetaItem.GANCHO);
            int col = fuera ? 0xFFFFB040 : espera > 0 ? 0xFF707070 : 0xFFFF8A20;
            g.drawString(mc.font, "⚓", x, y + 10, col, true);
            if (espera > 0) g.fill(x + 10, y + 13, x + 10 + espera, y + 15, 0xA0FF8A20);
        } else if (s.getItem() instanceof LanzacohetesItem) {
            int c = LanzacohetesItem.cargador(s);
            String n = creativo ? "∞" : String.valueOf(Municion.cuenta(mc.player, ModItems.COHETE.get()));
            for (int i = 0; i < LanzacohetesItem.MAX; i++)
                g.fill(x + i * 5, y, x + i * 5 + 3, y + 8, i < c ? 0xFFE8E8DC : 0x60FFFFFF);
            g.drawString(mc.font, n, x + LanzacohetesItem.MAX * 5 + 3, y, 0xFFE8B420, true);
        }
    }
}
