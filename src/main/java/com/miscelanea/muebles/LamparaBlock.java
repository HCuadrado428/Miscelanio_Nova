package com.miscelanea.muebles;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lámpara de pie: clic derecho la enciende/apaga; con un tinte cambias el color de la pantalla. */
public class LamparaBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape FORMA = Shapes.or(Block.box(5, 0, 5, 11, 1.6, 11), Block.box(7, 1.6, 7, 9, 16, 9));

    public LamparaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(Muebles.COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT, Muebles.COLOR);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = Muebles.tenir(s, level, pos, p, hand);
        if (r != InteractionResult.PASS) return r;
        if (!level.isClientSide) {
            boolean on = !s.getValue(LIT);
            level.setBlock(pos, s.setValue(LIT, on), 3);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, on ? 1.4F : 1.1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
