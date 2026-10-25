package com.miscelanea.programa;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Generador del sótano: 1) llénalo con un bidón, 2) activa los dos interruptores de los lados,
 * 3) dale a la manivela cuando la aguja esté en la zona verde, tres veces. Si fallas, hace ruido y vuelves a empezar.
 */
public class GeneradorBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty LLENO = BooleanProperty.create("lleno");
    public static final BooleanProperty ENCENDIDO = BooleanProperty.create("encendido");
    private static final VoxelShape FORMA = Block.box(1, 0, 1, 15, 15, 15);
    /** Periodo de la aguja (ticks) y aciertos necesarios. */
    public static final int PERIODO = 44, ACIERTOS = 3;

    public GeneradorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LLENO, false).setValue(ENCENDIDO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LLENO, ENCENDIDO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    /** Posición de la aguja (0..1) en un instante: va y viene. Igual en cliente y servidor. */
    public static float aguja(long tiempo) {
        double t = (tiempo % PERIODO) / (double) PERIODO;
        return (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2));
    }

    /** Zona verde para el acierto número n (0..2). */
    public static float[] zona(int n) {
        float c = new float[]{0.78F, 0.42F, 0.88F}[Math.floorMod(n, 3)];
        return new float[]{c - 0.08F, c + 0.08F};
    }

    /** ¿Los dos interruptores (a 4 bloques) están encendidos? */
    public static boolean interruptores(Level level, BlockPos pos) {
        int n = 0, on = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 2, 4))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof InterruptorBlock) {
                n++;
                if (s.getValue(InterruptorBlock.ON)) on++;
            }
        }
        return n > 0 && n == on;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack mano = player.getItemInHand(hand);
        if (s.getValue(ENCENDIDO)) return InteractionResult.CONSUME;
        if (!s.getValue(LLENO)) {
            if (!mano.is(ModBlocks.BIDON.get().asItem())) mano = player.getOffhandItem();
            if (mano.is(ModBlocks.BIDON.get().asItem())) {
                if (!player.isCreative()) mano.shrink(1);
                level.setBlock(pos, s.setValue(LLENO, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.6F);
                if (player instanceof ServerPlayer sp) Prueba.generadorCambio((ServerLevel) level, sp);
            } else {
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.6F);
            }
            return InteractionResult.CONSUME;
        }
        if (!interruptores(level, pos)) {
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.6F);
            return InteractionResult.CONSUME;
        }
        // Manivela
        if (player instanceof ServerPlayer sp0) Acciones.poner(sp0, Acciones.Accion.MANIVELA, 14);
        int n = Prueba.manivela();
        float a = aguja(level.getGameTime());
        float[] z = zona(n);
        if (a >= z[0] && a <= z[1]) {
            n++;
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.5F + n * 0.15F);
            if (n >= ACIERTOS) {
                level.setBlock(pos, s.setValue(ENCENDIDO, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5F, 0.6F);
                Prueba.setManivela(0);
                if (player instanceof ServerPlayer sp) Prueba.generadorArrancado((ServerLevel) level, sp);
                return InteractionResult.CONSUME;
            }
        } else {
            n = 0;
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 0.5F);
            com.miscelanea.carcel.Ruido.alertar((ServerLevel) level, net.minecraft.world.phys.Vec3.atCenterOf(pos), 20, player);
        }
        Prueba.setManivela(n);
        if (player instanceof ServerPlayer sp) Prueba.generadorCambio((ServerLevel) level, sp);
        return InteractionResult.CONSUME;
    }
}
