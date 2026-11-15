package com.miscelanea.armasfuego.comun;

import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Partículas de las armas de fuego: el fogonazo de la boca y los casquillos que caen (las usan todas). */
public final class ArmasFuegoRegistro {
    private ArmasFuegoRegistro() {}

    public static final DeferredRegister<ParticleType<?>> PARTICULAS = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);

    /** Fogonazo: una estrella de fuego que dura 2 ticks y brilla a oscuras (siempre se ve, aunque esté lejos). */
    public static final RegistryObject<SimpleParticleType> FOGONAZO = PARTICULAS.register("armafuego_fogonazo", () -> new SimpleParticleType(true));
    /** Casquillo de latón: sale volando, cae, rebota una vez en el suelo y desaparece. */
    public static final RegistryObject<SimpleParticleType> CASQUILLO = PARTICULAS.register("armafuego_casquillo", () -> new SimpleParticleType(false));

    /** Humo de pólvora: bocanadas grises que suben despacio y se deshacen. */
    public static final RegistryObject<SimpleParticleType> HUMO = PARTICULAS.register("armafuego_humo", () -> new SimpleParticleType(false));
    /** Chispa: salta al dar la bala en metal o piedra, cae y rebota (brilla a oscuras). */
    public static final RegistryObject<SimpleParticleType> CHISPA = PARTICULAS.register("armafuego_chispa", () -> new SimpleParticleType(true));
    /** Marca de bala: el agujero que se queda pegado al bloque unos segundos (la normal de la cara va en la velocidad). */
    public static final RegistryObject<SimpleParticleType> MARCA = PARTICULAS.register("armafuego_marca", () -> new SimpleParticleType(false));

    /** La bala que se ve pasar (cuatro colores con la misma textura): latón, fuego, frío y verde. */
    public static final RegistryObject<SimpleParticleType> TRAZO = PARTICULAS.register("armafuego_trazo", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> TRAZO_FUEGO = PARTICULAS.register("armafuego_trazo_fuego", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> TRAZO_FRIO = PARTICULAS.register("armafuego_trazo_frio", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> TRAZO_VERDE = PARTICULAS.register("armafuego_trazo_verde", () -> new SimpleParticleType(true));

    private static boolean es(net.minecraft.world.item.Item m, RegistryObject<net.minecraft.world.item.Item> o) {
        return m == o.get();
    }

    /** El color del trazo de cada munición. */
    public static SimpleParticleType trazoDe(@javax.annotation.Nullable net.minecraft.world.item.Item m) {
        if (m == null) return TRAZO.get();
        if (es(m, com.miscelanea.registry.ModItems.TAMBOR_TRAZADOR) || es(m, com.miscelanea.registry.ModItems.CINTA_INCENDIARIA)
                || es(m, com.miscelanea.registry.ModItems.BALA_DINAMITA) || es(m, com.miscelanea.registry.ModItems.REVOLVER_BENGALA)
                || es(m, com.miscelanea.registry.ModItems.BALA_TRAZADORA)) return TRAZO_FUEGO.get();
        if (es(m, com.miscelanea.registry.ModItems.BALA_HIELO) || es(m, com.miscelanea.registry.ModItems.BALA_PLATA)
                || es(m, com.miscelanea.registry.ModItems.TAMBOR_AGUA) || es(m, com.miscelanea.registry.ModItems.REVOLVER_TIEMPO)) return TRAZO_FRIO.get();
        if (es(m, com.miscelanea.registry.ModItems.REVOLVER_DARDO) || es(m, com.miscelanea.registry.ModItems.CINTA_GUISANTES)) return TRAZO_VERDE.get();
        return TRAZO.get();
    }

    /** Lo que echa la boca con cada munición especial (null = nada más que el fogonazo). */
    @javax.annotation.Nullable
    public static net.minecraft.core.particles.ParticleOptions bocaDe(@javax.annotation.Nullable net.minecraft.world.item.Item m) {
        if (m == null) return null;
        if (es(m, com.miscelanea.registry.ModItems.BALA_HIELO)) return net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
        if (es(m, com.miscelanea.registry.ModItems.BALA_PLATA)) return net.minecraft.core.particles.ParticleTypes.END_ROD;
        if (es(m, com.miscelanea.registry.ModItems.BALA_ORO)) return net.minecraft.core.particles.ParticleTypes.WAX_ON;
        if (es(m, com.miscelanea.registry.ModItems.BALA_DINAMITA) || es(m, com.miscelanea.registry.ModItems.CINTA_INCENDIARIA)
                || es(m, com.miscelanea.registry.ModItems.TAMBOR_TRAZADOR)) return net.minecraft.core.particles.ParticleTypes.SMALL_FLAME;
        if (es(m, com.miscelanea.registry.ModItems.BALA_HUMO)) return net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE;
        if (es(m, com.miscelanea.registry.ModItems.TAMBOR_AGUA)) return net.minecraft.core.particles.ParticleTypes.SPLASH;
        if (es(m, com.miscelanea.registry.ModItems.TAMBOR_TINTA)) return net.minecraft.core.particles.ParticleTypes.SQUID_INK;
        if (es(m, com.miscelanea.registry.ModItems.REVOLVER_DARDO)) return net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
        return null;
    }

    public static void registrar(IEventBus bus) {
        PARTICULAS.register(bus);
    }
}
