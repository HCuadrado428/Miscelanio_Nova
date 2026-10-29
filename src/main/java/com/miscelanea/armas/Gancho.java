package com.miscelanea.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lanzar y soltar el gancho de la superescopeta; y sin daño de caída un rato después de soltarlo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Gancho {
    private static final Map<UUID, Integer> ACTIVOS = new HashMap<>();
    private static final String SIN_CAIDA = "MiscelaneaSinCaida";
    private static final int ESPERA = 30;

    private Gancho() {}

    public static GanchoEntity activo(Player p) {
        Integer id = ACTIVOS.get(p.getUUID());
        if (id == null) return null;
        Entity e = p.level().getEntity(id);
        return e instanceof GanchoEntity g && g.isAlive() ? g : null;
    }

    public static void lanzar(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof EscopetaItem) || s.getOrCreateTag().getInt(EscopetaItem.ESPERA) > 0 || activo(p) != null) return;
        GanchoEntity g = new GanchoEntity(p.level(), p);
        Vec3 mira = p.getViewVector(1.0F);
        g.setPos(p.getX() + mira.x * 0.6, p.getEyeY() - 0.25 + mira.y * 0.6, p.getZ() + mira.z * 0.6);
        g.shoot(mira.x, mira.y, mira.z, 3.2F, 0.0F);
        p.level().addFreshEntity(g);
        ACTIVOS.put(p.getUUID(), g.getId());
        s.getOrCreateTag().putBoolean(EscopetaItem.GANCHO, true);
        s.getTag().putInt(EscopetaItem.LANZADO, 6);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.GANCHO_LANZAR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void soltar(ServerPlayer p) {
        GanchoEntity g = activo(p);
        if (g != null) g.discard();
    }

    /** Lo llama el gancho al desaparecer: espera antes de volver a lanzarlo y sin daño de caída 3 s. */
    static void alQuitar(ServerPlayer p) {
        ACTIVOS.remove(p.getUUID());
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.getItem() instanceof EscopetaItem) {
                s.getOrCreateTag().putBoolean(EscopetaItem.GANCHO, false);
                s.getTag().putInt(EscopetaItem.ESPERA, ESPERA);
            }
        }
        p.getPersistentData().putLong(SIN_CAIDA, p.level().getGameTime() + 60);
        p.resetFallDistance();
    }

    @SubscribeEvent
    public static void caida(LivingFallEvent e) {
        if (e.getEntity() instanceof Player p && p.getPersistentData().getLong(SIN_CAIDA) > p.level().getGameTime()) {
            e.setDamageMultiplier(0.0F);
        }
    }
}
