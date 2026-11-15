package com.miscelanea.armasfuego.ciudad;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pistola de resonancia, el arma de las ciudades antiguas. Dispara una onda sónica que atraviesa los bloques y hiere a todo
 * lo que haya en su línea (la armadura no la frena); a cambio, el estampido se oye (y lo oye el sculk). Funciona con
 * fragmentos de eco: cada uno es una carga, y caben cuatro.
 */
public class ResonanciaItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("resonancia_disparo");

    public ResonanciaItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return 4;
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return 24;
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(Items.ECHO_SHARD);
    }

    @Override
    protected Item municionNormal() {
        return Items.ECHO_SHARD;
    }

    @Override
    protected boolean llenaDeGolpe() {
        return true;
    }

    @Override
    protected int usosPorFase() {
        return 60;
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return 20;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        Vec3 dir = p.getViewVector(1F);
        Vec3 ojo = p.getEyePosition();
        double alcance = EquilibrioArmas.resonanciaAlcance();
        Vec3 fin = ojo.add(dir.scale(alcance));
        float dano = EquilibrioArmas.resonanciaDano();
        // la onda no se para en los bloques: hiere a todo lo que cruza su línea
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(ojo, fin).inflate(2.0),
                x -> x != p && x.isAlive() && !x.isSpectator())) {
            if (e.getBoundingBox().inflate(0.35).clip(ojo, fin).isEmpty()) continue;
            if (Balistica.herir(e, p, dano, 0.85F, DANO, false)) {
                Vec3 emp = dir.scale(0.6);
                e.push(emp.x, 0.25, emp.z);
                e.hurtMarked = true;
            }
        }
        // se ve (y se oye) por toda la línea
        Vec3 boca = boca(p, 0.9, 0.2, 0.2);
        for (double d = 1.5; d < alcance; d += 3.0)
            l.sendParticles(ParticleTypes.SONIC_BOOM, boca.x + dir.x * d, boca.y + dir.y * d, boca.z + dir.z * d, 1, 0, 0, 0, 0);
        for (double d = 0.8; d < alcance; d += 1.1)
            l.sendParticles(ParticleTypes.SCULK_SOUL, boca.x + dir.x * d, boca.y + dir.y * d, boca.z + dir.z * d, 1, 0.05, 0.05, 0.05, 0.01);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.5F);
        l.gameEvent(p, GameEvent.PROJECTILE_SHOOT, boca);
        com.miscelanea.armasfuego.comun.Ruido.disparo(l, p, boca);
    }
}
