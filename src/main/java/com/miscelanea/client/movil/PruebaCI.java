package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prueba automática del móvil para GitHub Actions (NUNCA se activa jugando: solo con {@code -Dmiscelanea.prueba=true}).
 * La partida la crea MC-Runtime-Test; esto, ya dentro del mundo, abre el móvil y va pasando por la cámara (filtros,
 * zoom, horizontal, temporizador, flash, foto y vídeo corto), la galería y el visor, y las pantallas de llamada con
 * datos de mentira (grupo, una persona, entrante y manos libres), sacando una captura en cada paso a
 * {@code screenshots/} y apuntando en {@code prueba_miscelanea.txt} qué paso falló. Mientras dura, mantiene la edad del
 * jugador por debajo de 100 ticks para que MC-Runtime-Test no cierre el juego antes de tiempo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PruebaCI {
    private PruebaCI() {}

    private static final boolean ACTIVA = Boolean.getBoolean("miscelanea.prueba");

    private record Paso(String nombre, int espera, Runnable accion, boolean captura) {}

    private static final List<Paso> PASOS = new ArrayList<>();
    private static final List<String> RESUMEN = new ArrayList<>();
    private static int paso = -1, espera, ticksEnMundo, fallos;
    /** Fotogramas dibujados: con gráficos por software (y más con shaders) puede haber muy pocos por tick. */
    private static int fotogramas, fotogramasPaso;

    /** Tiempo de procesador (ns) de los fotogramas, de los bloques-entidad y de la pantalla abierta, para el perfil. */
    private static long nsFotograma, nsBloques, nsPantalla, t0Fotograma, t0Bloques, t0Pantalla;

    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (!ACTIVA) return;
        if (e.phase == TickEvent.Phase.START) t0Fotograma = System.nanoTime();
        else {
            fotogramas++;
            if (t0Fotograma != 0) nsFotograma += System.nanoTime() - t0Fotograma;
        }
    }

    @SubscribeEvent
    public static void etapa(net.minecraftforge.client.event.RenderLevelStageEvent e) {
        if (!ACTIVA) return;
        if (e.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)
            nieblaFin = com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        if (e.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) t0Bloques = System.nanoTime();
        else if (e.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES && t0Bloques != 0) {
            nsBloques += System.nanoTime() - t0Bloques;
            t0Bloques = 0;
        }
    }

    @SubscribeEvent
    public static void pantallaAntes(net.minecraftforge.client.event.ScreenEvent.Render.Pre e) {
        if (ACTIVA) t0Pantalla = System.nanoTime();
    }

    @SubscribeEvent
    public static void pantallaDespues(net.minecraftforge.client.event.ScreenEvent.Render.Post e) {
        if (ACTIVA && t0Pantalla != 0) {
            nsPantalla += System.nanoTime() - t0Pantalla;
            t0Pantalla = 0;
        }
    }
    private static boolean terminada;
    private static volatile net.minecraft.core.BlockPos gramola;

    private static void paso(String nombre, int espera, Runnable accion) {
        PASOS.add(new Paso(nombre, espera, accion, true));
    }

    private static void sin(String nombre, int espera, Runnable accion) {
        PASOS.add(new Paso(nombre, espera, accion, false));
    }

    private static MovilScreen movil() {
        return Minecraft.getInstance().screen instanceof MovilScreen s ? s : null;
    }

    private static void abrirVista(Vista v) {
        MovilScreen s = movil();
        if (s == null) throw new IllegalStateException("el móvil no está abierto");
        s.abrir(v, false);
    }

    private static void tecla(int k) {
        MovilScreen s = movil();
        if (s == null || !s.arriba().tecla(k, 0, 0)) throw new IllegalStateException("la vista no ha cogido la tecla " + k);
    }

    private static Vista arriba() {
        MovilScreen s = movil();
        if (s == null) throw new IllegalStateException("el móvil no está abierto");
        return s.arriba();
    }

    private static void llamadaFalsa(int estado, boolean grupo, int personas, int dos) {
        MovilCliente.PARTES.clear();
        String[] nombres = {"Dtuoncat", "Elena", "Wemmbu_Fan"};
        for (int i = 0; i < personas; i++)
            MovilCliente.PARTES.add(new MovilCliente.Parte(UUID.nameUUIDFromBytes(nombres[i].getBytes()), nombres[i],
                    i == 2 ? com.miscelanea.movil.Llamadas.P_SONANDO : i == 1 && grupo ? com.miscelanea.movil.Llamadas.P_ESPERA : com.miscelanea.movil.Llamadas.P_DENTRO));
        MovilCliente.llGrupo = grupo;
        MovilCliente.llNombre = grupo ? "Los del server" : nombres[0];
        MovilCliente.llOtro = MovilCliente.PARTES.isEmpty() ? null : MovilCliente.PARTES.get(0).id();
        MovilCliente.llEstado = estado;
        MovilCliente.llInicio = net.minecraft.Util.getMillis() - 83_000;
        MovilCliente.llVideoPermitido = true;
        MovilCliente.dosEstado = dos;
        MovilCliente.dosId = com.miscelanea.movil.Llamadas.OCULTO;
        MovilCliente.dosNombre = "Desconocido";
    }

    private static void sinLlamada() {
        MovilCliente.llEstado = MovilRed.LL_FIN;
        MovilCliente.PARTES.clear();
        MovilCliente.dosEstado = -1;
        MovilCliente.manosLibres = false;
        MovilCliente.miVideo = false;
    }

    private static void preparar() {
        Minecraft mc = Minecraft.getInstance();
        sin("preparar", 40, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                sp.setGameMode(GameType.CREATIVE);
                sp.getInventory().setItem(0, new ItemStack(MovilRegistro.MOVIL.get()));
                sp.getInventory().selected = 0;
                sp.serverLevel().setDayTime(6000);
                sp.serverLevel().setWeatherParameters(6000, 0, false, false);
            });
        });
        paso("00_mundo", 10, () -> {
            // sin el tutorial ni los avisos de arriba a la derecha, que tapan cosas
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
        });
        paso("01_inicio", 30, () -> MovilCliente.abrir(0));
        paso("02_camara", 50, () -> abrirVista(new VistasFotos.Camara()));
        for (int f = 1; f < Filtros.N; f++) {
            int ff = f;
            paso("03_filtro_" + f, 12, () -> MovilCaptura.filtro = ff);
        }
        paso("03_foto_gameboy", 30, () -> {
            MovilCaptura.filtro = 7;
            tecla(GLFW.GLFW_KEY_SPACE);
        });
        paso("04_zoom", 20, () -> {
            MovilCaptura.filtro = 0;
            MovilCaptura.zoom = 2.5F;
        });
        paso("05_horizontal", 30, () -> {
            MovilCaptura.zoom = 1;
            tecla(GLFW.GLFW_KEY_R);
        });
        sin("vuelve_vertical", 20, () -> tecla(GLFW.GLFW_KEY_R));
        paso("06_foto", 6, () -> tecla(GLFW.GLFW_KEY_SPACE));
        sin("espera_foto", 40, () -> {});
        paso("07_temporizador", 25, () -> {
            tecla(GLFW.GLFW_KEY_T);
            tecla(GLFW.GLFW_KEY_SPACE);
        });
        sin("espera_temporizador", 70, () -> tecla(GLFW.GLFW_KEY_T));   // T dos veces más: 10 s y apagado
        sin("temporizador_fuera", 5, () -> tecla(GLFW.GLFW_KEY_T));
        paso("08_flash", 3, () -> {
            tecla(GLFW.GLFW_KEY_L);
            tecla(GLFW.GLFW_KEY_SPACE);
        });
        sin("espera_flash", 40, () -> {
            tecla(GLFW.GLFW_KEY_L);
            tecla(GLFW.GLFW_KEY_L);
        });
        paso("09_grabando", 60, () -> {
            MovilCaptura.filtro = 2;   // sepia: el filtro también va en el vídeo
            tecla(GLFW.GLFW_KEY_V);
            tecla(GLFW.GLFW_KEY_SPACE);
        });
        sin("para_video", 60, () -> {
            tecla(GLFW.GLFW_KEY_SPACE);
            MovilCaptura.filtro = 0;
        });
        paso("10_galeria", 40, () -> {
            movil().atras();
            abrirVista(new VistasFotos.Galeria(null));
        });
        paso("11_visor", 60, () -> {
            if (MovilCliente.FOTOS.isEmpty()) throw new IllegalStateException("la galería está vacía (no se guardó nada)");
            MovilCliente.Foto f = MovilCliente.FOTOS.get(0);
            RESUMEN.add("  galería: " + MovilCliente.FOTOS.size() + " (la última " + (f.ms() > 0 ? "es un vídeo de " + f.ms() + " ms" : "es una foto") + ")");
            abrirVista(new VistasFotos.Foto(MovilRed.FOTO_PROPIA, f.id(), f.ms()));
        });
        paso("12_llamada_grupo", 20, () -> {
            movil().atras();
            llamadaFalsa(MovilRed.LL_ACTIVA, true, 3, MovilRed.LL_ENTRANTE);
            abrirVista(new VistasTelefono.Llamada());
        });
        paso("13_llamada", 20, () -> llamadaFalsa(MovilRed.LL_ACTIVA, false, 1, MovilRed.LL_ESPERA));
        paso("14_entrante", 20, () -> llamadaFalsa(MovilRed.LL_ENTRANTE, false, 1, -1));
        paso("15_manos_libres", 20, () -> {
            llamadaFalsa(MovilRed.LL_ACTIVA, false, 1, -1);
            MovilCliente.manosLibres = true;
            mc.setScreen(null);
            mc.getToasts().clear();
        });
        paso("16_teclado", 20, () -> {
            sinLlamada();
            MovilCliente.abrir(0);
            abrirVista(new VistasTelefono.Telefono());
        });
        paso("17_ajustes", 20, () -> abrirVista(new VistasApps.Ajustes()));
        // ---- gramola: el videoclip y la cámara de cine
        sin("gramola_poner", 20, () -> {
            mc.setScreen(null);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                var lv = sp.serverLevel();
                net.minecraft.core.Direction f = sp.getDirection();
                net.minecraft.core.BlockPos g = sp.blockPosition().relative(f, 4);
                gramola = g;
                // suelo despejado alrededor
                // suelo plano y despejado alrededor, con un escalón de bajada justo delante de la gramola (para ver
                // que el decorado se pone a la altura de los pies)
                for (int dx = -6; dx <= 6; dx++)
                    for (int dz = -6; dz <= 6; dz++) {
                        for (int dy = 0; dy <= 4; dy++) lv.setBlockAndUpdate(g.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        lv.setBlockAndUpdate(g.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    }
                for (int dx = -2; dx <= 2; dx++)
                    for (int k = 1; k <= 4; k++) lv.setBlockAndUpdate(g.relative(f.getOpposite(), k).relative(f.getClockWise(), dx).below(),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                for (int dx = -2; dx <= 2; dx++)
                    for (int k = 1; k <= 4; k++) lv.setBlockAndUpdate(g.relative(f.getOpposite(), k).relative(f.getClockWise(), dx).below(2),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                lv.setBlockAndUpdate(g, com.miscelanea.registry.ModBlocks.GRAMOLA.get().defaultBlockState()
                        .setValue(com.miscelanea.baile.PistaBaileBlock.FACING, f.getOpposite()));
            });
        });
        paso("20_gramola_menu", 30, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp.serverLevel().getBlockEntity(gramola) instanceof com.miscelanea.baile.PistaBaileBlockEntity be) be.usar(sp);
            });
        });
        paso("21_videoclip_hotel_inicio", 40, () -> com.miscelanea.client.baile.BaileCliente.pruebaEmpezar("weapon_of_choice"));
        paso("22_videoclip_hotel_8s", 120, () -> {
            if (!com.miscelanea.client.baile.BaileCliente.bailando()) throw new IllegalStateException("no ha empezado a bailar");
        });
        paso("23_videoclip_hotel_20s", 240, () -> {});
        paso("24_tras_salir", 40, com.miscelanea.client.baile.BaileCliente::pruebaSalir);
        sin("gramola_otra", 30, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp.serverLevel().getBlockEntity(gramola) instanceof com.miscelanea.baile.PistaBaileBlockEntity pb) pb.usar(sp);
            });
        });
        paso("25_videoclip_disco", 160, () -> com.miscelanea.client.baile.BaileCliente.pruebaEmpezar("___otra"));
        paso("26_tras_salir_disco", 40, com.miscelanea.client.baile.BaileCliente::pruebaSalir);
        sin("pociones", 1, () -> {
            // el soporte para pociones: rara + ingrediente
            var rara = net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(net.minecraft.world.item.Items.POTION), net.minecraft.world.item.alchemy.Potions.AWKWARD);
            net.minecraft.world.item.Item[] ing = {net.minecraft.world.item.Items.RED_MUSHROOM, net.minecraft.world.item.Items.BROWN_MUSHROOM,
                    net.minecraft.world.item.Items.ECHO_SHARD, net.minecraft.world.item.Items.INK_SAC, net.minecraft.world.item.Items.AMETHYST_SHARD,
                    net.minecraft.world.item.Items.FEATHER, net.minecraft.world.item.Items.SNOWBALL, net.minecraft.world.item.Items.WHEAT,
                    net.minecraft.world.item.Items.COCOA_BEANS};
            StringBuilder sb = new StringBuilder("  pociones:");
            for (var i : ing) {
                ItemStack r = net.minecraftforge.common.brewing.BrewingRecipeRegistry.getOutput(rara, new ItemStack(i));
                String n = r.isEmpty() ? "NADA" : net.minecraft.world.item.alchemy.PotionUtils.getPotion(r).getName("");
                sb.append(' ').append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i).getPath()).append('=').append(n);
                if (r.isEmpty()) throw new IllegalStateException("la poción rara + " + i + " no da nada");
            }
            RESUMEN.add(sb.toString());
        });
        // recorte de niveles imposibles (equilibrio): todo lo que pase de su máximo se deja en el máximo
        sin("recorte_niveles", 5, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                ItemStack espada = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 10);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1);
                ItemStack peto = new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE);
                peto.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 7);
                peto.enchant(net.minecraft.world.item.enchantment.Enchantments.THORNS, 3);
                ItemStack libro = net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                        new net.minecraft.world.item.enchantment.EnchantmentInstance(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 6));
                sp.getInventory().setItem(3, espada);
                sp.getInventory().setItem(4, peto);
                sp.getInventory().setItem(5, libro);
                int xp = sp.totalExperience;
                com.miscelanea.equilibrio.RecorteNiveles.recortarInventario(sp);
                ItemStack e3 = sp.getInventory().getItem(3), e5 = sp.getInventory().getItem(5);
                var m1 = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(e3);
                var m2 = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(sp.getInventory().getItem(4));
                var m3 = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(e5);
                RESUMEN.add("  recorte: espada " + m1 + " · peto " + m2 + " · libro " + m3 + " · xp +" + (sp.totalExperience - xp)
                        + " · espada con durabilidad: " + e3.isDamageableItem());
                boolean bien = m1.size() == 3 && m1.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 0) == 5 && m1.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 0) == 3
                        && m1.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.MENDING, 0) == 1
                        && m2.size() == 2 && m2.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 0) == 4 && m2.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.THORNS, 0) == 3
                        && e5.is(net.minecraft.world.item.Items.ENCHANTED_BOOK) && m3.size() == 1 && m3.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 0) == 4
                        && sp.totalExperience > xp && e3.isDamageableItem();
                if (!bien) RESUMEN.add("  FALLO: el recorte de niveles imposibles no ha salido como debía");
            });
        });
        rendimiento();
        laser();
        eco();
        pistola();
        sin("cerrar", 10, () -> {
            mc.setScreen(null);
            RESUMEN.add("  cara propia (la de su modelo) guardada: " + (CarasCliente.tiene(mc.player.getUUID()) ? "sí" : "NO"));
        });
    }

    // ------------------------------------------------------------------ rendimiento de la sala recreativa
    private static long PERFIL_T0;
    private static Object GRABACION;
    private static int PERFIL_F0;
    private static final long[] PERFIL_NS = new long[3];
    /** Máquinas donde "juega otro" durante el perfil, y su pantalla de mentira. */
    private static final java.util.List<net.minecraft.core.BlockPos> JUGANDO = new java.util.ArrayList<>();
    private static final com.miscelanea.client.arcade.Pantalla FALSA = new com.miscelanea.client.arcade.Pantalla();
    private static int tFalsa;
    private static boolean buscarMaquinas;

    /** Una imagen de partida que cambia (fondo, cosas que se mueven y unos textos), como la que retransmite un jugador. */
    private static void retransmitir() {
        Minecraft mc = Minecraft.getInstance();
        if (!buscarMaquinas || mc.level == null || mc.player == null) return;
        if (++tFalsa % 2 != 0) return;
        if (JUGANDO.size() < 5 && tFalsa % 20 == 0) {
            JUGANDO.clear();
            for (var q : net.minecraft.core.BlockPos.betweenClosed(mc.player.blockPosition().offset(-24, -2, -24), mc.player.blockPosition().offset(24, 3, 24)))
                if (JUGANDO.size() < 5 && mc.level.getBlockEntity(q) instanceof com.miscelanea.arcade.RecreativaBlockEntity) JUGANDO.add(q.immutable());
        }
        if (JUGANDO.isEmpty()) return;
        var p = FALSA;
        p.font = Minecraft.getInstance().font;
        p.empezar();
        for (int y = 0; y < com.miscelanea.client.arcade.Pantalla.H; y++) p.rect(0, y, com.miscelanea.client.arcade.Pantalla.W, 1, 33 + (y + tFalsa) / 24 % 4);
        for (int k = 0; k < 12; k++) p.rect((k * 37 + tFalsa * 3) % 256, 40 + k * 11, 12, 8, 11 + k % 20);
        p.texto("1P " + tFalsa * 10, 6, 4, 7);
        p.textoD("HI 99999", 250, 4, 19);
        p.textoC("NIVEL " + (tFalsa / 100 + 1), 128, 176, 20);
        for (var q : JUGANDO) com.miscelanea.client.arcade.ArcadeCliente.local(q, p);
    }
    private static final java.util.List<String> PERFIL = new java.util.ArrayList<>();

    private static void medir(String fase) {
        long t = System.currentTimeMillis();
        if (PERFIL_T0 != 0) {
            double s = (t - PERFIL_T0) / 1000.0;
            int n = Math.max(1, fotogramas - PERFIL_F0);
            // ms de procesador por fotograma: todo el fotograma, los bloques-entidad (renderers del mundo) y la pantalla abierta
            RESUMEN.add(String.format(java.util.Locale.ROOT, "  rendimiento %s: %.1f fps · %.1f ms/fotograma · bloques-entidad %.2f ms · pantalla %.2f ms (%d fotogramas)",
                    fase, (fotogramas - PERFIL_F0) / Math.max(0.01, s), (nsFotograma - PERFIL_NS[0]) / 1e6 / n,
                    (nsBloques - PERFIL_NS[1]) / 1e6 / n, (nsPantalla - PERFIL_NS[2]) / 1e6 / n, fotogramas - PERFIL_F0));
        }
        PERFIL.add(fase + "=" + t);
        PERFIL_T0 = t;
        PERFIL_F0 = fotogramas;
        PERFIL_NS[0] = nsFotograma;
        PERFIL_NS[1] = nsBloques;
        PERFIL_NS[2] = nsPantalla;
    }

    /**
     * Resumen del perfil en el propio informe: por fase, qué métodos de Miscelánea están en la pila del hilo de dibujo
     * (contando el tiempo en Java y el que pasa en OpenGL), en % de las muestras de esa fase.
     */
    private static void resumirPerfil(java.nio.file.Path jfr) {
        try {
            java.util.List<String[]> fases = new java.util.ArrayList<>();
            for (String l : PERFIL) fases.add(l.split("="));
            java.util.Map<String, java.util.Map<String, Integer>> por = new java.util.LinkedHashMap<>();
            java.util.Map<String, Integer> total = new java.util.HashMap<>(), nativo = new java.util.HashMap<>();
            for (var e : jdk.jfr.consumer.RecordingFile.readAllEvents(jfr)) {
                String tipo = e.getEventType().getName();
                if (!tipo.equals("jdk.ExecutionSample") && !tipo.equals("jdk.NativeMethodSample")) continue;
                var hilo = e.getThread("sampledThread");
                if (hilo == null || !"Render thread".equals(hilo.getJavaName()) || e.getStackTrace() == null) continue;
                long t = e.getStartTime().toEpochMilli();
                String fase = null;
                for (int i = 0; i + 1 < fases.size(); i++)
                    if (t >= Long.parseLong(fases.get(i)[1]) && t < Long.parseLong(fases.get(i + 1)[1])) fase = fases.get(i + 1)[0];
                if (fase == null) continue;
                total.merge(fase, 1, Integer::sum);
                if (tipo.equals("jdk.NativeMethodSample")) nativo.merge(fase, 1, Integer::sum);
                var m = por.computeIfAbsent(fase, k -> new java.util.HashMap<>());
                java.util.Set<String> vistos = new java.util.HashSet<>();
                for (var fr : e.getStackTrace().getFrames()) {
                    if (fr.getMethod() == null || fr.getMethod().getType() == null) continue;
                    String clase = fr.getMethod().getType().getName();
                    if (!clase.startsWith("com.miscelanea.") || clase.contains("PruebaCI") || clase.contains("$$Lambda")) continue;
                    String n = clase.substring(clase.lastIndexOf('.') + 1) + "." + fr.getMethod().getName();
                    if (vistos.add(n)) m.merge(n, 1, Integer::sum);
                }
            }
            for (var f : por.entrySet()) {
                int n = total.getOrDefault(f.getKey(), 1);
                StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT, "  perfil %s (%d muestras, %d%% en nativo):",
                        f.getKey(), n, 100 * nativo.getOrDefault(f.getKey(), 0) / n));
                f.getValue().entrySet().stream().filter(x -> !x.getKey().startsWith("__") && !x.getKey().endsWith(".invoke"))
                        .sorted((a, c) -> c.getValue() - a.getValue()).limit(12)
                        .forEach(x -> b.append(String.format(java.util.Locale.ROOT, " %s %.1f%%", x.getKey(), 100.0 * x.getValue() / n)));
                RESUMEN.add(b.toString());
            }
        } catch (Throwable t) {
            RESUMEN.add("  rendimiento: no se pudo resumir el perfil: " + t);
        }
    }

    /** Una sala recreativa llena (máquinas, juegos de mesa, bolera…) y luego jugando a dos recreativas; se mide cada fase. */
    private static void rendimiento() {
        Minecraft mc = Minecraft.getInstance();
        sin("perfil_vacio", 200, () -> {
            mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.setXRot(15);
            medir("inicio");
            try {
                var r = new jdk.jfr.Recording();
                r.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(10));
                // también el tiempo dentro de código nativo (las llamadas de OpenGL), con la pila Java que lo pidió
                r.enable("jdk.NativeMethodSample").withPeriod(java.time.Duration.ofMillis(10));
                r.start();
                GRABACION = r;
            } catch (Throwable t) {
                RESUMEN.add("  rendimiento: sin perfil (JFR): " + t);
            }
        });
        sin("perfil_sala", 20, () -> {
            medir("sin_sala");
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                var mira = sp.getDirection();
                var lado = mira.getClockWise();
                var o = sp.blockPosition().relative(mira, 3);
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -12).below(), o.relative(lado, 12).relative(mira, 18).above(4)))
                    l.setBlock(b, b.getY() < o.getY() ? net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                java.util.List<net.minecraft.world.item.Item> cosas = new java.util.ArrayList<>(java.util.List.of(
                        com.miscelanea.registry.ModItems.RECREATIVA.get(), com.miscelanea.registry.ModItems.RECREATIVA_DOBLE.get(),
                        com.miscelanea.registry.ModItems.CABINA_CARRERAS.get(), com.miscelanea.registry.ModItems.RECREATIVA_PISTOLA.get(),
                        com.miscelanea.registry.ModItems.MESA_COCTEL.get(), com.miscelanea.registry.ModItems.CARTEL_RECORDS.get(),
                        com.miscelanea.registry.ModItems.TABLON_RECORDS.get(), com.miscelanea.registry.ModItems.TABLERO.get(),
                        com.miscelanea.registry.ModItems.IMPERIO.get(), com.miscelanea.registry.ModItems.PARCHIS.get(),
                        com.miscelanea.registry.ModItems.OCA.get(), com.miscelanea.registry.ModItems.CUATRO.get(),
                        com.miscelanea.registry.ModItems.FLOTA.get(), com.miscelanea.registry.ModItems.DOMINO.get(),
                        com.miscelanea.registry.ModItems.MESA_CARTAS.get(), com.miscelanea.registry.ModItems.BILLAR.get(),
                        com.miscelanea.registry.ModItems.FUTBOLIN.get(), com.miscelanea.registry.ModItems.AIRHOCKEY.get(),
                        com.miscelanea.registry.ModItems.BOLERA.get(), com.miscelanea.registry.ModItems.PISTA_BOLOS.get()));
                for (var r : com.miscelanea.registry.ModItems.recreativasJuego()) cosas.add(r.get());
                int k = 0, puestos = 0;
                for (var it : cosas) {
                    int fila = k / 8, col = k % 8;
                    k++;
                    var donde = o.relative(mira, 3 + fila * 5).relative(lado, -10 + col * 3);
                    try {
                        sp.teleportTo(donde.relative(mira.getOpposite(), 2).getX() + 0.5, donde.getY(), donde.relative(mira.getOpposite(), 2).getZ() + 0.5);
                        sp.setYRot(mira.toYRot());
                        var st = new ItemStack(it);
                        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(donde.below()), net.minecraft.core.Direction.UP, donde.below(), false);
                        var ctx = new net.minecraft.world.item.context.BlockPlaceContext(l, sp, net.minecraft.world.InteractionHand.MAIN_HAND, st, hit);
                        if (it instanceof net.minecraft.world.item.BlockItem bi && bi.place(ctx).consumesAction()) {
                            puestos++;
                            // con partida, como en una sala de verdad (un tablero recién puesto no dibuja nada)
                            if (l.getBlockEntity(donde) instanceof com.miscelanea.juegos.TableroBlockEntity tb) tb.reiniciar(0);
                            if (l.getBlockEntity(donde) instanceof com.miscelanea.bolos.BoleraBlockEntity)
                                l.getBlockState(donde).use(l, sp, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(donde), net.minecraft.core.Direction.UP, donde, false));
                        }
                    } catch (Throwable t) {
                        RESUMEN.add("  rendimiento: no se pudo poner " + it + ": " + t);
                    }
                }
                sp.teleportTo(o.relative(mira.getOpposite(), 3).getX() + 0.5, o.getY(), o.relative(mira.getOpposite(), 3).getZ() + 0.5);
                sp.setYRot(mira.toYRot());
                sp.setXRot(18);
                RESUMEN.add("  rendimiento: sala con " + puestos + " de " + cosas.size() + " cosas");
            });
        });
        sin("perfil_mirar", 300, () -> {
            medir("colocar");
            // otros jugadores jugando en cinco máquinas: su pantalla llega 10 veces por segundo, como la retransmisión
            // (se buscan poco a poco: las máquinas recién puestas tardan en llegar al cliente)
            JUGANDO.clear();
            buscarMaquinas = true;
        });
        sin("perfil_jugar", 5, () -> {
            medir("sala");
            RESUMEN.add("  rendimiento: " + JUGANDO.size() + " máquinas con partida retransmitida");
            // a jugar en la primera recreativa (sin menú: se arranca la mazmorra directamente)
            var b = mc.level.getBlockEntity(mc.player.blockPosition()) ;
            net.minecraft.core.BlockPos maquina = null;
            for (var q : net.minecraft.core.BlockPos.betweenClosed(mc.player.blockPosition().offset(-14, -1, -14), mc.player.blockPosition().offset(14, 2, 14)))
                if (mc.level.getBlockEntity(q) instanceof com.miscelanea.arcade.RecreativaBlockEntity
                        && (maquina == null || q.distSqr(mc.player.blockPosition()) < maquina.distSqr(mc.player.blockPosition()))) maquina = q.immutable();
            if (maquina == null) throw new IllegalStateException("no hay recreativa");
            var pant = new com.miscelanea.client.arcade.ArcadeScreen(maquina, 99, true, false, false, "");
            mc.setScreen(pant);
            try {
                var m = com.miscelanea.client.arcade.ArcadeScreen.class.getDeclaredMethod("empezar", String.class, long.class, int.class);
                m.setAccessible(true);
                m.invoke(pant, "mazmorra", 7L, 1);
            } catch (Exception ex) {
                throw new IllegalStateException("no se pudo empezar la partida: " + ex);
            }
        });
        paso("70_perfil_mazmorra", 300, () -> {});
        sin("perfil_turbo", 5, () -> {
            medir("mazmorra");
            try {
                var m = com.miscelanea.client.arcade.ArcadeScreen.class.getDeclaredMethod("empezar", String.class, long.class, int.class);
                m.setAccessible(true);
                m.invoke(mc.screen, "turbo", 7L, 1);
            } catch (Exception ex) {
                throw new IllegalStateException("no se pudo empezar la partida: " + ex);
            }
        });
        paso("71_perfil_turbo", 300, () -> {});
        sin("perfil_fin", 5, () -> {
            medir("turbo");
            buscarMaquinas = false;
            JUGANDO.clear();
            mc.setScreen(null);
            try {
                Files.write(new File(mc.gameDirectory, "perfil_fases.txt").toPath(), PERFIL);
                if (GRABACION instanceof jdk.jfr.Recording r) {
                    r.stop();
                    r.dump(new File(mc.gameDirectory, "perfil.jfr").toPath());
                    r.close();
                    resumirPerfil(new File(mc.gameDirectory, "perfil.jfr").toPath());
                }
            } catch (Throwable t) {
                RESUMEN.add("  rendimiento: no se pudo guardar el perfil: " + t);
            }
        });
        paso("72_perfil_sala", 5, () -> {});
    }

    // ------------------------------------------------------------------ puntero láser: el punto en la mira
    private static boolean mantenerUso;
    private static net.minecraft.world.entity.decoration.ArmorStand camaraFuera;
    private static int fovAntes = 70;

    /**
     * Pared negra delante, el puntero encendido (clic derecho mantenido) y se mide en la imagen dónde cae el punto: en
     * primera persona tiene que estar en el centro (la mira), también de gigante y con otro FOV; visto desde fuera, donde
     * mira el jugador. Para medir se quita el rayo y el HUD (y la mano) y se busca el rojo en la captura.
     */
    private static void laser() {
        Minecraft mc = Minecraft.getInstance();
        sin("laser_poner", 30, () -> {
            mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -8).relative(mira, -3).below(), o.relative(lado, 8).relative(mira, 11).above(16)))
                    l.setBlock(b, b.getY() < o.getY() ? net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState() : aire, 2);
                for (int dx = -8; dx <= 8; dx++)
                    for (int dy = -1; dy <= 16; dy++)
                        l.setBlock(o.relative(mira, 10).relative(lado, dx).above(dy), net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
                // fuera los objetos tirados (los libros de la mesa de antes son rojizos y confunden la medida)
                l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(o).inflate(24)).forEach(net.minecraft.world.entity.Entity::discard);
                sp.setGameMode(GameType.SURVIVAL);
                sp.removeAllEffects();
                sp.getInventory().setItem(0, new ItemStack(com.miscelanea.laser.LaserRegistro.PUNTERO.get()));
                sp.getInventory().selected = 0;
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 0F);
            });
        });
        sin("laser_encender", 30, () -> {
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 0;
            mantenerUso = true;
            mc.options.keyUse.setDown(true);
        });
        paso("80_laser_primera", 10, () -> {
            if (!com.miscelanea.laser.Laser.apuntando(mc.player)) throw new IllegalStateException("el puntero no se ha encendido");
        });
        medirLaser("laser_primera", false, true);
        sin("laser_fov", 10, () -> {
            fovAntes = mc.options.fov().get();
            mc.options.fov().set(105);
        });
        paso("81_laser_fov105", 10, () -> {});
        medirLaser("laser_fov105", false, true);
        sin("laser_gigante", 60, () -> {
            mc.options.fov().set(fovAntes);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.miscelanea.pociones.PocionesRegistro.GIGANTE.get(), 4000, 1));
            });
        });
        paso("82_laser_gigante", 10, () -> RESUMEN.add(String.format("  láser: de gigante, ojos a %.2f bloques del suelo", mc.player.getEyeHeight())));
        medirLaser("laser_gigante", false, true);
        sin("laser_normal", 60, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) sp.removeAllEffects();
            });
        });
        // como lo ve otro: una cámara a un lado que mira a la pared (el punto se calcula desde los ojos del jugador)
        sin("laser_fuera_poner", 20, () -> {
            var mira = mc.player.pick(64, 1.0F, false).getLocation();
            Vec3 ojo = mc.player.getEyePosition();
            Vec3 fwd = mc.player.getViewVector(1.0F);
            Vec3 derecha = new Vec3(-fwd.z, 0, fwd.x).normalize();
            Vec3 pos = ojo.add(derecha.scale(4)).add(fwd.scale(1)).add(0, 0.6, 0);
            Vec3 d = mira.subtract(pos);
            float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float xRot = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            camaraFuera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, pos.x, pos.y, pos.z);
            camaraFuera.moveTo(pos.x, pos.y - camaraFuera.getEyeHeight(), pos.z, yRot, xRot);
            camaraFuera.setYHeadRot(yRot);
            mc.setCameraEntity(camaraFuera);
        });
        paso("83_laser_desde_fuera", 10, () -> {});
        medirLaser("laser_desde_fuera", true, true);
        sin("laser_fuera_quitar", 10, () -> mc.setCameraEntity(mc.player));
        paso("84_laser_tercera", 30, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        sin("laser_fin", 10, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mantenerUso = false;
            mc.options.keyUse.setDown(false);
            com.miscelanea.client.laser.LaserCliente.pruebaSinRayo = false;
            mc.options.hideGui = false;
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) sp.setGameMode(GameType.CREATIVE);
            });
        });
    }

    // ------------------------------------------------------------------ revólver de ruleta (pistola)
    private static volatile net.minecraft.core.BlockPos piO;
    private static volatile net.minecraft.core.Direction piMira, piLado;
    private static volatile UUID piHusk;
    private static final java.util.Map<String, Object> PI = new java.util.concurrent.ConcurrentHashMap<>();
    private static net.minecraftforge.common.util.FakePlayer piForastero;

    private static void piSrv(java.util.function.Consumer<ServerPlayer> f) {
        Minecraft mc = Minecraft.getInstance();
        var srv = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            try {
                f.accept(sp);
            } catch (Throwable t) {
                PI.put("error", t.toString());
                Miscelanea.LOGGER.error("[PruebaCI] pistola (servidor)", t);
            }
        });
    }

    /** Punto del campo de tiro: m bloques hacia delante, l a la derecha, y de altura sobre el suelo (centro del bloque de salida). */
    private static Vec3 piPunto(double m, double l, double y) {
        var o = piO;
        return new Vec3(o.getX() + 0.5 + piMira.getStepX() * m + piLado.getStepX() * l, o.getY() + y,
                o.getZ() + 0.5 + piMira.getStepZ() * m + piLado.getStepZ() * l);
    }

    private static void piApuntar(Vec3 q) {
        var p = Minecraft.getInstance().player;
        Vec3 d = q.subtract(p.getEyePosition());
        float y = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float x = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.setYRot(y);
        p.setXRot(x);
        p.yRotO = y;
        p.xRotO = x;
        p.setYHeadRot(y);
    }

    private static void piDisparar() {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static net.minecraft.world.entity.monster.Husk piHusk(ServerPlayer sp) {
        return piHusk == null ? null : (net.minecraft.world.entity.monster.Husk) sp.serverLevel().getEntity(piHusk);
    }

    private static ItemStack piRevolver(int oxido, Object... encs) {
        ItemStack r = new ItemStack(com.miscelanea.registry.ModItems.REVOLVER.get());
        for (int i = 0; i + 1 < encs.length; i += 2)
            r.enchant(((net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.enchantment.Enchantment>) encs[i]).get(), (Integer) encs[i + 1]);
        if (oxido > 0) r.getOrCreateTag().putInt(com.miscelanea.armasfuego.pistola.Tambor.OXIDO, oxido);
        return r;
    }

    private static void piComprobar(String que, boolean bien, String detalle) {
        RESUMEN.add("  pistola: " + que + ": " + detalle);
        if (!bien) throw new IllegalStateException(que + " (" + detalle + ")");
    }

    private static Object piError() {
        return PI.remove("error");
    }

    /**
     * El revólver de ruleta en un campo de tiro: cargar con agachado + clic derecho (munición en la otra mano), girar
     * el tambor (agachado + clic izquierdo), Desenfunde contra un husk, Carambola en una pared de cobre, bengala de
     * noche (luz de verdad, Señalero, vista de lejos y que la luz se quita), dardo, fogueo con vacas, bala imán, ruleta
     * a la sien con fogueo, óxido/cera/rascado, la pose en tercera persona, Abanico, los encantamientos de la mesa y un
     * duelo contra un forastero (jugador de mentira).
     */
    private static void pistola() {
        Minecraft mc = Minecraft.getInstance();
        sin("pistola_preparar", 40, () -> {
            mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            mc.gui.getChat().clearMessages(false);
            piSrv(sp -> {
                var l = sp.serverLevel();
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -9).relative(mira, -3).below(), o.relative(lado, 9).relative(mira, 16).above(14)))
                    l.setBlock(b, b.getY() < o.getY() ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState() : aire, 2);
                // pared de cobre a la derecha (para la Carambola)
                for (int k = 1; k <= 10; k++)
                    for (int y = 0; y <= 3; y++)
                        l.setBlock(o.relative(lado, 3).relative(mira, k).above(y), net.minecraft.world.level.block.Blocks.CUT_COPPER.defaultBlockState(), 2);
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(o).inflate(30),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
                piO = o.immutable();
                piMira = mira;
                piLado = lado;
                // un husk quieto (no se quema de día) a 6 bloques y tres vacas a la izquierda
                var h = net.minecraft.world.entity.EntityType.HUSK.create(l);
                Vec3 hp = piPunto(6, 0, 0);
                h.moveTo(hp.x, hp.y, hp.z, mira.toYRot() + 180, 0);
                h.setNoAi(true);
                h.setPersistenceRequired();
                h.setSilent(true);
                l.addFreshEntity(h);
                piHusk = h.getUUID();
                for (int i = 0; i < 3; i++) {
                    var v = net.minecraft.world.entity.EntityType.COW.create(l);
                    Vec3 vp = piPunto(3 + i, -4 - (i % 2), 0);
                    v.moveTo(vp.x, vp.y, vp.z, 0, 0);
                    v.setPersistenceRequired();
                    l.addFreshEntity(v);
                }
                sp.setGameMode(GameType.SURVIVAL);
                sp.removeAllEffects();
                sp.setHealth(sp.getMaxHealth());
                sp.getInventory().clearContent();
                sp.getInventory().setItem(0, piRevolver(0, com.miscelanea.registry.ModEnchantments.REVOLVER_CARAMBOLA, 2,
                        com.miscelanea.registry.ModEnchantments.REVOLVER_DESENFUNDE, 1, com.miscelanea.registry.ModEnchantments.REVOLVER_SENALERO, 1));
                sp.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.STICK));
                sp.getInventory().selected = 0;
                sp.getInventory().offhand.set(0, new ItemStack(com.miscelanea.registry.ModItems.REVOLVER_BALA.get(), 2));
                sp.serverLevel().setDayTime(6000);
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 0F);
            });
        });
        sin("pistola_mirar", 10, () -> {
            mc.player.getInventory().selected = 0;
            piApuntar(piPunto(8, 0, 1.4));
        });
        paso("100_revolver_mano", 10, () -> {
            Object e = piError();
            if (e != null) throw new IllegalStateException("preparar: " + e);
        });
        // ---- cargar: agachado + clic derecho con la munición en la otra mano
        var orden = new net.minecraftforge.registries.RegistryObject[]{null, null, com.miscelanea.registry.ModItems.REVOLVER_BENGALA,
                com.miscelanea.registry.ModItems.REVOLVER_DARDO, com.miscelanea.registry.ModItems.REVOLVER_FOGUEO};
        sin("pistola_agachar", 6, () -> mc.options.keyShift.setDown(true));
        for (int i = 0; i < orden.length; i++) {
            final var mun = orden[i];
            if (mun != null)
                sin("pistola_mano_" + i, 4, () -> piSrv(sp -> sp.getInventory().offhand.set(0, new ItemStack((net.minecraft.world.item.Item) mun.get(), 3))));
            sin("pistola_cargar_" + i, 6, () -> {
                if (!mc.player.isShiftKeyDown()) throw new IllegalStateException("no está agachado");
                piDisparar();
            });
        }
        sin("pistola_cargado", 4, () -> piSrv(sp -> PI.put("tambor", java.util.Arrays.toString(com.miscelanea.armasfuego.pistola.Tambor.recamaras(sp.getMainHandItem())))));
        paso("101_tambor_cargado", 6, () -> {
            String t = String.valueOf(PI.get("tambor"));
            piComprobar("cargar 5 recámaras agachado (bala, bala, bengala, dardo, fogueo, vacía)", t.equals("[1, 1, 2, 3, 4, 0]"), t);
        });
        // ---- girar el tambor: agachado + clic izquierdo
        sin("pistola_girar", 5, () -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()));
        paso("102_ruleta_girando", 20, () -> {
            ItemStack s = mc.player.getMainHandItem();
            long hace = mc.level.getGameTime() - com.miscelanea.armasfuego.pistola.Tambor.giroT(s);
            piComprobar("girar el tambor (agachado + clic izquierdo)", com.miscelanea.armasfuego.pistola.Tambor.girado(s) && hace >= 0 && hace < 40,
                    "girado " + com.miscelanea.armasfuego.pistola.Tambor.girado(s) + ", hace " + hace + " ticks");
        });
        sin("pistola_ruleta_fin", 6, () -> {
            mc.options.keyShift.setDown(false);
            piSrv(sp -> {
                ItemStack s = sp.getMainHandItem();
                com.miscelanea.armasfuego.pistola.Tambor.ponerPos(s, 0);
                s.getOrCreateTag().remove(com.miscelanea.armasfuego.pistola.Tambor.GIRADO);
                sp.getInventory().offhand.set(0, ItemStack.EMPTY);
            });
        });
        // ---- Desenfunde: se cambia de ranura y se dispara enseguida al husk
        sin("pistola_enfunda", 5, () -> mc.player.getInventory().selected = 1);
        sin("pistola_desenfunda", 3, () -> {
            mc.player.getInventory().selected = 0;
            piApuntar(piPunto(6, 0, 1.3));
        });
        paso("103_bala_desenfunde", 3, piTiro());
        sin("pistola_bala_ver", 8, () -> piSrv(sp -> {
            var h = piHusk(sp);
            PI.put("vida1", h == null ? -1F : h.getHealth());
            if (h != null) h.setHealth(h.getMaxHealth());
        }));
        sin("pistola_bala_comprobar", 14, () -> {
            float v = (Float) PI.getOrDefault("vida1", -2F);
            piComprobar("bala con Desenfunde (crítico) al husk", v >= 0 && v <= 10.5F, "le queda " + v + " de 20");
            piApuntar(piPunto(6, 5, 1.2));   // el reflejo del husk en la pared de cobre
        });
        // ---- Carambola: la bala rebota en el cobre y da al husk
        sin("pistola_carambola_traza", 4, () -> piSrv(sp -> {
            var im = com.miscelanea.armasfuego.pistola.Revolver.trazar(sp.serverLevel(), sp, sp.getEyePosition(), sp.getViewVector(1F), 64, 1, sp.getEyePosition());
            PI.put("traza", im.rebotes() + " rebote(s), da a " + (im.entidad() == null ? "nada" : im.entidad().getType().toShortString()));
        }));
        paso("104_carambola", 3, piTiro());
        sin("pistola_carambola_ver", 8, () -> piSrv(sp -> {
            var h = piHusk(sp);
            PI.put("vida2", h == null ? -1F : h.getHealth());
            if (h != null) h.setHealth(h.getMaxHealth());
        }));
        sin("pistola_carambola_comprobar", 14, () -> {
            float v = (Float) PI.getOrDefault("vida2", -2F);
            String tr = String.valueOf(PI.get("traza"));
            piComprobar("Carambola: rebota en la pared de cobre y da al husk", v >= 0 && v < 20F && tr.startsWith("1 rebote") && tr.endsWith("husk"),
                    tr + "; le queda " + v + " de 20");
            mc.player.setXRot(-40);
            piApuntar(piPunto(12, 0, 12));
            piSrv(sp -> sp.serverLevel().setDayTime(18000));
        });
        // ---- bengala de noche
        paso("105_bengala_sale", 12, piTiro());
        sin("pistola_bengala_sube", 70, () -> piApuntar(piPunto(7, 0, 0.5)));
        sin("pistola_bengala_ver", 4, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            var bs = l.getEntitiesOfClass(com.miscelanea.armasfuego.pistola.BengalaEntity.class, new net.minecraft.world.phys.AABB(piO).inflate(40));
            int luces = 0;
            for (var b : net.minecraft.core.BlockPos.betweenClosed(piO.offset(-24, -2, -24), piO.offset(24, 24, 24)))
                if (l.getBlockState(b).is(net.minecraft.world.level.block.Blocks.LIGHT)) luces++;
            var h = piHusk(sp);
            PI.put("bengala", bs.size() + " bengala(s)" + (bs.isEmpty() ? "" : String.format(" a %.1f bloques de altura", bs.get(0).getY() - piO.getY()))
                    + ", " + luces + " bloque(s) de luz, " + com.miscelanea.armasfuego.pistola.LucesTemporales.cuantas(sp.server) + " apuntadas, husk brilla: "
                    + (h != null && h.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)));
            PI.put("bengala_ok", !bs.isEmpty() && luces >= 1 && h != null && h.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)
                    && l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, net.minecraft.core.BlockPos.containing(piPunto(7, 0, 0.5))) >= 8);
        }));
        paso("106_bengala_noche", 10, () -> piComprobar("bengala: sube, ilumina (bloques de luz) y con Señalero el husk brilla",
                Boolean.TRUE.equals(PI.get("bengala_ok")), String.valueOf(PI.get("bengala"))));
        sin("pistola_lejos_poner", 20, () -> {
            var bs = mc.level.getEntitiesOfClass(com.miscelanea.armasfuego.pistola.BengalaEntity.class, mc.player.getBoundingBox().inflate(48));
            Vec3 obj = bs.isEmpty() ? piPunto(10, 0, 12) : bs.get(0).position();
            Vec3 pos = piPunto(-40, -12, 8);
            Vec3 d = obj.subtract(pos);
            float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float xRot = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            camaraFuera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, pos.x, pos.y, pos.z);
            camaraFuera.moveTo(pos.x, pos.y - camaraFuera.getEyeHeight(), pos.z, yRot, xRot);
            camaraFuera.setYHeadRot(yRot);
            mc.setCameraEntity(camaraFuera);
        });
        paso("107_bengala_lejos", 10, () -> {});
        sin("pistola_lejos_quitar", 6, () -> {
            mc.setCameraEntity(mc.player);
            piApuntar(piPunto(6, 0, 1.3));
        });
        // ---- dardo de sedante
        paso("108_dardo", 10, piTiro());
        sin("pistola_dardo_ver", 4, () -> piSrv(sp -> {
            var h = piHusk(sp);
            PI.put("dardo", h != null && h.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));
        }));
        sin("pistola_dardo_comprobar", 8, () -> {
            piComprobar("dardo de sedante (lentitud)", Boolean.TRUE.equals(PI.get("dardo")), String.valueOf(PI.get("dardo")));
            piSrv(sp -> {
                double d = 0;
                for (var v : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, new net.minecraft.world.phys.AABB(piO).inflate(30)))
                    d += v.distanceTo(sp);
                PI.put("vacas0", d);
            });
            piApuntar(piPunto(10, 1, 1.5));
        });
        // ---- fogueo: las vacas salen corriendo
        paso("109_fogueo", 3, piTiro());
        sin("pistola_fogueo_espera", 50, () -> {});
        sin("pistola_fogueo_ver", 4, () -> piSrv(sp -> {
            double d = 0;
            for (var v : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, new net.minecraft.world.phys.AABB(piO).inflate(40)))
                d += v.distanceTo(sp);
            PI.put("vacas1", d);
        }));
        sin("pistola_fogueo_comprobar", 4, () -> {
            double a = (Double) PI.getOrDefault("vacas0", 0.0), b = (Double) PI.getOrDefault("vacas1", 0.0);
            piComprobar("fogueo: las vacas huyen", b > a + 3, String.format("distancia total de las vacas %.1f → %.1f", a, b));
        });
        // ---- recámara vacía y bala imán
        sin("pistola_vacia", 10, () -> piDisparar());
        sin("pistola_iman_poner", 10, () -> piSrv(sp -> {
            ItemStack s = sp.getMainHandItem();
            PI.put("vacio", java.util.Arrays.toString(com.miscelanea.armasfuego.pistola.Tambor.recamaras(s)) + " pos " + com.miscelanea.armasfuego.pistola.Tambor.pos(s));
            com.miscelanea.armasfuego.pistola.Tambor.poner(s, 0, com.miscelanea.armasfuego.pistola.Carga.IMAN);
            com.miscelanea.armasfuego.pistola.Tambor.poner(s, 1, com.miscelanea.armasfuego.pistola.Carga.FOGUEO);
            var l = sp.serverLevel();
            for (int i = 0; i < 3; i++) {
                Vec3 q = piPunto(9 + i * 0.6, -2, 0.1);
                var it = new net.minecraft.world.entity.item.ItemEntity(l, q.x, q.y, q.z, new ItemStack(i == 1 ? net.minecraft.world.item.Items.EMERALD : net.minecraft.world.item.Items.GOLD_INGOT, 2));
                it.setDeltaMovement(Vec3.ZERO);
                it.setPickUpDelay(0);
                l.addFreshEntity(it);
            }
        }));
        sin("pistola_iman_apuntar", 12, () -> {
            piComprobar("la recámara vacía hace clic y el tambor vuelve a la primera", String.valueOf(PI.get("vacio")).equals("[0, 0, 0, 0, 0, 0] pos 0"), String.valueOf(PI.get("vacio")));
            piApuntar(piPunto(9.6, -2, 0.05));
        });
        paso("110_iman", 6, piTiro());
        sin("pistola_iman_espera", 50, () -> piSrv(sp -> {
            int oro = 0;
            for (ItemStack x : sp.getInventory().items) if (x.is(net.minecraft.world.item.Items.GOLD_INGOT) || x.is(net.minecraft.world.item.Items.EMERALD)) oro += x.getCount();
            PI.put("iman", oro);
        }));
        sin("pistola_iman_comprobar", 4, () -> {
            piComprobar("bala imán: lo tirado viene volando hacia ti", (Integer) PI.getOrDefault("iman", 0) >= 4, PI.get("iman") + " objetos recogidos de 6");
            mc.player.setXRot(88);
            mc.player.xRotO = 88;
            piSrv(sp -> PI.put("vida_sien", sp.getHealth()));
        });
        // ---- ruleta rusa a la sien con fogueo
        paso("111_ruleta_sien", 4, piTiro());
        sin("pistola_sien_ver", 6, () -> piSrv(sp -> PI.put("sien", sp.isAlive() && sp.getHealth() >= (Float) PI.getOrDefault("vida_sien", 20F)
                && sp.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION))));
        sin("pistola_sien_comprobar", 4, () -> {
            piComprobar("ruleta a la sien con fogueo (vivo, mareado)", Boolean.TRUE.equals(PI.get("sien")), String.valueOf(PI.get("sien")));
            piSrv(sp -> {
                sp.removeAllEffects();
                for (int i = 1; i <= 3; i++) sp.getInventory().setItem(i, piRevolver(i));
                sp.getMainHandItem().getOrCreateTag().putInt(com.miscelanea.armasfuego.pistola.Tambor.OXIDO, 3);
                sp.getServer().overworld().setDayTime(6000);
            });
            piApuntar(piPunto(8, 2, 1.0));
        });
        paso("112_oxidado", 10, () -> {});
        // ---- encerar con panal y rascar con hacha (en la otra mano, agachado + clic derecho)
        sin("pistola_panal", 4, () -> piSrv(sp -> sp.getInventory().offhand.set(0, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB))));
        sin("pistola_agachar2", 4, () -> mc.options.keyShift.setDown(true));
        sin("pistola_encerar", 6, () -> piDisparar());
        sin("pistola_encerado", 4, () -> piSrv(sp -> {
            PI.put("cera", com.miscelanea.armasfuego.pistola.Tambor.encerado(sp.getMainHandItem()));
            sp.getInventory().offhand.set(0, new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
        }));
        sin("pistola_rascar1", 6, () -> piDisparar());
        sin("pistola_rascar2", 6, () -> piDisparar());
        sin("pistola_rascado", 4, () -> piSrv(sp -> {
            ItemStack s = sp.getMainHandItem();
            PI.put("rascado", "encerado " + com.miscelanea.armasfuego.pistola.Tambor.encerado(s) + ", óxido " + com.miscelanea.armasfuego.pistola.Tambor.oxido(s));
        }));
        sin("pistola_cera_comprobar", 4, () -> {
            mc.options.keyShift.setDown(false);
            piComprobar("panal: encera", Boolean.TRUE.equals(PI.get("cera")), String.valueOf(PI.get("cera")));
            piComprobar("hacha: quita la cera y luego un paso de óxido", "encerado false, óxido 2".equals(PI.get("rascado")), String.valueOf(PI.get("rascado")));
            piApuntar(piPunto(8, 0, 1.4));
        });
        paso("113_tercera_frente", 20, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        paso("114_tercera_espalda", 20, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        // ---- encantamientos en la mesa (lo que la mesa puede sacar para el revólver) y Abanico
        sin("pistola_mesa", 4, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            var rev = new ItemStack(com.miscelanea.registry.ModItems.REVOLVER.get());
            java.util.Set<String> vistos = new java.util.TreeSet<>();
            var rnd = net.minecraft.util.RandomSource.create(428);
            for (int i = 0; i < 200; i++)
                for (var e : net.minecraft.world.item.enchantment.EnchantmentHelper.selectEnchantment(rnd, rev, 5 + rnd.nextInt(26), false))
                    vistos.add(net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(e.enchantment).getPath() + " " + e.level);
            boolean soloSuyos = vistos.stream().allMatch(x -> x.startsWith("revolver_"));
            var ab = com.miscelanea.registry.ModEnchantments.REVOLVER_ABANICO.get();
            var de = com.miscelanea.registry.ModEnchantments.REVOLVER_DESENFUNDE.get();
            boolean incompatibles = !ab.isCompatibleWith(de) && !de.isCompatibleWith(ab);
            boolean espada = com.miscelanea.registry.ModEnchantments.REVOLVER_CARAMBOLA.get().canEnchant(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            piComprobar("mesa de encantar: solo los suyos, Abanico y Desenfunde incompatibles, no valen para una espada",
                    soloSuyos && vistos.size() >= 4 && incompatibles && !espada, vistos + ", incompatibles " + incompatibles + ", espada " + espada);
            piSrv(sp -> {
                ItemStack r = piRevolver(0, com.miscelanea.registry.ModEnchantments.REVOLVER_ABANICO, 1);
                for (int i = 0; i < 6; i++) com.miscelanea.armasfuego.pistola.Tambor.poner(r, i, com.miscelanea.armasfuego.pistola.Carga.BALA);
                sp.getInventory().setItem(0, r);
                sp.getInventory().offhand.set(0, ItemStack.EMPTY);
                var h = piHusk(sp);
                if (h != null) h.setHealth(h.getMaxHealth());
            });
            piApuntar(piPunto(6, 0, 1.3));
        });
        sin("pistola_agachar3", 6, () -> mc.options.keyShift.setDown(true));
        sin("pistola_abanico", 5, () -> piDisparar());
        paso("115_abanico", 24, () -> {});
        sin("pistola_abanico_ver", 4, () -> {
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("abanico", com.miscelanea.armasfuego.pistola.Tambor.cargadas(sp.getMainHandItem())));
        });
        sin("pistola_abanico_comprobar", 4, () -> piComprobar("Abanico: vacía el tambor de golpe", Integer.valueOf(0).equals(PI.get("abanico")),
                PI.get("abanico") + " balas quedan"));
        // ---- duelo contra un forastero (jugador de mentira: no está en la lista de jugadores, así que se le da por código)
        sin("pistola_duelo_poner", 6, () -> {
            mc.gui.getChat().clearMessages(false);
            piSrv(sp -> {
                ItemStack r = piRevolver(0);
                com.miscelanea.armasfuego.pistola.Tambor.poner(r, 0, com.miscelanea.armasfuego.pistola.Carga.FOGUEO);
                sp.getInventory().setItem(0, r);
                piForastero = net.minecraftforge.common.util.FakePlayerFactory.get(sp.serverLevel(),
                        new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("Forastero".getBytes()), "Forastero"));
                Vec3 q = piPunto(8, 0, 0);
                piForastero.setPos(q.x, q.y, q.z);
                com.miscelanea.armasfuego.pistola.Duelo.retar(piForastero, sp);
                com.miscelanea.armasfuego.pistola.Duelo.ganadorPrueba = null;
            });
        });
        paso("116_duelo_reto", 6, () -> {});
        sin("pistola_duelo_acepta", 10, () -> piSrv(sp -> PI.put("acepta", com.miscelanea.armasfuego.pistola.Duelo.aceptarPrueba(sp))));
        paso("117_duelo_cuenta", 4, () -> piComprobar("duelo: acepta el reto", Boolean.TRUE.equals(PI.get("acepta")), String.valueOf(PI.get("acepta"))));
        sin("pistola_duelo_antes", 4, () -> piDisparar());
        sin("pistola_duelo_antes_ver", 4, () -> piSrv(sp -> PI.put("antes", com.miscelanea.armasfuego.pistola.Tambor.pos(sp.getMainHandItem()) == 0
                && com.miscelanea.armasfuego.pistola.Tambor.siguiente(sp.getMainHandItem()) == com.miscelanea.armasfuego.pistola.Carga.FOGUEO)));
        sin("pistola_duelo_espera", 32, () -> piComprobar("duelo: antes del ¡YA! no dispara", Boolean.TRUE.equals(PI.get("antes")), String.valueOf(PI.get("antes"))));
        paso("118_duelo_ya", 8, () -> {});
        sin("pistola_duelo_tiro", 4, () -> piSrv(sp -> com.miscelanea.armasfuego.pistola.Duelo.impacto(sp, piForastero)));
        paso("119_duelo_gana", 10, () -> piComprobar("duelo: gana el primero que da", mc.player.getGameProfile().getName().equals(com.miscelanea.armasfuego.pistola.Duelo.ganadorPrueba),
                String.valueOf(com.miscelanea.armasfuego.pistola.Duelo.ganadorPrueba)));
        // ---- la luz de la bengala se quita sola al apagarse
        sin("pistola_apagar", 50, () -> piSrv(sp -> sp.serverLevel().getEntitiesOfClass(com.miscelanea.armasfuego.pistola.BengalaEntity.class,
                new net.minecraft.world.phys.AABB(piO).inflate(60)).forEach(net.minecraft.world.entity.Entity::discard)));
        sin("pistola_luces_ver", 4, () -> piSrv(sp -> {
            int luces = 0;
            for (var b : net.minecraft.core.BlockPos.betweenClosed(piO.offset(-24, -2, -24), piO.offset(24, 30, 24)))
                if (sp.serverLevel().getBlockState(b).is(net.minecraft.world.level.block.Blocks.LIGHT)) luces++;
            PI.put("luces", luces + " en el mundo, " + com.miscelanea.armasfuego.pistola.LucesTemporales.cuantas(sp.server) + " apuntadas");
        }));
        sin("pistola_fin", 6, () -> {
            mc.options.keyShift.setDown(false);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            piSrv(sp -> {
                sp.setGameMode(GameType.CREATIVE);
                sp.removeAllEffects();
                sp.setHealth(sp.getMaxHealth());
                var h = piHusk(sp);
                if (h != null) h.discard();
            });
            Object e = piError();
            piComprobar("la luz de la bengala se quita al apagarse", "0 en el mundo, 0 apuntadas".equals(PI.get("luces")), String.valueOf(PI.get("luces")));
            if (e != null) throw new IllegalStateException("error en el servidor: " + e);
        });
    }

    private static Runnable piTiro() {
        return PruebaCI::piDisparar;
    }

    // ------------------------------------------------------------------ ecolocalización
    private static volatile float nieblaFin = -1;
    private static UUID ecoHabla;
    private static net.minecraft.client.player.RemotePlayer ecoJugador;
    private static volatile UUID ecoZombi;
    private static boolean ecoAndando;
    private static int ecoPasos0, ecoPasosOtros0;
    private static final int ECO_ID = -428_001;

    /**
     * La poción de ecolocalización: niebla negra, un zombi con nombre, casco y espada encantados (brillo) que "hace
     * ruido" (en el CI no hay sonido: las ondas se lanzan a mano donde sonaría) y un jugador de mentira que habla por el
     * voicechat (se le llama a lo que llama el plugin al recibir su audio). Se mira por código que la niebla se cierre,
     * que se vean las siluetas sin errores al dibujarlas, que el ruido de fondo del micro no haga ondas y que el cielo
     * (sol) no se vea; al quitarla, que todo vuelva a la normalidad.
     */
    private static void eco() {
        Minecraft mc = Minecraft.getInstance();
        sin("eco_poner", 30, () -> {
            mc.setScreen(null);
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -7).relative(mira, -3).below(), o.relative(lado, 7).relative(mira, 11).above(8)))
                    l.setBlock(b, b.getY() < o.getY() ? net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState() : aire, 2);
                for (int dy = 0; dy < 3; dy++) {
                    l.setBlock(o.relative(mira, 7).relative(lado, -3).above(dy), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    l.setBlock(o.relative(mira, 6).relative(lado, 3).above(dy), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 2);
                }
                l.setBlock(o.relative(mira, 3).relative(lado, 1), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 2);
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(o).inflate(16),
                        en -> !(en instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
                var z = new net.minecraft.world.entity.monster.Zombie(l);
                var zp = net.minecraft.world.phys.Vec3.atBottomCenterOf(o.relative(mira, 4).relative(lado, -1));
                z.moveTo(zp.x, zp.y, zp.z, mira.getOpposite().toYRot(), 0F);
                z.setYHeadRot(mira.getOpposite().toYRot());
                z.setNoAi(true);
                z.setPersistenceRequired();
                z.setCustomName(net.minecraft.network.chat.Component.literal("Ruidoso"));
                z.setCustomNameVisible(true);
                ItemStack casco = new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);
                casco.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 1);
                ItemStack espada = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
                z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, casco);
                z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, espada);
                l.addFreshEntity(z);
                ecoZombi = z.getUUID();
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 12F);
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.miscelanea.pociones.PocionesRegistro.ECOLOCALIZACION.get(), 20 * 120, 0));
            });
        });
        sin("eco_hablador", 10, () -> {
            // un jugador de mentira (solo en este cliente) delante, a la derecha, que habla por el voicechat
            var mira = net.minecraft.core.Direction.fromYRot(mc.player.getYRot());
            var lado = mira.getClockWise();
            var pos = net.minecraft.world.phys.Vec3.atBottomCenterOf(mc.player.blockPosition().relative(mira, 5).relative(lado, 2));
            var perfil = new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("Hablador".getBytes()), "Hablador");
            ecoJugador = new net.minecraft.client.player.RemotePlayer(mc.level, perfil);
            ecoJugador.setId(ECO_ID);
            ecoJugador.moveTo(pos.x, pos.y, pos.z, mira.getOpposite().toYRot(), 0F);
            ecoJugador.setYHeadRot(mira.getOpposite().toYRot());
            ecoJugador.yBodyRot = mira.getOpposite().toYRot();
            mc.level.addPlayer(ECO_ID, ecoJugador);
            ecoHabla = perfil.getId();
            com.miscelanea.client.pociones.EcoCliente.pruebaFallos = 0;
        });
        sin("eco_ruido", 6, () -> {
            if (!com.miscelanea.client.pociones.EcoCliente.activo()) throw new IllegalStateException("no tiene la ecolocalización");
            ecoRuidos(mc);
        });
        paso("90_eco_activo", 6, () -> ecoRuidos(mc));
        sin("eco_medir", 1, () -> {
            String est = com.miscelanea.client.pociones.EcoCliente.pruebaEstado();
            String img = ecoImagen("eco_activo");
            RESUMEN.add(String.format("  eco activo: niebla hasta %.2f bloques · %s · %s · hablo (solo ruido del micro): %s", nieblaFin, est, img,
                    com.miscelanea.client.pociones.EcoCliente.yoHablando() ? "sí" : "no"));
            if (nieblaFin > 1F) throw new IllegalStateException("la niebla negra no se cierra con el efecto");
            if (com.miscelanea.client.pociones.EcoCliente.pruebaFallos > 0) throw new IllegalStateException("fallos al dibujar las siluetas: " + com.miscelanea.client.pociones.EcoCliente.pruebaFallos);
            if (com.miscelanea.client.pociones.EcoCliente.pruebaEntes() < 2) throw new IllegalStateException("no se ven el zombi y el que habla");
            if (!MovilCaptura.shaders() && ecoOscuros < 0.5) throw new IllegalStateException("con el efecto no está todo a oscuras");
            if (com.miscelanea.client.pociones.EcoCliente.yoHablando()) throw new IllegalStateException("el ruido de fondo del micro cuenta como hablar");
        });
        paso("91_eco_tercera", 8, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            ecoRuidos(mc);
        });
        sin("eco_mirar_cielo", 6, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.setXRot(-89F);
            mc.player.xRotO = -89F;
        });
        paso("92_eco_cielo", 4, () -> {});
        sin("eco_medir_cielo", 1, () -> {
            String img = ecoImagen("eco_cielo");
            RESUMEN.add("  eco mirando arriba (a mediodía): " + img);
            if (ecoClaros > 40) {
                // con shaders el cielo lo pinta el pack: solo aviso
                if (MovilCaptura.shaders()) RESUMEN.add("  AVISO eco: con shaders se ve el sol a ciegas");
                else throw new IllegalStateException("se ve el sol a ciegas");
            }
        });
        // andar: mis pasos y los del que habla (que se mueve) tienen que hacer ondas
        sin("eco_andar", 1, () -> {
            mc.player.setXRot(12F);
            mc.player.xRotO = 12F;
            ecoPasos0 = com.miscelanea.client.pociones.EcoCliente.pruebaPasosMios;
            ecoPasosOtros0 = com.miscelanea.client.pociones.EcoCliente.pruebaPasosOtros;
            ecoAndando = true;
        });
        paso("94_eco_andando", 24, () -> {});
        sin("eco_medir_pasos", 1, () -> {
            ecoAndando = false;
            mc.options.keyUp.setDown(false);
            int mios = com.miscelanea.client.pociones.EcoCliente.pruebaPasosMios - ecoPasos0;
            int otros = com.miscelanea.client.pociones.EcoCliente.pruebaPasosOtros - ecoPasosOtros0;
            RESUMEN.add("  eco andando: ondas de mis pasos " + mios + ", de los pasos del otro " + otros + " · " + com.miscelanea.client.pociones.EcoCliente.pruebaEstado());
            if (mios < 2) throw new IllegalStateException("mis pasos no hacen ondas");
            if (otros < 2) throw new IllegalStateException("los pasos de otro jugador no hacen ondas");
        });
        sin("eco_quitar", 30, () -> {
            ecoHabla = null;
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) sp.removeEffect(com.miscelanea.pociones.PocionesRegistro.ECOLOCALIZACION.get());
            });
        });
        paso("93_eco_quitado", 6, () -> {});
        sin("eco_comprobar", 1, () -> {
            String img = ecoImagen("eco_quitado");
            String est = com.miscelanea.client.pociones.EcoCliente.pruebaEstado();
            RESUMEN.add(String.format("  eco quitado: niebla hasta %.1f bloques · %s · %s", nieblaFin, est, img));
            if (com.miscelanea.client.pociones.EcoCliente.activo()) throw new IllegalStateException("el efecto no se ha quitado");
            if (nieblaFin < 8F) throw new IllegalStateException("la niebla negra se queda puesta al quitar el efecto");
            if (!com.miscelanea.client.pociones.EcoCliente.pruebaVacio()) throw new IllegalStateException("quedan ondas o siluetas al quitar el efecto");
            if (ecoOscuros > 0.9) throw new IllegalStateException("la pantalla sigue negra al quitar el efecto");
        });
        sin("eco_fin", 5, () -> {
            if (ecoJugador != null) mc.level.removeEntity(ECO_ID, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            ecoJugador = null;
            var srv = mc.getSingleplayerServer();
            UUID zid = ecoZombi;
            srv.execute(() -> {
                for (var l : srv.getAllLevels()) {
                    var z = zid == null ? null : l.getEntity(zid);
                    if (z != null) z.discard();
                }
            });
        });
    }

    /** Lo que haría ruido: el zombi (gruñido), mis pasos y el que habla (ya hace sus ondas al hablar). */
    private static void ecoRuidos(Minecraft mc) {
        UUID zid = ecoZombi;
        for (var en : mc.level.entitiesForRendering())
            if (en.getUUID().equals(zid)) com.miscelanea.client.pociones.EcoCliente.pruebaRuido(en.getX(), en.getEyeY(), en.getZ());
        com.miscelanea.client.pociones.EcoCliente.pruebaRuido(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private static double ecoOscuros;
    private static int ecoClaros;

    /** De la imagen de ahora: qué parte es casi negra, cuántos píxeles azulados (ondas y siluetas) y cuántos muy claros. */
    private static String ecoImagen(String nombre) {
        Minecraft mc = Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        int oscuros = 0, azules = 0, claros = 0, total = 0;
        long suma = 0;
        try (com.mojang.blaze3d.platform.NativeImage img = Screenshot.takeScreenshot(rt)) {
            for (int y = 0; y < img.getHeight(); y += 2)
                for (int x = 0; x < img.getWidth(); x += 2) {
                    int c = img.getPixelRGBA(x, y);
                    int r = c & 255, g = c >> 8 & 255, b = c >> 16 & 255;
                    int lum = (r * 3 + g * 6 + b) / 10;
                    total++;
                    suma += lum;
                    if (lum < 16) oscuros++;
                    if (b > 110 && b > r + 30 && g > r) azules++;
                    if (lum > 235) claros++;
                }
        }
        ecoOscuros = oscuros / (double) Math.max(1, total);
        ecoClaros = claros;
        return String.format("imagen: %.0f%% negra, luz media %d, %d px azulados, %d px muy claros", ecoOscuros * 100, suma / Math.max(1, total), azules, claros);
    }

    /** Dos pasos: quitar el rayo y el HUD y, ya dibujado así, medir. */
    private static void medirLaser(String nombre, boolean desdeFuera, boolean obligatorio) {
        Minecraft mc = Minecraft.getInstance();
        sin(nombre + "_quitar_hud", 12, () -> {
            mc.options.hideGui = true;
            com.miscelanea.client.laser.LaserCliente.pruebaSinRayo = true;
        });
        sin(nombre + "_medir", 1, () -> {
            try {
                analizarLaser(nombre, desdeFuera, obligatorio);
            } finally {
                mc.options.hideGui = false;
                com.miscelanea.client.laser.LaserCliente.pruebaSinRayo = false;
            }
        });
    }

    private static void analizarLaser(String nombre, boolean desdeFuera, boolean obligatorio) {
        Minecraft mc = Minecraft.getInstance();
        var rt = mc.getMainRenderTarget();
        int W = rt.width, H = rt.height;
        Vec3 punto = com.miscelanea.client.laser.LaserCliente.pruebaMiPunto;
        Vec3 mira = mc.player.pick(64, 1.0F, false).getLocation();
        Vec3 deOtros = com.miscelanea.laser.Laser.rayo(mc.player, 1.0F).punto();
        // dónde tiene que salir: en primera persona, en el centro (la mira); desde fuera, donde mira el jugador
        double ex = W / 2.0, ey = H / 2.0;
        if (desdeFuera) {
            double[] s = com.miscelanea.client.laser.LaserCliente.aPantalla(mira, W, H);
            if (s == null) throw new IllegalStateException("la pared no está delante de la cámara de fuera");
            ex = s[0];
            ey = s[1];
        }
        // se busca cerca de donde tiene que estar (si se va lejos no se encuentra y también falla): así no confunden
        // otras cosas rojas del fondo
        double v = 0.15 * H;
        double sx = 0, sy = 0, sw = 0;
        int n = 0;
        try (com.mojang.blaze3d.platform.NativeImage img = Screenshot.takeScreenshot(rt)) {
            for (int y = (int) Math.max(0, ey - v); y < Math.min(H, ey + v); y++)
                for (int x = (int) Math.max(0, ex - v); x < Math.min(W, ex + v); x++) {
                    int c = img.getPixelRGBA(x, y);
                    int r = c & 255, g = c >> 8 & 255, b = c >> 16 & 255;
                    int rojo = r - Math.max(g, b);
                    if (r >= 80 && rojo >= 40 && g * 10 < r * 6 && b * 10 < r * 6) {
                        sx += x * (double) rojo;
                        sy += y * (double) rojo;
                        sw += rojo;
                        n++;
                    }
                }
            File dir = new File(mc.gameDirectory, "screenshots");
            dir.mkdirs();
            img.writeToFile(new File(dir, nombre + ".png"));
        } catch (java.io.IOException ex2) {
            RESUMEN.add("  láser " + nombre + ": no se pudo guardar la imagen: " + ex2);
        }
        String calc = "sin punto";
        if (punto != null) {
            double[] s = com.miscelanea.client.laser.LaserCliente.aPantalla(punto, W, H);
            calc = (s == null ? "detrás" : String.format("(%+.1f, %+.1f) px", s[0] - ex, s[1] - ey))
                    + String.format(", a %.3f bloques de donde mira y a %.3f del que ven los demás", punto.distanceTo(mira), punto.distanceTo(deOtros));
        }
        double tol = Math.max(4, 0.025 * H);
        String linea;
        boolean bien;
        if (n < 6) {
            linea = String.format("no se ve el punto (%d píxeles rojos)", n);
            bien = false;
        } else {
            double dx = sx / sw - ex, dy = sy / sw - ey;
            bien = Math.hypot(dx, dy) <= tol;
            linea = String.format("el punto sale a (%+.1f, %+.1f) px de %s (%d píxeles rojos, imagen %dx%d)", dx, dy, desdeFuera ? "donde mira" : "la mira", n, W, H);
        }
        RESUMEN.add("  láser " + nombre + ": " + linea + " · calculado " + calc + (bien ? "" : " · MAL"));
        if (obligatorio && !bien) throw new IllegalStateException("el punto del láser no cae donde debe (" + nombre + ")");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!ACTIVA || e.phase != TickEvent.Phase.END || terminada) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // el puntero láser: el clic derecho mantenido
        if (mantenerUso) mc.options.keyUse.setDown(true);
        // ecolocalización: el jugador de mentira "habla" (como si llegara su audio del voicechat) y mi micro solo da ruido de fondo
        if (ecoAndando) {
            mc.options.keyUp.setDown(true);
            // el otro anda hacia atrás (alejándose), como si llegaran sus paquetes de movimiento
            if (ecoJugador != null) {
                var m = net.minecraft.core.Direction.fromYRot(mc.player.getYRot());
                ecoJugador.setPos(ecoJugador.getX() + m.getStepX() * 0.2, ecoJugador.getY(), ecoJugador.getZ() + m.getStepZ() * 0.2);
                ecoJugador.setOnGround(true);
            }
        }
        if (ecoHabla != null) {
            com.miscelanea.client.pociones.EcoCliente.habla(ecoHabla, 0.3F);
            com.miscelanea.client.pociones.EcoCliente.hablaYo(0.01F);
        }
        // que MC-Runtime-Test no dé la prueba por acabada mientras sigue esto
        if (mc.player.tickCount > 60) mc.player.tickCount = 60;
        if (++ticksEnMundo < 40) return;
        retransmitir();
        if (paso < 0) {
            preparar();
            paso = 0;
            espera = 0;
            RESUMEN.add("Prueba del móvil (Miscelánea)");
        }
        if (espera > 0) {
            espera--;
            return;
        }
        // cada paso necesita al menos 4 fotogramas dibujados (la cámara copia la imagen al dibujar)
        if (fotogramas - fotogramasPaso < 4) return;
        // el paso anterior ya ha tenido su tiempo: su captura
        if (paso > 0) {
            Paso antes = PASOS.get(paso - 1);
            if (antes.captura()) capturar(antes.nombre());
        }
        if (paso >= PASOS.size()) {
            terminar();
            return;
        }
        Paso p = PASOS.get(paso++);
        try {
            p.accion().run();
            RESUMEN.add("OK    " + p.nombre());
        } catch (Throwable t) {
            fallos++;
            RESUMEN.add("FALLO " + p.nombre() + ": " + t);
            Miscelanea.LOGGER.error("[PruebaCI] falló el paso {}", p.nombre(), t);
        }
        espera = p.espera();
        fotogramasPaso = fotogramas;
    }

    private static void capturar(String nombre) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Screenshot.grab(mc.gameDirectory, nombre + ".png", mc.getMainRenderTarget(), c -> {});
        } catch (Throwable t) {
            RESUMEN.add("FALLO captura " + nombre + ": " + t);
        }
    }

    private static void terminar() {
        terminada = true;
        sinLlamada();
        RESUMEN.add(fallos == 0 ? "TODO BIEN" : fallos + " PASOS FALLARON");
        try {
            Files.write(new File(Minecraft.getInstance().gameDirectory, "prueba_miscelanea.txt").toPath(), RESUMEN);
        } catch (Exception ex) {
            Miscelanea.LOGGER.error("[PruebaCI] no se pudo escribir el resumen", ex);
        }
        Miscelanea.LOGGER.info("[PruebaCI] terminada: {}", String.join(" | ", RESUMEN));
        Minecraft.getInstance().player.tickCount = 200;
    }
}
