package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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

    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (ACTIVA && e.phase == TickEvent.Phase.END) fotogramas++;
    }
    private static boolean terminada;
    private static net.minecraft.core.BlockPos ENCANTOS_MESA = net.minecraft.core.BlockPos.ZERO;
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
        // encantamientos estilo Enchancement: limpiar lo prohibido, mesa de elegir y sin durabilidad
        sin("encantos_limpiar", 5, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var E = net.minecraft.world.item.enchantment.Enchantments.class;
                ItemStack espada = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 10);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1);
                espada.enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, 7);
                ItemStack peto = new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE);
                peto.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 7);
                peto.enchant(net.minecraft.world.item.enchantment.Enchantments.THORNS, 3);
                ItemStack libro = com.miscelanea.encantos.MesaEncantar.libro(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
                sp.getInventory().setItem(3, espada);
                sp.getInventory().setItem(4, peto);
                sp.getInventory().setItem(5, libro);
                int xp = sp.totalExperience;
                com.miscelanea.encantos.Reglas.limpiarInventario(sp);
                var m1 = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(sp.getInventory().getItem(3));
                var m2 = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(sp.getInventory().getItem(4));
                var m3 = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(sp.getInventory().getItem(5));
                RESUMEN.add("  encantos: espada " + m1 + " · peto " + m2 + " · libro " + m3 + " · xp +" + (sp.totalExperience - xp)
                        + " · espada con durabilidad: " + sp.getInventory().getItem(3).isDamageableItem());
                boolean bien = m1.size() == 1 && m1.getOrDefault(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, 0) == 3 && m2.isEmpty()
                        && m3.size() == 1 && m3.keySet().iterator().next() instanceof com.miscelanea.encantos.EncantoMisc
                        && sp.totalExperience > xp && !sp.getInventory().getItem(3).isDamageableItem();
                if (!bien) RESUMEN.add("  FALLO: la limpieza de encantamientos no ha salido como debía");
            });
        });
        sin("encantos_mesa", 30, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var l = sp.serverLevel();
                var dir = sp.getDirection();
                var mesa = sp.blockPosition().relative(dir, 3);
                ENCANTOS_MESA = mesa;
                for (var b : net.minecraft.core.BlockPos.betweenClosed(mesa.offset(-2, 0, -2), mesa.offset(2, 1, 2)))
                    l.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                l.setBlockAndUpdate(mesa, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
                var est = mesa.relative(dir, 2);
                l.setBlockAndUpdate(est, net.minecraft.world.level.block.Blocks.CHISELED_BOOKSHELF.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dir.getOpposite()));
                if (l.getBlockEntity(est) instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity be) {
                    be.setItem(0, com.miscelanea.encantos.MesaEncantar.libro(com.miscelanea.encantos.Encantos.VAMPIRO.get()));
                    be.setItem(1, com.miscelanea.encantos.MesaEncantar.libro(com.miscelanea.encantos.Encantos.BERSERKER.get()));
                    be.setItem(2, com.miscelanea.encantos.MesaEncantar.libro(com.miscelanea.encantos.Encantos.ESCARCHA.get()));
                    be.setItem(3, com.miscelanea.encantos.MesaEncantar.libro(com.miscelanea.encantos.Encantos.ADRENALINA.get()));
                    be.setItem(4, com.miscelanea.encantos.MesaEncantar.libro(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
                }
                sp.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                sp.getInventory().selected = 0;
                com.miscelanea.encantos.MesaEncantar.abrir(sp, mesa);
            });
        });
        paso("60_mesa_encantar", 20, () -> {
            if (!(mc.screen instanceof com.miscelanea.client.encantos.MesaEncantarScreen)) throw new IllegalStateException("no se ha abierto la mesa de elegir");
        });
        sin("encantos_elegir", 20, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                com.miscelanea.encantos.MesaEncantar.elegir(sp, ENCANTOS_MESA, "miscelanea:vampiro");
                com.miscelanea.encantos.MesaEncantar.elegir(sp, ENCANTOS_MESA, "miscelanea:berserker");
                com.miscelanea.encantos.MesaEncantar.elegir(sp, ENCANTOS_MESA, "miscelanea:escarcha");    // ya no cabe (límite 2)
                var m = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(sp.getMainHandItem());
                RESUMEN.add("  mesa: la espada tiene " + m.keySet().stream().map(e -> e.getDescriptionId().replace("enchantment.", "")).toList());
                if (m.size() != 2 || !m.containsKey(com.miscelanea.encantos.Encantos.VAMPIRO.get())) RESUMEN.add("  FALLO: la mesa de elegir no ha encantado bien");
            });
        });
        paso("61_mesa_llena", 20, () -> {});
        sin("encantos_fin", 5, () -> mc.setScreen(null));
        sin("cerrar", 10, () -> {
            mc.setScreen(null);
            RESUMEN.add("  cara propia (la de su modelo) guardada: " + (CarasCliente.tiene(mc.player.getUUID()) ? "sí" : "NO"));
        });
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
                for (var b : net.minecraft.core.BlockPos.betweenClosed(o.offset(-3, 0, -26), o.offset(11, 10, 4)))
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
                sp.teleportTo(montanaO.getX() + 8.5, montanaO.getY() + 2, montanaO.getZ() - 17.5);
                sp.setYRot(90);
                sp.setYHeadRot(90);
                sp.setXRot(0);
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
                mc.player.setXRot(-4);
                return false;
            };
        });
        for (int i = 0; i < 12; i++) {
            int ii = i;
            paso(String.format("33_montana_rizo_%02d", i), 2, () -> {
                mc.player.setYRot(90);
                mc.player.setXRot(-4);
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
        sin("montana_rizo_fin", 2, () -> RESUMEN.add("  montaña, trozos (con el rizo): " + MONTANA_VISTO));
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
            sp.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).putLong("miscelanea_purgatorio_t", l.getGameTime());
            sp.teleportTo(l, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, yaw, pitch);
        });
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
            irA(com.miscelanea.purgatorio.Purgatorio.buscar(com.miscelanea.purgatorio.PurgatorioGenerador.Zona.FERIA), -35, 20);
        });
        paso("40_purgatorio_feria", 30, () -> {});
        paso("41_purgatorio_feria_2", 30, () -> mc.player.setYRot(mc.player.getYRot() + 120));
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
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (!ACTIVA || e.phase != TickEvent.Phase.END || terminada) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // que MC-Runtime-Test no dé la prueba por acabada mientras sigue esto
        if (mc.player.tickCount > 60) mc.player.tickCount = 60;
        if (++ticksEnMundo < 40) return;
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
