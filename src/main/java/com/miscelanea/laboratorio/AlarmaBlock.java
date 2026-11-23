package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * La alarma de laboratorio: un detector de presencia con sirena. Clic derecho = armarla o desarmarla. Armada, si alguien
 * entra a menos de 6 bloques (sin agacharse y sin llevar bata de laboratorio) suena 10 segundos y da señal de redstone.
 */
public class AlarmaBlock extends BaseEntityBlock {
    public static final BooleanProperty ARMADA = BooleanProperty.create("armada");
    public static final BooleanProperty SONANDO = BooleanProperty.create("sonando");
    private static final VoxelShape FORMA = Block.box(3, 0, 3, 13, 12, 13);

    public AlarmaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ARMADA, false).setValue(SONANDO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ARMADA, SONANDO);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new AlarmaBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, LabRegistro.ALARMA_BE.get(), AlarmaBlockEntity::tick);
    }

    @Override
    public boolean isSignalSource(BlockState s) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState s, BlockGetter l, BlockPos p, Direction d) {
        return s.getValue(SONANDO) ? 15 : 0;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult r) {
        if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        boolean armar = !s.getValue(ARMADA);
        l.setBlock(p, s.setValue(ARMADA, armar).setValue(SONANDO, false), 3);
        pl.displayClientMessage(Component.translatable(armar ? "laboratorio.miscelanea.alarma.armada" : "laboratorio.miscelanea.alarma.desarmada")
                .withStyle(armar ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }
}
