package com.miscelanea.tortura.aparatos;

import com.miscelanea.Miscelanea;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Mobs convertidos en el potro (como las criaturas de la sala de tortura de Dungeon Keeper): siguen a su dueño y pelean
 * por él (contra quien le pega o contra lo que él ataca). No tocan las tareas de IA del mob: se hace con su objetivo
 * (setTarget, que ya usan sus propios ataques), su navegación y, en los aldeanos, su memoria de destino. A los mobs sin
 * ataque propio (aldeanos...) se les da un golpe sencillo. Nunca atacan a su dueño ni a otros conversos del mismo dueño.
 * Se guarda en los datos persistentes del mob: {@code miscelanea_converso: {Dueno}}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Conversos {
    private Conversos() {}

    public static final String CLAVE = "miscelanea_converso";

    public static void convertir(Mob m, Player duenno) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Dueno", duenno.getUUID());
        m.getPersistentData().put(CLAVE, t);
        m.setTarget(null);
        m.setAggressive(false);
        m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        m.setPersistenceRequired();
        m.getNavigation().stop();
    }

    @Nullable
    public static UUID duenno(@Nullable Entity e) {
        if (e == null || !e.getPersistentData().contains(CLAVE)) return null;
        CompoundTag t = e.getPersistentData().getCompound(CLAVE);
        return t.hasUUID("Dueno") ? t.getUUID("Dueno") : null;
    }

    /** ¿'b' es el dueño de 'a' o es otro converso del mismo dueño? */
    public static boolean aliados(Entity a, Entity b) {
        UUID da = duenno(a);
        if (da == null) return false;
        return da.equals(b.getUUID()) || da.equals(duenno(b));
    }

    private static boolean valido(Mob m, @Nullable LivingEntity t, Player duenno) {
        return t != null && t.isAlive() && t != m && t != duenno && !aliados(m, t) && t.level() == m.level() && t.distanceTo(m) < 24
                && !(t instanceof Player pt && (pt.isCreative() || pt.isSpectator()));
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob m) || !(m.level() instanceof ServerLevel sl) || !m.getPersistentData().contains(CLAVE)) return;
        if (Aparatos.en(m)) return;   // otra vez en un aparato: quieto
        UUID u = duenno(m);
        ServerPlayer d = u == null ? null : sl.getServer().getPlayerList().getPlayer(u);
        LivingEntity t = m.getTarget();
        if (t != null && (d == null || !valido(m, t, d))) {
            m.setTarget(null);
            t = null;
        }
        if (d == null || d.level() != m.level() || !d.isAlive()) return;
        boolean sinAtaque = m.getAttribute(Attributes.ATTACK_DAMAGE) == null;
        // golpe propio de los que no saben pegar (cada tick, con su propio ritmo)
        if (t != null && sinAtaque && m.tickCount % 20 == 0) {
            double alcance = m.getBbWidth() * 2 + t.getBbWidth() + 0.6;
            if (m.distanceToSqr(t) <= alcance * alcance) {
                m.swing(InteractionHand.MAIN_HAND);
                t.hurt(m.damageSources().mobAttack(m), 3F);
            }
        }
        if (m.tickCount % 10 != 0) return;
        if (t == null) {
            LivingEntity a = d.getLastHurtByMob();
            if (valido(m, a, d)) t = a;
            else {
                LivingEntity b = d.getLastHurtMob();
                if (valido(m, b, d) && d.tickCount - d.getLastHurtMobTimestamp() < 200) t = b;
            }
            if (t != null) m.setTarget(t);
        }
        if (t != null) {
            if (sinAtaque) ir(m, t, 1.25);
            return;
        }
        double dist = m.distanceTo(d);
        if (dist > 28) {
            // demasiado lejos: aparece junto al dueño (como las mascotas)
            for (int i = 0; i < 8; i++) {
                Vec3 p = d.position().add(m.getRandom().nextInt(5) - 2, 0, m.getRandom().nextInt(5) - 2);
                if (sl.noCollision(m, m.getBoundingBox().move(p.subtract(m.position()))) && !sl.getBlockState(net.minecraft.core.BlockPos.containing(p).below()).isAir()) {
                    m.teleportTo(p.x, p.y, p.z);
                    m.getNavigation().stop();
                    break;
                }
            }
        } else if (dist > 5) {
            ir(m, d, 1.15);
        } else if (dist < 2.5) {
            m.getNavigation().stop();
        }
    }

    private static void ir(Mob m, Entity a, double vel) {
        if (m instanceof Villager v) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(a, (float) (vel * 0.6), 2));
        else m.getNavigation().moveTo(a, vel);
    }

    /** Nunca contra el dueño ni contra otros conversos suyos. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void objetivo(LivingChangeTargetEvent e) {
        LivingEntity n = e.getNewTarget();
        if (n == null || e.getEntity().level().isClientSide) return;
        if (aliados(e.getEntity(), n)) e.setCanceled(true);
    }
}
