package com.miscelanea;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModEffects;
import com.miscelanea.registry.ModEnchantments;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModParticles;
import com.miscelanea.registry.ModPotions;
import com.miscelanea.registry.ModSounds;
import com.miscelanea.registry.ModTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Miscelanea.MOD_ID)
public class Miscelanea {
    public static final String MOD_ID = "miscelanea";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public Miscelanea() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // configuración del servidor (serverconfig/miscelanea-server.toml; Forge la sincroniza con los clientes)
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                ConfigServidor.SPEC, MOD_ID + "-server.toml");

        ModEffects.EFFECTS.register(modBus);
        ModPotions.POTIONS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        com.miscelanea.registry.ModRecipes.SERIALIZERS.register(modBus);
        com.miscelanea.registry.ModBlocks.BLOCKS.register(modBus);
        com.miscelanea.registry.ModBlocks.BLOCK_ENTITIES.register(modBus);
        com.miscelanea.registry.ModTabs.TABS.register(modBus);
        com.miscelanea.tortura.TorturaRegistro.registrar(modBus);
        com.miscelanea.tortura.aparatos.AparatosRegistro.registrar(modBus);
        com.miscelanea.tortura.efectos.EfectosRegistro.registrar(modBus);
        com.miscelanea.pociones.PocionesRegistro.registrar(modBus);
        com.miscelanea.voz.VozRegistro.registrar(modBus);
        com.miscelanea.tortura.saw.SawRegistro.registrar(modBus);
        com.miscelanea.dream.DreamRegistro.registrar(modBus);
        com.miscelanea.glitch.GlitchRegistro.registrar(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::creativeTabs);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModPotions.registerBrewingRecipes();
            ModNetwork.register();
        });
    }

    /** Objetos del mod que ya se han metido en alguna pestaña propia (para avisar de los que se queden fuera). */
    private static final java.util.Set<Item> EN_PESTANAS = new java.util.HashSet<>();
    private static int pestanasHechas;
    /** Objetos internos que no van en el creativo a propósito (iconos de proyectiles, piezas que solo se dibujan). */
    private static final java.util.Set<String> SIN_PESTANA = java.util.Set.of("desatascador", "escudo_guardian", "muestra_glitch_lanzada");

    private void creativeTabs(final BuildCreativeModeTabContentsEvent event) {
        // ---- Feria y atracciones
        if (event.getTabKey() == ModTabs.FERIA.getKey()) {
            event.accept(ModItems.NORIA);
            event.accept(ModItems.TIOVIVO);
            event.accept(ModItems.COCHE_CHOQUE);
            event.accept(ModItems.VAGONETA_BRUJA);
            event.accept(ModItems.SUSTO);
            event.accept(ModItems.PULPO);
            event.accept(ModItems.CASETA_TIRO);
            event.accept(ModItems.ESCOPETA_CORCHO);
            event.accept(ModItems.PELUCHE);
            event.accept(ModItems.PESCA_PATITOS);
            event.accept(ModItems.CANA_PATITOS);
            event.accept(ModItems.TOMBOLA);
            event.accept(ModItems.MAQUINA_GANCHO);
            event.accept(ModItems.TRAGAPERRAS);
            event.accept(ModItems.GACHAPON);
            event.accept(ModItems.CAPSULA_GACHA);
            event.accept(ModItems.MAQUINA_CANASTA);
            event.accept(ModItems.MAQUINA_BOXEO);
            event.accept(ModItems.MARTILLO_FUERZA);
            event.accept(ModItems.BALON);
            event.accept(ModItems.ALGODONERA);
            event.accept(ModItems.PALOMITERA);
            event.accept(ModItems.CHURRERIA);
            event.accept(ModItems.TABURETE);
            event.accept(ModItems.ALGODON);
            event.accept(ModItems.PALOMITAS);
            event.accept(ModItems.CHURROS);
            event.accept(ModItems.CHURROS_CHOCOLATE);
            event.accept(ModItems.CASTILLO_FUEGOS);
            event.accept(ModItems.BOLAS_PETANCA);
            event.accept(ModItems.BOLICHE);
            event.accept(ModItems.METRO);
            event.accept(ModItems.BOLERA);
            event.accept(ModItems.BOLA_BOLOS);
            event.accept(ModItems.DIANA_DARDOS);
            event.accept(ModItems.DARDO);
            event.accept(ModItems.TORRE);
            event.accept(ModItems.TRAMPOLIN);
            event.accept(ModItems.POSTE_TIROLINA);
            event.accept(ModItems.CABLE_TIROLINA);
            event.accept(ModItems.PISTA_BAILE);
            event.accept(ModItems.PIZARRA_FERIA);
            for (int i = 0; i < com.miscelanea.feria.ChapaItem.DIBUJOS.length; i++) event.accept(com.miscelanea.feria.ChapaItem.de(i));
        }
        // ---- Sala recreativa
        if (event.getTabKey() == ModTabs.RECREATIVAS.getKey()) {
            event.accept(ModItems.RECREATIVA);
            event.accept(ModItems.RECREATIVA_DOBLE);
            event.accept(ModItems.CABINA_CARRERAS);
            event.accept(ModItems.RECREATIVA_PISTOLA);
            for (var r : ModItems.recreativasJuego()) event.accept(r);
            event.accept(ModItems.MESA_COCTEL);
            event.accept(ModItems.CARTEL_RECORDS);
            event.accept(ModItems.FICHA);
            event.accept(ModItems.CONSOLA);
            for (var c : ModItems.cartuchosJuego()) event.accept(c);
        }
        // ---- Juegos de mesa
        if (event.getTabKey() == ModTabs.JUEGOS_MESA.getKey()) {
            event.accept(ModItems.TABLERO);
            event.accept(ModItems.IMPERIO);
            event.accept(ModItems.PARCHIS);
            event.accept(ModItems.OCA);
            event.accept(ModItems.CUATRO);
            event.accept(ModItems.FLOTA);
            event.accept(ModItems.DOMINO);
            event.accept(ModItems.MESA_CARTAS);
            event.accept(ModItems.BILLAR);
            event.accept(ModItems.FUTBOLIN);
            event.accept(ModItems.AIRHOCKEY);
        }
        // ---- Armas y combate
        if (event.getTabKey() == ModTabs.ARMAS.getKey()) {
            event.accept(ModItems.PORRA);
            event.accept(ModItems.BATERIA);
            event.accept(ModItems.NUCLEO);
            event.accept(ModItems.LANZATINTAS);
            event.accept(ModItems.RODILLO);
            event.accept(ModItems.PISTOLA_GRAVEDAD);
            event.accept(ModItems.SUPERESCOPETA);
            event.accept(ModItems.CARTUCHOS);
            event.accept(ModItems.LANZACOHETES);
            event.accept(ModItems.COHETE);
            event.accept(ModItems.BALLESTA_REPETICION);
            event.accept(ModItems.BFG);
            event.accept(ModItems.MAZO_WEMMBU);
            event.accept(ModItems.LLAVE_ESPADA);
            event.accept(ModItems.HACHA_LEVIATAN);
            event.accept(ModItems.MEDIGUN);
            event.accept(ModItems.MOCHILA_MEDICA);
        }
        // ---- Sala de juegos / tortura
        if (event.getTabKey() == ModTabs.TORTURA.getKey()) {
            event.accept(com.miscelanea.tortura.TorturaRegistro.TRAMPA_OSO);
            event.accept(com.miscelanea.tortura.TorturaRegistro.LLAVE_TRAMPA);
            event.accept(com.miscelanea.tortura.TorturaRegistro.CAJA_JIGSAW_ITEM);
            event.accept(com.miscelanea.tortura.TorturaRegistro.GRILLETES);
            event.accept(com.miscelanea.tortura.TorturaRegistro.CADENA);
            event.accept(com.miscelanea.tortura.TorturaRegistro.GANZUA);
            event.accept(com.miscelanea.tortura.TorturaRegistro.LAZO);
            event.accept(ModItems.VENDA);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.DONCELLA_ITEM);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.POTRO_ITEM);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.SILLA_ITEM);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.CORREAS);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.HIERRO_MARCAR);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.PENDULO_ITEM);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.INMERSION_ITEM);
            event.accept(com.miscelanea.tortura.TorturaRegistro.JAULA_COLGANTE_ITEM);
            event.accept(com.miscelanea.tortura.TorturaRegistro.CEPO_ITEM);
            event.accept(com.miscelanea.tortura.TorturaRegistro.LLAVE_CARCELERO);
            event.accept(com.miscelanea.tortura.TorturaRegistro.TOMATE_PODRIDO);
            event.accept(com.miscelanea.tortura.TorturaRegistro.HUEVO_PODRIDO);
            event.accept(com.miscelanea.tortura.TorturaRegistro.BOLA_PRESO);
            event.accept(com.miscelanea.tortura.TorturaRegistro.SIERRA);
            event.accept(com.miscelanea.tortura.efectos.EfectosRegistro.COLLAR);
            event.accept(com.miscelanea.tortura.efectos.EfectosRegistro.DETONADOR);
            event.accept(com.miscelanea.tortura.efectos.EfectosRegistro.ALICATES);
            event.accept(com.miscelanea.tortura.efectos.EfectosRegistro.GRANADA_MIEDO);
            event.accept(com.miscelanea.tortura.efectos.EfectosRegistro.DARDO_MIEDO);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.INTERROGATORIO_ITEM);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.MALETIN);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.DIENTE);
            event.accept(com.miscelanea.tortura.aparatos.AparatosRegistro.GANCHO_ITEM);
            // juegos de Jigsaw (Saw)
            event.accept(com.miscelanea.tortura.saw.SawRegistro.CAJA_AGUA);
            event.accept(com.miscelanea.tortura.saw.SawRegistro.TANQUE_ITEM);
        }
        // ---- Pociones y efectos (cada poción con sus variantes: normal, arrojadiza, persistente y flecha)
        if (event.getTabKey() == ModTabs.POCIONES.getKey()) {
            for (var p : new net.minecraft.world.item.alchemy.Potion[]{ModPotions.CUPIDO.get(), ModPotions.LONG_CUPIDO.get(), ModPotions.STRONG_CUPIDO.get(),
                    ModPotions.ENEMIGO.get(), ModPotions.LONG_ENEMIGO.get(), ModPotions.STRONG_ENEMIGO.get()})
                for (var st : pociones(p)) event.accept(st);
            // pociones de fantasma (sin receta)
            for (var st : com.miscelanea.tortura.efectos.EfectosRegistro.pocionesFantasma()) event.accept(st);
            for (var p : new net.minecraft.world.item.alchemy.Potion[]{com.miscelanea.tortura.efectos.EfectosRegistro.POCION_FANTASMA.get(),
                    com.miscelanea.tortura.efectos.EfectosRegistro.POCION_FANTASMA_LARGA.get()})
                event.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TIPPED_ARROW), p));
            for (var st : com.miscelanea.pociones.PocionesRegistro.pociones()) event.accept(st);
            for (var p : com.miscelanea.voz.VozRegistro.FRASCOS_CREATIVO) event.accept(com.miscelanea.voz.VozRegistro.frasco(p));
            event.accept(com.miscelanea.voz.VozRegistro.arrojadiza(com.miscelanea.voz.VozRegistro.POCION_TARTAMUDEO));
            event.accept(com.miscelanea.voz.VozRegistro.arrojadiza(com.miscelanea.voz.VozRegistro.POCION_TARTAMUDEO_LARGA));
            event.accept(com.miscelanea.voz.VozRegistro.arrojadiza(com.miscelanea.voz.VozRegistro.POCION_BORRACHERA));
            // caldero de mezclas, un par de frascos de ejemplo y la esencia de Dream (solo creativo)
            event.accept(com.miscelanea.dream.DreamRegistro.CALDERO_ITEM);
            for (var st : com.miscelanea.dream.Mezclas.ejemplos()) event.accept(st);
            event.accept(com.miscelanea.dream.DreamRegistro.ESENCIA);
            // --- pociones (otros paquetes añaden aquí)
        }
        // ---- Varios
        if (event.getTabKey() == ModTabs.VARIOS.getKey()) {
            event.accept(ModItems.ASPIRAFANTASMAS);
            event.accept(ModItems.MOCHILA);
            event.accept(ModItems.PIPBOY);
            event.accept(ModItems.OCARINA);
            event.accept(ModItems.RED);
            event.accept(ModItems.CANA);
            event.accept(ModItems.BICHOPEDIA);
            event.accept(ModItems.VITRINA);
            for (var b : ModItems.bichos()) event.accept(b);
            event.accept(ModItems.ACUARIO);
            event.accept(ModItems.TERRARIO);
            event.accept(ModItems.DETECTOR);
            event.accept(ModItems.CAPSULA_TIEMPO);
            for (var f : ModItems.FOSILES) event.accept(f);
            event.accept(ModItems.MESA);
            for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) {
                event.accept(ModItems.mueble(ModItems.SILLA.get(), c));
                event.accept(ModItems.mueble(ModItems.SOFA.get(), c));
                event.accept(ModItems.mueble(ModItems.LAMPARA.get(), c));
            }
            event.accept(ModItems.FOGON);
            event.accept(ModItems.SEMILLAS_TOMATE);
            event.accept(ModItems.TOMATE);
            event.accept(ModItems.ARROZ);
            event.accept(ModItems.PAELLA);
            event.accept(ModItems.TORTILLA);
            event.accept(ModItems.GAZPACHO);
            event.accept(com.miscelanea.dream.DreamRegistro.ANILLO);
            // el glitch (solo de creativo): frasco para recoger muestras y la muestra
            event.accept(com.miscelanea.glitch.GlitchRegistro.FRASCO);
            event.accept(com.miscelanea.glitch.GlitchRegistro.MUESTRA);
        }
        comprobarPestanas(event);
    }

    /** Poción, arrojadiza, persistente y flecha con punta de una poción. */
    private static java.util.List<net.minecraft.world.item.ItemStack> pociones(net.minecraft.world.item.alchemy.Potion p) {
        java.util.List<net.minecraft.world.item.ItemStack> l = new java.util.ArrayList<>();
        for (Item i : new Item[]{net.minecraft.world.item.Items.POTION, net.minecraft.world.item.Items.SPLASH_POTION, net.minecraft.world.item.Items.LINGERING_POTION,
                net.minecraft.world.item.Items.TIPPED_ARROW})
            l.add(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(i), p));
        return l;
    }

    /**
     * Apunta lo que ha entrado en cada pestaña propia y, cuando se han construido todas, avisa en el log de cualquier
     * objeto del mod que no esté en ninguna (salvo los internos de {@link #SIN_PESTANA}).
     */
    private static void comprobarPestanas(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().getNamespace().equals(MOD_ID)) return;
        for (var e : event.getEntries()) EN_PESTANAS.add(e.getKey().getItem());
        if (++pestanasHechas < com.miscelanea.registry.ModTabs.TABS.getEntries().size()) return;
        pestanasHechas = 0;
        for (var e : net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(MOD_ID) || SIN_PESTANA.contains(id.getPath()) || EN_PESTANAS.contains(e.getValue())) continue;
            LOGGER.warn("[miscelanea] El objeto {} no está en ninguna pestaña del creativo", id);
        }
        EN_PESTANAS.clear();
    }
}
