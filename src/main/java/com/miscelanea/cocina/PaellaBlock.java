package com.miscelanea.cocina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Paellera en la mesa: cuatro raciones para compartir (clic derecho para comer una). */
public class PaellaBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty RACIONES = IntegerProperty.create("raciones", 1, 4);
    private static final VoxelShape FORMA = Block.box(1, 0, 1, 15, 3, 15);

    public PaellaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RACIONES, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, RACIONES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (!p.canEat(false)) return InteractionResult.PASS;
        if (!l.isClientSide) {
            p.getFoodData().eat(6, 0.8F);
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            l.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1F, 1F);
            int r = s.getValue(RACIONES);
            if (r <= 1) l.removeBlock(pos, false);
            else l.setBlock(pos, s.setValue(RACIONES, r - 1), 3);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
