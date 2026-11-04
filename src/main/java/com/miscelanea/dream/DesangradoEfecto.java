package com.miscelanea.dream;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * «Desangrado»: la herida del mordisco no deja de sangrar (el veneno de Dream es como la baba de un dragón de Komodo).
 * Cada 2 s (1 s en nivel II, 0,5 s en III...) pierde {@link ConfigServidor#desangradoDano()} de vida, sin que valga la
 * armadura, y gotea sangre; entre medias va soltando alguna gota. En pantalla, bordes rojos que chorrean (cliente).
 */
public class DesangradoEfecto extends MobEffect {
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "desangrado"));
    private static final DustParticleOptions SANGRE = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.03F), 1.0F);
    private static final DustParticleOptions SANGRE_OSCURA = new DustParticleOptions(new Vector3f(0.3F, 0.0F, 0.01F), 0.8F);

    public DesangradoEfecto() {
        super(MobEffectCategory.HARMFUL, DreamRegistro.COLOR_DESANGRADO);
    }

    public static int intervalo(int nivel) {
        return Math.max(10, 40 >> Math.max(0, nivel));
    }

    @Override
    public boolean isDurationEffectTick(int duracion, int nivel) {
        return duracion % 5 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int nivel) {
        if (!(e.level() instanceof ServerLevel l) || !e.isAlive()) return;
        MobEffectInstance inst = e.getEffect(this);
        int duracion = inst != null ? inst.getDuration() : 0;
        double x = e.getX(), y = e.getY() + e.getBbHeight() * 0.75, z = e.getZ();
        double w = e.getBbWidth() * 0.3;
        if (duracion % intervalo(nivel) == 0) {
            float dano = ConfigServidor.desangradoDano();
            if (dano > 0) e.hurt(new DamageSource(l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DANO)), dano);
            l.sendParticles(SANGRE, x, y, z, 10, w, e.getBbHeight() * 0.15, w, 0.0);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y, z, 6, w, 0.1, w, 0.0);
            l.sendParticles(SANGRE_OSCURA, x, e.getY() + 0.1, z, 4, w, 0.05, w, 0.0);
        } else {
            // goteo entre medias
            l.sendParticles(SANGRE_OSCURA, x, y - e.getBbHeight() * 0.2, z, 1, w, e.getBbHeight() * 0.2, w, 0.0);
        }
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        // el icono va en textures/gui (no en el atlas de efectos)
        consumer.accept(new com.miscelanea.client.dream.DesangradoIcono());
    }
}
