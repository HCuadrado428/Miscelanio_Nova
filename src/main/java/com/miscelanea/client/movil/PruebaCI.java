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
    private static int paso = -1, espera, ticksEnMundo, fallos;
    /** Fotogramas dibujados: con gráficos por software (y más con shaders) puede haber muy pocos por tick. */
    private static int fotogramas, fotogramasPaso;

    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (ACTIVA && e.phase == TickEvent.Phase.END) fotogramas++;
    }
    private static boolean terminada;
    private static net.minecraft.core.BlockPos ENCANTOS_MESA = net.minecraft.core.BlockPos.ZERO;
    private static UUID SEDANTE_CERDO = new UUID(0, 0);
    private static boolean SEDANTE_OK;
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
        // sedante de suavis: un cerdo pinchado se duerme; luego se lo pincha uno mismo
        sin("sedante_cerdo", 5, () -> {
            mc.setScreen(null);
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var cerdo = net.minecraft.world.entity.EntityType.PIG.create(sp.serverLevel());
                var mira = sp.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5);
                cerdo.moveTo(sp.getX() + mira.x, sp.getY(), sp.getZ() + mira.z, 0, 0);
                boolean puesto = sp.serverLevel().addFreshEntity(cerdo);
                RESUMEN.add("  sedante: cerdo puesto " + puesto + " en " + cerdo.blockPosition().toShortString() + " (jugador en " + sp.blockPosition().toShortString() + ")");
                com.miscelanea.sedante.Sedante.pinchar(cerdo, sp, false);
                SEDANTE_CERDO = cerdo.getUUID();
            });
        });
        // el servidor integrado va lento en la prueba: se mira varias veces (hasta ~20 s)
        for (int k = 0; k < 8; k++) {
            int kk = k;
            sin("sedante_espera_" + k, 50, () -> {
                if (SEDANTE_OK) return;
                var c = mc.getSingleplayerServer().overworld().getEntity(SEDANTE_CERDO);
                SEDANTE_OK = c instanceof net.minecraft.world.entity.LivingEntity le && com.miscelanea.tortura.Lazo.estado(le) == com.miscelanea.tortura.Lazo.DERRIBADO;
                if (kk == 0 || kk == 7) RESUMEN.add("  sedante: cerdo " + (c == null ? "no existe" : c.isAlive() ? "vivo" : "muerto") + " en " + (c == null ? "-" : c.blockPosition().toShortString()));
            });
        }
        paso("50_sedante_cerdo", 10, () -> {
            RESUMEN.add("  sedante: el cerdo se ha dormido: " + (SEDANTE_OK ? "sí" : "NO"));
            if (!SEDANTE_OK) throw new IllegalStateException("el cerdo no se ha dormido");
        });
        sin("sedante_yo", 40, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) com.miscelanea.sedante.Sedante.pinchar(sp, sp, false);
            });
        });
        paso("51_sedante_dormido", 10, () -> {
            var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            RESUMEN.add("  sedante: me he dormido: " + (sp != null && com.miscelanea.sedante.Sedante.dormido(sp) ? "sí" : "NO"));
        });
        sin("sedante_despertar", 5, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) com.miscelanea.sedante.Sedante.despertar(sp);
            });
        });
        // equilibrio de armaduras: netherita con Protección IV y Resistencia IV, golpe de 10 → al menos 2,5 (25 %)
        sin("equilibrio", 5, () -> {
            var srv = mc.getSingleplayerServer();
            UUID id = mc.player.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;
                sp.setGameMode(GameType.SURVIVAL);
                net.minecraft.world.item.Item[] ar = {net.minecraft.world.item.Items.NETHERITE_BOOTS, net.minecraft.world.item.Items.NETHERITE_LEGGINGS,
                        net.minecraft.world.item.Items.NETHERITE_CHESTPLATE, net.minecraft.world.item.Items.NETHERITE_HELMET};
                net.minecraft.world.entity.EquipmentSlot[] sl = {net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
                        net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD};
                for (int i = 0; i < 4; i++) {
                    ItemStack st = new ItemStack(ar[i]);
                    st.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 4);
                    sp.setItemSlot(sl[i], st);
                }
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 3));
                sp.setHealth(20);
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(sp.serverLevel());
                z.moveTo(sp.getX(), sp.getY() + 40, sp.getZ());
                sp.invulnerableTime = 0;
                sp.hurt(sp.damageSources().mobAttack(z), 10);
                float perdida = 20 - sp.getHealth();
                RESUMEN.add(String.format("  equilibrio: golpe de 10 con netherita P-IV y Resistencia IV quita %.2f (mínimo 2,50)", perdida));
                if (perdida < 2.49F) RESUMEN.add("  FALLO: el tope de reducción no funciona");
                for (var x : sl) sp.setItemSlot(x, ItemStack.EMPTY);
                sp.removeAllEffects();
                sp.setHealth(20);
                sp.setGameMode(GameType.CREATIVE);
            });
        });
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
