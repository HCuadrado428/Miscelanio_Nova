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
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.ASPIRAFANTASMAS);
            event.accept(ModItems.MOCHILA);
            event.accept(ModItems.BATERIA);
            event.accept(ModItems.MEDIGUN);
            event.accept(ModItems.MOCHILA_MEDICA);
            event.accept(ModItems.PIPBOY);
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
