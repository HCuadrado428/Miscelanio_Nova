package com.miscelanea.arcade;

import com.miscelanea.muebles.Asiento;
import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Recreativa de un solo juego. La cabina de carreras lleva su propio asiento delante (te sienta al volante y arranca
 * Turbo); la recreativa de pistola arranca directamente el Pistolero de Chatarra, que se juega con el ratón.
 */
public class CabinaArcadeBlock extends RecreativaBlock {
    public final String fijo;
    public final boolean asiento;
    private final VoxelShape[] abajo = new VoxelShape[4], arriba = new VoxelShape[4];

    public CabinaArcadeBlock(Properties p, String fijo, boolean asiento) {
        super(p, false);
        this.fijo = fijo;
        this.asiento = asiento;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int i = d.get2DDataValue();
            abajo[i] = asiento ? Shapes.or(caja(d, 0, 0, 0, 16, 7, 16), caja(d, 1, 7, 9, 15, 16, 16), caja(d, 2, 7, 0, 14, 15, 2.5))
                    : Block.box(0, 0, 0, 16, 16, 16);
            arriba[i] = caja(d, 0, 0, 2, 16, 16, 16);
        }
    }

    /** Caja en coordenadas de la máquina mirando al norte (z = 0 es el frente), girada a la dirección d. */
    static VoxelShape caja(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        int i = s.getValue(FACING).get2DDataValue();
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? abajo[i] : arriba[i];
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos abajo = s.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(p instanceof ServerPlayer sp)) return InteractionResult.PASS;
        InteractionResult r = usar(l, abajo, sp, false, fijo);
        if (asiento && l.getBlockEntity(abajo) instanceof RecreativaBlockEntity be && sp.getUUID().equals(be.jugador)) {
            // agachado (el jugador 2 que se enlaza con la cabina de al lado) se levantaría al momento: se le sienta un poco después
            if (sp.isShiftKeyDown()) l.scheduleTick(abajo, this, 4);
            else sentar(l, abajo, s, sp);
        }
        return r;
    }

    /** Sienta al jugador en el asiento de la cabina, mirando a la pantalla (si no está sentado ya y el asiento está libre). */
    private static void sentar(Level l, BlockPos abajo, BlockState s, ServerPlayer sp) {
        if (sp.isPassenger() || !l.getEntitiesOfClass(Asiento.class, new AABB(abajo).inflate(0, 0.5, 0)).isEmpty()) return;
        Direction f = s.getValue(FACING);
        Asiento a = new Asiento(ModEntities.ASIENTO.get(), l);
        a.setPos(abajo.getX() + 0.5 + f.getStepX() * 0.22, abajo.getY() + 0.42 - Asiento.SOBRE_COJIN, abajo.getZ() + 0.5 + f.getStepZ() * 0.22);
        a.setYRot(f.getOpposite().toYRot());
        l.addFreshEntity(a);
        sp.startRiding(a);
        sp.setYRot(f.getOpposite().toYRot());
        sp.setYHeadRot(f.getOpposite().toYRot());
    }

    /** Asiento aplazado: espera a que el jugador de la cabina suelte la tecla de agacharse. */
    @Override
    public void tick(BlockState s, ServerLevel l, BlockPos pos, RandomSource r) {
        if (!asiento || s.getValue(HALF) != DoubleBlockHalf.LOWER || !(l.getBlockEntity(pos) instanceof RecreativaBlockEntity be) || be.jugador == null) return;
        ServerPlayer sp = l.getServer().getPlayerList().getPlayer(be.jugador);
        if (sp == null || sp.level() != l || sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 9) return;
        if (sp.isShiftKeyDown()) {
            l.scheduleTick(pos, this, 4);
            return;
        }
        sentar(l, pos, s, sp);
    }
}
