package com.miscelanea.armasfuego.pistola;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.DanoArma;
import com.miscelanea.mixin.LivingEntityGolpeAccessor;
import com.miscelanea.registry.ModEnchantments;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Todo lo que hace el revólver en el servidor: disparar cada munición, cargar, girar el tambor, encerar y rascar. */
public final class Revolver {
    private Revolver() {}

    public static final ResourceKey<DamageType> DISPARO = clave("pistola_disparo"),
            PERFORANTE = clave("pistola_perforante"), RULETA = clave("pistola_ruleta");

    private static ResourceKey<DamageType> clave(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, n));
    }

    /** Parte del daño de la bala que se salta la armadura (equilibrio de armaduras: pistola 15 %). */
    public static final float PERFORACION = 0.15F;
    public static final float DANO_BALA = 7.0F, DANO_IMAN = 3.0F, DANO_DARDO = 1.0F, CRITICO = 1.75F;
    public static final double ALCANCE = 64;
    public static final int ENFRIAMIENTO = 7, VENTANA_DESENFUNDE = 20, USOS_POR_OXIDO = 40;
    /** Dispersión (radianes, gaussiana) y probabilidad de que se encasquille, según el óxido. */
    private static final double[] DISPERSION = {0.006, 0.014, 0.026, 0.045};
    private static final float[] ENCASQUILLA = {0F, 0.01F, 0.03F, 0.07F};

    // ------------------------------------------------------------------ estado en el servidor (no va al NBT)
    /** Cuándo se sacó el revólver (para Desenfunde) y el último tick en que se le vio en la mano. */
    static final Map<UUID, Long> SACADO = new HashMap<>(), VISTO = new HashMap<>();
    /** Disparos que quedan del abanico. */
    static final Map<UUID, Integer> ABANICO = new HashMap<>();

    private record Tiron(Entity e, UUID jugador, int hasta) {}

    private static final List<Tiron> TIRONES = new ArrayList<>();

    // ------------------------------------------------------------------ disparo

    /** Aprieta el gatillo: dispara la recámara que toca y pasa a la siguiente. Devuelve si ha sonado algo (tiro o clic). */
    public static boolean disparar(ServerPlayer p, ItemStack s, boolean abanico) {
        ServerLevel l = p.serverLevel();
        long ahora = l.getGameTime();
        if (ahora - Tambor.giroT(s) < Tambor.GIRO_TICKS) return false;   // el tambor aún gira
        if (Duelo.bloqueado(p)) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_VACIO.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
            p.displayClientMessage(Component.translatable("pistola.miscelanea.duelo.aun_no").withStyle(ChatFormatting.RED), true);
            return false;
        }
        RandomSource r = p.getRandom();
        int pos = Tambor.pos(s);
        Carga c = Tambor.en(s, pos);
        Tambor.avanzar(s);
        s.getOrCreateTag().remove(Tambor.GIRADO);
        boolean sien = !abanico && p.getXRot() >= 80F;   // mirando al suelo del todo: te apuntas a la sien (ruleta rusa)
        Vec3 boca = boca(p);
        if (c == Carga.VACIA) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_VACIO.get(), SoundSource.PLAYERS, 0.8F, 0.9F + r.nextFloat() * 0.2F);
            if (sien) p.displayClientMessage(Component.translatable("pistola.miscelanea.ruleta.vacia").withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        int ox = Tambor.oxido(s);
        if (r.nextFloat() < ENCASQUILLA[ox]) {
            // encasquillado: la bala se queda en su recámara
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_VACIO.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
            l.sendParticles(ParticleTypes.SMOKE, boca.x, boca.y, boca.z, 3, 0.03, 0.03, 0.03, 0.01);
            p.displayClientMessage(Component.translatable("pistola.miscelanea.encasquillado").withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        Tambor.poner(s, pos, Carga.VACIA);
        oxidar(p, s);

        boolean critico = false;
        if (!abanico) {
            Long sacado = SACADO.remove(p.getUUID());
            critico = sacado != null && ahora - sacado <= VENTANA_DESENFUNDE
                    && EncantamientoRevolver.nivel(ModEnchantments.REVOLVER_DESENFUNDE, s) > 0;
        }
        double disp = critico ? 0 : DISPERSION[ox] + (abanico ? 0.10 : 0);
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 dir = mira.add(r.nextGaussian() * disp, r.nextGaussian() * disp, r.nextGaussian() * disp).normalize();

        // fogonazo, humo y ruido (los sensores de sculk y el warden lo oyen)
        fogonazo(l, boca, c == Carga.FOGUEO);
        if (c == Carga.FOGUEO) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_FOGUEO.get(), SoundSource.PLAYERS, 6.0F, 0.95F + r.nextFloat() * 0.1F);
            l.gameEvent(p, GameEvent.EXPLODE, boca);
        } else {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_DISPARO.get(), SoundSource.PLAYERS, 4.0F,
                    (c == Carga.DARDO ? 1.25F : c == Carga.BENGALA ? 0.8F : 1.0F) + r.nextFloat() * 0.08F);
            l.gameEvent(p, GameEvent.PROJECTILE_SHOOT, boca);
        }
        if (critico) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
            p.displayClientMessage(Component.translatable("pistola.miscelanea.desenfunde").withStyle(ChatFormatting.GOLD), true);
        }

        if (sien) {
            aLaSien(p, c);
            return true;
        }
        switch (c) {
            case BALA, DARDO, IMAN -> {
                int rebotes = EncantamientoRevolver.nivel(ModEnchantments.REVOLVER_CARAMBOLA, s);
                Impacto im = trazar(l, p, p.getEyePosition(), dir, ALCANCE, rebotes, boca);
                impactar(l, p, c, im, critico);
            }
            case BENGALA -> {
                BengalaEntity b = new BengalaEntity(ModEntities.REVOLVER_BENGALA.get(), l);
                b.setOwner(p);
                b.setPos(boca.x, boca.y, boca.z);
                b.setDeltaMovement(dir.scale(1.5));
                b.preparar(EncantamientoRevolver.nivel(ModEnchantments.REVOLVER_SENALERO, s) > 0);
                l.addFreshEntity(b);
            }
            case FOGUEO -> asustar(l, p);
            default -> {}
        }
        return true;
    }

    /** Ruleta rusa: el tiro va para ti. */
    private static void aLaSien(ServerPlayer p, Carga c) {
        ServerLevel l = p.serverLevel();
        switch (c) {
            case BALA -> p.hurt(new DamageSource(l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RULETA), p), 30F);
            case DARDO -> sedar(p);
            case BENGALA -> {
                p.setSecondsOnFire(4);
                p.hurt(l.damageSources().onFire(), 2F);
            }
            case IMAN -> atraer(l, p, p.position());
            case FOGUEO -> {
                p.displayClientMessage(Component.translatable("pistola.miscelanea.ruleta.fogueo").withStyle(ChatFormatting.YELLOW), true);
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false));
            }
            default -> {}
        }
    }

    public record Impacto(Entity entidad, Vec3 pos, BlockHitResult bloque, int rebotes) {}

    /**
     * Trayectoria instantánea de la bala. Con Carambola rebota en los bloques (tantas veces como el nivel); en los
     * bloques de cobre rebota siempre (sin gastar rebote), hasta 8 rebotes en total.
     */
    public static Impacto trazar(ServerLevel l, Player p, Vec3 desde, Vec3 dir, double alcance, int rebotes, Vec3 boca) {
        Vec3 a = desde, traza = boca;
        double resto = alcance;
        int gastados = 0, total = 0;
        while (true) {
            Vec3 b = a.add(dir.scale(resto));
            BlockHitResult bh = l.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 fin = bh.getType() == HitResult.Type.MISS ? b : bh.getLocation();
            final int tt = total;
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(l, p, a, fin, new AABB(a, fin).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && (e != p || tt > 0));
            Vec3 hasta = eh != null ? eh.getLocation() : fin;
            estela(l, traza, hasta);
            if (eh != null) return new Impacto(eh.getEntity(), eh.getLocation(), null, total);
            if (bh.getType() == HitResult.Type.MISS) return new Impacto(null, fin, null, total);
            BlockState st = l.getBlockState(bh.getBlockPos());
            boolean cobre = esCobre(st);
            boolean rebota = rebotes > 0 && total < 8 && (cobre || gastados < rebotes);
            resto -= a.distanceTo(fin);
            if (!rebota || resto < 1) return new Impacto(null, fin, bh, total);
            if (!cobre) gastados++;
            total++;
            Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            dir = dir.subtract(n.scale(2 * dir.dot(n))).normalize();
            a = fin.add(n.scale(0.02));
            traza = a;
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, fin.x, fin.y, fin.z, 6, 0.05, 0.05, 0.05, 0.15);
            if (!st.isAir()) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), fin.x, fin.y, fin.z, 4, 0.05, 0.05, 0.05, 0.1);
            l.playSound(null, fin.x, fin.y, fin.z, ModSounds.REVOLVER_REBOTE.get(), SoundSource.PLAYERS, 1.2F, (cobre ? 1.3F : 1.0F) + l.random.nextFloat() * 0.15F);
            if (st.getBlock() instanceof BellBlock bell) bell.attemptToRing(p, l, bh.getBlockPos(), bh.getDirection());
        }
    }

    public static boolean esCobre(BlockState st) {
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().contains("copper");
    }

    private static void impactar(ServerLevel l, ServerPlayer p, Carga c, Impacto im, boolean critico) {
        Vec3 q = im.pos();
        if (im.entidad() instanceof LivingEntity v) {
            if (v instanceof ServerPlayer otro) Duelo.impacto(p, otro);
            switch (c) {
                case BALA -> {
                    float d = DANO_BALA * (critico ? CRITICO : 1F);
                    if (herir(v, p, d)) {
                        Vec3 emp = v.position().subtract(p.position()).normalize();
                        v.knockback(0.25, -emp.x, -emp.z);
                    }
                    l.sendParticles(critico ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, q.x, q.y, q.z, critico ? 12 : 5, 0.1, 0.1, 0.1, 0.25);
                }
                case DARDO -> {
                    herir(v, p, DANO_DARDO * (critico ? CRITICO : 1F));
                    sedar(v);
                }
                case IMAN -> {
                    herir(v, p, DANO_IMAN * (critico ? CRITICO : 1F));
                    atraer(l, p, q);
                }
                default -> {}
            }
            return;
        }
        if (im.bloque() != null) {
            BlockState st = l.getBlockState(im.bloque().getBlockPos());
            if (!st.isAir())
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), q.x, q.y, q.z, 6, 0.05, 0.05, 0.05, 0.1);
            l.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 2, 0.02, 0.02, 0.02, 0.01);
            if (st.getBlock() instanceof BellBlock bell) bell.attemptToRing(p, l, im.bloque().getBlockPos(), im.bloque().getDirection());
        }
        if (c == Carga.IMAN) atraer(l, p, q);
    }

    /**
     * El daño de la bala: el 85 % normal (con su armadura) y el 15 % que se la salta (pistola_perforante, en la
     * etiqueta {@code minecraft:bypasses_armor}), en el mismo golpe y sin romper la ventana de invulnerabilidad.
     */
    public static boolean herir(LivingEntity v, Entity tirador, float total) {
        float perf = total * PERFORACION;
        if (!v.hurt(fuente(v, DISPARO, tirador), total - perf)) return false;
        if (perf > 0.001F && v.isAlive()) {
            LivingEntityGolpeAccessor acc = (LivingEntityGolpeAccessor) v;
            int inv = v.invulnerableTime;
            float ultimo = acc.miscelanea$ultimoDano();
            Vec3 mov = v.getDeltaMovement();
            boolean marcado = v.hurtMarked;
            v.invulnerableTime = 0;
            DanoArma.extra = true;
            try {
                v.hurt(fuente(v, PERFORANTE, tirador), perf);
            } finally {
                DanoArma.extra = false;
            }
            v.invulnerableTime = inv;
            acc.miscelanea$ponerUltimoDano(ultimo + perf);
            v.setDeltaMovement(mov);
            v.hurtMarked = marcado;
        }
        return true;
    }

    private static DamageSource fuente(LivingEntity v, ResourceKey<DamageType> tipo, Entity tirador) {
        return new DamageSource(v.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(tipo), tirador);
    }

    /** Dardo de sedante (en esta rama no hay sedante propio): lentitud y ceguera cortas. */
    public static void sedar(LivingEntity v) {
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));
        v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
        if (!(v instanceof Player)) v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
        if (v.level() instanceof ServerLevel l)
            l.sendParticles(ParticleTypes.EFFECT, v.getX(), v.getY() + v.getBbHeight() * 0.8, v.getZ(), 8, 0.25, 0.25, 0.25, 0.02);
    }

    /** Bala de fogueo: los animales de alrededor salen corriendo (como cuando les pegas, pero sin pegarles). */
    private static void asustar(ServerLevel l, ServerPlayer p) {
        for (PathfinderMob m : l.getEntitiesOfClass(PathfinderMob.class, p.getBoundingBox().inflate(20), e -> e instanceof Animal && e.isAlive())) {
            Vec3 lejos = DefaultRandomPos.getPosAway(m, 16, 7, p.position());
            if (lejos != null) m.getNavigation().moveTo(lejos.x, lejos.y, lejos.z, 2.0);
            if (m instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h) h.makeMad();
        }
    }

    /** Bala imán: lo tirado alrededor del impacto va volando hacia el que disparó. */
    private static void atraer(ServerLevel l, ServerPlayer p, Vec3 donde) {
        AABB caja = new AABB(donde, donde).inflate(6);
        int hasta = p.server.getTickCount() + 80;
        int n = 0;
        for (Entity e : l.getEntities((Entity) null, caja, e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.isAlive())) {
            TIRONES.removeIf(t -> t.e == e);
            TIRONES.add(new Tiron(e, p.getUUID(), hasta));
            n++;
        }
        DustParticleOptions azul = new DustParticleOptions(new org.joml.Vector3f(0.3F, 0.5F, 1.0F), 1.0F);
        l.sendParticles(azul, donde.x, donde.y + 0.2, donde.z, 16, 1.5, 0.4, 1.5, 0);
        l.playSound(null, donde.x, donde.y, donde.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
        if (n > 0) l.playSound(null, donde.x, donde.y, donde.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    static void tickTirones(net.minecraft.server.MinecraftServer srv) {
        if (TIRONES.isEmpty()) return;
        int ahora = srv.getTickCount();
        for (Iterator<Tiron> it = TIRONES.iterator(); it.hasNext(); ) {
            Tiron t = it.next();
            ServerPlayer p = srv.getPlayerList().getPlayer(t.jugador);
            if (ahora > t.hasta || !t.e.isAlive() || p == null || p.level() != t.e.level() || p.distanceToSqr(t.e) > 64 * 64) {
                it.remove();
                continue;
            }
            Vec3 d = p.position().add(0, 0.6, 0).subtract(t.e.position());
            if (d.lengthSqr() < 1.0) {
                it.remove();
                continue;
            }
            t.e.setDeltaMovement(d.normalize().scale(Math.min(0.6, 0.15 + d.length() * 0.08)));
            t.e.hurtMarked = true;
            if (t.e instanceof ItemEntity ie && ahora % 4 == 0)
                ((ServerLevel) ie.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, ie.getX(), ie.getY() + 0.2, ie.getZ(), 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ efectos

    /** Dónde sale el tiro (un poco a la derecha y abajo de los ojos, en la mano del arma). */
    public static Vec3 boca(Player p) {
        Vec3 ojo = p.getEyePosition();
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        if (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) der = der.scale(-1);
        return ojo.add(mira.scale(0.8)).add(der.scale(0.3)).add(0, -0.22, 0);
    }

    private static void fogonazo(ServerLevel l, Vec3 b, boolean grande) {
        l.sendParticles(ParticleTypes.FLASH, b.x, b.y, b.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.SMOKE, b.x, b.y, b.z, grande ? 10 : 4, 0.06, 0.06, 0.06, 0.02);
        l.sendParticles(ParticleTypes.FLAME, b.x, b.y, b.z, grande ? 6 : 2, 0.03, 0.03, 0.03, 0.04);
        if (grande) l.sendParticles(ParticleTypes.LARGE_SMOKE, b.x, b.y, b.z, 6, 0.15, 0.15, 0.15, 0.03);
    }

    private static void estela(ServerLevel l, Vec3 a, Vec3 b) {
        double d = a.distanceTo(b);
        int n = (int) Math.min(12, d / 1.5);
        for (int k = 1; k <= n; k++) {
            Vec3 q = a.lerp(b, k / (double) (n + 1));
            l.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ cobre: óxido, cera y rascado

    private static void oxidar(ServerPlayer p, ItemStack s) {
        if (Tambor.encerado(s) || Tambor.oxido(s) >= 3) return;
        var t = s.getOrCreateTag();
        int u = t.getInt(Tambor.USOS) + 1;
        if (u >= USOS_POR_OXIDO) {
            t.putInt(Tambor.OXIDO, Tambor.oxido(s) + 1);
            t.putInt(Tambor.USOS, 0);
            p.displayClientMessage(Component.translatable("pistola.miscelanea.oxido." + Tambor.oxido(s)).withStyle(ChatFormatting.DARK_AQUA), true);
        } else {
            t.putInt(Tambor.USOS, u);
        }
    }

    /** Panal: encera (ya no se oxida). */
    public static boolean encerar(ServerPlayer p, ItemStack s, ItemStack panal) {
        if (Tambor.encerado(s)) return false;
        s.getOrCreateTag().putBoolean(Tambor.ENCERADO, true);
        if (!p.getAbilities().instabuild) panal.shrink(1);
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 1.0F, 1.0F);
        Vec3 b = boca(p);
        l.sendParticles(ParticleTypes.WAX_ON, b.x, b.y, b.z, 8, 0.2, 0.2, 0.2, 0.5);
        return true;
    }

    /** Hacha: quita la cera o, si no tiene, un paso de óxido (como los bloques de cobre). */
    public static boolean rascar(ServerPlayer p, ItemStack s, ItemStack hacha, net.minecraft.world.InteractionHand manoHacha) {
        ServerLevel l = p.serverLevel();
        Vec3 b = boca(p);
        if (Tambor.encerado(s)) {
            s.getOrCreateTag().remove(Tambor.ENCERADO);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AXE_WAX_OFF, SoundSource.PLAYERS, 1.0F, 1.0F);
            l.sendParticles(ParticleTypes.WAX_OFF, b.x, b.y, b.z, 8, 0.2, 0.2, 0.2, 0.5);
        } else if (Tambor.oxido(s) > 0) {
            s.getOrCreateTag().putInt(Tambor.OXIDO, Tambor.oxido(s) - 1);
            s.getOrCreateTag().putInt(Tambor.USOS, 0);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AXE_SCRAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
            l.sendParticles(ParticleTypes.SCRAPE, b.x, b.y, b.z, 8, 0.2, 0.2, 0.2, 0.5);
        } else {
            return false;
        }
        if (manoHacha != null) hacha.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(manoHacha));
        else if (hacha.isDamageableItem() && !p.getAbilities().instabuild && hacha.hurt(1, p.getRandom(), p)) hacha.shrink(1);
        return true;
    }

    public static boolean esHacha(ItemStack s) {
        return s.getItem() instanceof AxeItem;
    }

    public static boolean esPanal(ItemStack s) {
        return s.is(Items.HONEYCOMB);
    }

    // ------------------------------------------------------------------ tambor

    /** Mete una bala de {@code municion} en la siguiente recámara vacía. */
    public static boolean cargar(ServerPlayer p, ItemStack s, ItemStack municion) {
        Carga c = Carga.de(municion);
        if (c == null) return false;
        int hueco = Tambor.primeraVacia(s);
        ServerLevel l = p.serverLevel();
        if (hueco < 0) {
            p.displayClientMessage(Component.translatable("pistola.miscelanea.lleno").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Tambor.poner(s, hueco, c);
        if (!p.getAbilities().instabuild) municion.shrink(1);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_CARGAR.get(), SoundSource.PLAYERS, 0.7F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        return true;
    }

    /** Gira el tambor al azar: suena la ruleta y no se sabe qué sale. */
    public static void girar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        long ahora = l.getGameTime();
        if (ahora - Tambor.giroT(s) < Tambor.GIRO_TICKS + 4) return;
        ABANICO.remove(p.getUUID());
        Tambor.ponerPos(s, p.getRandom().nextInt(Tambor.N));
        var t = s.getOrCreateTag();
        t.putBoolean(Tambor.GIRADO, true);
        t.putLong(Tambor.GIRO_T, ahora);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_RULETA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Abanico: vacía el tambor muy deprisa (un tiro cada 2 ticks) con mucha dispersión. */
    public static void empezarAbanico(ServerPlayer p, ItemStack s) {
        if (Tambor.cargadas(s) == 0 || ABANICO.containsKey(p.getUUID())) return;
        ABANICO.put(p.getUUID(), Tambor.N);
    }

    /** Cada tick con el revólver en la mano (servidor): Desenfunde y el abanico. */
    static void enMano(ServerPlayer p, ItemStack s) {
        UUID id = p.getUUID();
        long ahora = p.serverLevel().getGameTime();
        Long visto = VISTO.put(id, ahora);
        if (visto == null || visto < ahora - 1) {
            // recién sacado
            SACADO.put(id, ahora);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_AMARTILLAR.get(), SoundSource.PLAYERS, 0.5F, 1.1F);
            ABANICO.remove(id);
        }
        Integer quedan = ABANICO.get(id);
        if (quedan != null && ahora % 2 == 0) {
            if (quedan <= 0 || Tambor.cargadas(s) == 0) ABANICO.remove(id);
            else {
                ABANICO.put(id, quedan - 1);
                disparar(p, s, true);
            }
        }
    }

    static void limpiar() {
        SACADO.clear();
        VISTO.clear();
        ABANICO.clear();
        TIRONES.clear();
    }

    static void olvidar(UUID id) {
        SACADO.remove(id);
        VISTO.remove(id);
        ABANICO.remove(id);
        TIRONES.removeIf(t -> t.jugador.equals(id));
    }
}
