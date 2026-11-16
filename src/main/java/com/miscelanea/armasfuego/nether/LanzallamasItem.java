package com.miscelanea.armasfuego.nether;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lanzallamas de cobre, el arma de las fortalezas del Nether. Manteniendo el clic izquierdo suelta un chorro de fuego
 * corto y ancho que quema a todo lo que cae en el cono (y prende lo que pille al final, si el juego deja que haya
 * fuego). Se alimenta de bidones de combustible, que se cambian enteros. Bajo el agua no prende.
 */
public class LanzallamasItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("lanzallamas_fuego");
    /** Cada cuántos ticks sale una pulsación de llama (con el gatillo apretado). */
    public static final int CADENCIA = 2;

    public LanzallamasItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return EquilibrioArmas.lanzallamasCapacidad();
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return 40;
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.BIDON_COMBUSTIBLE.get());
    }

    @Override
    protected Item municionNormal() {
        return ModItems.BIDON_COMBUSTIBLE.get();
    }

    /** Se cambia el bidón entero (lo que quedaba en el viejo se pierde). */
    @Override
    protected int balasPorObjeto(ItemStack s) {
        ponerBalas(s, 0);
        return capacidad(s);
    }

    @Override
    protected boolean cambiaEntera() {
        return true;
    }

    @Override
    protected int usosPorFase() {
        return 400;
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return CADENCIA;
    }

    @Override
    protected float probAtasco(Player p, ItemStack s) {
        return 0.001F;
    }

    @Override
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        empezarRafaga(p);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void rafaga(Level level, Player p, ItemStack s, int t) {
        if (t % CADENCIA != 0) return;
        if (balas(s) <= 0 || atascada(s)) {
            intentar(level, p, s);   // clic
            soltarGatillo(p);
            return;
        }
        intentar(level, p, s);
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        Vec3 dir = p.getViewVector(1F);
        Vec3 boca = boca(p, 0.9, 0.25, 0.25);
        double alcance = EquilibrioArmas.lanzallamasAlcance();
        if (p.isEyeInFluid(FluidTags.WATER)) {
            l.sendParticles(ParticleTypes.BUBBLE, boca.x, boca.y, boca.z, 6, 0.1, 0.1, 0.1, 0.05);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
            return;
        }
        // el chorro: llamas que se abren con la distancia
        for (double d = 0.5; d < alcance; d += 0.6) {
            double sp = 0.04 + d * 0.045;
            l.sendParticles(ParticleTypes.FLAME, boca.x + dir.x * d, boca.y + dir.y * d, boca.z + dir.z * d, 2, sp, sp, sp, 0.012);
            if (l.random.nextFloat() < 0.25F) l.sendParticles(ParticleTypes.SMOKE, boca.x + dir.x * d, boca.y + dir.y * d, boca.z + dir.z * d, 1, sp, sp, sp, 0.01);
        }
        com.miscelanea.armasfuego.comun.Ruido.disparo(l, p, boca);
        if (l.random.nextInt(3) == 0)
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.3F, 0.6F + l.random.nextFloat() * 0.2F);
        // lo que cae en el cono, arde
        Vec3 ojo = p.getEyePosition();
        float dano = EquilibrioArmas.lanzallamasDano();
        AABB caja = new AABB(ojo, ojo.add(dir.scale(alcance))).inflate(1.6);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, caja, x -> x != p && x.isAlive() && !x.isSpectator())) {
            Vec3 a = e.getBoundingBox().getCenter().subtract(ojo);
            double dist = a.length();
            if (dist > alcance + 0.5 || dist < 0.01 || a.normalize().dot(dir) < 0.92 || !p.hasLineOfSight(e)) continue;
            Balistica.herir(e, p, dano, 0F, DANO, true);
            e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 100));
        }
        // y al final, prende lo que haya (si el juego deja que se propague el fuego)
        if (l.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) && l.random.nextFloat() < 0.35F) {
            BlockHitResult bh = l.clip(new ClipContext(ojo, ojo.add(dir.scale(alcance)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = bh.getBlockPos().relative(bh.getDirection());
                if (l.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(l, pos, bh.getDirection()))
                    l.setBlockAndUpdate(pos, BaseFireBlock.getState(l, pos));
            }
        }
    }
}
