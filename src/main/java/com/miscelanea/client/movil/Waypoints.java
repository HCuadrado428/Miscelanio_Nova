package com.miscelanea.client.movil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Puntos guardados del Mapa (por servidor o mundo, en {@code config/miscelanea_movil_mapa/<...>/puntos.json}) y la
 * navegación: con el móvil cerrado, una flecha y la distancia arriba de la pantalla hasta el punto o el amigo elegido;
 * se apaga sola al llegar.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Waypoints {
    private Waypoints() {}

    public static final int[] COLORES = {0xFFEF4B4B, 0xFFF5A623, 0xFFF5D94C, 0xFF35D07F, 0xFF4EA4F6, 0xFFB46FE0};

    public static final class Punto {
        public String nombre = "";
        public int color, x, y, z;
        public String dim = "minecraft:overworld";
    }

    public static final List<Punto> PUNTOS = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Navegando a un punto o a un amigo que comparte su ubicación (uno de los dos). */
    public static Punto destino;
    public static UUID destinoAmigo;
    private static boolean cargado;

    private static Path archivo() {
        return MapaCache.carpeta().resolve("puntos.json");
    }

    static void cargar() {
        PUNTOS.clear();
        destino = null;
        destinoAmigo = null;
        try {
            Path f = archivo();
            if (Files.exists(f)) {
                List<Punto> l = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Punto>>() {}.getType());
                if (l != null) for (Punto p : l) if (p != null && p.nombre != null) PUNTOS.add(p);
            }
        } catch (Exception ignored) {
        }
        cargado = true;
    }

    static void guardar() {
        if (!cargado) return;
        try {
            Path f = archivo();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(PUNTOS), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    static void salir() {
        PUNTOS.clear();
        destino = null;
        destinoAmigo = null;
        cargado = false;
    }

    static String dimActual() {
        var l = Minecraft.getInstance().level;
        return l == null ? "" : l.dimension().location().toString();
    }

    static void navegar(Punto p) {
        destino = p;
        destinoAmigo = null;
    }

    static void navegar(UUID amigo) {
        destino = null;
        destinoAmigo = amigo;
    }

    static void parar() {
        destino = null;
        destinoAmigo = null;
    }

    static boolean navegando() {
        return destino != null || destinoAmigo != null;
    }

    /** Destino actual: {x, z, otraDimension(0/1)} o null; nombre aparte. */
    static double[] objetivo() {
        if (destino != null) return new double[]{destino.x + 0.5, destino.z + 0.5, destino.dim.equals(dimActual()) ? 0 : 1};
        if (destinoAmigo != null) {
            MovilCliente.UbiAmigo u = MovilCliente.ubiDe(destinoAmigo);
            if (u == null) return null;
            return new double[]{u.x(), u.z(), u.dim().equals(dimActual()) ? 0 : 1};
        }
        return null;
    }

    static String nombreObjetivo() {
        if (destino != null) return destino.nombre;
        if (destinoAmigo != null) {
            MovilCliente.UbiAmigo u = MovilCliente.ubiDe(destinoAmigo);
            return u == null ? "" : u.nombre();
        }
        return "";
    }

    static int colorObjetivo() {
        return destino != null ? destino.color : Ui.AZUL;
    }

    /** Ángulo de la flecha (radianes, 0 = hacia delante) hacia un punto, desde el jugador. */
    static double angulo(LocalPlayer p, double x, double z) {
        double dx = x - p.getX(), dz = z - p.getZ();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return Math.toRadians(Mth.wrapDegrees(yaw - p.getYRot()));
    }

    static String distancia(double d) {
        return d >= 1000 ? String.format("%.1f km", d / 1000) : Math.round(d) + " m";
    }

    // ------------------------------------------------------------------ HUD de navegación
    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !navegando() || mc.screen instanceof MovilScreen) return;
        double[] o = objetivo();
        if (destinoAmigo != null && o == null) {
            // ya no comparte su ubicación
            mc.getToasts().addToast(new MovilToast(Ui.APP_MAPA, Component.translatable("movil.miscelanea.app_mapa").getString(),
                    Component.translatable("movil.miscelanea.mapa_ya_no_comparte").getString()));
            parar();
            return;
        }
        if (o == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        boolean llamada = MovilCliente.llEstado != com.miscelanea.movil.MovilRed.LL_FIN;
        int y = llamada ? 20 : 4;
        String nombre = nombreObjetivo();
        String dist;
        double d = Math.sqrt((o[0] - p.getX()) * (o[0] - p.getX()) + (o[1] - p.getZ()) * (o[1] - p.getZ()));
        if (o[2] != 0) dist = Component.translatable("movil.miscelanea.otra_dimension").getString();
        else dist = distancia(d);
        String s = Ui.cortar(nombre, 90) + "  " + dist;
        int ancho = Ui.ancho(s) + 26, x = w / 2 - ancho / 2;
        Ui.redondo(g, x, y, ancho, 17, 8, 0xD0101418);
        RenderSystem.enableBlend();
        if (o[2] == 0) Ui.flecha(g, x + 10, y + 8, 6, angulo(p, o[0], o[1]), colorObjetivo(), 0xFF000000);
        else Ui.circulo(g, x + 10, y + 8, 3, Ui.TEXTO2);
        Ui.texto(g, s, x + 20, y + 5, 0xFFFFFFFF);
        if (o[2] == 0 && d < 4) {
            mc.getToasts().addToast(new MovilToast(Ui.APP_MAPA, nombre, Component.translatable("movil.miscelanea.mapa_llegaste").getString()));
            MovilCliente.sonido(com.miscelanea.movil.MovilRegistro.MENSAJE.get(), 0.7F, 1.2F);
            parar();
        }
    }
}
