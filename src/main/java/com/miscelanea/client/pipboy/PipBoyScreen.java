package com.miscelanea.client.pipboy;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.pipboy.PipBoyAccionPacket;
import com.miscelanea.registry.ModSounds;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla del Pip-Boy: monitor de fósforo verde con líneas de barrido, parpadeo y viñeta, dentro del marco del aparato.
 * Pestañas: DATOS (hora, posición, estadísticas y notas), MAPA (mapa de la zona con marcadores) y RADIO (emisoras).
 */
public class PipBoyScreen extends Screen {
    private static final int W = 320, H = 200;
    private static final int VERDE = 0xFF3CFF6E, VERDE_M = 0xFF22B04C, VERDE_O = 0xFF0F5A24, FONDO = 0xFF051006;
    private static final String[] PESTANAS = {"pipboy.miscelanea.datos", "pipboy.miscelanea.mapa", "pipboy.miscelanea.radio"};
    private static final String[] EMISORAS = {"pipboy.miscelanea.emisora0", "pipboy.miscelanea.emisora1", "pipboy.miscelanea.emisora2"};
    private static int pestana;
    private static int escala = 1;
    private final List<EditBox> notas = new ArrayList<>();
    private int x0, y0, ticks;
    private float apertura;

    public PipBoyScreen() {
        super(Component.literal("Pip-Boy"));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        notas.clear();
        for (int i = 0; i < com.miscelanea.pipboy.PipBoy.MAX_NOTAS; i++) {
            EditBox e = new EditBox(font, x0 + 172, y0 + 46 + i * 20, 134, 12, Component.empty());
            e.setBordered(false);
            e.setMaxLength(64);
            e.setTextColor(VERDE);
            e.setValue(i < PipBoyCliente.NOTAS.size() ? PipBoyCliente.NOTAS.get(i) : "");
            notas.add(addRenderableWidget(e));
        }
        visibilidad();
        if (minecraft != null && minecraft.getConnection() != null)
            minecraft.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        actualizarMapa();
    }

    private void visibilidad() {
        for (EditBox e : notas) e.visible = pestana == 0;
    }

    private void actualizarMapa() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;
        PipBoyMapa.actualizar(minecraft.level, minecraft.player.getBlockX(), minecraft.player.getBlockZ(), escala);
    }

    @Override
    public void tick() {
        ticks++;
        apertura = Math.min(1, apertura + 0.25F);
        if (pestana == 1 && ticks % 10 == 0) actualizarMapa();
        for (EditBox e : notas) e.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        List<String> l = new ArrayList<>();
        for (EditBox e : notas) l.add(e.getValue());
        if (!l.equals(PipBoyCliente.NOTAS)) {
            PipBoyCliente.NOTAS.clear();
            PipBoyCliente.NOTAS.addAll(l);
            ModNetwork.CHANNEL.sendToServer(new PipBoyAccionPacket(PipBoyAccionPacket.NOTAS, 0, 0, l));
        }
        PipBoyCliente.cerrado();
        super.removed();
    }

    private void clic() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PIPBOY_CLIC.get(), 1.0F, 0.6F));
    }

    private void cambiar(int p) {
        if (p == pestana) return;
        pestana = (p + PESTANAS.length) % PESTANAS.length;
        clic();
        visibilidad();
        if (pestana == 1) actualizarMapa();
    }

    // ------------------------------------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        float t = ticks + partial;
        float ap = Math.min(1, apertura + partial * 0.25F);
        // fondo oscurecido y el aparato
        g.fill(0, 0, width, height, 0x90000000);
        g.pose().pushPose();
        float s = 0.85F + 0.15F * ap;
        g.pose().translate(width / 2F, height / 2F, 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(-width / 2F, -height / 2F, 0);
        marco(g);
        g.fill(x0, y0, x0 + W, y0 + H, FONDO);
        // contenido
        cabecera(g);
        switch (pestana) {
            case 0 -> datos(g);
            case 1 -> mapa(g, mx, my);
            default -> radio(g, t);
        }
        super.render(g, mx, my, partial);
        pie(g);
        efectosCrt(g, t);
        g.pose().popPose();
    }

    private void marco(GuiGraphics g) {
        int b = 14;
        g.fill(x0 - b - 4, y0 - b - 4, x0 + W + b + 4, y0 + H + b + 4, 0xFF23261E);
        g.fill(x0 - b, y0 - b, x0 + W + b, y0 + H + b, 0xFF5B6150);
        g.fill(x0 - b, y0 - b, x0 + W + b, y0 - b + 2, 0xFF7C8470);
        g.fill(x0 - b, y0 + H + b - 2, x0 + W + b, y0 + H + b, 0xFF3A3E33);
        g.fill(x0 - 3, y0 - 3, x0 + W + 3, y0 + H + 3, 0xFF15170F);
        // tornillos
        int[][] t = {{x0 - b + 4, y0 - b + 4}, {x0 + W + b - 8, y0 - b + 4}, {x0 - b + 4, y0 + H + b - 8}, {x0 + W + b - 8, y0 + H + b - 8}};
        for (int[] p : t) {
            g.fill(p[0], p[1], p[0] + 4, p[1] + 4, 0xFF8C8F80);
            g.fill(p[0] + 1, p[1] + 2, p[0] + 3, p[1] + 2 + 1, 0xFF3A3E33);
        }
        // rueda de sintonía a la derecha (decorativa)
        g.fill(x0 + W + b + 4, y0 + 40, x0 + W + b + 14, y0 + 90, 0xFFB89650);
        for (int i = 0; i < 10; i++) g.fill(x0 + W + b + 4, y0 + 42 + i * 5, x0 + W + b + 14, y0 + 43 + i * 5, 0xFF7A6230);
    }

    private void texto(GuiGraphics g, String s, int x, int y, int col) {
        g.drawString(font, s, x + 1, y, (col & 0x00FFFFFF) | 0x30000000, false);   // halo
        g.drawString(font, s, x, y, col, false);
    }

    private void cabecera(GuiGraphics g) {
        int y = y0 + 8;
        int x = x0 + 18;
        int[] xs = new int[PESTANAS.length + 1];
        for (int i = 0; i < PESTANAS.length; i++) {
            String n = Component.translatable(PESTANAS[i]).getString();
            xs[i] = x;
            texto(g, n, x, y, i == pestana ? VERDE : VERDE_M);
            if (i == pestana) {
                // corchete estilo Fallout alrededor de la pestaña elegida
                int w = font.width(n);
                g.fill(x - 4, y + 11, x - 3, y + 14, VERDE);
                g.fill(x + w + 2, y + 11, x + w + 3, y + 14, VERDE);
                g.fill(x0 + 8, y + 13, x - 3, y + 14, VERDE);
                g.fill(x + w + 3, y + 13, x0 + W - 8, y + 14, VERDE);
            }
            x += font.width(n) + 30;
        }
    }

    private void pie(GuiGraphics g) {
        var p = minecraft.player;
        if (p == null) return;
        int y = y0 + H - 13;
        g.fill(x0 + 8, y - 3, x0 + W - 8, y - 2, VERDE_O);
        texto(g, "HP " + (int) p.getHealth() + "/" + (int) p.getMaxHealth(), x0 + 10, y, VERDE_M);
        texto(g, hora(p), x0 + W / 2 - 16, y, VERDE_M);
        String lv = "NV " + p.experienceLevel;
        texto(g, lv, x0 + W - 12 - font.width(lv), y, VERDE_M);
    }

    private String hora(Player p) {
        long dt = (p.level().getDayTime() + 6000) % 24000;
        int h = (int) (dt / 1000), m = (int) ((dt % 1000) * 60 / 1000);
        return String.format("%02d:%02d", h, m);
    }

    private void datos(GuiGraphics g) {
        var p = minecraft.player;
        if (p == null) return;
        int x = x0 + 14, y = y0 + 32;
        var lvl = p.level();
        long dia = lvl.getDayTime() / 24000 + 1;
        String bioma = lvl.getBiome(p.blockPosition()).unwrapKey().map(k -> Component.translatable("biome." + k.location().getNamespace() + "." + k.location().getPath()).getString()).orElse("?");
        String clima = lvl.isThundering() ? Component.translatable("pipboy.miscelanea.tormenta").getString()
                : lvl.isRaining() ? Component.translatable("pipboy.miscelanea.lluvia").getString() : Component.translatable("pipboy.miscelanea.despejado").getString();
        var st = p.getStats();
        long jugado = st.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20;
        String[][] filas = {
                {"pipboy.miscelanea.hora", hora(p)},
                {"pipboy.miscelanea.dia", String.valueOf(dia)},
                {"pipboy.miscelanea.luna", Component.translatable("pipboy.miscelanea.luna" + lvl.getMoonPhase()).getString()},
                {"pipboy.miscelanea.clima", clima},
                {"pipboy.miscelanea.posicion", p.getBlockX() + " / " + p.getBlockY() + " / " + p.getBlockZ()},
                {"pipboy.miscelanea.bioma", bioma},
                {"pipboy.miscelanea.jugado", String.format("%dh %02dm", jugado / 3600, (jugado / 60) % 60)},
                {"pipboy.miscelanea.muertes", String.valueOf(st.getValue(Stats.CUSTOM.get(Stats.DEATHS)))},
                {"pipboy.miscelanea.bajas", String.valueOf(st.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)))},
                {"pipboy.miscelanea.andado", String.format("%.1f km", (st.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) + st.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))) / 100000.0)},
        };
        for (String[] f : filas) {
            texto(g, Component.translatable(f[0]).getString(), x, y, VERDE_M);
            texto(g, f[1], x + 150 - 4 - font.width(f[1]), y, VERDE);
            g.fill(x, y + 10, x + 146, y + 11, VERDE_O);
            y += 14;
        }
        texto(g, Component.translatable("pipboy.miscelanea.quitar").getString(), x0 + 172, y0 + H - 30, VERDE_O | 0xFF000000);
        // notas
        texto(g, Component.translatable("pipboy.miscelanea.notas").getString(), x0 + 172, y0 + 32, VERDE);
        for (int i = 0; i < notas.size(); i++) {
            int yy = y0 + 46 + i * 20;
            g.fill(x0 + 170, yy + 11, x0 + W - 12, yy + 12, VERDE_O);
            texto(g, "›", x0 + 164, yy, VERDE_M);
        }
    }

    private int mapaX() {
        return x0 + 14;
    }

    private int mapaY() {
        return y0 + 28;
    }

    private static final int TAM = 148;

    private void mapa(GuiGraphics g, int mx, int my) {
        var p = minecraft.player;
        if (p == null) return;
        int mxs = mapaX(), mys = mapaY();
        g.fill(mxs - 1, mys - 1, mxs + TAM + 1, mys + TAM + 1, VERDE_M);
        g.blit(PipBoyMapa.TEX, mxs, mys, TAM, TAM, 0, 0, PipBoyMapa.N, PipBoyMapa.N, PipBoyMapa.N, PipBoyMapa.N);
        float px = TAM / (float) (PipBoyMapa.N * escala);
        // otros jugadores
        for (Player o : p.level().players()) {
            if (o == p) continue;
            int ox = mxs + TAM / 2 + Math.round((float) (o.getX() - p.getX()) * px), oy = mys + TAM / 2 + Math.round((float) (o.getZ() - p.getZ()) * px);
            if (ox > mxs && oy > mys && ox < mxs + TAM && oy < mys + TAM) g.fill(ox - 1, oy - 1, ox + 2, oy + 2, 0xFFFFFFFF);
        }
        // marcadores
        String dim = p.level().dimension().location().toString();
        for (PipBoyCliente.Marca m : PipBoyCliente.MARCAS) {
            if (!m.dim().equals(dim)) continue;
            int ox = mxs + TAM / 2 + Math.round((float) (m.x() + 0.5 - p.getX()) * px), oy = mys + TAM / 2 + Math.round((float) (m.z() + 0.5 - p.getZ()) * px);
            ox = Mth.clamp(ox, mxs + 2, mxs + TAM - 3);
            oy = Mth.clamp(oy, mys + 2, mys + TAM - 3);
            g.fill(ox - 1, oy - 3, ox + 2, oy + 4, VERDE);
            g.fill(ox - 3, oy - 1, ox + 4, oy + 2, VERDE);
            g.fill(ox, oy, ox + 1, oy + 1, FONDO);
        }
        // tú: flecha según hacia dónde miras
        g.pose().pushPose();
        g.pose().translate(mxs + TAM / 2F, mys + TAM / 2F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(p.getYRot() + 180));
        for (int i = 0; i < 6; i++) g.fill(-i / 2 - 1, -4 + i, i / 2 + 1, -3 + i, 0xFFFFFFFF);
        g.pose().popPose();
        texto(g, "N", mxs + TAM / 2 - 2, mys + 2, VERDE);
        // escala y lista de marcadores
        int x = mxs + TAM + 12, y = mys;
        texto(g, Component.translatable("pipboy.miscelanea.escala", escala).getString(), x, y, VERDE);
        texto(g, "[+]", x + 96, y, VERDE);
        texto(g, "[-]", x + 116, y, VERDE);
        y += 16;
        texto(g, Component.translatable("pipboy.miscelanea.marcadores").getString(), x, y, VERDE_M);
        y += 12;
        for (int i = 0; i < PipBoyCliente.MARCAS.size() && i < 9; i++) {
            PipBoyCliente.Marca m = PipBoyCliente.MARCAS.get(i);
            boolean aqui = m.dim().equals(dim);
            double d = Math.hypot(m.x() + 0.5 - p.getX(), m.z() + 0.5 - p.getZ());
            String txt = m.nombre() + (aqui ? "  " + (int) d + "m" : "");
            boolean sobre = mx >= x && mx < x + 140 && my >= y && my < y + 11;
            if (sobre) g.fill(x - 2, y - 1, x + 138, y + 10, VERDE_O);
            texto(g, txt, x, y, aqui ? VERDE : VERDE_O);
            y += 12;
        }
        texto(g, Component.translatable("pipboy.miscelanea.ayuda_mapa").getString(), x, mys + TAM - 18, VERDE_O | 0xFF000000);
        texto(g, Component.translatable("pipboy.miscelanea.ayuda_mapa2").getString(), x, mys + TAM - 8, VERDE_O | 0xFF000000);
    }

    private void radio(GuiGraphics g, float t) {
        int x = x0 + 14, y = y0 + 34;
        for (int i = 0; i < EMISORAS.length; i++) {
            boolean on = PipBoyCliente.emisora == i;
            if (on) g.fill(x - 3, y - 2, x + 140, y + 10, VERDE_O);
            texto(g, (on ? "■ " : "  ") + Component.translatable(EMISORAS[i]).getString(), x, y, on ? VERDE : VERDE_M);
            y += 16;
        }
        y += 6;
        texto(g, Component.translatable("pipboy.miscelanea.apagar").getString(), x, y, PipBoyCliente.emisora < 0 ? VERDE : VERDE_M);
        // osciloscopio
        int ox = x0 + 172, oy = y0 + 32, ow = 134, oh = 100;
        g.fill(ox - 1, oy - 1, ox + ow + 1, oy + oh + 1, VERDE_O);
        g.fill(ox, oy, ox + ow, oy + oh, FONDO);
        for (int i = 1; i < 6; i++) g.fill(ox, oy + i * oh / 6, ox + ow, oy + i * oh / 6 + 1, 0xFF0A2A12);
        for (int i = 1; i < 8; i++) g.fill(ox + i * ow / 8, oy, ox + i * ow / 8 + 1, oy + oh, 0xFF0A2A12);
        int prev = oy + oh / 2;
        boolean suena = PipBoyCliente.emisora >= 0;
        for (int i = 0; i < ow; i++) {
            double u = i / (double) ow;
            double v = 0;
            if (suena) {
                double a = 0.6 + 0.4 * Math.sin(t * 0.37 + PipBoyCliente.emisora);
                v = a * (Math.sin(u * 18 + t * 0.9) * 0.5 + Math.sin(u * 41 - t * 1.7) * 0.25 + Math.sin(u * 7 + t * 0.3) * 0.25);
                v *= Math.sin(u * Math.PI);
            }
            int yy = oy + oh / 2 - (int) (v * oh * 0.42);
            int a = Math.min(prev, yy), b = Math.max(prev, yy);
            g.fill(ox + i, a, ox + i + 1, b + 1, VERDE);
            prev = yy;
        }
        if (suena)
            texto(g, Component.translatable(EMISORAS[PipBoyCliente.emisora] + ".desc").getString(), ox, oy + oh + 8, VERDE_M);
    }

    private void efectosCrt(GuiGraphics g, float t) {
        // líneas de barrido, una banda que baja, parpadeo y viñeta
        for (int y = y0; y < y0 + H; y += 2) g.fill(x0, y, x0 + W, y + 1, 0x1E000000);
        int banda = y0 + (int) ((t * 2.2F) % (H + 40)) - 20;
        g.fill(x0, Math.max(y0, banda), x0 + W, Math.min(y0 + H, banda + 14), 0x0C3CFF6E);
        int parpadeo = (int) (8 + 6 * Math.sin(t * 1.3) + (Math.random() < 0.03 ? 18 : 0));
        g.fill(x0, y0, x0 + W, y0 + H, (parpadeo << 24) | 0x002A6E36);
        for (int i = 0; i < 10; i++) {
            int a = (int) (60 * (1 - i / 10F)) << 24;
            g.fill(x0 + i, y0, x0 + i + 1, y0 + H, a);
            g.fill(x0 + W - 1 - i, y0, x0 + W - i, y0 + H, a);
            g.fill(x0, y0 + i, x0 + W, y0 + i + 1, a);
            g.fill(x0, y0 + H - 1 - i, x0 + W, y0 + H - i, a);
        }
    }

    // ------------------------------------------------------------------------------------------------ entrada
    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        // pestañas
        int x = x0 + 18;
        for (int i = 0; i < PESTANAS.length; i++) {
            int w = font.width(Component.translatable(PESTANAS[i]).getString());
            if (mx >= x - 4 && mx <= x + w + 4 && my >= y0 + 4 && my <= y0 + 20) {
                cambiar(i);
                return true;
            }
            x += w + 30;
        }
        var p = minecraft.player;
        if (p == null) return super.mouseClicked(mx, my, boton);
        if (pestana == 1) {
            int mxs = mapaX(), mys = mapaY();
            int lx = mxs + TAM + 12;
            if (my >= mys && my <= mys + 10) {
                if (mx >= lx + 96 && mx <= lx + 112) { escala = Math.max(1, escala / 2); clic(); actualizarMapa(); return true; }
                if (mx >= lx + 116 && mx <= lx + 132) { escala = Math.min(8, escala * 2); clic(); actualizarMapa(); return true; }
            }
            if (mx >= mxs && mx < mxs + TAM && my >= mys && my < mys + TAM && boton == 0) {
                float bloques = PipBoyMapa.N * escala / (float) TAM;
                int wx = (int) Math.floor(p.getX() + (mx - (mxs + TAM / 2.0)) * bloques);
                int wz = (int) Math.floor(p.getZ() + (my - (mys + TAM / 2.0)) * bloques);
                String nombre = Component.translatable("pipboy.miscelanea.marca", PipBoyCliente.MARCAS.size() + 1).getString();
                ModNetwork.CHANNEL.sendToServer(new PipBoyAccionPacket(PipBoyAccionPacket.MARCA, wx, wz, List.of(nombre)));
                clic();
                return true;
            }
            int y = mys + 28;
            for (int i = 0; i < PipBoyCliente.MARCAS.size() && i < 9; i++) {
                if (mx >= lx && mx < lx + 140 && my >= y && my < y + 11 && boton == 1) {
                    ModNetwork.CHANNEL.sendToServer(new PipBoyAccionPacket(PipBoyAccionPacket.BORRAR_MARCA, i, 0, List.of()));
                    clic();
                    return true;
                }
                y += 12;
            }
        } else if (pestana == 2) {
            int x2 = x0 + 14, y = y0 + 34;
            for (int i = 0; i < EMISORAS.length; i++) {
                if (mx >= x2 - 3 && mx < x2 + 140 && my >= y - 2 && my < y + 10) {
                    PipBoyCliente.radio(i);
                    return true;
                }
                y += 16;
            }
            y += 6;
            if (mx >= x2 - 3 && mx < x2 + 140 && my >= y - 2 && my < y + 10) {
                PipBoyCliente.radio(-1);
                return true;
            }
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (pestana == 1) {
            escala = d > 0 ? Math.max(1, escala / 2) : Math.min(8, escala * 2);
            actualizarMapa();
            return true;
        }
        return super.mouseScrolled(mx, my, d);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean escribiendo = getFocused() instanceof EditBox e && e.isFocused();
        if (!escribiendo) {
            if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_LEFT) { cambiar(pestana - 1); return true; }
            if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_TAB) { cambiar(pestana + 1); return true; }
            if (PipBoyCliente.ABRIR.matches(key, scan)) { onClose(); return true; }
            if (key == GLFW.GLFW_KEY_DELETE && pestana == 0) {
                ModNetwork.CHANNEL.sendToServer(PipBoyAccionPacket.simple(PipBoyAccionPacket.QUITAR));
                onClose();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
