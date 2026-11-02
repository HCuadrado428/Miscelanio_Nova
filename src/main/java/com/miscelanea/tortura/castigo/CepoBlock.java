package com.miscelanea.tortura.castigo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cepo / picota de madera vieja: dos postes, una tabla partida con el agujero de la cabeza y los de las manos, y un
 * candado. FACING = hacia donde mira la víctima (hacia quien lo puso); la víctima va detrás. CERRADO: la mitad de
 * arriba de la tabla bajada. Clic derecho: encierra al cautivo que llevas (o a quien esté detrás de la tabla) o lo suelta.
 */
public class CepoBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty CERRADO = BlockStateProperties.LOCKED;
    private static final Map<Direction, VoxelShape> FORMA = new EnumMap<>(Direction.class), CHOQUE = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            FORMA.put(d, Shapes.or(caja(d, 0, 0, 6.0, 16, 28, 9.4)));
            CHOQUE.put(d, caja(d, 0, 0, 6.0, 16, 24, 9.4));
        }
    }

    /** Caja dada para FACING = norte, girada a la dirección d. */
    private static VoxelShape caja(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public CepoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CERRADO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CERRADO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA.get(s.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return CHOQUE.get(s.getValue(FACING));
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        // tirar tomates al de dentro no debe "usar" el cepo
        if (p.getItemInHand(hand).getItem() instanceof PodridoItem) return InteractionResult.PASS;
        if (!l.isClientSide && p instanceof ServerPlayer sp) CepoEntity.usar(sp, pos, s, p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState nuevo, boolean mov) {
        if (!s.is(nuevo.getBlock()) && !l.isClientSide) {
            CepoEntity c = CepoEntity.buscar(l, pos);
            if (c != null) c.liberar(null, true);
        }
        super.onRemove(s, l, pos, nuevo, mov);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable BlockGetter l, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable("block.miscelanea.cepo.ayuda").withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("block.miscelanea.cepo.ayuda2").withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
