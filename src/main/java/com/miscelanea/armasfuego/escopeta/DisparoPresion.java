package com.miscelanea.armasfuego.escopeta;

import com.miscelanea.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lo que pasa en el servidor con la escopeta de presión: bombear, disparar (perdigones o soplido), aspirar y reventar. */
public final class DisparoPresion {
    private DisparoPresion() {}

    /** Daño de un perdigón de grava a fuerza 1 (100 de presión) y a quemarropa. */
    public static float danoPerdigon() {
        return com.miscelanea.armasfuego.comun.EquilibrioArmas.escopetaPerdigon();
    }

    // ================================================================ bombear

    public static void bombear(ServerPlayer p, ItemStack s) {
        ServerLevel sl = p.serverLevel();
        if (Presion.reventada(s, sl) > 0) {
            p.stopUsingItem();
            return;
        }
        RandomSource r = p.getRandom();
        float tope = Presion.tope(s), pr = Presion.presion(s);
        boolean roja = pr / tope >= Presion.ZONA_ROJA;
        // en la zona roja la aguja tiembla: cada bombeo es una lotería
        float mas = roja ? 4 + r.nextFloat() * 8 : 7 + r.nextFloat() * 3;
        pr += mas;
        s.getOrCreateTag().remove(Presion.DISPAROS);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HORSE_BREATHE, SoundSource.PLAYERS, 0.55F, 1.25F + Math.min(pr / tope, 1) * 0.5F);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.12F, 1.9F);
        p.causeFoodExhaustion(0.03F);
        if (pr > tope) {
            if (Presion.nivel(s, Presion.Encantamiento.VALVULA) > 0) {
                // la válvula de seguridad suelta lo que sobra
                pr = tope;
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.9F);
                Vec3 m = boca(p);
                sl.sendParticles(ParticleTypes.CLOUD, m.x, m.y + 0.2, m.z, 4, 0.05, 0.1, 0.05, 0.03);
            } else {
                reventar(p, s);
                return;
            }
        } else if (pr / tope >= Presion.ZONA_ROJA) {
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.08F, 2.0F);
        }
        Presion.ponerPresion(s, pr);
    }

    public static void reventar(ServerPlayer p, ItemStack s) {
        ServerLevel sl = p.serverLevel();
        Vec3 m = boca(p);
        sl.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.POOF, m.x, m.y, m.z, 14, 0.2, 0.2, 0.2, 0.12);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 8, 0.15, 0.15, 0.15, 0.03);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 1.35F);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.9F, 0.6F);
        sl.gameEvent(p, GameEvent.EXPLODE, m);
        p.stopUsingItem();
        p.hurt(DanoEscopeta.fuente(p, DanoEscopeta.REVENTON, p), 4.0F);
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "escopeta_reventon");
        Vec3 atras = p.getViewVector(1.0F).scale(-1.1).add(0, 0.35, 0);
        p.setDeltaMovement(p.getDeltaMovement().add(atras));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        Presion.ponerPresion(s, 0);
        s.getOrCreateTag().remove(Presion.DISPAROS);
        s.getOrCreateTag().putLong(Presion.REVENTADA, sl.getGameTime() + Presion.HUMEO);
        p.getCooldowns().addCooldown(s.getItem(), Presion.HUMEO);
        s.hurtAndBreak(15, p, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    // ================================================================ disparar

    public static void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel sl = p.serverLevel();
        if (p.getCooldowns().isOnCooldown(s.getItem()) || Presion.reventada(s, sl) > 0 || p.isUsingItem()) return;
        float pr = Presion.presion(s);
        if (pr < Presion.MINIMA || Presion.disparosQuedan(s) <= 0) {
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.6F);
            p.getCooldowns().addCooldown(s.getItem(), 6);
            return;
        }
        RandomSource r = p.getRandom();
        int ox = Presion.oxido(s);
        // el cobre oxidado a veces se encasquilla: se escapa el aire por la junta
        if (r.nextFloat() < 0.03F * ox + com.miscelanea.armasfuego.comun.EquilibrioArmas.encasquilladoBase()) {
            Vec3 m = boca(p);
            sl.sendParticles(ParticleTypes.CLOUD, m.x, m.y, m.z, 6, 0.1, 0.1, 0.1, 0.02);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.5F);
            Presion.ponerPresion(s, pr * 0.5F);
            p.getCooldowns().addCooldown(s.getItem(), 10);
            return;
        }
        float fuerza = pr / 100F;
        TipoCartucho t = Presion.tolva(s);
        if (t == null) soplido(p, s, fuerza);
        else {
            perdigones(p, s, t, fuerza);
            boolean salva = t.reciclable && r.nextFloat() < 0.15F * Presion.nivel(s, Presion.Encantamiento.RECICLAJE);
            if (!p.getAbilities().instabuild && !salva) Presion.ponerTolva(s, t, Presion.tolvaN(s) - 1);
        }
        // presión gastada (con Doble cámara queda para otro disparo)
        int hechos = Presion.disparosHechos(s) + 1;
        if (hechos >= Presion.disparosPorBombeada(s)) {
            Presion.ponerPresion(s, 0);
            s.getOrCreateTag().remove(Presion.DISPAROS);
        } else s.getOrCreateTag().putInt(Presion.DISPAROS, hechos);
        // el cobre se oxida con el uso (antes si se usa mojado)
        if (!Presion.encerada(s) && ox < 3 && r.nextFloat() < (p.isInWaterOrRain() ? 1 / 15F : 1 / 45F)) Presion.ponerOxido(s, ox + 1);
        s.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        p.getCooldowns().addCooldown(s.getItem(), t == null ? 6 : 10);
    }

    /** Dónde sale el disparo (un poco a la derecha y abajo de los ojos). */
    static Vec3 boca(Entity p) {
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        return p.getEyePosition().add(mira.scale(0.9)).add(der.scale(0.25)).add(0, -0.25, 0);
    }

    // ---------------------------------------------------------------- perdigones

    private static void perdigones(ServerPlayer p, ItemStack s, TipoCartucho t, float fuerza) {
        ServerLevel sl = p.serverLevel();
        RandomSource r = p.getRandom();
        Vec3 ojo = p.getEyePosition(), mira = p.getViewVector(1.0F), boca = boca(p);
        float f = Math.min(fuerza, 1.3F);
        double disp = 0.075 * (1 + 0.15 * Presion.oxido(s)) * (1.3 - 0.5 * f);   // más presión, más agrupado
        double alcance = (6 + 20 * f) * t.alcance;
        Map<LivingEntity, float[]> golpes = new LinkedHashMap<>();   // daño, perdigones
        List<BlockHitResult> bloques = new ArrayList<>();
        ClipContext.Block forma = t == TipoCartucho.SEMILLAS ? ClipContext.Block.OUTLINE : ClipContext.Block.COLLIDER;
        for (int i = 0; i < t.perdigones; i++) {
          Vec3 dir = mira.add(r.nextGaussian() * disp, r.nextGaussian() * disp, r.nextGaussian() * disp).normalize();
          Vec3 desde = ojo, boca0 = boca;
          double resto = alcance;
          // la goma rebota hasta dos veces en los bloques (con lo que le quede de alcance)
          for (int rebote = 0; rebote <= (t == TipoCartucho.GOMA ? 2 : 0); rebote++) {
            final boolean rebotado = rebote > 0;
            final Vec3 o0 = desde;
            Vec3 fin = desde.add(dir.scale(resto));
            BlockHitResult bh = sl.clip(new ClipContext(desde, fin, forma, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) fin = bh.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, desde, fin, new AABB(desde, fin).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && (e != p || rebotado), o0.distanceToSqr(fin));
            Vec3 impacto;
            if (eh != null && eh.getEntity() instanceof LivingEntity v) {
                impacto = eh.getLocation();
                double d = alcance - resto + o0.distanceTo(impacto);
                float caida = d < 4 ? 1.0F : (float) Mth.clampedLerp(1.0, 0.3, (d - 4) / Math.max(1, alcance - 4));
                float[] g = golpes.computeIfAbsent(v, k -> new float[3]);
                g[0] += danoPerdigon() * f * t.dano * caida;
                if (rebotado) g[2] = 1;
                g[1] += 1;
            } else {
                impacto = fin;
                if (bh.getType() != HitResult.Type.MISS) {
                    bloques.add(bh);
                    BlockState st = sl.getBlockState(bh.getBlockPos());
                    if (!st.isAir() && t == TipoCartucho.GRAVA)
                        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), impacto.x, impacto.y, impacto.z, 3, 0.05, 0.05, 0.05, 0.1);
                }
            }
            impactoParticulas(sl, t, impacto, r);
            // estela del perdigón
            for (int k = 1; k <= 3; k++) {
                Vec3 q = boca0.lerp(impacto, k / 4.0 * r.nextDouble());
                sl.sendParticles(estela(t, r), q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            if (t != TipoCartucho.GOMA || eh != null || bh.getType() == HitResult.Type.MISS) break;
            resto -= desde.distanceTo(impacto);
            if (resto < 1) break;
            Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            dir = dir.subtract(n.scale(2 * dir.dot(n))).normalize();
            desde = impacto.add(n.scale(0.02));
            boca0 = desde;
            if (rebote == 0 && i % 3 == 0) sl.playSound(null, impacto.x, impacto.y, impacto.z, com.miscelanea.registry.ModSounds.GOMA_REBOTE.get(), SoundSource.PLAYERS, 0.6F, 0.9F + r.nextFloat() * 0.4F);
          }
        }
        for (Map.Entry<LivingEntity, float[]> e : golpes.entrySet()) {
            alcanzar(p, t, e.getKey(), e.getValue()[0], (int) e.getValue()[1], fuerza, mira);
            if (e.getValue()[2] > 0 && e.getKey() != p) com.miscelanea.logros.LogrosMiscelanea.dar(p, "escopeta_goma");
        }
        Set<BlockPos> hechos = new HashSet<>();
        int nieve = 0;
        for (BlockHitResult bh : bloques) {
            if (!hechos.add(bh.getBlockPos())) continue;
            nieve += bloque(p, t, bh, fuerza, nieve);
        }
        // fogonazo de aire, ruido y retroceso
        sl.sendParticles(ParticleTypes.CLOUD, boca.x, boca.y, boca.z, 3, 0.05, 0.05, 0.05, 0.05);
        sl.sendParticles(ParticleTypes.POOF, boca.x, boca.y, boca.z, 2, 0.03, 0.03, 0.03, 0.03);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), com.miscelanea.registry.ModSounds.PRESION_DISPARO.get(), SoundSource.PLAYERS, 3.0F, 0.95F + r.nextFloat() * 0.1F);
        if (t == TipoCartucho.CONFETI) {
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5F, 1.2F);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.2F, 1.0F);
        }
        sl.gameEvent(p, GameEvent.PROJECTILE_SHOOT, boca);
        p.setDeltaMovement(p.getDeltaMovement().add(mira.scale(-0.12 * f)));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    private static ParticleOptions estela(TipoCartucho t, RandomSource r) {
        return switch (t) {
            case GRAVA -> ParticleTypes.SMOKE;
            case INCENDIARIO -> ParticleTypes.FLAME;
            case ESCARCHA -> ParticleTypes.SNOWFLAKE;
            case MIEL -> ParticleTypes.FALLING_HONEY;
            case SAL -> ParticleTypes.WHITE_ASH;
            case SEMILLAS -> new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.WHEAT_SEEDS));
            case CONFETI -> confeti(r);
            case GOMA -> ParticleTypes.CRIT;
        };
    }

    private static final int[] COLORES_CONFETI = {0xF06FB4, 0xFFE84A, 0x4AD8FF, 0x7CF04A, 0xB44FD0, 0xFF7A2A, 0xFFFFFF};

    private static ParticleOptions confeti(RandomSource r) {
        int c = COLORES_CONFETI[r.nextInt(COLORES_CONFETI.length)];
        return new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F), 1.0F);
    }

    private static void impactoParticulas(ServerLevel sl, TipoCartucho t, Vec3 q, RandomSource r) {
        switch (t) {
            case INCENDIARIO -> sl.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 3, 0.08, 0.08, 0.08, 0.02);
            case ESCARCHA -> sl.sendParticles(ParticleTypes.SNOWFLAKE, q.x, q.y, q.z, 4, 0.1, 0.1, 0.1, 0.02);
            case MIEL -> sl.sendParticles(ParticleTypes.DRIPPING_HONEY, q.x, q.y, q.z, 3, 0.1, 0.1, 0.1, 0);
            case SAL -> sl.sendParticles(ParticleTypes.WHITE_ASH, q.x, q.y, q.z, 6, 0.15, 0.15, 0.15, 0.02);
            case SEMILLAS -> sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.WHEAT_SEEDS)), q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0.05);
            case CONFETI -> {
                for (int k = 0; k < 4; k++) sl.sendParticles(confeti(r), q.x, q.y, q.z, 2, 0.3, 0.3, 0.3, 0);
            }
            default -> sl.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.1);
        }
    }

    /** Lo que le hace el cartucho a un ser vivo alcanzado por {@code n} perdigones que suman {@code dano}. */
    private static void alcanzar(ServerPlayer p, TipoCartucho t, LivingEntity v, float dano, int n, float fuerza, Vec3 mira) {
        ServerLevel sl = p.serverLevel();
        boolean entra = dano > 0 && DanoEscopeta.golpear(v, sl.damageSources().playerAttack(p), dano);
        if (entra) v.knockback(Math.min(1.2, 0.2 + dano / 12.0), -mira.x, -mira.z);
        int segundos = 60 + (int) (60 * Math.min(fuerza, 1.3F));
        switch (t) {
            case INCENDIARIO -> v.setSecondsOnFire(3 + (int) (4 * Math.min(fuerza, 1.3F)));
            case ESCARCHA -> {
                v.clearFire();
                v.setTicksFrozen(Math.min(v.getTicksRequiredToFreeze() + 120, v.getTicksFrozen() + 60 + 15 * n));
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), p);
            }
            case MIEL -> {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, segundos, 3), p);
                v.addEffect(new MobEffectInstance(ModEffects.PRINGADO.get(), segundos), p);
            }
            case SAL -> {
                // escuece: el respingo y el quejido, pero sin daño (salvo a los slimes, que la sal les sienta fatal)
                if (v instanceof Slime) DanoEscopeta.golpear(v, sl.damageSources().playerAttack(p), n);
                else sl.broadcastDamageEvent(v, sl.damageSources().playerAttack(p));
                double k = (0.4 + 0.8 * Math.min(fuerza, 1.3F)) * (1 - v.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                v.setDeltaMovement(v.getDeltaMovement().add(mira.x * k, 0.25 * k, mira.z * k));
                v.hurtMarked = true;
            }
            case SEMILLAS -> {
                if (v instanceof Animal a && a.isFood(new ItemStack(Items.WHEAT_SEEDS)) && a.canFallInLove() && a.getAge() == 0) a.setInLove(p);
            }
            case CONFETI -> sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + v.getBbHeight() * 0.8, v.getZ(), 4, 0.3, 0.3, 0.3, 0);
            case GOMA -> {
                // las bolas de goma empujan mucho más que la grava
                double k = (0.3 + 0.12 * n) * Math.min(fuerza, 1.3F) * (1 - v.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                v.setDeltaMovement(v.getDeltaMovement().add(mira.x * k, 0.2 * k, mira.z * k));
                v.hurtMarked = true;
            }
            default -> {}
        }
    }

    /** Lo que le hace el cartucho a un bloque alcanzado. Devuelve cuánta nieve en polvo ha puesto. */
    private static int bloque(ServerPlayer p, TipoCartucho t, BlockHitResult bh, float fuerza, int nieveYa) {
        ServerLevel sl = p.serverLevel();
        BlockPos pos = bh.getBlockPos();
        BlockState st = sl.getBlockState(pos);
        BlockPos fuera = pos.relative(bh.getDirection());
        switch (t) {
            case INCENDIARIO -> {
                if (CampfireBlock.canLight(st) || CandleBlock.canLight(st) || CandleCakeBlock.canLight(st)) {
                    sl.setBlock(pos, st.setValue(BlockStateProperties.LIT, true), 11);
                    sl.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
                    sl.gameEvent(p, GameEvent.BLOCK_CHANGE, pos);
                } else if (st.getBlock() instanceof TntBlock) {
                    st.onCaughtFire(sl, pos, bh.getDirection(), p);
                    sl.removeBlock(pos, false);
                }
            }
            case ESCARCHA -> {
                apagar(p, sl, pos);
                apagar(p, sl, fuera);
                if (nieveYa < 2 && fuerza >= 0.3F && bh.getDirection() == Direction.UP && st.isFaceSturdy(sl, pos, Direction.UP)
                        && sl.getBlockState(fuera).canBeReplaced() && sl.getFluidState(fuera).isEmpty()) {
                    sl.setBlockAndUpdate(fuera, Blocks.POWDER_SNOW.defaultBlockState());
                    sl.playSound(null, fuera, SoundEvents.POWDER_SNOW_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                    return 1;
                }
            }
            case SAL -> {
                // la sal derrite la nieve y el hielo
                for (BlockPos q : new BlockPos[]{pos, fuera}) {
                    BlockState b = sl.getBlockState(q);
                    if (b.is(Blocks.SNOW) || b.is(Blocks.POWDER_SNOW)) sl.removeBlock(q, false);
                    else if ((b.is(Blocks.ICE) || b.is(Blocks.FROSTED_ICE)) && !sl.dimensionType().ultraWarm())
                        sl.setBlockAndUpdate(q, Blocks.WATER.defaultBlockState());
                    else continue;
                    sl.playSound(null, q, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.6F);
                }
            }
            case SEMILLAS -> {
                // siembra en la tierra de cultivo y abona lo que crece
                BlockPos crece = pos;
                if (st.getBlock() instanceof FarmBlock) {
                    BlockPos arriba = pos.above();
                    if (sl.getBlockState(arriba).isAir()) {
                        sl.setBlockAndUpdate(arriba, Blocks.WHEAT.defaultBlockState());
                        sl.playSound(null, arriba, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.0F);
                        com.miscelanea.logros.LogrosMiscelanea.dar(p, "escopeta_semillas");
                        return 0;
                    }
                    crece = arriba;
                }
                if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), sl, crece)) sl.levelEvent(1505, crece, 0);
            }
            default -> {}
        }
        return 0;
    }

    private static void apagar(ServerPlayer p, ServerLevel sl, BlockPos q) {
        BlockState b = sl.getBlockState(q);
        if (b.is(BlockTags.FIRE)) {
            sl.removeBlock(q, false);
            sl.levelEvent(null, 1009, q, 0);
        } else if (b.getBlock() instanceof CampfireBlock && b.getValue(CampfireBlock.LIT)) {
            CampfireBlock.dowse(p, sl, q, b);
            sl.setBlock(q, b.setValue(CampfireBlock.LIT, false), 11);
            sl.levelEvent(null, 1009, q, 0);
        } else if (b.getBlock() instanceof AbstractCandleBlock && b.hasProperty(BlockStateProperties.LIT) && b.getValue(BlockStateProperties.LIT)) {
            AbstractCandleBlock.extinguish(p, b, sl, q);
        }
    }

    // ---------------------------------------------------------------- soplido

    /** Disparo sin cartucho: suelta el aire. Empuja, apaga el fuego, frena la caída y con mucha presión te lanza. */
    private static void soplido(ServerPlayer p, ItemStack s, float fuerza) {
        ServerLevel sl = p.serverLevel();
        RandomSource r = p.getRandom();
        int fuelle = Presion.nivel(s, Presion.Encantamiento.FUELLE);
        float f = Math.min(fuerza, 1.3F);
        double radio = 3 + 5 * f + 1.5 * fuelle;
        Vec3 ojo = p.getEyePosition(), mira = p.getViewVector(1.0F), boca = boca(p);
        // empuja todo lo que haya en el cono
        for (Entity e : sl.getEntities(p, p.getBoundingBox().inflate(radio), e -> e.isAlive() && !e.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 d = c.subtract(ojo);
            double dist = d.length();
            if (dist > radio || dist < 1e-3 || d.scale(1 / dist).dot(mira) < 0.6) continue;
            if (e instanceof LivingEntity && sl.clip(new ClipContext(ojo, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS)
                continue;
            double k = (0.4 + 1.4 * f) * (1 + 0.25 * fuelle) * (1 - 0.5 * dist / radio);
            if (e instanceof LivingEntity v) k *= 1 - v.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            Vec3 emp = d.scale(1 / dist).scale(k);
            e.setDeltaMovement(e.getDeltaMovement().add(emp.x, Math.max(emp.y, 0) + 0.12 * k, emp.z));
            e.hasImpulse = true;
            e.hurtMarked = true;
            e.clearFire();
            if (e instanceof ServerPlayer otro) otro.connection.send(new ClientboundSetEntityMotionPacket(otro));
        }
        // apaga el fuego, las hogueras y las velas del cono
        BlockPos o = BlockPos.containing(ojo);
        int R = (int) Math.ceil(radio);
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-R, -R, -R), o.offset(R, R, R))) {
            Vec3 d = Vec3.atCenterOf(q).subtract(ojo);
            double dist = d.length();
            if (dist > radio || dist < 0.5 || d.scale(1 / dist).dot(mira) < 0.6) continue;
            BlockState b = sl.getBlockState(q);
            if (b.isAir() && !b.is(BlockTags.FIRE)) continue;
            apagar(p, sl, q.immutable());
        }
        // la nube de aire
        for (int i = 0; i < 18; i++) {
            Vec3 dir = mira.add(r.nextGaussian() * 0.25, r.nextGaussian() * 0.25, r.nextGaussian() * 0.25).normalize();
            sl.sendParticles(ParticleTypes.CLOUD, boca.x, boca.y, boca.z, 0, dir.x, dir.y, dir.z, 0.25 + 0.5 * f);
        }
        // y el retroceso: frena la caída mirando abajo y, con mucha presión, te lanza (salto escopeta)
        double k = f >= 0.6 ? (0.5 + 1.8 * (f - 0.6)) * (1 + 0.15 * fuelle) : 0.15 * f;
        Vec3 atras = mira.scale(-k);
        Vec3 v = p.getDeltaMovement();
        if (p.getXRot() > 50) {
            v = new Vec3(v.x + atras.x, Math.max(v.y, 0) + Math.max(atras.y, 0.15 + 0.3 * f), v.z + atras.z);
            p.fallDistance = 0;
            if (f >= 0.6) com.miscelanea.logros.LogrosMiscelanea.dar(p, "escopeta_salto");   // el salto escopeta de verdad (no solo frenar la caída)
        } else {
            v = v.add(atras);
            if (atras.y > 0.2) p.fallDistance = 0;
        }
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        // menos ruido que un disparo
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.7F + r.nextFloat() * 0.2F);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.9F);
        sl.gameEvent(p, GameEvent.FLAP, boca);
    }

    // ================================================================ aspirar

    /** Agachado: la válvula al revés. Chupa los objetos tirados de alrededor y tira un poco de los mobs. */
    public static void aspirar(ServerPlayer p, ItemStack s, int t) {
        ServerLevel sl = p.serverLevel();
        if (Presion.reventada(s, sl) > 0) {
            p.stopUsingItem();
            return;
        }
        RandomSource r = p.getRandom();
        int fuelle = Presion.nivel(s, Presion.Encantamiento.FUELLE);
        double radio = 6 + 2 * fuelle;
        Vec3 centro = p.position().add(0, 0.6, 0), mira = p.getViewVector(1.0F), boca = boca(p);
        for (Entity e : sl.getEntities(p, p.getBoundingBox().inflate(radio), e -> e.isAlive() && !e.isSpectator())) {
            Vec3 d = centro.subtract(e.position());
            double dist = d.length();
            if (dist > radio || dist < 0.3) continue;
            if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
                Vec3 m = e.getDeltaMovement().scale(0.8).add(d.scale(1 / dist).scale(0.16 + 0.05 * fuelle));
                e.setDeltaMovement(m);
            } else if (e instanceof Mob m && !m.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)
                    && d.scale(-1 / dist).dot(mira) > 0.5) {
                double k = 0.035 * (1 + 0.3 * fuelle) * (1 - m.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                m.setDeltaMovement(m.getDeltaMovement().add(d.x / dist * k, 0, d.z / dist * k));
            } else continue;
            e.hasImpulse = true;
            e.hurtMarked = true;
        }
        if (t % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                Vec3 dir = mira.add(r.nextGaussian() * 0.35, r.nextGaussian() * 0.35, r.nextGaussian() * 0.35).normalize();
                Vec3 q = boca.add(dir.scale(radio * (0.4 + 0.5 * r.nextDouble())));
                Vec3 v = boca.subtract(q).normalize();
                sl.sendParticles(ParticleTypes.CLOUD, q.x, q.y, q.z, 0, v.x, v.y, v.z, 0.35);
            }
        }
        if (t % 10 == 0) {
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HORSE_BREATHE, SoundSource.PLAYERS, 0.5F, 0.6F);
            sl.gameEvent(p, GameEvent.FLAP, boca);
        }
    }
}
