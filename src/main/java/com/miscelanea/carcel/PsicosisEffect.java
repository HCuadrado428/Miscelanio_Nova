package com.miscelanea.carcel;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Psicosis por el gas: la pantalla se tiñe y ondula (cliente), oyes pasos y monstruos que no están
 * y, al nivel máximo, vas perdiendo vida poco a poco. Se cura con el Antídoto.
 */
public class PsicosisEffect extends MobEffect {
    private static final SoundEvent[] FALSOS = {
            SoundEvents.ZOMBIE_AMBIENT, SoundEvents.ZOMBIE_STEP, SoundEvents.SKELETON_STEP,
            SoundEvents.IRON_DOOR_OPEN, SoundEvents.CHAIN_STEP, SoundEvents.WARDEN_HEARTBEAT, SoundEvents.ENDERMAN_STARE
    };

    public PsicosisEffect() {
        super(MobEffectCategory.HARMFUL, 0x7FBF3A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int nivel) {
        if (e.level().isClientSide || !(e instanceof Player p)) return;
        var r = p.getRandom();
        // Sonidos falsos solo para él (playNotifySound los oye nada más el jugador)
        if (r.nextInt(Math.max(20, 120 - nivel * 40)) == 0 && p instanceof net.minecraft.server.level.ServerPlayer sp) {
            SoundEvent s = FALSOS[r.nextInt(FALSOS.length)];
            double ang = r.nextDouble() * Math.PI * 2;
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(s), SoundSource.HOSTILE,
                    p.getX() + Math.cos(ang) * 4, p.getY(), p.getZ() + Math.sin(ang) * 4, 0.8F, 0.8F + r.nextFloat() * 0.3F, r.nextLong()));
        }
        if (nivel >= 1 && p.tickCount % 40 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        }
        if (nivel >= 2 && p.tickCount % 60 == 0) {
            p.hurt(p.damageSources().magic(), 1.0F);
        }
    }
}
