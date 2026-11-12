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
        // las armas dan logros: sin avisos ni chat mientras se prueban (que no ensucien las capturas)
        // (el chat no se borra: el reto del duelo sale en él; el aviso de los logros en el chat se apaga con la regla)
        sin("armas_sin_avisos", 1, () -> {
            sinToasts = true;
            com.miscelanea.armasfuego.comun.EquilibrioArmas.sinAtascosPrueba = true;
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> srv.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, srv));
        });
        pistola();
        escopeta();
        rifle();
        armas3d();
        armas2();
        sin("armas_avisos", 1, () -> {
            com.miscelanea.armasfuego.comun.EquilibrioArmas.sinAtascosPrueba = false;
            sinToasts = false;
            mc.getToasts().clear();
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> srv.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(true, srv));
        });
        logros();
        sin("cerrar", 10, () -> {
            mc.setScreen(null);
            RESUMEN.add("  cara propia (la de su modelo) guardada: " + (CarasCliente.tiene(mc.player.getUUID()) ? "sí" : "NO"));
        });
    }

    // ------------------------------------------------------------------ logros
    /** Todos los logros de la pestaña de Miscelánea (data/miscelanea/advancements). */
    private static final String[] LOGROS = ("raiz,movil/movil,movil/foto,movil/video,movil/minegram,movil/flash,movil/mensaje,movil/llamada,movil/videollamada,movil/sin_cobertura,movil/numero_oculto,movil/bizum,arcade/ficha,arcade/partida,arcade/record,arcade/pleno,arcade/canal,feria/noria,feria/choque,feria/tirolina,feria/trampolin,feria/campana,feria/jackpot,mesa/victoria,mesa/ajedrez,mesa/parchis,mesa/calavera,mesa/billar,mesa/futbolin,baile/pista,baile/videoclip,baile/walken,baile/nota_s,baile/nota_e,pociones/gigante,pociones/enano,pociones/borrachera,pociones/tartamudeo,pociones/ventilador,pociones/eco,pociones/laboratorio,pociones/fantasma,bromas/uwu,bromas/australia,bromas/estatica,sala/lazo,sala/grilletes,sala/engrilletado,sala/ganzua,sala/tomatazo,dream/esencia,dream/mordisco,dream/escapar,dream/veneno,dream/medianoche,armas/gambito,armas/laser,armas/hacha,armas/aspira,armas/uber,armas/cofre,armas/ruleta,armas/ruleta_fogueo,armas/duelo,armas/gallina,armas/sedante,armas/encasquillado,armas/salto,armas/reventon,armas/granjero,armas/aspirar_piedras,armas/cuerda,armas/francotirador,armas/gatling,armas/gatling_calor,armas/gatling_cubo,armas/winchester,armas/winchester_giro,armas/derringer,armas/violin,armas/thompson_tambor,armas/mosquete,armas/bayoneta,armas/polvora_mojada,armas/canon,armas/canon_revienta,armas/tiempo_bala,armas/goma,armas/pintura,armas/atasco,armas/plata,armas/oro,armas/carta,armas/soaker,armas/palomitas,armas/cadena,armas/feria,armas/patina,armas/gatling_tolva,armas/gatling_torreta,armas/gatling_vagoneta,armas/gatling_barco,armas/guisantes,armas/winchester_mezcla,armas/winchester_hielo,armas/derringer_manga,armas/derringer_humo,armas/paganini,armas/tinta,armas/bayoneta_netherita,armas/arpon,armas/yunque,armas/castillo_arena,armas/rebote,armas/cera_cinco,armas/bayoneta_ignea,armas/coleccionista,varios/paella,varios/bacalao,varios/ocarina").split(",");
    /** Los que ya tienen que estar hechos por los pasos de antes de la prueba (móvil, cámara, vídeo, gramola, pociones). */
    private static final String[] LOGROS_YA = {"raiz", "movil/movil", "movil/foto", "movil/video", "baile/pista", "baile/videoclip",
            "pociones/eco", "pociones/gigante"};
    /** Los que se dan aquí: por el criterio propio (miscelanea:accion), por un efecto y por tener un objeto con NBT. */
    private static final String[] LOGROS_AQUI = {"sala/lazo", "movil/bizum", "feria/tirolina", "mesa/parchis", "pociones/borrachera",
            "varios/paella", "varios/bacalao"};
    /** Los que dan las pruebas de las armas de fuego (van antes que esta). */
    private static final String[] LOGROS_ARMAS = {"armas/cofre", "armas/ruleta_fogueo", "armas/duelo", "armas/sedante", "armas/salto", "armas/reventon",
            "armas/granjero", "armas/aspirar_piedras", "armas/cuerda", "armas/gatling_calor", "armas/gatling_cubo", "armas/winchester", "armas/winchester_giro",
            "armas/violin", "armas/mosquete", "armas/tiempo_bala", "armas/pintura"};
    private static volatile List<String> logrosHechos, logrosFaltan;
    /** Mientras se mide la imagen: fuera los avisos de logros (y su mensaje en el chat). */
    private static boolean sinAvisos;
    /** Mientras se prueban las armas (que dan logros): fuera los avisos de logros de las capturas. */
    private static boolean sinToasts;
    private static volatile int logrosCargados = -1;

    /**
     * Los logros: se mira por código que se cargan todos los de la pestaña y que no hay errores de sus JSON en el registro;
     * que los pasos de antes ya han dado los suyos (foto, vídeo, baile, pociones…) y que se dan de verdad al hacer la
     * acción (el criterio propio, un efecto y un objeto). Capturas del aviso al conseguirlos y de la pantalla de logros con
     * la pestaña de Miscelánea abierta.
     */
    private static void logros() {
        Minecraft mc = Minecraft.getInstance();
        sin("logros_cargados", 10, () -> {
            mc.setScreen(null);
            mc.options.hideGui = false;
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                List<String> faltan = new ArrayList<>();
                int n = 0;
                for (var a : srv.getAdvancements().getAllAdvancements())
                    if (a.getId().getNamespace().equals(Miscelanea.MOD_ID)) n++;
                for (String id : LOGROS)
                    if (srv.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, id)) == null) faltan.add(id);
                logrosFaltan = faltan;
                logrosCargados = n;
            });
        });
        sin("logros_comprobar_carga", 1, () -> {
            if (logrosFaltan == null) throw new IllegalStateException("el servidor no ha contestado");
            // errores de los JSON en el registro (logs/latest.log)
            List<String> errores = new ArrayList<>();
            try {
                for (String l : Files.readAllLines(new File(mc.gameDirectory, "logs/latest.log").toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    String b = l.toLowerCase(java.util.Locale.ROOT);
                    if (b.contains("miscelanea") && b.contains("advancement") && (b.contains("error") || b.contains("couldn't") || b.contains("invalid")))
                        errores.add(l.length() > 300 ? l.substring(0, 300) : l);
                }
            } catch (Exception ex) {
                RESUMEN.add("  logros: no se pudo leer logs/latest.log: " + ex);
            }
            RESUMEN.add("  logros: " + logrosCargados + " cargados de la pestaña (se esperan " + LOGROS.length + ")"
                    + (logrosFaltan.isEmpty() ? "" : " · faltan " + logrosFaltan) + (errores.isEmpty() ? " · sin errores en el registro" : ""));
            for (String e : errores) RESUMEN.add("  logros, error en el registro: " + e);
            if (!logrosFaltan.isEmpty() || logrosCargados != LOGROS.length) throw new IllegalStateException("no se cargan todos los logros");
            if (!errores.isEmpty()) throw new IllegalStateException("errores al cargar los logros");
        });
        sin("logros_dar", 30, () -> {
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                com.miscelanea.logros.LogrosMiscelanea.dar(sp, "lazo");
                com.miscelanea.logros.LogrosMiscelanea.dar(sp, "bizum");
                com.miscelanea.logros.LogrosMiscelanea.dar(sp, "tirolina");
                com.miscelanea.logros.LogrosMiscelanea.dar(sp.getUUID(), "parchis_come");
                com.miscelanea.logros.LogrosMiscelanea.dar(sp, "accion_que_no_existe");
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.miscelanea.voz.VozRegistro.BORRACHERA.get(), 200, 0));
                sp.getInventory().setItem(8, com.miscelanea.cocina.PaellaItem.bacalao());
            });
        });
        paso("95_logros_aviso", 20, () -> {});
        sin("logros_mirar", 10, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                List<String> hechos = new ArrayList<>();
                for (String l : LOGROS) {
                    var a = srv.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, l));
                    if (a != null && sp.getAdvancements().getOrStartProgress(a).isDone()) hechos.add(l);
                }
                sp.removeEffect(com.miscelanea.voz.VozRegistro.BORRACHERA.get());
                sp.getInventory().setItem(8, ItemStack.EMPTY);
                logrosHechos = hechos;
            });
        });
        sin("logros_comprobar", 1, () -> {
            List<String> h = logrosHechos;
            if (h == null) throw new IllegalStateException("el servidor no ha contestado");
            RESUMEN.add("  logros conseguidos (" + h.size() + "): " + String.join(" ", h));
            List<String> faltan = new ArrayList<>();
            for (String l : LOGROS_YA) if (!h.contains(l)) faltan.add(l);
            for (String l : LOGROS_AQUI) if (!h.contains(l)) faltan.add(l);
            for (String l : LOGROS_ARMAS) if (!h.contains(l)) faltan.add(l);
            if (!h.contains("baile/walken")) RESUMEN.add("  AVISO logros: no ha salido el de Weapon of Choice (¿no llegó a empezar el videoclip del hotel?)");
            if (!faltan.isEmpty()) throw new IllegalStateException("no se han dado: " + faltan);
        });
        paso("96_logros_pantalla", 30, () -> {
            var ca = mc.player.connection.getAdvancements();
            var raiz = ca.getAdvancements().get(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "raiz"));
            if (raiz == null) throw new IllegalStateException("el cliente no tiene la pestaña de Miscelánea");
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
            ca.setSelectedTab(raiz, true);
            mc.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(ca));
        });
        // arrastrando con el ratón: la parte de arriba del árbol (el móvil, la recreativa…)
        paso("97_logros_pantalla_arriba", 20, () -> {
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen a)) throw new IllegalStateException("no está la pantalla de logros");
            double x = a.width / 2.0, y = a.height / 2.0;
            a.mouseDragged(x, y, 0, 0, 0);
            a.mouseDragged(x, y, 0, 60, 400);
        });
        sin("logros_fin", 5, () -> mc.setScreen(null));
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
    private static volatile boolean piVigilaBrillo, escVigilaMiel, a2VigilaGatling, escVigilaSalto, a2CongelaYunque, a2VigilaNotas, escVigilaEscarcha;

    /** Las notitas del violín que se ven en el cliente: lo más que ha habido a la vez (se mira en cada tick). */
    @SubscribeEvent
    public static void a2NotasTick(TickEvent.ClientTickEvent e) {
        if (!ACTIVA || e.phase != TickEvent.Phase.END || !a2VigilaNotas) return;
        int n = a2Notas();
        PI.merge("v_notas_max", n, (a, b) -> Math.max((Integer) a, (Integer) b));
    }
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
                l.setWeatherParameters(6000, 0, false, false);   // sin lluvia: apaga el fuego y cambia la luz
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
                    v.setNoAi(true);   // quietas hasta el fogueo: si no, alguna se mete delante del husk
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
        paso("103_bala_desenfunde", 3, () -> {
            // el servidor mira con la vista de ahora y cuenta el desenfunde desde ya (a pocos fps se pasaba la ventana)
            float y = mc.player.getYRot(), x = mc.player.getXRot();
            piSrv(sp -> {
                sp.setYRot(y);
                sp.setXRot(x);
                com.miscelanea.armasfuego.pistola.Revolver.desenfundePrueba(sp);
            });
            piDisparar();
        });
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
        sin("pistola_carambola_traza", 4, () -> {
          float cy = mc.player.getYRot(), cx = mc.player.getXRot();
          piSrv(sp -> {
            // la vista del servidor puede ir por detrás de la del cliente (con shaders): se pone la de ahora
            sp.setYRot(cy);
            sp.setXRot(cx);
            var im = com.miscelanea.armasfuego.pistola.Revolver.trazar(sp.serverLevel(), sp, sp.getEyePosition(), sp.getViewVector(1F), 64, 1, sp.getEyePosition());
            PI.put("traza", im.rebotes() + " rebote(s), da a " + (im.entidad() == null ? "nada" : im.entidad().getType().toShortString()));
          });
        });
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
        sin("pistola_bengala_sube", 70, () -> {
            piApuntar(piPunto(7, 0, 0.5));
            // el brillo de Señalero se renueva cada segundo: se mira en cada tick del servidor si el husk ha brillado
            PI.remove("husk_brillo");
            piVigilaBrillo = true;
        });
        sin("pistola_bengala_ver", 4, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            var bs = l.getEntitiesOfClass(com.miscelanea.armasfuego.pistola.BengalaEntity.class, new net.minecraft.world.phys.AABB(piO).inflate(40));
            int luces = 0, luzCalculada = 0;
            var junto = net.minecraft.core.BlockPos.containing(piPunto(7, 0, 0.5));
            for (var b : net.minecraft.core.BlockPos.betweenClosed(piO.offset(-24, -2, -24), piO.offset(24, 24, 24))) {
                var st = l.getBlockState(b);
                if (st.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                    luces++;
                    // la luz que llega al husk (la del motor de luz va en otro hilo y a veces aún no estaba puesta)
                    luzCalculada = Math.max(luzCalculada, st.getValue(net.minecraft.world.level.block.LightBlock.LEVEL) - b.distManhattan(junto));
                }
            }
            var h = piHusk(sp);
            piVigilaBrillo = false;
            boolean brilla = h != null && (h.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) || PI.containsKey("husk_brillo"));
            String donde = h == null ? " (no está el husk)" : bs.isEmpty() ? "" : String.format(" (a %.1f bloques en horizontal de la bengala)",
                    Math.hypot(h.getX() - bs.get(0).getX(), h.getZ() - bs.get(0).getZ()));
            PI.put("bengala", bs.size() + " bengala(s)" + (bs.isEmpty() ? "" : String.format(" a %.1f bloques de altura", bs.get(0).getY() - piO.getY()))
                    + ", " + luces + " bloque(s) de luz, " + com.miscelanea.armasfuego.pistola.LucesTemporales.cuantas(sp.server) + " apuntadas, husk brilla: "
                    + brilla + (brilla ? "" : donde));
            PI.put("bengala_ok", !bs.isEmpty() && luces >= 1 && brilla
                    && Math.max(luzCalculada, l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, junto)) >= 8);
        }));
        paso("106_bengala_noche", 10, () -> piComprobar("bengala: sube, ilumina (bloques de luz) y con Señalero el husk brilla",
                Boolean.TRUE.equals(PI.get("bengala_ok")), String.valueOf(PI.get("bengala"))));
        sin("pistola_lejos_poner", 20, () -> {
            var bs = mc.level.getEntitiesOfClass(com.miscelanea.armasfuego.pistola.BengalaEntity.class, mc.player.getBoundingBox().inflate(48));
            Vec3 obj = bs.isEmpty() ? piPunto(10, 0, 12) : bs.get(0).position();
            Vec3 pos = piPunto(-36, -10, 34);
            mc.options.hideGui = true;
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
            mc.options.hideGui = false;
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
                for (var v : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, new net.minecraft.world.phys.AABB(piO).inflate(30))) {
                    d += v.distanceTo(sp);
                    v.setNoAi(false);
                }
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
        sin("pistola_iman_espera", 50, () -> {});
        sin("pistola_iman_ver", 4, () -> piSrv(sp -> {
            int oro = 0;
            for (ItemStack x : sp.getInventory().items) if (x.is(net.minecraft.world.item.Items.GOLD_INGOT) || x.is(net.minecraft.world.item.Items.EMERALD)) oro += x.getCount();
            PI.put("iman", oro);
        }));
        sin("pistola_iman_comprobar", 4, () -> {
            mc.player.setXRot(88);
            mc.player.xRotO = 88;
            piSrv(sp -> {
                sp.removeAllEffects();
                sp.setHealth(sp.getMaxHealth());
                PI.put("vida_sien", sp.getHealth());
            });
            piComprobar("bala imán: lo tirado viene volando hacia ti", (Integer) PI.getOrDefault("iman", 0) >= 4, PI.get("iman") + " objetos recogidos de 6");
        });
        // ---- ruleta rusa a la sien con fogueo
        paso("111_ruleta_sien", 4, piTiro());
        sin("pistola_sien_ver", 6, () -> piSrv(sp -> PI.put("sien", sp.isAlive() && sp.getHealth() >= (Float) PI.getOrDefault("vida_sien", 20F)
                && sp.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION))));
        sin("pistola_sien_comprobar", 4, () -> {
            boolean sien = Boolean.TRUE.equals(PI.get("sien"));
            piSrv(sp -> {
                sp.removeAllEffects();
                for (int i = 1; i <= 3; i++) sp.getInventory().setItem(i, piRevolver(i));
                sp.getMainHandItem().getOrCreateTag().putInt(com.miscelanea.armasfuego.pistola.Tambor.OXIDO, 3);
                sp.getServer().overworld().setDayTime(6000);
            });
            piApuntar(piPunto(8, 2, 1.0));
            piComprobar("ruleta a la sien con fogueo (vivo, mareado)", sien, String.valueOf(PI.get("sien")));
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

    // ------------------------------------------------------------------ rifle de catalejo (pasos 140-159)
    private static volatile int rifleZ1 = -1, rifleZ2 = -1, rifleGolem = -1;
    private static volatile net.minecraft.core.BlockPos rifleO, rifleSensor;
    private static volatile net.minecraft.core.Direction rifleMira;
    /** Lo que ha salido mal en una comprobación hecha en el hilo del servidor (se mira en el paso siguiente). */
    private static volatile String rifleFallo;
    private static boolean rifleSalto;
    /** Se vigila en cada tick si va colgado de la tirolina; la captura 149_colgado se saca a los pocos ticks de colgarse. */
    private static boolean rifleVigilaColgado, rifleColgadoVisto;
    private static int rifleColgadoTicks, rifleColgadoFotograma;
    private static int rifleFotosAntes, rifleCuerdasAntes;

    /** Algo en el hilo del servidor con el jugador. */
    private static void rifleSrv(java.util.function.Consumer<ServerPlayer> c) {
        Minecraft mc = Minecraft.getInstance();
        var srv = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            try {
                c.accept(sp);
            } catch (Throwable t) {
                rifleFallo = t.toString();
            }
        });
    }

    /** Lo mismo pero dentro de n ticks del servidor (para mirar algo que dura poco, como un sensor de sculk). */
    private static final List<Object[]> RIFLE_TRAS = new ArrayList<>();

    private static void rifleSrvTras(int n, java.util.function.Consumer<ServerPlayer> c) {
        synchronized (RIFLE_TRAS) {
            RIFLE_TRAS.add(new Object[]{n, c, Minecraft.getInstance().player.getUUID()});
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void rifleServidor(TickEvent.ServerTickEvent e) {
        if (!ACTIVA || e.phase != TickEvent.Phase.END) return;
        List<Object[]> listos = new ArrayList<>();
        synchronized (RIFLE_TRAS) {
            for (var it = RIFLE_TRAS.iterator(); it.hasNext(); ) {
                Object[] t = it.next();
                t[0] = (Integer) t[0] - 1;
                if ((Integer) t[0] <= 0) {
                    listos.add(t);
                    it.remove();
                }
            }
        }
        if (piVigilaBrillo && piHusk != null) {
            for (var lv : e.getServer().getAllLevels()) {
                var h = lv.getEntity(piHusk);
                if (h instanceof net.minecraft.world.entity.LivingEntity le && le.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) PI.put("husk_brillo", true);
            }
        }
        if (a2VigilaGatling && a2Gatling != null) {
            for (var lv : e.getServer().getAllLevels())
                if (lv.getEntity(a2Gatling) instanceof com.miscelanea.armasfuego.gatling.GatlingEntity g && g.atasco() > 0) PI.put("g_atasco_visto", g.atasco());
        }
        if (a2CongelaYunque) {
            for (var lv : e.getServer().getAllLevels())
                for (var b : lv.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(com.miscelanea.armasfuego.oeste.BolaCanonEntity.class),
                        x -> x.getItem().is(net.minecraft.world.item.Items.ANVIL) && x.tickCount >= 3)) {
                    if (!PI.containsKey("arco_pos")) {
                        PI.put("arco_pos", b.position());
                        PI.put("arco_vel", b.getDeltaMovement());
                    }
                    b.setDeltaMovement(Vec3.ZERO);
                    b.setNoGravity(true);
                }
        }
        if (escVigilaEscarcha && escHusk != null) {
            for (var lv : e.getServer().getAllLevels())
                if (lv.getEntity(escHusk) instanceof net.minecraft.world.entity.LivingEntity le && le.getTicksFrozen() > 0) PI.put("escarcha_frio", le.getTicksFrozen());
        }
        if (escVigilaSalto) {
            for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers())
                PI.merge("salto_max", sp.getY(), (a, b) -> Math.max((Double) a, (Double) b));
        }
        if (escVigilaMiel && escHusk != null) {
            for (var lv : e.getServer().getAllLevels())
                if (lv.getEntity(escHusk) instanceof net.minecraft.world.entity.LivingEntity le && le.hasEffect(com.miscelanea.registry.ModEffects.PRINGADO.get()))
                    PI.put("miel_visto", true);
        }
        for (Object[] t : listos) {
            ServerPlayer sp = e.getServer().getPlayerList().getPlayer((UUID) t[2]);
            if (sp == null) continue;
            try {
                ((java.util.function.Consumer<ServerPlayer>) t[1]).accept(sp);
            } catch (Throwable ex) {
                rifleFallo = ex.toString();
            }
        }
    }

    private static void rifleComprobar() {
        String f = rifleFallo;
        rifleFallo = null;
        if (f != null) throw new IllegalStateException(f);
    }

    /** Gira la vista del jugador para que mire a q. */
    private static void rifleApuntar(Vec3 q) {
        var p = Minecraft.getInstance().player;
        Vec3 d = q.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
    }

    private static net.minecraft.world.entity.Entity rifleEnte(int id) {
        var e = Minecraft.getInstance().level.getEntity(id);
        if (e == null) throw new IllegalStateException("no está la entidad " + id);
        return e;
    }

    private static void rifleClic() {
        var p = Minecraft.getInstance().player;
        com.miscelanea.client.armasfuego.rifle.RifleCliente.clic(p, p.getMainHandItem());
    }

    private static void rifleUsar(boolean si) {
        mantenerUso = si;
        Minecraft.getInstance().options.keyUse.setDown(si);
    }

    private static int rifleCuenta(net.minecraft.world.item.Item i) {
        var inv = Minecraft.getInstance().player.getInventory();
        int n = 0;
        for (int k = 0; k < inv.getContainerSize(); k++) if (inv.getItem(k).is(i)) n += inv.getItem(k).getCount();
        return n;
    }

    /**
     * El rifle de catalejo: cerrojo, mira (zoom del catalejo y telémetro en el HUD), disparo a la cabeza de un husk,
     * al cuerpo de otro con armadura de diamante (la mitad del daño se salta la armadura), bala trazadora (brillo), foto
     * con la mira a la galería del móvil, sensor de sculk con y sin Silenciador, bala de cuerda (tirolina: colgarse
     * saltando, bajar y cortarla con tijeras), óxido del cobre (encerar y rascar) y encantamientos en la mesa.
     */
    private static void rifle() {
        Minecraft mc = Minecraft.getInstance();
        sin("rifle_poner", 40, () -> {
            // un menú abierto (cofre…) se cierra también en el servidor
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.player.closeContainer();
            mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            rifleSrv(sp -> {
                var l = sp.serverLevel();
                l.setWeatherParameters(6000, 0, false, false);   // sin lluvia: apaga el fuego y cambia la luz
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                rifleO = o;
                rifleMira = mira;
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                var suelo = net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -7).relative(mira, -3).below(), o.relative(lado, 7).relative(mira, 30).above(12)))
                    l.setBlock(b, b.getY() < o.getY() ? suelo : aire, 2);
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(o).inflate(40),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
                // dianas: dos husks (no se queman al sol) y un gólem de hierro, quietos
                java.util.function.BiFunction<net.minecraft.world.entity.Mob, net.minecraft.core.BlockPos, Integer> poner = (m, p) -> {
                    m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, mira.getOpposite().toYRot(), 0);
                    m.setNoAi(true);
                    m.setPersistenceRequired();
                    l.addFreshEntity(m);
                    return m.getId();
                };
                var z1 = new net.minecraft.world.entity.monster.Husk(net.minecraft.world.entity.EntityType.HUSK, l);
                rifleZ1 = poner.apply(z1, o.relative(mira, 24).relative(lado, -3));
                var z2 = new net.minecraft.world.entity.monster.Husk(net.minecraft.world.entity.EntityType.HUSK, l);
                z2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
                z2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
                rifleZ2 = poner.apply(z2, o.relative(mira, 24).relative(lado, 3));
                var golem = new net.minecraft.world.entity.animal.IronGolem(net.minecraft.world.entity.EntityType.IRON_GOLEM, l);
                rifleGolem = poner.apply(golem, o.relative(mira, 26));
                // sensor de sculk al lado del tirador
                rifleSensor = o.relative(lado, -3).relative(mira, -1);   // a la izquierda: los casquillos salen por la derecha
                l.setBlock(rifleSensor, net.minecraft.world.level.block.Blocks.SCULK_SENSOR.defaultBlockState(), 3);
                sp.setGameMode(GameType.SURVIVAL);
                sp.removeAllEffects();
                sp.setHealth(sp.getMaxHealth());
                sp.getInventory().clearContent();
                sp.getInventory().setItem(0, new ItemStack(com.miscelanea.registry.ModItems.RIFLE.get()));
                sp.getInventory().setItem(1, new ItemStack(com.miscelanea.registry.ModItems.BALA_RIFLE.get(), 32));
                sp.getInventory().setItem(2, new ItemStack(com.miscelanea.registry.ModItems.BALA_TRAZADORA.get(), 8));
                sp.getInventory().setItem(3, new ItemStack(com.miscelanea.registry.ModItems.BALA_CUERDA.get(), 2));
                sp.getInventory().setItem(4, new ItemStack(net.minecraft.world.item.Items.SHEARS));
                sp.getInventory().setItem(8, new ItemStack(MovilRegistro.MOVIL.get()));
                sp.getInventory().selected = 0;
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 0F);
            });
        });
        paso("140_rifle_mano", 20, () -> {
            rifleComprobar();
            mc.player.getInventory().selected = 0;
            mc.gui.getChat().clearMessages(false);
            if (!(mc.player.getMainHandItem().getItem() instanceof com.miscelanea.armasfuego.rifle.RifleItem)) throw new IllegalStateException("no tiene el rifle en la mano");
        });
        // primer clic: la recámara está vacía, así que acciona el cerrojo (y mete una bala)
        paso("141_cerrojo", 5, RifleCI::clic);
        sin("rifle_cargado", 25, () -> {});
        paso("142_mira", 50, () -> {
            if (!com.miscelanea.armasfuego.rifle.Rifle.lista(mc.player.getMainHandItem()))
                throw new IllegalStateException("el cerrojo no ha metido la bala: " + mc.player.getMainHandItem().getTag());
            var z = rifleEnte(rifleZ1);
            rifleApuntar(new Vec3(z.getX(), z.getEyeY(), z.getZ()));
            rifleUsar(true);
        });
        sin("rifle_disparo_cabeza", 15, () -> {
            if (!mc.player.isScoping()) throw new IllegalStateException("no mira por el catalejo");
            double d = com.miscelanea.client.armasfuego.rifle.RifleCliente.distancia;
            RESUMEN.add(String.format("  rifle: telémetro %.1f m · %s · firme %s", d, com.miscelanea.client.armasfuego.rifle.RifleCliente.objetivo.getString(),
                    com.miscelanea.client.armasfuego.rifle.RifleCliente.firme()));
            if (d < 15 || d > 35) throw new IllegalStateException("el telémetro no marca la distancia al husk: " + d);
            rifleClic();
            // el sensor de sculk, a 3 bloques, se activa un momento con el ruido del disparo
            rifleSrvTras(8, sp -> {
                var fase = net.minecraft.world.level.block.SculkSensorBlock.getPhase(sp.serverLevel().getBlockState(rifleSensor));
                RESUMEN.add("  rifle: sensor de sculk tras el disparo: " + fase);
                if (fase != net.minecraft.world.level.block.state.properties.SculkSensorPhase.ACTIVE) rifleFallo = "el sensor de sculk no ha oído el disparo";
            });
        });
        sin("rifle_cabeza_comprobar", 2, () -> rifleSrv(sp -> {
            var l = sp.serverLevel();
            var z = l.getEntity(rifleZ1);
            // 9 x 1,6 en la cabeza = 14,4 (equilibrio 1.1.0: ya no mata de un tiro a nadie con 20 de vida)
            boolean muerto = z == null || !z.isAlive() || ((net.minecraft.world.entity.LivingEntity) z).getHealth() <= 0;
            float hp = muerto ? 0 : ((net.minecraft.world.entity.LivingEntity) z).getHealth();
            RESUMEN.add("  rifle: disparo a la cabeza → husk " + (muerto ? "muerto" : "vivo con " + hp) + " (esperado ~5,6)");
            if (hp > 7F && rifleFallo == null) rifleFallo = "el disparo a la cabeza no hace el daño de la cabeza (" + hp + ")";
        }));
        sin("rifle_cerrojo2", 25, () -> {
            rifleComprobar();
            rifleClic();
        });
        sin("rifle_apunta_cuerpo", 50, () -> {
            var z = rifleEnte(rifleZ2);
            rifleApuntar(new Vec3(z.getX(), z.getY() + 1.0, z.getZ()));
        });
        sin("rifle_disparo_cuerpo", 15, RifleCI::clic);
        sin("rifle_cuerpo_comprobar", 2, () -> rifleSrv(sp -> {
            var z = (net.minecraft.world.entity.LivingEntity) sp.serverLevel().getEntity(rifleZ2);
            float hp = z == null ? -1 : z.getHealth();
            // 9 de daño: la mitad contra armadura 13 (diamante + la del husk) y la otra mitad sin armadura → le quedan
            // unos 13,0 (sin perforar se quedaría en 14,4)
            RESUMEN.add(String.format("  rifle: disparo al cuerpo del husk con diamante → le quedan %.2f de vida (esperado ~13,0; sin perforar 14,4)", hp));
            if (hp < 12.2F || hp > 13.8F) rifleFallo = "el daño contra armadura no es el esperado (" + hp + ")";
        }));
        // trazadora: en la otra mano, el cerrojo la mete
        sin("rifle_trazadora", 25, () -> {
            rifleComprobar();
            rifleSrv(sp -> {
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, sp.getInventory().getItem(2).copy());
                sp.getInventory().setItem(2, ItemStack.EMPTY);
            });
        });
        sin("rifle_trazadora_cerrojo", 25, RifleCI::clic);
        sin("rifle_apunta_golem", 50, () -> {
            String rec = com.miscelanea.armasfuego.rifle.Rifle.recamara(mc.player.getMainHandItem());
            if (!rec.endsWith("bala_trazadora")) throw new IllegalStateException("el cerrojo no ha metido la trazadora de la otra mano: " + rec);
            var g = rifleEnte(rifleGolem);
            rifleApuntar(new Vec3(g.getX(), g.getY() + 1.3, g.getZ()));
        });
        paso("143_trazadora", 2, RifleCI::clic);
        paso("144_brillo", 30, () -> rifleUsar(false));
        sin("rifle_brillo_comprobar", 2, () -> {
            var g = (net.minecraft.world.entity.LivingEntity) rifleEnte(rifleGolem);
            boolean brilla = g.isCurrentlyGlowing() || g.hasGlowingTag();
            RESUMEN.add("  rifle: gólem tras la trazadora: brilla " + brilla + ", vida " + g.getHealth());
            if (!brilla) throw new IllegalStateException("la trazadora no ha hecho brillar al gólem");
        });
        // foto con la mira: a la galería del móvil (lo lleva en el inventario)
        sin("rifle_foto_mira", 30, () -> {
            rifleFotosAntes = MovilCliente.FOTOS.size();
            rifleUsar(true);
        });
        sin("rifle_foto", 30, com.miscelanea.client.armasfuego.rifle.RifleCliente::foto);
        paso("145_foto", 5, () -> {
            RESUMEN.add("  rifle: fotos " + com.miscelanea.client.armasfuego.rifle.RifleCliente.fotosHechas + " (" + com.miscelanea.client.armasfuego.rifle.RifleCliente.ultimaFoto
                    + "), galería " + rifleFotosAntes + " → " + MovilCliente.FOTOS.size());
            if (com.miscelanea.client.armasfuego.rifle.RifleCliente.fotosHechas == 0) throw new IllegalStateException("no se ha sacado la foto");
            if (MovilCliente.FOTOS.size() <= rifleFotosAntes) throw new IllegalStateException("la foto no ha llegado a la galería del móvil");
        });
        paso("146_foto_galeria", 50, () -> {
            rifleUsar(false);
            MovilCliente.abrir(0);
            MovilCliente.Foto f = MovilCliente.FOTOS.get(0);
            abrirVista(new VistasFotos.Foto(MovilRed.FOTO_PROPIA, f.id(), f.ms()));
        });
        // Silenciador: el sensor de sculk no se entera
        sin("rifle_silenciador", 60, () -> {
            mc.setScreen(null);
            rifleSrv(sp -> sp.getMainHandItem().enchant(com.miscelanea.registry.ModEnchantments.SILENCIADOR.get(), 1));
            rifleClic();
        });
        sin("rifle_silencio_apunta", 45, () -> {
            var o = rifleO;
            rifleApuntar(Vec3.atCenterOf(o.relative(rifleMira, 20)).add(0, -0.5, 0));
            rifleUsar(true);
        });
        sin("rifle_silencio_disparo", 10, () -> {
            if (!com.miscelanea.armasfuego.rifle.Rifle.lista(mc.player.getMainHandItem())) throw new IllegalStateException("no hay bala para el disparo con silenciador");
            rifleSrv(sp -> {
                // si ya estaba activo o en reposo (por el catalejo, el cerrojo o el tiro de antes), se deja quieto antes
                // del tiro: si no, un sensor ya activo hacía fallar la prueba sin que el disparo se oyera
                var st = sp.serverLevel().getBlockState(rifleSensor);
                var fase = net.minecraft.world.level.block.SculkSensorBlock.getPhase(st);
                if (fase != net.minecraft.world.level.block.state.properties.SculkSensorPhase.INACTIVE) {
                    RESUMEN.add("  rifle: el sensor de sculk estaba " + fase + " antes del tiro con silenciador; se pone quieto");
                    sp.serverLevel().setBlock(rifleSensor, st.setValue(net.minecraft.world.level.block.SculkSensorBlock.PHASE,
                            net.minecraft.world.level.block.state.properties.SculkSensorPhase.INACTIVE).setValue(net.minecraft.world.level.block.SculkSensorBlock.POWER, 0), 3);
                }
            });
            rifleClic();
            rifleSrvTras(8, sp -> {
                var fase = net.minecraft.world.level.block.SculkSensorBlock.getPhase(sp.serverLevel().getBlockState(rifleSensor));
                RESUMEN.add("  rifle: con Silenciador, el sensor de sculk tras el disparo: " + fase);
                if (fase == net.minecraft.world.level.block.state.properties.SculkSensorPhase.ACTIVE) rifleFallo = "el sensor de sculk ha oído el disparo con silenciador";
            });
        });
        sin("rifle_silencio_espera", 15, () -> {});
        // bala de cuerda: desde lo alto de una torre hasta el suelo
        sin("rifle_torre", 30, () -> {
            rifleUsar(false);
            rifleCuerdasAntes = rifleCuenta(com.miscelanea.registry.ModItems.BALA_CUERDA.get());
            rifleSrv(sp -> {
                var l = sp.serverLevel();
                var o = rifleO;
                for (int dy = 0; dy < 6; dy++) l.setBlock(o.above(dy), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, sp.getInventory().getItem(3).copy());
                sp.getInventory().setItem(3, ItemStack.EMPTY);
                sp.connection.teleport(o.getX() + 0.5, o.getY() + 6, o.getZ() + 0.5, rifleMira.toYRot(), 20F);
            });
            rifleComprobar();
        });
        sin("rifle_cuerda_cerrojo", 25, () -> {
            rifleCuerdasAntes = rifleCuenta(com.miscelanea.registry.ModItems.BALA_CUERDA.get());
            rifleClic();
        });
        sin("rifle_cuerda_apunta", 10, () -> {
            String rec = com.miscelanea.armasfuego.rifle.Rifle.recamara(mc.player.getMainHandItem());
            if (!rec.endsWith("bala_cuerda")) throw new IllegalStateException("el cerrojo no ha metido la bala de cuerda: " + rec);
            rifleApuntar(Vec3.atCenterOf(rifleO.relative(rifleMira, 18)).add(0, -0.5, 0));
        });
        paso("147_cuerda", 20, RifleCI::clic);
        paso("148_cuerda_fuera", 20, () -> {
            var l = com.miscelanea.client.armasfuego.rifle.TirolinasCliente.LINEAS;
            if (l.size() != 1) throw new IllegalStateException("no se ha tendido la tirolina (" + l.size() + ")");
            var x = l.get(0);
            RESUMEN.add(String.format("  rifle: tirolina de %.1f bloques, de %.1f a %.1f de altura", x.a().distanceTo(x.b()), x.a().y, x.b().y));
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            // que se guarda con el mundo: lo guardado y vuelto a cargar tiene las mismas tirolinas
            rifleSrv(sp -> {
                var d = com.miscelanea.armasfuego.rifle.Tirolinas.de(sp.serverLevel());
                var t = d.save(new net.minecraft.nbt.CompoundTag());
                var otra = com.miscelanea.armasfuego.rifle.Tirolinas.cargar(t);
                // (sin mirar isDirty: si justo se ha autoguardado el mundo ya no está «sucio» y fallaba sin motivo)
                if (otra.lineas().size() != d.lineas().size() || !otra.lineas().get(0).b().equals(d.lineas().get(0).b()))
                    rifleFallo = "la tirolina no se guarda bien con el mundo";
            });
        });
        // saltar debajo de la cuerda: se agarra. La bajada dura unos 2 s y con shaders cada paso tarda varios segundos
        // (4 fotogramas a 2-4 fps), así que se vigila en cada tick si va colgado y la captura se saca en cuanto lo está
        sin("rifle_salta", 12, () -> {
            rifleComprobar();
            rifleColgadoVisto = false;
            rifleColgadoTicks = 0;
            rifleVigilaColgado = true;
            rifleSalto = true;
        });
        sin("rifle_salta_soltar", 8, () -> {
            rifleSalto = false;
            mc.options.keyJump.setDown(false);
        });
        // si el primer salto no ha cogido la cuerda (pocos fotogramas con shaders), se vuelve a saltar
        sin("rifle_salta2", 12, () -> {
            if (!rifleColgadoVisto && !(mc.player.getVehicle() instanceof com.miscelanea.armasfuego.rifle.GanchoTirolina)) {
                RESUMEN.add("  rifle: el primer salto no ha cogido la cuerda; se salta otra vez");
                rifleSalto = true;
            }
        });
        sin("rifle_salta_soltar2", 8, () -> {
            rifleSalto = false;
            mc.options.keyJump.setDown(false);
        });
        sin("rifle_colgado", 3, () -> {
            rifleVigilaColgado = false;
            RESUMEN.add("  rifle: colgado de la tirolina " + rifleColgadoTicks + " ticks (captura 149_colgado " + (rifleColgadoVisto ? "sacada colgando" : "sin sacar") + ")");
            if (!rifleColgadoVisto && !(mc.player.getVehicle() instanceof com.miscelanea.armasfuego.rifle.GanchoTirolina))
                throw new IllegalStateException("saltando debajo de la cuerda no se ha colgado");
        });
        paso("150_bajando", 6, () -> {});
        sin("rifle_llegada", 100, () -> {});
        paso("151_abajo", 10, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            RESUMEN.add(String.format("  rifle: tras bajar está a %.1f del suelo, colgado: %s", mc.player.getY() - rifleO.getY(), mc.player.isPassenger()));
            if (mc.player.isPassenger()) throw new IllegalStateException("no ha llegado abajo");
            if (mc.player.getY() > rifleO.getY() + 3) throw new IllegalStateException("no ha bajado por la tirolina");
        });
        // cortarla con tijeras (clic derecho de verdad)
        sin("rifle_tijeras", 10, () -> {
            mc.player.getInventory().selected = 4;
            var x = com.miscelanea.client.armasfuego.rifle.TirolinasCliente.LINEAS.get(0);
            Vec3 ojo = mc.player.getEyePosition();
            double t = com.miscelanea.armasfuego.rifle.Cuerda.cercano(x.a(), x.b(), ojo);
            rifleApuntar(com.miscelanea.armasfuego.rifle.Cuerda.punto(x.a(), x.b(), t));
        });
        sin("rifle_cortar", 15, () -> net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey()));
        paso("152_cortada", 10, () -> {
            int n = rifleCuenta(com.miscelanea.registry.ModItems.BALA_CUERDA.get());
            RESUMEN.add("  rifle: tirolinas tras las tijeras " + com.miscelanea.client.armasfuego.rifle.TirolinasCliente.LINEAS.size() + ", balas de cuerda " + rifleCuerdasAntes + " → " + n);
            if (!com.miscelanea.client.armasfuego.rifle.TirolinasCliente.LINEAS.isEmpty()) throw new IllegalStateException("las tijeras no han cortado la tirolina");
            if (n < rifleCuerdasAntes) throw new IllegalStateException("las tijeras no han devuelto la bala de cuerda");
        });
        // cobre oxidado: textura verdosa; encerar con panal y rascar con hacha
        sin("rifle_oxido", 10, () -> {
            mc.player.getInventory().selected = 0;
            rifleSrv(sp -> {
                sp.getInventory().selected = 0;
                ItemStack r = sp.getInventory().getItem(0);
                r.getOrCreateTag().putInt(com.miscelanea.armasfuego.rifle.Rifle.OXIDO, 2);
                var item = r.getItem();
                ItemStack panal = new ItemStack(net.minecraft.world.item.Items.HONEYCOMB, 2);
                ItemStack hacha = new ItemStack(net.minecraft.world.item.Items.IRON_AXE);
                var der = net.minecraft.world.inventory.ClickAction.SECONDARY;
                boolean a = item.overrideOtherStackedOnMe(r, panal, null, der, sp, null) && com.miscelanea.armasfuego.rifle.Rifle.encerado(r) && panal.getCount() == 1;
                boolean b = item.overrideOtherStackedOnMe(r, hacha, null, der, sp, null) && !com.miscelanea.armasfuego.rifle.Rifle.encerado(r);
                boolean c = item.overrideOtherStackedOnMe(r, hacha, null, der, sp, null) && com.miscelanea.armasfuego.rifle.Rifle.oxido(r) == 1;
                r.getOrCreateTag().putInt(com.miscelanea.armasfuego.rifle.Rifle.OXIDO, 2);
                // encantamientos: en la mesa (nivel 30) y solo para el rifle
                var mesa = net.minecraft.world.item.enchantment.EnchantmentHelper.getAvailableEnchantmentResults(30, new ItemStack(item), false);
                java.util.Set<String> nuestros = new java.util.TreeSet<>();
                for (var ei : mesa) {
                    var k = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(ei.enchantment);
                    if (k != null && k.getNamespace().equals(Miscelanea.MOD_ID)) nuestros.add(k.getPath());
                }
                boolean soloRifle = !com.miscelanea.registry.ModEnchantments.ATRAVESAR.get().canEnchant(new ItemStack(net.minecraft.world.item.Items.BOW))
                        && com.miscelanea.registry.ModEnchantments.ATRAVESAR.get().canEnchant(new ItemStack(item));
                RESUMEN.add("  rifle: encerar " + a + ", desencerar " + b + ", rascar " + c + " · en la mesa: " + nuestros + " · solo para el rifle " + soloRifle);
                if (!a || !b || !c) rifleFallo = "encerar/rascar el cobre no ha ido bien";
                else if (nuestros.size() < 4 || !soloRifle) rifleFallo = "los encantamientos del rifle no salen bien en la mesa";
            });
        });
        paso("153_oxidado", 15, () -> {});
        sin("rifle_fin", 5, () -> {
            rifleComprobar();
            rifleUsar(false);
            rifleSalto = false;
            mc.options.keyJump.setDown(false);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            rifleSrv(sp -> sp.setGameMode(GameType.CREATIVE));
        });
    }

    // ------------------------------------------------------------------ las tres armas en 3D (pasos 160-180)
    /**
     * Las tres armas de fuego en la mano con su modelo 3D: primera persona, tercera de frente y de espaldas, y el
     * inventario con todas las armas (las cuatro fases del cobre) y sus municiones (que siguen siendo iconos 2D).
     */
    private static void armas3d() {
        Minecraft mc = Minecraft.getInstance();
        sin("armas3d_preparar", 30, () -> {
            mc.setScreen(null);
            mc.options.hideGui = false;
            mc.gui.getChat().clearMessages(false);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            rifleSrv(sp -> {
                var l = sp.serverLevel();
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                var suelo = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -8).relative(mira, -8).below(), o.relative(lado, 8).relative(mira, 12).above(6)))
                    l.setBlock(b, b.getY() < o.getY() ? suelo : aire, 2);
                // una pared de ladrillo lejos, para que el arma se vea sobre algo
                for (int k = -8; k <= 8; k++)
                    for (int y = 0; y <= 4; y++)
                        l.setBlock(o.relative(mira, 12).relative(lado, k).above(y), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(), 2);
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(o).inflate(20),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
                sp.setGameMode(GameType.SURVIVAL);
                sp.removeAllEffects();
                sp.setHealth(sp.getMaxHealth());
                sp.serverLevel().setDayTime(6000);
                sp.serverLevel().setWeatherParameters(6000, 0, false, false);
                var inv = sp.getInventory();
                inv.clearContent();
                ItemStack rev = piRevolver(0);
                for (int i = 0; i < 4; i++) com.miscelanea.armasfuego.pistola.Tambor.poner(rev, i, com.miscelanea.armasfuego.pistola.Carga.values()[1 + i % 5]);
                ItemStack esc = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get());
                com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(esc, 60);
                inv.setItem(0, rev);
                inv.setItem(1, esc);
                inv.setItem(2, new ItemStack(com.miscelanea.registry.ModItems.RIFLE.get()));
                // munición del revólver y del rifle en la barra
                int k = 3;
                for (var m : List.of(com.miscelanea.registry.ModItems.REVOLVER_BALA, com.miscelanea.registry.ModItems.REVOLVER_BENGALA,
                        com.miscelanea.registry.ModItems.REVOLVER_DARDO, com.miscelanea.registry.ModItems.REVOLVER_FOGUEO,
                        com.miscelanea.registry.ModItems.REVOLVER_IMAN, com.miscelanea.registry.ModItems.BALA_RIFLE))
                    inv.setItem(k++, new ItemStack(m.get(), 12));
                // en la mochila: las otras fases del cobre, el resto de la munición y los cartuchos (la casilla 13 se
                // deja vacía: al abrir el inventario el ratón queda en el centro, justo encima, y sacaría su ficha)
                k = 9;
                java.util.function.IntUnaryOperator sig = x -> x == 13 ? 14 : x;
                for (int o2 = 1; o2 < 4; o2++) {
                    k = sig.applyAsInt(k);
                    inv.setItem(k++, piRevolver(o2));
                    ItemStack e = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get());
                    com.miscelanea.armasfuego.escopeta.Presion.ponerOxido(e, o2);
                    com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(e, 30 * o2);
                    k = sig.applyAsInt(k);
                    inv.setItem(k++, e);
                    ItemStack r = new ItemStack(com.miscelanea.registry.ModItems.RIFLE.get());
                    r.getOrCreateTag().putInt(com.miscelanea.armasfuego.rifle.Rifle.OXIDO, o2);
                    k = sig.applyAsInt(k);
                    inv.setItem(k++, r);
                }
                for (var m : List.of(com.miscelanea.registry.ModItems.BALA_TRAZADORA, com.miscelanea.registry.ModItems.BALA_CUERDA,
                        com.miscelanea.registry.ModItems.CASQUILLO_RIFLE))
                    inv.setItem(k++, new ItemStack(m.get(), 8));
                for (var t : com.miscelanea.armasfuego.escopeta.TipoCartucho.values())
                    inv.setItem(k++, new ItemStack(t.item(), 16));
                inv.selected = 0;
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 0F);
            });
        });
        // las armas no se fabrican: salen en los cofres (puestos de saqueadores, barcos hundidos, aldeas)
        sin("armas_botin", 40, () -> rifleSrv(sp -> {
          try {
            var srv = sp.server;
            var l = sp.serverLevel();
            String[][] tablas = {{"chests/pillager_outpost", "rifle"}, {"chests/shipwreck_supply", "escopeta_presion"},
                    {"chests/shipwreck_treasure", "escopeta_presion"}, {"chests/shipwreck_map", "escopeta_presion"},
                    {"chests/village/village_weaponsmith", "revolver"}, {"chests/village/village_armorer", "revolver"},
                    {"chests/village/village_toolsmith", "revolver"}, {"chests/village/village_plains_house", "revolver"},
                    {"chests/simple_dungeon", "-"}};
            java.util.Set<String> armas = java.util.Set.of("revolver", "rifle", "escopeta_presion");
            StringBuilder sb = new StringBuilder(), mal = new StringBuilder();
            // se tira cada tabla muchas veces con su semilla, tal cual se abre un cofre (con los modificadores de Forge).
            // Sin ORIGIN: así el mapa del tesoro de los barcos no se pone a buscar estructuras (eso bloqueaba el servidor
            // decenas de segundos y la comprobación se quedaba sin datos)
            int total = 300;
            long semilla = 0x4D15CL;
            for (String[] t : tablas) {
                var id = new net.minecraft.resources.ResourceLocation("minecraft", t[0]);
                var tabla = srv.getLootData().getLootTable(id);
                if (tabla == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
                    mal.append("no existe la tabla ").append(t[0]).append("; ");
                    continue;
                }
                int n = 0, enc = 0, ox = 0, mun = 0;
                for (int i = 0; i < total; i++) {
                    var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(l)
                            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
                    boolean salio = false;
                    for (ItemStack it : tabla.getRandomItems(params, semilla++)) {
                        var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it.getItem());
                        if (k == null || !k.getNamespace().equals(Miscelanea.MOD_ID)) continue;
                        if (armas.contains(k.getPath())) {
                            if (!k.getPath().equals(t[1])) mal.append(k.getPath()).append(" en ").append(t[0]).append("; ");
                            n++;
                            salio = true;
                            if (it.isEnchanted()) enc++;
                            if (it.hasTag() && it.getTag().getInt("Oxido") > 0) ox++;
                        } else if (salio) mun++;
                    }
                }
                double p = com.miscelanea.armasfuego.BotinArmas.probabilidad(id);
                sb.append(String.format(java.util.Locale.ROOT, "%s %d/%d (%.0f %%, config %.0f %%; encantadas %d, oxidadas %d)",
                        t[0].replace("chests/", ""), n, total, 100.0 * n / total, 100 * p, enc, ox)).append(" · ");
                if (t[1].equals("-")) continue;
                double f = (double) n / total;
                if (n == 0) mal.append("no sale en ").append(t[0]).append("; ");
                else if (f < p * 0.4 || f > p * 1.8 + 0.03) mal.append(String.format(java.util.Locale.ROOT, "en %s sale el %.0f %% y debería ~%.0f %%; ", t[0], 100 * f, 100 * p));
                if (n > 0 && mun == 0) mal.append("sale sin munición en ").append(t[0]).append("; ");
            }
            // y ya no hay receta que las fabrique (la munición sí)
            int recMunicion = 0;
            for (var r : srv.getRecipeManager().getRecipes()) {
                ItemStack res;
                try {
                    res = r.getResultItem(srv.registryAccess());
                } catch (Throwable t) {
                    continue;   // recetas especiales de otros mods que no tienen un resultado fijo
                }
                if (res == null || res.isEmpty()) continue;
                var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(res.getItem());
                if (k == null || !k.getNamespace().equals(Miscelanea.MOD_ID)) continue;
                if (armas.contains(k.getPath())) mal.append("receta ").append(r.getId()).append("; ");
                if (k.getPath().startsWith("revolver_") || k.getPath().startsWith("bala_") || k.getPath().startsWith("escopeta_cartucho_") || k.getPath().equals("casquillo_rifle")) recMunicion++;
            }
            sb.append("recetas de munición ").append(recMunicion);
            if (recMunicion < 16) mal.append("faltan recetas de munición (").append(recMunicion).append("); ");
            PI.put("botin", sb.toString());
            PI.put("botin_mal", mal.toString());
          } catch (Throwable t) {
            Miscelanea.LOGGER.error("[PruebaCI] botín de las armas", t);
            PI.put("botin_mal", "error: " + t);
          }
        }));
        sin("armas_botin_comprobar", 4, () -> {
            RESUMEN.add("  armas en cofres: " + PI.getOrDefault("botin", "(sin datos)"));
            String mal = String.valueOf(PI.getOrDefault("botin_mal", "sin datos"));
            if (!mal.isEmpty()) throw new IllegalStateException("botín de las armas: " + mal);
        });
        // los modelos de mano son 3D (forge:separate_transforms) y el del inventario es el icono plano
        sin("armas3d_modelos", 4, () -> {
            var ir = mc.getItemRenderer();
            StringBuilder mal = new StringBuilder();
            for (var it : List.of(com.miscelanea.registry.ModItems.REVOLVER, com.miscelanea.registry.ModItems.ESCOPETA_PRESION,
                    com.miscelanea.registry.ModItems.RIFLE)) {
                ItemStack st = new ItemStack(it.get());
                var m = ir.getModel(st, mc.level, mc.player, 0);
                var mano = m.applyTransform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new com.mojang.blaze3d.vertex.PoseStack(), false);
                var gui = m.applyTransform(net.minecraft.world.item.ItemDisplayContext.GUI, new com.mojang.blaze3d.vertex.PoseStack(), false);
                boolean ok = m.getClass().getName().contains("SeparateTransforms") && mano != gui;
                String linea = it.getId().getPath() + ": " + m.getClass().getSimpleName() + (mano != gui ? " (mano 3D, inventario plano)" : " (MISMO MODELO EN MANO E INVENTARIO)");
                RESUMEN.add("  armas 3D: " + linea);
                if (!ok || m == ir.getItemModelShaper().getModelManager().getMissingModel()) mal.append(linea).append("; ");
            }
            if (mal.length() > 0) throw new IllegalStateException("modelos 3D de las armas: " + mal);
        });
        String[] nombres = {"revolver", "escopeta", "rifle"};
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            String n = nombres[i];
            int p0 = 160 + i * 4;
            paso(p0 + "_" + n + "_1p", 20, () -> {
                mc.player.getInventory().selected = slot;
                mc.player.setXRot(0F);
                mc.player.xRotO = 0F;
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            });
            paso((p0 + 1) + "_" + n + "_3p_frente", 15, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
            paso((p0 + 2) + "_" + n + "_3p_espalda", 15, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            // de lado (una cámara aparte a la derecha del jugador), para ver cómo la sujeta
            paso((p0 + 3) + "_" + n + "_3p_lado", 15, () -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                Vec3 ojo = mc.player.getEyePosition();
                Vec3 fwd = Vec3.directionFromRotation(0F, mc.player.getYRot());
                Vec3 der = new Vec3(-fwd.z, 0, fwd.x).normalize();
                Vec3 obj = ojo.add(fwd.scale(0.5)).add(0, -0.3, 0);
                Vec3 pos = obj.add(der.scale(2.4)).add(fwd.scale(0.4)).add(0, 0.2, 0);
                Vec3 d = obj.subtract(pos);
                float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float xRot = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                camaraFuera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, pos.x, pos.y, pos.z);
                camaraFuera.moveTo(pos.x, pos.y - camaraFuera.getEyeHeight(), pos.z, yRot, xRot);
                camaraFuera.setYHeadRot(yRot);
                mc.setCameraEntity(camaraFuera);
            });
            sin("armas3d_lado_quitar_" + n, 3, () -> {
                mc.setCameraEntity(mc.player);
                mc.options.hideGui = false;
            });
        }
        // la escopeta con la aguja en cada zona y reventada (el manómetro del costado se ve en primera persona)
        float[] presiones = {0F, 15F, 45F, 75F, 97F};
        for (int a = 0; a < presiones.length; a++) {
            final float pr = presiones[a];
            paso((172 + a) + "_escopeta_presion_" + a, 12, () -> {
                mc.player.getInventory().selected = 1;
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                rifleSrv(sp -> {
                    ItemStack e = sp.getInventory().getItem(1);
                    com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(e, pr);
                    RESUMEN.add("  escopeta 3D: presión " + pr + " → aguja " + com.miscelanea.armasfuego.escopeta.Presion.agujaModelo(e));
                });
            });
        }
        paso("177_escopeta_roja_3p_frente", 15, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        paso("178_escopeta_reventada", 12, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            rifleSrv(sp -> {
                ItemStack e = sp.getInventory().getItem(1);
                com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(e, 0F);
                e.getOrCreateTag().putLong(com.miscelanea.armasfuego.escopeta.Presion.REVENTADA, sp.level().getGameTime() + 300);
            });
        });
        sin("armas3d_escopeta_arreglar", 3, () -> rifleSrv(sp -> sp.getInventory().getItem(1).getOrCreateTag().remove(com.miscelanea.armasfuego.escopeta.Presion.REVENTADA)));
        paso("180_inventario_armas", 20, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        });
        sin("armas3d_fin", 5, () -> {
            mc.setScreen(null);
            mc.player.getInventory().selected = 0;
            rifleSrv(sp -> {
                sp.getInventory().clearContent();
                sp.setGameMode(GameType.CREATIVE);
            });
        });
    }

    // ------------------------------------------------------------------ armas de fuego, segunda tanda (pasos 200-259)
    private static volatile UUID a2Husk, a2Gatling, a2Oveja, a2Caballo;
    private static volatile net.minecraft.core.BlockPos a2O;
    private static volatile net.minecraft.core.Direction a2Mira, a2Lado;

    private static Vec3 a2Punto(double m, double l, double y) {
        var o = a2O;
        return new Vec3(o.getX() + 0.5 + a2Mira.getStepX() * m + a2Lado.getStepX() * l, o.getY() + y,
                o.getZ() + 0.5 + a2Mira.getStepZ() * m + a2Lado.getStepZ() * l);
    }

    private static void a2Comprobar(String que, boolean bien, String detalle) {
        RESUMEN.add("  armas2: " + que + ": " + detalle);
        if (!bien) throw new IllegalStateException(que + " (" + detalle + ")");
    }

    private static Object a2Error() {
        Object e = PI.remove("error");
        return e;
    }

    private static net.minecraft.world.entity.LivingEntity a2Ente(ServerPlayer sp, UUID id) {
        return id == null ? null : (net.minecraft.world.entity.LivingEntity) sp.serverLevel().getEntity(id);
    }

    private static ItemStack a2Cargada(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> it, int balas) {
        ItemStack s = new ItemStack(it.get());
        com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(s, balas);
        return s;
    }

    /** Pone en la mano (casilla 0) lo que se pida, con la vista al husk. */
    private static void a2Mano(ItemStack s) {
        piSrv(sp -> {
            sp.getInventory().setItem(0, s);
            sp.getInventory().selected = 0;
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        });
    }

    /** Vida del husk (se guarda en PI con esa clave). */
    private static void a2Vida(String clave) {
        piSrv(sp -> {
            var h = a2Ente(sp, a2Husk);
            PI.put(clave, h == null ? -1F : h.getHealth());
        });
    }

    private static float a2F(String k) {
        Object o = PI.get(k);
        return o instanceof Float f ? f : -999F;
    }

    /** Cámara aparte a un lado del jugador (para verle de lado con el arma). */
    private static void a2Vista(double der, double adelante, double alto) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.options.hideGui = true;
        Vec3 ojo = mc.player.getEyePosition();
        Vec3 fwd = Vec3.directionFromRotation(0F, mc.player.getYRot());
        Vec3 d0 = new Vec3(-fwd.z, 0, fwd.x).normalize();
        Vec3 obj = ojo.add(fwd.scale(0.6)).add(0, -0.4, 0);
        Vec3 pos = obj.add(d0.scale(der)).add(fwd.scale(adelante)).add(0, alto, 0);
        Vec3 d = obj.subtract(pos);
        float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float xRot = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        camaraFuera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, pos.x, pos.y, pos.z);
        camaraFuera.moveTo(pos.x, pos.y - camaraFuera.getEyeHeight(), pos.z, yRot, xRot);
        camaraFuera.setYHeadRot(yRot);
        mc.setCameraEntity(camaraFuera);
    }

    /** Cámara aparte en {@code pos} mirando a {@code obj}. */
    private static void a2VistaA(Vec3 obj, Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.options.hideGui = true;
        Vec3 d = obj.subtract(pos);
        float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float xRot = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        camaraFuera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, pos.x, pos.y, pos.z);
        camaraFuera.moveTo(pos.x, pos.y - camaraFuera.getEyeHeight(), pos.z, yRot, xRot);
        camaraFuera.setYHeadRot(yRot);
        mc.setCameraEntity(camaraFuera);
    }

    private static void a2Camara1p() {
        Minecraft mc = Minecraft.getInstance();
        mc.setCameraEntity(mc.player);
        mc.options.hideGui = false;
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
    }

    private static boolean a2Logro(ServerPlayer sp, String id) {
        var a = sp.server.getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, id));
        return a != null && sp.getAdvancements().getOrStartProgress(a).isDone();
    }

    /**
     * Las seis armas nuevas (Gatling, Winchester, Derringer, Thompson con su estuche, mosquete y cañón de mano) y la
     * munición nueva de las tres primeras (tiempo bala, goma y pintura): se disparan de verdad contra un husk con mucha
     * vida (se mira por código que pierde vida y que se gastan las balas), se recargan con la animación, se calienta la
     * Gatling hasta atascarse, se enfría con un cubo y se recoge; capturas en primera y tercera persona y disparando.
     */
    private static void armas2() {
        Minecraft mc = Minecraft.getInstance();
        sin("a2_preparar", 40, () -> {
            mc.setScreen(null);
            a2Camara1p();
            mc.gui.getChat().clearMessages(false);
            piSrv(sp -> {
                var l = sp.serverLevel();
                l.setWeatherParameters(6000, 0, false, false);
                l.setDayTime(6000);
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -9).relative(mira, -6).below(), o.relative(lado, 9).relative(mira, 18).above(8)))
                    l.setBlock(b, b.getY() < o.getY() ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState() : aire, 2);
                // pared de ladrillo al fondo y una de lana blanca a la izquierda (para la pintura)
                for (int k = -8; k <= 8; k++)
                    for (int y = 0; y <= 5; y++)
                        l.setBlock(o.relative(mira, 17).relative(lado, k).above(y), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(), 2);
                for (int k = 2; k <= 6; k++)
                    for (int y = 0; y <= 3; y++)
                        l.setBlock(o.relative(mira, k + 4).relative(lado, -6).above(y), net.minecraft.world.level.block.Blocks.WHITE_WOOL.defaultBlockState(), 2);
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(o).inflate(30),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
                a2O = o.immutable();
                a2Mira = mira;
                a2Lado = lado;
                // un husk quieto con mucha vida a 8 bloques y una oveja blanca a la derecha
                var h = net.minecraft.world.entity.EntityType.HUSK.create(l);
                Vec3 hp = a2Punto(8, 0, 0);
                h.moveTo(hp.x, hp.y, hp.z, mira.toYRot() + 180, 0);
                h.setNoAi(true);
                h.setPersistenceRequired();
                h.setSilent(true);
                h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                h.setHealth(1000);
                l.addFreshEntity(h);
                a2Husk = h.getUUID();
                var ov = net.minecraft.world.entity.EntityType.SHEEP.create(l);
                Vec3 op = a2Punto(7, 4, 0);
                ov.moveTo(op.x, op.y, op.z, 0, 0);
                ov.setNoAi(true);
                ov.setPersistenceRequired();
                l.addFreshEntity(ov);
                a2Oveja = ov.getUUID();
                sp.setGameMode(GameType.SURVIVAL);
                sp.removeAllEffects();
                sp.setHealth(sp.getMaxHealth());
                sp.getFoodData().setFoodLevel(20);
                var inv = sp.getInventory();
                inv.clearContent();
                inv.setItem(9, new ItemStack(com.miscelanea.registry.ModItems.CINTA_GATLING.get(), 4));
                inv.setItem(10, new ItemStack(com.miscelanea.registry.ModItems.REVOLVER_BALA.get(), 32));
                inv.setItem(11, new ItemStack(com.miscelanea.registry.ModItems.BALA_DERRINGER.get(), 16));
                inv.setItem(12, new ItemStack(com.miscelanea.registry.ModItems.CARGADOR_THOMPSON.get(), 2));
                inv.setItem(14, new ItemStack(com.miscelanea.registry.ModItems.BOLA_MOSQUETE.get(), 8));
                inv.setItem(15, new ItemStack(net.minecraft.world.item.Items.GUNPOWDER, 32));
                inv.setItem(16, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 8));
                inv.setItem(1, new ItemStack(net.minecraft.world.item.Items.STICK));
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 0F);
            });
        });
        // ================================================================ Gatling
        sin("a2_gatling_poner", 20, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            var g = com.miscelanea.registry.ModEntities.GATLING.get().create(l);
            Vec3 q = a2Punto(1.3, 0, 0);
            g.moveTo(q.x, q.y, q.z, a2Mira.toYRot(), 0);
            ItemStack it = new ItemStack(com.miscelanea.registry.ModItems.GATLING.get());
            it.getOrCreateTag().putInt(com.miscelanea.armasfuego.gatling.GatlingItem.BALAS, com.miscelanea.armasfuego.comun.EquilibrioArmas.gatlingCinta());
            g.desde(it);
            l.addFreshEntity(g);
            a2Gatling = g.getUUID();
        }));
        paso("200_gatling_plantada", 15, () -> a2Vista(2.6, 1.6, 0.6));
        sin("a2_gatling_montar", 10, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var g = sp.serverLevel().getEntity(a2Gatling);
                if (g == null) throw new IllegalStateException("no está la Gatling");
                sp.startRiding(g, true);
            });
        });
        paso("201_gatling_mandos", 10, () -> {
            if (!(mc.player.getVehicle() instanceof com.miscelanea.armasfuego.gatling.GatlingEntity))
                throw new IllegalStateException("no se ha subido a la Gatling (vehículo: " + mc.player.getVehicle() + ")");
            piApuntar(a2Punto(8, 0, 1.2));
        });
        sin("a2_gatling_vida0", 2, () -> a2Vida("g0"));
        sin("a2_gatling_girar", 12, () -> com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = true);
        paso("202_gatling_disparando", 4, () -> piApuntar(a2Punto(8, 0, 1.2)));
        paso("203_gatling_disparando_3p", 8, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            piApuntar(a2Punto(8, 0, 1.2));
        });
        sin("a2_gatling_vida1", 2, () -> {
            a2Vida("g1");
            piSrv(sp -> {
                if (sp.getVehicle() instanceof com.miscelanea.armasfuego.gatling.GatlingEntity g) PI.put("g_balas", g.balas());
            });
        });
        sin("a2_gatling_comprobar", 2, () -> {
            float d = a2F("g0") - a2F("g1");
            a2Comprobar("la Gatling dispara (manivela con clic derecho) y hiere", d > 4, "el husk ha perdido " + d + " de vida; quedan " + PI.get("g_balas") + " balas");
        });
        // al rojo: se pone casi en 100 y sigue disparando
        sin("a2_gatling_calentar", 2, () -> piSrv(sp -> {
            // la cinta llena y casi al rojo: a veces se vaciaba la cinta (y recargaba) antes de llegar a 100; y con
            // shaders el atasco (5 s) podía pasarse antes de mirarlo, así que se vigila en cada tick
            PI.remove("g_atasco_visto");
            a2VigilaGatling = true;
            if (sp.getVehicle() instanceof com.miscelanea.armasfuego.gatling.GatlingEntity g) {
                g.ponerBalasPrueba(g.balasCinta());
                g.ponerCalor(99F);
            }
        }));
        paso("204_gatling_al_rojo", 14, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        sin("a2_gatling_atasco", 4, () -> piSrv(sp -> {
            a2VigilaGatling = false;
            if (sp.getVehicle() instanceof com.miscelanea.armasfuego.gatling.GatlingEntity g) {
                PI.put("g_atasco", Math.max(g.atasco(), PI.get("g_atasco_visto") instanceof Integer i ? i : 0));
                PI.put("g_logro", a2Logro(sp, "armas/gatling_calor"));
            }
        }));
        paso("205_gatling_atascada_1p", 6, () -> {
            com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = false;
            a2Camara1p();
            int at = PI.get("g_atasco") instanceof Integer i ? i : -1;
            a2Comprobar("la Gatling se calienta, se atasca y quema", at > 0 && Boolean.TRUE.equals(PI.get("g_logro")), "atasco " + at + " ticks, logro " + PI.get("g_logro"));
        });
        sin("a2_gatling_cubo", 6, () -> piSrv(sp -> {
            var g = (com.miscelanea.armasfuego.gatling.GatlingEntity) sp.serverLevel().getEntity(a2Gatling);
            sp.stopRiding();
            sp.clearFire();
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            g.interact(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
            PI.put("g_calor", g.calor());
            PI.put("g_cubo", sp.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET));
        }));
        sin("a2_gatling_recoger", 6, () -> {
            a2Comprobar("un cubo de agua enfría la Gatling", a2F("g_calor") == 0F && Boolean.TRUE.equals(PI.get("g_cubo")), "calor " + PI.get("g_calor") + ", cubo vacío " + PI.get("g_cubo"));
            piSrv(sp -> {
                var g = (com.miscelanea.armasfuego.gatling.GatlingEntity) sp.serverLevel().getEntity(a2Gatling);
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sp.setShiftKeyDown(true);
                g.interact(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
                sp.setShiftKeyDown(false);
                PI.put("g_recogida", g.isRemoved() && sp.getInventory().hasAnyMatching(x -> x.is(com.miscelanea.registry.ModItems.GATLING.get())));
            });
        });
        paso("206_gatling_en_mano", 10, () -> {
            a2Comprobar("agachado + clic derecho recoge la Gatling", Boolean.TRUE.equals(PI.get("g_recogida")), String.valueOf(PI.get("g_recogida")));
            piSrv(sp -> {
                int i = sp.getInventory().findSlotMatchingItem(new ItemStack(com.miscelanea.registry.ModItems.GATLING.get()));
                if (i >= 0) {
                    ItemStack g = sp.getInventory().getItem(i);
                    sp.getInventory().setItem(i, ItemStack.EMPTY);
                    sp.getInventory().setItem(0, g);
                }
                sp.getInventory().selected = 0;
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
            });
        });
        paso("207_gatling_en_mano_3p", 12, () -> a2Vista(2.4, 0.5, 0.2));
        // ================================================================ Winchester
        sin("a2_winchester", 8, () -> {
            a2Camara1p();
            a2Mano(a2Cargada(com.miscelanea.registry.ModItems.WINCHESTER, 6));
            piApuntar(a2Punto(8, 0, 1.2));
        });
        paso("210_winchester_1p", 4, () -> a2Vida("w0"));
        paso("211_winchester_disparo", 2, piTiro());
        paso("212_winchester_palanca", 3, () -> {});
        sin("a2_winchester_ver", 10, () -> {
            a2Vida("w1");
            piSrv(sp -> PI.put("w_balas", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_winchester_ok", 2, () -> a2Comprobar("la Winchester dispara, hiere y gasta una bala", a2F("w0") - a2F("w1") > 3 && Integer.valueOf(5).equals(PI.get("w_balas")),
                "vida " + a2F("w0") + " → " + a2F("w1") + ", balas " + PI.get("w_balas")));
        sin("a2_winchester_agachar", 4, () -> mc.options.keyShift.setDown(true));
        sin("a2_winchester_recargar", 2, () -> rifleUsar(true));
        paso("213_winchester_recargando", 22, () -> {});
        sin("a2_winchester_recargado", 4, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("w_balas2", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_winchester_rec_ok", 2, () -> a2Comprobar("la Winchester se recarga bala a bala", PI.get("w_balas2") instanceof Integer b && b >= 7, "balas " + PI.get("w_balas2")));
        sin("a2_atasco_poner", 4, () -> piSrv(sp -> sp.getMainHandItem().getOrCreateTag().putBoolean(com.miscelanea.armasfuego.comun.ArmaFuegoItem.ATASCO, true)));
        sin("a2_atasco_clic", 6, piTiro());
        paso("216_winchester_atascada", 2, () -> mc.options.keyShift.setDown(true));
        sin("a2_atasco_quitar", 2, () -> rifleUsar(true));
        paso("217_winchester_desatascando", 10, () -> {});
        sin("a2_atasco_fin", 14, () -> {});
        sin("a2_atasco_ver", 4, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("w_atasco", com.miscelanea.armasfuego.comun.ArmaFuegoItem.atascada(sp.getMainHandItem())));
        });
        sin("a2_atasco_ok", 2, () -> a2Comprobar("encasquillada no dispara y agachado + clic derecho la desatasca", Boolean.FALSE.equals(PI.get("w_atasco")), "atascada " + PI.get("w_atasco")));
        paso("214_winchester_3p_frente", 12, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        // a caballo: la palanca se gira con una mano
        sin("a2_caballo", 10, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var l = sp.serverLevel();
                var c = net.minecraft.world.entity.EntityType.HORSE.create(l);
                c.moveTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0);
                c.setTamed(true);
                c.equipSaddle(null);
                c.setNoAi(true);
                l.addFreshEntity(c);
                a2Caballo = c.getUUID();
                sp.startRiding(c, true);
            });
        });
        sin("a2_caballo_apuntar", 4, () -> piApuntar(a2Punto(8, 0, 1.2)));
        sin("a2_caballo_tiro", 3, piTiro());
        paso("215_winchester_giro_caballo", 1, () -> a2Vista(2.8, 0.4, 0.4));
        sin("a2_caballo_fuera", 6, () -> {
            a2Camara1p();
            piSrv(sp -> {
                PI.put("w_giro", a2Logro(sp, "armas/winchester_giro"));
                sp.stopRiding();
                var c = sp.serverLevel().getEntity(a2Caballo);
                if (c != null) c.discard();
                sp.connection.teleport(a2O.getX() + 0.5, a2O.getY(), a2O.getZ() + 0.5, a2Mira.toYRot(), 0F);
            });
        });
        sin("a2_caballo_ok", 2, () -> a2Comprobar("la Winchester a caballo (giro de la palanca)", Boolean.TRUE.equals(PI.get("w_giro")), "logro " + PI.get("w_giro")));
        // ================================================================ Derringer
        sin("a2_derringer", 8, () -> {
            a2Mano(a2Cargada(com.miscelanea.registry.ModItems.DERRINGER, 2));
            piApuntar(a2Punto(5, 0, 1.2));
        });
        paso("220_derringer_oculta_3p", 12, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        sin("a2_derringer_vida", 2, () -> a2Vida("d0"));
        paso("221_derringer_disparo_3p", 3, piTiro());
        paso("222_derringer_1p", 8, A2::camara1p);
        sin("a2_derringer_ver", 4, () -> {
            a2Vida("d1");
            piSrv(sp -> PI.put("d_balas", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_derringer_ok", 2, () -> a2Comprobar("la Derringer dispara y gasta un cañón", a2F("d0") - a2F("d1") > 2 && Integer.valueOf(1).equals(PI.get("d_balas")),
                "vida " + a2F("d0") + " → " + a2F("d1") + ", balas " + PI.get("d_balas")));
        sin("a2_derringer_agachar", 4, () -> mc.options.keyShift.setDown(true));
        sin("a2_derringer_recargar", 2, () -> rifleUsar(true));
        paso("223_derringer_abierta", 12, () -> {});
        sin("a2_derringer_fin", 20, () -> {});
        sin("a2_derringer_recargada", 4, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("d_balas2", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_derringer_rec_ok", 2, () -> a2Comprobar("la Derringer se recarga partiéndola", Integer.valueOf(2).equals(PI.get("d_balas2")), "balas " + PI.get("d_balas2")));
        // ================================================================ Thompson y su estuche
        sin("a2_estuche", 8, () -> {
            ItemStack t = a2Cargada(com.miscelanea.registry.ModItems.THOMPSON, 50);
            a2Mano(com.miscelanea.armasfuego.oeste.EstucheViolinItem.con(t));
        });
        paso("230_estuche_violin", 6, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        paso("231_estuche_1p", 6, A2::camara1p);
        // agachado + clic derecho lo abre (sin agacharse, el estuche es un violín)
        sin("a2_estuche_agachar", 4, () -> mc.options.keyShift.setDown(true));
        sin("a2_estuche_abrir", 8, piTiro());
        sin("a2_estuche_ok", 2, () -> {
            mc.options.keyShift.setDown(false);
            boolean t = mc.player.getMainHandItem().getItem() instanceof com.miscelanea.armasfuego.oeste.ThompsonItem;
            a2Comprobar("el estuche de violín se abre y la Thompson pasa a la mano", t, mc.player.getMainHandItem().toString());
            piApuntar(a2Punto(8, 0, 1.2));
            a2Vida("t0");
        });
        sin("a2_thompson_rafaga", 2, () -> rifleUsar(true));
        paso("232_thompson_rafaga", 10, () -> piApuntar(a2Punto(8, 0, 1.2)));
        paso("233_thompson_rafaga_3p", 6, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        sin("a2_thompson_suelta", 6, () -> {
            rifleUsar(false);
            a2Vida("t1");
            piSrv(sp -> PI.put("t_balas", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_thompson_ok", 2, () -> a2Comprobar("la Thompson dispara en ráfaga", a2F("t0") - a2F("t1") > 6 && PI.get("t_balas") instanceof Integer b && b <= 44,
                "vida " + a2F("t0") + " → " + a2F("t1") + ", balas " + PI.get("t_balas")));
        sin("a2_thompson_agachar", 4, () -> {
            a2Camara1p();
            mc.options.keyShift.setDown(true);
        });
        sin("a2_thompson_recargar", 2, () -> rifleUsar(true));
        paso("234_thompson_sin_tambor", 12, () -> {});
        sin("a2_thompson_fin", 34, () -> {});
        sin("a2_thompson_recargada", 4, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("t_balas2", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_thompson_rec_ok", 2, () -> a2Comprobar("la Thompson cambia el tambor", Integer.valueOf(com.miscelanea.armasfuego.comun.EquilibrioArmas.thompsonCapacidad()).equals(PI.get("t_balas2")), "balas " + PI.get("t_balas2")));
        // ================================================================ mosquete
        sin("a2_mosquete", 8, () -> {
            a2Mano(a2Cargada(com.miscelanea.registry.ModItems.MOSQUETE, 1));
            piApuntar(a2Punto(8, 0, 1.2));
            a2Vida("m0");
        });
        paso("240_mosquete_1p", 10, () -> {});
        sin("a2_mosquete_gatillo", 1, piTiro());
        paso("241_mosquete_cazoleta", 1, () -> {});
        paso("242_mosquete_humo", 6, () -> {});
        sin("a2_mosquete_ver", 4, () -> {
            a2Vida("m1");
            piSrv(sp -> PI.put("m_balas", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_mosquete_ok", 2, () -> a2Comprobar("el mosquete dispara tras la chispa (o falla en la cazoleta, que también vale)",
                Integer.valueOf(0).equals(PI.get("m_balas")) || Boolean.TRUE.equals(mc.player.getMainHandItem().getTag() != null && mc.player.getMainHandItem().getTag().getBoolean("Atasco")),
                "vida " + a2F("m0") + " → " + a2F("m1") + ", balas " + PI.get("m_balas")));
        sin("a2_mosquete_agachar", 4, () -> mc.options.keyShift.setDown(true));
        sin("a2_mosquete_recargar", 2, () -> rifleUsar(true));
        paso("243_mosquete_baqueta", 30, () -> {});
        sin("a2_mosquete_fin", 46, () -> {});
        sin("a2_mosquete_recargado", 4, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("m_balas2", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2_mosquete_rec_ok", 2, () -> a2Comprobar("el mosquete se recarga con pólvora y bola", Integer.valueOf(1).equals(PI.get("m_balas2")), "balas " + PI.get("m_balas2")));
        // la bayoneta: un golpe cuerpo a cuerpo al husk (se le acerca)
        sin("a2_bayoneta_acercar", 6, () -> piSrv(sp -> {
            Vec3 q = a2Punto(5.8, 0, 0);
            sp.connection.teleport(q.x, q.y, q.z, a2Mira.toYRot(), 0F);
        }));
        sin("a2_bayoneta_vida", 4, () -> {
            piApuntar(a2Punto(8, 0, 1.0));
            a2Vida("b0");
        });
        paso("244_bayoneta", 6, () -> {
            var h = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
            if (h == null) throw new IllegalStateException("no se ve el husk");
            mc.gameMode.attack(mc.player, h);
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        sin("a2_bayoneta_ver", 4, () -> a2Vida("b1"));
        sin("a2_bayoneta_ok", 2, () -> a2Comprobar("la bayoneta pega de lejos (2,2 bloques)", a2F("b0") - a2F("b1") >= 4, "vida " + a2F("b0") + " → " + a2F("b1")));
        paso("245_mosquete_3p_lado", 12, () -> a2Vista(2.6, 0.8, 0.3));
        sin("a2_volver", 6, () -> {
            a2Camara1p();
            piSrv(sp -> sp.connection.teleport(a2O.getX() + 0.5, a2O.getY(), a2O.getZ() + 0.5, a2Mira.toYRot(), 0F));
        });
        // ================================================================ cañón de mano
        sin("a2_canon", 8, () -> {
            a2Mano(a2Cargada(com.miscelanea.registry.ModItems.CANON_MANO, 1));
            piApuntar(a2Punto(8, 0, 1.6));
            a2Vida("c0");
        });
        paso("250_canon_1p", 10, () -> {});
        paso("251_canon_mecha", 14, piTiro());
        paso("252_canon_disparo", 18, () -> piApuntar(a2Punto(8, 0, 1.6)));
        sin("a2_canon_esperar", 10, () -> {});
        sin("a2_canon_ver", 4, () -> {
            a2Vida("c1");
            piSrv(sp -> {
                PI.put("c_balas", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem()));
                PI.put("c_logro", a2Logro(sp, "armas/canon") || a2Logro(sp, "armas/canon_revienta"));
            });
        });
        sin("a2_canon_ok", 2, () -> a2Comprobar("el cañón de mano prende la mecha y dispara (o revienta)", Integer.valueOf(0).equals(PI.get("c_balas")) && Boolean.TRUE.equals(PI.get("c_logro")),
                "vida " + a2F("c0") + " → " + a2F("c1") + ", balas " + PI.get("c_balas") + ", logro " + PI.get("c_logro")));
        paso("253_canon_3p_lado", 12, () -> a2Vista(2.6, 0.8, 0.3));
        // ================================================================ munición nueva de las tres primeras
        sin("a2_tiempo", 8, () -> {
            a2Camara1p();
            ItemStack r = new ItemStack(com.miscelanea.registry.ModItems.REVOLVER.get());
            for (int i = 0; i < 6; i++) com.miscelanea.armasfuego.pistola.Tambor.poner(r, i, com.miscelanea.armasfuego.pistola.Carga.TIEMPO);
            a2Mano(r);
            piApuntar(a2Punto(8, 0, 1.2));
        });
        paso("255_tiempo_bala", 6, piTiro());
        sin("a2_tiempo_ver", 2, () -> piSrv(sp -> {
            var h = a2Ente(sp, a2Husk);
            PI.put("tb", h != null && h.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));
        }));
        sin("a2_tiempo_ok", 2, () -> a2Comprobar("la bala de tiempo bala ralentiza", Boolean.TRUE.equals(PI.get("tb")), String.valueOf(PI.get("tb"))));
        // pintura: el rifle con una bala de pintura azul en la recámara, a la pared de lana
        sin("a2_pintura", 8, () -> {
            ItemStack r = new ItemStack(com.miscelanea.registry.ModItems.RIFLE.get());
            r.getOrCreateTag().putString(com.miscelanea.armasfuego.rifle.Rifle.RECAMARA, "miscelanea:bala_pintura");
            r.getOrCreateTag().putInt(com.miscelanea.armasfuego.rifle.Rifle.RECAMARA_COLOR, 11);
            a2Mano(r);
            piApuntar(a2Punto(8, -6, 1.5));
        });
        sin("a2_pintura_tiro", 10, PruebaCI::rifleClic);
        paso("256_pintura", 4, () -> {});
        sin("a2_pintura_ver", 2, () -> piSrv(sp -> {
            int n = 0;
            for (int k = 2; k <= 6; k++)
                for (int y = 0; y <= 3; y++)
                    if (sp.serverLevel().getBlockState(a2O.relative(a2Mira, k + 4).relative(a2Lado, -6).above(y)).is(net.minecraft.world.level.block.Blocks.BLUE_WOOL)) n++;
            PI.put("pintados", n);
        }));
        sin("a2_pintura_ok", 2, () -> a2Comprobar("la bala de pintura pinta la lana de azul", PI.get("pintados") instanceof Integer n && n >= 3, "bloques pintados " + PI.get("pintados")));
        // goma: la escopeta con cartuchos de goma, contra el suelo delante (rebotan)
        sin("a2_goma", 8, () -> {
            ItemStack e = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get());
            com.miscelanea.armasfuego.escopeta.Presion.ponerTolva(e, com.miscelanea.armasfuego.escopeta.TipoCartucho.GOMA, 8);
            com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(e, 90);
            a2Mano(e);
            piApuntar(a2Punto(4, 0, 0));
        });
        paso("257_goma", 4, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("a2_goma_ver", 4, () -> piSrv(sp -> PI.put("goma", com.miscelanea.armasfuego.escopeta.Presion.tolvaN(sp.getMainHandItem()))));
        sin("a2_goma_ok", 2, () -> a2Comprobar("el cartucho de goma se dispara", Integer.valueOf(7).equals(PI.get("goma")), "quedan " + PI.get("goma")));
        // las recetas de la munición nueva existen (y las armas no se fabrican)
        sin("a2_recetas", 4, () -> piSrv(sp -> {
            java.util.Set<String> armas = java.util.Set.of("gatling", "winchester", "derringer", "thompson", "mosquete", "canon_mano");
            java.util.Set<String> mun = new java.util.HashSet<>();
            StringBuilder mal = new StringBuilder();
            for (var r : sp.server.getRecipeManager().getRecipes()) {
                ItemStack res;
                try {
                    res = r.getResultItem(sp.server.registryAccess());
                } catch (Throwable t) {
                    continue;
                }
                if (res == null || res.isEmpty()) continue;
                var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(res.getItem());
                if (k == null || !k.getNamespace().equals(Miscelanea.MOD_ID)) continue;
                if (armas.contains(k.getPath())) mal.append("receta de ").append(k.getPath()).append("; ");
                mun.add(k.getPath());
            }
            for (String m : List.of("cinta_gatling", "bala_derringer", "cargador_thompson", "bola_mosquete", "revolver_tiempo", "bala_pintura", "escopeta_cartucho_goma", "estuche_violin",
                    "bala_plata", "bala_dinamita", "carta_marcada", "bala_oro", "tambor_trazador", "tambor_agua", "cinta_incendiaria", "cinta_palomitas",
                    "metralla", "bola_cadena", "bolano_incendiario", "bolano_fuegos", "bala_hielo", "bala_humo", "tambor_tinta", "cinta_guisantes",
                    "bola_arpon", "bolano_saltarin"))
                if (!mun.contains(m)) mal.append("sin receta: ").append(m).append("; ");
            PI.put("rec_mal", mal.toString());
        }));
        sin("a2_recetas_ok", 2, () -> a2Comprobar("recetas de la munición nueva (las armas sin receta)", "".equals(PI.get("rec_mal")), String.valueOf(PI.get("rec_mal"))));
        // botín: cada arma nueva sale en su estructura
        sin("a2_botin", 30, () -> piSrv(sp -> {
            StringBuilder sb = new StringBuilder(), mal = new StringBuilder();
            String[][] tablas = {{"chests/desert_pyramid", "gatling"}, {"chests/abandoned_mineshaft", "winchester"}, {"chests/buried_treasure", "derringer"},
                    {"chests/woodland_mansion", "estuche_violin"}, {"chests/stronghold_corridor", "mosquete"}, {"chests/bastion_other", "canon_mano"}};
            long semilla = 0x1873L;
            for (String[] t : tablas) {
                var id = new net.minecraft.resources.ResourceLocation("minecraft", t[0]);
                var tabla = sp.server.getLootData().getLootTable(id);
                int n = 0, total = 200;
                for (int i = 0; i < total; i++) {
                    var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(sp.serverLevel()).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
                    for (ItemStack it : tabla.getRandomItems(params, semilla++)) {
                        var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it.getItem());
                        if (k != null && k.getNamespace().equals(Miscelanea.MOD_ID) && k.getPath().equals(t[1])) n++;
                    }
                }
                double p = com.miscelanea.armasfuego.BotinArmas.probabilidad(id);
                sb.append(String.format(java.util.Locale.ROOT, "%s %d/%d (config %.0f %%) · ", t[0].replace("chests/", ""), n, total, 100 * p));
                double f = (double) n / total;
                if (n == 0 || f < p * 0.4 || f > p * 1.8 + 0.03) mal.append(t[0]).append(" ").append(n).append("; ");
            }
            PI.put("a2_botin", sb.toString());
            PI.put("a2_botin_mal", mal.toString());
        }));
        sin("a2_botin_ok", 2, () -> {
            RESUMEN.add("  armas2 en cofres: " + PI.get("a2_botin"));
            a2Comprobar("cada arma nueva sale en su estructura", "".equals(PI.get("a2_botin_mal")), String.valueOf(PI.get("a2_botin_mal")));
        });
        // las armas nuevas en la mano son 3D y en el inventario, iconos
        sin("a2_modelos", 2, () -> {
            var ir = mc.getItemRenderer();
            StringBuilder mal = new StringBuilder();
            for (var it : List.of(com.miscelanea.registry.ModItems.GATLING, com.miscelanea.registry.ModItems.WINCHESTER, com.miscelanea.registry.ModItems.DERRINGER,
                    com.miscelanea.registry.ModItems.THOMPSON, com.miscelanea.registry.ModItems.MOSQUETE, com.miscelanea.registry.ModItems.CANON_MANO,
                    com.miscelanea.registry.ModItems.ESTUCHE_VIOLIN)) {
                var m = ir.getModel(new ItemStack(it.get()), mc.level, mc.player, 0);
                var mano = m.applyTransform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new com.mojang.blaze3d.vertex.PoseStack(), false);
                var gui = m.applyTransform(net.minecraft.world.item.ItemDisplayContext.GUI, new com.mojang.blaze3d.vertex.PoseStack(), false);
                if (mano == gui || m == ir.getItemModelShaper().getModelManager().getMissingModel()) mal.append(it.getId().getPath()).append("; ");
            }
            for (var r : com.miscelanea.client.armasfuego.comun.GatlingRenderer.TODOS)
                if (mc.getModelManager().getModel(r) == mc.getModelManager().getMissingModel()) mal.append(r).append("; ");
            a2Comprobar("modelos 3D de las armas nuevas y piezas de la Gatling", mal.length() == 0, mal.length() == 0 ? "todos" : mal.toString());
        });
        // ================================================================ cobre de las seis nuevas (como las tres primeras)
        sin("a2c_modelos", 2, () -> {
            var ir = mc.getItemRenderer();
            StringBuilder mal = new StringBuilder();
            for (var it : List.of(com.miscelanea.registry.ModItems.GATLING, com.miscelanea.registry.ModItems.WINCHESTER, com.miscelanea.registry.ModItems.DERRINGER,
                    com.miscelanea.registry.ModItems.THOMPSON, com.miscelanea.registry.ModItems.MOSQUETE, com.miscelanea.registry.ModItems.CANON_MANO)) {
                java.util.Set<Object> vistos = new java.util.HashSet<>();
                for (int o = 0; o < 4; o++) {
                    ItemStack st = new ItemStack(it.get());
                    com.miscelanea.armasfuego.comun.Cobre.ponerOxido(st, o);
                    var m = ir.getModel(st, mc.level, mc.player, 0);
                    if (m == ir.getItemModelShaper().getModelManager().getMissingModel()) mal.append(it.getId().getPath()).append(" fase ").append(o).append(" sin modelo; ");
                    vistos.add(m);
                }
                if (vistos.size() != 4) mal.append(it.getId().getPath()).append(" con ").append(vistos.size()).append(" modelos de cobre; ");
            }
            for (var r : com.miscelanea.client.armasfuego.comun.GatlingRenderer.CUERPOS)
                if (mc.getModelManager().getModel(r) == mc.getModelManager().getMissingModel()) mal.append(r).append("; ");
            a2Comprobar("cuatro fases del cobre (modelos) en las seis armas y en la Gatling plantada", mal.length() == 0, mal.length() == 0 ? "todas" : mal.toString());
        });
        // texturas a 16 px por bloque, como las del revólver y el rifle (iconos, munición y materiales de los modelos)
        sin("a2c_texturas_16", 2, () -> {
            // TODAS las de las armas de fuego y su munición (las nueve y todo lo suyo, también lo que se añada): iconos,
            // materiales de los modelos (armas3d) y la bengala y la cuerda del rifle
            StringBuilder mal = new StringBuilder();
            int n = 0;
            String[] pref = {"revolver", "rifle", "escopeta_presion", "escopeta_cartucho", "bala_", "casquillo_rifle", "gatling", "winchester", "derringer",
                    "thompson", "mosquete", "canon_mano", "estuche_violin", "cinta_", "cargador_thompson", "carta_marcada", "tambor_", "metralla",
                    "bola_mosquete", "bola_cadena", "bola_arpon", "bolano_"};
            var todas = mc.getResourceManager().listResources("textures", rl -> rl.getNamespace().equals(Miscelanea.MOD_ID) && rl.getPath().endsWith(".png"));
            java.util.Set<String> vistas = new java.util.TreeSet<>();
            for (var e : todas.entrySet()) {
                String path = e.getKey().getPath();
                String nombre = path.substring(path.lastIndexOf('/') + 1);
                boolean arma = path.startsWith("textures/item/armas3d/") || path.equals("textures/entity/revolver_bengala.png") || path.equals("textures/entity/cuerda_rifle.png");
                if (!arma && path.startsWith("textures/item/") && path.indexOf('/', "textures/item/".length()) < 0)
                    for (String pr : pref) if (nombre.startsWith(pr)) { arma = true; break; }
                if (!arma) continue;
                vistas.add(nombre);
                try (var in = e.getValue().open(); var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                    if (img.getWidth() != 16 || img.getHeight() != 16) mal.append(path).append(" ").append(img.getWidth()).append("x").append(img.getHeight()).append("; ");
                    n++;
                } catch (Exception ex) {
                    mal.append(path).append(" no se lee; ");
                }
            }
            // que estén las de esta tanda (si no, la lista de arriba se habría quedado corta)
            for (String x : List.of("bala_hielo.png", "bala_humo.png", "tambor_tinta.png", "cinta_guisantes.png", "bola_arpon.png", "bolano_saltarin.png",
                    "g_acero.png", "winchester_ox3.png", "gatling_ox3.png", "revolver_bengala.png"))
                if (!vistas.contains(x)) mal.append("no se ha mirado ").append(x).append("; ");
            a2Comprobar("todas las texturas de las armas de fuego y su munición a 16x16", mal.length() == 0 && n >= 140, mal.length() == 0 ? n + " a 16x16" : mal.toString());
        });
        // los encantamientos propios: valen para su arma y no para una espada
        sin("a2c_encantamientos", 2, () -> {
            StringBuilder mal = new StringBuilder();
            int n = 0;
            for (var e : net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getEntries()) {
                var k = e.getKey().location();
                if (!k.getNamespace().equals(Miscelanea.MOD_ID)) continue;
                String arma = null;
                for (String a : List.of("gatling", "winchester", "derringer", "thompson", "mosquete", "canon_mano"))
                    if (k.getPath().startsWith(a.equals("canon_mano") ? "canon_" : a + "_")) arma = a;
                if (arma == null) continue;
                n++;
                var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, arma));
                if (!e.getValue().canEnchant(new ItemStack(it))) mal.append(k.getPath()).append(" no vale para ").append(arma).append("; ");
                if (e.getValue().canEnchant(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD))) mal.append(k.getPath()).append(" vale para una espada; ");
            }
            a2Comprobar("encantamientos propios de las seis nuevas (" + n + ")", n >= 15 && mal.length() == 0, mal.length() == 0 ? n + " bien" : mal.toString());
        });
        // encerar con un panal en la otra mano, quitar la cera y rascar con un hacha; el nombre cambia con el estado
        sin("a2c_cera_poner", 6, () -> {
            ItemStack w = a2Cargada(com.miscelanea.registry.ModItems.WINCHESTER, 4);
            com.miscelanea.armasfuego.comun.Cobre.ponerOxido(w, 2);
            a2Mano(w);
            piSrv(sp -> sp.getInventory().offhand.set(0, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB, 2)));
            mc.options.keyShift.setDown(true);
        });
        sin("a2c_cera", 6, piTiro());
        paso("260_winchester_encerada", 6, () -> piSrv(sp -> {
            ItemStack w = sp.getMainHandItem();
            PI.put("cera", com.miscelanea.armasfuego.comun.Cobre.encerado(w) + " · " + w.getHoverName().getString());
            sp.getInventory().offhand.set(0, new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
        }));
        sin("a2c_hacha1", 6, piTiro());
        sin("a2c_hacha2", 6, piTiro());
        sin("a2c_cera_ver", 4, () -> {
            mc.options.keyShift.setDown(false);
            piSrv(sp -> {
                ItemStack w = sp.getMainHandItem();
                PI.put("rascado", com.miscelanea.armasfuego.comun.Cobre.encerado(w) + " · óxido " + com.miscelanea.armasfuego.comun.Cobre.oxido(w) + " · " + w.getHoverName().getString());
                sp.getInventory().offhand.set(0, ItemStack.EMPTY);
            });
        });
        sin("a2c_cera_ok", 2, () -> a2Comprobar("panal en la otra mano encera, el hacha quita la cera y luego un paso de óxido (y el nombre lo dice)",
                String.valueOf(PI.get("cera")).startsWith("true") && String.valueOf(PI.get("cera")).contains("erosionada")
                        && String.valueOf(PI.get("rascado")).startsWith("false · óxido 1"), PI.get("cera") + " → " + PI.get("rascado")));
        // cada arma oxidada en primera persona y erosionada en tercera
        var nuevas = List.of(com.miscelanea.registry.ModItems.GATLING, com.miscelanea.registry.ModItems.WINCHESTER, com.miscelanea.registry.ModItems.DERRINGER,
                com.miscelanea.registry.ModItems.THOMPSON, com.miscelanea.registry.ModItems.MOSQUETE, com.miscelanea.registry.ModItems.CANON_MANO);
        for (int i = 0; i < nuevas.size(); i++) {
            final var it = nuevas.get(i);
            String n = it.getId().getPath();
            paso((262 + i * 2) + "_" + n + "_oxidada_1p", 12, () -> {
                a2Camara1p();
                ItemStack st = new ItemStack(it.get());
                com.miscelanea.armasfuego.comun.Cobre.ponerOxido(st, 3);
                a2Mano(st);
                piApuntar(a2Punto(8, 0, 1.2));
            });
            paso((263 + i * 2) + "_" + n + "_erosionada_3p", 14, () -> {
                ItemStack st = new ItemStack(it.get());
                com.miscelanea.armasfuego.comun.Cobre.ponerOxido(st, 2);
                if (st.getItem() instanceof com.miscelanea.armasfuego.oeste.DerringerItem) st.getOrCreateTag().putLong(com.miscelanea.armasfuego.comun.ArmaFuegoItem.DISPARO_T, mc.level.getGameTime() + 400);
                a2Mano(st);
                a2Vista(2.4, 0.6, 0.3);
            });
        }
        // la Gatling plantada y oxidada del todo (y encerada con un panal)
        sin("a2c_gatling", 10, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var l = sp.serverLevel();
                var g = com.miscelanea.registry.ModEntities.GATLING.get().create(l);
                Vec3 q = a2Punto(2.5, 2, 0);
                g.moveTo(q.x, q.y, q.z, a2Mira.toYRot() + 30, 0);
                g.desde(new ItemStack(com.miscelanea.registry.ModItems.GATLING.get()));
                g.ponerOxido(3);
                l.addFreshEntity(g);
                a2Gatling = g.getUUID();
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB));
                g.interact(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
                PI.put("g_cera", com.miscelanea.armasfuego.comun.Cobre.encerado(g.comoObjeto()) + " · óxido " + g.oxido());
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
        });
        paso("274_gatling_oxidada", 14, () -> {
            a2Comprobar("la Gatling plantada se oxida y se encera con un panal", "true · óxido 3".equals(PI.get("g_cera")), String.valueOf(PI.get("g_cera")));
            // de cerca y de tres cuartos, mirando a la Gatling (no al jugador)
            Vec3 g = a2Punto(2.5, 2, 0.55);
            a2VistaA(g, a2Punto(0.6, 4.2, 1.6));
        });
        sin("a2c_gatling_fuera", 4, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var g = sp.serverLevel().getEntity(a2Gatling);
                if (g != null) g.discard();
            });
        });
        // munición especial: plata (x2 al husk, que es no muerto), carta marcada (brilla), agua (apaga) y bolaño de feria
        sin("a2c_plata", 8, () -> {
            ItemStack w = a2Cargada(com.miscelanea.registry.ModItems.WINCHESTER, 3);
            w.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "miscelanea:bala_plata");
            a2Mano(w);
            piApuntar(a2Punto(8, 0, 1.2));
            a2Vida("p0");
        });
        paso("276_bala_plata", 4, piTiro());
        sin("a2c_plata_ver", 4, () -> a2Vida("p1"));
        sin("a2c_plata_ok", 2, () -> a2Comprobar("la bala de plata hace el doble a un no muerto", a2F("p0") - a2F("p1") > com.miscelanea.armasfuego.comun.EquilibrioArmas.winchesterDano() * 1.5F,
                "vida " + a2F("p0") + " → " + a2F("p1")));
        sin("a2c_carta", 8, () -> {
            ItemStack d = a2Cargada(com.miscelanea.registry.ModItems.DERRINGER, 2);
            d.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "miscelanea:carta_marcada");
            a2Mano(d);
            piApuntar(a2Punto(8, 0, 1.2));
        });
        sin("a2c_carta_tiro", 6, piTiro());
        sin("a2c_carta_ver", 2, () -> piSrv(sp -> {
            var h = a2Ente(sp, a2Husk);
            PI.put("carta", h != null && h.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING));
        }));
        sin("a2c_carta_ok", 2, () -> a2Comprobar("la carta marcada hace brillar al que la recibe", Boolean.TRUE.equals(PI.get("carta")), String.valueOf(PI.get("carta"))));
        sin("a2c_agua", 8, () -> {
            ItemStack t = a2Cargada(com.miscelanea.registry.ModItems.THOMPSON, 50);
            t.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "miscelanea:tambor_agua");
            a2Mano(t);
            piApuntar(a2Punto(8, 0, 1.2));
            piSrv(sp -> {
                var h = a2Ente(sp, a2Husk);
                if (h != null) h.setSecondsOnFire(30);
            });
        });
        sin("a2c_agua_rafaga", 2, () -> rifleUsar(true));
        paso("277_tambor_agua", 10, () -> piApuntar(a2Punto(8, 0, 1.2)));
        sin("a2c_agua_ver", 4, () -> {
            rifleUsar(false);
            piSrv(sp -> {
                var h = a2Ente(sp, a2Husk);
                PI.put("agua", h != null && !h.isOnFire());
            });
        });
        sin("a2c_agua_ok", 2, () -> a2Comprobar("el tambor de agua apaga al que arde", Boolean.TRUE.equals(PI.get("agua")), String.valueOf(PI.get("agua"))));
        sin("a2c_feria", 8, () -> {
            ItemStack c = a2Cargada(com.miscelanea.registry.ModItems.CANON_MANO, 1);
            c.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "miscelanea:bolano_fuegos");
            a2Mano(c);
            piApuntar(a2Punto(8, 0, 1.6));
        });
        sin("a2c_feria_mecha", 30, piTiro());
        paso("278_bolano_feria", 10, () -> {});
        sin("a2c_feria_ver", 4, () -> piSrv(sp -> PI.put("feria", a2Logro(sp, "armas/feria"))));
        sin("a2c_feria_ok", 2, () -> a2Comprobar("el bolaño de feria estalla en fuegos artificiales", Boolean.TRUE.equals(PI.get("feria")), String.valueOf(PI.get("feria"))));
        armas2Utilidad();
        armas2Cobre();
        // las cuatro fases de las seis en el inventario (iconos)
        sin("a2c_fases_poner", 6, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var inv = sp.getInventory();
                inv.clearContent();
                int k = 9;
                for (var it : nuevas)
                    for (int o = 0; o < 4; o++) {
                        ItemStack st = new ItemStack(it.get());
                        com.miscelanea.armasfuego.comun.Cobre.ponerOxido(st, o);
                        if (k == 13) k++;
                        if (k < 36) inv.setItem(k++, st);
                        else inv.setItem(k++ - 36, st);
                    }
            });
        });
        paso("279_fases_inventario", 20, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        sin("a2c_fases_fuera", 4, () -> mc.setScreen(null));
        sin("a2_inventario_poner", 6, () -> piSrv(sp -> {
            var inv = sp.getInventory();
            inv.clearContent();
            int k = 0;
            for (var it : List.of(com.miscelanea.registry.ModItems.GATLING, com.miscelanea.registry.ModItems.WINCHESTER, com.miscelanea.registry.ModItems.DERRINGER,
                    com.miscelanea.registry.ModItems.THOMPSON, com.miscelanea.registry.ModItems.ESTUCHE_VIOLIN, com.miscelanea.registry.ModItems.MOSQUETE,
                    com.miscelanea.registry.ModItems.CANON_MANO, com.miscelanea.registry.ModItems.CINTA_GATLING, com.miscelanea.registry.ModItems.BALA_DERRINGER,
                    com.miscelanea.registry.ModItems.CARGADOR_THOMPSON, com.miscelanea.registry.ModItems.BOLA_MOSQUETE, com.miscelanea.registry.ModItems.REVOLVER_TIEMPO))
            {
                inv.setItem(k < 13 ? k : k + 1, new ItemStack(it.get(), it.get().getMaxStackSize() > 1 ? 8 : 1));
                k++;
            }
            for (int c = 0; c < 6; c++) {
                ItemStack p = com.miscelanea.armasfuego.rifle.Pintura.bala(c * 3);
                p.setCount(4);
                inv.setItem(20 + c, p);
            }
            inv.setItem(27, new ItemStack(com.miscelanea.armasfuego.escopeta.TipoCartucho.GOMA.item(), 8));
            inv.selected = 0;
        }));
        paso("258_inventario_armas2", 20, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        sin("a2_fin", 5, () -> {
            mc.setScreen(null);
            a2Camara1p();
            com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = false;
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            RESUMEN.add("  armas2: golpes de retroceso de cámara " + com.miscelanea.client.armasfuego.comun.FuegoCliente.patadas);
            piSrv(sp -> {
                sp.getInventory().clearContent();
                sp.setGameMode(GameType.CREATIVE);
            });
        });
    }

    private static volatile UUID a2Gat2, a2Vehiculo;
    private static volatile net.minecraft.core.BlockPos a2Tolva, a2Piedra;

    /** Apunta a {@code q} en el cliente y en el servidor a la vez (el servidor dispara con su vista). */
    private static void a2Apuntar(Vec3 q) {
        piApuntar(q);
        piSrv(sp -> {
            Vec3 d = q.subtract(sp.getEyePosition());
            float y = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float x = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            sp.setYRot(y);
            sp.setXRot(x);
            sp.setYHeadRot(y);
        });
    }

    /** Lo que pone ahora la barra de acción (encima del inventario), o "" si nada. */
    private static String a2Aviso() {
        var gui = Minecraft.getInstance().gui;
        for (var f : net.minecraft.client.gui.Gui.class.getDeclaredFields()) {
            if (f.getType() != net.minecraft.network.chat.Component.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(gui);
                if (v instanceof net.minecraft.network.chat.Component c && !c.getString().isEmpty()) return c.getString();
            } catch (Exception ignorada) {
            }
        }
        return "";
    }

    /** Las partículas de nota que hay ahora en el cliente (en el motor de partículas, también las que van a entrar). */
    @SuppressWarnings("unchecked")
    private static int a2Notas() {
        var pe = Minecraft.getInstance().particleEngine;
        int n = 0;
        for (var f : pe.getClass().getDeclaredFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(pe);
                java.util.Collection<Object> grupos = v instanceof java.util.Map<?, ?> m ? (java.util.Collection<Object>) m.values()
                        : v instanceof java.util.Collection<?> c ? java.util.List.of((Object) c) : java.util.List.of();
                for (Object g : grupos)
                    if (g instanceof java.util.Collection<?> c) for (Object o : c) if (o instanceof net.minecraft.client.particle.NoteParticle) n++;
            } catch (Exception ignorada) {
            }
        }
        return n;
    }

    /** La arena que hay alrededor (para ver si el tiro ha puesto más). */
    private static int a2Arena(ServerPlayer sp) {
        int n = 0;
        for (var b : net.minecraft.core.BlockPos.betweenClosed(a2O.offset(-12, -1, -12), a2O.offset(12, 6, 12)))
            if (sp.serverLevel().getBlockState(b).is(net.minecraft.world.level.block.Blocks.SAND)) n++;
        return n;
    }

    private static com.miscelanea.armasfuego.gatling.GatlingEntity a2Gat(ServerPlayer sp) {
        return a2Gat2 == null ? null : (com.miscelanea.armasfuego.gatling.GatlingEntity) sp.serverLevel().getEntity(a2Gat2);
    }

    /**
     * Lo que hacen las seis además de disparar (1.1.0): la Gatling con tolva, de torreta con redstone y montada en
     * vagoneta y en barco; el depósito mezclado de la Winchester y su HUD; la Derringer que sale de la manga; el violín;
     * las bayonetas (mesa, inventario, daño y modelo); el HUD de los pasos del mosquete; y el cañón con un yunque y con
     * arena (y sin poder construir, que no coloque).
     */
    private static void armas2Utilidad() {
        Minecraft mc = Minecraft.getInstance();
        // ---------------------------------------------------------------- Gatling: tolva
        sin("a2u_gatling_tolva", 10, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var l = sp.serverLevel();
                sp.getInventory().clearContent();
                var g = com.miscelanea.registry.ModEntities.GATLING.get().create(l);
                Vec3 q = a2Punto(1.5, -2, 0);
                g.moveTo(q.x, q.y, q.z, a2Mira.toYRot(), 0);
                ItemStack it = new ItemStack(com.miscelanea.registry.ModItems.GATLING.get());
                it.enchant(com.miscelanea.registry.ModEnchantments.GATLING_TORRETA.get(), 1);
                g.desde(it);
                g.ponerDueno(sp);
                g.apuntar(a2Mira.toYRot(), 0);
                l.addFreshEntity(g);
                a2Gat2 = g.getUUID();
                // la tolva al lado (a la izquierda) con dos cintas
                var b = g.blockPosition().relative(a2Lado.getOpposite());
                l.setBlock(b, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(), 3);
                if (l.getBlockEntity(b) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h)
                    h.setItem(0, new ItemStack(com.miscelanea.registry.ModItems.CINTA_GATLING.get(), 2));
                a2Tolva = b;
            });
        });
        sin("a2u_tolva_espera", 50, () -> {});
        sin("a2u_tolva_ver", 4, () -> piSrv(sp -> {
            var g = a2Gat(sp);
            int quedan = sp.serverLevel().getBlockEntity(a2Tolva) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h ? h.getItem(0).getCount() : -1;
            PI.put("tolva", (g == null ? "sin gatling" : "balas " + g.balas() + ", recarga " + g.recarga()) + ", cintas en la tolva " + quedan);
            PI.put("tolva_ok", g != null && (g.balas() > 0 || g.recarga() > 0) && quedan == 1);
        }));
        sin("a2u_tolva_ok", 2, () -> a2Comprobar("una tolva al lado le mete una cinta a la Gatling", Boolean.TRUE.equals(PI.get("tolva_ok")), String.valueOf(PI.get("tolva"))));
        // ---------------------------------------------------------------- Gatling: torreta con redstone (con Torreta, busca al husk)
        sin("a2u_redstone", 2, () -> {
            a2Vida("tr0");
            piSrv(sp -> {
                var g = a2Gat(sp);
                a2Piedra = g.blockPosition().relative(a2Lado);
                sp.serverLevel().setBlock(a2Piedra, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            });
        });
        sin("a2u_redstone_espera", 16, () -> {});
        paso("280_gatling_torreta_redstone", 10, () -> a2VistaA(a2Punto(4.5, -1, 0.9), a2Punto(2.2, 2.8, 2.0)));
        sin("a2u_redstone_ver", 2, () -> {
            a2Vida("tr1");
            piSrv(sp -> {
                var g = a2Gat(sp);
                PI.put("tr_gira", g != null && g.girando());
                PI.put("tr_logro", a2Logro(sp, "armas/gatling_torreta"));
            });
        });
        sin("a2u_redstone_ok", 2, () -> a2Comprobar("con redstone y sin nadie, la Gatling gira sola y (con Torreta) le da al husk",
                Boolean.TRUE.equals(PI.get("tr_gira")) && a2F("tr0") - a2F("tr1") > 2 && Boolean.TRUE.equals(PI.get("tr_logro")),
                "gira " + PI.get("tr_gira") + ", el husk pierde " + (a2F("tr0") - a2F("tr1")) + ", logro " + PI.get("tr_logro")));
        sin("a2u_redstone_quitar", 4, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var l = sp.serverLevel();
                l.setBlock(a2Piedra, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(a2Tolva, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                var g = a2Gat(sp);
                if (g != null) g.discard();
                var h = a2Ente(sp, a2Husk);
                if (h != null) h.setHealth(h.getMaxHealth());
            });
        });
        // ---------------------------------------------------------------- Gatling en una vagoneta
        sin("a2u_vagoneta", 10, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            var c = new net.minecraft.world.entity.vehicle.Minecart(l, 0, 0, 0);
            Vec3 q = a2Punto(1, 2, 0);
            c.moveTo(q.x, q.y, q.z, a2Mira.toYRot() - 90F, 0);
            l.addFreshEntity(c);
            a2Vehiculo = c.getUUID();
            ItemStack gi = new ItemStack(com.miscelanea.registry.ModItems.GATLING.get());
            gi.getOrCreateTag().putInt(com.miscelanea.armasfuego.gatling.GatlingItem.BALAS, 40);
            gi.getOrCreateTag().putString(com.miscelanea.armasfuego.gatling.GatlingItem.CINTA, "miscelanea:cinta_gatling");
            var g = com.miscelanea.armasfuego.comun.ArmasFuegoEventos.montar(sp, gi, c);
            a2Gat2 = g.getUUID();
        }));
        sin("a2u_vagoneta_subir", 10, () -> piSrv(sp -> {
            var g = a2Gat(sp);
            PI.put("vag_montada", g != null && g.getVehicle() instanceof net.minecraft.world.entity.vehicle.AbstractMinecart);
            if (g != null) sp.startRiding(g, true);
        }));
        sin("a2u_vagoneta_apuntar", 4, () -> {
            a2Vida("vg0");
            a2Apuntar(a2Punto(8, 0, 1.2));
            com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = true;
        });
        sin("a2u_vagoneta_tiros", 10, () -> a2Apuntar(a2Punto(8, 0, 1.2)));
        paso("281_gatling_vagoneta", 8, () -> a2Vista(2.8, 1.0, 0.6));
        sin("a2u_vagoneta_ver", 2, () -> {
            com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = false;
            a2Vida("vg1");
            piSrv(sp -> PI.put("vag_logro", a2Logro(sp, "armas/gatling_vagoneta")));
        });
        sin("a2u_vagoneta_ok", 2, () -> a2Comprobar("la Gatling se monta en una vagoneta y se dispara desde ella",
                Boolean.TRUE.equals(PI.get("vag_montada")) && a2F("vg0") - a2F("vg1") > 2 && Boolean.TRUE.equals(PI.get("vag_logro")),
                "montada " + PI.get("vag_montada") + ", el husk pierde " + (a2F("vg0") - a2F("vg1")) + ", logro " + PI.get("vag_logro")));
        sin("a2u_vagoneta_bajar", 6, () -> {
            a2Camara1p();
            piSrv(sp -> {
                sp.stopRiding();
                var g = a2Gat(sp);
                if (g != null) g.discard();
                var c = sp.serverLevel().getEntity(a2Vehiculo);
                if (c != null) c.discard();
                var h = a2Ente(sp, a2Husk);
                if (h != null) h.setHealth(h.getMaxHealth());
                sp.connection.teleport(a2O.getX() + 0.5, a2O.getY(), a2O.getZ() + 0.5, a2Mira.toYRot(), 0F);
            });
        });
        // ---------------------------------------------------------------- Gatling en un barco
        sin("a2u_barco", 10, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            var b = new net.minecraft.world.entity.vehicle.Boat(l, 0, 0, 0);
            Vec3 q = a2Punto(1, 2, 0);
            b.moveTo(q.x, q.y, q.z, a2Mira.toYRot(), 0);
            l.addFreshEntity(b);
            a2Vehiculo = b.getUUID();
            ItemStack gi = new ItemStack(com.miscelanea.registry.ModItems.GATLING.get());
            gi.getOrCreateTag().putInt(com.miscelanea.armasfuego.gatling.GatlingItem.BALAS, 40);
            gi.getOrCreateTag().putString(com.miscelanea.armasfuego.gatling.GatlingItem.CINTA, "miscelanea:cinta_gatling");
            var g = com.miscelanea.armasfuego.comun.ArmasFuegoEventos.montar(sp, gi, b);
            a2Gat2 = g.getUUID();
        }));
        // el que lleva el barco se sube después: la Gatling se pasa al asiento de atrás
        sin("a2u_barco_subir", 24, () -> piSrv(sp -> sp.startRiding(sp.serverLevel().getEntity(a2Vehiculo), true)));
        sin("a2u_barco_apuntar", 4, () -> {
            a2Vida("bc0");
            piSrv(sp -> {
                var b = sp.serverLevel().getEntity(a2Vehiculo);
                PI.put("barco_orden", b == null ? "sin barco" : b.getPassengers().stream().map(e -> e.getType().toShortString()).toList().toString());
                PI.put("barco_ok", b != null && b.getPassengers().size() == 2 && b.getPassengers().get(0) == sp);
            });
            a2Apuntar(a2Punto(8, 0, 1.2));
            com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = true;
        });
        sin("a2u_barco_tiros", 10, () -> a2Apuntar(a2Punto(8, 0, 1.2)));
        paso("282_gatling_barco", 8, () -> a2Vista(2.8, 1.2, 0.8));
        sin("a2u_barco_ver", 2, () -> {
            com.miscelanea.client.armasfuego.comun.FuegoCliente.manivelaPrueba = false;
            a2Vida("bc1");
            piSrv(sp -> PI.put("barco_logro", a2Logro(sp, "armas/gatling_barco")));
        });
        sin("a2u_barco_ok", 2, () -> a2Comprobar("la Gatling va en el barco (atrás) y la dispara el que lo lleva",
                Boolean.TRUE.equals(PI.get("barco_ok")) && a2F("bc0") - a2F("bc1") > 2 && Boolean.TRUE.equals(PI.get("barco_logro")),
                "pasajeros " + PI.get("barco_orden") + ", el husk pierde " + (a2F("bc0") - a2F("bc1")) + ", logro " + PI.get("barco_logro")));
        sin("a2u_barco_bajar", 6, () -> {
            a2Camara1p();
            piSrv(sp -> {
                sp.stopRiding();
                var g = a2Gat(sp);
                if (g != null) g.discard();
                var c = sp.serverLevel().getEntity(a2Vehiculo);
                if (c != null) c.discard();
                var h = a2Ente(sp, a2Husk);
                if (h != null) {
                    h.setHealth(h.getMaxHealth());
                    h.removeAllEffects();
                }
                sp.connection.teleport(a2O.getX() + 0.5, a2O.getY(), a2O.getZ() + 0.5, a2Mira.toYRot(), 0F);
            });
        });
        // ---------------------------------------------------------------- Winchester: depósito mezclado (la última que entra sale primero)
        sin("a2u_winchester", 8, () -> {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
            a2Mano(new ItemStack(com.miscelanea.registry.ModItems.WINCHESTER.get()));
            piSrv(sp -> {
                var inv = sp.getInventory();
                inv.setItem(9, new ItemStack(com.miscelanea.registry.ModItems.REVOLVER_BALA.get()));
                inv.setItem(10, new ItemStack(com.miscelanea.registry.ModItems.BALA_HIELO.get()));
                inv.setItem(11, new ItemStack(com.miscelanea.registry.ModItems.REVOLVER_DARDO.get()));
                inv.setItem(12, new ItemStack(com.miscelanea.registry.ModItems.BALA_PLATA.get()));
                inv.setItem(14, new ItemStack(com.miscelanea.registry.ModItems.REVOLVER_TIEMPO.get()));
            });
            mc.options.keyShift.setDown(true);
        });
        sin("a2u_winchester_cargar", 2, () -> rifleUsar(true));
        sin("a2u_winchester_cargando", 56, () -> {});
        sin("a2u_winchester_cargada", 4, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> {
                PI.put("dep", com.miscelanea.armasfuego.oeste.WinchesterItem.deposito(sp.getMainHandItem()).stream()
                        .map(i -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i).getPath()).toList().toString());
                PI.put("dep_logro", a2Logro(sp, "armas/winchester_mezcla"));
            });
        });
        sin("a2u_winchester_dep_ok", 2, () -> {
            // y al acabar la carga (con el botón aún pulsado) no sale ningún «No llevas...» en la barra de acción
            String aviso = a2Aviso();
            a2Comprobar("la Winchester mezcla las balas en el depósito (y al acabar no avisa de munición)",
                    "[revolver_bala, bala_hielo, revolver_dardo, bala_plata, revolver_tiempo]".equals(PI.get("dep")) && Boolean.TRUE.equals(PI.get("dep_logro"))
                            && !aviso.startsWith("No llevas") && !aviso.startsWith("No te queda"),
                    "depósito " + PI.get("dep") + ", logro " + PI.get("dep_logro") + ", aviso «" + aviso + "»");
        });
        paso("283_winchester_hud", 6, () -> a2Apuntar(a2Punto(8, 0, 1.2)));
        sin("a2u_winchester_tiro", 6, piTiro());
        sin("a2u_winchester_tiro_ver", 2, () -> piSrv(sp -> {
            var h = a2Ente(sp, a2Husk);
            var dep = com.miscelanea.armasfuego.oeste.WinchesterItem.deposito(sp.getMainHandItem());
            PI.put("dep2", dep.size() + ", la siguiente " + (dep.isEmpty() ? "-" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(dep.get(dep.size() - 1)).getPath()));
            var ef = h == null ? null : h.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            PI.put("dep2_ok", dep.size() == 4 && dep.get(3) == com.miscelanea.registry.ModItems.BALA_PLATA.get() && ef != null && ef.getAmplifier() == 3);
        }));
        sin("a2u_winchester_tiro_ok", 2, () -> a2Comprobar("sale primero la última que entró (la de tiempo bala: ralentiza al husk)", Boolean.TRUE.equals(PI.get("dep2_ok")),
                "quedan " + PI.get("dep2")));
        // ---------------------------------------------------------------- Derringer: de la manga a la mano
        sin("a2u_manga", 8, () -> {
            a2Camara1p();
            piSrv(sp -> {
                var inv = sp.getInventory();
                inv.clearContent();
                inv.setItem(0, new ItemStack(net.minecraft.world.item.Items.STICK));
                inv.setItem(22, a2Cargada(com.miscelanea.registry.ModItems.DERRINGER, 2));
                inv.selected = 0;
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                var h = a2Ente(sp, a2Husk);
                if (h != null) h.removeAllEffects();
            });
            a2Apuntar(a2Punto(8, 0, 1.2));
        });
        // sacarla y el tiro seguidos (los paquetes llegan en orden: primero sale de la manga y luego dispara); con
        // shaders, a pocos fps, unos pocos pasos ya eran más de un segundo del servidor
        sin("a2u_manga_tiro", 4, () -> {
            a2Apuntar(a2Punto(8, 0, 1.2));
            com.miscelanea.client.armasfuego.comun.FuegoCliente.sacarDeLaManga();
            piDisparar();
        });
        sin("a2u_manga_mano", 2, () -> piSrv(sp -> PI.put("manga", sp.getMainHandItem().getItem() instanceof com.miscelanea.armasfuego.oeste.DerringerItem
                && sp.getInventory().getItem(22).is(net.minecraft.world.item.Items.STICK))));
        paso("284_derringer_manga", 3, () -> {});
        sin("a2u_manga_ver", 2, () -> piSrv(sp -> PI.put("manga_logro", a2Logro(sp, "armas/derringer_manga"))));
        sin("a2u_manga_ok", 2, () -> a2Comprobar("la tecla de la manga saca la Derringer de golpe (y el tiro en menos de un segundo da el logro)",
                Boolean.TRUE.equals(PI.get("manga")) && Boolean.TRUE.equals(PI.get("manga_logro")), "en la mano " + PI.get("manga") + ", logro " + PI.get("manga_logro")));
        // ---------------------------------------------------------------- el violín (con el arco en la otra mano)
        sin("a2u_violin", 6, () -> {
            a2Mano(new ItemStack(com.miscelanea.registry.ModItems.ESTUCHE_VIOLIN.get()));
            piSrv(sp -> sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY));
        });
        // sin arco: clic derecho no toca (abre y cierra el estuche)
        sin("a2u_violin_sin_arco", 6, () -> {
            PI.put("v_sin0", com.miscelanea.armasfuego.oeste.EstucheViolinItem.tocadas);
            piDisparar();
        });
        sin("a2u_violin_arco", 4, () -> {
            PI.put("v_sin1", com.miscelanea.armasfuego.oeste.EstucheViolinItem.tocadas);
            piSrv(sp -> sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.BOW)));
            PI.put("v0", com.miscelanea.armasfuego.oeste.EstucheViolinItem.tocadas);
        });
        // en primera persona, mirando al frente, frotando (clic mantenido): la notita sale delante y el HUD enseña la nota
        sin("a2u_violin_1p", 2, () -> {
            a2Camara1p();
            piSrv(sp -> sp.setXRot(0F));
            mc.player.setXRot(0F);
            PI.remove("v_notas_max");
            a2VigilaNotas = true;
            rifleUsar(true);
        });
        sin("a2u_violin_1p_sostiene", 30, () -> {});
        paso("285_violin_1p", 2, () -> {});
        sin("a2u_violin_arriba", 4, () -> {
            PI.put("v_media", com.miscelanea.armasfuego.oeste.EstucheViolinItem.ultimaNota);
            PI.put("v_sostenidas", com.miscelanea.armasfuego.oeste.EstucheViolinItem.tocadas - (PI.get("v0") instanceof Integer i ? i : 0));
            // las notitas que han llegado a verse en el cliente (se miran en cada tick: con shaders, a pocos fps, podían
            // haber salido y acabado entre paso y paso)
            PI.put("v_notas1p", PI.get("v_notas_max") instanceof Integer i ? i : 0);
            // mirando arriba sin soltar: la nota cambia sola (legato)
            piSrv(sp -> sp.setXRot(-70F));
            mc.player.setXRot(-70F);
        });
        sin("a2u_violin_arriba_ver", 6, () -> PI.put("v_alta", com.miscelanea.armasfuego.oeste.EstucheViolinItem.ultimaNota));
        // en tercera persona (de frente), mirando abajo: grave, y la notita encima de la cabeza
        sin("a2u_violin_3p", 2, () -> {
            PI.remove("v_notas_max");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            piSrv(sp -> sp.setXRot(40F));
            mc.player.setXRot(40F);
        });
        sin("a2u_violin_3p_espera", 4, () -> {});
        paso("285_violin", 2, () -> {});
        sin("a2u_violin_ver", 4, () -> {
            a2VigilaNotas = false;
            rifleUsar(false);
            PI.put("v_notas3p", PI.get("v_notas_max") instanceof Integer i ? i : 0);
            PI.put("v_baja", com.miscelanea.armasfuego.oeste.EstucheViolinItem.ultimaNota);
        });
        sin("a2u_violin_ok", 2, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            int sinArco = (PI.get("v_sin1") instanceof Integer i ? i : 0) - (PI.get("v_sin0") instanceof Integer i ? i : 0);
            int sost = PI.get("v_sostenidas") instanceof Integer i ? i : -1;
            int media = PI.get("v_media") instanceof Integer i ? i : -1, alta = PI.get("v_alta") instanceof Integer i ? i : -1, baja = PI.get("v_baja") instanceof Integer i ? i : -1;
            int p1 = PI.get("v_notas1p") instanceof Integer i ? i : -1, p3 = PI.get("v_notas3p") instanceof Integer i ? i : -1;
            a2Comprobar("con el arco, mantener clic derecho toca sostenido (la nota según la vista, cambia sin soltar) y salen las notitas; sin arco no toca",
                    sinArco == 0 && sost >= 2 && media == 12 && alta >= 18 && baja <= 8 && baja >= 0 && p1 > 0 && p3 > 0
                            && "F♯4".equals(com.miscelanea.armasfuego.oeste.EstucheViolinItem.nombreNota(12)) && "C4".equals(com.miscelanea.armasfuego.oeste.EstucheViolinItem.nombreNota(6)),
                    "sin arco " + sinArco + ", sostenida " + sost + " veces, al frente " + media + " (" + com.miscelanea.armasfuego.oeste.EstucheViolinItem.nombreNota(Math.max(0, media)) + "), arriba " + alta
                            + ", abajo " + baja + ", partículas en 1.ª " + p1 + " y en 3.ª " + p3);
            piSrv(sp -> sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY));
        });
        // ---------------------------------------------------------------- bayonetas: mesa, inventario, daño, modelo
        sin("a2u_bayoneta", 4, () -> piSrv(sp -> {
            StringBuilder mal = new StringBuilder();
            var l = sp.serverLevel();
            // mesa: mosquete sin bayoneta + espada de diamante
            ItemStack sin = new ItemStack(com.miscelanea.registry.ModItems.MOSQUETE.get());
            com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(sin, ItemStack.EMPTY);
            var mesa = new net.minecraft.world.inventory.TransientCraftingContainer(sp.inventoryMenu, 3, 3);
            mesa.setItem(0, sin.copy());
            mesa.setItem(4, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            var rec = l.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, mesa, l);
            ItemStack con = rec.map(r -> r.assemble(mesa, l.registryAccess())).orElse(ItemStack.EMPTY);
            if (com.miscelanea.armasfuego.oeste.MosqueteItem.material(con) != 5) mal.append("la mesa no cala la de diamante; ");
            // mesa: el mosquete solo, se quita (y la espada se queda en la mesa)
            var mesa2 = new net.minecraft.world.inventory.TransientCraftingContainer(sp.inventoryMenu, 3, 3);
            mesa2.setItem(2, con.copy());
            var rec2 = l.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, mesa2, l);
            ItemStack fuera = rec2.map(r -> r.assemble(mesa2, l.registryAccess())).orElse(ItemStack.EMPTY);
            ItemStack queda = rec2.map(r -> r.getRemainingItems(mesa2).get(2)).orElse(ItemStack.EMPTY);
            if (!fuera.is(com.miscelanea.registry.ModItems.MOSQUETE.get()) || com.miscelanea.armasfuego.oeste.MosqueteItem.tieneBayoneta(fuera) || !queda.is(net.minecraft.world.item.Items.DIAMOND_SWORD))
                mal.append("la mesa no quita la bayoneta (").append(fuera).append(", queda ").append(queda).append("); ");
            // inventario, como un saco: la de netherita encima; con la mano vacía, fuera
            ItemStack m = sin.copy();
            ItemStack[] cursor = {new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD)};
            net.minecraft.world.entity.SlotAccess acc = new net.minecraft.world.entity.SlotAccess() {
                public ItemStack get() { return cursor[0]; }
                public boolean set(ItemStack x) { cursor[0] = x; return true; }
            };
            var slot = sp.inventoryMenu.getSlot(9);
            m.getItem().overrideOtherStackedOnMe(m, cursor[0], slot, net.minecraft.world.inventory.ClickAction.SECONDARY, sp, acc);
            if (com.miscelanea.armasfuego.oeste.MosqueteItem.material(m) != 6 || !cursor[0].isEmpty()) mal.append("el inventario no cala la de netherita; ");
            double dN = com.miscelanea.armasfuego.oeste.MosqueteItem.danoEspada(com.miscelanea.armasfuego.oeste.MosqueteItem.bayoneta(m));
            m.getItem().overrideOtherStackedOnMe(m, ItemStack.EMPTY, slot, net.minecraft.world.inventory.ClickAction.SECONDARY, sp, acc);
            if (com.miscelanea.armasfuego.oeste.MosqueteItem.tieneBayoneta(m) || !cursor[0].is(net.minecraft.world.item.Items.NETHERITE_SWORD)) mal.append("el inventario no la quita; ");
            // el daño va con el material
            ItemStack madera = sin.copy();
            com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(madera, new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD));
            double dM = 0, dD = 0;
            for (var mod : madera.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) dM += mod.getAmount();
            for (var mod : con.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) dD += mod.getAmount();
            if (!(dD > dM + 2)) mal.append("el daño no va con la espada (madera ").append(dM).append(", diamante ").append(dD).append("); ");
            PI.put("bay", mal.toString());
            PI.put("bay_txt", "madera " + dM + ", diamante " + dD + ", netherita (espada) " + dN);
            PI.put("bay_logro", a2Logro(sp, "armas/bayoneta_netherita"));
            // cuatro marcos en una pared con un mosquete cada uno (madera, oro, diamante, netherita): se ven de perfil
            net.minecraft.world.item.Item[] esp = {net.minecraft.world.item.Items.WOODEN_SWORD, net.minecraft.world.item.Items.GOLDEN_SWORD,
                    net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.NETHERITE_SWORD};
            for (int k = 0; k < 4; k++) {
                var pared = a2O.relative(a2Mira, 4).relative(a2Lado, k - 2).above(1);
                l.setBlock(pared, net.minecraft.world.level.block.Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                var hueco = pared.relative(a2Mira.getOpposite());
                var marco = new net.minecraft.world.entity.decoration.GlowItemFrame(l, hueco, a2Mira.getOpposite());
                ItemStack mq = new ItemStack(com.miscelanea.registry.ModItems.MOSQUETE.get());
                com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(mq, new ItemStack(esp[k]));
                marco.setItem(mq, false);
                marco.setRotation(1);   // en diagonal: cabe entero y se ve la hoja
                marco.addTag("a2u_bayoneta");
                l.addFreshEntity(marco);
            }
        }));
        paso("286_bayonetas", 10, () -> a2VistaA(a2Punto(5, -0.6, 1.2), a2Punto(1.2, -0.6, 1.8)));
        sin("a2u_bayoneta_ok", 2, () -> {
            a2Camara1p();
            a2Comprobar("bayoneta cambiable: en la mesa y en el inventario se cala y se quita, y el daño va con la espada",
                    "".equals(PI.get("bay")) && Boolean.TRUE.equals(PI.get("bay_logro")), PI.get("bay") + " " + PI.get("bay_txt") + ", logro " + PI.get("bay_logro"));
            piSrv(sp -> {
                sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, new net.minecraft.world.phys.AABB(a2O).inflate(12),
                        e -> e.getTags().contains("a2u_bayoneta")).forEach(net.minecraft.world.entity.Entity::discard);
                for (int k = 0; k < 4; k++)
                    sp.serverLevel().setBlock(a2O.relative(a2Mira, 4).relative(a2Lado, k - 2).above(1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            });
        });
        // ---------------------------------------------------------------- mosquete: HUD de los pasos de la recarga
        sin("a2u_mosquete", 6, () -> {
            a2Mano(new ItemStack(com.miscelanea.registry.ModItems.MOSQUETE.get()));
            piSrv(sp -> {
                sp.getInventory().setItem(9, new ItemStack(com.miscelanea.registry.ModItems.BOLA_MOSQUETE.get(), 4));
                sp.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.GUNPOWDER, 8));
            });
            mc.options.keyShift.setDown(true);
        });
        sin("a2u_mosquete_cargar", 2, () -> rifleUsar(true));
        paso("287_mosquete_hud", 30, () -> {});
        sin("a2u_mosquete_fin", 50, () -> {});
        sin("a2u_mosquete_ver", 2, () -> {
            rifleUsar(false);
            mc.options.keyShift.setDown(false);
            piSrv(sp -> PI.put("mq", com.miscelanea.armasfuego.comun.ArmaFuegoItem.balas(sp.getMainHandItem())));
        });
        sin("a2u_mosquete_ok", 2, () -> a2Comprobar("el mosquete se recarga por pasos (pólvora, bola y baqueta, en el HUD)",
                Integer.valueOf(1).equals(PI.get("mq")) && com.miscelanea.armasfuego.oeste.MosqueteItem.pasoRecarga(0.1F) == 0
                        && com.miscelanea.armasfuego.oeste.MosqueteItem.pasoRecarga(0.5F) == 1 && com.miscelanea.armasfuego.oeste.MosqueteItem.pasoRecarga(0.9F) == 2,
                "balas " + PI.get("mq")));
        // ---------------------------------------------------------------- cañón: un yunque en el aire (en su parábola)
        // tiro alto por encima del husk; cuando el yunque lleva unos ticks volando, se para en el aire para la captura
        // (con shaders, a pocos fps, no se podía acertar el momento) y luego sigue su camino
        sin("a2u_arco", 6, () -> {
            ItemStack c = new ItemStack(com.miscelanea.registry.ModItems.CANON_MANO.get());
            com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(c, 1);
            c.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "minecraft:anvil");
            a2Mano(c);
            PI.remove("arco_pos");
            a2CongelaYunque = true;
            a2Apuntar(a2Punto(8, 0, 9));
        });
        sin("a2u_arco_mecha", 2, piTiro());
        sin("a2u_arco_vuela", 50, () -> a2Apuntar(a2Punto(8, 0, 9)));
        paso("288_canon_yunque", 6, () -> {
            if (!(PI.get("arco_pos") instanceof Vec3 q)) throw new IllegalStateException("el yunque no ha salido del cañón");
            Vec3 lado = new Vec3(a2Lado.getStepX(), 0, a2Lado.getStepZ()), fr = new Vec3(a2Mira.getStepX(), 0, a2Mira.getStepZ());
            a2VistaA(q, q.add(lado.scale(1.9)).add(fr.scale(-1.0)).add(0, -0.3, 0));
        });
        sin("a2u_arco_sigue", 40, () -> {
            a2CongelaYunque = false;
            a2Camara1p();
            piSrv(sp -> {
                for (var b : sp.serverLevel().getEntitiesOfClass(com.miscelanea.armasfuego.oeste.BolaCanonEntity.class, new net.minecraft.world.phys.AABB(a2O).inflate(40))) {
                    b.setNoGravity(false);
                    if (PI.get("arco_vel") instanceof Vec3 v) b.setDeltaMovement(v);
                }
            });
        });
        sin("a2u_arco_limpiar", 2, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            for (var b : net.minecraft.core.BlockPos.betweenClosed(a2O.offset(-20, -1, -20), a2O.offset(20, 8, 20)))
                if (l.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            var h = a2Ente(sp, a2Husk);
            if (h != null) h.setHealth(h.getMaxHealth());
        }));
        // ---------------------------------------------------------------- cañón: un yunque (daño de yunque) y arena sin poder construir
        sin("a2u_yunque", 6, () -> {
            ItemStack c = new ItemStack(com.miscelanea.registry.ModItems.CANON_MANO.get());
            com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(c, 1);
            c.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "minecraft:anvil");
            a2Mano(c);
            a2Vida("yq0");
            a2Apuntar(a2Punto(8, 0, 1.3));
        });
        sin("a2u_yunque_mecha", 2, piTiro());
        sin("a2u_yunque_vuela", 30, () -> a2Apuntar(a2Punto(8, 0, 1.3)));
        sin("a2u_yunque_espera", 20, () -> {});
        sin("a2u_yunque_ver", 2, () -> {
            a2Vida("yq1");
            piSrv(sp -> {
                int yunques = 0;
                var l = sp.serverLevel();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(a2O.offset(-12, -1, -12), a2O.offset(12, 6, 12)))
                    if (l.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) {
                        yunques++;
                        l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                PI.put("yq_bloques", yunques);
                PI.put("yq_logro", a2Logro(sp, "armas/yunque") && a2Logro(sp, "armas/castillo_arena"));
            });
        });
        sin("a2u_yunque_ok", 2, () -> a2Comprobar("el cañón dispara un yunque: daño de yunque y se queda donde da", a2F("yq0") - a2F("yq1") > 5 && PI.get("yq_bloques") instanceof Integer n && n >= 1
                && Boolean.TRUE.equals(PI.get("yq_logro")), "el husk pierde " + (a2F("yq0") - a2F("yq1")) + ", yunques colocados " + PI.get("yq_bloques") + ", logros " + PI.get("yq_logro")));
        // sin poder construir (modo aventura): la arena no se coloca, cae como objeto
        sin("a2u_arena", 6, () -> {
            a2Camara1p();
            piSrv(sp -> {
                sp.setGameMode(GameType.ADVENTURE);
                var h = a2Ente(sp, a2Husk);
                if (h != null) h.setHealth(h.getMaxHealth());
            });
            ItemStack c = new ItemStack(com.miscelanea.registry.ModItems.CANON_MANO.get());
            com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(c, 1);
            c.getOrCreateTag().putString(com.miscelanea.armasfuego.comun.ArmaFuegoItem.TIPO, "minecraft:sand");
            a2Mano(c);
            a2Apuntar(a2Punto(6, -3, 0));
            piSrv(sp -> PI.put("arena0", a2Arena(sp)));
        });
        sin("a2u_arena_mecha", 2, piTiro());
        sin("a2u_arena_vuela", 60, () -> a2Apuntar(a2Punto(6, -3, 0)));
        sin("a2u_arena_ver", 2, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            int arena = a2Arena(sp) - (PI.get("arena0") instanceof Integer i ? i : 0);
            int objetos = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(a2O).inflate(14), e -> e.getItem().is(net.minecraft.world.item.Items.SAND)).size();
            PI.put("arena", arena);
            PI.put("arena_obj", objetos);
            sp.setGameMode(GameType.SURVIVAL);
        }));
        sin("a2u_arena_ok", 2, () -> a2Comprobar("sin poder construir, el bloque disparado no se coloca (cae como objeto)",
                Integer.valueOf(0).equals(PI.get("arena")) && PI.get("arena_obj") instanceof Integer n && n >= 1, "arena colocada " + PI.get("arena") + ", como objeto " + PI.get("arena_obj")));
    }

    private static volatile UUID a2Diana;

    /**
     * El cobre como intercambio (la oxidada pega más que la nueva, y lo dice el tooltip), la cera que se gasta (y se
     * rellena; la Gatling dura más), la bayoneta con los encantamientos de su espada (Filo, Aspecto ígneo, Botín,
     * Reparación) y el logro de encerar cinco veces.
     */
    private static void armas2Cobre() {
        Minecraft mc = Minecraft.getInstance();
        // ---------------------------------------------------------------- la oxidada pega más que la nueva
        sin("a2o_nueva", 6, () -> {
            a2Camara1p();
            a2Mano(a2Cargada(com.miscelanea.registry.ModItems.WINCHESTER, 3));
            piSrv(sp -> {
                var h = a2Ente(sp, a2Husk);
                if (h != null) {
                    h.setHealth(h.getMaxHealth());
                    h.removeAllEffects();
                }
            });
            a2Apuntar(a2Punto(8, 0, 1.2));
        });
        sin("a2o_nueva_vida", 2, () -> a2Vida("o0"));
        sin("a2o_nueva_tiro", 6, () -> {
            a2Apuntar(a2Punto(8, 0, 1.2));
            piDisparar();
        });
        sin("a2o_nueva_ver", 2, () -> a2Vida("o1"));
        sin("a2o_oxidada", 20, () -> {
            ItemStack w = a2Cargada(com.miscelanea.registry.ModItems.WINCHESTER, 3);
            com.miscelanea.armasfuego.comun.Cobre.ponerOxido(w, 3);
            a2Mano(w);
            a2Apuntar(a2Punto(8, 0, 1.2));
        });
        sin("a2o_oxidada_tiro", 6, () -> {
            a2Apuntar(a2Punto(8, 0, 1.2));
            piDisparar();
        });
        // la oxidada dispersa más: si el tiro no ha dado, otro (le quedan balas)
        sin("a2o_oxidada_otra", 2, () -> a2Vida("o2"));
        sin("a2o_oxidada_tiro2", 20, () -> {
            if (a2F("o1") - a2F("o2") <= 0) {
                RESUMEN.add("  armas2: el tiro de la Winchester oxidada no ha dado (dispersa más); otro");
                a2Apuntar(a2Punto(8, 0, 1.2));
                piDisparar();
            }
        });
        sin("a2o_oxidada_ver", 2, () -> {
            a2Vida("o3");
            piSrv(sp -> {
                List<net.minecraft.network.chat.Component> l = new java.util.ArrayList<>();
                com.miscelanea.armasfuego.comun.Cobre.tooltip(sp.getMainHandItem(), l);
                PI.put("o_tip", l.isEmpty() ? "" : l.get(0).getString());
            });
        });
        sin("a2o_ok", 2, () -> {
            float dn = a2F("o0") - a2F("o1"), dx = a2F("o1") - a2F("o3");
            if (a2F("o1") - a2F("o2") <= 0 && dx > 0) dx = a2F("o2") - a2F("o3");
            String tip = String.valueOf(PI.get("o_tip"));
            a2Comprobar("el cobre es un intercambio: la oxidada pega más que la nueva (y se encasquilla y dispersa más), y el tooltip lo dice",
                    dn > 0 && dx > dn * 1.08F && com.miscelanea.armasfuego.comun.Cobre.atasco(3) > com.miscelanea.armasfuego.comun.Cobre.atasco(0) && com.miscelanea.armasfuego.comun.Cobre.dispersion(3) > com.miscelanea.armasfuego.comun.Cobre.dispersion(0)
                            && com.miscelanea.armasfuego.comun.Cobre.perforacion(3) > 0 && tip.contains("+15"),
                    String.format("nueva %.2f, oxidada %.2f; tooltip «%s»", dn, dx, tip));
        });
        // ---------------------------------------------------------------- la cera se gasta
        sin("a2o_cera", 6, () -> {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
            piSrv(sp -> {
                ItemStack w = a2Cargada(com.miscelanea.registry.ModItems.WINCHESTER, 6);
                com.miscelanea.armasfuego.comun.Cobre.encerar(sp, w, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB), null);
                PI.put("cera0", com.miscelanea.armasfuego.comun.Cobre.cera(w, com.miscelanea.armasfuego.comun.Cobre.duracionCera(w)));
                sp.getInventory().setItem(0, w);
                // la Gatling (y la Thompson) duran más
                ItemStack g = new ItemStack(com.miscelanea.registry.ModItems.GATLING.get());
                com.miscelanea.armasfuego.comun.Cobre.encerar(sp, g, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB), null);
                PI.put("cera_g", com.miscelanea.armasfuego.comun.Cobre.cera(g, com.miscelanea.armasfuego.comun.Cobre.duracionCera(g)));
            });
            a2Apuntar(a2Punto(8, 0, 1.2));
        });
        sin("a2o_cera_tiro", 18, () -> piDisparar());
        sin("a2o_cera_ver", 2, () -> piSrv(sp -> {
            ItemStack w = sp.getMainHandItem();
            PI.put("cera1", com.miscelanea.armasfuego.comun.Cobre.cera(w, com.miscelanea.armasfuego.comun.Cobre.duracionCera(w)));
            // la última gota de cera
            com.miscelanea.armasfuego.comun.Cobre.ponerCera(w, 1);
        }));
        sin("a2o_cera_fin", 18, () -> piDisparar());
        sin("a2o_cera_fin_ver", 2, () -> {
            PI.put("cera_aviso", a2Aviso());
            piSrv(sp -> {
                ItemStack w = sp.getMainHandItem();
                PI.put("cera2", com.miscelanea.armasfuego.comun.Cobre.encerado(w));
                // encerar cinco veces la misma arma
                for (int k = 0; k < 5; k++) {
                    com.miscelanea.armasfuego.comun.Cobre.ponerCera(w, 0);
                    com.miscelanea.armasfuego.comun.Cobre.encerar(sp, w, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB), null);
                }
                PI.put("cera_logro", a2Logro(sp, "armas/cera_cinco"));
            });
        });
        sin("a2o_cera_ok", 2, () -> a2Comprobar("la cera dura N disparos (la Gatling más), al acabarse avisa y vuelve a oxidarse; encerar cinco veces da el logro",
                Integer.valueOf(com.miscelanea.armasfuego.comun.EquilibrioArmas.cera()).equals(PI.get("cera0")) && Integer.valueOf(com.miscelanea.armasfuego.comun.EquilibrioArmas.ceraAutomaticas()).equals(PI.get("cera_g"))
                        && PI.get("cera1") instanceof Integer c1 && PI.get("cera0") instanceof Integer c0 && c1 == c0 - 1
                        && Boolean.FALSE.equals(PI.get("cera2")) && String.valueOf(PI.get("cera_aviso")).contains("cera") && Boolean.TRUE.equals(PI.get("cera_logro")),
                "cera " + PI.get("cera0") + " → " + PI.get("cera1") + ", Gatling " + PI.get("cera_g") + ", al acabarse encerada " + PI.get("cera2")
                        + " y aviso «" + PI.get("cera_aviso") + "», logro " + PI.get("cera_logro")));
        // ---------------------------------------------------------------- bayoneta con los encantamientos de la espada
        sin("a2o_bayoneta", 26, () -> piSrv(sp -> {
            var l = sp.serverLevel();
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(l);
            Vec3 q = a2Punto(2.6, 0, 0);
            z.moveTo(q.x, q.y, q.z, a2Mira.toYRot() + 180, 0);
            z.setNoAi(true);
            z.setPersistenceRequired();
            z.setSilent(true);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60);
            z.setHealth(60);
            // con casco: que no arda al sol (el fuego tiene que ser del Aspecto ígneo)
            z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
            l.addFreshEntity(z);
            a2Diana = z.getUUID();
            ItemStack espada = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            espada.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 5);
            espada.enchant(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, 1);
            espada.enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, 2);
            espada.enchant(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, 3);
            espada.enchant(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1);
            espada.setDamageValue(200);
            ItemStack m = new ItemStack(com.miscelanea.registry.ModItems.MOSQUETE.get());
            com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(m, espada);
            sp.getInventory().setItem(0, m);
            sp.getInventory().selected = 0;
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
            List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
            m.getItem().appendHoverText(m, l, tip, net.minecraft.world.item.TooltipFlag.NORMAL);
            String t = String.join(" | ", tip.stream().map(net.minecraft.network.chat.Component::getString).toList());
            PI.put("bay_tip", t);
            PI.put("bay_brilla", m.hasFoil());
            PI.put("bay_botin", net.minecraft.world.item.enchantment.EnchantmentHelper.getMobLooting(sp));
            PI.put("bay_barrido", net.minecraft.world.item.enchantment.EnchantmentHelper.getSweepingDamageRatio(sp));
            PI.put("bay_vida0", z.getHealth());
            a2Apuntar(z.getBoundingBox().getCenter());
        }));
        sin("a2o_bayoneta_golpe", 4, () -> {
            var z = mc.level.getEntity(mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, mc.player.getBoundingBox().inflate(6)).stream()
                    .findFirst().map(net.minecraft.world.entity.Entity::getId).orElse(-1));
            if (z != null) {
                mc.gameMode.attack(mc.player, z);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        });
        sin("a2o_bayoneta_ver", 2, () -> piSrv(sp -> {
            var z = (net.minecraft.world.entity.LivingEntity) sp.serverLevel().getEntity(a2Diana);
            PI.put("bay_dano", z == null ? -1F : (Float) PI.get("bay_vida0") - z.getHealth());
            PI.put("bay_fuego", z != null && z.isOnFire());
            // la experiencia arregla la espada calada (Reparación)
            ItemStack b = com.miscelanea.armasfuego.oeste.MosqueteItem.bayoneta(sp.getMainHandItem());
            PI.put("bay_rota0", b.getDamageValue());
            var orbe = new net.minecraft.world.entity.ExperienceOrb(sp.serverLevel(), sp.getX(), sp.getY() + 0.5, sp.getZ(), 30);
            sp.serverLevel().addFreshEntity(orbe);
            if (z != null) {
                // a 1 de vida y apagado (si no, el fuego del primer golpe lo remataba antes que la bayoneta)
                z.setHealth(1F);
                z.clearFire();
            }
        }));
        sin("a2o_bayoneta_remate", 26, () -> {});
        sin("a2o_bayoneta_golpe2", 6, () -> {
            var z = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
            if (z != null) {
                mc.gameMode.attack(mc.player, z);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        });
        sin("a2o_bayoneta_ver2", 2, () -> piSrv(sp -> {
            PI.put("bay_rota1", com.miscelanea.armasfuego.oeste.MosqueteItem.bayoneta(sp.getMainHandItem()).getDamageValue());
            PI.put("bay_logro", a2Logro(sp, "armas/bayoneta_ignea"));
            var z = sp.serverLevel().getEntity(a2Diana);
            if (z != null) z.discard();
        }));
        sin("a2o_bayoneta_ok", 2, () -> {
            float d = a2F("bay_dano");
            String tip = String.valueOf(PI.get("bay_tip"));
            int r0 = PI.get("bay_rota0") instanceof Integer i ? i : -1, r1 = PI.get("bay_rota1") instanceof Integer i ? i : -1;
            a2Comprobar("la bayoneta usa los encantamientos de la espada (Filo, Aspecto ígneo, Botín, Reparación; Barrido no), brilla y los lista",
                    d >= 10F && Boolean.TRUE.equals(PI.get("bay_fuego")) && Integer.valueOf(2).equals(PI.get("bay_botin")) && Float.valueOf(0F).equals(PI.get("bay_barrido"))
                            && Boolean.TRUE.equals(PI.get("bay_brilla")) && tip.contains("Filo") && tip.contains("es una estocada") && r1 < r0 && Boolean.TRUE.equals(PI.get("bay_logro")),
                    String.format("daño %.1f (diamante 8 + Filo V 3), arde %s, botín %s, barrido %s, brilla %s, desgaste %d → %d con la experiencia, logro %s; tooltip «%s»",
                            d, PI.get("bay_fuego"), PI.get("bay_botin"), PI.get("bay_barrido"), PI.get("bay_brilla"), r0, r1, PI.get("bay_logro"), tip));
        });
    }

    /** Atajos para los pasos (referencias a método). */
    private static final class A2 {
        static void camara1p() {
            a2Camara1p();
        }
    }

    /** Atajos del rifle para los pasos (referencias a método). */
    private static final class RifleCI {
        static void clic() {
            rifleClic();
        }
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
            // sin avisos de logros (el de la ecolocalización sale ahora): son claros y estropean la medida del cielo
            sinAvisos = true;
            mc.gui.getChat().clearMessages(false);
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
            sinAvisos = false;
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

    /**
     * Dónde estaba (y qué hora era) antes de la parte de los extras del Purgatorio, ya fuera tras las de la generación: al
     * acabar se le devuelve ahí, que es donde se montan las pruebas de después (al salir tras caerse se puede acabar en
     * otro sitio, y en lo que duran los extras se hace de noche).
     */
    private static volatile Vec3 antesPurgatorio;
    private static volatile long horaAntesPurgatorio = -1;

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
                // y que no le oiga ninguno mientras cruza (a veces salía uno nuevo y le recolocaba lejos de la puerta)
                var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) com.miscelanea.purgatorio.eventos.PurgatorioExtras.ponerNoMolestar(sp, 200);
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
            if (!fuera) RESUMEN.add("  purgatorio: no ha salido a la primera (le habrá pillado un habitante por el camino): otra vez");
        });
        // otra vez, si a la primera le pilló un habitante justo al llegar a la puerta (pasa de vez en cuando)
        sin("purgatorio_cruzar_2", 60, () -> {
            if (!com.miscelanea.purgatorio.Purgatorio.en(mc.level)) return;
            var b = com.miscelanea.purgatorio.Purgatorio.buscarSalida();
            var lp = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            if (lp != null) mc.getSingleplayerServer().execute(() -> {
                for (var g : lp.getEntitiesOfClass(com.miscelanea.purgatorio.Purgado.class, new net.minecraft.world.phys.AABB(b).inflate(96))) g.discard();
            });
            irA(new net.minecraft.core.BlockPos(b.getX(), b.getY(), b.getZ() - 3), 180, 0);
        });
        sin("purgatorio_fuera_2", 5, () -> {
            if (com.miscelanea.purgatorio.Purgatorio.en(mc.level)) throw new IllegalStateException("la puerta de salida no le ha sacado");
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
        purgatorioExtras();
    }

    // ------------------------------------------------------------------ El Purgatorio: eventos, objetos purgados y logros (2.0.0)
    private static ServerPlayer spPrueba() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    /** En el hilo del servidor, con el jugador. */
    private static void enServidor(java.util.function.Consumer<ServerPlayer> c) {
        Minecraft mc = Minecraft.getInstance();
        var srv = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
    // ------------------------------------------------------------------ escopeta de presión
    private static boolean escAgachado;
    private static net.minecraft.core.BlockPos escHoguera, escVela, escTierra;
    private static UUID escHusk, escCerdo;
    private static float escVida;
    private static double escY;
    private static Vec3 escCerdoPos;
    private static int escCosas;

    private static ServerPlayer escJugador() {
        var srv = Minecraft.getInstance().getSingleplayerServer();
        return srv.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
    }

    /** En el hilo del servidor, con el jugador de la prueba. */
    private static void escServidor(java.util.function.Consumer<ServerPlayer> c) {
        var srv = Minecraft.getInstance().getSingleplayerServer();
        UUID id = Minecraft.getInstance().player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp != null) c.accept(sp);
        });
    }

    private static final String[] LOGROS_PURGATORIO = {"raiz", "todas_zonas", "rincones", "turno", "no_molestar", "pillado", "herobrine", "reloj",
            "sin_pillar", "caida", "fantasma", "objeto_raro", "missingno"};

    private static String logrosPurgatorio() {
        ServerPlayer sp = spPrueba();
        StringBuilder b = new StringBuilder();
        for (String l : LOGROS_PURGATORIO)
            b.append(l).append(com.miscelanea.purgatorio.eventos.Logros.tiene(sp, l) ? "=sí " : "=NO ");
        b.append("(rincones:");
        for (String c : new String[]{"sala", "recreativa", "feria", "hotel", "piscina", "aparcamiento", "colegio", "centro"})
            b.append(' ').append(c).append(com.miscelanea.purgatorio.eventos.Logros.tiene(sp, "rincones", c) ? "=sí" : "=NO");
        return b.append(')').toString();
    }

    private static void purgatorioExtras() {
        Minecraft mc = Minecraft.getInstance();
        sin("pex_entrar", 60, () -> enServidor(sp -> {
            if (!com.miscelanea.purgatorio.Purgatorio.en(sp.level())) {
                antesPurgatorio = sp.position();
                horaAntesPurgatorio = sp.serverLevel().getDayTime();
            }
            com.miscelanea.purgatorio.Purgatorio.entrar(sp, null);
            var inv = sp.getInventory();
            for (int i = 0; i < 9; i++) inv.setItem(i, ItemStack.EMPTY);
            inv.setItem(0, new ItemStack(com.miscelanea.purgatorio.eventos.ExtrasRegistro.BRUJULA.get()));
            inv.setItem(1, new ItemStack(com.miscelanea.purgatorio.eventos.ExtrasRegistro.RELOJ.get()));
            inv.setItem(2, new ItemStack(com.miscelanea.purgatorio.eventos.ExtrasRegistro.TIQUE.get(), 2));
            inv.setItem(3, new ItemStack(com.miscelanea.purgatorio.eventos.ExtrasRegistro.LLAVE.get()));
            inv.setItem(4, new ItemStack(com.miscelanea.purgatorio.eventos.ExtrasRegistro.FICHA_INFINITA.get()));
            inv.setItem(5, new ItemStack(net.minecraft.world.item.Items.DIRT, 3));
            inv.setItem(6, new ItemStack(com.miscelanea.purgatorio.eventos.ExtrasRegistro.MISSINGNO_ITEM.get(), 4));
            inv.selected = 0;
        }));
        // lo que ya se ha ganado en las partes de antes (entrar, que te pille, caerse por el agujero, salir sin que te pille, zonas, rincones)
        sin("pex_logros_antes", 2, () -> {
            String l = logrosPurgatorio();
            RESUMEN.add("  purgatorio, logros tras recorrerlo: " + l);
            ServerPlayer sp = spPrueba();
            for (String n : new String[]{"raiz", "pillado", "sin_pillar", "todas_zonas"})
                if (!com.miscelanea.purgatorio.eventos.Logros.tiene(sp, n)) throw new IllegalStateException("no se ha dado el logro purgatorio/" + n);
            RESUMEN.add("  purgatorio, " + com.miscelanea.client.purgatorio.ExtrasCliente.estado());
        });
        // brújula de salida
        sin("pex_brujula_ir", 60, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA), 0, 10));
        paso("4l_purgatorio_brujula", 30, () -> {
            ServerPlayer sp = spPrueba();
            ItemStack b = sp.getInventory().getItem(0);
            var obj = com.miscelanea.purgatorio.eventos.BrujulaSalidaItem.objetivo(b);
            var esperada = com.miscelanea.purgatorio.eventos.Salidas.cercana(sp.serverLevel(), sp.blockPosition(), false);
            RESUMEN.add("  purgatorio, brújula de salida: apunta a " + (obj == null ? "NADA" : obj.toShortString()) + ", salida más cercana "
                    + (esperada == null ? "?" : esperada.toShortString()) + " (en el cliente: " + com.miscelanea.purgatorio.eventos.BrujulaSalidaItem.objetivo(mc.player.getInventory().getItem(0)) + ")");
            if (obj == null || esperada == null || obj.distSqr(esperada) > 16) throw new IllegalStateException("la brújula no apunta a la salida más cercana");
        });
        // reloj al revés: en A un rato, luego en B, y al usarlo vuelve a A
        net.minecraft.core.BlockPos[] ab = new net.minecraft.core.BlockPos[2];
        sin("pex_reloj_a", 50, () -> {
            ab[0] = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA);
            ab[1] = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.RECREATIVA);
            irA(ab[0], 0, 0);
            enServidor(com.miscelanea.purgatorio.eventos.PurgatorioExtras::olvidarCamino);
        });
        sin("pex_reloj_b", 30, () -> irA(ab[1], 0, 0));
        sin("pex_reloj_usar", 20, () -> enServidor(sp -> RESUMEN.add("  purgatorio, reloj al revés: usado=" + com.miscelanea.purgatorio.eventos.RelojRevesItem.usar(sp)
                + " desde " + sp.blockPosition().toShortString())));
        sin("pex_reloj_fin", 2, () -> {
            ServerPlayer sp = spPrueba();
            double d = Math.sqrt(sp.blockPosition().distSqr(ab[0]));
            RESUMEN.add(String.format("  purgatorio, reloj al revés: ahora en %s, a %.1f bloques de donde estaba (A %s, B %s)", sp.blockPosition().toShortString(), d,
                    ab[0].toShortString(), ab[1].toShortString()));
            if (d > 3) throw new IllegalStateException("el reloj no le ha devuelto a donde estaba");
        });
        // tique de turno: a los 3 s, delante de la salida
        sin("pex_tique", 90, () -> enServidor(sp -> {
            sp.getInventory().selected = 2;
            RESUMEN.add("  purgatorio, tique de turno: canjeado=" + com.miscelanea.purgatorio.eventos.TiqueTurnoItem.canjear(sp));
        }));
        paso("4m_purgatorio_tique_salida", 30, () -> {
            ServerPlayer sp = spPrueba();
            var puerta = com.miscelanea.purgatorio.eventos.Salidas.cercana(sp.serverLevel(), sp.blockPosition(), false);
            double d = puerta == null ? 999 : Math.sqrt(sp.blockPosition().distSqr(puerta));
            RESUMEN.add(String.format("  purgatorio, tique: le han llamado a %s, a %.1f bloques de la puerta %s", sp.blockPosition().toShortString(), d,
                    puerta == null ? "?" : puerta.toShortString()));
            if (d > 4) throw new IllegalStateException("el tique no le ha llevado a la salida");
        });
        // llave (No molestar) y ficha infinita
        sin("pex_llave_ficha", 5, () -> enServidor(sp -> {
            boolean ok = com.miscelanea.purgatorio.eventos.LlaveHabitacionItem.colgar(sp);
            RESUMEN.add("  purgatorio, llave 404: colgada=" + ok + ", no molestar=" + com.miscelanea.purgatorio.eventos.PurgatorioExtras.noMolestar(sp));
            sp.getInventory().selected = 4;
            boolean f1 = com.miscelanea.arcade.RecreativaBlock.gastarFicha(sp), f2 = com.miscelanea.arcade.RecreativaBlock.gastarFicha(sp),
                    f3 = com.miscelanea.arcade.RecreativaBlock.gastarFicha(sp);
            RESUMEN.add("  purgatorio, ficha infinita: tres partidas " + f1 + " " + f2 + " " + f3 + ", quedan " + sp.getMainHandItem().getCount()
                    + " " + sp.getMainHandItem().getItem());
            if (!ok || !(f1 && f2 && f3) || sp.getMainHandItem().getCount() != 1) RESUMEN.add("  FALLO: llave o ficha infinita");
        }));
        sin("pex_llave_ficha_ver", 2, () -> {
            ServerPlayer sp = spPrueba();
            if (!com.miscelanea.purgatorio.eventos.PurgatorioExtras.noMolestar(sp)) throw new IllegalStateException("la llave no ha colgado el No molestar");
            if (!(sp.getInventory().getItem(4).getItem() instanceof com.miscelanea.purgatorio.eventos.FichaInfinitaItem)) throw new IllegalStateException("la ficha infinita se ha gastado");
        });
        // MissingNo.: unos cuantos puestos (para la foto) y uno que llena el sexto hueco
        sin("pex_missingno_ir", 40, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA), 0, 20));
        paso("4n_purgatorio_missingno", 30, () -> enServidor(sp -> {
            var l = sp.serverLevel();
            var b = sp.blockPosition();
            for (int i = -1; i <= 1; i++) l.setBlockAndUpdate(b.offset(i, 0, 3), com.miscelanea.purgatorio.eventos.ExtrasRegistro.MISSINGNO.get().defaultBlockState());
            l.setBlockAndUpdate(b.offset(0, 1, 3), com.miscelanea.purgatorio.eventos.ExtrasRegistro.MISSINGNO.get().defaultBlockState());
            sp.getInventory().selected = 6;
        }));
        sin("pex_missingno", 10, () -> enServidor(sp -> {
            var b = sp.blockPosition().offset(0, 1, 3);
            int suma = com.miscelanea.purgatorio.eventos.MissingnoBlock.corromper(sp, sp.serverLevel(), b);
            ItemStack sexto = sp.getInventory().getItem(5);
            RESUMEN.add("  purgatorio, MissingNo.: +" + suma + " en el sexto hueco (" + sexto.getCount() + " " + sexto.getItem() + "), el bloque "
                    + (sp.serverLevel().getBlockState(b).isAir() ? "se ha ido" : "SIGUE AHÍ"));
            if (sexto.getCount() != 64 || !sp.serverLevel().getBlockState(b).isAir()) RESUMEN.add("  FALLO: el MissingNo. no ha hecho lo suyo");
        }));
        // objetos tirados con nombres raros: cuatro detrás de él; se gira a mirarlos y coge uno
        sin("pex_raros_ir", 40, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA).offset(0, 0, -4), 0, 25));
        sin("pex_raros", 20, () -> enServidor(sp -> {
            StringBuilder b = new StringBuilder("  purgatorio, objetos raros tirados:");
            for (int k : new int[]{0, 1, 2, 9}) {
                var ie = com.miscelanea.purgatorio.eventos.PurgatorioExtras.tirarRaro(sp, k);
                b.append(ie == null ? " NINGUNO" : " «" + ie.getItem().getHoverName().getString() + "» en " + ie.blockPosition().toShortString());
            }
            RESUMEN.add(b.toString());
        }));
        // para la foto: a tres bloques del primero, mirándolos
        sin("pex_raros_mirar", 30, () -> enServidor(sp -> {
            var l = sp.serverLevel();
            var lista = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(16),
                    ie -> ie.getItem().hasTag() && ie.getItem().getTag().getBoolean(com.miscelanea.purgatorio.eventos.ObjetosRaros.MARCA));
            if (lista.isEmpty()) return;
            var ie = lista.get(0);
            for (int[] d : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                var q = com.miscelanea.purgatorio.eventos.PurgatorioExtras.libreCerca(l, ie.getBlockX() + d[0], ie.getBlockY(), ie.getBlockZ() + d[1], 0);
                if (q == null) continue;
                double dx = ie.getX() - (q.getX() + 0.5), dz = ie.getZ() - (q.getZ() + 0.5);
                sp.teleportTo(l, q.getX() + 0.5, q.getY(), q.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-dx, dz)), 45);
                return;
            }
        }));
        paso("4o_purgatorio_objetos_raros", 10, () -> {});
        sin("pex_raros_coger", 30, () -> enServidor(sp -> {
            var l = sp.serverLevel();
            var lista = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(16),
                    ie -> ie.getItem().hasTag() && ie.getItem().getTag().getBoolean(com.miscelanea.purgatorio.eventos.ObjetosRaros.MARCA));
            if (!lista.isEmpty()) sp.teleportTo(lista.get(0).getX(), lista.get(0).getY(), lista.get(0).getZ());
            RESUMEN.add("  purgatorio, objetos raros en el suelo: " + lista.size());
        }));
        sin("pex_raros_cogido", 2, () -> {
            boolean ok = com.miscelanea.purgatorio.eventos.Logros.tiene(spPrueba(), "objeto_raro");
            RESUMEN.add("  purgatorio, ha cogido un objeto raro: " + (ok ? "sí" : "NO"));
            if (!ok) throw new IllegalStateException("no se ha dado el logro de coger un objeto raro");
        });
        // chat corrupto y sonidos rotos
        paso("4p_purgatorio_chat", 30, () -> {
            var r = mc.player.getRandom();
            for (int k : new int[]{0, 3, 7, 12, 15})
                RESUMEN.add("  purgatorio, chat roto: " + com.miscelanea.client.purgatorio.ExtrasCliente.chatRoto(mc, r, k));
            com.miscelanea.client.purgatorio.ExtrasCliente.sonidoRoto(mc, r, 2);
            com.miscelanea.client.purgatorio.ExtrasCliente.sonidoRoto(mc, r, 9);
            mc.player.setXRot(0);
        });
        // jugador fantasma: uno quieto a 11 bloques (foto con su nombre), luego se le mira fijamente y se va
        sin("pex_fantasma_ir", 40, () -> {
            com.miscelanea.client.purgatorio.ExtrasCliente.sinSorpresas = true;
            com.miscelanea.client.purgatorio.ExtrasCliente.quitarTodos();
        });
        sin("pex_fantasma_ir_2", 40, () -> irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA), 0, 0));
        Vec3[] fan = new Vec3[1];
        sin("pex_fantasma_sale", 20, () -> {
            var l = mc.level;
            var p = mc.player;
            fan[0] = null;
            for (int a = 0; a < 360 && fan[0] == null; a += 45)
                for (double d = 12; d >= 9.5 && fan[0] == null; d -= 0.5) {
                    double fx = p.getX() - Math.sin(Math.toRadians(a)) * d, fz = p.getZ() + Math.cos(Math.toRadians(a)) * d;
                    var bp = net.minecraft.core.BlockPos.containing(fx, p.getY(), fz);
                    if (!l.getBlockState(bp).getCollisionShape(l, bp).isEmpty() || !l.getBlockState(bp.above()).getCollisionShape(l, bp.above()).isEmpty()) continue;
                    var bh = l.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), new Vec3(fx, p.getY() + 1.5, fz), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, p));
                    if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
                    fan[0] = new Vec3(fx, p.getY(), fz);
                    p.setYRot(a);
                    p.setXRot(0);
                }
            if (fan[0] == null) throw new IllegalStateException("no hay sitio a la vista para el fantasma");
            boolean ok = com.miscelanea.client.purgatorio.ExtrasCliente.aparecer(mc, p.getRandom(), fan[0], null, true);
            RESUMEN.add("  purgatorio, jugador fantasma: sale=" + ok + " en " + net.minecraft.core.BlockPos.containing(fan[0]).toShortString() + ", "
                    + com.miscelanea.client.purgatorio.ExtrasCliente.estado());
            if (!ok) throw new IllegalStateException("no sale el jugador fantasma");
        });
        paso("4q_purgatorio_fantasma", 30, () -> {});
        sin("pex_fantasma_mirar", 80, () -> {
            // se le mira fijamente (el jugador no se mueve y lo tiene delante)
            com.miscelanea.client.purgatorio.ExtrasCliente.soltarFijos();
            var p = mc.player;
            double dx = fan[0].x - p.getX(), dz = fan[0].z - p.getZ(), dy = fan[0].y + 1.5 - p.getEyeY();
            p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
            p.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        });
        paso("4r_purgatorio_fantasma_ido", 20, () -> {
            int n = com.miscelanea.client.purgatorio.ExtrasCliente.fantasmas();
            boolean logro = com.miscelanea.purgatorio.eventos.Logros.tiene(spPrueba(), "fantasma");
            RESUMEN.add("  purgatorio, fantasma tras mirarlo fijamente: quedan " + n + ", logro " + (logro ? "sí" : "NO") + ", "
                    + com.miscelanea.client.purgatorio.ExtrasCliente.estado());
            if (n != 0) throw new IllegalStateException("el fantasma no se ha ido al mirarlo");
            if (!logro) throw new IllegalStateException("no se ha dado el logro de ver un fantasma");
            // otro, que se va al acercarse
            if (!com.miscelanea.client.purgatorio.ExtrasCliente.aparecer(mc, mc.player.getRandom(), fan[0], "Invitado_404", false))
                throw new IllegalStateException("no sale el segundo fantasma");
            mc.player.setYRot(mc.player.getYRot() + 180);
        });
        sin("pex_fantasma_acercarse", 10, () -> {
            if (com.miscelanea.client.purgatorio.ExtrasCliente.fantasmas() != 1) throw new IllegalStateException("el segundo fantasma se ha ido antes de tiempo");
            irA(net.minecraft.core.BlockPos.containing(fan[0]), 0, 0);
        });
        sin("pex_fantasma_acercarse_fin", 2, () -> {
            int n = com.miscelanea.client.purgatorio.ExtrasCliente.fantasmas();
            RESUMEN.add("  purgatorio, fantasma al acercarse: quedan " + n);
            com.miscelanea.client.purgatorio.ExtrasCliente.sinSorpresas = false;
            if (n != 0) throw new IllegalStateException("el fantasma no se ha ido al acercarse");
        });
        // pegarle a Herobrine (logro y, esta vez sí o sí, se le cae algo)
        sin("pex_herobrine", 10, () -> enServidor(sp -> {
            var l = sp.serverLevel();
            var g = com.miscelanea.purgatorio.Habitantes.aparecer(l, sp.blockPosition().offset(2, 0, 0), com.miscelanea.purgatorio.Purgado.Variante.HEROBRINE);
            if (g == null) g = com.miscelanea.purgatorio.Habitantes.aparecer(l, sp.blockPosition().offset(-2, 0, 0), com.miscelanea.purgatorio.Purgado.Variante.HEROBRINE);
            if (g == null) {
                RESUMEN.add("  FALLO: no sale Herobrine para pegarle");
                return;
            }
            g.getPersistentData().putBoolean("MiscelaneaSueltaSiOSi", true);
            // al pegarle, a veces se descoloca (aparece unos bloques más allá): lo suyo se le cae donde estaba
            var dondeEstaba = g.getBoundingBox();
            sp.attack(g);
            var caido = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, dondeEstaba.inflate(12), ie -> ie.getAge() < 40);
            RESUMEN.add("  purgatorio, pegarle a Herobrine: logro " + com.miscelanea.purgatorio.eventos.Logros.tiene(sp, "herobrine") + ", se le cae "
                    + (caido.isEmpty() ? "NADA" : caido.get(0).getItem().getItem().toString()));
            if (caido.isEmpty()) RESUMEN.add("  FALLO: a Herobrine no se le ha caído nada");
            g.discard();
        }));
        // el premio de un rincón raro (la caseta abierta de la feria: la brújula)
        sin("pex_rincon_ir", 50, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA);
            if (b == null) throw new IllegalStateException("no hay caseta abierta");
            irMirando(b[0], b[1]);
        });
        paso("4s_purgatorio_rincon_premio", 20, () -> {
            ServerPlayer sp = spPrueba();
            var lista = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(24),
                    ie -> ie.getItem().hasTag() && ie.getItem().getTag().contains("MiscelaneaRincon"));
            StringBuilder b = new StringBuilder("  purgatorio, premios de rincones raros cerca:");
            for (var ie : lista) b.append(' ').append(ie.getItem().getItem()).append(" en ").append(ie.blockPosition().toShortString());
            RESUMEN.add(b.toString());
            if (lista.isEmpty()) throw new IllegalStateException("en la caseta abierta no hay premio");
        });
        // los premios de los rincones raros de las zonas nuevas (en los pisos de abajo también), cada uno en su sitio
        for (String[] zr : new String[][]{{"PISCINA", null}, {"APARCAMIENTO", "4u_purgatorio_premio_coche"}, {"COLEGIO", null}, {"CENTRO", "4v_purgatorio_premio_teles"}}) {
            var zona = com.miscelanea.purgatorio.PurgatorioGenerador.Zona.valueOf(zr[0]);
            sin("pex_premio_" + zr[0], 60, () -> {
                var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(zona);
                if (b == null) throw new IllegalStateException("no hay rincón raro de " + zona);
                irMirando(b[0], b[1]);
            });
            Runnable ver = () -> {
                ServerPlayer sp = spPrueba();
                int n = com.miscelanea.purgatorio.PurgatorioPlano.nivel(sp.getY());
                int[] s = com.miscelanea.purgatorio.eventos.PurgatorioExtras.sitioPremio(zona, n, sp.getBlockX() >> 4, sp.getBlockZ() >> 4);
                var lista = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(16),
                        ie -> ie.getItem().hasTag() && ie.getItem().getTag().contains("MiscelaneaRincon"));
                StringBuilder sb = new StringBuilder("  purgatorio, premio del rincón raro de " + zona + " (piso " + n + ", sitio "
                        + (s == null ? "?" : s[0] + ", " + s[1] + ", " + s[2]) + "):");
                boolean bien = false;
                for (var ie : lista) {
                    sb.append(' ').append(ie.getItem().getItem()).append(" en ").append(ie.blockPosition().toShortString());
                    if (s != null && ie.blockPosition().distSqr(new net.minecraft.core.BlockPos(s[0], s[1], s[2])) <= 2) bien = true;
                }
                if (lista.isEmpty()) sb.append(" NINGUNO");
                RESUMEN.add(sb.toString());
                if (!bien) throw new IllegalStateException("el premio del rincón raro de " + zona + " no está en su sitio");
            };
            if (zr[1] != null) paso(zr[1], 30, ver);
            else sin("pex_premio_" + zr[0] + "_ver", 30, ver);
        }
        // una escalera mecánica suelta del centro comercial, vista desde el cruce de pasillos (los lados en rampa y el cristal)
        sin("pex_escalera_mecanica_ir", 60, () -> {
            net.minecraft.core.BlockPos[] ir = null;
            for (int n = 0; n < 3 && ir == null; n++)
                for (int r = 0; r < 50 && ir == null; r++)
                    for (int cx = -r; cx <= r && ir == null; cx++)
                        for (int cz = -r; cz <= r && ir == null; cz++) {
                            if (Math.max(Math.abs(cx), Math.abs(cz)) != r || com.miscelanea.purgatorio.PurgatorioPlano.zona(n, cx, cz) != com.miscelanea.purgatorio.PurgatorioGenerador.Zona.CENTRO
                                    || com.miscelanea.purgatorio.PurgatorioPlano.grande(n, cx, cz) != null || com.miscelanea.purgatorio.PurgatorioPlano.escalera(n, cx, cz)
                                    || (n > 0 && com.miscelanea.purgatorio.PurgatorioPlano.escalera(n - 1, cx, cz))) continue;
                            for (int k = 0; k < 4 && ir == null; k++) {
                                if (com.miscelanea.purgatorio.PurgatorioGenerador.ocupada(n, cx, cz, k) || com.miscelanea.purgatorio.PurgatorioPlano.hn(n, cx, cz, 220 + k) % 10 != 8) continue;
                                int qx = com.miscelanea.purgatorio.PurgatorioPlano.PARCELAS[k][0], f = com.miscelanea.purgatorio.PurgatorioPlano.base(n);
                                boolean norte = com.miscelanea.purgatorio.PurgatorioPlano.PARCELAS[k][1] == 0;
                                ir = new net.minecraft.core.BlockPos[]{new net.minecraft.core.BlockPos(cx * 16 + (qx == 0 ? 8 : 7), f, cz * 16 + (norte ? 8 : 7)),
                                        new net.minecraft.core.BlockPos(cx * 16 + qx + 2, f + 2, cz * 16 + (norte ? 3 : 12))};
                            }
                        }
            RESUMEN.add("  purgatorio, escalera mecánica suelta: " + (ir == null ? "NINGUNA cerca" : ir[1].toShortString()));
            if (ir == null) throw new IllegalStateException("no hay escalera mecánica suelta cerca");
            irMirando(ir[0], ir[1]);
        });
        paso("4w_purgatorio_escalera_mecanica", 30, () -> {});
        liminal();
        // caerse por un agujero del Mundo sin Texturas (de golpe por debajo del suelo, sin esperar a la caída: con shaders va lento)
        sin("pex_caida", 30, () -> enServidor(sp -> {
            var ag = com.miscelanea.purgatorio.Purgatorio.buscarAgujero();
            if (ag == null) return;
            com.miscelanea.purgatorio.Purgatorio.preparar(sp.serverLevel(), ag);
            sp.teleportTo(ag.getX() + 0.5, -30, ag.getZ() + 0.5);
        }));
        sin("pex_caida_fin", 60, () -> {
            boolean fuera = !com.miscelanea.purgatorio.Purgatorio.en(mc.level), ok = com.miscelanea.purgatorio.eventos.Logros.tiene(spPrueba(), "caida");
            RESUMEN.add("  purgatorio, caerse por el agujero: fuera=" + fuera + ", logro " + (ok ? "sí" : "NO"));
            enServidor(sp -> com.miscelanea.purgatorio.Purgatorio.entrar(sp, null));
        });
        // los logros: todos, y la pestaña del Purgatorio abierta para la foto
        sin("pex_logros", 10, () -> {
            String l = logrosPurgatorio();
            RESUMEN.add("  purgatorio, logros al final: " + l);
            if (l.contains("=NO")) throw new IllegalStateException("faltan logros del Purgatorio: " + l);
            var ca = mc.player.connection.getAdvancements();
            mc.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(ca));
            var raiz = ca.getAdvancements().get(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "purgatorio/raiz"));
            if (raiz != null) ca.setSelectedTab(raiz, true);
        });
        paso("4t_purgatorio_logros", 40, () -> {});
        sin("pex_salir", 40, () -> {
            mc.setScreen(null);
            enServidor(com.miscelanea.purgatorio.Purgatorio::salir);
        });
        sin("pex_fuera", 2, () -> {
            ItemStack b = spPrueba().getInventory().getItem(0);
            RESUMEN.add("  purgatorio, brújula fuera: " + (com.miscelanea.purgatorio.eventos.BrujulaSalidaItem.objetivo(b) == null ? "da vueltas" : "SIGUE APUNTANDO"));
            enServidor(sp -> {
                for (int i = 0; i < 9; i++) sp.getInventory().setItem(i, ItemStack.EMPTY);
                // de vuelta a donde estaba antes de los extras
                if (antesPurgatorio != null && !com.miscelanea.purgatorio.Purgatorio.en(sp.level()))
                    sp.teleportTo(sp.serverLevel(), antesPurgatorio.x, antesPurgatorio.y, antesPurgatorio.z, sp.getYRot(), 0);
                // y a la hora de entonces, sin bichos alrededor: las pruebas del Purgatorio son largas y, mientras, se acercan
                // zombis (de noche) y aldeanos que luego se meten en medio del láser o de la ecolocalización
                if (horaAntesPurgatorio >= 0) sp.serverLevel().setDayTime(horaAntesPurgatorio);
                sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(80)).forEach(net.minecraft.world.entity.Entity::discard);
            });
        });
    }

    // ------------------------------------------------------------------ el espacio liminal (2.0.0): atrezo nuevo y zonas mejoradas
    /** En el servidor: el bloque de ese tipo más cercano a c (en x/z, hasta r, de y0 a y1) que cumpla "ok", o null. */
    private static net.minecraft.core.BlockPos cercano(net.minecraft.core.BlockPos c, int r, int y0, int y1, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> ok) {
        var srv = Minecraft.getInstance().getSingleplayerServer();
        var l = srv.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
        return srv.submit(() -> {
            for (int d = 0; d <= r; d++)
                for (int dx = -d; dx <= d; dx++)
                    for (int dz = -d; dz <= d; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                        l.getChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4);
                        for (int y = y0; y <= y1; y++) {
                            var q = new net.minecraft.core.BlockPos(c.getX() + dx, y, c.getZ() + dz);
                            if (ok.test(l.getBlockState(q))) return q;
                        }
                    }
            return (net.minecraft.core.BlockPos) null;
        }).join();
    }

    private static net.minecraft.core.BlockPos[] lim = new net.minecraft.core.BlockPos[8];
    private static float limGiro;

    private static void liminal() {
        Minecraft mc = Minecraft.getInstance();
        var FAC = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
        // otra foto de la escalera mecánica un momento después: los peldaños se han movido
        paso("6a_liminal_escalera_en_marcha", 12, () -> {
            int n = 0;
            var p = mc.player.blockPosition();
            for (int cx = -3; cx <= 3; cx++)
                for (int cz = -3; cz <= 3; cz++) {
                    var ch = mc.level.getChunkSource().getChunkNow((p.getX() >> 4) + cx, (p.getZ() >> 4) + cz);
                    if (ch != null) for (var be : ch.getBlockEntities().values()) if (be instanceof com.miscelanea.purgatorio.liminal.AtrezoBE) n++;
                }
            net.minecraft.world.level.block.entity.BlockEntity uno = null;
            for (int cx = -1; cx <= 1 && uno == null; cx++)
                for (int cz = -1; cz <= 1 && uno == null; cz++) {
                    var ch = mc.level.getChunkSource().getChunkNow((p.getX() >> 4) + cx, (p.getZ() >> 4) + cz);
                    if (ch != null) for (var be : ch.getBlockEntities().values()) if (be instanceof com.miscelanea.purgatorio.liminal.AtrezoBE) uno = be;
                }
            RESUMEN.add("  liminal, bloques-entidad del atrezo (GeckoLib) cargados en 7 x 7 chunks del centro comercial: " + n + ", dibujados hasta ahora "
                    + com.miscelanea.client.purgatorio.LiminalCliente.dibujados + (uno == null ? "" : ", uno en " + uno.getBlockPos().toShortString() + " (" + mc.level.getBlockState(uno.getBlockPos()).getBlock()
                    + ", renderer " + mc.getBlockEntityRenderDispatcher().getRenderer(uno) + ", quitado=" + uno.isRemoved() + ")"));
        });
        // un coche aparcado, de cerca
        sin("lim_coche_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.APARCAMIENTO);
            lim[0] = cercano(b, 40, b.getY(), b.getY(), st -> st.is(com.miscelanea.purgatorio.liminal.LiminalRegistro.COCHE.get()) && st.getValue(com.miscelanea.purgatorio.liminal.Bloques.PARTE8) == 0
                    && !st.getValue(com.miscelanea.purgatorio.liminal.Bloques.FAROS));
            RESUMEN.add("  liminal, coche aparcado: " + (lim[0] == null ? "NINGUNO" : lim[0].toShortString()));
            if (lim[0] == null) throw new IllegalStateException("no hay coches en el aparcamiento");
            var st = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[0]);
            var f = st.getValue(FAC);
            var centro = lim[0].relative(f.getClockWise(), 1).relative(f.getOpposite(), 2);
            irMirando(lim[0].relative(f, 3).relative(f.getClockWise(), -2), centro);
        });
        paso("6b_liminal_coche", 30, () -> {});
        sin("lim_coche_2", 30, () -> {
            var st = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[0]);
            var f = st.getValue(FAC);
            var centro = lim[0].relative(f.getClockWise(), 1).relative(f.getOpposite(), 2);
            // desde la calle, en diagonal
            var hacia = Math.floorMod(lim[0].getX(), 16) < 8 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
            irMirando(lim[0].relative(f, 3).relative(hacia, 4), centro);
        });
        paso("6c_liminal_coche_diagonal", 30, () -> {});
        // los maniquíes del escaparate: se mira a uno, se aparta la vista y al volver te está mirando
        sin("lim_maniqui_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.CENTRO);
            lim[1] = cercano(b, 64, b.getY(), b.getY(), st -> st.is(com.miscelanea.purgatorio.liminal.LiminalRegistro.MANIQUI.get()));
            RESUMEN.add("  liminal, maniquí: " + (lim[1] == null ? "NINGUNO" : lim[1].toShortString()));
            if (lim[1] == null) throw new IllegalStateException("no hay escaparates con maniquíes cerca");
            var f = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[1]).getValue(FAC);
            irMirando(lim[1].relative(f, 4), lim[1].above());
        });
        paso("6d_liminal_maniquies", 30, () -> {
            if (mc.level.getBlockEntity(lim[1]) instanceof com.miscelanea.purgatorio.liminal.AtrezoBE be) limGiro = be.giro;
        });
        // se aparta a un lado, de espaldas al escaparate, y luego vuelve a mirar
        sin("lim_maniqui_mirar_atras", 20, () -> {
            var f = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[1]).getValue(FAC);
            irA(lim[1].relative(f, 5).relative(f.getClockWise(), 2), f.toYRot(), 0);
        });
        sin("lim_maniqui_volver", 10, () -> {
            var f = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[1]).getValue(FAC);
            irMirando(lim[1].relative(f, 5).relative(f.getClockWise(), 2), lim[1].above());
        });
        paso("6e_liminal_maniqui_te_mira", 20, () -> {
            var be = mc.level.getBlockEntity(lim[1]) instanceof com.miscelanea.purgatorio.liminal.AtrezoBE a ? a : null;
            RESUMEN.add(String.format("  liminal, maniquí: miraba a %.0f, ahora a %.0f (se ha girado %d veces; maniquíes vistos %d, tics %d)", limGiro, be == null ? Float.NaN : be.giro,
                    be == null ? -1 : be.giros, com.miscelanea.client.purgatorio.LiminalCliente.MANIQUIES.size(), com.miscelanea.client.purgatorio.LiminalCliente.ticksManiqui));
            if (be == null || be.giros == 0) RESUMEN.add("  AVISO: el maniquí no se ha girado al no mirarlo");
        });
        // la piscina llena con su flotador
        sin("lim_piscina_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.PISCINA);
            lim[2] = cercano(b, 48, b.getY() - 1, b.getY() - 1, st -> st.is(com.miscelanea.purgatorio.liminal.LiminalRegistro.FLOTADOR.get()));
            RESUMEN.add("  liminal, flotador en una piscina llena: " + (lim[2] == null ? "NINGUNO" : lim[2].toShortString()));
            if (lim[2] == null) throw new IllegalStateException("no hay piscinas llenas con flotador cerca");
            var l = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            // de pie en el borde (o en la calle de al lado), mirando al flotador
            var desde = com.miscelanea.purgatorio.eventos.PurgatorioExtras.libreCerca(l, lim[2].getX(), lim[2].getY() + 1, lim[2].getZ() + 3, 1);
            irMirando(desde == null ? lim[2].offset(0, 1, 3) : desde, lim[2].below());
        });
        paso("6f_liminal_piscina_llena", 40, () -> {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), net.minecraft.sounds.SoundEvents.STONE_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1F, false);
            RESUMEN.add("  liminal, piscina: ecos de pasos " + com.miscelanea.client.purgatorio.LiminalCliente.ecos + " (sin tarjeta de sonido no suena nada)");
        });
        // las Backrooms (en la sala de espera)
        sin("lim_backrooms_ir", 60, () -> {
            net.minecraft.core.BlockPos b = null;
            for (int n = 0; n < 3 && b == null; n++)
                for (int r = 0; r < 40 && b == null; r++)
                    for (int cx = -r; cx <= r && b == null; cx++)
                        for (int cz = -r; cz <= r && b == null; cz++)
                            if (Math.max(Math.abs(cx), Math.abs(cz)) == r && com.miscelanea.purgatorio.PurgatorioPlano.zona(n, cx, cz) == com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA
                                    && com.miscelanea.purgatorio.PurgatorioGenerador.entero(n, cx, cz) && com.miscelanea.purgatorio.PurgatorioGenerador.backrooms(n, cx, cz)
                                    && com.miscelanea.purgatorio.PurgatorioGenerador.backrooms(n, cx + 1, cz) && !com.miscelanea.purgatorio.PurgatorioPlano.salida(n, cx, cz))
                                b = new net.minecraft.core.BlockPos(cx * 16 + 8, com.miscelanea.purgatorio.PurgatorioPlano.base(n), cz * 16 + 8);
            RESUMEN.add("  liminal, Backrooms: " + (b == null ? "NINGUNA" : b.toShortString()));
            if (b == null) throw new IllegalStateException("no hay Backrooms cerca");
            lim[3] = b;
            irA(b, -70, 4);
        });
        paso("6g_liminal_backrooms", 40, () -> {});
        sin("lim_backrooms_2", 30, () -> irA(lim[3], 160, 2));
        paso("6h_liminal_backrooms_2", 30, () -> {});
        // un aula con sus pupitres
        sin("lim_aula_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.COLEGIO);
            lim[4] = cercano(b, 40, b.getY(), b.getY(), st -> st.is(com.miscelanea.purgatorio.liminal.LiminalRegistro.PUPITRE.get()) && !st.getValue(com.miscelanea.purgatorio.liminal.Bloques.SUBIDA));
            RESUMEN.add("  liminal, pupitre: " + (lim[4] == null ? "NINGUNO" : lim[4].toShortString()));
            if (lim[4] == null) throw new IllegalStateException("no hay pupitres cerca");
            var f = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[4]).getValue(FAC);
            // de pie al fondo del aula, mirando a la pizarra (el pupitre de delante es el de la fila de la pizarra)
            int x0 = lim[4].getX() - Math.floorMod(lim[4].getX(), 16) + (Math.floorMod(lim[4].getX(), 16) < 8 ? 3 : 12);
            var fondo = new net.minecraft.core.BlockPos(x0, lim[4].getY(), lim[4].getZ()).relative(f.getOpposite(), 1);
            irMirando(fondo, fondo.relative(f, 5).above());
        });
        paso("6i_liminal_aula", 30, () -> {});
        // el ventilador del techo de la sala de espera
        sin("lim_ventilador_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA);
            lim[5] = cercano(b, 80, b.getY() + 3, b.getY() + 3, st -> st.is(com.miscelanea.purgatorio.liminal.LiminalRegistro.VENTILADOR.get()));
            for (int n = 0; n < 3 && lim[5] == null; n++) {
                var g = com.miscelanea.purgatorio.Purgatorio.buscarGrande(com.miscelanea.purgatorio.PurgatorioPlano.Tipo.VESTIBULO);
                if (g != null) lim[5] = new net.minecraft.core.BlockPos(g.x0() + 8, com.miscelanea.purgatorio.PurgatorioPlano.base(g.n()) + 7, g.z0() + 8);
            }
            RESUMEN.add("  liminal, ventilador: " + (lim[5] == null ? "NINGUNO" : lim[5].toShortString()));
            if (lim[5] == null) throw new IllegalStateException("no hay ventiladores cerca");
            irMirando(lim[5].offset(-3, -3, -3), lim[5]);
        });
        paso("6j_liminal_ventilador", 30, () -> {});
        // una máquina expendedora: foto y se usa (suelta algo, o se traga la moneda, y queda agotada)
        sin("lim_maquina_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.CENTRO);
            lim[6] = cercano(b, 64, b.getY(), b.getY(), st -> st.is(com.miscelanea.purgatorio.liminal.LiminalRegistro.MAQUINA.get()) && !st.getValue(com.miscelanea.purgatorio.liminal.Bloques.ARRIBA));
            RESUMEN.add("  liminal, máquina expendedora: " + (lim[6] == null ? "NINGUNA" : lim[6].toShortString()));
            if (lim[6] == null) throw new IllegalStateException("no hay máquinas expendedoras cerca");
            var f = mc.getSingleplayerServer().getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION).getBlockState(lim[6]).getValue(FAC);
            irMirando(lim[6].relative(f, 3).relative(f.getClockWise(), 1), lim[6].above());
        });
        paso("6k_liminal_maquina", 30, () -> enServidor(sp -> {
            var l = sp.serverLevel();
            var st = l.getBlockState(lim[6]);
            var res = st.use(l, sp, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lim[6]), net.minecraft.core.Direction.UP, lim[6], false));
            boolean vacia = l.getBlockState(lim[6]).getValue(com.miscelanea.purgatorio.liminal.Bloques.VACIA);
            RESUMEN.add("  liminal, máquina usada: " + res + ", agotada=" + vacia);
            if (!vacia) RESUMEN.add("  FALLO: la máquina expendedora no se ha agotado al usarla");
        }));
        // el garaje con el coche de los faros, de lejos (el haz de luz)
        sin("lim_faros_ir", 60, () -> {
            var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.APARCAMIENTO);
            if (b == null) throw new IllegalStateException("no hay coche con los faros encendidos");
            int bx = b[0].getX() - Math.floorMod(b[0].getX(), 16), bz = b[0].getZ() - Math.floorMod(b[0].getZ(), 16);
            irMirando(new net.minecraft.core.BlockPos(bx + 9, b[0].getY(), bz + 4), new net.minecraft.core.BlockPos(bx + 13, b[0].getY(), bz + 12));
        });
        paso("6l_liminal_faros", 30, () -> {});
    }

    // ------------------------------------------------------------------ el Purgatorio, estructura nueva (2.0.0)
    private static int[] p3Escalera, p3Ascensor;
    private static int p3AscensorPiso;
    private static com.miscelanea.purgatorio.PurgatorioPlano.Grande p3Bucle;

    /** Un sitio donde se pueda estar de pie (los dos de alto libres y suelo debajo) para andar() . */
    private static boolean pasable(net.minecraft.server.level.ServerLevel l, net.minecraft.core.BlockPos p) {
        var s = l.getBlockState(p);
        if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && !s.is(net.minecraft.world.level.block.Blocks.IRON_DOOR)) return true;
        var f = s.getCollisionShape(l, p);
        return f.isEmpty() || f.max(net.minecraft.core.Direction.Axis.Y) <= 0.2;
    }

    private static boolean dePie(net.minecraft.server.level.ServerLevel l, net.minecraft.core.BlockPos p) {
        if (!pasable(l, p) || !pasable(l, p.above())) return false;
        var b = p.below();
        var s = l.getBlockState(b);
        var f = s.getCollisionShape(l, b);
        if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return false;
        return !f.isEmpty() && f.max(net.minecraft.core.Direction.Axis.Y) <= 1.0 && f.max(net.minecraft.core.Direction.Axis.Y) > 0.4;
    }

    /**
     * Busca andando (de pie, subiendo de uno en uno, bajando hasta tres, abriendo puertas de madera) un camino desde
     * "desde" hasta algo que cumpla "meta". Devuelve los pasos o -1 (sin camino en "max" sitios). En el hilo del servidor.
     */
    private static int andar(net.minecraft.server.level.ServerLevel l, net.minecraft.core.BlockPos desde, java.util.function.Predicate<net.minecraft.core.BlockPos> meta, int max) {
        java.util.HashMap<Long, Integer> visto = new java.util.HashMap<>();
        java.util.ArrayDeque<net.minecraft.core.BlockPos> cola = new java.util.ArrayDeque<>();
        cola.add(desde);
        visto.put(desde.asLong(), 0);
        while (!cola.isEmpty() && visto.size() < max) {
            var p = cola.poll();
            int d = visto.get(p.asLong());
            if (meta.test(p)) return d;
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var q = p.relative(dir);
                l.getChunk(q.getX() >> 4, q.getZ() >> 4);
                net.minecraft.core.BlockPos ok = null;
                if (dePie(l, q)) ok = q;
                else if (dePie(l, q.above()) && pasable(l, p.above(2))) ok = q.above();
                else if (pasable(l, q) && pasable(l, q.above()))
                    for (int k = 1; k <= 3 && ok == null; k++) {
                        var r = q.below(k);
                        if (dePie(l, r)) ok = r;
                        else if (!pasable(l, r)) break;
                    }
                if (ok != null && visto.putIfAbsent(ok.asLong(), d + 1) == null) cola.add(ok);
            }
        }
        return -1;
    }

    /** El plano del Purgatorio dibujado (los tres pisos, un píxel por chunk): para ver las formas de las zonas. */
    private static void mapaPurgatorio() {
        int R = 64, px = 3, W = (2 * R + 1) * px;
        int[] col = {0x3a2a55, 0xc9c2a0, 0x8a2bd0, 0xb5663a, 0xff00dc, 0x6fd3e8, 0x6b6f74, 0x5f8f4a, 0xe0b84a};
        var img = new java.awt.image.BufferedImage(W * 3 + 20, W, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] cuenta = new int[com.miscelanea.purgatorio.PurgatorioGenerador.Zona.values().length];
        int grandes = 0, escaleras = 0, ascensores = 0, salidas = 0;
        for (int n = 0; n < 3; n++)
            for (int cx = -R; cx <= R; cx++)
                for (int cz = -R; cz <= R; cz++) {
                    var z = com.miscelanea.purgatorio.PurgatorioPlano.zona(n, cx, cz);
                    cuenta[z.ordinal()]++;
                    var g = com.miscelanea.purgatorio.PurgatorioPlano.grande(n, cx, cz);
                    if (g != null && g.cx0() == cx && g.cz0() == cz) grandes++;
                    Integer marca = null;
                    if (com.miscelanea.purgatorio.PurgatorioPlano.salida(n, cx, cz)) {
                        marca = 0x00ff00;
                        salidas++;
                    }
                    if (com.miscelanea.purgatorio.PurgatorioPlano.escalera(n, cx, cz)) {
                        marca = 0xffffff;
                        escaleras++;
                    }
                    if (com.miscelanea.purgatorio.PurgatorioPlano.ascensor(cx, cz)) {
                        marca = 0xffff00;
                        if (n == 0) ascensores++;
                    }
                    for (int i = 0; i < px; i++)
                        for (int j = 0; j < px; j++) {
                            int c = col[z.ordinal()];
                            if (g != null) {
                                boolean borde = (cx == g.cx0() && i == 0) || (cx == g.cx0() + g.w() - 1 && i == px - 1) || (cz == g.cz0() && j == 0) || (cz == g.cz0() + g.d() - 1 && j == px - 1);
                                if (borde) c = g.tipo() == com.miscelanea.purgatorio.PurgatorioPlano.Tipo.BUCLE ? 0xff2020 : 0xffffff;
                            }
                            if (marca != null && i == 1 && j == 1) c = marca;
                            img.setRGB(n * (W + 10) + (cx + R) * px + i, (cz + R) * px + j, c);
                        }
                }
        try {
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "screenshots");
            dir.mkdirs();
            javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, "50_purgatorio_mapa.png"));
        } catch (Exception ex) {
            RESUMEN.add("  purgatorio, mapa: no se pudo guardar: " + ex);
        }
        StringBuilder sb = new StringBuilder("  purgatorio, mapa (3 pisos, 129 x 129 chunks):");
        for (var z : com.miscelanea.purgatorio.PurgatorioGenerador.Zona.values()) sb.append(' ').append(z).append('=').append(cuenta[z.ordinal()]);
        sb.append(" · salas grandes ").append(grandes).append(", escaleras ").append(escaleras).append(", ascensores ").append(ascensores).append(", salidas ").append(salidas);
        RESUMEN.add(sb.toString());
    }

    /** El Purgatorio, tercera parte (2.0.0): pisos, escaleras, ascensor, salas grandes, el pasillo que vuelve, puertas a la pared y que se puede llegar andando a una salida. */
    private static void purgatorio3() {
        Minecraft mc = Minecraft.getInstance();
        var srv0 = mc.getSingleplayerServer();
        sin("purgatorio3_mapa", 2, PruebaCI::mapaPurgatorio);
        // desde arriba y sin niebla: los tejados de cada zona dibujan sus formas
        GameType[] antes = {GameType.CREATIVE};
        sin("purgatorio3_arriba_ir", 100, () -> {
            com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.sinNiebla = true;
            UUID id = mc.player.getUUID();
            srv0.execute(() -> {
                ServerPlayer sp = srv0.getPlayerList().getPlayer(id);
                var l = srv0.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                if (sp == null || l == null) return;
                antes[0] = sp.gameMode.getGameModeForPlayer();
                sp.setGameMode(GameType.SPECTATOR);
                sp.teleportTo(l, 8, 150, 8, 0, 90);
            });
        });
        paso("50_purgatorio_desde_arriba", 80, () -> mc.player.setXRot(90));
        sin("purgatorio3_arriba_fin", 20, () -> {
            com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.sinNiebla = false;
            UUID id = mc.player.getUUID();
            srv0.execute(() -> {
                ServerPlayer sp = srv0.getPlayerList().getPlayer(id);
                if (sp != null) sp.setGameMode(antes[0]);
            });
            irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA), 0, 0);
        });
        // los pisos de abajo
        for (int n = 1; n <= 2; n++) {
            int nn = n;
            sin("purgatorio3_piso" + n + "_ir", 60, () -> {
                net.minecraft.core.BlockPos b = null;
                for (var z : new com.miscelanea.purgatorio.PurgatorioGenerador.Zona[]{com.miscelanea.purgatorio.PurgatorioGenerador.Zona.HOTEL, com.miscelanea.purgatorio.PurgatorioGenerador.Zona.SALA})
                    if (b == null) b = com.miscelanea.purgatorio.Purgatorio.buscar(z, nn, 30);
                RESUMEN.add("  purgatorio, piso " + nn + ": " + (b == null ? "NADA" : b.toShortString() + " " + com.miscelanea.purgatorio.PurgatorioGenerador.zonaEn(b.getX(), b.getY(), b.getZ())));
                if (b != null) irA(b, 30, 4);
            });
            paso("51_purgatorio_piso" + n, 40, () -> RESUMEN.add("  purgatorio, piso " + nn + ": el jugador está a y=" + (int) mc.player.getY() + ", niebla "
                    + String.format("%.1f", com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual()) + ", ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando()));
        }
        // la escalera que baja del piso de arriba al de abajo
        sin("purgatorio3_escalera_ir", 60, () -> {
            p3Escalera = com.miscelanea.purgatorio.Purgatorio.buscarEscalera(0);
            RESUMEN.add("  purgatorio, escalera del piso 0 al 1: " + (p3Escalera == null ? "NINGUNA" : "chunk " + p3Escalera[0] + ", " + p3Escalera[1]
                    + " (" + com.miscelanea.purgatorio.PurgatorioPlano.zona(0, p3Escalera[0], p3Escalera[1]) + " -> " + com.miscelanea.purgatorio.PurgatorioPlano.zona(1, p3Escalera[0], p3Escalera[1] + 1) + ")"));
            if (p3Escalera == null) throw new IllegalStateException("no hay escaleras cerca del origen");
            irA(new net.minecraft.core.BlockPos(p3Escalera[0] * 16 + 8, 64, p3Escalera[1] * 16 - 3), 0, 30);
        });
        paso("52_purgatorio_escalera_arriba", 40, () -> {});
        sin("purgatorio3_escalera_tunel", 40, () -> irA(new net.minecraft.core.BlockPos(p3Escalera[0] * 16 + 8, 63 - 5, p3Escalera[1] * 16 + 5), 0, 20));
        paso("53_purgatorio_escalera_tunel", 30, () -> {});
        sin("purgatorio3_escalera_abajo", 40, () -> irA(new net.minecraft.core.BlockPos(p3Escalera[0] * 16 + 8, 48, (p3Escalera[1] + 1) * 16 + 4), 180, -20));
        paso("54_purgatorio_escalera_abajo", 30, () -> {});
        sin("purgatorio3_escalera_andar", 20, () -> {
            var l = srv0.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            int[] e = p3Escalera;
            var desde = new net.minecraft.core.BlockPos(e[0] * 16 + 8, 64, e[1] * 16 - 2);
            var fin = new net.minecraft.core.BlockPos(e[0] * 16 + 8, 48, e[1] * 16 + 17);
            int d = srv0.submit(() -> andar(l, desde, q -> q.equals(fin) || q.equals(fin.west()), 60000)).join();
            RESUMEN.add("  purgatorio, bajar la escalera andando: " + (d < 0 ? "NO SE PUEDE" : d + " pasos"));
            if (d < 0) throw new IllegalStateException("la escalera no se puede bajar andando");
        });
        // el ascensor del hotel: se aprieta la botonera y te deja en otro piso, en el mismo sitio
        sin("purgatorio3_ascensor_ir", 60, () -> {
            p3Ascensor = com.miscelanea.purgatorio.Purgatorio.buscarAscensor();
            if (p3Ascensor == null) throw new IllegalStateException("no hay ascensores cerca del origen");
            p3AscensorPiso = 0;
            for (int n = 0; n < 3; n++)
                if (com.miscelanea.purgatorio.PurgatorioPlano.zona(n, p3Ascensor[0], p3Ascensor[1]) == com.miscelanea.purgatorio.PurgatorioGenerador.Zona.HOTEL) {
                    p3AscensorPiso = n;
                    break;
                }
            RESUMEN.add("  purgatorio, ascensor en el chunk " + p3Ascensor[0] + ", " + p3Ascensor[1] + ", piso " + p3AscensorPiso);
            irA(new net.minecraft.core.BlockPos(p3Ascensor[0] * 16 + 12, com.miscelanea.purgatorio.PurgatorioPlano.base(p3AscensorPiso), p3Ascensor[1] * 16 + 7), 180, 5);
        });
        paso("55_purgatorio_ascensor_fuera", 30, () -> {});
        sin("purgatorio3_ascensor_dentro", 30, () -> irA(new net.minecraft.core.BlockPos(p3Ascensor[0] * 16 + 12, com.miscelanea.purgatorio.PurgatorioPlano.base(p3AscensorPiso), p3Ascensor[1] * 16 + 2), 0, 10));
        paso("56_purgatorio_ascensor", 20, () -> {
            UUID id = mc.player.getUUID();
            srv0.execute(() -> {
                ServerPlayer sp = srv0.getPlayerList().getPlayer(id);
                var l = srv0.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                if (sp == null || l == null) return;
                var b = com.miscelanea.purgatorio.PurgatorioGenerador.botonera(p3Ascensor[0], p3Ascensor[1], p3AscensorPiso);
                RESUMEN.add("  purgatorio, botonera: " + l.getBlockState(b).getBlock().getDescriptionId());
                com.miscelanea.purgatorio.Purgatorio.llamarAscensor(sp, b);
            });
        });
        sin("purgatorio3_ascensor_viaje", 60, () -> {});
        sin("purgatorio3_ascensor_llega", 2, () -> {
            int n = com.miscelanea.purgatorio.PurgatorioPlano.nivel(mc.player.getY());
            double dx = mc.player.getX() - (p3Ascensor[0] * 16 + 12.5), dz = mc.player.getZ() - (p3Ascensor[1] * 16 + 2.5);
            RESUMEN.add(String.format("  purgatorio, ascensor: del piso %d al %d (y=%.1f), movido en x/z %.2f, %.2f, zona al salir %s", p3AscensorPiso, n, mc.player.getY(), dx, dz,
                    com.miscelanea.purgatorio.PurgatorioGenerador.zonaEn(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
            if (n == p3AscensorPiso) throw new IllegalStateException("el ascensor no ha cambiado de piso");
            if (Math.abs(dx) > 0.6 || Math.abs(dz) > 0.6) throw new IllegalStateException("el ascensor le ha movido de sitio");
        });
        paso("57_purgatorio_ascensor_otro_piso", 30, () -> {
            mc.player.setYRot(0);
            mc.player.setXRot(10);
        });
        // las salas grandes
        String[][] grandes = {{"PABELLON", "58_purgatorio_pabellon"}, {"SALA_ENORME", "59_purgatorio_sala_enorme"}, {"VESTIBULO", "5a_purgatorio_vestibulo"}};
        for (String[] gr : grandes) {
            var t = com.miscelanea.purgatorio.PurgatorioPlano.Tipo.valueOf(gr[0]);
            sin("ir_" + gr[1], 60, () -> {
                var g = com.miscelanea.purgatorio.Purgatorio.buscarGrande(t);
                RESUMEN.add("  purgatorio, sala grande " + t + ": " + (g == null ? "NINGUNA" : "piso " + g.n() + ", chunks " + g.cx0() + ", " + g.cz0() + " (" + g.w() + " x " + g.d() + ")"));
                if (g == null) throw new IllegalStateException("no hay " + t + " cerca del origen");
                irA(new net.minecraft.core.BlockPos(g.x0() + (g.w() / 2) * 16 + 8, com.miscelanea.purgatorio.PurgatorioPlano.base(g.n()), g.z0() + g.d() * 16 - 3), 180, 6);
            });
            paso(gr[1], 50, () -> {});
            sin("ir_" + gr[1] + "_2", 40, () -> {
                var g = com.miscelanea.purgatorio.Purgatorio.buscarGrande(t);
                irA(new net.minecraft.core.BlockPos(g.x0() + 4, com.miscelanea.purgatorio.PurgatorioPlano.base(g.n()), g.z0() + 4), -45, 12);
            });
            paso(gr[1] + "_2", 40, () -> {});
        }
        // el pasillo que vuelve al mismo sitio: se le pone pasada la raya y tiene que volver 20 atrás
        sin("purgatorio3_bucle_ir", 60, () -> {
            p3Bucle = com.miscelanea.purgatorio.Purgatorio.buscarGrande(com.miscelanea.purgatorio.PurgatorioPlano.Tipo.BUCLE);
            RESUMEN.add("  purgatorio, pasillo que vuelve: " + (p3Bucle == null ? "NINGUNO" : "piso " + p3Bucle.n() + ", chunks " + p3Bucle.cx0() + ", " + p3Bucle.cz0() + " (" + p3Bucle.zona() + ")"));
            if (p3Bucle == null) throw new IllegalStateException("no hay pasillo-bucle cerca del origen");
            irA(new net.minecraft.core.BlockPos(p3Bucle.x0() + 8, com.miscelanea.purgatorio.PurgatorioPlano.base(p3Bucle.n()), p3Bucle.z0() + 2), 0, 4);
        });
        paso("5b_purgatorio_bucle", 40, () -> {});
        sin("purgatorio3_bucle_andar", 40, () -> irA(new net.minecraft.core.BlockPos(p3Bucle.x0() + 8, com.miscelanea.purgatorio.PurgatorioPlano.base(p3Bucle.n()), p3Bucle.z0() + 29), 0, 4));
        sin("purgatorio3_bucle_mira", 2, () -> {
            double lz = mc.player.getZ() - p3Bucle.z0();
            RESUMEN.add(String.format("  purgatorio, pasillo que vuelve: puesto a 29,5 de la entrada, ahora a %.1f (%s)", lz, Math.abs(lz - 9.5) < 2 ? "ha vuelto 20 atrás" : "MAL"));
            if (Math.abs(lz - 9.5) >= 2) throw new IllegalStateException("el pasillo que vuelve no le ha devuelto 20 bloques: " + lz);
        });
        paso("5c_purgatorio_bucle_vuelta", 20, () -> {});
        // una puerta que da a la pared (en el hotel): se abre y se mira
        net.minecraft.core.BlockPos[] ciega = {null};
        sin("purgatorio3_puerta_pared_ir", 60, () -> {
            for (int r = 0; r < 50 && ciega[0] == null; r++)
                for (int cx = -r; cx <= r && ciega[0] == null; cx++)
                    for (int cz = -r; cz <= r && ciega[0] == null; cz++) {
                        if (Math.max(Math.abs(cx), Math.abs(cz)) != r || com.miscelanea.purgatorio.PurgatorioPlano.zona(0, cx, cz) != com.miscelanea.purgatorio.PurgatorioGenerador.Zona.HOTEL
                                || !com.miscelanea.purgatorio.PurgatorioGenerador.entero(0, cx, cz) || com.miscelanea.purgatorio.PurgatorioPlano.salida(0, cx, cz)) continue;
                        for (int k = 2; k < 4; k++)
                            if (!com.miscelanea.purgatorio.PurgatorioGenerador.habitacionTele(0, cx, cz, k) && com.miscelanea.purgatorio.PurgatorioPlano.hn(0, cx, cz, 180 + k) % 6 == 0) {
                                ciega[0] = new net.minecraft.core.BlockPos(cx * 16 + com.miscelanea.purgatorio.PurgatorioPlano.PARCELAS[k][0] + 3, 64, cz * 16 + 9);
                                break;
                            }
                    }
            RESUMEN.add("  purgatorio, puerta que da a la pared: " + (ciega[0] == null ? "NINGUNA" : ciega[0].toShortString()));
            if (ciega[0] == null) return;
            irA(ciega[0].north(2), 0, 12);
        });
        sin("purgatorio3_puerta_pared_abrir", 40, () -> {
            if (ciega[0] == null) return;
            var l = srv0.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            irA(ciega[0].north(2), 0, 12);
            srv0.execute(() -> {
                var s = l.getBlockState(ciega[0]);
                if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    ((net.minecraft.world.level.block.DoorBlock) s.getBlock()).setOpen(null, l, s, ciega[0], true);
            });
        });
        paso("5d_purgatorio_puerta_pared", 30, () -> {});
        // las cuatro zonas nuevas: dos fotos de cada una, su rincón raro y su sala grande
        String[][] nuevas = {{"PISCINA", "60_purgatorio_piscina", "PISCINA_OLIMPICA"}, {"APARCAMIENTO", "61_purgatorio_aparcamiento", null},
                {"COLEGIO", "62_purgatorio_colegio", "GIMNASIO"}, {"CENTRO", "63_purgatorio_centro", "ATRIO"}};
        for (String[] zn : nuevas) {
            var zona = com.miscelanea.purgatorio.PurgatorioGenerador.Zona.valueOf(zn[0]);
            sin("ir_" + zn[1], 60, () -> {
                var b = com.miscelanea.purgatorio.Purgatorio.buscar(zona);
                RESUMEN.add("  purgatorio, zona nueva " + zona + ": " + b.toShortString() + " (piso " + com.miscelanea.purgatorio.PurgatorioPlano.nivel(b.getY()) + ")");
                if (com.miscelanea.purgatorio.PurgatorioGenerador.zonaEn(b.getX(), b.getY(), b.getZ()) != zona) throw new IllegalStateException("no hay " + zona + " cerca");
                irA(b, 25, 6);
            });
            paso(zn[1], 40, () -> RESUMEN.add("  purgatorio, " + zn[1] + ": niebla " + String.format("%.1f", com.miscelanea.client.purgatorio.PurgatorioCliente.Niebla.distanciaActual())
                    + ", ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando()));
            paso(zn[1] + "_2", 30, () -> {
                mc.player.setYRot(mc.player.getYRot() + 150);
                mc.player.setXRot(10);
            });
            sin("ir_" + zn[1] + "_raro", 60, () -> {
                var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(zona);
                RESUMEN.add("  purgatorio, rincón raro de " + zona + ": " + (b == null ? "NINGUNO cerca" : b[0].toShortString()));
                if (b == null) throw new IllegalStateException("no hay rincón raro de " + zona + " cerca del origen");
                irMirando(b[0], b[1]);
            });
            paso(zn[1] + "_raro", 50, () -> {
                var b = com.miscelanea.purgatorio.Purgatorio.buscarRaro(zona);
                var lp = srv0.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
                RESUMEN.add("  purgatorio, rincón raro de " + zona + ": mira a " + (lp == null ? "?" : lp.getBlockState(b[1]).getBlock().getDescriptionId())
                        + ", ambiente " + com.miscelanea.client.purgatorio.PurgatorioCliente.Ambiente.sonando());
            });
            if (zn[2] == null) continue;
            var t = com.miscelanea.purgatorio.PurgatorioPlano.Tipo.valueOf(zn[2]);
            sin("ir_" + zn[1] + "_grande", 60, () -> {
                var g = com.miscelanea.purgatorio.Purgatorio.buscarGrande(t);
                RESUMEN.add("  purgatorio, sala grande " + t + ": " + (g == null ? "NINGUNA" : "piso " + g.n() + ", chunks " + g.cx0() + ", " + g.cz0()));
                if (g == null) throw new IllegalStateException("no hay " + t + " cerca del origen");
                irA(new net.minecraft.core.BlockPos(g.x0() + (g.w() / 2) * 16 + 8, com.miscelanea.purgatorio.PurgatorioPlano.base(g.n()), g.z0() + g.d() * 16 - 3), 180, 10);
            });
            paso(zn[1] + "_grande", 50, () -> {});
            sin("ir_" + zn[1] + "_grande_2", 40, () -> {
                var g = com.miscelanea.purgatorio.Purgatorio.buscarGrande(t);
                irA(new net.minecraft.core.BlockPos(g.x0() + 3, com.miscelanea.purgatorio.PurgatorioPlano.base(g.n()), g.z0() + g.d() * 16 - 3), -135, 12);
            });
            paso(zn[1] + "_grande_2", 40, () -> {});
        }
        // que se llega andando a una salida desde el piso de más abajo (por escaleras: el ascensor no se cuenta)
        sin("purgatorio3_alcanzable", 40, () -> {
            var l = srv0.getLevel(com.miscelanea.purgatorio.Purgatorio.DIMENSION);
            String mal = srv0.submit(() -> {
                String m = null;
                for (int n = 0; n < 3; n++) {
                    net.minecraft.core.BlockPos desde = null;
                    for (var z : com.miscelanea.purgatorio.PurgatorioGenerador.Zona.values())
                        if (desde == null) desde = com.miscelanea.purgatorio.Purgatorio.buscar(z, n, 6);
                    if (desde == null) continue;
                    long t0 = System.nanoTime();
                    var puerta = com.miscelanea.registry.ModBlocks.PUERTA_SALIDA.get();
                    int d = andar(l, desde, q -> l.getBlockState(q).is(puerta), 250000);
                    RESUMEN.add(String.format("  purgatorio, andando desde el piso %d (%s) hasta una salida: %s (%.1f s)", n, desde.toShortString(),
                            d < 0 ? "NO SE LLEGA" : d + " pasos", (System.nanoTime() - t0) / 1e9));
                    if (d < 0) m = "desde el piso " + n + " no se llega andando a ninguna salida";
                }
                return m;
            }).join();
            if (mal != null) throw new IllegalStateException(mal);
        });
        sin("purgatorio3_fin", 200, () -> {});
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
        purgatorio3();
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
    private static net.minecraft.world.entity.Entity escEntidad(UUID id) {
        return escJugador().serverLevel().getEntity(id);
    }

    private static void escApuntar(ServerPlayer sp, Vec3 a) {
        Vec3 d = a.subtract(sp.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), yaw, pitch);
    }

    /** Pone la escopeta en la mano con esta presión y estos cartuchos en la tolva, y apunta a {@code a}. */
    private static void escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho t, float presion, Vec3 a) {
        escServidor(sp -> {
            ItemStack s = sp.getMainHandItem();
            com.miscelanea.armasfuego.escopeta.Presion.ponerTolva(s, t, t == null ? 0 : 16);
            com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(s, presion);
            s.getOrCreateTag().remove(com.miscelanea.armasfuego.escopeta.Presion.DISPAROS);
            if (a != null) escApuntar(sp, a);
            if (escEntidad(escHusk) instanceof net.minecraft.world.entity.LivingEntity h) escVida = h.getHealth();
        });
    }

    private static int escTrigo() {
        var l = escJugador().serverLevel();
        int trigo = 0;
        for (var b : net.minecraft.core.BlockPos.betweenClosed(escTierra.offset(-2, 1, -2), escTierra.offset(2, 1, 2)))
            if (l.getBlockState(b).is(net.minecraft.world.level.block.Blocks.WHEAT)) trigo++;
        return trigo;
    }

    private static ItemStack escMano() {
        return Minecraft.getInstance().player.getMainHandItem();
    }

    private static Vec3 escHuskPos() {
        var h = escEntidad(escHusk);
        if (h == null) throw new IllegalStateException("no está la diana (husk)");
        return h.position().add(0, 1.0, 0);
    }

    private static void escopeta() {
        Minecraft mc = Minecraft.getInstance();
        sin("esc_poner", 40, () -> {
            mc.setScreen(null);
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            escServidor(sp -> {
                var l = sp.serverLevel();
                l.setWeatherParameters(6000, 0, false, false);   // sin lluvia: apaga el fuego y cambia la luz
                var mira = net.minecraft.core.Direction.fromYRot(sp.getYRot());
                var lado = mira.getClockWise();
                var o = sp.blockPosition();
                var aire = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.relative(lado, -9).relative(mira, -4).below(), o.relative(lado, 9).relative(mira, 12).above(18)))
                    l.setBlock(b, b.getY() < o.getY() ? net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState() : aire, 2);
                for (int dx = -9; dx <= 9; dx++)
                    for (int dy = 0; dy <= 6; dy++)
                        l.setBlock(o.relative(mira, 12).relative(lado, dx).above(dy), net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(o).inflate(30), e -> !(e instanceof net.minecraft.world.entity.player.Player))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                // la diana: un husk quieto (no se quema de día)
                var h = net.minecraft.world.entity.EntityType.HUSK.create(l);
                var ph = o.relative(mira, 6);
                h.moveTo(ph.getX() + 0.5, ph.getY(), ph.getZ() + 0.5, mira.toYRot() + 180, 0);
                h.setNoAi(true);
                h.setPersistenceRequired();
                h.setCustomName(net.minecraft.network.chat.Component.literal("Diana"));
                l.addFreshEntity(h);
                escHusk = h.getUUID();
                // un cerdo para el soplido y el aspirado
                var c = net.minecraft.world.entity.EntityType.PIG.create(l);
                var pc = o.relative(mira, 5).relative(lado, 4);
                c.moveTo(pc.getX() + 0.5, pc.getY(), pc.getZ() + 0.5, 0, 0);
                c.setPersistenceRequired();
                l.addFreshEntity(c);
                escCerdo = c.getUUID();
                // hoguera y vela apagadas
                escHoguera = o.relative(mira, 5).relative(lado, -3);
                escVela = escHoguera.relative(lado, -1);
                l.setBlock(escHoguera, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 3);
                l.setBlock(escVela, net.minecraft.world.level.block.Blocks.CANDLE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 4), 3);
                // tierra de cultivo (3x3) para las semillas
                escTierra = o.relative(mira, 4).relative(lado, -7).below();
                for (int a = -1; a <= 1; a++)
                    for (int b = -1; b <= 1; b++)
                        l.setBlock(escTierra.relative(mira, a).relative(lado, b), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
                sp.setGameMode(GameType.SURVIVAL);
                sp.removeAllEffects();
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 20 * 600, 0, false, false));
                sp.setHealth(sp.getMaxHealth());
                sp.getInventory().clearContent();
                sp.getInventory().setItem(0, new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get()));
                sp.getInventory().selected = 0;
                sp.connection.teleport(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, mira.toYRot(), 0F);
            });
        });
        paso("120_escopeta_mano", 20, () -> {
            if (!(escMano().getItem() instanceof com.miscelanea.armasfuego.escopeta.EscopetaPresionItem))
                throw new IllegalStateException("no tengo la escopeta de presión en la mano");
        });
        // ---- bombear con el clic derecho mantenido
        sin("esc_bombear", 10, () -> mantenerUso = true);
        paso("121_bombeando", 25, () -> {});
        sin("esc_soltar", 10, () -> {
            mantenerUso = false;
            mc.options.keyUse.setDown(false);
        });
        paso("122_tercera_persona", 20, () -> {
            float pr = com.miscelanea.armasfuego.escopeta.Presion.presion(escMano());
            RESUMEN.add(String.format("  escopeta: tras bombear ~2 s la presión es %.0f (zona %d)", pr, com.miscelanea.armasfuego.escopeta.Presion.zona(escMano())));
            if (pr < 25) throw new IllegalStateException("bombeando no sube la presión (" + pr + ")");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        });
        // ---- grava
        sin("esc_grava", 10, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.GRAVA, 100, escHuskPos());
        });
        paso("123_grava", 12, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_grava_chk", 5, () -> {
            var h = (net.minecraft.world.entity.LivingEntity) escEntidad(escHusk);
            ItemStack s = escJugador().getMainHandItem();
            RESUMEN.add(String.format("  escopeta: grava a 6 bloques quita %.1f de vida a la diana; presión después %.0f; tolva %d",
                    escVida - h.getHealth(), com.miscelanea.armasfuego.escopeta.Presion.presion(s), com.miscelanea.armasfuego.escopeta.Presion.tolvaN(s)));
            if (escVida - h.getHealth() < 3) throw new IllegalStateException("la grava casi no hace daño");
            if (com.miscelanea.armasfuego.escopeta.Presion.presion(s) > 0) throw new IllegalStateException("tras disparar sigue con presión");
            if (com.miscelanea.armasfuego.escopeta.Presion.tolvaN(s) != 15) throw new IllegalStateException("no ha gastado un cartucho");
            escServidor(sp -> h.setHealth(h.getMaxHealth()));
        });
        // ---- incendiario: a la diana y a la hoguera
        sin("esc_fuego", 10, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.INCENDIARIO, 100, escHuskPos()));
        paso("124_incendiario", 12, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_hoguera", 10, () -> {
            if (!escEntidad(escHusk).isOnFire()) throw new IllegalStateException("el incendiario no prende a la diana");
            escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.INCENDIARIO, 100, Vec3.atCenterOf(escHoguera).add(0, -0.25, 0));
        });
        paso("125_hoguera", 15, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_hoguera_chk", 5, () -> {
            var l = escJugador().serverLevel();
            boolean hog = l.getBlockState(escHoguera).getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
            boolean vel = l.getBlockState(escVela).getValue(net.minecraft.world.level.block.CandleBlock.LIT);
            RESUMEN.add("  escopeta: incendiario → hoguera encendida " + hog + ", vela encendida " + vel);
            if (!hog && !vel) throw new IllegalStateException("el incendiario no ha encendido ni la hoguera ni la vela");
        });
        // ---- escarcha: apaga a la diana, la congela y deja nieve en polvo en el suelo
        sin("esc_escarcha", 5, () -> {
            // lo congelado se va derritiendo (2 por tick): se vigila en cada tick si ha llegado a congelarse
            PI.remove("escarcha_frio");
            escVigilaEscarcha = true;
            escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.ESCARCHA, 100, escHuskPos().add(0, -0.9, 0));   // a los pies: más perdigones al suelo (con shaders a veces no caía ninguno)
        });
        paso("126_escarcha", 15, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        // si los perdigones no han dado a la diana (se reparten al azar; con mala suerte ardía aún), otro tiro
        sin("esc_escarcha_otra", 5, () -> {
            var h = escEntidad(escHusk);
            if (h.isOnFire() || h.getTicksFrozen() <= 0 && !PI.containsKey("escarcha_frio")) {
                RESUMEN.add("  escopeta: el primer tiro de escarcha no ha dado bien; otro");
                PI.put("escarcha_otra", true);
                escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.ESCARCHA, 100, escHuskPos().add(0, -0.9, 0));
            } else PI.remove("escarcha_otra");
        });
        sin("esc_escarcha_tiro2", 15, () -> {
            if (Boolean.TRUE.equals(PI.get("escarcha_otra"))) com.miscelanea.client.armasfuego.escopeta.EscopetaCliente.disparar();
        });
        sin("esc_escarcha_chk", 5, () -> {
            var h = escEntidad(escHusk);
            int nieve = 0;
            var l = escJugador().serverLevel();
            for (var b : net.minecraft.core.BlockPos.betweenClosed(h.blockPosition().offset(-6, -1, -6), h.blockPosition().offset(6, 2, 6)))
                if (l.getBlockState(b).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) nieve++;
            RESUMEN.add("  escopeta: escarcha → diana ardiendo " + h.isOnFire() + ", congelada " + h.getTicksFrozen() + " ticks, nieve en polvo " + nieve);
            if (h.isOnFire()) throw new IllegalStateException("la escarcha no apaga el fuego");
            escVigilaEscarcha = false;
            if (h.getTicksFrozen() <= 0 && !PI.containsKey("escarcha_frio")) throw new IllegalStateException("la escarcha no congela");
            if (nieve == 0) throw new IllegalStateException("la escarcha no deja nieve en polvo");
            escServidor(sp -> {
                for (var b : net.minecraft.core.BlockPos.betweenClosed(h.blockPosition().offset(-6, -1, -6), h.blockPosition().offset(6, 2, 6)))
                    if (l.getBlockState(b).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                h.setTicksFrozen(0);
            });
        });
        // ---- miel
        sin("esc_miel", 5, () -> {
            // el pringado dura unos 6 s y con shaders (4 fotogramas por paso a 1-2 fps) se puede pasar antes de mirarlo:
            // se vigila en cada tick del servidor
            PI.remove("miel_visto");
            escVigilaMiel = true;
            escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.MIEL, 100, escHuskPos());
        });
        paso("127_miel", 12, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        // si ningún perdigón de miel ha dado (se reparten al azar), otro tiro
        sin("esc_miel_otra", 5, () -> {
            var h = (net.minecraft.world.entity.LivingEntity) escEntidad(escHusk);
            if (!h.hasEffect(com.miscelanea.registry.ModEffects.PRINGADO.get()) && !PI.containsKey("miel_visto")) {
                RESUMEN.add("  escopeta: el primer tiro de miel no ha pringado; otro");
                PI.put("miel_otra", true);
                escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.MIEL, 100, escHuskPos());
            } else PI.remove("miel_otra");
        });
        sin("esc_miel_tiro2", 12, () -> {
            if (Boolean.TRUE.equals(PI.get("miel_otra"))) com.miscelanea.client.armasfuego.escopeta.EscopetaCliente.disparar();
        });
        sin("esc_miel_chk", 5, () -> {
            var h = (net.minecraft.world.entity.LivingEntity) escEntidad(escHusk);
            escVigilaMiel = false;
            if (!h.hasEffect(com.miscelanea.registry.ModEffects.PRINGADO.get()) && !PI.containsKey("miel_visto")) throw new IllegalStateException("la miel no pringa");
            escServidor(sp -> h.setHealth(h.getMaxHealth()));
        });
        // ---- sal: sin daño
        sin("esc_sal", 5, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.SAL, 100, escHuskPos()));
        paso("128_sal", 12, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_sal_chk", 5, () -> {
            var h = (net.minecraft.world.entity.LivingEntity) escEntidad(escHusk);
            RESUMEN.add(String.format("  escopeta: sal → vida de la diana %.1f → %.1f", escVida, h.getHealth()));
            if (h.getHealth() < escVida) throw new IllegalStateException("la sal hace daño");
        });
        // ---- semillas: siembra la tierra de cultivo
        // la tierra de cultivo sin agua se seca y vuelve a tierra con los ticks al azar: se rehace justo antes del tiro
        sin("esc_semillas_tierra", 2, () -> escServidor(sp -> {
            var l = sp.serverLevel();
            for (var b : net.minecraft.core.BlockPos.betweenClosed(escTierra.offset(-1, 0, -1), escTierra.offset(1, 0, 1))) {
                l.setBlock(b, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
                if (!l.getBlockState(b.above()).isAir()) l.setBlock(b.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }));
        sin("esc_semillas", 5, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.SEMILLAS, 100, Vec3.atCenterOf(escTierra).add(0, 0.45, 0)));
        paso("129_semillas", 15, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_semillas_otra", 5, () -> {
            // los perdigones se reparten al azar y, a veces, ninguna semilla caía en la tierra de cultivo: otro tiro
            if (escTrigo() == 0) {
                RESUMEN.add("  escopeta: el primer tiro de semillas no ha sembrado; otro");
                PI.put("semillas_otra", true);
                escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.SEMILLAS, 100, Vec3.atCenterOf(escTierra).add(0, 0.45, 0));
            } else PI.remove("semillas_otra");
        });
        sin("esc_semillas_tiro2", 15, () -> {
            if (Boolean.TRUE.equals(PI.get("semillas_otra"))) com.miscelanea.client.armasfuego.escopeta.EscopetaCliente.disparar();
        });
        sin("esc_semillas_chk", 5, () -> {
            int trigo = escTrigo();
            RESUMEN.add("  escopeta: semillas → " + trigo + " matas de trigo sembradas");
            if (trigo == 0) throw new IllegalStateException("las semillas no siembran");
        });
        // ---- confeti
        sin("esc_confeti", 5, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.CONFETI, 100, escHuskPos()));
        paso("130_confeti", 6, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        // ---- soplido (tolva vacía): empuja al cerdo
        sin("esc_soplido_cerdo", 5, () -> escServidor(sp -> {
            // el cerdo se pasea: se le pone a 4 bloques delante
            var c = escEntidad(escCerdo);
            Vec3 m = Vec3.directionFromRotation(0, sp.getYRot());
            c.teleportTo(sp.getX() + m.x * 4, sp.getY(), sp.getZ() + m.z * 4);
            c.setDeltaMovement(Vec3.ZERO);
        }));
        sin("esc_soplido", 3, () -> {
            var c = escEntidad(escCerdo);
            escCerdoPos = c.position();
            escCargar(null, 90, c.position().add(0, 0.5, 0));
        });
        paso("131_soplido", 10, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_soplido_chk", 5, () -> {
            double d = escEntidad(escCerdo).position().distanceTo(escCerdoPos);
            RESUMEN.add(String.format("  escopeta: el soplido mueve al cerdo %.1f bloques", d));
            if (d < 1.2) throw new IllegalStateException("el soplido no empuja al cerdo");
        });
        // ---- salto escopeta: mirando abajo con mucha presión
        sin("esc_salto", 10, () -> {
            escY = mc.player.getY();
            PI.remove("salto_max");
            escVigilaSalto = true;
            escCargar(null, 100, null);
            escServidor(sp -> sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 89F));
        });
        sin("esc_salto_tercera", 2, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        paso("132_salto", 7, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_salto_chk", 40, () -> {
            // lo más alto que ha llegado (con shaders, a pocos fps, a veces ya había bajado al mirarlo)
            escVigilaSalto = false;
            double sube = Math.max(mc.player.getY(), PI.get("salto_max") instanceof Double d ? d : -1e9) - escY;
            RESUMEN.add(String.format("  escopeta: el salto escopeta sube %.1f bloques en 7 ticks", sube));
            if (sube < 1.5) throw new IllegalStateException("el soplido hacia abajo con mucha presión no lanza");
        });
        // ---- frenar la caída: desde 14 bloques, soplido hacia abajo antes de llegar
        sin("esc_caida", 14, () -> {
            escCargar(null, 70, null);
            escServidor(sp -> sp.connection.teleport(sp.getX(), sp.getY() + 14, sp.getZ(), sp.getYRot(), 89F));
        });
        sin("esc_frenar", 4, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_frenar_chk", 40, () -> {
            float caida = escJugador().fallDistance;
            RESUMEN.add(String.format("  escopeta: tras el soplido hacia abajo, velocidad %.2f y caída acumulada %.1f", mc.player.getDeltaMovement().y, caida));
            if (caida > 3) throw new IllegalStateException("el soplido hacia abajo no frena la caída");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        });
        // ---- aspirar (agachado): objetos tirados alrededor
        sin("esc_tirar", 10, () -> escServidor(sp -> {
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 20F);
            var l = sp.serverLevel();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                var it = new net.minecraft.world.entity.item.ItemEntity(l, sp.getX() + Math.cos(a) * 4.5, sp.getY() + 0.3, sp.getZ() + Math.sin(a) * 4.5,
                        new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 4));
                it.setPickUpDelay(10);
                l.addFreshEntity(it);
            }
            escCosas = 0;
        }));
        sin("esc_aspirar", 10, () -> {
            escAgachado = true;
            mantenerUso = true;
        });
        paso("133_aspirar", 40, () -> {});
        sin("esc_aspirar_chk", 5, () -> {
            escAgachado = false;
            mantenerUso = false;
            mc.options.keyShift.setDown(false);
            mc.options.keyUse.setDown(false);
            int n = escJugador().getInventory().countItem(net.minecraft.world.item.Items.COBBLESTONE);
            RESUMEN.add("  escopeta: aspirando se han recogido " + n + " de 32 piedras");
            if (n < 16) throw new IllegalStateException("aspirar no trae los objetos");
        });
        // ---- revienta: bombear con la aguja en el tope
        sin("esc_tope", 5, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.GRAVA, 99.5F, null));
        sin("esc_reventar", 20, () -> mantenerUso = true);
        paso("134_reventada", 15, () -> {
            mantenerUso = false;
            mc.options.keyUse.setDown(false);
            int r = com.miscelanea.armasfuego.escopeta.Presion.reventada(escMano(), mc.level);
            RESUMEN.add("  escopeta: reventada, humeando " + r + " ticks más; enfriamiento " + mc.player.getCooldowns().isOnCooldown(escMano().getItem()));
            if (r <= 0) throw new IllegalStateException("pasándose de la roja no revienta");
        });
        // ---- válvula de seguridad: en el tope suelta el aire en vez de reventar
        sin("esc_valvula", 5, () -> escServidor(sp -> {
            ItemStack s = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get());
            s.enchant(com.miscelanea.registry.ModEnchantments.ESCOPETA_VALVULA.get(), 1);
            s.enchant(com.miscelanea.registry.ModEnchantments.ESCOPETA_DOBLE_CAMARA.get(), 1);
            com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(s, 95);
            sp.getInventory().setItem(0, s);
            // el enfriamiento de la reventada es del objeto (de todas las escopetas de presión): fuera para seguir
            sp.getCooldowns().removeCooldown(s.getItem());
        }));
        // mirando al cielo: tras el empujón de la reventada a veces quedaba mirando un cofre y el clic derecho mantenido
        // lo abría (y con el cofre abierto en el servidor, el inventario ya no llegaba al cliente en los pasos del rifle)
        sin("esc_valvula_bombear", 30, () -> {
            mc.player.setXRot(-75F);
            mantenerUso = true;
        });
        sin("esc_valvula_chk", 5, () -> {
            mantenerUso = false;
            mc.options.keyUse.setDown(false);
            ItemStack s = escMano();
            float pr = com.miscelanea.armasfuego.escopeta.Presion.presion(s);
            if (com.miscelanea.armasfuego.escopeta.Presion.reventada(s, mc.level) > 0) throw new IllegalStateException("con Válvula de seguridad revienta");
            if (Math.abs(pr - com.miscelanea.armasfuego.escopeta.Presion.tope(s)) > 1.5) throw new IllegalStateException("la válvula no deja la presión en el tope (" + pr + ")");
        });
        // ---- doble cámara: dos disparos con una bombeada
        sin("esc_doble", 5, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.GRAVA, 60, escHuskPos()));
        sin("esc_doble_1", 15, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_doble_2", 15, () -> {
            ItemStack s = escJugador().getMainHandItem();
            if (com.miscelanea.armasfuego.escopeta.Presion.disparosQuedan(s) != 1) throw new IllegalStateException("con Doble cámara no queda un segundo disparo");
            com.miscelanea.client.armasfuego.escopeta.EscopetaCliente.disparar();
        });
        sin("esc_doble_chk", 5, () -> {
            ItemStack s = escJugador().getMainHandItem();
            if (com.miscelanea.armasfuego.escopeta.Presion.presion(s) > 0 || com.miscelanea.armasfuego.escopeta.Presion.tolvaN(s) != 14)
                throw new IllegalStateException("el segundo disparo de la Doble cámara no ha salido");
        });
        // ---- encantamientos, recetas, tolva, panal y hacha
        sin("esc_encantamientos", 5, () -> {
            var g = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get());
            var esp = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            var todos = java.util.List.of(com.miscelanea.registry.ModEnchantments.ESCOPETA_COMPRESOR.get(), com.miscelanea.registry.ModEnchantments.ESCOPETA_VALVULA.get(),
                    com.miscelanea.registry.ModEnchantments.ESCOPETA_DOBLE_CAMARA.get(), com.miscelanea.registry.ModEnchantments.ESCOPETA_FUELLE.get(),
                    com.miscelanea.registry.ModEnchantments.ESCOPETA_RECICLAJE.get());
            for (var en : todos) {
                if (!en.canEnchant(g)) throw new IllegalStateException(en.getDescriptionId() + " no vale para la escopeta");
                if (en.canEnchant(esp)) throw new IllegalStateException(en.getDescriptionId() + " vale para una espada");
            }
            var mesa = net.minecraft.world.item.enchantment.EnchantmentHelper.getAvailableEnchantmentResults(30, g, false);
            int salen = 0;
            for (var en : todos) if (mesa.stream().anyMatch(i -> i.enchantment == en)) salen++;
            RESUMEN.add("  escopeta: en la mesa a nivel 30 pueden salir " + salen + " de 5 encantamientos propios (" + mesa.size() + " en total)");
            if (salen < 5) throw new IllegalStateException("no salen todos los encantamientos propios en la mesa");
            if (todos.get(0).isCompatibleWith(todos.get(1))) throw new IllegalStateException("Compresor y Válvula de seguridad son compatibles");
            g.enchant(todos.get(0), 2);
            if (Math.abs(com.miscelanea.armasfuego.escopeta.Presion.tope(g) - 120) > 0.01) throw new IllegalStateException("Compresor II no sube el tope a 120");
            // recetas
            var rm = escJugador().serverLevel().getRecipeManager();
            int recetas = 0;
            for (String r : new String[]{"escopeta_cartucho_grava", "escopeta_cartucho_incendiario", "escopeta_cartucho_escarcha",
                    "escopeta_cartucho_miel", "escopeta_cartucho_sal", "escopeta_cartucho_semillas", "escopeta_cartucho_confeti"})
                if (rm.byKey(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, r)).isPresent()) recetas++;
            if (recetas != 7) throw new IllegalStateException("faltan recetas de los cartuchos (" + recetas + "/7)");
            // la escopeta ya no se fabrica: sale en los barcos hundidos
            if (rm.byKey(new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "escopeta_presion")).isPresent())
                throw new IllegalStateException("la escopeta todavía tiene receta");
            // tolva, panal y hacha (clic derecho en el inventario)
            var p = escJugador();
            ItemStack cart = new ItemStack(com.miscelanea.armasfuego.escopeta.TipoCartucho.MIEL.item(), 20);
            com.miscelanea.armasfuego.escopeta.EscopetaPresionItem.usarEnInventario(g, cart, p, null);
            if (com.miscelanea.armasfuego.escopeta.Presion.tolvaN(g) != 16 || cart.getCount() != 4) throw new IllegalStateException("la tolva no se carga bien");
            com.miscelanea.armasfuego.escopeta.Presion.ponerOxido(g, 3);
            com.miscelanea.armasfuego.escopeta.EscopetaPresionItem.usarEnInventario(g, new ItemStack(net.minecraft.world.item.Items.IRON_AXE), p, null);
            if (com.miscelanea.armasfuego.escopeta.Presion.oxido(g) != 2) throw new IllegalStateException("el hacha no rasca el óxido");
            com.miscelanea.armasfuego.escopeta.EscopetaPresionItem.usarEnInventario(g, new ItemStack(net.minecraft.world.item.Items.HONEYCOMB), p, null);
            if (!com.miscelanea.armasfuego.escopeta.Presion.encerada(g)) throw new IllegalStateException("el panal no encera");
        });
        // ---- los cuatro estados del cobre y la aguja en la roja
        sin("esc_estados", 10, () -> escServidor(sp -> {
            for (int o = 0; o < 4; o++) {
                ItemStack s = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get());
                com.miscelanea.armasfuego.escopeta.Presion.ponerOxido(s, o);
                com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(s, 20 + o * 25);
                sp.getInventory().setItem(o, s);
            }
            for (var t : com.miscelanea.armasfuego.escopeta.TipoCartucho.values())
                sp.getInventory().setItem(4 + t.ordinal(), new ItemStack(t.item(), 16));
            ItemStack s = sp.getInventory().getItem(3);
            com.miscelanea.armasfuego.escopeta.Presion.ponerTolva(s, com.miscelanea.armasfuego.escopeta.TipoCartucho.INCENDIARIO, 9);
            com.miscelanea.armasfuego.escopeta.Presion.ponerPresion(s, 92);
            sp.getInventory().selected = 3;
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(3));
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 10F);
        }));
        paso("135_cobre_y_roja", 10, () -> {});
        paso("136_inventario", 20, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        sin("esc_fin", 10, () -> {
            mc.setScreen(null);
            mantenerUso = false;
            escAgachado = false;
            mc.options.keyUse.setDown(false);
            mc.options.keyShift.setDown(false);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            escServidor(sp -> {
                sp.removeAllEffects();
                sp.setGameMode(GameType.CREATIVE);
            });
        });
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!ACTIVA || e.phase != TickEvent.Phase.END || terminada) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // el puntero láser: el clic derecho mantenido
        if (mantenerUso) mc.options.keyUse.setDown(true);
        // escopeta de presión: agachado (aspirar)
        if (escAgachado) mc.options.keyShift.setDown(true);
        if (rifleSalto) mc.options.keyJump.setDown(true);
        if (rifleVigilaColgado && mc.player.getVehicle() instanceof com.miscelanea.armasfuego.rifle.GanchoTirolina) {
            if (rifleColgadoTicks++ == 0) rifleColgadoFotograma = fotogramas;
            if (!rifleColgadoVisto && rifleColgadoTicks >= 6 && fotogramas - rifleColgadoFotograma >= 2) {
                rifleColgadoVisto = true;
                rifleSalto = false;
                mc.options.keyJump.setDown(false);
                capturar("149_colgado");
            }
        }
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
        if (sinToasts) mc.getToasts().clear();
        if (sinAvisos) {
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);   // «… ha conseguido el progreso …» también es texto claro
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
