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
    private static int paso = -1, espera, ticksEnMundo, fallos, hastaMax;
    private static java.util.function.BooleanSupplier hasta;
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
        montana();
        purgatorio();
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

    // ------------------------------------------------------------------ montaña rusa (2.0.0)
    private static net.minecraft.core.BlockPos montanaO;
    private static final java.util.Set<String> MONTANA_VISTO = new java.util.LinkedHashSet<>();

    /**
     * Circuito cerrado: estación, cadena que sube 6, bajada de 6, rizo, vuelta en U, recta de vuelta con freno y un
     * zigzag que vuelve a la estación. El jugador va montado y se sacan capturas cada poco (en tercera y primera persona).
     */
    private static void montana() {
        Minecraft mc = Minecraft.getInstance();
        sin("montana_construir", 30, () -> {
            mc.setScreen(null);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                var o = sp.blockPosition().offset(6, 0, 4);
                montanaO = o;
                // despejar el sitio
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.offset(-3, 0, -26), o.offset(13, 18, 4)))
                    l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                var N = net.minecraft.core.Direction.NORTH;
                var E = net.minecraft.core.Direction.EAST;
                var S = net.minecraft.core.Direction.SOUTH;
                var W = net.minecraft.core.Direction.WEST;
                var R = com.miscelanea.montana.ViaMontanaBlock.Forma.RECTA;
                var SU = com.miscelanea.montana.ViaMontanaBlock.Forma.SUBIDA;
                var BA = com.miscelanea.montana.ViaMontanaBlock.Forma.BAJADA;
                var CD = com.miscelanea.montana.ViaMontanaBlock.Forma.CURVA_DER;
                var CI = com.miscelanea.montana.ViaMontanaBlock.Forma.CURVA_IZQ;
                var V = com.miscelanea.registry.ModBlocks.VIA_MONTANA.get();
                var C = com.miscelanea.registry.ModBlocks.VIA_MONTANA_CADENA.get();
                java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> pon = (b, st) -> l.setBlock(o.offset(b), st, 3);
                java.util.function.Function<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.BlockState> d = net.minecraft.world.level.block.Block::defaultBlockState;
                var FAC = com.miscelanea.montana.ViaMontanaBlock.FACING;
                var FOR = com.miscelanea.montana.ViaMontanaBlock.FORMA;
                pon.accept(new net.minecraft.core.BlockPos(0, 0, 0), d.apply(com.miscelanea.registry.ModBlocks.VIA_MONTANA_ESTACION.get()).setValue(FAC, N));
                pon.accept(new net.minecraft.core.BlockPos(0, 0, -1), d.apply(V).setValue(FAC, N));
                for (int k = 0; k < 6; k++) pon.accept(new net.minecraft.core.BlockPos(0, k, -2 - k), d.apply(C).setValue(FAC, N).setValue(FOR, SU));
                pon.accept(new net.minecraft.core.BlockPos(0, 6, -8), d.apply(V).setValue(FAC, N));
                for (int k = 0; k < 6; k++) pon.accept(new net.minecraft.core.BlockPos(0, 5 - k, -9 - k), d.apply(V).setValue(FAC, N).setValue(FOR, BA));
                pon.accept(new net.minecraft.core.BlockPos(0, 0, -15), d.apply(V).setValue(FAC, N));
                pon.accept(new net.minecraft.core.BlockPos(0, 0, -16), d.apply(com.miscelanea.registry.ModBlocks.RIZO.get()).setValue(com.miscelanea.montana.RizoBlock.FACING, N));
                pon.accept(new net.minecraft.core.BlockPos(1, 0, -21), d.apply(V).setValue(FAC, N));
                pon.accept(new net.minecraft.core.BlockPos(1, 0, -22), d.apply(V).setValue(FAC, N).setValue(FOR, CD));
                pon.accept(new net.minecraft.core.BlockPos(2, 0, -22), d.apply(V).setValue(FAC, E).setValue(FOR, CD));
                for (int z = -21; z <= 1; z++)
                    pon.accept(new net.minecraft.core.BlockPos(2, 0, z), d.apply(z == -3 || z == -2 ? com.miscelanea.registry.ModBlocks.VIA_MONTANA_FRENO.get() : V).setValue(FAC, S));
                pon.accept(new net.minecraft.core.BlockPos(2, 0, 2), d.apply(V).setValue(FAC, S).setValue(FOR, CD));
                pon.accept(new net.minecraft.core.BlockPos(1, 0, 2), d.apply(V).setValue(FAC, W).setValue(FOR, CD));
                pon.accept(new net.minecraft.core.BlockPos(1, 0, 1), d.apply(V).setValue(FAC, N).setValue(FOR, CI));
                pon.accept(new net.minecraft.core.BlockPos(0, 0, 1), d.apply(V).setValue(FAC, W).setValue(FOR, CD));
                // el tren en la estación, con el jugador dentro
                var t = new com.miscelanea.montana.TrenMontana(com.miscelanea.registry.ModEntities.TREN_MONTANA.get(), l);
                t.colocar(o, 0.5, 3);
                l.addFreshEntity(t);
                sp.teleportTo(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5);
                sp.startRiding(t, true);
                sp.setXRot(20);
            });
        });
        sin("montana_circuito", 1, () -> {
            // ¿el circuito está cerrado? se recorre de trozo en trozo desde la estación
            var l = mc.getSingleplayerServer().overworld();
            var p0 = com.miscelanea.montana.Via.en(l, montanaO);
            if (p0 == null) throw new IllegalStateException("no hay estación");
            var p = p0;
            StringBuilder sb = new StringBuilder("  montaña: ");
            int n = 0;
            do {
                sb.append(p.clase().name().charAt(0));
                p = com.miscelanea.montana.Via.siguiente(l, p);
                n++;
            } while (p != null && !p.pos().equals(p0.pos()) && n < 200);
            sb.append(" (").append(n).append(" trozos, ").append(p == null ? "ABIERTO" : "cerrado").append(')');
            RESUMEN.add(sb.toString());
            if (p == null) throw new IllegalStateException("el circuito no se cierra");
        });
        for (int i = 0; i < 16; i++) {
            int ii = i;
            paso(String.format("%02d_montana_%02d", 30 + i / 10, i), 22, () -> {
                var tren = mc.player.getVehicle() instanceof com.miscelanea.montana.TrenMontana tt ? tt : null;
                if (tren == null) throw new IllegalStateException("no va montado en el tren");
                mc.options.setCameraType(ii % 3 == 2 ? net.minecraft.client.CameraType.FIRST_PERSON : net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                var s = tren.sitio();
                String q = s == null ? "?" : s.pieza().clase().name() + (s.pieza().tipo() == com.miscelanea.montana.ViaMontanaBlock.Tipo.NORMAL ? "" : "/" + s.pieza().tipo());
                MONTANA_VISTO.add(q);
                RESUMEN.add(String.format("  tren %02d: %s y=%.1f v=%.2f", ii, q, tren.getY() - montanaO.getY(), tren.velocidad()));
            });
        }
        sin("montana_fin", 5, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            RESUMEN.add("  montaña, trozos por los que ha pasado: " + MONTANA_VISTO);
            if (!MONTANA_VISTO.contains("RIZO") && MONTANA_VISTO.stream().noneMatch(x -> x.startsWith("CURVA"))) RESUMEN.add("  AVISO: el tren no llegó al rizo");
        });
        // pilares: se baja del tren y mira la subida desde un lado
        sin("montana_pilares_ir", 20, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                if (sp.isPassenger()) com.miscelanea.montana.MontanaEventos.bajarSiOSi(sp);
                int n = 0;
                for (var b : net.minecraft.core.BlockPos.betweenClosed(montanaO.offset(-1, -1, -24), montanaO.offset(3, 8, 3)))
                    if (l.getBlockState(b).is(com.miscelanea.registry.ModBlocks.PILAR_MONTANA.get())) n++;
                RESUMEN.add("  montaña: " + n + " bloques de pilar debajo de la vía");
                if (n == 0) RESUMEN.add("  AVISO: no ha salido ningún pilar");
                sp.teleportTo(montanaO.getX() + 9.5, montanaO.getY() + 3, montanaO.getZ() - 6.5);
                sp.setYRot(90);
                sp.setYHeadRot(90);
                sp.setXRot(8);
            });
        });
        paso("32_montana_pilares", 30, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.setYRot(90);
            mc.player.setXRot(8);
        });
        // el rizo visto desde fuera, con dos aldeanos montados (deben ir boca abajo arriba del todo)
        sin("montana_rizo_ir", 10, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                com.miscelanea.montana.TrenMontana t = null;
                var trenes = l.getEntitiesOfClass(com.miscelanea.montana.TrenMontana.class, new net.minecraft.world.phys.AABB(montanaO).inflate(40));
                for (var en : trenes) t = en;
                RESUMEN.add("  rizo: " + trenes.size() + " tren(es) cerca; jugador en " + sp.level().dimension().location());
                if (t == null) {
                    RESUMEN.add("  FALLO: no se encuentra el tren para el rizo");
                    return;
                }
                for (var pa : java.util.List.copyOf(t.getPassengers())) com.miscelanea.montana.MontanaEventos.bajarSiOSi(pa);
                for (int k = 0; k < 2; k++) {
                    var a = net.minecraft.world.entity.EntityType.VILLAGER.create(l);
                    if (a == null) continue;
                    a.moveTo(t.getX(), t.getY(), t.getZ());
                    a.setNoAi(true);
                    a.setSilent(true);
                    l.addFreshEntity(a);
                    a.startRiding(t, true);
                }
                // volando un poco por encima, para que no tape nada (árboles, pilares)
                sp.getAbilities().mayfly = true;
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
                sp.teleportTo(montanaO.getX() + 9.5, montanaO.getY() + 5, montanaO.getZ() - 18);
                sp.setYRot(90);
                sp.setYHeadRot(90);
                sp.setXRot(14);
            });
        });
        // espera (como mucho 30 s) a que el tren, que sigue dando vueltas, entre en el rizo
        sin("montana_rizo_espera", 0, () -> {
            hastaMax = 600;
            hasta = () -> {
                for (var en : mc.level.entitiesForRendering())
                    if (en instanceof com.miscelanea.montana.TrenMontana t && !t.getPassengers().isEmpty()) {
                        var s = t.sitio();
                        if (s != null && s.pieza().clase() == com.miscelanea.montana.Via.Clase.RIZO) return true;
                    }
                mc.player.setYRot(90);
                mc.player.setXRot(14);
                return false;
            };
        });
        for (int i = 0; i < 12; i++) {
            int ii = i;
            paso(String.format("33_montana_rizo_%02d", i), 2, () -> {
                mc.player.setYRot(90);
                mc.player.setXRot(14);
                if (ii == 0) return;
                for (var en : mc.level.entitiesForRendering())
                    if (en instanceof com.miscelanea.montana.TrenMontana t) {
                        var s = t.sitio();
                        RESUMEN.add(String.format("  rizo %d: %s %s y=%.1f v=%.2f, %d montados", ii, s == null ? "?" : s.pieza().clase().name(),
                                s == null ? "" : s.pieza().pos().subtract(montanaO).toShortString(),
                                t.getY() - montanaO.getY(), t.velocidad(), t.getPassengers().size()));
                        if (s != null) MONTANA_VISTO.add(s.pieza().clase().name());
                    }
            });
        }
        sin("montana_rizo_fin", 2, () -> {
            RESUMEN.add("  montaña, trozos (con el rizo): " + MONTANA_VISTO);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                sp.getAbilities().flying = false;
                sp.getAbilities().mayfly = sp.isCreative();
                sp.onUpdateAbilities();
            });
        });
    }

    // ------------------------------------------------------------------ El Purgatorio (2.0.0)
    private static void irA(net.minecraft.core.BlockPos b, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        var srv = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            var l = srv.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            if (sp == null || l == null) return;
            com.miscelanea.purgatorio.Purgatorio.preparar(l, b);
            // que ningún habitante que ande cerca le pille y le recoloque en mitad de la foto
            for (var g : l.getEntitiesOfClass(com.miscelanea.purgatorio.Purgado.class, new net.minecraft.world.phys.AABB(b).inflate(96))) g.discard();
            sp.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).putLong("miscelanea_purgatorio_t", l.getGameTime());
            sp.teleportTo(l, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, yaw, pitch);
        });
    }

    /** Pone al jugador en "desde" (en el Purgatorio) mirando al centro del bloque "hacia". */
    private static void irMirando(net.minecraft.core.BlockPos desde, net.minecraft.core.BlockPos hacia) {
        double dx = hacia.getX() - desde.getX(), dz = hacia.getZ() - desde.getZ(), dy = hacia.getY() + 0.5 - (desde.getY() + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        irA(desde, yaw, pitch);
    }

    private static void purgatorio() {
        Minecraft mc = Minecraft.getInstance();
        sin("purgatorio_entrar", 60, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                if (sp.isPassenger()) com.miscelanea.montana.MontanaEventos.bajarSiOSi(sp);
                sp.getInventory().setItem(0, ItemStack.EMPTY);
                if (srv.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION) == null) RESUMEN.add("  FALLO: no existe la dimensión del Purgatorio");
                com.miscelanea.purgatorio.Purgatorio.entrar(sp, "purgatorio.miscelanea.vacio");
            });
        });
        sin("purgatorio_feria_ir", 140, () -> {
            if (!com.miscelanea.purgatorio.Purgatorio.en(mc.level)) throw new IllegalStateException("no ha entrado en el Purgatorio");
            // por la calle de en medio, mirando hacia el cruce (no pegado a la farola)
            irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA).offset(0, 0, -6), 0, 6);
        });
        paso("40_purgatorio_feria", 30, () -> {});
        paso("41_purgatorio_feria_2", 30, () -> mc.player.setYRot(mc.player.getYRot() + 180));
        // un habitante: se le "habla" (como si fuera el chat de voz) y viene a tientas hasta pillarle
        sin("purgatorio_habitante", 20, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA);
            irA(b, 0, 6);
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var l = srv.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                if (l == null) return;
                for (var g : l.getEntitiesOfClass(com.miscelanea.purgatorio.Purgado.class, new net.minecraft.world.phys.AABB(b).inflate(80))) g.discard();
                var g = com.miscelanea.purgatorio.Habitantes.aparecer(l, b.offset(0, 0, 9), com.miscelanea.purgatorio.Purgado.Variante.HEROBRINE);
                RESUMEN.add("  purgatorio, habitante: " + (g == null ? "NO ha salido" : "sale en " + g.blockPosition().toShortString()));
                var g2 = com.miscelanea.purgatorio.Habitantes.aparecer(l, b.offset(-2, 0, 11), com.miscelanea.purgatorio.Purgado.Variante.SIN_TEXTURA);
                var g3 = com.miscelanea.purgatorio.Habitantes.aparecer(l, b.offset(1, 0, 13), com.miscelanea.purgatorio.Purgado.Variante.RELLENO);
                if (g2 != null) g2.setYRot(180);
                if (g3 != null) g3.setYRot(200);
            });
        });
        paso("49_purgatorio_habitantes", 20, () -> {
            mc.player.setYRot(0);
            mc.player.setXRot(6);
        });
        for (int i = 0; i < 4; i++) {
            int ii = i;
            paso("49_purgatorio_habitante_" + i, 12, () -> {
                mc.player.setYRot(0);
                mc.player.setXRot(6);
                com.miscelanea.purgatorio.Habitantes.habla(mc.player.getUUID());
                var l = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                if (l == null) return;
                var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA);
                StringBuilder sb = new StringBuilder("  habitantes " + ii + ":");
                for (var g : l.getEntitiesOfClass(com.miscelanea.purgatorio.Purgado.class, new net.minecraft.world.phys.AABB(b).inflate(40)))
                    sb.append(String.format(" %s a %.1f%s", g.variante(), Math.sqrt(g.distanceToSqr(b.getX() + 0.5, b.getY(), b.getZ() + 0.5)), g.cazando() ? " (cazando)" : ""));
                RESUMEN.add(sb.toString());
            });
        }
        sin("purgatorio_habitante_fin", 60, () -> com.miscelanea.purgatorio.Habitantes.habla(mc.player.getUUID()));
        sin("purgatorio_habitante_mira", 2, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA);
            double d = Math.sqrt(mc.player.distanceToSqr(b.getX() + 0.5, b.getY(), b.getZ() + 0.5));
            RESUMEN.add(String.format("  purgatorio, habitante: el jugador está a %.0f bloques de donde estaba (%s)", d, d > 20 ? "le ha pillado y le ha recolocado" : "NO le ha pillado"));
            if (d <= 20) RESUMEN.add("  AVISO: el habitante no ha pillado al jugador");
        });
        sin("purgatorio_sala_ir", 50, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA), 10, 5));
        paso("42_purgatorio_sala", 30, () -> {});
        paso("43_purgatorio_sala_2", 30, () -> mc.player.setYRot(mc.player.getYRot() + 140));
        sin("purgatorio_recreativa_ir", 50, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.RECREATIVA), 0, 8));
        paso("45_purgatorio_recreativa", 40, () -> {});
        paso("46_purgatorio_recreativa_2", 30, () -> {
            mc.player.setYRot(-45);
            mc.player.setXRot(12);
            // máquinas de alrededor: fundidas, vivas, y si las vivas tienen su bloque-entidad
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.RECREATIVA);
            var lp = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            int fund = 0, vivas = 0, conBe = 0;
            if (lp != null)
                for (var q : net.minecraft.core.BlockPos.betweenClosed(b.offset(-24, 0, -24), b.offset(24, 0, 24))) {
                    var st = lp.getBlockState(q);
                    if (st.getBlock() instanceof com.miscelanea.purgatorio.RecreativaApagadaBlock) fund++;
                    else if (st.getBlock() instanceof com.miscelanea.arcade.RecreativaBlock) {
                        vivas++;
                        if (lp.getChunkAt(q).getBlockEntity(q, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) != null) conBe++;
                    }
                }
            RESUMEN.add("  purgatorio, recreativa: " + fund + " fundidas, " + vivas + " vivas (" + conBe + " con bloque-entidad), ambiente: "
                    + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando());
            if (vivas > 0 && conBe < vivas) RESUMEN.add("  AVISO: hay recreativas vivas sin bloque-entidad");
        });
        sin("purgatorio_hotel_ir", 50, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.HOTEL), 0, 5));
        paso("4a_purgatorio_hotel", 40, () -> {});
        paso("4b_purgatorio_hotel_2", 30, () -> {
            mc.player.setYRot(90);
            RESUMEN.add("  purgatorio, hotel: ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando());
        });
        sin("purgatorio_hotel_cuarto", 30, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.HOTEL).offset(4, 0, 4), 45, 25));
        paso("4c_purgatorio_hotel_cuarto", 30, () -> {});
        sin("purgatorio_sin_textura_ir", 50, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SIN_TEXTURA), 30, 6));
        paso("47_purgatorio_sin_textura", 40, () -> {});
        paso("48_purgatorio_sin_textura_2", 30, () -> {
            mc.player.setYRot(mc.player.getYRot() + 150);
            RESUMEN.add("  purgatorio, sin texturas: ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando());
        });
        sin("purgatorio_salida_ir", 50, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscarSalida(), 180, 0));
        paso("44_purgatorio_salida", 30, () -> {});
        sin("purgatorio_cruzar", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscarSalida();
            // que no ande ningún habitante cerca (si le pilla, le recoloca y no llega a la puerta)
            var lp = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            if (lp != null) mc.getSingleplayerServer().execute(() -> {
                for (var g : lp.getEntitiesOfClass(com.miscelanea.purgatorio.Purgado.class, new net.minecraft.world.phys.AABB(b).inflate(96))) g.discard();
            });
            irA(new net.minecraft.core.BlockPos(b.getX(), b.getY(), b.getZ() - 3), 180, 0);
        });
        sin("purgatorio_fuera", 5, () -> {
            var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var b = com.miscelanea.purgatorio.Purgatorio.buscarSalida();
            var lp = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            RESUMEN.add("  purgatorio (servidor): jugador en " + (sp == null ? "?" : sp.level().dimension().location() + " " + sp.blockPosition().toShortString()
                    + (sp.isPassenger() ? " MONTADO EN " + sp.getVehicle() : ""))
                    + ", bloque de la puerta: " + (lp == null ? "?" : lp.getBlockState(new net.minecraft.core.BlockPos(b.getX(), b.getY(), b.getZ() - 3))));
            boolean fuera = !com.miscelanea.purgatorio.Purgatorio.en(mc.level);
            RESUMEN.add("  purgatorio: zona del origen " + com.miscelanea.purgatorio.PurgatorioGenerador.zona(0, 0)
                    + ", salida en " + com.miscelanea.purgatorio.Purgatorio.buscarSalida() + ", ha salido por la puerta: " + (fuera ? "sí" : "NO"));
            if (!fuera) throw new IllegalStateException("la puerta de salida no le ha sacado");
        });
        // otra salida: el agujero del Mundo sin Texturas (se cae fuera del Purgatorio)
        sin("purgatorio_agujero", 60, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            var ag = com.miscelanea.purgatorio.Purgatorio.buscarAgujero();
            RESUMEN.add("  purgatorio: agujero del Mundo sin Texturas en " + ag);
            if (ag == null) return;
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                var l = srv.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                if (sp == null || l == null) return;
                com.miscelanea.purgatorio.Purgatorio.entrar(sp, null);
                com.miscelanea.purgatorio.Purgatorio.preparar(l, ag);
                // como si llevara un rato allí (si no, al caerse nada más llegar se le devuelve al mismo sitio)
                sp.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).putLong("miscelanea_purgatorio_t", l.getGameTime() - 2000);
                sp.teleportTo(l, ag.getX() + 0.5, ag.getY(), ag.getZ() - 3.5, 0, 40);
            });
        });
        paso("4d_purgatorio_agujero", 30, () -> mc.player.setXRot(40));
        sin("purgatorio_agujero_caer", 140, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            var ag = com.miscelanea.purgatorio.Purgatorio.buscarAgujero();
            if (ag == null) return;
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) sp.teleportTo(ag.getX() + 0.5, ag.getY() + 0.5, ag.getZ() + 0.5);
            });
        });
        sin("purgatorio_agujero_fuera", 5, () -> {
            boolean fuera = !com.miscelanea.purgatorio.Purgatorio.en(mc.level);
            RESUMEN.add("  purgatorio: ha salido por el agujero: " + (fuera ? "sí" : "NO") + " (y=" + (int) mc.player.getY() + ")");
            if (!fuera) RESUMEN.add("  AVISO: el agujero no le ha sacado");
        });
        purgatorio2();
    }

    /** El Purgatorio, segunda parte (2.0.0, repaso): rincones raros, costuras, salir de un muro, configuración y el aviso de mundo experimental. */
    private static void purgatorio2() {
        Minecraft mc = Minecraft.getInstance();
        sin("purgatorio2_entrar", 60, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) com.miscelanea.purgatorio.Purgatorio.entrar(sp, null);
            });
        });
        // los rincones raros: la silla de cara a la pared, la única máquina encendida, la caseta abierta y la tele sin señal
        String[][] raros = {{"SALA", "4e_purgatorio_sala_silla"}, {"RECREATIVA", "4f_purgatorio_recreativa_una"},
                {"FERIA", "4g_purgatorio_caseta_peluche"}, {"HOTEL", "4h_purgatorio_hotel_tele"}};
        for (String[] r : raros) {
            var zona = com.miscelanea.purgatorio.PurgatorioGenerador.Zona.valueOf(r[0]);
            sin("ir_" + r[1], 50, () -> {
                if (!com.miscelanea.purgatorio.Purgatorio.en(mc.level)) throw new IllegalStateException("no está en el Purgatorio");
                var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(zona);
                RESUMEN.add("  purgatorio, rincón raro de " + zona + ": " + (b == null ? "NINGUNO cerca" : b[0].toShortString()));
                if (b == null) throw new IllegalStateException("no hay rincón raro de " + zona + " cerca del origen");
                irMirando(b[0], b[1]);
            });
            paso(r[1], 50, () -> {
                var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(zona);
                var lp = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                String que = lp == null || b == null ? "?" : lp.getBlockState(b[1]).getBlock().getDescriptionId();
                RESUMEN.add("  purgatorio, " + r[1] + ": mira a " + que + ", niebla " + String.format("%.1f", com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual())
                        + ", ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando());
                if (zona == com.miscelanea.purgatorio.PurgatorioGenerador.Zona.HOTEL && lp != null && !(lp.getBlockState(b[1]).getBlock() instanceof com.miscelanea.purgatorio.TeleBlock))
                    throw new IllegalStateException("en la habitación de la tele no hay tele: " + lp.getBlockState(b[1]));
                if (zona == com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA && lp != null && !(lp.getBlockState(b[1]).getBlock() instanceof com.miscelanea.feria.PelucheBlock))
                    throw new IllegalStateException("en la caseta abierta no hay peluche: " + lp.getBlockState(b[1]));
                // un susto ya, para ver que se programa y suena
                com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sustoYa();
            });
        }
        sin("purgatorio_sustos", 40, () -> RESUMEN.add("  purgatorio, tras los sustos: " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando()));
        // costuras entre zonas: de la feria a una sala de espera, y el borde del Mundo sin Texturas
        String[][] costuras = {{"FERIA", "SALA", "4i_purgatorio_costura_feria_sala"}, {"SIN_TEXTURA", "HOTEL", "4j_purgatorio_costura_sin_textura"},
                {"FERIA", "SIN_TEXTURA", "4k_purgatorio_costura_feria_sin_textura"}};
        for (String[] c : costuras) {
            var a = com.miscelanea.purgatorio.PurgatorioGenerador.Zona.valueOf(c[0]);
            var b = com.miscelanea.purgatorio.PurgatorioGenerador.Zona.valueOf(c[1]);
            sin("ir_" + c[2], 50, () -> {
                net.minecraft.core.BlockPos p = null;
                for (int r = 0; r < 60 && p == null; r++)
                    for (int cx = -r; cx <= r && p == null; cx++)
                        for (int cz = -r; cz <= r && p == null; cz++)
                            if (Math.max(Math.abs(cx), Math.abs(cz)) == r && com.miscelanea.purgatorio.PurgatorioGenerador.zona(cx, cz) == a
                                    && com.miscelanea.purgatorio.PurgatorioGenerador.zona(cx + 1, cz) == b && !com.miscelanea.purgatorio.PurgatorioGenerador.salida(cx, cz))
                                p = new net.minecraft.core.BlockPos(cx * 16 + 9, com.miscelanea.purgatorio.PurgatorioGenerador.SUELO, cz * 16 + 8);
                RESUMEN.add("  purgatorio, costura " + a + " -> " + b + ": " + (p == null ? "NINGUNA cerca" : p.toShortString()));
                if (p != null) irA(p, -90, 8);
            });
            paso(c[2], 40, () -> RESUMEN.add("  purgatorio, " + c[2] + ": niebla " + String.format("%.1f", com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual())
                    + ", ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando()));
        }
        // la niebla cambia poco a poco al pasar de zona: de la feria (34) a la sala (24) no salta de golpe
        sin("purgatorio_niebla_cruza", 3, () -> {
            irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA), 0, 0);
            RESUMEN.add(String.format("  purgatorio, niebla antes de cruzar: %.1f", com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual()));
        });
        sin("purgatorio_niebla_cruza_2", 60, () -> RESUMEN.add(String.format("  purgatorio, niebla a los 3 tics de cruzar: %.1f (fundiéndose)",
                com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual())));
        sin("purgatorio_niebla_cruza_3", 2, () -> {
            float d = com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual();
            RESUMEN.add(String.format("  purgatorio, niebla a los 3 s: %.1f (sala: 24)", d));
            if (Math.abs(d - 24) > 1.5F) throw new IllegalStateException("la niebla no ha llegado a la de la sala: " + d);
        });
        // habitantes: cuántos hay y que no pasan del tope
        sin("purgatorio_cuantos", 2, () -> {
            var srv = mc.getSingleplayerServer();
            var lp = srv.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            if (lp == null) return;
            srv.execute(() -> {
                int n = com.miscelanea.purgatorio.Habitantes.cuantos(lp);
                RESUMEN.add("  purgatorio, habitantes ahora: " + n + " (tope por jugador " + com.miscelanea.purgatorio.PurgatorioConfig.habitantesPorJugador()
                        + ", total " + com.miscelanea.purgatorio.PurgatorioConfig.maxHabitantes() + ")");
            });
        });
        // salir después de colarse por un muro: no puede volver a quedar dentro del bloque
        sin("purgatorio_muro", 40, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                com.miscelanea.purgatorio.Purgatorio.salir(sp);
                var ow = sp.serverLevel();
                var g = sp.blockPosition();
                // un muro macizo de 3 x 4 x 3 donde está (como si se hubiera asfixiado dentro)
                for (var q : net.minecraft.core.BlockPos.betweenClosed(g.offset(-1, 0, -1), g.offset(1, 3, 1)))
                    ow.setBlockAndUpdate(q, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                com.miscelanea.purgatorio.Purgatorio.entrar(sp, "purgatorio.miscelanea.noclip");
            });
        });
        sin("purgatorio_muro_salir", 40, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) com.miscelanea.purgatorio.Purgatorio.salir(sp);
            });
        });
        sin("purgatorio_muro_fuera", 2, () -> {
            var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            var l = sp.serverLevel();
            var pies = sp.blockPosition();
            boolean libre = l.getBlockState(pies).getCollisionShape(l, pies).isEmpty() && l.getBlockState(pies.above()).getCollisionShape(l, pies.above()).isEmpty();
            RESUMEN.add("  purgatorio, salir tras colarse por un muro: en " + l.dimension().location() + " " + pies.toShortString() + (libre ? " (fuera del muro)" : " DENTRO DEL MURO"));
            if (com.miscelanea.purgatorio.Purgatorio.en(l)) throw new IllegalStateException("no ha salido del Purgatorio");
            if (!libre) throw new IllegalStateException("ha salido dentro del muro");
        });
        // configuración: con el Purgatorio desactivado, a quien esté dentro se le saca
        sin("purgatorio_config_entrar", 40, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) com.miscelanea.purgatorio.Purgatorio.entrar(sp, null);
            });
        });
        sin("purgatorio_config_apagar", 60, () -> {
            RESUMEN.add("  purgatorio, configuración: activado=" + com.miscelanea.purgatorio.PurgatorioConfig.activado() + ", pared="
                    + com.miscelanea.purgatorio.PurgatorioConfig.probabilidadPared() + ", vacío=" + com.miscelanea.purgatorio.PurgatorioConfig.entrarAlCaer()
                    + ", dentro=" + com.miscelanea.purgatorio.Purgatorio.en(mc.level));
            com.miscelanea.purgatorio.PurgatorioConfig.paraPrueba(false);
        });
        sin("purgatorio_config_fuera", 2, () -> {
            boolean dentro = com.miscelanea.purgatorio.Purgatorio.en(mc.level);
            com.miscelanea.purgatorio.PurgatorioConfig.paraPrueba(true);
            RESUMEN.add("  purgatorio, desactivado: " + (dentro ? "SIGUE DENTRO" : "le ha sacado"));
            if (dentro) throw new IllegalStateException("con el Purgatorio desactivado sigue dentro");
        });
        // el aviso de "configuración experimental" al crear un mundo (y que el del abrir está puesto)
        sin("purgatorio_aviso_experimental", 5, () -> {
            var c = net.minecraft.client.gui.screens.worldselection.WorldOpenFlows.class;
            StringBuilder sb = new StringBuilder("  aviso experimental: ");
            int puestos = 0;
            for (var m : c.getDeclaredMethods())
                if (m.getName().contains("sinAvisoAlCrear") || m.getName().contains("sinAvisoAlAbrir")) {
                    puestos++;
                    sb.append(m.getName()).append(' ');
                }
            boolean[] creado = {false};
            boolean encontrado = false;
            for (var m : c.getDeclaredMethods()) {
                var t = m.getParameterTypes();
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && t.length == 5 && t[0] == Minecraft.class && t[2] == com.mojang.serialization.Lifecycle.class
                        && t[3] == Runnable.class && t[4] == boolean.class) {
                    encontrado = true;
                    try {
                        m.setAccessible(true);
                        m.invoke(null, mc, null, com.mojang.serialization.Lifecycle.experimental(), (Runnable) () -> creado[0] = true, false);
                    } catch (Exception ex) {
                        sb.append("(no se pudo llamar: ").append(ex).append(") ");
                    }
                }
            }
            var pantalla = mc.screen;
            if (pantalla != null) {
                sb.append("sale la pantalla ").append(pantalla.getClass().getSimpleName()).append(' ');
                mc.setScreen(null);
            }
            sb.append(!encontrado ? "al crear: no se encuentra el método" : creado[0] ? "al crear: sin aviso" : "al crear: CON AVISO");
            RESUMEN.add(sb.toString());
            if (puestos < 2) throw new IllegalStateException("el mixin del aviso experimental no está puesto (" + puestos + " de 2)");
            if (encontrado && !creado[0]) throw new IllegalStateException("al crear un mundo sale el aviso experimental");
        });
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
        // un paso puede pedir esperar a que pase algo (con un tope)
        if (hasta != null) {
            boolean listo;
            try {
                listo = hasta.getAsBoolean();
            } catch (RuntimeException ex) {
                listo = false;
            }
            if (!listo && --hastaMax > 0) return;
            if (!listo) RESUMEN.add("  AVISO: se ha cansado de esperar");
            hasta = null;
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
