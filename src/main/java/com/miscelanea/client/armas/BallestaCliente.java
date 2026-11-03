package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.Ballesta;
import com.miscelanea.armas.BallestaItem;
import com.miscelanea.armas.BallestaRecargaPacket;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Ballesta de repetición en el cliente: tecla de recargar, retroceso de la mira en cada virote y contador del HUD. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class BallestaCliente {
    public static final KeyMapping RECARGAR = new KeyMapping("key.miscelanea.recargar_ballesta", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories." + Miscelanea.MOD_ID);
    private static int tirosVistos = -1;

    private BallestaCliente() {}

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void teclas(RegisterKeyMappingsEvent e) {
            e.register(RECARGAR);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) return;
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof BallestaItem)) {
            tirosVistos = -1;
            return;
        }
        while (RECARGAR.consumeClick()) {
            if (mc.screen == null && !Ballesta.recargando(s) && Ballesta.cargados(s) < Ballesta.MAX)
                ModNetwork.CHANNEL.sendToServer(new BallestaRecargaPacket());
        }
        // retroceso: cuando el servidor cuenta un tiro nuevo
        int tiros = s.hasTag() ? s.getTag().getInt(Ballesta.TIROS) : 0;
        if (tirosVistos >= 0 && tiros > tirosVistos) {
            p.setXRot(p.getXRot() - 1.4F);
            p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5F) * 0.8F);
        }
        tirosVistos = tiros;
    }

    /** Contador junto a la mira: virotes en el cargador ("4/6") y flechas de reserva. */
    public static void hud(GuiGraphics g, int x, int y) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack s = mc.player.getMainHandItem();
        int n = Ballesta.cargados(s);
        boolean recargando = Ballesta.recargando(s);
        for (int i = 0; i < Ballesta.MAX; i++) {
            int col = recargando ? 0x80B0B0B0 : i < n ? 0xFFD9B26A : 0x50FFFFFF;
            g.fill(x + i * 4, y + 1, x + i * 4 + 2, y + 8, col);
            if (i < n && !recargando) g.fill(x + i * 4, y, x + i * 4 + 2, y + 1, 0xFF9A9CA4);   // punta de hierro
        }
        int tx = x + Ballesta.MAX * 4 + 3;
        String txt = n + "/" + Ballesta.MAX;
        g.drawString(mc.font, txt, tx, y, n == 0 ? 0xFFE0533A : 0xFFE8D8B0, true);
        String reserva = mc.player.getAbilities().instabuild ? "∞" : String.valueOf(Ballesta.flechas(mc.player));
        g.drawString(mc.font, "(" + reserva + ")", tx + mc.font.width(txt) + 3, y, 0xFF8C8C8C, true);
    }
}
