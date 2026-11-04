package com.miscelanea.arcade;

import com.miscelanea.ranking.Ranking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Tablón de récords: tablón de corcho con letrero de neón que enseña los 10 mejores de una categoría del ranking del
 * servidor ({@link Ranking}). Varios juntos en la pared (2x2, 3x2...) forman uno grande. Clic derecho: siguiente categoría
 * (de las que tienen marcas); agachado + clic derecho: los resúmenes globales.
 */
public class TablonRecordsBlock extends CartelRecordsBlock {
    public TablonRecordsBlock(Properties p) {
        super(p, TABLON);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(p instanceof ServerPlayer sp)) return InteractionResult.PASS;
        Direction f = s.getValue(FACING);
        Object[] g = grupo(l, pos, f);
        BlockPos esquina = g == null ? pos : (BlockPos) g[0];
        if (!(l.getBlockEntity(esquina) instanceof CartelRecordsBlockEntity be)) return InteractionResult.CONSUME;
        Ranking r = Ranking.de(sp.server);
        List<String> lista = new ArrayList<>();
        if (sp.isShiftKeyDown()) lista.addAll(List.of(Ranking.GLOBALES));
        else for (String c : r.categorias(true)) if (!Ranking.esGlobal(c)) lista.add(c);
        if (lista.isEmpty()) {
            sp.displayClientMessage(Component.translatable("ranking.miscelanea.tablon.sin_marcas").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        int i = lista.indexOf(be.categoria);
        String nueva = lista.get((i + 1) % lista.size());
        be.ponerCategoria(nueva);
        l.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1F, 0.9F + l.random.nextFloat() * 0.2F);
        sp.displayClientMessage(Component.translatable("ranking.miscelanea.tablon.cambia", Ranking.nombre(nueva).withStyle(ChatFormatting.YELLOW),
                lista.indexOf(nueva) + 1, lista.size()).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.CONSUME;
    }
}
