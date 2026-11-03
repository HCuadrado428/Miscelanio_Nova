package com.miscelanea.tortura.saw;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Relleno del pozo de agujas: un montón de jeringuillas usadas sobre hormigón (4 modelos al azar, ligeros: capas de
 * textura cutout, la piel de los lados y unas pocas jeringuillas de tarjetas cruzadas; ver gen_pozo_ligero.py). Es un
 * modelo de bloque normal (va en la caché del chunk), sin BlockEntityRenderer. Se anda por dentro (los pies se hunden hasta
 * {@link #HONDO} px) y despacio, como en la arena de almas. Clic derecho: registrar ({@link Pozo}) o, con una llave de
 * la trampa en la mano, esconderla aquí. Romperlo suelta la llave escondida.
 */
public class PozoAgujasBlock extends Block {
    public static final int HONDO = 5;
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 14, 16);
    private static final VoxelShape COLISION = Block.box(0, 0, 0, 16, HONDO, 16);

    public PozoAgujasBlock(Properties p) {
        super(p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return COLISION;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getBlockSupportShape(BlockState s, BlockGetter l, BlockPos p) {
        return COLISION;
    }

    /**
     * Entre dos bloques del pozo no se dibujan las caras que se tocan (la "piel" de los lados, el suelo por debajo y las
     * capas de arriba si hay otro encima: los modelos llevan cullface). Así un pozo grande solo pinta su borde.
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState s, BlockState vecino, net.minecraft.core.Direction lado) {
        return vecino.is(this) || super.skipRendering(s, vecino, lado);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!l.isClientSide && p instanceof ServerPlayer sp) Pozo.usar(sp, pos, p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public void fallOn(Level l, BlockState s, BlockPos p, Entity e, float caida) {
        if (!l.isClientSide && caida > 1.2F && e instanceof LivingEntity le && l.getRandom().nextFloat() < 0.65F) Pozo.pinchazo(le);
        super.fallOn(l, s, p, e, caida);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean movido) {
        if (!s.is(nuevo.getBlock()) && l instanceof ServerLevel sl) {
            ItemStack st = PozoLlaves.de(sl).sacar(p);
            if (st != null && !st.isEmpty()) Block.popResource(sl, p, st);
        }
        super.onRemove(s, l, p, nuevo, movido);
    }

}
