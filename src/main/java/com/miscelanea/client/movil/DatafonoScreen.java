package com.miscelanea.client.movil;

import com.miscelanea.movil.DatafonoBlockEntity;
import com.miscelanea.movil.MovilRed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Pantalla del datáfono. El dueño pone precio y concepto; los demás ven a quién pagan, cuánto y por qué, y confirman.
 * El servidor lo vuelve a comprobar todo (también que el precio no haya cambiado mientras tanto).
 */
public class DatafonoScreen extends Screen {
    private static final int PW = 184, PH = 158;
    private static final int CARCASA = 0xFF2A2F35, CARCASA2 = 0xFF1A1D21, PANT = 0xFF0B2A1E, PANT2 = 0xFF0F3A29, VERDE2 = 0xFF9DD9B8;

    private final BlockPos pos;
    private boolean dueno;
    private EditBox precio, concepto;
    private long pagado = -1;

    public DatafonoScreen(BlockPos pos) {
        super(Component.translatable("block.miscelanea.datafono"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DatafonoScreen(pos));
    }

    private DatafonoBlockEntity be() {
        var l = Minecraft.getInstance().level;
        return l != null && l.getBlockEntity(pos) instanceof DatafonoBlockEntity d ? d : null;
    }

    private static String t(String k, Object... a) {
        return Component.translatable("movil.miscelanea." + k, a).getString();
    }

    private int x0() {
        return (width - PW) / 2;
    }

    private int y0() {
        return (height - PH) / 2;
    }

    @Override
    protected void init() {
        DatafonoBlockEntity d = be();
        var p = Minecraft.getInstance().player;
        dueno = d != null && p != null && (d.esDueno(p) || (d.dueno() == null && p.hasPermissions(2)));
        int x = x0(), y = y0();
        if (dueno) {
            precio = new EditBox(font, x + 14, y + 58, PW - 28, 16, Component.translatable("movil.miscelanea.datafono_precio"));
            precio.setFilter(s -> s.matches("\\d{0,9}"));
            precio.setValue(d == null || d.precio() <= 0 ? "" : String.valueOf(d.precio()));
            addRenderableWidget(precio);
            concepto = new EditBox(font, x + 14, y + 92, PW - 28, 16, Component.translatable("movil.miscelanea.datafono_concepto"));
            concepto.setMaxLength(BancoCliente.maxConcepto > 0 ? BancoCliente.maxConcepto : 40);
            concepto.setValue(d == null ? "" : d.concepto());
            addRenderableWidget(concepto);
            setInitialFocus(precio);
            addRenderableWidget(Button.builder(Component.translatable("movil.miscelanea.datafono_guardar"), b -> guardar())
                    .bounds(x + 14, y + PH - 28, PW - 28, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("movil.miscelanea.datafono_pagar"), b -> pagar())
                    .bounds(x + 14, y + PH - 28, (PW - 34) / 2, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                    .bounds(x + 20 + (PW - 34) / 2, y + PH - 28, (PW - 34) / 2, 18).build());
        }
        MovilRed.alServidor(MovilRed.C_BANCO, null);
    }

    private void guardar() {
        long n;
        try {
            n = precio.getValue().isEmpty() ? 0 : Long.parseLong(precio.getValue());
        } catch (NumberFormatException e) {
            n = 0;
        }
        final long nn = n;
        final String c = concepto.getValue().trim();
        MovilRed.alServidor(MovilRed.C_DATAFONO_CONF, b -> {
            b.writeBlockPos(pos);
            b.writeVarLong(nn);
            b.writeUtf(c, 256);
        });
        onClose();
    }

    private void pagar() {
        DatafonoBlockEntity d = be();
        if (d == null || d.precio() <= 0 || pagado >= 0) return;
        if (BancoCliente.cargado && BancoCliente.saldo < d.precio()) return;
        long n = d.precio();
        pagado = n;
        MovilRed.alServidor(MovilRed.C_DATAFONO_PAGAR, b -> {
            b.writeBlockPos(pos);
            b.writeVarLong(n);
        });
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.6F, 0.5F));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        var p = Minecraft.getInstance().player;
        if (be() == null || p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 8 * 8) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0();
        g.fill(x - 2, y - 2, x + PW + 2, y + PH + 2, 0xFF0A0C0E);
        g.fill(x, y, x + PW, y + PH, CARCASA);
        g.fill(x, y, x + PW, y + 1, 0xFF4A525C);
        g.fill(x, y + PH - 1, x + PW, y + PH, CARCASA2);
        // pantalla
        int sx = x + 8, sy = y + 8, sw = PW - 16, sh = PH - 44;
        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sh + 1, 0xFF050607);
        for (int i = 0; i < sh; i++) g.fill(sx, sy + i, sx + sw, sy + i + 1, i % 2 == 0 ? PANT : PANT2);
        g.fill(sx, sy, sx + sw, sy + 12, 0xFF17A05A);
        g.drawString(font, Component.translatable("block.miscelanea.datafono"), sx + 4, sy + 2, 0xFFFFFFFF, true);
        DatafonoBlockEntity d = be();
        if (d == null) return;
        String unidad = t(BancoCliente.omega ? "cajero_chips" : "cajero_esmeraldas");
        if (dueno) {
            g.drawString(font, t("datafono_tuyo"), sx + 4, sy + 18, VERDE2, false);
            g.drawString(font, t("datafono_precio") + " (" + unidad + ")", sx + 6, sy + 38, VERDE2, false);
            g.drawString(font, t("datafono_concepto"), sx + 6, sy + 72, VERDE2, false);
        } else {
            String a = d.nombreDueno().isEmpty() ? "?" : d.nombreDueno();
            g.drawString(font, t("datafono_pagar_a", a), sx + 4, sy + 18, VERDE2, false);
            if (!d.concepto().isEmpty()) g.drawString(font, font.plainSubstrByWidth(d.concepto(), sw - 8), sx + 4, sy + 30, 0xFFE8F5EE, false);
            String n = d.precio() > 0 ? Ui.miles(d.precio()) : "--";
            g.pose().pushPose();
            g.pose().translate(sx + sw / 2F - font.width(n) * 1.5F, sy + 46, 0);
            g.pose().scale(3F, 3F, 1F);
            g.drawString(font, n, 0, 0, 0xFFFFFFFF, true);
            g.pose().popPose();
            g.drawString(font, unidad, sx + sw / 2 - font.width(unidad) / 2, sy + 74, 0xFF34E08A, false);
            boolean falta = BancoCliente.cargado && d.precio() > BancoCliente.saldo;
            String s = d.precio() <= 0 ? t("datafono_sin_precio") : falta ? t("cajero_sin_saldo")
                    : t("datafono_saldo", BancoCliente.cargado ? Ui.miles(BancoCliente.saldo) : "…");
            g.drawString(font, s, sx + 4, sy + sh - 12, falta || d.precio() <= 0 ? 0xFFFF7070 : VERDE2, false);
        }
        super.render(g, mx, my, pt);
    }
}
