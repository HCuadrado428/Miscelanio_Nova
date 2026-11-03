package com.miscelanea.feria;

import com.miscelanea.registry.ModBlocks;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Puestos de feria de un bloque: pesca de patitos (estanque alrededor), tómbola y churrería. */
public class PuestoFeriaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int PATITOS = 0, TOMBOLA = 1, CHURRERIA = 2;
    private static final VoxelShape BAJO = Block.box(0, 0, 0, 16, 10, 16);
    public final int tipo;

    public PuestoFeriaBlock(Properties p, int tipo) {
        super(p);
        this.tipo = tipo;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return tipo == PATITOS ? BAJO : Block.box(0, 0, 0, 16, 16, 16);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        // con la caña en la mano el clic es para pescar, no para el puesto
        if (tipo == PATITOS && p.getMainHandItem().is(com.miscelanea.registry.ModItems.CANA_PATITOS.get())) return InteractionResult.PASS;
        if (!l.isClientSide && l.getBlockEntity(pos) instanceof PuestoFeriaBlockEntity be) be.usar(p, p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new PuestoFeriaBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.PUESTO_FERIA_BE.get(), PuestoFeriaBlockEntity::tick);
    }

    @Override
    public void animateTick(BlockState s, Level l, BlockPos pos, net.minecraft.util.RandomSource r) {
        double a = CasetaTiroBlockEntity.giro(s.getValue(FACING)), c = Math.cos(a), sn = Math.sin(a);
        if (tipo == CHURRERIA) {
            // humo de la bandeja de churros y vapor de la freidora (a la izquierda del mostrador)
            if (r.nextInt(3) == 0) {
                double x = (r.nextDouble() - 0.5) * 0.6, z = (r.nextDouble() - 0.5) * 0.4;
                l.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + 0.5 + x * c + z * sn, pos.getY() + 1.0, pos.getZ() + 0.5 - x * sn + z * c, 0, 0.03, 0);
            }
            double x = -0.9 + (r.nextDouble() - 0.5) * 0.55, z = 0.06 + (r.nextDouble() - 0.5) * 0.4;
            double wx = pos.getX() + 0.5 + x * c + z * sn, wz = pos.getZ() + 0.5 - x * sn + z * c;
            if (r.nextInt(2) == 0) l.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, wx, pos.getY() + 1.27, wz, 0, 0.02, 0);
            if (r.nextInt(6) == 0) l.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, wx, pos.getY() + 1.3, wz, 0, 0.015, 0);
            if (r.nextInt(14) == 0) l.playLocalSound(wx, pos.getY() + 1.25, wz, net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.25F, 1.6F + r.nextFloat() * 0.3F, false);
        } else if (tipo == PATITOS && r.nextInt(5) == 0) {
            // algún chapoteo junto a un pato
            double[] q = PuestoFeriaBlockEntity.pato(r.nextInt(PuestoFeriaBlockEntity.PATOS), l.getGameTime());
            l.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, pos.getX() + 0.5 + q[0] + (r.nextDouble() - 0.5) * 0.2,
                    pos.getY() + PuestoFeriaBlockEntity.AGUA + 0.02, pos.getZ() + 0.5 + q[2] + (r.nextDouble() - 0.5) * 0.2, 0, 0.05, 0);
        } else if (tipo == TOMBOLA && r.nextInt(12) == 0
                && l.getBlockEntity(pos) instanceof PuestoFeriaBlockEntity be && be.activo && l.getGameTime() - be.inicio > 60 && be.numero > 70) {
            // número con premio: chispitas alrededor del panel
            l.addParticle(net.minecraft.core.particles.ParticleTypes.WAX_ON, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.9, pos.getY() + 1.6 + r.nextDouble() * 0.5,
                    pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.9, 0, 0, 0);
        }
    }
}
