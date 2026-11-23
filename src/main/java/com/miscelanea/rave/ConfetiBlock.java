package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cañón de confeti: clic derecho (o una señal de redstone) y dispara una nube de confeti hacia donde apunta. En blanco, de
 * todos los colores; con un tinte, de ese color (con algo de dorado).
 */
public class ConfetiBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty CARGADO = BooleanProperty.create("cargado");
    private static final VoxelShape[] FORMAS = new VoxelShape[6];

    public ConfetiBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(Muebles.COLOR, DyeColor.WHITE).setValue(CARGADO, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, Muebles.COLOR, CARGADO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction d = s.getValue(FACING);
        VoxelShape f = FORMAS[d.get3DDataValue()];
        if (f == null) f = FORMAS[d.get3DDataValue()] = Neon.forma(d, 4, 0, 4, 12, 14, 12);
        return f;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (j.getItemInHand(m).getItem() instanceof DyeItem) return Muebles.tenir(s, l, p, j, m);
        if (!l.isClientSide) disparar(l, p, s);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    /** Dispara (si está cargado: se recarga solo en un segundo). */
    public static void disparar(Level l, BlockPos p, BlockState s) {
        if (!s.getValue(CARGADO)) return;
        l.setBlock(p, s.setValue(CARGADO, false), 3);
        l.scheduleTick(p, s.getBlock(), 20);
        l.playSound(null, p, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.4F, 1.2F);
        l.playSound(null, p, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 0.8F, 1.4F);
        l.blockEvent(p, s.getBlock(), 1, 0);
    }

    @Override
    public void tick(BlockState s, net.minecraft.server.level.ServerLevel l, BlockPos p, net.minecraft.util.RandomSource r) {
        l.setBlock(p, s.setValue(CARGADO, true), 3);
    }

    @Override
    public boolean triggerEvent(BlockState s, Level l, BlockPos p, int id, int param) {
        if (l.isClientSide && id == 1)
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.miscelanea.client.rave.EfectosRave.confeti(l, p, s.getValue(FACING), s.getValue(Muebles.COLOR)));
        return true;
    }

    @Override
    public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos de, boolean mov) {
        super.neighborChanged(s, l, p, b, de, mov);
        if (!l.isClientSide && l.hasNeighborSignal(p)) disparar(l, p, s);
    }
}
