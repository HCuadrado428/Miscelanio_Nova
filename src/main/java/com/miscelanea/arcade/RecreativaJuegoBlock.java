package com.miscelanea.arcade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Recreativa dedicada a un solo juego ("recreativa_&lt;juego&gt;"): arranca ese juego directamente, sin menú. Si el juego es
 * de dos jugadores ({@link ArcadeJuegos#DOS}) el mueble es el doble (dos juegos de mandos, el segundo jugador se une con
 * clic derecho). Los récords se guardan por id de juego, igual que en la recreativa normal.
 */
public class RecreativaJuegoBlock extends RecreativaBlock {
    public final String juego;

    public RecreativaJuegoBlock(Properties p, String juego) {
        super(p, ArcadeJuegos.dosJugadores(juego));
        this.juego = juego;
    }

    @Override
    public String juegoFijo() {
        return juego;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos abajo = s.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(p instanceof ServerPlayer sp)) return InteractionResult.PASS;
        return usar(l, abajo, sp, doble, juego);
    }
}
