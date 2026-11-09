package com.miscelanea.armasfuego.rifle;

import com.miscelanea.Miscelanea;
import com.miscelanea.mixin.LivingEntityGolpeAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Daño del rifle: la mitad con {@code miscelanea:rifle_disparo} (proyectil normal, la armadura lo para) y la otra mitad
 * con {@code miscelanea:rifle_perforante} (en {@code #minecraft:bypasses_armor}: atraviesa el 50 % de la armadura). Las
 * dos partes cuentan como un solo golpe (misma ventana de invulnerabilidad, un solo empujón).
 */
public final class RifleDano {
    private RifleDano() {}

    public static final ResourceKey<DamageType> DISPARO =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "rifle_disparo"));
    public static final ResourceKey<DamageType> PERFORANTE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "rifle_perforante"));
    /** Parte del daño que se salta la armadura. */
    public static final float PERFORA = 0.5F;

    public static DamageSource fuente(Level l, ResourceKey<DamageType> tipo, @Nullable Entity bala, @Nullable Entity tirador) {
        return new DamageSource(l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(tipo), bala, tirador);
    }

    /** Aplica el golpe repartido. Devuelve si ha entrado. */
    public static boolean golpear(Entity v, @Nullable Entity bala, @Nullable Entity tirador, float total) {
        Level l = v.level();
        DamageSource normal = fuente(l, DISPARO, bala, tirador);
        if (!(v instanceof LivingEntity le)) return v.hurt(normal, total);
        float perf = total * PERFORA, resto = total - perf;
        if (!le.hurt(normal, resto)) return false;
        if (!le.isAlive() || perf <= 0) return true;
        // la parte perforante entra en el mismo golpe: sin reiniciar la invulnerabilidad ni empujar otra vez
        LivingEntityGolpeAccessor acc = (LivingEntityGolpeAccessor) le;
        int inv = le.invulnerableTime;
        float ultimo = acc.miscelanea$ultimoDano();
        Vec3 mov = le.getDeltaMovement();
        boolean marcado = le.hurtMarked;
        le.invulnerableTime = 0;
        le.hurt(fuente(l, PERFORANTE, bala, tirador), perf);
        le.invulnerableTime = inv;
        acc.miscelanea$ponerUltimoDano(ultimo + perf);
        le.setDeltaMovement(mov);
        le.hurtMarked = marcado;
        return true;
    }
}
