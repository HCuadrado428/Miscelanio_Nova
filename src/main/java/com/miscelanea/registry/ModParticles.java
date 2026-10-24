package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Partículas de aire con la textura del humo de fogata (assets/miscelanea/particles/*.json).
 * Los parámetros de velocidad se usan como desplazamiento hasta el destino.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);

    /** Humo que viaja hacia la boca acelerando y encogiéndose. */
    public static final RegistryObject<SimpleParticleType> ASPIRACION =
            PARTICLES.register("aspiracion", () -> new SimpleParticleType(false));
    /** Humo que sale de la boca frenando y creciendo. */
    public static final RegistryObject<SimpleParticleType> SOPLIDO =
            PARTICLES.register("soplido", () -> new SimpleParticleType(false));

    /** Chorro de poción del lanzallamas (Alquimista), con el color del tanque. */
    public static final RegistryObject<ParticleType<com.miscelanea.aspira.RocioOptions>> ROCIO =
            PARTICLES.register("rocio", () -> new ParticleType<com.miscelanea.aspira.RocioOptions>(false,
                    com.miscelanea.aspira.RocioOptions.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<com.miscelanea.aspira.RocioOptions> codec() {
                    return com.miscelanea.aspira.RocioOptions.CODEC;
                }
            });

    private ModParticles() {}
}
