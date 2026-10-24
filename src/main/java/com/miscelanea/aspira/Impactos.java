package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sigue a las entidades lanzadas por la aspiradora para hacer daño cuando chocan:
 * ESTAMPAR = golpe contra el suelo con onda expansiva; PROYECTIL = choque contra lo que sea.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Impactos {
    /** ESTAMPAR: contra el suelo. PROYECTIL: contra lo que sea. VUELO: solo vuela (el daño lo pone la caída). */
    public enum Tipo { ESTAMPAR, PROYECTIL, VUELO }

    private static final class Entrada {
        final Tipo tipo;
        @Nullable final Player atacante;
        final float potencia;   // multiplica el daño del impacto
        final Vec3 impulso;     // velocidad que se mantiene mientras dura el empuje
        int empuje;             // ticks que se sigue empujando (para llegar MUY lejos)
        int ticks;

        Entrada(Tipo tipo, @Nullable Player atacante, float potencia, Vec3 impulso, int empuje) {
            this.tipo = tipo;
            this.atacante = atacante;
            this.potencia = potencia;
            this.impulso = impulso;
            this.empuje = empuje;
        }
    }

    private static final Map<Entity, Entrada> SEGUIDAS = new WeakHashMap<>();

    private Impactos() {}

    public static void seguir(Entity e, Tipo tipo, @Nullable Player atacante) {
        SEGUIDAS.put(e, new Entrada(tipo, atacante, 1.0F, Vec3.ZERO, 0));
    }

    /**
     * Lanza una entidad. La velocidad por tick se limita a 3,9 (lo máximo que el juego sincroniza a un jugador);
     * el resto de la potencia se convierte en ticks de empuje sostenido, así llega mucho más lejos.
     */
    public static void lanzar(Entity e, Tipo tipo, @Nullable Player atacante, Vec3 direccion, double velocidad, float potencia) {
        double v = Math.min(velocidad, 3.9);
        int empuje = velocidad > 3.9 ? (int) Math.min(40, Math.round((velocidad - 3.9) * 6)) : 0;
        Vec3 impulso = direccion.normalize().scale(v);
        AspiraUtil.empujar(e, impulso);
        SEGUIDAS.put(e, new Entrada(tipo, atacante, potencia, impulso, empuje));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;

        Iterator<Map.Entry<Entity, Entrada>> it = SEGUIDAS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Entrada> en = it.next();
            Entity e = en.getKey();
            Entrada d = en.getValue();
            if (e.level() != level) continue;
            if (!e.isAlive() || ++d.ticks > 200) {
                it.remove();
                continue;
            }

            // Empuje sostenido: mantiene la velocidad unos ticks (va perdiendo fuerza poco a poco)
            if (d.empuje > 0) {
                d.empuje--;
                Vec3 v = d.impulso.scale(Math.pow(0.97, d.ticks));
                AspiraUtil.empujar(e, v);
                if (d.ticks % 2 == 0) {
                    Vec3 c = AspiraUtil.centro(e);
                    level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0.0);
                }
            }

            if (d.ticks < 3) continue; // dar tiempo a despegar
            boolean impacto = switch (d.tipo) {
                case ESTAMPAR -> e.onGround();
                case PROYECTIL -> e.onGround() || e.horizontalCollision || e.verticalCollision || golpeaAlguien(level, e, d.atacante);
                case VUELO -> d.empuje <= 0;
            };
            if (!impacto) continue;

            if (d.tipo == Tipo.ESTAMPAR) estampar(level, e, d.atacante, d.potencia);
            else if (d.tipo == Tipo.PROYECTIL) proyectil(level, e, d.atacante, d.potencia);
            it.remove();
        }
    }

    private static boolean golpeaAlguien(ServerLevel level, Entity e, @Nullable Player atacante) {
        return !level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.3),
                o -> o != e && o != atacante && o.isAlive()).isEmpty();
    }

    private static DamageSource fuente(ServerLevel level, @Nullable Player atacante) {
        return atacante != null ? level.damageSources().playerAttack(atacante) : level.damageSources().flyIntoWall();
    }

    private static void estampar(ServerLevel level, Entity e, @Nullable Player atacante, float potencia) {
        Vec3 p = e.position();
        BlockState suelo = level.getBlockState(BlockPos.containing(p.x, p.y - 0.2, p.z));

        e.fallDistance = 0.0F;
        e.hurt(fuente(level, atacante), Math.min(8.0F * potencia, 30.0F));

        // Onda expansiva
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(3.0),
                o -> o != e && o != atacante && o.isAlive())) {
            o.hurt(fuente(level, atacante), Math.min(4.0F * potencia, 15.0F));
            Vec3 dir = o.position().subtract(p).normalize();
            AspiraUtil.empujar(o, o.getDeltaMovement().add(dir.x * 0.8, 0.5, dir.z * 0.8));
        }

        if (!suelo.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, suelo), p.x, p.y + 0.1, p.z,
                    80, 1.2, 0.1, 1.2, 0.2);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.3, p.z, 2, 0.5, 0.1, 0.5, 0.0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    private static void proyectil(ServerLevel level, Entity e, @Nullable Player atacante, float potencia) {
        Vec3 p = AspiraUtil.centro(e);
        e.hurt(fuente(level, atacante), Math.min(4.0F * potencia, 24.0F));
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.6),
                o -> o != e && o != atacante && o.isAlive())) {
            o.hurt(fuente(level, atacante), Math.min(5.0F * potencia, 20.0F));
            AspiraUtil.empujar(o, o.getDeltaMovement().add(e.getDeltaMovement().scale(0.6)).add(0, 0.3, 0));
        }
        level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 20, 0.3, 0.3, 0.3, 0.3);
        level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
    }
}
