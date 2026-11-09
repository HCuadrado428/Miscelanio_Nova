package com.miscelanea.armasfuego.comun;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.DanoArma;
import com.miscelanea.mixin.LivingEntityGolpeAccessor;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;

/**
 * Lo común de las armas de fuego nuevas en el servidor: el tiro instantáneo (traza contra bloques y criaturas), el
 * reparto del daño (una parte normal y otra que se salta la armadura, en el mismo golpe) y los efectos del impacto.
 */
public final class Balistica {
    private Balistica() {}

    /** La parte que atraviesa la armadura (en {@code #minecraft:bypasses_armor}): la comparten todas las nuevas. */
    public static final ResourceKey<DamageType> PERFORANTE = clave("armafuego_perforante");

    public static ResourceKey<DamageType> clave(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, n));
    }

    public static DamageSource fuente(Entity v, ResourceKey<DamageType> tipo, @Nullable Entity directo, @Nullable Entity tirador) {
        return new DamageSource(v.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(tipo), directo, tirador);
    }

    /**
     * Hiere a {@code v} con {@code total}: {@code (1 - perf)} con {@code tipo} y {@code perf} con la perforante, sin
     * empuje propio en la segunda parte. Con {@code rafaga} se salta la ventana de invulnerabilidad (armas automáticas:
     * si no, de cada dos balas una no haría nada). Devuelve si ha entrado.
     */
    public static boolean herir(LivingEntity v, @Nullable Entity tirador, float total, float perf, ResourceKey<DamageType> tipo, boolean rafaga) {
        if (total <= 0) return false;
        if (rafaga) v.invulnerableTime = 0;
        float p = total * perf;
        if (!v.hurt(fuente(v, tipo, tirador, tirador), total - p)) return false;
        if (p > 0.001F && v.isAlive()) {
            LivingEntityGolpeAccessor acc = (LivingEntityGolpeAccessor) v;
            int inv = v.invulnerableTime;
            float ultimo = acc.miscelanea$ultimoDano();
            Vec3 mov = v.getDeltaMovement();
            boolean marcado = v.hurtMarked;
            v.invulnerableTime = 0;
            DanoArma.extra = true;
            try {
                v.hurt(fuente(v, PERFORANTE, tirador, tirador), p);
            } finally {
                DanoArma.extra = false;
            }
            v.invulnerableTime = inv;
            acc.miscelanea$ponerUltimoDano(ultimo + p);
            v.setDeltaMovement(mov);
            v.hurtMarked = marcado;
        }
        return true;
    }

    /** Resultado de un tiro: lo alcanzado (o null) y dónde acabó. */
    public record Tiro(@Nullable LivingEntity entidad, Vec3 pos, @Nullable BlockHitResult bloque, double distancia) {}

    /**
     * Traza un tiro instantáneo desde {@code ojo} en {@code dir} (ya con la dispersión). {@code estela}: partícula de
     * la estela (null = sin estela) desde {@code boca}. No hiere: lo hace quien llama (cada arma su daño).
     */
    public static Tiro trazar(ServerLevel l, Entity tirador, Vec3 ojo, Vec3 dir, double alcance, Vec3 boca, @Nullable ParticleOptions estela) {
        Vec3 fin = ojo.add(dir.scale(alcance));
        BlockHitResult bh = l.clip(new ClipContext(ojo, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tirador));
        Vec3 hasta = bh.getType() == HitResult.Type.MISS ? fin : bh.getLocation();
        Entity montura = tirador.getVehicle();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(l, tirador, ojo, hasta, new AABB(ojo, hasta).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != tirador && e != montura);
        Vec3 donde = eh != null ? eh.getLocation() : hasta;
        if (estela != null) {
            double d = boca.distanceTo(donde);
            int n = (int) Math.min(10, d / 2.5);
            for (int k = 1; k <= n; k++) {
                Vec3 q = boca.lerp(donde, k / (double) (n + 1));
                l.sendParticles(estela, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        if (eh != null) return new Tiro((LivingEntity) eh.getEntity(), donde, null, ojo.distanceTo(donde));
        return new Tiro(null, donde, bh.getType() == HitResult.Type.MISS ? null : bh, ojo.distanceTo(donde));
    }

    /** Lo que pasa donde da la bala (polvo del bloque, chispa, campanas, cristal fino). */
    public static void impacto(ServerLevel l, Entity tirador, Tiro t) {
        Vec3 q = t.pos();
        if (t.entidad() != null) {
            l.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 4, 0.08, 0.08, 0.08, 0.2);
            l.gameEvent(tirador, GameEvent.PROJECTILE_LAND, q);
            return;
        }
        if (t.bloque() == null) return;
        BlockPos pos = t.bloque().getBlockPos();
        BlockState st = l.getBlockState(pos);
        if (!st.isAir()) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), q.x, q.y, q.z, 5, 0.05, 0.05, 0.05, 0.1);
        l.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.01);
        l.gameEvent(tirador, GameEvent.PROJECTILE_LAND, q);
        if (st.getBlock() instanceof BellBlock bell && tirador instanceof net.minecraft.world.entity.player.Player p)
            bell.attemptToRing(p, l, pos, t.bloque().getDirection());
        if (st.is(Tags.Blocks.GLASS_PANES) || st.is(BlockTags.FLOWER_POTS)) l.destroyBlock(pos, false, tirador);
        else if (l.random.nextInt(3) == 0)
            l.playSound(null, q.x, q.y, q.z, ModSounds.REVOLVER_REBOTE.get(), SoundSource.PLAYERS, 0.35F, 1.4F + l.random.nextFloat() * 0.4F);
    }

    /** La dirección de la vista con dispersión gaussiana (radianes). */
    public static Vec3 dispersar(Vec3 mira, double disp, RandomSource r) {
        if (disp <= 0) return mira;
        return mira.add(r.nextGaussian() * disp, r.nextGaussian() * disp, r.nextGaussian() * disp).normalize();
    }

    /** Daño que llega a distancia {@code d} de un arma con caída lineal desde {@code desde} hasta {@code alcance} (hasta {@code min}). */
    public static float caida(double d, double desde, double alcance, float min) {
        if (d <= desde) return 1F;
        double k = (d - desde) / Math.max(1, alcance - desde);
        return (float) Math.max(min, 1 - (1 - min) * k);
    }
}
