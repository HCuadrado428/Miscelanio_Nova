package com.miscelanea.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Golpe en caída del mazo de Wemmbu: daño extra por altura, onda que empuja alrededor y ráfaga que te lanza arriba. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class MazoEventos {
    private MazoEventos() {}

    @SubscribeEvent
    public static void golpe(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof Player p) || e.getSource().getDirectEntity() != p) return;
        if (!(p.getMainHandItem().getItem() instanceof MazoItem) || p.isFallFlying() || p.fallDistance <= 1.5F) return;
        if (!(p.level() instanceof ServerLevel sl)) return;
        float caida = p.fallDistance;
        e.setAmount(e.getAmount() + MazoItem.extraCaida(caida));
        LivingEntity objetivo = e.getEntity();
        Vec3 c = objetivo.position();
        // onda: polvo del suelo, estallido y empujón a los de alrededor
        BlockState suelo = sl.getBlockState(objetivo.blockPosition().below());
        if (!suelo.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, suelo), c.x, c.y + 0.1, c.z, 60, 1.4, 0.1, 1.4, 0.2);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.3, c.z, 25, 1.0, 0.2, 1.0, 0.12);
        sl.playSound(null, c.x, c.y, c.z, caida > 5 ? ModSounds.MAZO_GOLPE_FUERTE.get() : ModSounds.MAZO_GOLPE.get(), SoundSource.PLAYERS, 1.6F, 1F);
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, objetivo.getBoundingBox().inflate(3.5), x -> x != p && x != objetivo && x.isAlive())) {
            Vec3 d = o.position().subtract(c);
            double dist = d.length();
            if (dist > 3.5) continue;
            Vec3 emp = dist < 1e-3 ? new Vec3(0, 0, 0) : d.normalize();
            double f = (3.5 - dist) * 0.25 + 0.3;
            o.setDeltaMovement(o.getDeltaMovement().add(emp.scale(f)).add(0, 0.35 + f * 0.2, 0));
            o.hurtMarked = true;
        }
        // ráfaga de viento: te devuelve al aire para encadenar otro golpe
        p.resetFallDistance();
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x * 0.5, 1.15, v.z * 0.5);
        p.hurtMarked = true;
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VIENTO_RAFAGA.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
