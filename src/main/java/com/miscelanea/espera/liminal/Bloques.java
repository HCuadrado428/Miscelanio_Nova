package com.miscelanea.espera.liminal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Los bloques de atrezo del espacio liminal (2.0.0). Todos son bloques normales con su modelo (se dibujan con el chunk:
 * no cuestan nada por fotograma); lo único que se mueve (la escalera mecánica, el maniquí, el ventilador y el flotador)
 * lleva bloque-entidad con GeckoLib (ver {@link AtrezoBE}).
 */
public final class Bloques {
    private Bloques() {}

    public static final IntegerProperty PARTE8 = IntegerProperty.create("parte", 0, 7);
    public static final IntegerProperty PARTE4 = IntegerProperty.create("parte", 0, 3);
    public static final IntegerProperty PARTE3 = IntegerProperty.create("parte", 0, 2);
    public static final IntegerProperty MODELO = IntegerProperty.create("modelo", 0, 1);
    public static final IntegerProperty COLOR8 = IntegerProperty.create("color", 0, 7);
    public static final IntegerProperty COLOR4 = IntegerProperty.create("color", 0, 3);
    public static final IntegerProperty COLOR2 = IntegerProperty.create("color", 0, 1);
    public static final IntegerProperty ESTADO = IntegerProperty.create("estado", 0, 2);
    public static final IntegerProperty POSE = IntegerProperty.create("pose", 0, 2);
    public static final BooleanProperty FAROS = BooleanProperty.create("faros");
    public static final BooleanProperty ARRIBA = BooleanProperty.create("arriba");
    public static final BooleanProperty VACIA = BooleanProperty.create("vacia");
    public static final BooleanProperty GIRADO = BooleanProperty.create("girado");
    public static final BooleanProperty SUBIDA = BooleanProperty.create("subida");
    public static final BooleanProperty RELLANO = BooleanProperty.create("rellano");
    public static final BooleanProperty LENTO = BooleanProperty.create("lento");
    public static final EnumProperty<Direction.Axis> EJE = BlockStateProperties.HORIZONTAL_AXIS;

    // ------------------------------------------------------------------ atrezo que mira a un lado (con su forma)
    /** Un mueble que mira a un lado: la forma se da mirando al norte (en px) y se gira sola. */
    public static class Mueble extends HorizontalDirectionalBlock {
        private final VoxelShape[] forma;
        private final boolean choca;

        public Mueble(Properties p, boolean choca, double... cajas) {
            super(p);
            this.forma = Formas.cuatro(cajas);
            this.choca = choca;
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
            return forma[Formas.indice(s.getValue(FACING))];
        }

        @Override
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return choca ? getShape(s, l, p, c) : Shapes.empty();
        }
    }

    /** Las sillas de la sala de espera (en fila: la viga las une), de colores (con tinte). */
    public static class SillaEspera extends Mueble {
        public SillaEspera(Properties p) {
            super(p, true, 1.5, 0, 2, 14.5, 7.6, 12.2, 2, 7.6, 12.2, 14, 17, 13.4);
            registerDefaultState(defaultBlockState().setValue(COLOR4, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, COLOR4);
        }
    }

    /** El carrito del súper abandonado (a veces torcido). */
    public static class Carrito extends Mueble {
        public Carrito(Properties p) {
            super(p, true, 2, 0, 1, 14, 16, 15);
            registerDefaultState(defaultBlockState().setValue(GIRADO, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, GIRADO);
        }
    }

    /** El pupitre (con la silla subida encima, patas arriba, cuando se acaban las clases). */
    public static class Pupitre extends Mueble {
        public Pupitre(Properties p) {
            super(p, true, 0.8, 0, 1, 15.2, 12.4, 12);
            registerDefaultState(defaultBlockState().setValue(SUBIDA, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, SUBIDA);
        }
    }

    // ------------------------------------------------------------------ el coche
    /**
     * Un coche aparcado de 2 x 4 bloques (turismo o furgoneta), cada bloque con su trozo del modelo. La carrocería lleva
     * tinte (el color lo pone el juego: un modelo para los ocho colores). Si se rompe un trozo, se va el coche entero (y
     * el haz de los faros). Si le das, salta la alarma un momento.
     */
    public static class Coche extends HorizontalDirectionalBlock {
        public Coche(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTE8, 0).setValue(MODELO, 0).setValue(COLOR8, 0).setValue(FAROS, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PARTE8, MODELO, COLOR8, FAROS);
        }

        private static final VoxelShape[] ALTO = {Block.box(0, 0, 0, 16, 12, 16), Block.box(0, 0, 0, 16, 21, 16), Block.box(0, 0, 0, 16, 13, 16), Block.box(0, 0, 0, 16, 27, 16)};

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            int k = s.getValue(PARTE8) / 2;
            if (s.getValue(MODELO) == 0) return ALTO[k == 1 || k == 2 ? 1 : 0];
            return ALTO[k == 0 ? 2 : 3];
        }

        /** El bloque de la pieza (i a lo ancho, k a lo largo desde el morro) de un coche cuyo morro izquierdo está en "ancla". */
        public static BlockPos pieza(BlockPos ancla, Direction f, int i, int k) {
            Direction r = f.getClockWise(), b = f.getOpposite();
            return ancla.relative(r, i).relative(b, k);
        }

        @Override
        public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS;
            long ahora = l.getGameTime();
            if (ahora - pl.getPersistentData().getLong("miscelanea_alarma") < 60) return InteractionResult.CONSUME;
            pl.getPersistentData().putLong("miscelanea_alarma", ahora);
            // la alarma: cuatro pitidos y se calla (como la de los sustos, pero de cerca)
            ServerLevel sl = (ServerLevel) l;
            for (int i = 0; i < 6; i++) {
                int ii = i;
                sl.getServer().tell(new net.minecraft.server.TickTask(sl.getServer().getTickCount() + i * 6, () ->
                        sl.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.9F, ii % 2 == 0 ? 1.4F : 1.1F)));
            }
            pl.displayClientMessage(Component.translatable("espera.miscelanea.coche." + l.random.nextInt(6)), true);
            return InteractionResult.CONSUME;
        }

        @Override
        public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
            if (!s.is(nuevo.getBlock()) && !l.isClientSide) {
                Direction f = s.getValue(FACING);
                int parte = s.getValue(PARTE8);
                BlockPos ancla = p.relative(f.getClockWise(), -(parte % 2)).relative(f.getOpposite(), -(parte / 2));
                for (int i = 0; i < 2; i++) {
                    for (int k = -2; k < 4; k++) {
                        BlockPos q = pieza(ancla, f, i, k);
                        if (q.equals(p)) continue;
                        BlockState t = l.getBlockState(q);
                        if ((t.is(this) && t.getValue(FACING) == f) || (k < 0 && t.getBlock() instanceof Haz)) l.setBlock(q, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            }
            super.onRemove(s, l, p, nuevo, mov);
        }
    }

    /** El haz de luz de los faros (2 x 2 bloques delante del coche): translúcido, da luz y no se toca. */
    public static class Haz extends HorizontalDirectionalBlock {
        public Haz(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTE4, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PARTE4);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }

        @Override
        public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
            return true;
        }
    }

    // ------------------------------------------------------------------ la máquina expendedora
    /**
     * Máquina expendedora de dos de alto con su cristal iluminado. Una vez por máquina suelta algo (o se traga la moneda)
     * y dice en el HUD una tontería; luego, agotada.
     */
    public static class Maquina extends HorizontalDirectionalBlock {
        private static final VoxelShape[] FORMA = Formas.cuatro(0.5, 0, 1, 15.5, 16, 16);

        public Maquina(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ARRIBA, false).setValue(COLOR2, 0).setValue(VACIA, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, ARRIBA, COLOR2, VACIA);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA[Formas.indice(s.getValue(FACING))];
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            BlockPos p = c.getClickedPos();
            if (p.getY() >= c.getLevel().getMaxBuildHeight() - 1 || !c.getLevel().getBlockState(p.above()).canBeReplaced(c)) return null;
            return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()).setValue(COLOR2, c.getLevel().random.nextInt(2));
        }

        @Override
        public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable net.minecraft.world.entity.LivingEntity e, ItemStack st) {
            l.setBlock(p.above(), s.setValue(ARRIBA, true), 3);
        }

        @Override
        public BlockState updateShape(BlockState s, Direction d, BlockState otro, LevelAccessor l, BlockPos p, BlockPos po) {
            boolean arriba = s.getValue(ARRIBA);
            if ((d == Direction.UP && !arriba) || (d == Direction.DOWN && arriba))
                return otro.is(this) && otro.getValue(ARRIBA) != arriba ? s : Blocks.AIR.defaultBlockState();
            return s;
        }

        /** Lo que suelta (con su chiste en el HUD): -1 = nada (se ha tragado la moneda). */
        static final net.minecraft.world.item.Item[] PREMIOS = {Items.APPLE, Items.HONEY_BOTTLE, Items.COOKIE, null, Items.COOKIE, Items.HONEY_BOTTLE, Items.CAKE, Items.DRIED_KELP};

        @Override
        public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS;
            BlockPos abajo = s.getValue(ARRIBA) ? p.below() : p;
            BlockState sa = l.getBlockState(abajo);
            if (!sa.is(this)) return InteractionResult.PASS;
            if (sa.getValue(VACIA)) {
                pl.displayClientMessage(Component.translatable("espera.miscelanea.maquina.agotada"), true);
                l.playSound(null, p, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.2F);
                return InteractionResult.CONSUME;
            }
            int k = l.random.nextInt(PREMIOS.length);
            // la tarta, muy de vez en cuando (la del pastel es mentira, pero esta no)
            if (k == 6 && l.random.nextInt(4) != 0) k = 2;
            l.setBlock(abajo, sa.setValue(VACIA, true), 2);
            BlockState arr = l.getBlockState(abajo.above());
            if (arr.is(this)) l.setBlock(abajo.above(), arr.setValue(VACIA, true), 2);
            l.playSound(null, p, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.7F, 0.8F);
            l.playSound(null, p, SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.6F, 0.7F);
            if (PREMIOS[k] != null) {
                Direction f = sa.getValue(FACING);
                var ie = new net.minecraft.world.entity.item.ItemEntity(l, abajo.getX() + 0.5 + f.getStepX() * 0.7, abajo.getY() + 0.3, abajo.getZ() + 0.5 + f.getStepZ() * 0.7,
                        new ItemStack(PREMIOS[k], k == 4 ? 2 : 1));
                ie.setDeltaMovement(f.getStepX() * 0.08, 0.1, f.getStepZ() * 0.08);
                l.addFreshEntity(ie);
            }
            pl.displayClientMessage(Component.translatable("espera.miscelanea.maquina." + k), true);
            return InteractionResult.CONSUME;
        }
    }

    // ------------------------------------------------------------------ piscina
    /** La escalerilla de acero de la piscina (se sube como una escalera de mano); la de arriba lleva los pasamanos curvos. */
    public static class Escalerilla extends LadderBlock {
        public Escalerilla(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(ARRIBA, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            super.createBlockStateDefinition(b);
            b.add(ARRIBA);
        }
    }

    /** La silla alta del socorrista (tres de alto, vacía, con el flotador colgado). */
    public static class Socorrista extends HorizontalDirectionalBlock {
        private static final VoxelShape[][] FORMA = {Formas.cuatro(1.5, 0, 1.5, 14.5, 16, 14.5), Formas.cuatro(1, 0, 1, 15, 15.6, 15),
                Formas.cuatro(1, 0, 1, 15, 12, 15)};

        public Socorrista(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTE3, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PARTE3);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA[s.getValue(PARTE3)][Formas.indice(s.getValue(FACING))];
        }

        @Override
        public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
            if (!s.is(nuevo.getBlock()) && !l.isClientSide) {
                int k = s.getValue(PARTE3);
                for (int i = 0; i < 3; i++) {
                    BlockPos q = p.above(i - k);
                    if (i != k && l.getBlockState(q).is(this)) l.setBlock(q, Blocks.AIR.defaultBlockState(), 35);
                }
            }
            super.onRemove(s, l, p, nuevo, mov);
        }
    }

    /** El foco de la pared de la piscina, debajo del agua. De vez en cuando, una gota que cae con eco. */
    public static class LuzPiscina extends HorizontalDirectionalBlock {
        public LuzPiscina(Properties p) {
            super(p);
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
        public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            if (r.nextInt(90) == 0)
                l.playLocalSound(p.getX() + 0.5, p.getY() + 3.5, p.getZ() + 0.5, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.AMBIENT, 0.6F, 0.7F + r.nextFloat() * 0.2F, false);
        }
    }

    // ------------------------------------------------------------------ luces
    /** El tubo fluorescente colgado del techo del garaje: encendido, fundido o parpadeando (con su zumbido). */
    public static class Tubo extends Block {
        private static final VoxelShape X = Block.box(0.5, 11.6, 5, 15.5, 16, 11), Z = Block.box(5, 11.6, 0.5, 11, 16, 15.5);

        public Tubo(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(EJE, Direction.Axis.X).setValue(ESTADO, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(EJE, ESTADO);
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(EJE, c.getHorizontalDirection().getAxis());
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(EJE) == Direction.Axis.X ? X : Z;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }

        @Override
        public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            if (s.getValue(ESTADO) == 2) zumbido(l, p, r, 6);
            else if (s.getValue(ESTADO) == 0 && r.nextInt(14) == 0) zumbido(l, p, r, 3);
        }
    }

    /** El zumbido de un fluorescente (con posición; cuanto más falla, más se oye). */
    static void zumbido(Level l, BlockPos p, RandomSource r, int fallo) {
        if (r.nextInt(12) >= fallo) return;
        l.playLocalSound(p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, LiminalRegistro.ZUMBIDO.get(), SoundSource.BLOCKS, 0.12F + fallo * 0.03F, 0.92F + r.nextFloat() * 0.16F, false);
        if (fallo > 4 && r.nextInt(5) == 0)
            l.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, p.getX() + r.nextDouble(), p.getY() - 0.05, p.getZ() + r.nextDouble(), 0, -0.05, 0);
    }

    /** El panel fluorescente del techo que parpadea (textura animada: la luz no cambia, no hay nada que recalcular). */
    public static class Parpadea extends Block {
        public Parpadea(Properties p) {
            super(p);
        }

        @Override
        public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            zumbido(l, p.below(), r, 6);
        }
    }

    /** Tuberías del techo del garaje (no chocan). */
    public static class Tuberia extends Block {
        private static final VoxelShape X = Block.box(0, 10.6, 5.6, 16, 16, 12.8), Z = Block.box(5.6, 10.6, 0, 12.8, 16, 16);

        public Tuberia(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(EJE, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(EJE);
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(EJE, c.getHorizontalDirection().getAxis());
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(EJE) == Direction.Axis.X ? X : Z;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }
    }

    // ------------------------------------------------------------------ invisibles (los de la escalera mecánica)
    /** Macizo e invisible: el hueco de debajo de la escalera mecánica (lo que se ve es el modelo de la escalera). */
    public static class Oculto extends Block {
        public Oculto(Properties p) {
            super(p);
        }

        @Override
        public RenderShape getRenderShape(BlockState s) {
            return RenderShape.INVISIBLE;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
            return true;
        }

        @Override
        public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
            return 1.0F;
        }
    }

    /** Un peldaño invisible de la escalera mecánica (para pisar: lo que se ve son los peldaños que se mueven). */
    public static class Escalon extends StairBlock {
        public Escalon(Properties p) {
            super(() -> Blocks.SMOOTH_STONE.defaultBlockState(), p);
        }

        @Override
        public RenderShape getRenderShape(BlockState s) {
            return RenderShape.INVISIBLE;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
            return true;
        }

        @Override
        public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
            return 1.0F;
        }
    }
}
