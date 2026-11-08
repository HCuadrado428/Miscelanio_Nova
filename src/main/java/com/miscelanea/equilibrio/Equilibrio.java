package com.miscelanea.equilibrio;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Equilibrio de armaduras: con armaduras muy fuertes (Ice and Fire), encantamientos por encima del máximo (el libro de
 * impregnación mágica de Corail Tombstone), el perk Guardián, Resistencia alta… se llegaba a no recibir casi nada de
 * daño. Aquí se pone un TOPE a lo que se puede quitar entre todo junto (armadura, dureza, encantamientos de protección
 * y efectos): por defecto, como mucho el 75 %, así que cada golpe hace siempre al menos un 25 %.
 * <p>
 * No toca las armaduras ni los encantamientos (siguen igual de buenos hasta el tope): solo mira el daño antes de
 * restarle nada ({@link LivingHurtEvent}, lo último) y el que queda al final ({@link LivingDamageEvent}, lo último), y
 * si la diferencia pasa del tope, sube el final hasta el tope. Lo que se coma la absorción (corazones amarillos)
 * cuenta como daño hecho. Todo se ajusta en la config del servidor (sección "equilibrio").
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Equilibrio {
    private Equilibrio() {}

    /** Daño antes de las reducciones y absorción que tenía, por entidad (del golpe en curso). */
    private record Golpe(float danio, float absorcion) {}

    private static final Map<LivingEntity, Golpe> GOLPES = new WeakHashMap<>();

    static boolean aplica(LivingEntity e) {
        if (!ConfigServidor.equilibrioActivo() || e.level().isClientSide) return false;
        return e instanceof Player || ConfigServidor.equilibrioMobs();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void antes(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (!aplica(v) || e.isCanceled() || e.getAmount() <= 0) return;
        GOLPES.put(v, new Golpe(e.getAmount(), v.getAbsorptionAmount()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(LivingDamageEvent e) {
        LivingEntity v = e.getEntity();
        Golpe g = GOLPES.remove(v);
        if (g == null || e.isCanceled()) return;
        float comido = Math.max(0, g.absorcion() - v.getAbsorptionAmount());
        float llega = e.getAmount() + comido;
        float minimo = g.danio() * (1F - (float) ConfigServidor.equilibrioReduccionMaxima());
        if (llega < minimo) e.setAmount(e.getAmount() + (minimo - llega));
    }
}
