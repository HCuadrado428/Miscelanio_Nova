package com.miscelanea.montana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vía de la montaña rusa. Tiene una dirección y una forma (recta, curva a la izquierda o a la derecha, subida o bajada)
 * que coge sola al ponerla según la vía que haya al lado ({@link Colocacion}); con la mano vacía, clic derecho la gira y
 * agachado le cambia la forma. Una bajada se pone un bloque más abajo que la vía de la que viene (como las rampas de los
 * raíles normales). Tipos: normal, con cadena (sube los vagones despacio), estación (los para, espera a que suba la gente
 * y los lanza; con redstone, sale ya) y freno (no deja pasar de una velocidad; con redstone, para el tren y lo retiene).
 * La geometría y la física están en {@link Via}.
 */
public class ViaMontanaBlock extends HorizontalDirectionalBlock {
    public enum Forma implements StringRepresentable {
        RECTA, CURVA_IZQ, CURVA_DER, SUBIDA, BAJADA;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Tipo {NORMAL, CADENA, ESTACION, FRENO}

    public static final EnumProperty<Forma> FORMA = EnumProperty.create("forma", Forma.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public final Tipo tipo;

    private static final VoxelShape PLANA = Block.box(0, 0, 0, 16, 4, 16);

    public ViaMontanaBlock(Properties p, Tipo tipo) {
        super(p);
        this.tipo = tipo;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMA, Forma.RECTA).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FORMA, POWERED);
    }

    /** Formas que admite cada tipo (la estación y el freno, solo recta; la cadena, recta, subida y bajada). */
    public boolean admite(Forma f) {
        return switch (tipo) {
            case NORMAL -> true;
            case CADENA -> f == Forma.RECTA || f == Forma.SUBIDA || f == Forma.BAJADA;
            default -> f == Forma.RECTA;
        };
    }

    /** Lo que se decidió al colocar (en el servidor): la pieza de al lado que hay que girar o inclinar para unirla. */
    private static BlockPos planPos, planOtroPos;
    private static BlockState planOtro;
    private static long planTick;

    /**
     * Al ponerla, coge sola la forma y la dirección que encajan con lo que hay alrededor, como los raíles: sigue a la vía
     * que acaba en este bloque (si está un bloque más abajo, es una bajada), gira o inclina la vía suelta de al lado para
     * unirse a ella (curva o subida) y se convierte en curva o subida si así enlaza con una vía que ya hay delante.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Level l = c.getLevel();
        BlockPos pos = c.getClickedPos();
        Colocacion.Plan p = Colocacion.planear(l, pos, c.getHorizontalDirection(), this::admite, tipo);
        if (!l.isClientSide) {
            planPos = p.otroPos() == null ? null : pos.immutable();
            planOtroPos = p.otroPos();
            planOtro = p.otro();
            planTick = l.getGameTime();
        }
        return defaultBlockState().setValue(FACING, p.dir()).setValue(FORMA, p.forma()).setValue(POWERED, l.hasNeighborSignal(pos));
    }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState s, @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity quien, net.minecraft.world.item.ItemStack st) {
        super.setPlacedBy(l, pos, s, quien, st);
        if (l.isClientSide || planPos == null || !planPos.equals(pos) || planTick != l.getGameTime()) return;
        BlockState otro = l.getBlockState(planOtroPos);
        if (otro.getBlock() == planOtro.getBlock() && otro.getValue(FACING) == planOtro.getValue(FACING))
            l.setBlock(planOtroPos, planOtro, Block.UPDATE_ALL);
        planPos = null;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack st, @javax.annotation.Nullable BlockGetter l, java.util.List<Component> t, net.minecraft.world.item.TooltipFlag f) {
        String k = "montana.miscelanea.ayuda." + tipo.name().toLowerCase(java.util.Locale.ROOT);
        t.add(Component.translatable(k).withStyle(net.minecraft.ChatFormatting.GRAY));
        t.add(Component.translatable("montana.miscelanea.ayuda.colocar").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return PLANA;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        // en las rampas se puede andar por encima como por una losa, y los vagones no chocan con nada
        return s.getValue(FORMA) == Forma.SUBIDA || s.getValue(FORMA) == Forma.BAJADA ? Shapes.empty() : PLANA;
    }

    /** Con la mano vacía: clic derecho la gira; agachado, le cambia la forma. */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
        if (!pl.getItemInHand(h).isEmpty() || h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        BlockState n;
        if (pl.isShiftKeyDown()) {
            Forma[] fs = Forma.values();
            Forma f = s.getValue(FORMA);
            do f = fs[(f.ordinal() + 1) % fs.length]; while (!admite(f));
            n = s.setValue(FORMA, f);
        } else n = s.setValue(FACING, s.getValue(FACING).getClockWise());
        l.setBlock(p, n, Block.UPDATE_ALL);
        l.playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
        pl.displayClientMessage(Component.translatable("montana.miscelanea.pieza",
                Component.translatable("montana.miscelanea.forma." + n.getValue(FORMA).getSerializedName()),
                Component.translatable("montana.miscelanea.dir." + n.getValue(FACING).getSerializedName())), true);
        return InteractionResult.CONSUME;
    }

    /** En el aire, le sale su pilar hasta el suelo. */
    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState antes, boolean mov) {
        super.onPlace(s, l, p, antes, mov);
        if (!antes.is(s.getBlock())) PilarBlock.levantar(l, p);
    }

    @Override
    public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos from, boolean mov) {
        if ((tipo != Tipo.ESTACION && tipo != Tipo.FRENO) || l.isClientSide) return;
        boolean on = l.hasNeighborSignal(p);
        if (on != s.getValue(POWERED)) l.setBlock(p, s.setValue(POWERED, on), Block.UPDATE_CLIENTS);
    }
}
