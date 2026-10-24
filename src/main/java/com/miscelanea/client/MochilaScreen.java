package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.Deposito;
import com.miscelanea.aspira.Reserva;
import com.miscelanea.network.MochilaAccionPacket;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ventana de la mochila del Aspirafantasmas (tecla B con la mochila puesta).
 * Tres tarros con las capturas (se ven los bichos dentro), el tanque de pociones para expulsar y la Reserva.
 * Sin texto en pantalla: todo con iconos; al pasar el ratón sale la descripción.
 */
public class MochilaScreen extends Screen {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/mochila.png");
    private static final int W = 232, H = 190;
    private static final int FILAS = 4, COLUMNAS = 2;

    private int x0, y0;
    private int scroll;
    private final Map<String, LivingEntity> modelos = new HashMap<>();

    public MochilaScreen() {
        super(Component.translatable("gui.miscelanea.mochila"));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack mochila() {
        return minecraft.player == null ? ItemStack.EMPTY : Deposito.mochila(minecraft.player);
    }

    @Override
    public void tick() {
        if (mochila().isEmpty()) onClose();
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        ItemStack mochila = mochila();
        if (mochila.isEmpty()) return;
        g.blit(TEX, x0, y0, 0, 0, W, H);
        Component tip = null;

        // --- tarros
        for (int i = 0; i < Deposito.CAPACIDAD; i++) {
            int jx = x0 + 8 + i * 44, jy = y0 + 10;
            CompoundTag c = Deposito.captura(mochila, i);
            if (c != null) {
                LivingEntity e = modelo(c);
                if (e != null) {
                    float esc = Math.min(22.0F, 40.0F / Math.max(e.getBbHeight(), e.getBbWidth() * 1.2F));
                    g.enableScissor(jx, jy + 5, jx + 40, jy + 59);
                    // flota un poco dentro del tarro
                    int flota = (int) (Math.sin((minecraft.player.tickCount + partial) * 0.08 + i) * 2);
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, jx + 20, jy + 54 + flota, (int) esc,
                            jx + 20 - mx, jy + 30 - my, e);
                    g.disableScissor();
                }
                if (dentro(mx, my, jx, jy, 40, 64)) tip = nombreCaptura(c);
            }
            RenderSystem.enableBlend();
            g.blit(TEX, jx, jy, 128, 192, 40, 64);
            RenderSystem.disableBlend();
            int bx = jx + 12, by = y0 + 78;
            if (c != null) {
                boolean h = dentro(mx, my, bx, by, 16, 16);
                g.blit(TEX, bx, by, h ? 112 : 96, 192, 16, 16);
                g.blit(TEX, bx, by, 0, 192, 16, 16);
                if (h) tip = Component.translatable("gui.miscelanea.soltar");
            }
        }

        // --- tanque
        ItemStack tanque = Deposito.tanque(mochila);
        float carga = Deposito.carga(mochila) / 100.0F;
        if (!tanque.isEmpty() && carga > 0) {
            int color = PotionUtils.getColor(tanque);
            int alto = Math.max(2, Math.round(60 * Mth.clamp(carga, 0, 1)));
            int tx = x0 + 151, tyFondo = y0 + 72;
            g.fill(tx, tyFondo - alto, tx + 16, tyFondo, 0xE0000000 | color);
            g.fill(tx, tyFondo - alto, tx + 16, tyFondo - alto + 1, 0x80FFFFFF);
            // burbujas
            long t = minecraft.player.tickCount;
            for (int k = 0; k < 4; k++) {
                int by = tyFondo - (int) ((t * (1 + k * 0.3) + k * 17) % Math.max(1, alto));
                int bx = tx + 3 + (k * 5) % 11;
                g.fill(bx, by, bx + 2, by + 2, 0x70FFFFFF);
            }
        }
        RenderSystem.enableBlend();
        g.blit(TEX, x0 + 150, y0 + 10, 170, 192, 18, 64);
        RenderSystem.disableBlend();
        if (dentro(mx, my, x0 + 150, y0 + 10, 18, 64)) {
            tip = Component.translatable("gui.miscelanea.tanque", Math.round(carga * 100));
        }
        List<MobEffectInstance> efTanque = tanque.isEmpty() ? List.of() : PotionUtils.getCustomEffects(tanque);
        for (int k = 0; k < Math.min(6, efTanque.size()); k++) {
            int ix = x0 + 177 + (k % 2) * 22, iy = y0 + 11 + (k / 2) * 22;
            MobEffectInstance ef = efTanque.get(k);
            icono(g, ef.getEffect(), ix, iy, 1.0F);
            if (dentro(mx, my, ix - 1, iy - 1, 20, 20)) tip = nombreEfecto(ef.getEffect(), ef.getAmplifier(), ef.getDuration(), false);
        }
        if (!tanque.isEmpty()) {
            int bx = x0 + 150, by = y0 + 78;
            boolean h1 = dentro(mx, my, bx, by, 16, 16), h2 = dentro(mx, my, bx + 20, by, 16, 16);
            g.blit(TEX, bx, by, h1 ? 112 : 96, 192, 16, 16);
            g.blit(TEX, bx, by, 16, 192, 16, 16);
            g.blit(TEX, bx + 20, by, h2 ? 112 : 96, 192, 16, 16);
            g.blit(TEX, bx + 20, by, 32, 192, 16, 16);
            if (h1) tip = Component.translatable("gui.miscelanea.rociar");
            if (h2) tip = Component.translatable("gui.miscelanea.vaciar");
        }

        // --- Reserva
        List<Reserva.Entrada> reserva = Reserva.leer(mochila);
        int porPagina = FILAS * COLUMNAS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, (reserva.size() - porPagina + 1) / 2));
        for (int k = 0; k < porPagina; k++) {
            int idx = k + scroll * 2;
            if (idx >= reserva.size()) break;
            Reserva.Entrada en = reserva.get(idx);
            int col = k % 2, fila = k / 2;
            int rx = x0 + 11 + col * 108, ry = y0 + 103 + fila * 19;
            float alfa = en.pausado() ? 0.35F : 1.0F;
            icono(g, en.efecto(), rx, ry, alfa);
            // barra de tiempo que queda (del color del efecto)
            int bw = 48, bx = rx + 21, by = ry + 6;
            float f = Mth.clamp(en.ticks() / (float) Reserva.maxTicks(en.efecto()), 0, 1);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF0A1410);
            int c = en.efecto().getColor();
            g.fill(bx, by, bx + Math.max(1, Math.round(bw * f)), by + 5, (en.pausado() ? 0x60000000 : 0xFF000000) | c);
            g.fill(bx, by, bx + Math.max(1, Math.round(bw * f)), by + 1, 0x40FFFFFF);
            // potencia: puntitos
            for (int a = 0; a <= Math.min(4, en.amplificador()); a++) g.fill(bx + a * 3, by + 7, bx + a * 3 + 2, by + 9, 0xFFE8D27A);
            if (dentro(mx, my, rx - 1, ry - 1, 72, 20)) tip = nombreEfecto(en.efecto(), en.amplificador(), en.ticks(), en.pausado());
            // botones pequeños: pausa/reanudar, usar ahora, tirar
            int[] us = {en.pausado() ? 11 : 0, 22, 33};
            String[] claves = {en.pausado() ? "gui.miscelanea.reanudar" : "gui.miscelanea.pausar", "gui.miscelanea.usar", "gui.miscelanea.tirar"};
            for (int b = 0; b < 3; b++) {
                int sx = rx + 72 + b * 12, sy = ry + 3;
                boolean h = dentro(mx, my, sx, sy, 11, 11);
                g.blit(TEX, sx, sy, h ? 55 : 44, 210, 11, 11);
                g.blit(TEX, sx, sy, us[b], 210, 11, 11);
                if (h) tip = Component.translatable(claves[b]);
            }
        }
        // flechas de scroll si hay más
        if (scroll > 0) g.fill(x0 + W / 2 - 3, y0 + 101, x0 + W / 2 + 3, y0 + 102, 0xFF7CE07A);
        if ((scroll + FILAS) * 2 < reserva.size()) g.fill(x0 + W / 2 - 3, y0 + 180, x0 + W / 2 + 3, y0 + 181, 0xFF7CE07A);
        if (reserva.isEmpty() && dentro(mx, my, x0 + 8, y0 + 100, 216, 82)) tip = Component.translatable("gui.miscelanea.reserva_vacia");

        super.render(g, mx, my, partial);
        if (tip != null) g.renderTooltip(font, tip, mx, my);
    }

    private static boolean dentro(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void icono(GuiGraphics g, MobEffect ef, int x, int y, float alfa) {
        TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(ef);
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alfa);
        g.blit(x, y, 0, 18, 18, sprite);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private static Component nombreEfecto(MobEffect ef, int amp, int ticks, boolean pausado) {
        Component nombre = ef.getDisplayName().copy();
        if (amp > 0) nombre = Component.empty().append(nombre).append(" ").append(Component.translatable("enchantment.level." + (amp + 1)));
        var c = Component.empty().append(nombre);
        if (ef.isInstantenous()) c.append(Component.literal(" ×" + ticks).withStyle(ChatFormatting.GRAY));
        else c.append(Component.literal(" " + StringUtil.formatTickDuration(ticks)).withStyle(ChatFormatting.GRAY));
        if (pausado) c.append(Component.literal(" ⏸").withStyle(ChatFormatting.YELLOW));
        return c;
    }

    private static Component nombreCaptura(CompoundTag c) {
        EntityType<?> tipo = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(c.getString("id")));
        CompoundTag data = c.getCompound("data");
        if (data.contains("CustomName")) {
            Component custom = Component.Serializer.fromJson(data.getString("CustomName"));
            if (custom != null) return custom;
        }
        return tipo.getDescription();
    }

    /** Mob de muestra para dibujar dentro del tarro (se crea una vez por captura). */
    private LivingEntity modelo(CompoundTag c) {
        String clave = c.toString();
        if (modelos.containsKey(clave)) return modelos.get(clave);
        LivingEntity out = null;
        try {
            EntityType<?> tipo = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(c.getString("id")));
            Entity e = tipo.create(minecraft.level);
            if (e instanceof LivingEntity le) {
                le.load(c.getCompound("data"));
                le.setYRot(0);
                le.yBodyRot = 0;
                le.yHeadRot = 0;
                out = le;
            }
        } catch (Exception ignorada) {
            // Si un mob de otro mod no se deja cargar aquí, el tarro sale vacío pero con su nombre
        }
        modelos.put(clave, out);
        return out;
    }

    // ------------------------------------------------------------------ clics

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        ItemStack mochila = mochila();
        if (boton != 0 || mochila.isEmpty()) return super.mouseClicked(mx, my, boton);
        for (int i = 0; i < Deposito.numCapturados(mochila); i++) {
            if (dentro(mx, my, x0 + 8 + i * 44 + 12, y0 + 78, 16, 16)) return enviar(MochilaAccionPacket.SOLTAR, i);
        }
        if (!Deposito.tanque(mochila).isEmpty()) {
            if (dentro(mx, my, x0 + 150, y0 + 78, 16, 16)) {
                enviar(MochilaAccionPacket.ROCIAR, 0);
                onClose(); // para ver dónde cae la nube
                return true;
            }
            if (dentro(mx, my, x0 + 170, y0 + 78, 16, 16)) return enviar(MochilaAccionPacket.VACIAR, 0);
        }
        List<Reserva.Entrada> reserva = Reserva.leer(mochila);
        for (int k = 0; k < FILAS * COLUMNAS; k++) {
            int idx = k + scroll * 2;
            if (idx >= reserva.size()) break;
            int rx = x0 + 11 + (k % 2) * 108, ry = y0 + 103 + (k / 2) * 19;
            int[] acciones = {MochilaAccionPacket.PAUSA, MochilaAccionPacket.USAR, MochilaAccionPacket.QUITAR};
            for (int b = 0; b < 3; b++) {
                if (dentro(mx, my, rx + 72 + b * 12, ry + 3, 11, 11)) return enviar(acciones[b], idx);
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    private boolean enviar(int accion, int indice) {
        ModNetwork.CHANNEL.sendToServer(new MochilaAccionPacket(accion, indice));
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        if (scroll < 0) scroll = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.MOCHILA.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
