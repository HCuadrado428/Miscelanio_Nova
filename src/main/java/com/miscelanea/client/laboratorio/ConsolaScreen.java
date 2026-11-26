package com.miscelanea.client.laboratorio;

import com.miscelanea.laboratorio.CamaraBlockEntity;
import com.miscelanea.laboratorio.ConsolaLab;
import com.miscelanea.tortura.aparatos.AparatoAccionPacket;
import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import com.miscelanea.tortura.aparatos.AparatosRed;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * El panel de la consola de una estación del laboratorio (clic derecho en el bloque de detrás de la cabina): quién está dentro,
 * si está en marcha, el nivel (o la temperatura, en la cámara de temperatura) con un botón por cada uno, arrancar o parar y sacar
 * al sujeto. Lo que se ve es el estado del bloque, que el servidor manda solo; las órdenes van al servidor, que las comprueba.
 */
public class ConsolaScreen extends Screen {
    private static final int W = 262, H = 184;
    private static final int BORDE = 0xFF0C1814, FONDO = 0xFF15231E, BARRA = 0xFF1F3A30, TEXTO = 0xFFCDEBDD, SUAVE = 0xFF7FA595,
            VERDE = 0xFF6DFF96, ROJO = 0xFFFF6A5A;
    private static final int NADA = Integer.MIN_VALUE;

    private final BlockPos pos;
    private final List<Button> ajustes = new ArrayList<>();
    private Button marcha, sacar;
    private boolean dial;
    /** El extremo pide pulsar dos veces: cuál está esperando la segunda pulsación y hasta cuándo. */
    private int confirmar = NADA;
    private long confirmarHasta;

    public ConsolaScreen(BlockPos pos) {
        super(nombre(pos));
        this.pos = pos.immutable();
    }

    public static void abrir(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ConsolaScreen(pos));
    }

    private static Component nombre(BlockPos pos) {
        var l = Minecraft.getInstance().level;
        return l == null ? Component.empty() : l.getBlockState(pos).getBlock().getName();
    }

    @Nullable
    private AparatoBlockEntity aparato() {
        var l = Minecraft.getInstance().level;
        return l != null && l.getBlockEntity(pos) instanceof AparatoBlockEntity a && a instanceof ConsolaLab ? a : null;
    }

    @Override
    protected void init() {
        ajustes.clear();
        AparatoBlockEntity be = aparato();
        if (be == null) {
            onClose();
            return;
        }
        dial = ((ConsolaLab) be).conDial();
        int x = (width - W) / 2, y = (height - H) / 2;
        int n = dial ? 7 : 3, ancho = dial ? 34 : 78, hueco = dial ? 2 : 4;
        int x0 = x + (W - (n * ancho + (n - 1) * hueco)) / 2;
        for (int i = 0; i < n; i++) {
            int valor = dial ? i - 3 : i;
            ajustes.add(addRenderableWidget(Button.builder(Component.empty(), b -> elegir(valor)).bounds(x0 + i * (ancho + hueco), y + 70, ancho, 20).build()));
        }
        int mitad = (W - 30) / 2;
        marcha = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ConsolaLab c = consola();
            if (c != null) enviar(c.enMarcha() ? AparatoAccionPacket.LAB_PARAR : AparatoAccionPacket.LAB_ARRANCAR, 0);
        }).bounds(x + 10, y + 100, mitad, 20).build());
        sacar = addRenderableWidget(Button.builder(Component.translatable("laboratorio.miscelanea.consola.sacar"), b -> enviar(AparatoAccionPacket.LAB_SACAR, 0))
                .bounds(x + 20 + mitad, y + 100, mitad, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + W / 2 - 40, y + H - 26, 80, 20).build());
        actualizar();
    }

    @Nullable
    private ConsolaLab consola() {
        return aparato() instanceof ConsolaLab c ? c : null;
    }

    private void elegir(int valor) {
        boolean extremo = dial ? Math.abs(valor) == 3 : valor == 2;
        if (extremo && !(confirmar == valor && Util.getMillis() < confirmarHasta)) {
            confirmar = valor;
            confirmarHasta = Util.getMillis() + 3000;
            actualizar();
            return;
        }
        confirmar = NADA;
        enviar(AparatoAccionPacket.LAB_AJUSTE, valor);
    }

    private void enviar(int accion, int valor) {
        AparatosRed.CANAL.sendToServer(new AparatoAccionPacket(accion, pos, valor));
    }

    @Override
    public void tick() {
        AparatoBlockEntity be = aparato();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(be.centro()) > 64) {
            onClose();
            return;
        }
        if (confirmar != NADA && Util.getMillis() > confirmarHasta) confirmar = NADA;
        actualizar();
    }

    private void actualizar() {
        AparatoBlockEntity be = aparato();
        if (!(be instanceof ConsolaLab c) || marcha == null) return;
        for (int i = 0; i < ajustes.size(); i++) {
            int valor = dial ? i - 3 : i;
            MutableComponent t = confirmar == valor ? Component.translatable("laboratorio.miscelanea.consola.seguro").withStyle(ChatFormatting.RED)
                    : etiqueta(valor);
            if (valor == c.ajuste()) t = t.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            ajustes.get(i).setMessage(t);
        }
        marcha.setMessage(Component.translatable(c.enMarcha() ? "laboratorio.miscelanea.consola.parar" : "laboratorio.miscelanea.consola.arrancar"));
        marcha.active = c.enMarcha() || be.ocupado();
        sacar.active = be.ocupado();
    }

    private MutableComponent etiqueta(int valor) {
        if (dial) return Component.literal(Math.round(CamaraBlockEntity.TEMPERATURA[valor + 3]) + "°")
                .withStyle(valor < 0 ? ChatFormatting.AQUA : valor > 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE);
        return Component.translatable("laboratorio.miscelanea.consola.nivel." + valor);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BORDE);
        g.fill(x, y, x + W, y + H, FONDO);
        g.fill(x, y, x + W, y + 18, BARRA);
        g.drawString(font, title, x + 8, y + 5, TEXTO, false);
        AparatoBlockEntity be = aparato();
        if (be instanceof ConsolaLab c) {
            // piloto de marcha en la barra
            boolean on = c.enMarcha();
            Component estado = Component.translatable(on ? "laboratorio.miscelanea.consola.marcha" : "laboratorio.miscelanea.consola.parada");
            int ew = font.width(estado);
            g.fill(x + W - ew - 20, y + 6, x + W - ew - 14, y + 12, on ? VERDE : 0xFF4A5A54);
            g.drawString(font, estado, x + W - ew - 8, y + 5, on ? VERDE : SUAVE, false);
            // quién está dentro
            Component quien;
            if (be.ocupado()) {
                Entity v = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(be.victimaId());
                quien = Component.translatable("laboratorio.miscelanea.consola.sujeto", v == null ? Component.literal("?") : v.getDisplayName());
            } else {
                quien = Component.translatable("laboratorio.miscelanea.consola.vacia");
            }
            int ty = y + 26;
            for (FormattedCharSequence l : font.split(quien, W - 16)) {
                g.drawString(font, l, x + 8, ty, be.ocupado() ? TEXTO : ROJO, false);
                ty += 10;
            }
            g.drawString(font, Component.translatable(dial ? "laboratorio.miscelanea.consola.temperatura" : "laboratorio.miscelanea.consola.nivel"), x + 8, y + 58, SUAVE, false);
            int hy = y + 128;
            for (FormattedCharSequence l : font.split(Component.translatable("laboratorio.miscelanea.consola.atajo"), W - 16)) {
                g.drawString(font, l, x + 8, hy, SUAVE, false);
                hy += 10;
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
