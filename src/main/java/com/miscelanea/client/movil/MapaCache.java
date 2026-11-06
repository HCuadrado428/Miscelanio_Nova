package com.miscelanea.client.movil;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mapa del móvil: pinta, como un mapa de vanilla (colores {@link MapColor} con sombreado por altura y profundidad del
 * agua), los chunks que el cliente tiene cargados, poco a poco y empezando por los de alrededor del jugador. Lo explorado se
 * guarda en regiones de 256x256 bloques (una textura de 256x256 cada una) en memoria y en disco:
 * {@code config/miscelanea_movil_mapa/<servidor-o-mundo>/<dimensión>/r.<x>.<z>.png}, así el mapa se va llenando al explorar.
 */
public final class MapaCache {
    private MapaCache() {}

    private static final Logger LOG = LogUtils.getLogger();
    static final int R = 256, MAX_REGIONES = 64;

    static final class Region {
        final int rx, rz;
        final NativeImage img;
        final DynamicTexture tex;
        final ResourceLocation rl;
        final long[] escaneado = new long[256];
        boolean subir, guardar;

        Region(int rx, int rz, NativeImage img) {
            this.rx = rx;
            this.rz = rz;
            this.img = img;
            tex = new DynamicTexture(img);
            rl = Minecraft.getInstance().getTextureManager().register("miscelanea_mapa_" + (sig++), tex);
        }

        void soltar() {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
    }

    private static int sig;
    private static final LinkedHashMap<Long, Region> REGIONES = new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<Long> SIN_ARCHIVO = new HashSet<>();
    private static String dimActual, carpetaServidor;
    private static int espiral;
    private static long ultGuardado;
    private static List<int[]> ESPIRAL;

    private static long clave(int rx, int rz) {
        return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ carpetas
    private static String limpio(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Carpeta de este servidor o mundo (también la usan los puntos guardados). */
    static Path carpeta() {
        Minecraft mc = Minecraft.getInstance();
        String n;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null)
            n = "mundo_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        else {
            ServerData sd = mc.getCurrentServer();
            n = sd != null ? "servidor_" + sd.ip : "desconocido";
        }
        return FMLPaths.CONFIGDIR.get().resolve("miscelanea_movil_mapa").resolve(limpio(n));
    }

    private static Path carpetaDim() {
        return carpeta().resolve(limpio(dimActual == null ? "overworld" : dimActual));
    }

    private static Path archivo(int rx, int rz) {
        return carpetaDim().resolve("r." + rx + "." + rz + ".png");
    }

    // ------------------------------------------------------------------ regiones
    /** La región si está en memoria o en disco (crear = hacerla nueva si no existe). */
    static Region region(int rx, int rz, boolean crear) {
        long k = clave(rx, rz);
        Region r = REGIONES.get(k);
        if (r != null) return r;
        if (!crear && SIN_ARCHIVO.contains(k)) return null;
        NativeImage img = null;
        Path f = archivo(rx, rz);
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                NativeImage leida = NativeImage.read(in);
                if (leida.getWidth() == R && leida.getHeight() == R && leida.format() == NativeImage.Format.RGBA) img = leida;
                else leida.close();
            } catch (Exception e) {
                LOG.debug("Mapa del móvil: no se pudo leer {}: {}", f, e.toString());
            }
        }
        if (img == null) {
            if (!crear) {
                SIN_ARCHIVO.add(k);
                return null;
            }
            img = new NativeImage(NativeImage.Format.RGBA, R, R, true);
        }
        SIN_ARCHIVO.remove(k);
        r = new Region(rx, rz, img);
        REGIONES.put(k, r);
        while (REGIONES.size() > MAX_REGIONES) {
            Iterator<Map.Entry<Long, Region>> it = REGIONES.entrySet().iterator();
            Region vieja = it.next().getValue();
            it.remove();
            if (vieja.guardar) guardar(vieja);
            vieja.soltar();
        }
        return r;
    }

    private static void guardar(Region r) {
        r.guardar = false;
        NativeImage copia = new NativeImage(NativeImage.Format.RGBA, R, R, false);
        copia.copyFrom(r.img);
        Path f = archivo(r.rx, r.rz);
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(f.getParent());
                copia.writeToFile(f);
            } catch (Exception e) {
                LOG.debug("Mapa del móvil: no se pudo guardar {}: {}", f, e.toString());
            } finally {
                copia.close();
            }
        });
    }

    static void guardarTodo() {
        for (Region r : REGIONES.values()) if (r.guardar) guardar(r);
        ultGuardado = Util.getMillis();
    }

    private static void vaciar() {
        guardarTodo();
        for (Region r : REGIONES.values()) r.soltar();
        REGIONES.clear();
        SIN_ARCHIVO.clear();
    }

    static void salir() {
        vaciar();
        dimActual = null;
        carpetaServidor = null;
        Waypoints.salir();
    }

    // ------------------------------------------------------------------ explorar
    private static List<int[]> espiral() {
        if (ESPIRAL == null) {
            List<int[]> l = new ArrayList<>();
            int rad = 12;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) if (dx * dx + dz * dz <= rad * rad) l.add(new int[]{dx, dz});
            l.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
            ESPIRAL = l;
        }
        return ESPIRAL;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null || mc.player == null) return;
        String dim = l.dimension().location().toString();
        String srv = carpeta().toString();
        if (!dim.equals(dimActual) || !srv.equals(carpetaServidor)) {
            vaciar();
            dimActual = dim;
            if (!srv.equals(carpetaServidor)) Waypoints.cargar();
            carpetaServidor = srv;
        }
        if (Util.getMillis() - ultGuardado > 30000) guardarTodo();
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        int vista = Math.min(12, mc.options.getEffectiveRenderDistance());
        List<int[]> esp = espiral();
        long ahora = Util.getMillis();
        int hechos = 0;
        for (int n = 0; n < 80 && hechos < 3; n++) {
            espiral = (espiral + 1) % esp.size();
            int[] o = esp.get(espiral);
            int d2 = o[0] * o[0] + o[1] * o[1];
            if (d2 > vista * vista) continue;
            int cx = pcx + o[0], cz = pcz + o[1];
            Region r = region(Math.floorDiv(cx, 16), Math.floorDiv(cz, 16), false);
            int i = Math.floorMod(cx, 16) + Math.floorMod(cz, 16) * 16;
            long cada = d2 <= 4 ? 2500 : d2 <= 36 ? 15000 : 60000;
            if (r != null && ahora - r.escaneado[i] < cada) continue;
            LevelChunk ch = l.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (ch == null || ch.isEmpty()) continue;
            if (r == null) r = region(Math.floorDiv(cx, 16), Math.floorDiv(cz, 16), true);
            pintar(l, ch, r, mc.player.getBlockY());
            r.escaneado[i] = ahora;
            hechos++;
        }
    }

    /** Altura (y del bloque visible) de una columna; en dimensiones con techo, bajo el jugador. */
    private static int altura(ClientLevel l, LevelChunk ch, int x, int z, int yJugador, BlockPos.MutableBlockPos p) {
        if (!l.dimensionType().hasCeiling()) return ch.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        int y = Math.min(yJugador + 2, l.getMaxBuildHeight() - 1);
        p.set(x, y, z);
        // si estás dentro de roca, sube hasta el aire
        while (y > l.getMinBuildHeight() && !ch.getBlockState(p).isAir()) p.setY(--y);
        while (y > l.getMinBuildHeight() && ch.getBlockState(p).isAir()) p.setY(--y);
        return y;
    }

    private static void pintar(ClientLevel l, LevelChunk ch, Region r, int yJugador) {
        int x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int[] norte = new int[16];
        // fila de justo al norte (otro chunk; si no está cargado, la misma altura)
        LevelChunk n = l.getChunkSource().getChunk(ch.getPos().x, ch.getPos().z - 1, ChunkStatus.FULL, false);
        for (int x = 0; x < 16; x++) norte[x] = n != null && !n.isEmpty() ? altura(l, n, x0 + x, z0 - 1, yJugador, p) : Integer.MIN_VALUE;
        int ox = Math.floorMod(x0, R), oz = Math.floorMod(z0, R);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = x0 + x, wz = z0 + z;
                int y = altura(l, ch, wx, wz, yJugador, p);
                int hNorte = norte[x] == Integer.MIN_VALUE ? y : norte[x];
                norte[x] = y;
                p.set(wx, y, wz);
                BlockState st = ch.getBlockState(p);
                MapColor mc = st.getMapColor(l, p);
                int bajo = 0;
                while (mc == MapColor.NONE && y > l.getMinBuildHeight() && bajo < 32) {
                    p.setY(--y);
                    st = ch.getBlockState(p);
                    mc = st.getMapColor(l, p);
                    bajo++;
                }
                int color;
                if (mc == MapColor.NONE) color = 0;
                else {
                    MapColor.Brightness b;
                    if (mc == MapColor.WATER) {
                        int prof = 0;
                        BlockPos.MutableBlockPos q = p.mutable();
                        while (prof < 10 && q.getY() > l.getMinBuildHeight() && !ch.getBlockState(q).getFluidState().isEmpty()) {
                            q.setY(q.getY() - 1);
                            prof++;
                        }
                        double d = prof * 0.1 + ((wx + wz) & 1) * 0.2;
                        b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    } else {
                        double d = (y - hNorte) * 0.8 + (((wx + wz) & 1) - 0.5) * 0.4;
                        b = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    }
                    color = mc.calculateRGBColor(b);
                }
                r.img.setPixelRGBA(ox + x, oz + z, color);
            }
        }
        r.subir = true;
        r.guardar = true;
    }

    // ------------------------------------------------------------------ dibujar
    /**
     * Dibuja el mapa en el rectángulo (x0,y0,w,h) con el centro del mapa en el bloque (cx,cz) y "esc" píxeles por bloque
     * (el recorte lo pone quien llama).
     */
    static void dibujar(GuiGraphics g, double cx, double cz, float esc, int x0, int y0, int w, int h) {
        double bx0 = cx - w / 2.0 / esc, bz0 = cz - h / 2.0 / esc, bx1 = cx + w / 2.0 / esc, bz1 = cz + h / 2.0 / esc;
        int rx0 = Math.floorDiv((int) Math.floor(bx0), R), rz0 = Math.floorDiv((int) Math.floor(bz0), R);
        int rx1 = Math.floorDiv((int) Math.floor(bx1), R), rz1 = Math.floorDiv((int) Math.floor(bz1), R);
        for (int rx = rx0; rx <= rx1; rx++)
            for (int rz = rz0; rz <= rz1; rz++) {
                Region r = region(rx, rz, false);
                if (r == null) continue;
                if (r.subir) {
                    r.tex.upload();
                    r.subir = false;
                }
                float sx = (float) (x0 + w / 2.0 + (rx * R - cx) * esc), sy = (float) (y0 + h / 2.0 + (rz * R - cz) * esc);
                // a píxel entero de pantalla para que no tiemble
                sx = Math.round(sx);
                sy = Math.round(sy);
                int tam = Math.round(R * esc);
                g.pose().pushPose();
                g.pose().translate(sx, sy, 0);
                g.blit(r.rl, 0, 0, tam, tam, 0, 0, R, R, R, R);
                g.pose().popPose();
            }
    }

    /** ¿Hay algo explorado en este bloque? (para el botón de "aún no has explorado esto"). */
    static boolean explorado(int x, int z) {
        Region r = region(Math.floorDiv(x, R), Math.floorDiv(z, R), false);
        return r != null && (r.img.getPixelRGBA(Math.floorMod(x, R), Math.floorMod(z, R)) >>> 24) != 0;
    }
}
