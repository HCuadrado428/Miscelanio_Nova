package com.miscelanea.trampolin;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * Trampolín (varios juntos forman uno grande). Rebota más si pulsas saltar justo antes de caer; agachado no rebota.
 * La lona visible está más alta que la colisión: así se hunde bajo los pies (la dibuja el renderizador).
 */
public class TrampolinBlock extends BaseEntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, SOUTH = BlockStateProperties.SOUTH,
            EAST = BlockStateProperties.EAST, WEST = BlockStateProperties.WEST;
    public static final double LONA = 11.7 / 16, SUELO = 8.0 / 16;
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 8, 16);

    public TrampolinBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    private BlockState unir(BlockState s, BlockGetter l, BlockPos p) {
        return s.setValue(NORTH, l.getBlockState(p.north()).is(this)).setValue(SOUTH, l.getBlockState(p.south()).is(this))
                .setValue(EAST, l.getBlockState(p.east()).is(this)).setValue(WEST, l.getBlockState(p.west()).is(this));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return unir(defaultBlockState(), c.getLevel(), c.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState o, LevelAccessor l, BlockPos p, BlockPos po) {
        return d.getAxis().isHorizontal() ? unir(s, l, p) : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new TrampolinBlockEntity(p, s);
    }

    @Override
    public void fallOn(Level l, BlockState s, BlockPos p, Entity e, float dist) {
        if (e.isSuppressingBounce()) super.fallOn(l, s, p, e, dist * 0.3F);
        else e.resetFallDistance();
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter g, Entity e) {
        if (e.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(g, e);
            return;
        }
        Vec3 d = e.getDeltaMovement();
        if (d.y >= 0) return;
        double vin = -d.y;
        boolean local = e instanceof Player pl && pl.level().isClientSide && TrampolinFisica.esLocal(pl);
        boolean perfecto = local && TrampolinFisica.saltoATiempo();
        double vout = perfecto ? Math.min(1.55, Math.max(0.62, vin * 1.12 + 0.1)) : (vin < 0.2 ? 0 : Math.max(0.42, vin * 0.86));
        e.setDeltaMovement(d.x, vout, d.z);
        if (vout <= 0) return;
        if (local) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.trampolin.TrampolinCliente.rebote(perfecto, vout));
        } else if (!e.level().isClientSide) {
            e.level().playSound(e instanceof Player pl ? pl : null, e.getX(), e.getY(), e.getZ(), ModSounds.TRAMPOLIN_BOING.get(), SoundSource.BLOCKS,
                    0.6F, 0.8F + (float) vout * 0.5F);
        }
    }
}
