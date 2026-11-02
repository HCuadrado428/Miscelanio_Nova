package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.TorturaRegistro;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Argolla de techo de la jaula colgante (The Elder Scrolls Online / RimWorld). Va bajo un techo; al ponerla cuelga
 * de ella la jaula (una entidad que se balancea, {@link JaulaEntity}) con su cadena. Clic derecho: baja la jaula un
 * bloque; Shift + clic derecho: la sube. Sin jaula (se cayó), clic derecho cuelga otra (en creativo). Si se rompe la
 * argolla, la jaula cae.
 */
public class JaulaAnclaBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape FORMA = Block.box(3, 9, 3, 13, 16, 13);

    public JaulaAnclaBlock(Properties p) {
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
        BlockState s = defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        return s.canSurvive(c.getLevel(), c.getClickedPos()) ? s : null;
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos pos) {
        return Block.canSupportCenter(l, pos.above(), Direction.DOWN);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState otro, LevelAccessor l, BlockPos pos, BlockPos otroPos) {
        if (d == Direction.UP && !canSurvive(s, l, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(s, d, otro, l, pos, otroPos);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState s, @Nullable LivingEntity quien, ItemStack st) {
        super.setPlacedBy(l, pos, s, quien, st);
        if (!l.isClientSide && JaulaEntity.de(l, pos) == null) JaulaEntity.colgar(l, pos, s.getValue(FACING));
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        JaulaEntity j = JaulaEntity.de(l, pos);
        if (j == null) {
            ItemStack st = p.getItemInHand(hand);
            if (p.getAbilities().instabuild || st.is(TorturaRegistro.JAULA_COLGANTE_ITEM.get())) {
                if (!p.getAbilities().instabuild) st.shrink(1);
                JaulaEntity.colgar(l, pos, s.getValue(FACING));
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.nueva").withStyle(ChatFormatting.GOLD), true);
            } else {
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.sin").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.CONSUME;
        }
        j.mover(p, p.isShiftKeyDown() ? -1.0 : 1.0);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState nuevo, boolean mov) {
        if (!s.is(nuevo.getBlock()) && !l.isClientSide) {
            JaulaEntity j = JaulaEntity.de(l, pos);
            if (j != null) j.romperCadena();
        }
        super.onRemove(s, l, pos, nuevo, mov);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable BlockGetter l, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable("block.miscelanea.jaula_colgante.ayuda").withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("block.miscelanea.jaula_colgante.ayuda2").withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("block.miscelanea.jaula_colgante.ayuda3").withStyle(ChatFormatting.GRAY));
        t.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
