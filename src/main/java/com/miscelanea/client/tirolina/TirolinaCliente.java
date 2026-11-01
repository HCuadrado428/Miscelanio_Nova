package com.miscelanea.client.tirolina;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import com.miscelanea.tirolina.Polea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Zumbido de la polea por el cable (más fuerte y agudo cuanto más rápido) para cada tirolina en marcha cercana. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TirolinaCliente {
    private static final Map<Integer, Zumbido> SONIDOS = new HashMap<>();

    private TirolinaCliente() {}

    static final class Zumbido extends AbstractTickableSoundInstance {
        final Polea p;

        Zumbido(Polea p) {
            super(ModSounds.TIROLINA_ZUMBIDO.get(), SoundSource.PLAYERS, RandomSource.create());
            this.p = p;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
        }

        @Override
        public void tick() {
            if (p.isRemoved()) {
                stop();
                return;
            }
            float v = Math.abs(p.velocidad());
            x = p.getX();
            y = p.getY() + 0.75;
            z = p.getZ();
            volume = Math.min(1F, 0.1F + v * 0.9F);
            pitch = 0.6F + v * 0.9F;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.player == null) return;
        SONIDOS.values().removeIf(z -> z.p.isRemoved() || z.isStopped());
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof Polea p && !SONIDOS.containsKey(p.getId()) && p.distanceToSqr(mc.player) < 48 * 48) {
                Zumbido z = new Zumbido(p);
                SONIDOS.put(p.getId(), z);
                mc.getSoundManager().play(z);
            }
        }
    }
}
