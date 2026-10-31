package com.miscelanea.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Atributos de los bichos (vida 1). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Registro {
    private Registro() {}

    @SubscribeEvent
    public static void atributos(EntityAttributeCreationEvent e) {
        e.put(ModEntities.BICHO.get(), BichoEntity.atributos().build());
    }
}
