package com.miscelanea.camaras;

import com.miscelanea.multibloque.MultiBloque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * La televisión (ver {@link MontadoBlock}): una pantalla plana de dos bloques de ancho (su hueco y el de la derecha de quien la
 * mira de frente: ver {@link MultiBloque}). El canal 0 es el automático, que rota entre todas las cámaras.
 */
public class TeleBlock extends MontadoBlock implements MultiBloque {
    /** Pantalla (en píxeles del modelo): la imagen de 16:9 cabe dentro del marco. */
    public static final double PX0 = -13.5, PX1 = 13.5, PY0 = 0.40625, PY1 = 15.59375, PZ = 12.98;
    private final VoxelShape[] choques = new VoxelShape[6];

    public TeleBlock(Properties p) {
        super(p, TeleBlockEntity::new, () -> CamarasRegistro.TELE_BE.get());
    }

    /** Clic derecho: la imagen en pantalla grande; agachado: canal siguiente (0 es el automático). */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (!j.isShiftKeyDown()) {
            if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.camaras.VistaCamaraScreen.abrir(p));
            return InteractionResult.SUCCESS;
        }
        return super.use(s, l, p, j, m, h);
    }

    @Override
    protected boolean agachadoRetrocede() { return false; }

    @Override
    protected boolean sueloHaciaJugador() { return true; }

    @Override
    protected double[] caja() { return new double[]{-14.25, 0, 12.6, 14.25, 16, 16}; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = super.getStateForPlacement(c);
        return s != null && MultiBloque.cabe(this, s, c) ? s : null;
    }

    /** El otro hueco: el de -x del modelo (a la derecha de quien la mira de frente; colgada del techo, el del oeste). */
    @Override
    public List<BlockPos> partes(BlockState s) {
        Direction d = s.getValue(Montaje.FACING);
        return List.of(BlockPos.ZERO.relative(d == Direction.DOWN ? Direction.WEST : d.getCounterClockWise()));
    }

    @Override
    public VoxelShape formaCompleta(BlockState s) {
        return forma(s);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        int i = s.getValue(Montaje.FACING).get3DDataValue();
        VoxelShape f = choques[i];
        if (f == null) f = choques[i] = MultiBloque.trozo(forma(s));
        return f;
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

    @Override
    protected int canalMin() { return 0; }

    @Override
    protected int canalActual(BlockEntity be) { return be instanceof TeleBlockEntity t ? t.canal() : 1; }

    @Override
    protected void ponerCanal(BlockEntity be, int canal) {
        if (be instanceof TeleBlockEntity t) t.canal(canal);
    }
}
