package com.miscelanea.client.trampolin;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import com.miscelanea.trampolin.TrampolinFisica;
import com.miscelanea.trampolin.TrucoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Cliente del trampolín: momento del salto, combos, giros (también los de otros) y el aviso en pantalla. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TrampolinCliente {
    /** entidad → {inicio (tick de juego), duración, giros} */
    static final Map<Integer, long[]> TRUCOS = new HashMap<>();
    private static boolean saltoAntes;
    private static int combo, avisoT;
    private static String aviso = "";

    private TrampolinCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.START || mc.player == null) return;
        TrampolinFisica.local = mc.player.getUUID();
        boolean salto = mc.options.keyJump.isDown();
        if (salto && !saltoAntes) TrampolinFisica.ultimoSalto = System.currentTimeMillis();
        saltoAntes = salto;
        if (avisoT > 0) avisoT--;
        if (mc.player.onGround() && mc.player.getDeltaMovement().y > -0.1 && !(mc.level.getBlockState(mc.player.blockPosition().below()).getBlock()
                instanceof com.miscelanea.trampolin.TrampolinBlock) && !(mc.level.getBlockState(mc.player.blockPosition()).getBlock() instanceof com.miscelanea.trampolin.TrampolinBlock))
            combo = 0;
    }

    public static void rebote(boolean perfecto, double v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        combo = perfecto ? combo + 1 : 0;
        mc.player.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.TRAMPOLIN_BOING.get(), SoundSource.PLAYERS,
                0.8F, 0.8F + (float) v * 0.5F, false);
        if (perfecto) {
            mc.player.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS,
                    0.5F, 1.0F + Math.min(combo, 8) * 0.08F, false);
            int giros = combo >= 4 ? 2 : combo >= 2 ? 1 : 0;
            aviso = giros == 0 ? I18n.get("hud.miscelanea.trampolin.perfecto") : I18n.get("hud.miscelanea.trampolin.giro", giros * 360) + "  x" + combo;
            avisoT = 40;
            if (giros > 0) {
                int dur = (int) Math.max(12, 2 * v / 0.08 * 0.9);
                truco(mc.player.getId(), giros, dur);
                ModNetwork.CHANNEL.sendToServer(new TrucoPacket(0, giros, dur));
            }
        }
    }

    public static void truco(int entidad, int giros, int duracion) {
        if (Minecraft.getInstance().level == null) return;
        TRUCOS.put(entidad, new long[]{Minecraft.getInstance().level.getGameTime(), duracion, giros});
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (avisoT <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        float a = Math.min(1F, avisoT / 10F);
        int col = ((int) (a * 255) << 24) | 0xFFE060;
        g.pose().pushPose();
        g.pose().translate(w / 2F, h / 2F - 40, 0);
        float s = 1.4F + (40 - avisoT) * 0.01F;
        g.pose().scale(s, s, 1);
        g.drawString(mc.font, aviso, -mc.font.width(aviso) / 2, 0, col, true);
        g.pose().popPose();
    }
}
