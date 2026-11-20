package com.miscelanea.fiesta;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * El lobby de la fiesta: una plataforma cuadrada de baldosas en medio del vacío, con un borde de bloques de barrera
 * (invisibles) para que nadie se caiga y cuatro farolas de mar en las esquinas. Se construye la primera vez que alguien
 * entra (y se rehace si falta).
 */
public final class Lobby {
    private Lobby() {}

    /** Altura del suelo del lobby (el primer bloque de aire está encima). */
    public static final int SUELO = 64;
    /** Radio de la plataforma (medio lado): va de -RADIO a RADIO. */
    public static final int RADIO = 10;

    /** Dónde aparece la gente (centro, sobre el suelo). */
    public static BlockPos aparicion() {
        return new BlockPos(0, SUELO + 1, 0);
    }

    public static void preparar(ServerLevel l) {
        if (!l.getBlockState(new BlockPos(0, SUELO, 0)).isAir()) return;
        BlockState a = Blocks.WHITE_CONCRETE.defaultBlockState(), b = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
        BlockState borde = Blocks.YELLOW_CONCRETE.defaultBlockState();
        for (int x = -RADIO; x <= RADIO; x++)
            for (int z = -RADIO; z <= RADIO; z++) {
                boolean canto = Math.abs(x) == RADIO || Math.abs(z) == RADIO;
                l.setBlock(new BlockPos(x, SUELO, z), canto ? borde : (((x + z) & 1) == 0 ? a : b), 2);
                if (canto) for (int y = 1; y <= 4; y++) l.setBlock(new BlockPos(x, SUELO + y, z), Blocks.BARRIER.defaultBlockState(), 2);
            }
        for (int sx : new int[]{-RADIO + 1, RADIO - 1})
            for (int sz : new int[]{-RADIO + 1, RADIO - 1}) {
                l.setBlock(new BlockPos(sx, SUELO + 1, sz), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                l.setBlock(new BlockPos(sx, SUELO + 2, sz), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                l.setBlock(new BlockPos(sx, SUELO + 3, sz), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        l.setBlock(new BlockPos(0, SUELO, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        l.setDefaultSpawnPos(aparicion(), 0);
    }
}
