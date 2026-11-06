package com.miscelanea.client.movil;

import com.miscelanea.movil.Dinero;
import com.miscelanea.movil.MovilRed;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Pantalla del cajero automático: saldo con su desglose en monedas, lo que llevas encima, un teclado para la cantidad,
 * botones rápidos por moneda y ingresar / retirar (sin el límite del móvil). Todo lo comprueba el servidor.
 */
public class CajeroScreen extends Screen {
    private static final int PW = 236, PH = 218;
    private static final int MARCO = 0xFF2B3138, MARCO2 = 0xFF1B1F24, PANT = 0xFF0B2A1E, PANT2 = 0xFF0F3A29, VERDE = 0xFF34E08A,
            VERDE2 = 0xFF9DD9B8, TECLA = 0xFF3A424B, TECLA_SO = 0xFF4C5661, ROJO = 0xFFFF6B6B, AMARILLO = 0xFFE8C66A;
    private static final String[] TECLAS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<"};

    private final BlockPos pos;
    private final StringBuilder num = new StringBuilder();
    private long aviso;
    private String textoAviso = "";

    public CajeroScreen(BlockPos pos) {
        super(Component.translatable("block.miscelanea.cajero"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CajeroScreen(pos));
    }

    @Override
    protected void init() {
        MovilRed.alServidor(MovilRed.C_BANCO, null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        var p = Minecraft.getInstance().player;
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 8 * 8) onClose();
    }

    private int x0() {
        return (width - PW) / 2;
    }

    private int y0() {
        return (height - PH) / 2;
    }

    private long valor() {
        try {
            return num.length() == 0 ? 0 : Long.parseLong(num.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String t(String k, Object... a) {
        return Component.translatable("movil.miscelanea." + k, a).getString();
    }

    private static String miles(long n) {
        return Ui.miles(n);
    }

    // ------------------------------------------------------------------ zonas (relativas al panel)
    private static int kx(int i) {
        return 132 + (i % 3) * 32;
    }

    private static int ky(int i) {
        return 64 + (i / 3) * 22;
    }

    private static final int KW = 28, KH = 18;
    /** Botones de abajo: ingresar, retirar, todo (ingresar), todo (retirar). */
    /** Botones: ingresar, retirar, todo (ingresar), todo (retirar), 1 ficha, 10 fichas. */
    private static final int[][] BOT = {{10, 156, 52, 18}, {66, 156, 52, 18}, {10, 177, 52, 12}, {66, 177, 52, 12}, {10, 198, 108, 14}, {122, 198, 104, 14}};

    // ------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0();
        int rx = mx - x, ry = my - y;
        // carcasa
        g.fill(x - 2, y - 2, x + PW + 2, y + PH + 2, 0xFF0A0C0E);
        g.fill(x, y, x + PW, y + PH, MARCO);
        g.fill(x, y, x + PW, y + 1, 0xFF49525C);
        g.fill(x, y + PH - 1, x + PW, y + PH, MARCO2);
        // cartel de arriba
        g.fill(x + 6, y + 5, x + PW - 6, y + 21, 0xFF0F6B45);
        g.fill(x + 6, y + 5, x + PW - 6, y + 6, 0xFF1FA06A);
        g.drawString(font, Component.translatable("movil.miscelanea.cajero_titulo"), x + 12, y + 9, 0xFFFFFFFF, true);
        String sub = t("cajero_sub");
        g.drawString(font, sub, x + PW - 12 - font.width(sub), y + 9, VERDE2, false);
        // pantalla
        int sx = x + 8, sy = y + 26, sw = 116, sh = 124;
        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sh + 1, 0xFF050607);
        for (int i = 0; i < sh; i++) g.fill(sx, sy + i, sx + sw, sy + i + 1, i % 2 == 0 ? PANT : PANT2);
        boolean cargado = BancoCliente.cargado;
        g.drawString(font, t("banco_saldo"), sx + 5, sy + 4, VERDE2, false);
        String s = cargado ? miles(BancoCliente.saldo) : "…";
        g.pose().pushPose();
        g.pose().translate(sx + 5, sy + 15, 0);
        g.pose().scale(2F, 2F, 1F);
        g.drawString(font, s, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        g.drawString(font, t(BancoCliente.omega ? "cajero_chips" : "cajero_esmeraldas"), sx + 8 + font.width(s) * 2, sy + 22, VERDE, false);
        // desglose en monedas
        List<Dinero.Billete> mon = Dinero.monedas();
        long resto = BancoCliente.saldo;
        int dy = sy + 36;
        for (Dinero.Billete b : mon) {
            long q = resto / b.valor();
            resto -= q * b.valor();
            g.renderItem(new ItemStack(b.item()), sx + 4, dy);
            g.drawString(font, "x" + miles(q), sx + 22, dy + 4, q > 0 ? 0xFFFFFFFF : 0xFF5E7A6C, false);
            String v = "= " + miles(b.valor());
            g.drawString(font, v, sx + sw - 5 - font.width(v), dy + 4, 0xFF6FA98A, false);
            dy += 16;
        }
        g.fill(sx + 4, sy + sh - 20, sx + sw - 4, sy + sh - 19, 0xFF1F5A40);
        if (Util.getMillis() - aviso < 2500) g.drawString(font, textoAviso, sx + 5, sy + sh - 14, AMARILLO, false);
        else g.drawString(font, t("cajero_encima", miles(BancoCliente.enInventario)), sx + 5, sy + sh - 14, VERDE2, false);
        // cantidad
        int cx = x + 132, cy = y + 28;
        g.fill(cx - 1, cy - 1, cx + 94 + 1, cy + 28 + 1, 0xFF050607);
        g.fill(cx, cy, cx + 94, cy + 28, PANT);
        g.drawString(font, t("cajero_cantidad"), cx + 4, cy + 3, VERDE2, false);
        String n = num.length() == 0 ? "0" : miles(valor());
        g.drawString(font, n, cx + 90 - font.width(n), cy + 16, num.length() == 0 ? 0xFF5E7A6C : 0xFFFFFFFF, true);
        // teclado
        for (int i = 0; i < TECLAS.length; i++) {
            int kx = x + kx(i), ky = y + ky(i);
            boolean so = rx >= kx(i) && rx < kx(i) + KW && ry >= ky(i) && ry < ky(i) + KH;
            tecla(g, kx, ky, KW, KH, so ? TECLA_SO : TECLA);
            String k = TECLAS[i];
            int col = k.equals("C") ? ROJO : k.equals("<") ? AMARILLO : 0xFFEAEFF3;
            g.drawString(font, k, kx + KW / 2 - font.width(k) / 2, ky + 5, col, false);
        }
        // monedas rápidas: suman su valor a la cantidad
        int qy = y + 156;
        for (int i = 0; i < mon.size(); i++) {
            int qx = x + 132 + i * 24;
            boolean so = rx >= qx - x && rx < qx - x + 22 && ry >= 156 && ry < 156 + 22;
            tecla(g, qx, qy, 22, 22, so ? TECLA_SO : TECLA);
            g.renderItem(new ItemStack(mon.get(i).item()), qx + 3, qy + 3);
        }
        g.drawString(font, t("cajero_rapido"), x + 132, y + 180 + 2, 0xFF8A949E, false);
        // botones
        int pf = BancoCliente.precioFicha;
        String[] bt = {t("banco_ingresar"), t("banco_retirar"), t("cajero_todo_in"), t("cajero_todo_out"),
                t("cajero_ficha", 1, miles(pf)), t("cajero_fichas", 10, miles(10L * pf))};
        g.fill(x + 6, y + 194, x + PW - 6, y + 195, 0xFF1B1F24);
        for (int i = 0; i < BOT.length; i++) {
            int[] b = BOT[i];
            boolean so = rx >= b[0] && rx < b[0] + b[2] && ry >= b[1] && ry < b[1] + b[3];
            int fondo = i == 0 ? (so ? 0xFF22B868 : 0xFF17A05A) : i == 1 ? (so ? 0xFFE0A02A : 0xFFC8881A)
                    : i >= 4 ? (so ? 0xFF5A4A9A : 0xFF483A82) : (so ? TECLA_SO : TECLA);
            tecla(g, x + b[0], y + b[1], b[2], b[3], fondo);
            float esc = i < 2 || i >= 4 ? 1F : 0.75F;
            g.pose().pushPose();
            g.pose().translate(x + b[0] + b[2] / 2F - font.width(bt[i]) * esc / 2F, y + b[1] + (b[3] - 8 * esc) / 2F, 0);
            g.pose().scale(esc, esc, 1F);
            g.drawString(font, bt[i], 0, 0, i < 2 ? 0xFF06140C : 0xFFEAEFF3, false);
            g.pose().popPose();
        }
        super.render(g, mx, my, pt);
    }

    private static void tecla(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, 0xFF101316);
        g.fill(x, y, x + w, y + h - 1, c);
        g.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    // ------------------------------------------------------------------ entrada
    private void clic() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.35F));
    }

    private void teclear(String k) {
        switch (k) {
            case "C" -> num.setLength(0);
            case "<" -> {
                if (num.length() > 0) num.setLength(num.length() - 1);
            }
            default -> {
                if (num.length() < 9 && !(num.length() == 0 && k.equals("0"))) num.append(k);
            }
        }
    }

    private void sumar(long v) {
        long n = Math.min(999_999_999L, valor() + v);
        num.setLength(0);
        num.append(n);
    }

    private void avisar(String s) {
        textoAviso = s;
        aviso = Util.getMillis();
    }

    private void ingresar(long v) {
        if (v <= 0) return;
        if (v > BancoCliente.enInventario) {
            avisar(t("cajero_no_llevas"));
            return;
        }
        MovilRed.alServidor(MovilRed.C_BANCO_INGRESAR, b -> b.writeVarInt((int) Math.min(Integer.MAX_VALUE, v)));
        avisar(t("cajero_ingresado", miles(v)));
        num.setLength(0);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.4F));
    }

    private void fichas(int n) {
        if ((long) n * BancoCliente.precioFicha > BancoCliente.saldo) {
            avisar(t("cajero_sin_saldo"));
            return;
        }
        MovilRed.alServidor(MovilRed.C_FICHAS, b -> b.writeVarInt(n));
        avisar(t("cajero_fichas_ok", n));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(com.miscelanea.registry.ModSounds.ARCADE_FICHA.get(), 1F, 0.5F));
    }

    private void retirar(long v) {
        if (v <= 0) return;
        if (v > BancoCliente.saldo) {
            avisar(t("cajero_sin_saldo"));
            return;
        }
        MovilRed.alServidor(MovilRed.C_BANCO_RETIRAR, b -> b.writeVarInt((int) Math.min(Integer.MAX_VALUE, v)));
        avisar(t("cajero_retirado", miles(v)));
        num.setLength(0);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1F, 0.5F));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        double rx = mx - x0(), ry = my - y0();
        for (int i = 0; i < TECLAS.length; i++)
            if (rx >= kx(i) && rx < kx(i) + KW && ry >= ky(i) && ry < ky(i) + KH) {
                clic();
                teclear(TECLAS[i]);
                return true;
            }
        List<Dinero.Billete> mon = Dinero.monedas();
        for (int i = 0; i < mon.size(); i++) {
            int qx = 132 + i * 24;
            if (rx >= qx && rx < qx + 22 && ry >= 156 && ry < 178) {
                clic();
                sumar(mon.get(i).valor());
                return true;
            }
        }
        for (int i = 0; i < BOT.length; i++) {
            int[] b = BOT[i];
            if (rx < b[0] || rx >= b[0] + b[2] || ry < b[1] || ry >= b[1] + b[3]) continue;
            clic();
            switch (i) {
                case 0 -> ingresar(valor());
                case 1 -> retirar(valor());
                case 2 -> ingresar(BancoCliente.enInventario);
                case 3 -> retirar(BancoCliente.saldo);
                default -> fichas(i == 4 ? 1 : 10);
            }
            return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (c >= '0' && c <= '9') {
            teclear(String.valueOf(c));
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            teclear("<");
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            ingresar(valor());
            return true;
        }
        if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
