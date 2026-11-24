package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Base de las luces de la rave: clic derecho enciende o apaga; agachado + clic cambia el modo (velocidad o patrón); con un
 * tinte, el color (en las que lo tienen). También se encienden con redstone.
 */
public abstract class LuzRaveBlock extends BaseEntityBlock {
    private final BiFunction<BlockPos, BlockState, BlockEntity> fabrica;
    private final RenderShape forma;

    protected LuzRaveBlock(Properties p, BiFunction<BlockPos, BlockState, BlockEntity> fabrica, RenderShape forma) {
        super(p);
        this.fabrica = fabrica;
        this.forma = forma;
    }

    protected abstract VoxelShape caja(BlockState s);

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return caja(s);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return forma;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return fabrica.apply(p, s);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (j.getItemInHand(m).getItem() instanceof DyeItem && s.hasProperty(Muebles.COLOR)) return Muebles.tenir(s, l, p, j, m);
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (j.isShiftKeyDown()) {
            int modo = (s.getValue(LucesRave.MODO) + 1) % 4;
            l.setBlock(p, s.setValue(LucesRave.MODO, modo), 3);
            j.displayClientMessage(Component.translatable("rave.miscelanea.modo." + modo), true);
            l.playSound(null, p, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.4F);
        } else {
            boolean on = !s.getValue(LucesRave.ENCENDIDA);
            l.setBlock(p, s.setValue(LucesRave.ENCENDIDA, on), 3);
            l.playSound(null, p, on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6F, on ? 1.2F : 0.9F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos de, boolean mov) {
        super.neighborChanged(s, l, p, b, de, mov);
        if (l.isClientSide) return;
        boolean señal = l.hasNeighborSignal(p);
        // la redstone solo enciende al subir y apaga al bajar (así se puede seguir usando el clic)
        if (señal && !s.getValue(LucesRave.ENCENDIDA)) l.setBlock(p, s.setValue(LucesRave.ENCENDIDA, true), 3);
    }
}
