package com.miscelanea.llave;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lado servidor de los movimientos de la Llave Espada: golpes programados, hechizos, Strike Raid. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class CombateLlave {
    private record Tarea(ServerPlayer jugador, long cuando, Runnable r) {}

    private static final List<Tarea> TAREAS = new ArrayList<>();
    private static final Map<UUID, Long> LIBRE = new HashMap<>();

    private CombateLlave() {}

    public static void accion(ServerPlayer p, AccionLlave a, float yaw, int objetivo) {
        var s = p.getMainHandItem();
        if (!(s.getItem() instanceof LlaveEspadaItem) || p.isSpectator()) return;
        long ahora = p.level().getGameTime();
        if (ahora < LIBRE.getOrDefault(p.getUUID(), 0L) - 3) return;
        boolean lanzada = LlaveEspadaItem.lanzada(s);
        if (lanzada && a != AccionLlave.CURA && a != AccionLlave.PIRO && a != AccionLlave.HIELO) return;
        if (a.coste > 0 && !Magia.gastar(p, a.coste)) return;
        LIBRE.put(p.getUUID(), ahora + a.recuperacion);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new AnimPacket(p.getId(), a.ordinal()));
        Entity obj = objetivo >= 0 ? p.level().getEntity(objetivo) : null;
        for (AccionLlave.Golpe g : a.golpes) programar(p, g.tick(), () -> golpear(p, yaw, g, a));
        switch (a) {
            case STRIKE_RAID -> programar(p, 5, () -> lanzarLlave(p, yaw, obj));
            case PIRO -> programar(p, 4, () -> hechizo(p, yaw, obj, HechizoEntity.PIRO));
            case HIELO -> programar(p, 4, () -> hechizo(p, yaw, obj, HechizoEntity.HIELO));
            case CURA -> programar(p, 6, () -> cura(p));
            default -> {}
        }
    }

    private static void programar(ServerPlayer p, int ticks, Runnable r) {
        TAREAS.add(new Tarea(p, p.level().getGameTime() + ticks, r));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || TAREAS.isEmpty()) return;
        List<Tarea> listas = new ArrayList<>();
        for (Iterator<Tarea> it = TAREAS.iterator(); it.hasNext(); ) {
            Tarea t = it.next();
            if (t.jugador().isRemoved() || !t.jugador().isAlive()) {
                it.remove();
                continue;
            }
            if (t.jugador().level().getGameTime() >= t.cuando()) {
                listas.add(t);
                it.remove();
            }
        }
        for (Tarea t : listas) t.r().run();
    }

    private static void golpear(ServerPlayer p, float yaw, AccionLlave.Golpe g, AccionLlave a) {
        if (!(p.getMainHandItem().getItem() instanceof LlaveEspadaItem)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 delante = Vec3.directionFromRotation(0, yaw);
        boolean acerto = false;
        AABB caja = p.getBoundingBox().inflate(g.alcance() + 1, 2.0, g.alcance() + 1);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, caja, x -> x != p && x.isAlive() && !x.isSpectator())) {
            if (v instanceof TamableAnimal t && t.isOwnedBy(p)) continue;
            Vec3 d = v.position().subtract(p.position());
            double hd = d.horizontalDistance() - v.getBbWidth() * 0.5;
            if (hd > g.alcance() || Math.abs(d.y) > 2.6) continue;
            if (g.angulo() < 360 && hd > 0.6) {
                Vec3 h = new Vec3(d.x, 0, d.z).normalize();
                double ang = Math.acos(Mth.clamp(h.dot(delante), -1, 1)) * Mth.RAD_TO_DEG;
                if (ang > g.angulo() / 2) continue;
            }
            v.invulnerableTime = 0;
            if (!v.hurt(sl.damageSources().playerAttack(p), g.dano())) continue;
            acerto = true;
            efecto(p, v, g.efecto());
            double y = v.getY() + v.getBbHeight() * 0.5;
            sl.sendParticles(ParticleTypes.CRIT, v.getX(), y, v.getZ(), 6, 0.2, 0.2, 0.2, 0.3);
            sl.sendParticles(ParticleTypes.END_ROD, v.getX(), y, v.getZ(), 3, 0.1, 0.1, 0.1, 0.06);
        }
        boolean remate = g.efecto() != AccionLlave.Efecto.NORMAL;
        if (g.efecto() == AccionLlave.Efecto.EXPLOSION) {
            double y = p.getY() + 1;
            sl.sendParticles(ParticleTypes.FLASH, p.getX(), y, p.getZ(), 1, 0, 0, 0, 0);
            for (int i = 0; i < 28; i++) {
                double an = i / 28.0 * Math.PI * 2;
                sl.sendParticles(ParticleTypes.END_ROD, p.getX(), y, p.getZ(), 0, Math.cos(an), 0.05, Math.sin(an), 0.45);
            }
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), remate ? ModSounds.LLAVE_REMATE.get() : ModSounds.LLAVE_GOLPE.get(),
                SoundSource.PLAYERS, 0.8F, 0.9F + p.getRandom().nextFloat() * 0.25F);
        if (acerto) {
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LLAVE_IMPACTO.get(), SoundSource.PLAYERS, 0.9F, 0.9F + p.getRandom().nextFloat() * 0.2F);
            Magia.ganar(p, 2F);
        }
    }

    private static void efecto(ServerPlayer p, LivingEntity v, AccionLlave.Efecto e) {
        double dx = p.getX() - v.getX(), dz = p.getZ() - v.getZ();
        Vec3 fuera = new Vec3(-dx, 0, -dz);
        fuera = fuera.lengthSqr() < 1e-4 ? Vec3.ZERO : fuera.normalize();
        switch (e) {
            case NORMAL -> v.knockback(0.3, dx, dz);
            case LANZAR -> {
                v.setDeltaMovement(v.getDeltaMovement().x * 0.3 + fuera.x * 0.2, 0.85, v.getDeltaMovement().z * 0.3 + fuera.z * 0.2);
                v.hurtMarked = true;
            }
            case EXPLOSION -> {
                v.knockback(1.4, dx, dz);
                v.setDeltaMovement(v.getDeltaMovement().add(0, 0.35, 0));
                v.hurtMarked = true;
            }
            case ABAJO -> {
                v.setDeltaMovement(fuera.x * 0.7, v.onGround() ? 0.25 : -0.9, fuera.z * 0.7);
                v.hurtMarked = true;
            }
            case LEJOS -> {
                v.knockback(2.0, dx, dz);
                v.setDeltaMovement(v.getDeltaMovement().add(0, 0.45, 0));
                v.hurtMarked = true;
            }
        }
    }

    private static void lanzarLlave(ServerPlayer p, float yaw, Entity obj) {
        var s = p.getMainHandItem();
        if (!(s.getItem() instanceof LlaveEspadaItem) || LlaveEspadaItem.lanzada(s)) return;
        LlaveLanzada l = new LlaveLanzada(p.level(), p, obj instanceof LivingEntity le ? le : null);
        Vec3 dir = obj != null ? obj.position().add(0, obj.getBbHeight() * 0.5, 0).subtract(p.getEyePosition()).normalize()
                : Vec3.directionFromRotation(p.getXRot() * 0.5F, yaw);
        l.setPos(p.getX() + dir.x * 0.8, p.getEyeY() - 0.3, p.getZ() + dir.z * 0.8);
        l.setDeltaMovement(dir.scale(1.3));
        p.level().addFreshEntity(l);
        LlaveEspadaItem.ponerLanzada(s, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LLAVE_LANZAR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void hechizo(ServerPlayer p, float yaw, Entity obj, byte tipo) {
        Vec3 ojo = p.getEyePosition().add(0, -0.3, 0);
        Vec3 dir = obj != null ? obj.position().add(0, obj.getBbHeight() * 0.5, 0).subtract(ojo).normalize()
                : Vec3.directionFromRotation(p.getXRot() * 0.5F, yaw);
        float[] abanico = tipo == HechizoEntity.HIELO ? new float[]{-11, 0, 11} : new float[]{0};
        for (float a : abanico) {
            Vec3 d = dir.yRot(a * Mth.DEG_TO_RAD);
            HechizoEntity h = new HechizoEntity(p.level(), p, tipo);
            h.setPos(ojo.x + d.x * 0.9, ojo.y + d.y * 0.9, ojo.z + d.z * 0.9);
            h.shoot(d.x, d.y, d.z, tipo == HechizoEntity.PIRO ? 1.0F : 1.25F, 0);
            p.level().addFreshEntity(h);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), tipo == HechizoEntity.PIRO ? ModSounds.HECHIZO_PIRO.get() : ModSounds.HECHIZO_HIELO.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void cura(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        p.heal(8.0F);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
        for (Player o : sl.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(5), o -> o != p && o.isAlive())) o.heal(4.0F);
        double y = p.getY() + 0.1;
        for (int i = 0; i < 36; i++) {
            double an = i / 36.0 * Math.PI * 2;
            sl.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(an) * 1.6, y, p.getZ() + Math.sin(an) * 1.6, 1, 0, 0.15, 0, 0.02);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + Math.cos(an) * 1.2, y + 0.4 + (i % 4) * 0.3, p.getZ() + Math.sin(an) * 1.2, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.6, p.getZ(), 30, 0.4, 0.6, 0.4, 0.15);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.HECHIZO_CURA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
