package com.miscelanea.laboratorio;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mientras un laberinto de la rata está en marcha, su recinto no se puede picar ni rellenar (salvo en creativo): si no, el
 * sujeto cavaba un atajo hasta el queso y cualquiera se llevaba el hormigón, las placas y el panal gratis.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class LaberintoProteccion {
    private LaberintoProteccion() {}

    private static boolean protegido(LevelAccessor l, BlockPos p, Entity quien) {
        if (quien instanceof Player pl && pl.getAbilities().instabuild) return false;
        for (LaberintoBlockEntity be : LaberintoBlockEntity.ACTIVOS)
            if (!be.isRemoved() && be.activo() && be.getLevel() == l && be.enRecinto(p)) return true;
        return false;
    }

    @SubscribeEvent
    public static void romper(BlockEvent.BreakEvent e) {
        if (!LaberintoBlockEntity.ACTIVOS.isEmpty() && protegido(e.getLevel(), e.getPos(), e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void poner(BlockEvent.EntityPlaceEvent e) {
        if (!LaberintoBlockEntity.ACTIVOS.isEmpty() && protegido(e.getLevel(), e.getPos(), e.getEntity())) e.setCanceled(true);
    }
}
