package com.miscelanea.client.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.bichos.Especie;
import com.miscelanea.bichos.PescaResultadoPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * Minijuego de pesca (estilo Stardew Valley): mantén pulsado (clic o espacio) para subir la zona verde y mantener
 * el pez dentro. Si se llena la barra de la derecha, lo pescas; si se vacía, se escapa.
 */
public class PescaScreen extends Screen {
    private static final int ALTO = 150, ANCHO = 22;
    private final Especie especie;
    private final int dificultad;
    private final RandomSource r = RandomSource.create();
    private float pez = 0.5F, pezO = 0.5F, pezObj = 0.5F, pezVel;
    private float zona = 0.2F, zonaO = 0.2F, zonaVel;
    private float progreso = 0.3F, progresoO = 0.3F;
    private boolean pulsado, terminado;
    private int ticks;

    public PescaScreen(Especie e, int dificultad) {
        super(Component.translatable("pesca.miscelanea.titulo"));
        this.especie = e;
        this.dificultad = dificultad;
    }

    public static void abrir(int especie, int dificultad) {
        Especie[] v = Especie.values();
        Minecraft.getInstance().setScreen(new PescaScreen(v[Mth.clamp(especie, 0, v.length - 1)], dificultad));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_SPLASH, 1.0F, 0.8F));
    }

    private float tamZona() {
        return 0.30F - dificultad * 0.018F;
    }

    @Override
    public void tick() {
        if (terminado) return;
        ticks++;
        pezO = pez;
        zonaO = zona;
        progresoO = progreso;
        // el pez cambia de idea más a menudo y más bruscamente cuanto más difícil es
        if (r.nextFloat() < 0.04F + dificultad * 0.012F) pezObj = Mth.clamp(r.nextFloat(), 0.04F, 0.96F);
        float fuerza = 0.012F + dificultad * 0.0045F;
        pezVel += (pezObj - pez) * fuerza;
        pezVel *= 0.82F;
        pez = Mth.clamp(pez + pezVel, 0.02F, 0.98F);
        // la zona sube mientras pulsas y cae si no
        zonaVel += pulsado ? 0.012F : -0.010F;
        zonaVel *= 0.9F;
        zona += zonaVel;
        float tz = tamZona();
        if (zona < 0) { zona = 0; zonaVel = Math.max(0, -zonaVel * 0.3F); }
        if (zona > 1 - tz) { zona = 1 - tz; zonaVel = Math.min(0, -zonaVel * 0.3F); }
        boolean dentro = pez >= zona && pez <= zona + tz;
        progreso = Mth.clamp(progreso + (dentro ? 0.011F : -0.008F - dificultad * 0.0008F), 0, 1);
        if (dentro && ticks % 6 == 0)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.4F + progreso * 0.4F, 0.25F));
        if (progreso >= 1) acabar(true);
        else if (progreso <= 0) acabar(false);
    }

    private void acabar(boolean ganado) {
        terminado = true;
        ModNetwork.CHANNEL.sendToServer(new PescaResultadoPacket(ganado));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ganado ? SoundEvents.PLAYER_LEVELUP : SoundEvents.GENERIC_SPLASH, ganado ? 1.6F : 1.0F, 0.6F));
        onClose();
    }

    @Override
    public void onClose() {
        if (!terminado) {
            terminado = true;
            ModNetwork.CHANNEL.sendToServer(new PescaResultadoPacket(false));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int x = width / 2 + 40, y = (height - ALTO) / 2;
        // marco de madera
        g.fill(x - 8, y - 8, x + ANCHO + 30, y + ALTO + 8, 0xFF5A3A1E);
        g.fill(x - 6, y - 6, x + ANCHO + 28, y + ALTO + 6, 0xFF8A5A2E);
        g.fill(x - 6, y - 6, x + ANCHO + 28, y - 4, 0xFFB07A42);
        // agua
        for (int i = 0; i < ALTO; i++) {
            float k = i / (float) ALTO;
            int col = 0xFF000000 | ((int) (20 + 30 * k) << 16) | ((int) (90 + 60 * k) << 8) | (int) (150 + 60 * k);
            g.fill(x, y + i, x + ANCHO, y + i + 1, col);
        }
        // zona verde
        float tz = tamZona();
        float z = Mth.lerp(partial, zonaO, zona);
        int zy1 = y + ALTO - (int) ((z + tz) * ALTO), zy2 = y + ALTO - (int) (z * ALTO);
        g.fill(x + 1, zy1, x + ANCHO - 1, zy2, 0xB060E070);
        g.fill(x + 1, zy1, x + ANCHO - 1, zy1 + 1, 0xFFB0FFB8);
        // pez (su icono)
        float p = Mth.lerp(partial, pezO, pez);
        int py = y + ALTO - (int) (p * ALTO);
        ResourceLocation ico = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + especie.item() + ".png");
        g.blit(ico, x + ANCHO / 2 - 9, py - 9, 18, 18, 0, 0, 32, 32, 32, 32);
        // progreso
        float pr = Mth.lerp(partial, progresoO, progreso);
        int bx = x + ANCHO + 8;
        g.fill(bx, y, bx + 12, y + ALTO, 0xFF2A1A0C);
        int col = pr > 0.66F ? 0xFF50E050 : pr > 0.33F ? 0xFFE0C040 : 0xFFE05030;
        g.fill(bx + 2, y + ALTO - (int) (pr * (ALTO - 4)) - 2, bx + 10, y + ALTO - 2, col);
        // textos
        g.drawCenteredString(font, Component.translatable("pesca.miscelanea.pica"), width / 2 - 60, y + 10, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal("★".repeat(especie.estrellas)), width / 2 - 60, y + 24, 0xFFFFD040);
        g.drawCenteredString(font, Component.translatable("pesca.miscelanea.ayuda"), width / 2 - 60, y + ALTO - 10, 0xFFDDDDDD);
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        pulsado = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int b) {
        pulsado = false;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE) {
            pulsado = true;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE) {
            pulsado = false;
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }
}
