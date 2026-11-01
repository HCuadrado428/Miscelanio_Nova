package com.miscelanea.client.ocarina;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.ocarina.Cancion;
import com.miscelanea.ocarina.CancionPacket;
import com.miscelanea.ocarina.NotaPacket;
import com.miscelanea.ocarina.OcarinaItem;
import com.miscelanea.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Tocar la ocarina: teclas 1-5 mientras la tienes en la boca (clic derecho mantenido), pentagrama y reconocimiento de canciones. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class OcarinaCliente {
    private static final List<Integer> NOTAS = new ArrayList<>();
    private static final List<Integer> EDAD = new ArrayList<>();        // ticks desde que se tocó cada nota (para la animación)
    private static Cancion mostrada;
    private static int destello, sinTocar, visible, visibleO;

    private OcarinaCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean toca = OcarinaItem.tocando(p) && mc.screen == null;
        if (e.phase == TickEvent.Phase.START) {
            // las teclas 1-5 tocan notas en vez de cambiar de ranura (se consumen antes que el juego las lea)
            if (OcarinaItem.tocando(p))
                for (int i = 0; i < 5; i++)
                    while (mc.options.keyHotbarSlots[i].consumeClick()) if (toca) tocar(p, i);
            return;
        }
        visibleO = visible;
        visible = Mth.clamp(visible + (toca || destello > 0 ? 2 : -1), 0, 6);
        for (int i = 0; i < EDAD.size(); i++) EDAD.set(i, EDAD.get(i) + 1);
        if (destello > 0 && --destello == 0) {
            mostrada = null;
            NOTAS.clear();
            EDAD.clear();
        }
        if (!toca && ++sinTocar > 60 && destello == 0) {
            NOTAS.clear();
            EDAD.clear();
        }
    }

    private static void tocar(LocalPlayer p, int n) {
        if (destello > 0) return;      // mientras suena la canción reconocida no se toca
        sinTocar = 0;
        p.level().playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.OCARINA[n].get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        ModNetwork.CHANNEL.sendToServer(new NotaPacket(n));
        NOTAS.add(n + 1);
        EDAD.add(0);
        while (NOTAS.size() > 8) {
            NOTAS.remove(0);
            EDAD.remove(0);
        }
        for (Cancion c : Cancion.values())
            if (c.coincide(NOTAS)) {
                mostrada = c;
                destello = 90;
                // deja en el pentagrama solo las notas de la canción
                while (NOTAS.size() > c.notas.length) {
                    NOTAS.remove(0);
                    EDAD.remove(0);
                }
                ModNetwork.CHANNEL.sendToServer(new CancionPacket(c.ordinal()));
                break;
            }
    }

    /** En primera persona, mientras tocas, la ocarina se centra delante de la cara. */
    public static boolean manoPrimeraPersona(PoseStack ps, LocalPlayer p, HumanoidArm brazo, ItemStack st, float pt, float equip) {
        if (!OcarinaItem.tocando(p)) return false;
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        float t = Math.min(1F, (p.getTicksUsingItem() + pt) / 6F);
        t = t * t * (3 - 2 * t);
        ps.translate(i * Mth.lerp(t, 0.56F, 0.12F), Mth.lerp(t, -0.52F, -0.36F), Mth.lerp(t, -0.72F, -0.5F));
        ps.mulPose(Axis.YP.rotationDegrees(i * Mth.lerp(t, 0F, 25F)));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(t, 0F, 8F)));
        return true;
    }

    private static void circulo(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.5));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, col);
        }
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float a = Mth.lerp(pt, visibleO, visible) / 6F;
        if (a <= 0) return;
        int pw = 190, ph = 50;
        int x0 = (w - pw) / 2, y0 = h - 62 - ph - (int) ((1 - a) * 12);
        int al = (int) (a * 255) << 24;
        boolean exito = mostrada != null;
        float brillo = exito ? 0.5F + 0.5F * Mth.sin((mc.player.tickCount + pt) * 0.4F) : 0;
        // marco: pergamino oscuro con borde dorado
        int borde = exito ? mezcla(0xC89B3C, 0xFFF0A0, brillo) : 0x8A6A3A;
        g.fill(x0 - 2, y0 - 2, x0 + pw + 2, y0 + ph + 2, al | borde);
        g.fill(x0, y0, x0 + pw, y0 + ph, ((int) (a * 0xD0) << 24) | 0x1A1420);
        // pentagrama: 5 líneas
        int ly0 = y0 + 12, sep = 6;
        for (int k = 0; k < 5; k++) g.fill(x0 + 8, ly0 + k * sep, x0 + pw - 8, ly0 + k * sep + 1, ((int) (a * 0x90) << 24) | 0xE8DCC0);
        g.drawString(mc.font, "♫", x0 + 11, ly0 + 7, al | 0xE8DCC0, false);
        // notas: la 1 en la línea de abajo, la 5 en la de arriba
        for (int i = 0; i < NOTAS.size(); i++) {
            int n = NOTAS.get(i);
            int edad = EDAD.get(i);
            int x = x0 + 32 + i * 19;
            int y = ly0 + (5 - n) * sep;
            float rebote = Math.max(0, 1 - (edad + pt) / 5F);
            y -= (int) (rebote * 4);
            int col = OcarinaItem.COLORES[n - 1];
            if (exito) col = mezcla(col, 0xFFFFFF, brillo * 0.5F);
            circulo(g, x, y, 4, al | 0x101010);
            circulo(g, x, y, 3, al | col);
            g.fill(x + 3, y - 13, x + 4, y, al | 0x101010);
            g.fill(x - 1, y - 2, x + 1, y - 1, al | 0xFFFFFF);
            g.drawString(mc.font, Integer.toString(n), x - 2, ly0 + 26, al | col, false);
        }
        if (exito) {
            String nom = I18n.get(mostrada.clave());
            int tw = mc.font.width(nom);
            g.fill(w / 2 - tw / 2 - 6, y0 - 17, w / 2 + tw / 2 + 6, y0 - 4, al | 0x2A1A08);
            g.drawString(mc.font, nom, w / 2 - tw / 2, y0 - 14, al | 0xFFD860, true);
        } else if (NOTAS.isEmpty()) {
            String s = I18n.get("hud.miscelanea.ocarina");
            g.drawString(mc.font, s, w / 2 - mc.font.width(s) / 2, ly0 + 9, al | 0xBFAF90, false);
        }
    }

    private static int mezcla(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), gg = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bb = (int) Mth.lerp(k, a & 255, b & 255);
        return (r << 16) | (gg << 8) | bb;
    }
}
