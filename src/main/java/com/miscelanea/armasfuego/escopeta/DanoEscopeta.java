package com.miscelanea.armasfuego.escopeta;

import com.miscelanea.Miscelanea;
import com.miscelanea.mixin.LivingEntityGolpeAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Daño de la escopeta de presión: el 75 % va con el golpe normal del jugador (la armadura lo para como siempre) y el
 * 25 % con {@code miscelanea:escopeta_presion}, que está en {@code #minecraft:bypasses_armor} (atraviesa la armadura).
 * La segunda parte va en el mismo tick sin empuje propio ni romper la ventana de invulnerabilidad.
 */
public final class DanoEscopeta {
    private DanoEscopeta() {}

    public static final float PERFORA = 0.25F;

    public static final ResourceKey<DamageType> PERDIGON =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "escopeta_presion"));
    public static final ResourceKey<DamageType> REVENTON =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "escopeta_presion_reventon"));

    public static DamageSource fuente(Entity v, ResourceKey<DamageType> tipo, Entity causa) {
        return new DamageSource(v.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(tipo), null, causa);
    }

    /** Devuelve si el golpe ha entrado. */
    public static boolean golpear(LivingEntity v, DamageSource normal, float total) {
        if (total <= 0) return false;
        float n = total * (1 - PERFORA), perf = total * PERFORA;
        if (!v.hurt(normal, n)) return false;
        if (!v.isAlive()) return true;
        LivingEntityGolpeAccessor acc = (LivingEntityGolpeAccessor) v;
        int inv = v.invulnerableTime;
        float ultimo = acc.miscelanea$ultimoDano();
        Vec3 mov = v.getDeltaMovement();
        boolean marcado = v.hurtMarked;
        v.invulnerableTime = 0;
        v.hurt(fuente(v, PERDIGON, normal.getEntity()), perf);
        v.invulnerableTime = inv;
        acc.miscelanea$ponerUltimoDano(ultimo + perf);
        v.setDeltaMovement(mov);
        v.hurtMarked = marcado;
        return true;
    }
}
