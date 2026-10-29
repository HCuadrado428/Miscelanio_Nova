package com.miscelanea.hacha;

import com.miscelanea.Miscelanea;
import com.miscelanea.llave.CombateLlave;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lado servidor del Hacha Leviatán: golpes, lanzar/llamar, escudo y parry, esquivas, rúnicos y escarcha. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Leviatan {
    private static final Map<UUID, Long> LIBRE = new HashMap<>();
    private static final Map<UUID, Byte> POSTURA = new HashMap<>();
    private static final Map<UUID, Long> ESCUDO_DESDE = new HashMap<>();
    private static final Map<UUID, Long> INVULNERABLE = new HashMap<>();
    private static final Map<UUID, Integer> HACHAS = new HashMap<>();
    public static final int CD_LIGERO = 200, CD_PESADO = 400, ESCARCHA_TICKS = 300;

    private Leviatan() {}

    private static ItemStack hacha(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof HachaItem ? s : ItemStack.EMPTY;
    }

    public static HachaLanzada lanzada(ServerPlayer p) {
        Integer id = HACHAS.get(p.getUUID());
        if (id == null) return null;
        Entity e = p.level().getEntity(id);
        return e instanceof HachaLanzada h && h.isAlive() ? h : null;
    }

    /** Envía a los que ven al jugador (y a él mismo si 'yo') que ha hecho una acción, para la animación. */
    static void anunciar(ServerPlayer p, AccionHacha a, boolean yo) {
        var msg = new PosturaOtroPacket(p.getId(), (byte) 1, (byte) a.ordinal());
        ModNetwork.CHANNEL.send(yo ? PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p) : PacketDistributor.TRACKING_ENTITY.with(() -> p), msg);
    }

    public static void postura(ServerPlayer p, byte v) {
        if (hacha(p).isEmpty()) v = PosturaPacket.NADA;
        Byte antes = POSTURA.put(p.getUUID(), v);
        if (v == PosturaPacket.ESCUDO && (antes == null || antes != PosturaPacket.ESCUDO)) {
            ESCUDO_DESDE.put(p.getUUID(), p.level().getGameTime());
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ESCUDO_ABRIR.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new PosturaOtroPacket(p.getId(), (byte) 0, v));
    }

    public static void accion(ServerPlayer p, AccionHacha a, float yaw, float pitch, int objetivo) {
        ItemStack s = hacha(p);
        if (s.isEmpty() || p.isSpectator()) return;
        ServerLevel sl = p.serverLevel();
        long ahora = sl.getGameTime();
        boolean fuera = HachaItem.lanzada(s);
        if (a == AccionHacha.LLAMAR) {
            llamar(p, s);
            return;
        }
        if (ahora < LIBRE.getOrDefault(p.getUUID(), 0L) - 3) return;
        if (fuera && !a.sinHacha && a != AccionHacha.ESQUIVA && a != AccionHacha.VOLTERETA) return;
        if (!fuera && (a == AccionHacha.PUNO1 || a == AccionHacha.PUNO2 || a == AccionHacha.PUNO3)) return;
        var tag = s.getOrCreateTag();
        if (a.runicoLigero() || a.runicoPesado()) {
            String cd = a.runicoLigero() ? HachaItem.CD_LIGERO : HachaItem.CD_PESADO;
            if (tag.getLong(cd) > ahora && !p.getAbilities().instabuild) return;
            tag.putLong(cd, ahora + (a.runicoLigero() ? CD_LIGERO : CD_PESADO));
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.RUNICO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        LIBRE.put(p.getUUID(), ahora + a.recuperacion);
        anunciar(p, a, false);
        for (AccionHacha.Golpe g : a.golpes) CombateLlave.programar(p, g.tick(), () -> golpear(p, yaw, g));
        switch (a) {
            case LANZAR_LIGERO, LANZAR_PESADO -> CombateLlave.programar(p, 3, () -> lanzar(p, yaw, pitch, a == AccionHacha.LANZAR_PESADO));
            case ESQUIVA -> INVULNERABLE.put(p.getUUID(), ahora + 5);
            case VOLTERETA -> INVULNERABLE.put(p.getUUID(), ahora + 10);
            case RUN_RIOS -> rios(p, yaw);
            case RUN_HEL -> CombateLlave.programar(p, 6, () -> anillo(p, 5.0));
            case ESCARCHA -> CombateLlave.programar(p, 12, () -> {
                ItemStack h = hacha(p);
                if (h.isEmpty()) return;
                h.getOrCreateTag().putLong(HachaItem.ESCARCHA, sl.getGameTime() + ESCARCHA_TICKS);
                sl.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.6, 0.6, 0.6, 0.05);
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ESCARCHA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            });
            default -> {}
        }
    }

    // ------------------------------------------------------------------------------------------- golpes
    private static void golpear(ServerPlayer p, float yaw, AccionHacha.Golpe g) {
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof HachaItem)) return;
        ServerLevel sl = p.serverLevel();
        boolean puno = g.efecto() == AccionHacha.Efecto.PUNO || (HachaItem.lanzada(s) && g.efecto() == AccionHacha.Efecto.LANZAR);
        boolean helada = !puno && HachaItem.escarcha(s, sl);
        Vec3 delante = Vec3.directionFromRotation(0, yaw);
        boolean acerto = false;
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(g.alcance() + 1, 2.0, g.alcance() + 1),
                x -> x != p && x.isAlive() && !x.isSpectator())) {
            if (v instanceof TamableAnimal t && t.isOwnedBy(p)) continue;
            Vec3 d = v.position().subtract(p.position());
            double hd = d.horizontalDistance() - v.getBbWidth() * 0.5;
            if (hd > g.alcance() || Math.abs(d.y) > 2.6) continue;
            if (g.angulo() < 360 && hd > 0.6) {
                Vec3 h = new Vec3(d.x, 0, d.z).normalize();
                if (Math.acos(Mth.clamp(h.dot(delante), -1, 1)) * Mth.RAD_TO_DEG > g.angulo() / 2) continue;
            }
            v.invulnerableTime = 0;
            float dano = g.dano() + (helada ? 3 : 0);
            if (!v.hurt(sl.damageSources().playerAttack(p), dano)) continue;
            acerto = true;
            efecto(p, v, g.efecto());
            if (helada) helar(v, 60, 1);
            double y = v.getY() + v.getBbHeight() * 0.5;
            sl.sendParticles(ParticleTypes.CRIT, v.getX(), y, v.getZ(), 6, 0.2, 0.2, 0.2, 0.3);
            if (!puno) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), v.getX(), y, v.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
        if (g.efecto() == AccionHacha.Efecto.ESCARCHA || g.efecto() == AccionHacha.Efecto.ONDA || g.efecto() == AccionHacha.Efecto.CONGELAR) {
            Vec3 c = p.position().add(delante.scale(1.6));
            sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.3, c.z, 30, 0.8, 0.2, 0.8, 0.08);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), c.x, c.y + 0.2, c.z, 20, 0.6, 0.1, 0.6, 0.15);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), puno ? ModSounds.PUNETAZO.get() : ModSounds.HACHA_GOLPE.get(), SoundSource.PLAYERS,
                0.9F, 0.85F + p.getRandom().nextFloat() * 0.25F);
        if (acerto && !puno) sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.HACHA_IMPACTO.get(), SoundSource.PLAYERS, 1.0F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        if (acerto && puno) sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.PUNETAZO.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    static void efecto(ServerPlayer p, LivingEntity v, AccionHacha.Efecto e) {
        double dx = p.getX() - v.getX(), dz = p.getZ() - v.getZ();
        switch (e) {
            case NORMAL -> v.knockback(0.35, dx, dz);
            case PUNO -> {
                v.knockback(0.2, dx, dz);
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
            }
            case ESCARCHA -> {
                v.knockback(0.6, dx, dz);
                helar(v, 80, 2);
            }
            case ONDA -> {
                v.knockback(1.3, dx, dz);
                v.setDeltaMovement(v.getDeltaMovement().add(0, 0.3, 0));
                v.hurtMarked = true;
            }
            case LANZAR -> {
                v.setDeltaMovement(v.getDeltaMovement().x * 0.3, 0.8, v.getDeltaMovement().z * 0.3);
                v.hurtMarked = true;
            }
            case CONGELAR -> {
                v.knockback(0.8, dx, dz);
                helar(v, 100, 5);
            }
        }
    }

    /** Escarcha: ralentiza y deja el cuerpo escarchado (como la nieve polvo). */
    public static void helar(LivingEntity v, int ticks, int nivel) {
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, nivel));
        v.setTicksFrozen(Math.max(v.getTicksFrozen(), v.getTicksRequiredToFreeze() + ticks));
    }

    /** Hel's Touch: onda expansiva en anillo. */
    private static void anillo(ServerPlayer p, double radio) {
        ServerLevel sl = p.serverLevel();
        for (int r = 1; r <= (int) radio; r++) {
            final double rr = r;
            CombateLlave.programar(p, r - 1, () -> {
                for (int i = 0; i < 12 + rr * 6; i++) {
                    double an = i / (12 + rr * 6) * Math.PI * 2;
                    double x = p.getX() + Math.cos(an) * rr, z = p.getZ() + Math.sin(an) * rr;
                    sl.sendParticles(ParticleTypes.SNOWFLAKE, x, p.getY() + 0.2, z, 1, 0, 0.05, 0, 0.02);
                    if (i % 3 == 0) sl.sendParticles(ParticleTypes.CLOUD, x, p.getY() + 0.15, z, 1, 0, 0, 0, 0.01);
                }
            });
        }
    }

    /** River of Knives: una línea de cristales de hielo que avanza y lanza por los aires. */
    private static void rios(ServerPlayer p, float yaw) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        Vec3 inicio = p.position();
        for (int i = 1; i <= 11; i++) {
            final int k = i;
            CombateLlave.programar(p, 7 + i, () -> {
                Vec3 q = inicio.add(dir.scale(k * 0.95));
                BlockPos suelo = BlockPos.containing(q.x, q.y - 0.5, q.z);
                double y = sl.getBlockState(suelo).isAir() ? q.y - 1 : q.y;
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), q.x, y + 0.4, q.z, 18, 0.25, 0.5, 0.25, 0.2);
                sl.sendParticles(ParticleTypes.SNOWFLAKE, q.x, y + 0.6, q.z, 8, 0.2, 0.6, 0.2, 0.05);
                sl.sendParticles(ParticleTypes.END_ROD, q.x, y + 0.2, q.z, 3, 0.1, 0.4, 0.1, 0.02);
                if (k % 3 == 1) sl.playSound(null, q.x, q.y, q.z, ModSounds.ESCARCHA.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
                for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(1.2, 1.5, 1.2), x -> x != p && x.isAlive())) {
                    v.invulnerableTime = 0;
                    if (v.hurt(sl.damageSources().playerAttack(p), 9.0F)) {
                        efecto(p, v, AccionHacha.Efecto.LANZAR);
                        helar(v, 80, 2);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------------------------------- lanzar y llamar
    private static void lanzar(ServerPlayer p, float yaw, float pitch, boolean pesado) {
        ItemStack s = hacha(p);
        if (s.isEmpty() || HachaItem.lanzada(s)) return;
        HachaLanzada h = new HachaLanzada(p.level(), p, pesado, HachaItem.escarcha(s, p.level()));
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 der = dir.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? Vec3.ZERO : der.normalize();
        h.setPos(p.getX() + der.x * 0.35 + dir.x * 0.6, p.getEyeY() - 0.2 + dir.y * 0.6, p.getZ() + der.z * 0.35 + dir.z * 0.6);
        h.setDeltaMovement(dir.scale(1.9));
        p.level().addFreshEntity(h);
        HACHAS.put(p.getUUID(), h.getId());
        HachaItem.ponerLanzada(s, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.HACHA_LANZAR.get(), SoundSource.PLAYERS, 1.0F, pesado ? 0.85F : 1.1F);
    }

    private static void llamar(ServerPlayer p, ItemStack s) {
        if (!HachaItem.lanzada(s)) return;
        HachaLanzada h = lanzada(p);
        if (h == null) {
            atrapar(p);
            return;
        }
        h.volver();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.HACHA_VOLVER.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** El hacha vuelve a la mano. */
    static void atrapar(ServerPlayer p) {
        HACHAS.remove(p.getUUID());
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof HachaItem) HachaItem.ponerLanzada(s, false);
        ItemStack o = p.getOffhandItem();
        if (o.getItem() instanceof HachaItem) HachaItem.ponerLanzada(o, false);
        if (p.getMainHandItem().getItem() instanceof HachaItem) {
            anunciar(p, AccionHacha.ATRAPAR, true);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.HACHA_ATRAPAR.get(), SoundSource.PLAYERS, 1.1F, 1.0F);
        }
    }

    static void olvidar(ServerPlayer p, HachaLanzada h) {
        Integer id = HACHAS.get(p.getUUID());
        if (id != null && id == h.getId()) HACHAS.remove(p.getUUID());
    }

    // ------------------------------------------------------------------------------------------- escudo, parry y esquivas
    @SubscribeEvent
    public static void ataque(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || hacha(p).isEmpty()) return;
        long ahora = p.level().getGameTime();
        if (INVULNERABLE.getOrDefault(p.getUUID(), 0L) > ahora) {
            e.setCanceled(true);
            return;
        }
        if (POSTURA.getOrDefault(p.getUUID(), PosturaPacket.NADA) != PosturaPacket.ESCUDO) return;
        DamageSource src = e.getSource();
        Vec3 desde = src.getSourcePosition();
        if (desde == null || src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) return;
        Vec3 d = desde.subtract(p.position());
        Vec3 h = new Vec3(d.x, 0, d.z);
        if (h.lengthSqr() < 1e-4) return;
        Vec3 mira = Vec3.directionFromRotation(0, p.getYRot());
        if (h.normalize().dot(mira) < 0.25) return;   // solo por delante
        e.setCanceled(true);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(mira.scale(0.7)).add(0, 1.1, 0);
        boolean parry = ahora - ESCUDO_DESDE.getOrDefault(p.getUUID(), -100L) <= 6;
        if (parry && src.getEntity() instanceof LivingEntity atacante && src.getDirectEntity() == atacante) {
            atacante.knockback(1.1, p.getX() - atacante.getX(), p.getZ() - atacante.getZ());
            atacante.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 4));
            atacante.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            atacante.hurtMarked = true;
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.4);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ESCUDO_PARRY.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            anunciar(p, AccionHacha.PARRY, true);
        } else {
            sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.2);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ESCUDO_BLOQUEO.get(), SoundSource.PLAYERS, 1.0F, 0.9F + p.getRandom().nextFloat() * 0.2F);
            p.setDeltaMovement(p.getDeltaMovement().add(mira.scale(-0.15)));
            p.hurtMarked = true;
        }
    }
}
