package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.efectos.CollarPacket;
import com.miscelanea.tortura.efectos.Collares;
import com.miscelanea.tortura.efectos.DetonadorItem;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Collar explosivo en el cliente: estado de cada entidad que lo lleva (lo manda el servidor), la cuenta atrás local, los
 * pitidos (Dead Money: cada vez más seguidos y agudos al alejarse), el parpadeo de la luz y el HUD del que lo lleva
 * (señal, distancia, alarma) y del que mantiene el detonador.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CollarCliente {
    private CollarCliente() {}

    public static final class Estado {
        public int alarma = -1, alarmaMax = Collares.ALARMA, detona = -1, errores, senal, radio = Collares.RADIO;
        public float dist = -1F;
        long ultimoPitido;
    }

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();
    private static long reloj;

    public static Estado estado(int id) {
        return ESTADOS.get(id);
    }

    public static void recibir(CollarPacket p) {
        if (!p.tiene) {
            ESTADOS.remove(p.entidad);
            return;
        }
        Estado e = ESTADOS.computeIfAbsent(p.entidad, k -> new Estado());
        e.alarma = p.alarma;
        if (p.alarmaMax > 0) e.alarmaMax = p.alarmaMax;
        e.detona = p.detona;
        e.errores = p.errores;
        e.senal = p.senal;
        e.radio = p.radio;
        if (p.dist >= 0 || p.senal != Collares.SENAL_OK) e.dist = p.dist;
    }

    /** Ticks entre pitidos (0 = no pita). */
    private static int intervalo(Estado e) {
        if (e.detona >= 0) return 0;
        if (e.alarma >= 0) {
            float q = Mth.clamp(e.alarma / (float) Math.max(1, e.alarmaMax), 0F, 1F);
            return Math.max(2, Math.round(2 + 18 * q * q));
        }
        return e.errores > 0 ? 30 : 0;
    }

    /** Lo encendida que está la luz (0..1). */
    public static float luz(Estado e, LivingEntity ent, float pt) {
        if (e.detona >= 0) return 1F;
        int iv = intervalo(e);
        float t = (reloj - e.ultimoPitido) + pt;
        if (iv > 0) return t < Math.max(1.2F, iv * 0.4F) ? 1F : 0F;
        // en reposo, un destello corto cada 2 s
        return (ent.tickCount + ent.getId() * 7) % 40 < 3 ? 1F : 0F;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            ESTADOS.clear();
            return;
        }
        if (mc.isPaused()) return;
        reloj++;
        for (Iterator<Map.Entry<Integer, Estado>> it = ESTADOS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Estado> en = it.next();
            Estado e = en.getValue();
            Entity ent = level.getEntity(en.getKey());
            if (e.alarma > 0) e.alarma--;
            if (e.detona > 0) e.detona--;
            if (!(ent instanceof LivingEntity le)) {
                // entidad que ya no está (descargada, muerta): se quita a los pocos segundos de no verla
                if (reloj % 100 == 0) it.remove();
                continue;
            }
            int iv = intervalo(e);
            if (iv > 0 && reloj - e.ultimoPitido >= iv) {
                e.ultimoPitido = reloj;
                float q = e.alarma >= 0 ? 1F - Mth.clamp(e.alarma / (float) Math.max(1, e.alarmaMax), 0F, 1F) : 0F;
                float vol = le == mc.player ? 0.9F : 0.6F;
                level.playLocalSound(le.getX(), le.getEyeY() - 0.3, le.getZ(), EfectosRegistro.COLLAR_PITIDO.get(), SoundSource.PLAYERS, vol,
                        1.0F + 0.5F * q, false);
            }
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        ESTADOS.clear();
    }

    // ------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        EfectosCliente.destello(g, w, h, pt);
        detonador(mc, p, g, w, h, pt);
        Estado e = ESTADOS.get(p.getId());
        if (e == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float t = reloj + pt;
        int cx = w / 2, y = 6;
        if (e.detona >= 0) {
            // cuenta final: rojo que sube, sin destellos
            float k = 1F - Mth.clamp(e.detona / (float) Collares.DETONA, 0F, 1F);
            g.fill(0, 0, w, h, ((int) (40 + 120 * k) << 24) | 0x900000);
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.collar.detona").withStyle(ChatFormatting.BOLD), cx, h * 0.35F,
                    2.2F, 0xFFFFF0E0, true);
            return;
        }
        boolean alarma = e.alarma >= 0;
        // panel de estado arriba
        int ancho = 150, alto = alarma ? 44 : 22;
        Dibujo.panelMetal(g, cx - ancho / 2, y, cx + ancho / 2, y + alto, alarma ? 0xFF3A1414 : 0xFF2A2C30);
        float frac = e.dist >= 0 && e.radio > 0 ? (float) (e.dist / e.radio) : 0F;
        int col;
        Component linea;
        switch (e.senal) {
            case Collares.SENAL_NADA -> {
                col = 0xFF9A9AA0;
                linea = Component.translatable("hud.miscelanea.collar.sin_emisor");
            }
            case Collares.SENAL_OTRA_DIM -> {
                col = 0xFFFF4A3A;
                linea = Component.translatable("hud.miscelanea.collar.otra_dim");
            }
            default -> {
                col = alarma ? 0xFFFF4A3A : frac > 0.75F ? 0xFFFFC840 : 0xFF6CF07A;
                linea = Component.translatable(alarma ? "hud.miscelanea.collar.perdida" : frac > 0.75F ? "hud.miscelanea.collar.debil"
                        : "hud.miscelanea.collar.ok", Math.round(Math.max(0, e.dist)), e.radio);
            }
        }
        // "luz" del panel: la misma que la del collar
        Dibujo.brillo(g, cx - ancho / 2F + 10, y + 11, 3F, luz(e, p, pt) > 0.5F ? 3 : 0, luz(e, p, pt) > 0.5F ? 0xFFFF2A1A : 0xFF4A1010);
        Dibujo.texto(g, mc.font, linea, cx + 6, y + 7, 0.85F, col, true);
        // barra de señal (llena = cerca)
        if (e.senal == Collares.SENAL_OK || e.senal == Collares.SENAL_BALIZA) {
            int bx = cx - ancho / 2 + 20, bw = ancho - 30;
            Dibujo.barra(g, bx, y + 16, bw, 2, 1F - Mth.clamp(frac, 0F, 1F), col, Dibujo.mezcla(col, 0xFF000000, 0.4F));
        }
        if (alarma) {
            float seg = e.alarma / 20F;
            String s = String.format("%04.1f", Math.max(0F, seg - pt / 20F));
            float pulso = 0.5F + 0.5F * Mth.sin(t * 0.5F);
            Dibujo.texto(g, mc.font, Component.literal(s).withStyle(ChatFormatting.BOLD), cx, y + 23, 1.7F,
                    Dibujo.mezcla(0xFFFF3020, 0xFFFFD0C0, pulso * 0.4F), true);
            // viñeta roja que late despacio (≤ 1,6 Hz)
            float q = 1F - Mth.clamp(e.alarma / (float) Math.max(1, e.alarmaMax), 0F, 1F);
            int a = (int) ((0.10F + 0.22F * q) * (0.6F + 0.4F * pulso) * 255);
            int bordes = Math.max(12, h / 6);
            g.fillGradient(0, 0, w, bordes, (a << 24) | 0xB00000, 0x00B00000);
            g.fillGradient(0, h - bordes, w, h, 0x00B00000, (a << 24) | 0xB00000);
        }
        if (e.errores > 0)
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.collar.errores", e.errores), cx, y + alto + 3, 0.7F, 0xFFFF6A5A, true);
    }

    /** Mantener el detonador: anillo que se llena y "MANTÉN PARA DETONAR". */
    private static void detonador(Minecraft mc, LocalPlayer p, GuiGraphics g, int w, int h, float pt) {
        if (!p.isUsingItem()) return;
        ItemStack st = p.getUseItem();
        if (!(st.getItem() instanceof DetonadorItem)) return;
        float t = Mth.clamp((st.getUseDuration() - p.getUseItemRemainingTicks() + pt) / DetonadorItem.CARGA, 0F, 1F);
        float cx = w / 2F, cy = h / 2F;
        Dibujo.arco(g, cx, cy, 13, 17, 0, 360, 0x80000000);
        Dibujo.arco(g, cx, cy, 13.5F, 16.5F, 0, 360 * t, Dibujo.mezcla(0xFFFFB020, 0xFFFF2010, t));
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.detonador.manten").withStyle(ChatFormatting.BOLD), cx, cy + 22, 0.9F,
                0xFFFFD0A0, true);
    }
}
