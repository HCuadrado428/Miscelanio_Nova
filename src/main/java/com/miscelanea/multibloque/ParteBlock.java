package com.miscelanea.multibloque;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relleno invisible de un bloque de varios huecos (ver {@link MultiBloque}): tiene el trozo de forma del maestro que cae en su
 * hueco, le pasa los clics, y si lo rompen se rompe el maestro entero. Si se queda sin maestro, desaparece.
 */
public class ParteBlock extends BaseEntityBlock {
    private static final VoxelShape SUELTA = Shapes.box(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

    private record Trozo(BlockState maestro, int dx, int dy, int dz) {}

    private static final Map<Trozo, VoxelShape> TROZOS = new ConcurrentHashMap<>();

    public ParteBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new ParteBlockEntity(p, s);
    }

    @Nullable
    private static BlockPos maestro(BlockGetter l, BlockPos p) {
        if (!(l.getBlockEntity(p) instanceof ParteBlockEntity be) || be.maestro() == null) return null;
        return l.getBlockState(be.maestro()).getBlock() instanceof MultiBloque ? be.maestro() : null;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        BlockPos m = maestro(l, p);
        if (m == null) return SUELTA;
        BlockState ms = l.getBlockState(m);
        return ((MultiBloque) ms.getBlock()).formaCompleta(ms).move(m.getX() - p.getX(), m.getY() - p.getY(), m.getZ() - p.getZ());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        BlockPos m = maestro(l, p);
        if (m == null) return Shapes.empty();
        BlockState ms = l.getBlockState(m);
        MultiBloque mb = (MultiBloque) ms.getBlock();
        if (!mb.solido()) return Shapes.empty();
        return TROZOS.computeIfAbsent(new Trozo(ms, m.getX() - p.getX(), m.getY() - p.getY(), m.getZ() - p.getZ()),
                t -> MultiBloque.trozo(mb.formaCompleta(ms).move(t.dx(), t.dy(), t.dz())));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0F;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand mano, BlockHitResult h) {
        BlockPos m = maestro(l, p);
        if (m == null) return InteractionResult.PASS;
        return l.getBlockState(m).use(l, j, mano, new BlockHitResult(h.getLocation(), h.getDirection(), m, h.isInside()));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState s, HitResult h, BlockGetter l, BlockPos p, Player j) {
        BlockPos m = maestro(l, p);
        return m == null ? ItemStack.EMPTY : l.getBlockState(m).getCloneItemStack(h, l, m, j);
    }

    @Override
    public void playerWillDestroy(Level l, BlockPos p, BlockState s, Player j) {
        BlockPos m = maestro(l, p);
        if (m != null && !l.isClientSide) l.destroyBlock(m, !j.isCreative(), j);
        super.playerWillDestroy(l, p, s, j);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
        // roto por otra cosa (una explosión, un pistón...): el maestro se rompe y suelta su objeto
        if (!s.is(nuevo.getBlock()) && !l.isClientSide) {
            BlockPos m = maestro(l, p);
            if (m != null) l.destroyBlock(m, true);
        }
        super.onRemove(s, l, p, nuevo, mov);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState vecino, LevelAccessor l, BlockPos p, BlockPos pv) {
        // sin maestro (lo han quitado sin avisar): fuera. Si aún no ha llegado el dato de dónde está, se espera.
        if (l.getBlockEntity(p) instanceof ParteBlockEntity be && be.maestro() != null && !(l.getBlockState(be.maestro()).getBlock() instanceof MultiBloque))
            return Blocks.AIR.defaultBlockState();
        return s;
    }
}
