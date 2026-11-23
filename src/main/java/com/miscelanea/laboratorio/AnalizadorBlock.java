package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashMap;
import java.util.Map;

/** El analizador: clic derecho con un suero sin etiquetar para identificarlo (y apuntarlo en el cuaderno); con una muestra, dice qué es. */
public class AnalizadorBlock extends Block {
    private static final VoxelShape FORMA = Block.box(1, 0, 1, 15, 15, 15);

    public AnalizadorBlock(Properties p) {
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
        analizar(pl, pl.getMainHandItem());
        return InteractionResult.CONSUME;
    }

    /** Identifica el suero que lleva en la mano (y lo apunta en el cuaderno). Devuelve si ha hecho algo. */
    public static boolean analizar(Player p, ItemStack s) {
        if (s.is(LabRegistro.SUERO.get())) {
            int tipo = Sueros.tipo(s);
            boolean nuevo = !Sueros.conocido(s);
            s.getOrCreateTag().putBoolean("Conocido", true);
            Component nombre = Component.translatable("laboratorio.miscelanea.suero." + tipo);
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.analizador.suero", nombre).withStyle(ChatFormatting.GREEN), true);
            if (nuevo) {
                Map<String, String> lecturas = new LinkedHashMap<>();
                lecturas.put("suero", nombre.getString());
                Experimentos.anotar(p, "analisis", null, 0, lecturas);
            }
            return true;
        }
        if (Sueros.valor(s) > 0) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.analizador.muestra", s.getHoverName()).withStyle(ChatFormatting.AQUA), true);
            return true;
        }
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.analizador.nada").withStyle(ChatFormatting.GRAY), true);
        return false;
    }
}
