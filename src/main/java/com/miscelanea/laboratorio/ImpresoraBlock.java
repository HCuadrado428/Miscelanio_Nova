package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** La impresora de informes: gasta un papel y saca en un informe la última ficha de tu cuaderno. */
public class ImpresoraBlock extends Block {
    private static final VoxelShape FORMA = Block.box(1, 0, 1, 15, 10, 15);

    public ImpresoraBlock(Properties p) {
        super(p);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult r) {
        if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        imprimir(pl);
        return InteractionResult.CONSUME;
    }

    /** Imprime la última ficha del cuaderno que lleve encima. Devuelve si lo ha hecho. */
    public static boolean imprimir(Player p) {
        ListTag fichas = Experimentos.fichas(Experimentos.cuaderno(p));
        if (fichas.isEmpty()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.impresora.sin_fichas").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack papel = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.PAPER)) { papel = s; break; }
        if (papel.isEmpty() && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.impresora.sin_papel").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!papel.isEmpty() && !p.getAbilities().instabuild) papel.shrink(1);
        ItemStack informe = InformeItem.con(fichas.getCompound(fichas.size() - 1));
        if (!p.getInventory().add(informe)) p.drop(informe, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_LOOM_SELECT_PATTERN, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }
}
