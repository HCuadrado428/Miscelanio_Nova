package com.miscelanea.espera.liminal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * El bloque-entidad del atrezo que se mueve (GeckoLib): la escalera mecánica en marcha, el maniquí, el ventilador del
 * techo y el flotador. No tiene tic ni guarda nada: solo dibuja (y GeckoLib solo anima lo que se ve). Hay muy pocos por
 * chunk y se dejan de dibujar a 40 bloques (la niebla ya no deja ver más).
 */
public class AtrezoBE extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Solo en el cliente: hacia dónde mira el maniquí (grados; NaN = aún no lo ha visto nadie). */
    public float giro = Float.NaN;
    /** Solo en el cliente: si el jugador lo estaba mirando en el último fotograma. */
    public boolean mirado;
    /** Solo en el cliente: cuántas veces se ha girado (para la prueba). */
    public int giros;

    public AtrezoBE(BlockPos p, BlockState s) {
        super(LiminalRegistro.ATREZO_BE.get(), p, s);
    }

    static final RawAnimation SUBE_TECHO = RawAnimation.begin().thenLoop("animation.escalera_mecanica.techo");
    static final RawAnimation SUBE_RELLANO = RawAnimation.begin().thenLoop("animation.escalera_mecanica.rellano");
    static final RawAnimation[] POSES = {RawAnimation.begin().thenLoop("animation.maniqui.pose0"), RawAnimation.begin().thenLoop("animation.maniqui.pose1"),
            RawAnimation.begin().thenLoop("animation.maniqui.pose2")};
    static final RawAnimation GIRA = RawAnimation.begin().thenLoop("animation.ventilador_techo.gira");
    static final RawAnimation LENTO = RawAnimation.begin().thenLoop("animation.ventilador_techo.lento");
    static final RawAnimation MECE = RawAnimation.begin().thenLoop("animation.flotador.mece");

    public String tipo() {
        Block b = getBlockState().getBlock();
        if (b instanceof Motor) return "escalera_mecanica";
        if (b instanceof Maniqui) return "maniqui";
        if (b instanceof Ventilador) return "ventilador_techo";
        return "flotador";
    }

    private RawAnimation animacion() {
        BlockState s = getBlockState();
        Block b = s.getBlock();
        if (b instanceof Motor) return s.getValue(Bloques.RELLANO) ? SUBE_RELLANO : SUBE_TECHO;
        if (b instanceof Maniqui) return POSES[s.getValue(Bloques.POSE)];
        if (b instanceof Ventilador) return s.getValue(Bloques.LENTO) ? LENTO : GIRA;
        return MECE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar r) {
        r.add(new AnimationController<>(this, "anim", 0, st -> st.setAndContinue(animacion())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        if (getBlockState().getBlock() instanceof Motor) return new AABB(p).inflate(7, 0, 7).expandTowards(0, 8, 0);
        if (getBlockState().getBlock() instanceof Ventilador) return new AABB(p).inflate(1, 0, 1);
        return new AABB(p).expandTowards(0, 1, 0);
    }

    // ------------------------------------------------------------------ los bloques
    abstract static class ConBE extends BaseEntityBlock {
        ConBE(Properties p) {
            super(p);
        }

        @Override
        public RenderShape getRenderShape(BlockState s) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
            return new AtrezoBE(p, s);
        }
    }

    /**
     * El pie de la escalera mecánica: lleva el modelo entero (con los peldaños que suben sin parar) y se pisa como un
     * escalón. El resto de peldaños son {@link Bloques.Escalon} invisibles. rellano = acaba en una entreplanta (el último
     * peldaño se mete en el suelo); si no, sube hasta el techo y no lleva a nada.
     */
    public static class Motor extends ConBE {
        private static final VoxelShape[] FORMA = new VoxelShape[4];

        static {
            VoxelShape abajo = Block.box(0, 0, 0, 16, 8, 16);
            Direction[] ds = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            for (int i = 0; i < 4; i++) FORMA[i] = Shapes.or(abajo, Formas.girar(0, 8, 0, 16, 16, 8, ds[i]));
        }

        public Motor(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(Bloques.RELLANO, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(HorizontalDirectionalBlock.FACING, Bloques.RELLANO);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA[Formas.indice(s.getValue(HorizontalDirectionalBlock.FACING))];
        }
    }

    /** El maniquí del escaparate. Cuando no lo miras, se gira hacia ti (como los Boo de Mario, pero al revés). */
    public static class Maniqui extends ConBE {
        private static final VoxelShape FORMA = Block.box(4, 0, 4, 12, 16, 12);

        public Maniqui(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(Bloques.POSE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(HorizontalDirectionalBlock.FACING, Bloques.POSE);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA;
        }
    }

    /** El ventilador del techo (colgado del bloque de encima), que da vueltas despacio (alguno, bamboleándose). */
    public static class Ventilador extends ConBE {
        private static final VoxelShape FORMA = Block.box(3, 4, 3, 13, 16, 13);

        public Ventilador(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(Bloques.LENTO, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(Bloques.LENTO);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }
    }

    /** El flotador que se mece en el agua de la piscina (el bloque está anegado: el agua sigue ahí). */
    public static class Flotador extends ConBE implements SimpleWaterloggedBlock {
        private static final VoxelShape FORMA = Block.box(2, 11, 2, 14, 15, 14);

        public Flotador(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(BlockStateProperties.WATERLOGGED);
        }

        @Override
        public FluidState getFluidState(BlockState s) {
            return s.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(s);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return FORMA;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }

        @Override
        public void animateTick(BlockState s, net.minecraft.world.level.Level l, BlockPos p, net.minecraft.util.RandomSource r) {
            if (r.nextInt(70) == 0)
                l.playLocalSound(p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, net.minecraft.sounds.SoundEvents.WATER_AMBIENT, net.minecraft.sounds.SoundSource.AMBIENT, 0.25F, 0.6F, false);
        }
    }
}
