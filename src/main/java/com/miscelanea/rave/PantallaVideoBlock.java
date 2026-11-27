package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * Pantalla LED de vídeo para el escenario: paneles de un bloque que, juntos en rectángulo (hasta 12x8), forman una sola
 * pantalla. Enseña en bucle un vídeo corto de la galería del móvil de quien lo pone (clic derecho: elegir el vídeo).
 */
public class PantallaVideoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int MAX_ANCHO = 12, MAX_ALTO = 8;
    private final VoxelShape[] formas = new VoxelShape[6];

    public PantallaVideoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction d = s.getValue(FACING);
        VoxelShape f = formas[d.get3DDataValue()];
        if (f == null) f = formas[d.get3DDataValue()] = com.miscelanea.camaras.Montaje.forma(d, 0, 0, 13, 16, 16, 16);
        return f;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction clic = c.getClickedFace();
        return defaultBlockState().setValue(FACING, clic.getAxis().isHorizontal() ? clic : c.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new PantallaVideoBlockEntity(p, s);
    }

    private static boolean igual(BlockGetter l, BlockPos q, Direction d) {
        BlockState s = l.getBlockState(q);
        return s.getBlock() instanceof PantallaVideoBlock && s.getValue(FACING) == d;
    }

    /** {izquierda, derecha, abajo, arriba}: cuántas pantallas iguales hay seguidas desde esta. */
    public static int[] grupo(BlockGetter l, BlockPos p, Direction d) {
        Direction der = d.getCounterClockWise();
        int[] r = new int[4];
        Direction[] dirs = {der.getOpposite(), der, Direction.DOWN, Direction.UP};
        int[] max = {MAX_ANCHO - 1, MAX_ANCHO - 1, MAX_ALTO - 1, MAX_ALTO - 1};
        for (int i = 0; i < 4; i++) {
            BlockPos q = p;
            while (r[i] < max[i] && igual(l, q = q.relative(dirs[i]), d)) r[i]++;
        }
        return r;
    }

    public static BlockPos origen(BlockGetter l, BlockPos p, Direction d) {
        int[] g = grupo(l, p, d);
        return p.relative(d.getCounterClockWise().getOpposite(), g[0]).below(g[2]);
    }

    /*
     * El vídeo lo guarda el panel de abajo a la izquierda. Si se añaden paneles por la izquierda o por abajo, o se rompe ese
     * panel, el de abajo a la izquierda pasa a ser otro: el vídeo se le pasa a él (antes la pantalla se quedaba vacía).
     */
    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState antes, boolean mov) {
        super.onPlace(s, l, p, antes, mov);
        if (!l.isClientSide && !antes.is(this)) reubicar(l, p, s.getValue(FACING), null);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
        PantallaVideoBlockEntity quitado = !l.isClientSide && !nuevo.is(this) && l.getBlockEntity(p) instanceof PantallaVideoBlockEntity be && be.conVideo() ? be : null;
        // copia del vídeo antes de que desaparezca el panel
        PantallaVideoBlockEntity copia = null;
        if (quitado != null) {
            copia = new PantallaVideoBlockEntity(p, s);
            copia.load(quitado.saveWithoutMetadata());
        }
        super.onRemove(s, l, p, nuevo, mov);
        if (copia == null) return;
        Direction d = s.getValue(FACING), der = d.getCounterClockWise();
        for (Direction v : new Direction[]{der.getOpposite(), der, Direction.DOWN, Direction.UP}) {
            BlockPos q = p.relative(v);
            if (igual(l, q, d) && reubicar(l, q, d, copia)) return;
        }
    }

    /**
     * Deja el vídeo del grupo de {@code p} en su panel de abajo a la izquierda: el de {@code traido} si llega uno o, si no, el
     * de cualquier otro panel del grupo. Devuelve si ha colocado {@code traido}.
     */
    private static boolean reubicar(Level l, BlockPos p, Direction d, @Nullable PantallaVideoBlockEntity traido) {
        BlockPos o = origen(l, p, d);
        if (!(l.getBlockEntity(o) instanceof PantallaVideoBlockEntity org) || org.conVideo()) return false;
        if (traido != null) {
            org.quitarVideoA(traido);
            return true;
        }
        int[] g = grupo(l, o, d);
        Direction der = d.getCounterClockWise();
        for (int i = 0; i <= g[1]; i++)
            for (int j = 0; j <= g[3]; j++) {
                BlockPos q = o.relative(der, i).above(j);
                if (!q.equals(o) && l.getBlockEntity(q) instanceof PantallaVideoBlockEntity otro && otro.conVideo()) {
                    org.quitarVideoA(otro);
                    return false;
                }
            }
        return false;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        BlockPos o = origen(l, p, s.getValue(FACING));
        if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.rave.PantallaVideoScreen.abrir(o));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
