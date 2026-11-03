package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.Tamanos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** Gigante / enano: el modelo se dibuja a escala (la caja y los ojos van por EntityEvent.Size en los dos lados). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TamanoCliente {
    private TamanoCliente() {}

    private static final Set<Integer> ESCALADOS = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity v = e.getEntity();
        float s = Tamanos.escala(v);
        if (s == 1F) return;
        e.getPoseStack().pushPose();
        e.getPoseStack().scale(s, s, s);
        ESCALADOS.add(v.getId());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (ESCALADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }
}
