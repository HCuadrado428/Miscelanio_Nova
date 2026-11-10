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
    private static final String[] LOGROS = ("raiz,movil/movil,movil/foto,movil/video,movil/minegram,movil/flash,movil/mensaje,movil/llamada,movil/videollamada,movil/sin_cobertura,movil/numero_oculto,movil/bizum,arcade/ficha,arcade/partida,arcade/record,arcade/pleno,arcade/canal,feria/noria,feria/choque,feria/tirolina,feria/trampolin,feria/campana,feria/jackpot,mesa/victoria,mesa/ajedrez,mesa/parchis,mesa/calavera,mesa/billar,mesa/futbolin,baile/pista,baile/videoclip,baile/walken,baile/nota_s,baile/nota_e,pociones/gigante,pociones/enano,pociones/borrachera,pociones/tartamudeo,pociones/ventilador,pociones/eco,pociones/laboratorio,pociones/fantasma,bromas/uwu,bromas/australia,bromas/estatica,sala/lazo,sala/grilletes,sala/engrilletado,sala/ganzua,sala/tomatazo,dream/esencia,dream/mordisco,dream/escapar,dream/veneno,dream/medianoche,armas/gambito,armas/laser,armas/hacha,armas/aspira,armas/uber,armas/cofre,armas/ruleta,armas/ruleta_fogueo,armas/duelo,armas/gallina,armas/sedante,armas/encasquillado,armas/salto,armas/reventon,armas/granjero,armas/aspirar_piedras,armas/cuerda,armas/francotirador,armas/gatling,armas/gatling_calor,armas/gatling_cubo,armas/winchester,armas/winchester_giro,armas/derringer,armas/violin,armas/thompson_tambor,armas/mosquete,armas/bayoneta,armas/polvora_mojada,armas/canon,armas/canon_revienta,armas/tiempo_bala,armas/goma,armas/pintura,armas/atasco,armas/plata,armas/oro,armas/carta,armas/soaker,armas/palomitas,armas/cadena,armas/feria,armas/patina,armas/gatling_tolva,armas/gatling_torreta,armas/gatling_vagoneta,armas/gatling_barco,armas/guisantes,armas/winchester_mezcla,armas/winchester_hielo,armas/derringer_manga,armas/derringer_humo,armas/paganini,armas/tinta,armas/bayoneta_netherita,armas/arpon,armas/yunque,armas/castillo_arena,armas/rebote,armas/coleccionista,varios/paella,varios/bacalao,varios/ocarina").split(",");
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
    private static volatile boolean piVigilaBrillo, escVigilaMiel, a2VigilaGatling, escVigilaSalto;
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
            StringBuilder mal = new StringBuilder();
            int n = 0;
            java.util.List<String> nombres = new java.util.ArrayList<>(List.of("estuche_violin", "bala_derringer", "bola_mosquete", "cinta_gatling",
                    "cargador_thompson", "revolver_tiempo", "bala_pintura", "bala_pintura_punta", "bala_plata", "bala_dinamita", "bala_oro", "carta_marcada",
                    "tambor_trazador", "tambor_agua", "cinta_incendiaria", "cinta_palomitas", "metralla", "bola_cadena", "bolano_incendiario", "bolano_fuegos"));
            for (String r : List.of("gatling", "winchester", "derringer", "thompson", "mosquete", "canon_mano")) {
                nombres.add(r);
                for (int o = 1; o < 4; o++) nombres.add(r + "_ox" + o);
            }
            for (String m : List.of("g_acero", "g_madera", "g_cuero", "g_nacar", "g_mecha", "g_brasa", "g_terciopelo", "g_boca", "g_boca2", "g_tambor", "g_tolva"))
                nombres.add("armas3d/" + m);
            for (String t : nombres) {
                var rl = new net.minecraft.resources.ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + t + ".png");
                try (var in = mc.getResourceManager().open(rl); var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                    if (img.getWidth() != 16 || img.getHeight() != 16) mal.append(t).append(" ").append(img.getWidth()).append("x").append(img.getHeight()).append("; ");
                    n++;
                } catch (Exception ex) {
                    mal.append(t).append(" falta; ");
                }
            }
            a2Comprobar("texturas de la segunda tanda a 16x16", mal.length() == 0, mal.length() == 0 ? n + " a 16x16" : mal.toString());
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
        sin("a2u_winchester_dep_ok", 2, () -> a2Comprobar("la Winchester mezcla las balas en el depósito",
                "[revolver_bala, bala_hielo, revolver_dardo, bala_plata, revolver_tiempo]".equals(PI.get("dep")) && Boolean.TRUE.equals(PI.get("dep_logro")),
                "depósito " + PI.get("dep") + ", logro " + PI.get("dep_logro")));
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
        // ---------------------------------------------------------------- el violín
        sin("a2u_violin", 6, () -> {
            a2Mano(new ItemStack(com.miscelanea.registry.ModItems.ESTUCHE_VIOLIN.get()));
            PI.put("v0", com.miscelanea.armasfuego.oeste.EstucheViolinItem.tocadas);
        });
        sin("a2u_violin_arriba", 6, () -> {
            piSrv(sp -> sp.setXRot(-70F));
            mc.player.setXRot(-70F);
            piDisparar();
        });
        sin("a2u_violin_arriba_ver", 2, () -> PI.put("v_alta", com.miscelanea.armasfuego.oeste.EstucheViolinItem.ultimaNota));
        paso("285_violin", 6, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            piSrv(sp -> sp.setXRot(60F));
            mc.player.setXRot(60F);
            piDisparar();
        });
        sin("a2u_violin_ver", 2, () -> PI.put("v_baja", com.miscelanea.armasfuego.oeste.EstucheViolinItem.ultimaNota));
        sin("a2u_violin_ok", 2, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            int n = com.miscelanea.armasfuego.oeste.EstucheViolinItem.tocadas - (PI.get("v0") instanceof Integer i ? i : 0);
            int alta = PI.get("v_alta") instanceof Integer i ? i : -1, baja = PI.get("v_baja") instanceof Integer i ? i : -1;
            a2Comprobar("el estuche vacío toca notas: aguda mirando arriba, grave mirando abajo", n >= 2 && alta >= 18 && baja <= 6 && baja >= 0,
                    n + " notas, arriba " + alta + ", abajo " + baja);
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
        paso("288_canon_yunque", 10, () -> a2Vista(2.6, 2.6, 0.8));
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
        sin("esc_escarcha", 5, () -> escCargar(com.miscelanea.armasfuego.escopeta.TipoCartucho.ESCARCHA, 100, escHuskPos().add(0, -0.9, 0)));   // a los pies: más perdigones al suelo (con shaders a veces no caía ninguno)
        paso("126_escarcha", 15, com.miscelanea.client.armasfuego.escopeta.EscopetaCliente::disparar);
        sin("esc_escarcha_chk", 5, () -> {
            var h = escEntidad(escHusk);
            int nieve = 0;
            var l = escJugador().serverLevel();
            for (var b : net.minecraft.core.BlockPos.betweenClosed(h.blockPosition().offset(-6, -1, -6), h.blockPosition().offset(6, 2, 6)))
                if (l.getBlockState(b).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) nieve++;
            RESUMEN.add("  escopeta: escarcha → diana ardiendo " + h.isOnFire() + ", congelada " + h.getTicksFrozen() + " ticks, nieve en polvo " + nieve);
            if (h.isOnFire()) throw new IllegalStateException("la escarcha no apaga el fuego");
            if (h.getTicksFrozen() <= 0) throw new IllegalStateException("la escarcha no congela");
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
