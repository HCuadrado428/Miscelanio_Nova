package com.miscelanea.tortura.saw;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tanque de agua de la caja de agua: depósito verde desconchado con patas, nivel de cristal, manómetro, volante y la
 * palanca de control. Clic derecho (la palanca): empieza el juego de todas las cajas enganchadas a este tanque. La
 * palanca baja y vuelve a subir sola. La manguera de cada caja sale del tubo de arriba ({@link #toma}).
 */
public class TanqueAguaBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVO = BooleanProperty.create("activo");
    private static final VoxelShape FORMA = Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);
    /** Boca del tubo de salida en el modelo (mirando al norte, píxeles): gen_tortura_saw.py T_TOMA. */
    private static final double TOMA_X = 8.0, TOMA_Y = 27.6, TOMA_Z = 11.0;

    public TanqueAguaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ACTIVO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    /** Punto del mundo donde acaba la manguera (boca del tubo de arriba). */
    public static Vec3 toma(BlockState s, BlockPos p) {
        Direction f = s.hasProperty(FACING) ? s.getValue(FACING) : Direction.NORTH;
        double dx = (TOMA_X - 8) / 16, dz = (TOMA_Z - 8) / 16, rx = dx, rz = dz;
        if (f == Direction.EAST) {
            rx = -dz;
            rz = dx;
        } else if (f == Direction.SOUTH) {
            rx = -dx;
            rz = -dz;
        } else if (f == Direction.WEST) {
            rx = dz;
            rz = -dx;
        }
        return new Vec3(p.getX() + 0.5 + rx, p.getY() + TOMA_Y / 16, p.getZ() + 0.5 + rz);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (CajaAgua.tiene(p)) return InteractionResult.FAIL;   // la víctima no llega a la palanca
        if (l instanceof ServerLevel sl) {
            sl.setBlock(pos, s.setValue(ACTIVO, true), 3);
            sl.scheduleTick(pos, this, 40);
            sl.playSound(null, pos, SawRegistro.TANQUE_PALANCA.get(), SoundSource.BLOCKS, 1.2F, 1F);
            int n = CajaAgua.empezar(sl, pos, p);
            p.displayClientMessage((n > 0 ? Component.translatable("msg.miscelanea.saw.tanque.marcha", n)
                    : Component.translatable("msg.miscelanea.saw.tanque.nada")).withStyle(n > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        if (s.getValue(ACTIVO)) {
            l.setBlock(p, s.setValue(ACTIVO, false), 3);
            l.playSound(null, p, SawRegistro.TANQUE_PALANCA.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
        }
    }
}
