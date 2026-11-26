package com.miscelanea.laboratorio;

import com.miscelanea.multibloque.MultiBloque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decoración orientada de dos bloques de alto (la taquilla): como {@link DecoracionOrientadaBlock}, pero su caja puede pasar de
 * 16 de alto y el hueco de encima se rellena con una parte invisible (ver {@link MultiBloque}).
 */
public class DecoracionAltaBlock extends DecoracionOrientadaBlock implements MultiBloque {
    private static final List<BlockPos> ENCIMA = List.of(BlockPos.ZERO.above());
    private final Map<BlockState, VoxelShape> choques = new ConcurrentHashMap<>();

    public DecoracionAltaBlock(Properties p, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(p, x0, y0, z0, x1, y1, z1);
    }

    @Override
    public List<BlockPos> partes(BlockState s) {
        return ENCIMA;
    }

    @Override
    public VoxelShape formaCompleta(BlockState s) {
        return getShape(s, null, BlockPos.ZERO, CollisionContext.empty());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return choques.computeIfAbsent(s, x -> MultiBloque.trozo(formaCompleta(x)));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = super.getStateForPlacement(c);
        return s != null && MultiBloque.cabe(this, s, c) ? s : null;
    }

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState viejo, boolean mov) {
        super.onPlace(s, l, p, viejo, mov);
        if (!viejo.is(this)) MultiBloque.colocar(this, l, p, s);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
        if (!nuevo.is(this)) MultiBloque.quitar(this, l, p, s);
        super.onRemove(s, l, p, nuevo, mov);
    }
}
