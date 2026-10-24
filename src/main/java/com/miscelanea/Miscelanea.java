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
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.PORRA);
            event.accept(ModItems.NUCLEO);
            event.accept(ModItems.BOTELLA);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            com.miscelanea.registry.ModBlocks.ITEMS_BLOQUE.forEach(event::accept);
            event.accept(ModItems.CARRO_CHIVATO);
            event.accept(ModItems.HUEVO_GUARDIA);
            event.accept(ModItems.HUEVO_GRITON);
            event.accept(ModItems.HUEVO_JEFE);
            for (int i = 0; i < com.miscelanea.carcel.Simbolo.TOTAL; i++) {
                event.accept(com.miscelanea.carcel.LlaveItem.con(ModItems.LLAVE.get(), i));
            }
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.GAFAS);
            event.accept(ModItems.PILA);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.ANTIDOTO);
            event.accept(ModItems.MEDICINA);
        }
    }
}
