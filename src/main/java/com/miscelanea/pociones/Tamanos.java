package com.miscelanea.pociones;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Gigante y enano: el tamaño en "pasos" (amplificador + 1). Gigante: x2, x3, x4; enano: x1/2, x1/3, x1/4. De aquí
 * salen la escala (dibujo, caja de choque y altura de los ojos, ver PocionesEventos.tamano y TamanoCliente) y los
 * modificadores de atributos (alcance, daño, velocidad, altura de escalón), que se ponen y quitan en el tick en los
 * dos lados (transitorios: no se guardan). El cliente no conoce los efectos de los demás: para ellos pregunta a
 * {@link #PASOS_REMOTOS} (lo rellena Estados, con lo que manda el servidor).
 */
public final class Tamanos {
    private Tamanos() {}

    public static final int PASOS_MAX = 3;
    private static final String CLAVE_ESCALA = "MiscelaneaEscala";
    private static final UUID VEL = UUID.fromString("2f0c4a7e-1b3d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID DANO = UUID.fromString("3a1d5b8f-2c4e-4f60-9b0c-1d2e3f4a5b6c");
    private static final UUID ALC_BLOQUE = UUID.fromString("4b2e6c90-3d5f-4071-ac1d-2e3f4a5b6c7d");
    private static final UUID ALC_ENTIDAD = UUID.fromString("5c3f7da1-4e60-4182-bd2e-3f4a5b6c7d8e");
    private static final UUID ESCALON = UUID.fromString("6d408eb2-5f71-4293-ce3f-4a5b6c7d8e9f");

    /** Lo rellena el cliente: pasos de una entidad que no es el propio jugador (según el último EstadoPacket). */
    public static ToIntFunction<LivingEntity> PASOS_REMOTOS = v -> 0;

    /** +n gigante, -n enano (gigante manda si lleva los dos), 0 nada. Solo por los efectos (sin preguntar al cliente). */
    public static int pasosPorEfectos(LivingEntity v) {
        MobEffectInstance g = v.getEffect(PocionesRegistro.GIGANTE.get());
        if (g != null) return Math.min(PASOS_MAX, g.getAmplifier() + 1);
        MobEffectInstance e = v.getEffect(PocionesRegistro.ENANO.get());
        if (e != null) return -Math.min(PASOS_MAX, e.getAmplifier() + 1);
        return 0;
    }

    public static int pasos(LivingEntity v) {
        int p = pasosPorEfectos(v);
        if (p == 0 && v.level().isClientSide) p = PASOS_REMOTOS.applyAsInt(v);
        return p;
    }

    public static float escala(int pasos) {
        if (pasos > 0) return 1 + pasos;
        if (pasos < 0) return 1F / (1 - pasos);
        return 1F;
    }

    /** Escala de dibujo y de caja (1 si no lleva nada). Aguanta que la entidad esté a medio construir. */
    public static float escala(LivingEntity v) {
        try {
            return escala(pasos(v));
        } catch (RuntimeException ex) {
            return 1F;
        }
    }

    /** Cada tick (los dos lados): si la escala cambió, caja nueva; y los modificadores de atributos. */
    public static void tick(LivingEntity v) {
        int pasos = pasos(v);
        float s = escala(pasos);
        float antes = v.getPersistentData().contains(CLAVE_ESCALA) ? v.getPersistentData().getFloat(CLAVE_ESCALA) : 1F;
        if (antes != s) {
            v.getPersistentData().putFloat(CLAVE_ESCALA, s);
            v.refreshDimensions();
        }
        boolean gig = pasos > 0, ena = pasos < 0;
        modificador(v, Attributes.MOVEMENT_SPEED, VEL, gig ? 0.25 : 0.35, gig || ena);
        modificador(v, Attributes.ATTACK_DAMAGE, DANO, s - 1, gig);
        modificador(v, ForgeMod.BLOCK_REACH.get(), ALC_BLOQUE, gig ? s - 1 : -0.3, gig || ena);
        modificador(v, ForgeMod.ENTITY_REACH.get(), ALC_ENTIDAD, gig ? s - 1 : -0.3, gig || ena);
        modificador(v, ForgeMod.STEP_HEIGHT_ADDITION.get(), ESCALON, 1.4 * (s - 1), gig, AttributeModifier.Operation.ADDITION);
    }

    private static void modificador(LivingEntity v, Attribute at, UUID id, double valor, boolean activo) {
        modificador(v, at, id, valor, activo, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void modificador(LivingEntity v, Attribute at, UUID id, double valor, boolean activo, AttributeModifier.Operation op) {
        AttributeInstance a = v.getAttribute(at);
        if (a == null) return;
        AttributeModifier m = a.getModifier(id);
        if (activo && (m == null || m.getAmount() != valor)) {
            if (m != null) a.removeModifier(id);
            a.addTransientModifier(new AttributeModifier(id, "miscelanea_tamano", valor, op));
        } else if (!activo && m != null) a.removeModifier(id);
    }
}
