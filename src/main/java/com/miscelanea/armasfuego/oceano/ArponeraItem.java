package com.miscelanea.armasfuego.oceano;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arponera de cobre, el arma de los océanos. Un solo arpón con cuerda: si da en una criatura la hiere fuerte y tira de ella
 * hacia ti; si da en un bloque, te tira a ti hacia él. Bajo el agua tira mucho más fuerte (es para lo que sirve) y el arpón,
 * a veces, se puede recoger donde da.
 */
public class ArponeraItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("arponera_disparo");

    public ArponeraItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return 1;
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return 30;
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.ARPON_COBRE.get());
    }

    @Override
    protected Item municionNormal() {
        return ModItems.ARPON_COBRE.get();
    }

    @Override
    protected boolean llenaDeGolpe() {
        return true;
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return 30;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1F);
        double alcance = EquilibrioArmas.arponeraAlcance();
        boolean agua = p.isInWater();
        // la cuerda atraviesa el agua: solo se para en bloques
        var bh = l.clip(new ClipContext(ojo, ojo.add(dir.scale(alcance)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 fin = bh.getLocation();
        Entity montura = p.getVehicle();
        var eh = ProjectileUtil.getEntityHitResult(l, p, ojo, fin, new AABB(ojo, fin).inflate(1.0),
                e -> e != p && e != montura && !e.isSpectator() && e.isPickable());
        float tiron = EquilibrioArmas.arponeraTiron() * (agua ? 1.6F : 1F);
        Vec3 hasta = eh != null ? eh.getLocation() : fin;
        if (eh != null) {
            Entity e = eh.getEntity();
            if (e instanceof LivingEntity v) Balistica.herir(v, p, EquilibrioArmas.arponeraDano(), 0.3F, DANO, false);
            Vec3 hacia = p.position().subtract(e.position());
            e.setDeltaMovement(hacia.normalize().scale(Math.min(tiron, hacia.length() * 0.25 + 0.4)).add(0, agua ? 0.05 : 0.3, 0));
            e.hurtMarked = true;
            if (l.random.nextFloat() < 0.5F) soltar(l, e.position().add(0, e.getBbHeight() / 2, 0));
        } else if (bh.getType() != HitResult.Type.MISS) {
            Vec3 hacia = fin.subtract(p.position());
            p.setDeltaMovement(hacia.normalize().scale(Math.min(tiron * 1.1, hacia.length() * 0.22 + 0.5)).add(0, agua ? 0.05 : 0.35, 0));
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
            p.fallDistance = 0;
            if (l.random.nextFloat() < 0.6F) soltar(l, bh.getLocation().subtract(dir.scale(0.4)));
        }
        var cuerda = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.LEAD));
        int n = (int) Math.min(18, ojo.distanceTo(hasta) / 1.5);
        for (int k = 1; k <= n; k++) {
            Vec3 q = ojo.lerp(hasta, k / (double) (n + 1));
            l.sendParticles(cuerda, q.x, q.y - 0.2, q.z, 1, 0, 0, 0, 0);
            if (agua) l.sendParticles(ParticleTypes.BUBBLE, q.x, q.y - 0.2, q.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ARPON.get(), SoundSource.PLAYERS, 1.2F, 0.85F);
        com.miscelanea.armasfuego.comun.Ruido.disparo(l, p, ojo);
    }

    /** El arpón se queda donde da y se puede recoger. */
    private static void soltar(ServerLevel l, Vec3 q) {
        l.addFreshEntity(new ItemEntity(l, q.x, q.y, q.z, new ItemStack(ModItems.ARPON_COBRE.get())));
    }
}
