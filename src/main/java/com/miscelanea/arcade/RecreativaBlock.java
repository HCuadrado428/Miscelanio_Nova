package com.miscelanea.arcade;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModBlocks;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/** Máquina recreativa de dos bloques de alto. Clic derecho con fichas para jugar. */
public class RecreativaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape ABAJO = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape[] ARRIBA = {Block.box(0, 0, 2, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 14), Block.box(0, 0, 0, 14, 16, 16), Block.box(2, 0, 0, 16, 16, 16)};

    /** Recreativa doble: un mueble ancho con dos juegos de mandos. */
    public final boolean doble;

    public RecreativaBlock(Properties p) {
        this(p, false);
    }

    public RecreativaBlock(Properties p, boolean doble) {
        super(p);
        this.doble = doble;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (s.getValue(HALF) == DoubleBlockHalf.LOWER) return ABAJO;
        return ARRIBA[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case EAST -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockPos p = c.getClickedPos();
        Level l = c.getLevel();
        if (p.getY() >= l.getMaxBuildHeight() - 1 || !l.getBlockState(p.above()).canBeReplaced(c)) return null;
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity e, ItemStack st) {
        l.setBlock(p.above(), s.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        if (s.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState d = l.getBlockState(p.below());
        return d.is(this) && d.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState otro, LevelAccessor l, BlockPos p, BlockPos po) {
        DoubleBlockHalf h = s.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (h == DoubleBlockHalf.LOWER) == (dir == Direction.UP))
            return otro.is(this) && otro.getValue(HALF) != h ? s : Blocks.AIR.defaultBlockState();
        return s;
    }

    @Override
    public void playerWillDestroy(Level l, BlockPos p, BlockState s, Player pl) {
        if (!l.isClientSide && pl.isCreative() && s.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos d = p.below();
            if (l.getBlockState(d).is(this)) {
                l.setBlock(d, Blocks.AIR.defaultBlockState(), 35);
                l.levelEvent(pl, 2001, d, Block.getId(l.getBlockState(d)));
            }
        }
        super.playerWillDestroy(l, p, s, pl);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? new RecreativaBlockEntity(p, s) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.RECREATIVA_BE.get(), RecreativaBlockEntity::tickServidor);
    }

    /** Juego fijo de la máquina (cabinas y recreativas dedicadas); vacío en la recreativa normal, que tiene menú. */
    public String juegoFijo() {
        return "";
    }

    public static boolean esFicha(ItemStack s) {
        return s.getItem() instanceof FichaItem;
    }

    public static boolean gastarFicha(Player p) {
        var inv = p.getInventory();
        ItemStack mano = p.getMainHandItem();
        if (esFicha(mano)) {
            mano.shrink(1);
            return true;
        }
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (esFicha(inv.getItem(i))) {
                inv.getItem(i).shrink(1);
                return true;
            }
        return false;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos abajo = s.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(p instanceof ServerPlayer sp)) return InteractionResult.PASS;
        return usar(l, abajo, sp, doble);
    }

    /** Uso de una máquina (recreativa, doble o mesa cóctel): jugar, unirse como 2P o enlazarse con la de al lado. */
    public static InteractionResult usar(Level l, BlockPos abajo, ServerPlayer sp, boolean doble) {
        return usar(l, abajo, sp, doble, "");
    }

    /**
     * Igual, pero en una máquina de un solo juego (fijo no vacío): sin menú de juegos, y solo se enlaza por cable con otra
     * máquina igual (dos cabinas de carreras) si su juego es de dos jugadores.
     */
    public static InteractionResult usar(Level l, BlockPos abajo, ServerPlayer sp, boolean doble, String fijo) {
        if (!(l.getBlockEntity(abajo) instanceof RecreativaBlockEntity be)) return InteractionResult.PASS;
        if (doble && be.ocupadaPorOtro(sp) && (be.jugador2 == null || be.jugador2.equals(sp.getUUID()))) {
            unirse(l, be, sp, null);
            return InteractionResult.CONSUME;
        }
        if (be.ocupadaPorOtro(sp)) {
            sp.displayClientMessage(Component.translatable("msg.miscelanea.arcade.ocupada").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        // recreativa normal (o cabina de un juego a dos) junto a otra igual en la que alguien juega: agachado, se enlazan
        // con un cable y se juega a dos
        if (!doble && (fijo.isEmpty() || ArcadeJuegos.dosJugadores(fijo))) {
            RecreativaBlockEntity vecina = vecinaConPartida(l, abajo, sp);
            if (vecina != null) {
                if (sp.isShiftKeyDown()) {
                    be.jugador = sp.getUUID();
                    vecina.enlazada = abajo;
                    unirse(l, vecina, sp, abajo);
                    return InteractionResult.CONSUME;
                }
                ServerPlayer host = sp.server.getPlayerList().getPlayer(vecina.jugador);
                sp.displayClientMessage(Component.translatable("msg.miscelanea.arcade.enlace", host == null ? "?" : host.getGameProfile().getName())
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        boolean libre = sp.getAbilities().instabuild;
        if (esFicha(sp.getMainHandItem()) && gastarFicha(sp)) {
            be.creditos++;
            be.setChanged();
            l.playSound(null, abajo, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        if (be.creditos <= 0 && !libre) {
            sp.displayClientMessage(Component.translatable("msg.miscelanea.arcade.inserta").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        be.jugador = sp.getUUID();
        if (sp.getUUID().equals(be.jugador2)) be.jugador2 = null;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ArcadeAbrirPacket(abajo, be.creditos, libre, 0, doble, 0, fijo, "", 1));
        return InteractionResult.CONSUME;
    }

    /** Se une como jugador 2 a la partida de be (en la misma máquina o, si propia no es null, desde la recreativa enlazada). */
    private static void unirse(Level l, RecreativaBlockEntity be, ServerPlayer sp, @Nullable BlockPos propia) {
        BlockPos host = be.getBlockPos();
        be.jugador2 = sp.getUUID();
        if (esFicha(sp.getMainHandItem()) && gastarFicha(sp)) {
            be.creditos++;
            be.setChanged();
            l.playSound(null, propia != null ? propia : host, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        ServerPlayer anfitrion = sp.server.getPlayerList().getPlayer(be.jugador);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ArcadeAbrirPacket(host, be.creditos, sp.getAbilities().instabuild, 4, propia == null, 0, "",
                anfitrion == null ? "" : anfitrion.getGameProfile().getName(), 2, propia == null ? host : propia));
        be.avisarCompaneros();
    }

    /**
     * Máquina enlazable a uno o dos bloques (mismo nivel) en la que juega otro y que aún no tiene compañero: desde una
     * recreativa normal, otra recreativa normal; desde una cabina o recreativa de un solo juego, otra del mismo bloque
     * (nunca se mezclan).
     */
    @Nullable
    static RecreativaBlockEntity vecinaConPartida(Level l, BlockPos abajo, ServerPlayer sp) {
        Block propio = l.getBlockState(abajo).getBlock();
        boolean cabina = propio instanceof RecreativaBlock pr && !pr.juegoFijo().isEmpty();
        for (Direction d : Direction.Plane.HORIZONTAL)
            for (int k = 1; k <= 2; k++) {
                BlockPos q = abajo.relative(d, k);
                BlockState st = l.getBlockState(q);
                if (!(st.getBlock() instanceof RecreativaBlock rb) || rb.doble || st.getValue(HALF) != DoubleBlockHalf.LOWER) continue;
                if (cabina ? rb != propio : !rb.juegoFijo().isEmpty()) continue;
                if (l.getBlockEntity(q) instanceof RecreativaBlockEntity v && v.ocupadaPorOtro(sp) && (v.jugador2 == null || v.jugador2.equals(sp.getUUID())))
                    return v;
            }
        return null;
    }
}
