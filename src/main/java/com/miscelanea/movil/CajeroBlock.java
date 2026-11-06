package com.miscelanea.movil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Cajero automático: ingresar y retirar dinero del Banco sin límite (con el móvil solo se puede un poco). */
public class CajeroBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape[] FORMA = new VoxelShape[4];

    static {
        // mirando al norte: cuerpo pegado a la pared (sur) y la repisa del teclado que sale hacia delante
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape cuerpo = caja(d, 1, 0, 4, 15, 16, 16), repisa = caja(d, 2, 5, 1, 14, 7, 4), visera = caja(d, 1, 13, 1, 15, 15, 4);
            FORMA[d.get2DDataValue()] = Shapes.or(cuerpo, repisa, visera);
        }
    }

    /** Caja definida mirando al norte, girada a 'd'. */
    private static VoxelShape caja(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public CajeroBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA[s.getValue(FACING).get2DDataValue()];
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        if (!MovilConfig.banco()) return InteractionResult.PASS;
        if (l.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.movil.CajeroScreen.abrir(pos));
        } else if (p instanceof ServerPlayer sp) {
            Banco.abrirCajero(sp, pos);
            l.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
