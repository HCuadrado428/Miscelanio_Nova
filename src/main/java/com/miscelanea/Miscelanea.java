package com.miscelanea;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModEffects;
import com.miscelanea.registry.ModEnchantments;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModParticles;
import com.miscelanea.registry.ModPotions;
import com.miscelanea.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
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

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::creativeTabs);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModPotions.registerBrewingRecipes();
            ModNetwork.register();
        });
    }

    private void creativeTabs(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.MESA);
            for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) {
                event.accept(ModItems.mueble(ModItems.SILLA.get(), c));
                event.accept(ModItems.mueble(ModItems.SOFA.get(), c));
                event.accept(ModItems.mueble(ModItems.LAMPARA.get(), c));
            }
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.ASPIRAFANTASMAS);
            event.accept(ModItems.MOCHILA);
            event.accept(ModItems.BATERIA);
            event.accept(ModItems.MEDIGUN);
            event.accept(ModItems.MOCHILA_MEDICA);
            event.accept(ModItems.PIPBOY);
            event.accept(ModItems.RED);
            event.accept(ModItems.CANA);
            event.accept(ModItems.BICHOPEDIA);
            event.accept(ModItems.VITRINA);
            for (var b : ModItems.bichos()) event.accept(b);
            event.accept(ModItems.OCARINA);
            event.accept(ModItems.TABLERO);
            event.accept(ModItems.IMPERIO);
            event.accept(ModItems.PARCHIS);
            event.accept(ModItems.OCA);
            event.accept(ModItems.CUATRO);
            event.accept(ModItems.FLOTA);
            event.accept(ModItems.DOMINO);
            event.accept(ModItems.POSTE_TIROLINA);
            event.accept(ModItems.CABLE_TIROLINA);
            event.accept(ModItems.TRAMPOLIN);
            event.accept(ModItems.DETECTOR);
            event.accept(ModItems.CAPSULA_TIEMPO);
            for (var f : ModItems.FOSILES) event.accept(f);
            event.accept(ModItems.ACUARIO);
            event.accept(ModItems.TERRARIO);
            event.accept(ModItems.RECREATIVA);
            event.accept(ModItems.RECREATIVA_DOBLE);
            event.accept(ModItems.MESA_COCTEL);
            event.accept(ModItems.CARTEL_RECORDS);
            event.accept(ModItems.PIZARRA_FERIA);
            event.accept(ModItems.BILLAR);
            event.accept(ModItems.FUTBOLIN);
            event.accept(ModItems.PISTA_BAILE);
            event.accept(ModItems.NORIA);
            event.accept(ModItems.TIOVIVO);
            event.accept(ModItems.COCHE_CHOQUE);
            event.accept(ModItems.VAGONETA_BRUJA);
            event.accept(ModItems.SUSTO);
            event.accept(ModItems.CASETA_TIRO);
            event.accept(ModItems.ESCOPETA_CORCHO);
            event.accept(ModItems.PELUCHE);
            event.accept(ModItems.ALGODONERA);
            event.accept(ModItems.PALOMITERA);
            event.accept(ModItems.CASTILLO_FUEGOS);
            event.accept(ModItems.ALGODON);
            event.accept(ModItems.PALOMITAS);
            event.accept(ModItems.MAQUINA_GANCHO);
            event.accept(ModItems.TRAGAPERRAS);
            event.accept(ModItems.GACHAPON);
            event.accept(ModItems.CAPSULA_GACHA);
            event.accept(ModItems.MAQUINA_CANASTA);
            event.accept(ModItems.MAQUINA_BOXEO);
            event.accept(ModItems.MARTILLO_FUERZA);
            event.accept(ModItems.BALON);
            event.accept(ModItems.AIRHOCKEY);
            event.accept(ModItems.BOLERA);
            event.accept(ModItems.BOLA_BOLOS);
            event.accept(ModItems.DIANA_DARDOS);
            event.accept(ModItems.DARDO);
            event.accept(ModItems.TORRE);
            event.accept(ModItems.MESA_CARTAS);
            event.accept(ModItems.FOGON);
            event.accept(ModItems.SEMILLAS_TOMATE);
            event.accept(ModItems.TOMATE);
            event.accept(ModItems.ARROZ);
            event.accept(ModItems.PAELLA);
            event.accept(ModItems.TORTILLA);
            event.accept(ModItems.GAZPACHO);
            event.accept(ModItems.CHURROS);
            event.accept(ModItems.CHURROS_CHOCOLATE);
            for (int i = 0; i < com.miscelanea.feria.ChapaItem.DIBUJOS.length; i++) event.accept(com.miscelanea.feria.ChapaItem.de(i));
            event.accept(ModItems.FICHA);
            event.accept(ModItems.CONSOLA);
            for (var c : ModItems.cartuchosJuego()) event.accept(c);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.PORRA);
            event.accept(ModItems.NUCLEO);
            event.accept(ModItems.LANZATINTAS);
            event.accept(ModItems.RODILLO);
            event.accept(ModItems.PISTOLA_GRAVEDAD);
            event.accept(ModItems.SUPERESCOPETA);
            event.accept(ModItems.CARTUCHOS);
            event.accept(ModItems.LANZACOHETES);
            event.accept(ModItems.COHETE);
            event.accept(ModItems.LLAVE_ESPADA);
            event.accept(ModItems.HACHA_LEVIATAN);
        }
    }
}
