package com.miscelanea.montana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vía de la montaña rusa. Va en la dirección en la que la pones (la de tu mirada) y tiene una forma: recta, curva a la
 * izquierda o a la derecha, subida o bajada; agachado y con la mano vacía se cambia la forma. Una bajada se pone un
 * bloque más abajo que la vía de la que viene (como las rampas de los raíles normales). Tipos: normal, con cadena (sube
 * los vagones despacio), estación (los para, espera a que suba la gente y los lanza) y freno (no deja pasar de una
 * velocidad). La geometría y la física están en {@link Via}.
 */
public class ViaMontanaBlock extends HorizontalDirectionalBlock {
    public enum Forma implements StringRepresentable {
        RECTA, CURVA_IZQ, CURVA_DER, SUBIDA, BAJADA;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Tipo {NORMAL, CADENA, ESTACION, FRENO}

    public static final EnumProperty<Forma> FORMA = EnumProperty.create("forma", Forma.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public final Tipo tipo;

    private static final VoxelShape PLANA = Block.box(0, 0, 0, 16, 4, 16);

    public ViaMontanaBlock(Properties p, Tipo tipo) {
        super(p);
        this.tipo = tipo;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMA, Forma.RECTA).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FORMA, POWERED);
    }

    /** Formas que admite cada tipo (la estación y el freno, solo recta; la cadena, recta, subida y bajada). */
    public boolean admite(Forma f) {
        return switch (tipo) {
            case NORMAL -> true;
            case CADENA -> f == Forma.RECTA || f == Forma.SUBIDA || f == Forma.BAJADA;
            default -> f == Forma.RECTA;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return PLANA;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        // en las rampas se puede andar por encima como por una losa, y los vagones no chocan con nada
        return s.getValue(FORMA) == Forma.SUBIDA || s.getValue(FORMA) == Forma.BAJADA ? Shapes.empty() : PLANA;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
        if (!pl.isShiftKeyDown() || !pl.getItemInHand(h).isEmpty()) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        Forma[] fs = Forma.values();
        Forma f = s.getValue(FORMA);
        do f = fs[(f.ordinal() + 1) % fs.length]; while (!admite(f));
        l.setBlock(p, s.setValue(FORMA, f), Block.UPDATE_ALL);
        l.playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
        pl.displayClientMessage(Component.translatable("montana.miscelanea.forma." + f.getSerializedName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos from, boolean mov) {
        if (tipo != Tipo.ESTACION || l.isClientSide) return;
        boolean on = l.hasNeighborSignal(p);
        if (on != s.getValue(POWERED)) l.setBlock(p, s.setValue(POWERED, on), Block.UPDATE_CLIENTS);
    }
}
