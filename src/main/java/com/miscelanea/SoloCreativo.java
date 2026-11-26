package com.miscelanea;

import com.miscelanea.multibloque.ParteBlock;
import com.miscelanea.multibloque.ParteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
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

    /**
     * El estado que manda: el del propio bloque o, si es el hueco invisible de un bloque de varios huecos (la parte de arriba de
     * una cápsula del laboratorio, del frigorífico...), el del bloque grande. Si no, rompiendo ese hueco se rompía entero.
     */
    public static BlockState real(BlockGetter l, BlockPos p, BlockState s) {
        if (s.getBlock() instanceof ParteBlock && l.getBlockEntity(p) instanceof ParteBlockEntity pb && pb.maestro() != null)
            return l.getBlockState(pb.maestro());
        return s;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void velocidad(PlayerEvent.BreakSpeed e) {
        BlockState s = e.getPosition().map(p -> real(e.getEntity().level(), p, e.getState())).orElse(e.getState());
        if (protegido(s, e.getEntity())) e.setNewSpeed(0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void romper(BlockEvent.BreakEvent e) {
        if (protegido(real(e.getLevel(), e.getPos(), e.getState()), e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(p -> real(e.getLevel(), p, e.getLevel().getBlockState(p)).is(ETIQUETA));
    }
}
