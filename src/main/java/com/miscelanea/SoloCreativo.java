package com.miscelanea;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bloques que solo se consiguen en creativo (los de la etiqueta {@code miscelanea:solo_creativo}: máquinas, atracciones,
 * aparatos...): en supervivencia y aventura son irrompibles (no se pueden picar, ni romper, ni los vuelan las
 * explosiones). En creativo se quitan como siempre. Para cambiar cuáles, basta con tocar la etiqueta (un datapack).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class SoloCreativo {
    private SoloCreativo() {}

    public static final TagKey<Block> ETIQUETA = TagKey.create(Registries.BLOCK, new ResourceLocation(Miscelanea.MOD_ID, "solo_creativo"));

    public static boolean protegido(BlockState s, Player p) {
        return s.is(ETIQUETA) && (p == null || !p.getAbilities().instabuild);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void velocidad(PlayerEvent.BreakSpeed e) {
        if (protegido(e.getState(), e.getEntity())) e.setNewSpeed(0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void romper(BlockEvent.BreakEvent e) {
        if (protegido(e.getState(), e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(p -> e.getLevel().getBlockState(p).is(ETIQUETA));
    }
}
