package com.miscelanea.client.voz;

import com.miscelanea.Miscelanea;
import com.miscelanea.voz.VozEntrada;
import com.miscelanea.voz.VozEstadoPacket;
import com.miscelanea.voz.VozPorEfecto;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente de las voces: guarda qué voces tiene cada jugador (lo manda el servidor, {@link VozEstadoPacket}), monta
 * la cadena de procesadores de cada hablante y la aplica a su audio cuando lo pide el plugin de Simple Voice Chat
 * ({@code plugin.MiscelaneaVozPlugin}, que es quien llama a {@link #procesar}). Si el voicechat no está, nadie llama
 * y todo esto queda en un mapa que no molesta. Además pinta un aviso pequeño en el HUD cuando TU voz suena distinta
 * (a ti te oyes normal: el cambio lo aplican los demás).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class VozCliente {
    private VozCliente() {}

    private static final ResourceLocation ICONO = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/voz_icono.png");

    /** Estado de un hablante. Las entradas las cambia el hilo del juego; la cadena solo la toca el hilo de audio. */
    private static final class Hablante {
        volatile List<VozEntrada> entradas = List.of();
        volatile int version;
        int versionCadena = -1;
        final List<ProcesadorVoz> cadena = new ArrayList<>();
        final List<Integer> amps = new ArrayList<>();
    }

    private static final Map<UUID, Hablante> HABLANTES = new ConcurrentHashMap<>();
    /** Último momento (ms) en que el plugin vio salir audio del micro: para animar el icono del HUD. */
    private static volatile long hablandoHasta;
    private static volatile boolean voicechat;

    // ------------------------------------------------------------------ estado que manda el servidor
    public static void recibir(VozEstadoPacket p) {
        if (p.voces.isEmpty()) {
            HABLANTES.remove(p.jugador);
            return;
        }
        Hablante h = HABLANTES.computeIfAbsent(p.jugador, u -> new Hablante());
        h.entradas = List.copyOf(p.voces);
        h.version++;
    }

    public static List<VozEntrada> vocesDe(UUID jugador) {
        Hablante h = HABLANTES.get(jugador);
        return h == null ? List.of() : h.entradas;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        HABLANTES.clear();
    }

    // ------------------------------------------------------------------ audio (hilo del voicechat)
    /** El plugin avisa de que está activo (para el HUD). */
    public static void voicechatActivo() {
        voicechat = true;
    }

    public static void hablando() {
        hablandoHasta = System.currentTimeMillis() + 250;
    }

    /**
     * Aplica al fotograma las voces del hablante. Devuelve {@code false} si no había nada que hacer (el audio queda
     * intacto). Se llama desde el hilo de audio del voicechat: no toca nada del juego.
     */
    public static boolean procesar(UUID hablante, short[] frame) {
        if (frame == null) return false;
        Hablante h = HABLANTES.get(hablante);
        if (h == null) return false;
        int v = h.version;
        if (h.versionCadena != v) {
            h.cadena.clear();
            h.amps.clear();
            for (VozEntrada e : h.entradas) {
                ProcesadorVoz p = Voces.crear(e.id());
                if (p == null) continue;
                h.cadena.add(p);
                h.amps.add(e.amp());
            }
            h.versionCadena = v;
        }
        if (h.cadena.isEmpty()) return false;
        try {
            for (int i = 0; i < h.cadena.size(); i++) h.cadena.get(i).procesar(frame, h.amps.get(i));
        } catch (RuntimeException ex) {
            // un fallo en un procesador no puede tirar el hilo de audio: se quita la cadena y se sigue con la voz normal
            Miscelanea.LOGGER.warn("[miscelanea] Fallo procesando la voz de {}; se deja normal", hablante, ex);
            h.cadena.clear();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ HUD: aviso de que tu voz suena distinta
    private static final String[] VOCES_CONOCIDAS = {VozPorEfecto.HELIO, VozPorEfecto.TARTAMUDEO, VozPorEfecto.VENTILADOR,
            VozPorEfecto.LENGUA_TRAPO, VozPorEfecto.GRAVE, VozPorEfecto.AGUDO, VozPorEfecto.SUSURRO, VozPorEfecto.FANTASMA,
            VozPorEfecto.GUTURAL, VozPorEfecto.AMORTIGUADO};

    /** Voces del jugador local, por sus propios efectos (sin esperar al servidor). */
    static List<String> vocesPropias(LocalPlayer p) {
        List<String> out = new ArrayList<>();
        for (MobEffectInstance m : p.getActiveEffects()) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(m.getEffect());
            if (id == null) continue;
            String v = VozPorEfecto.vozDe(id);
            if (v == null) continue;
            for (String s : v.split(",")) {
                s = s.trim();
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
        }
        return out;
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        List<String> voces = vocesPropias(p);
        if (voces.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (String v : voces) {
            if (sb.length() > 0) sb.append(", ");
            boolean conocida = false;
            for (String c : VOCES_CONOCIDAS) if (c.equals(v)) conocida = true;
            sb.append(conocida ? Component.translatable("miscelanea.voz." + v).getString() : v);
        }
        String texto = Component.translatable(voicechat ? "miscelanea.voz.hud" : "miscelanea.voz.hud.sin", sb.toString()).getString();
        boolean habla = System.currentTimeMillis() < hablandoHasta;
        float pulso = habla ? 0.75F + 0.25F * Mth.sin((p.tickCount + pt) * 0.9F) : 1F;
        int x = 4, y = 4;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int alfa = (int) ((voicechat ? 0.95F : 0.6F) * 255);
        g.setColor(1F, 1F, 1F, alfa / 255F * pulso);
        g.blit(ICONO, x, y, 0, 0, 16, 16, 16, 16);
        g.setColor(1F, 1F, 1F, 1F);
        int col = voicechat ? (habla ? 0xFFF2E35C : 0xFFE8E8E8) : 0xFF9A9A9A;
        g.drawString(mc.font, texto, x + 20, y + 4, (alfa << 24) | (col & 0xFFFFFF), true);
        RenderSystem.disableBlend();
    }
}
