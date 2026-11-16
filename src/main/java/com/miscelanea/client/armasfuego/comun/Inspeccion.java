package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.escopeta.EscopetaPresionItem;
import com.miscelanea.armasfuego.pistola.RevolverItem;
import com.miscelanea.armasfuego.rifle.RifleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Inspeccionar el arma (cosmético, solo en primera persona): una tecla (N por defecto) y el arma sube hacia el centro y
 * se gira despacio para verla por los lados (el cobre, el óxido, el tambor). Se corta al apuntar, disparar, usar el
 * arma, abrir un menú o cambiar de objeto. Vale para todas las armas de mano.
 */
public final class Inspeccion {
    private Inspeccion() {}

    public static final net.minecraft.client.KeyMapping TECLA = new net.minecraft.client.KeyMapping("key.miscelanea.inspeccionar",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_N, "key.categories.miscelanea");

    /** Cuánto dura (ticks). */
    public static final int DURACION = 60;

    /** Ticks que lleva inspeccionando (-1: no), el objeto que inspecciona y cuántos gatillos llevaba. */
    private static int t = -1;
    private static Item objeto;
    private static int gatillos;

    public static boolean inspeccionable(ItemStack s) {
        Item i = s.getItem();
        return i instanceof ArmaFuegoItem || i instanceof RevolverItem || i instanceof EscopetaPresionItem || i instanceof RifleItem;
    }

    public static boolean activa() {
        return t >= 0;
    }

    /** Cada tick del cliente (lo llama FuegoCliente.tick). */
    public static void tick(Minecraft mc, LocalPlayer p) {
        if (p == null) {
            t = -1;
            return;
        }
        ItemStack s = p.getMainHandItem();
        while (TECLA.consumeClick()) {
            if (t >= 0) t = -1;   // otra vez: la guarda
            else if (mc.screen == null && inspeccionable(s) && !Apuntado.apunta && !p.isUsingItem() && mc.options.getCameraType().isFirstPerson()) {
                t = 0;
                objeto = s.getItem();
                gatillos = Apuntado.gatillos;
                p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 0.35F, 1.5F, false);
            }
        }
        if (t < 0) return;
        if (mc.screen != null || Apuntado.apunta || p.isUsingItem() || s.getItem() != objeto || Apuntado.gatillos != gatillos
                || mc.options.keyAttack.isDown() || !mc.options.getCameraType().isFirstPerson() || ++t >= DURACION) t = -1;
    }

    /** El arma sube hacia el centro y se gira (se aplica en la transformación de la mano, antes de lo demás). */
    public static void aplicar(PoseStack ps, int lado, ItemStack st, float pt) {
        if (t < 0 || !inspeccionable(st) || st.getItem() != objeto) return;
        float x = Mth.clamp((t + pt) / DURACION, 0F, 1F);
        // sube y baja con suavidad (0 → 1 → 0), y entre medias se mece despacio
        float e = Mth.clamp(Math.min(x / 0.16F, (1F - x) / 0.16F), 0F, 1F);
        e = e * e * (3 - 2 * e);
        ps.translate(-lado * 0.30F * e, 0.13F * e, 0.26F * e);
        float giro = 22F + 58F * Mth.sin(x * Mth.TWO_PI);
        FuegoCliente.girar(ps, lado, Axis.YP.rotationDegrees(lado * giro * e));
        FuegoCliente.girar(ps, lado, Axis.ZP.rotationDegrees(lado * -16F * e));
        FuegoCliente.girar(ps, lado, Axis.XP.rotationDegrees((10F + 9F * Mth.sin(x * Mth.TWO_PI * 2)) * e));
    }
}
