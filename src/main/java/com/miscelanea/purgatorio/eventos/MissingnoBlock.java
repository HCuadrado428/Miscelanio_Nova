package com.miscelanea.purgatorio.eventos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * El bloque MissingNo. (sí, el de Pokémon: el que te dejaba el sexto objeto de la mochila a rebosar). Puesto en el
 * suelo, si le das con la mano, se "corrompe" y desaparece... y el objeto del sexto hueco de tu barra se llena hasta
 * arriba. Si en el sexto hueco no hay nada, o no se puede amontonar, solo hace ruido raro (y se va igual). Picado con la
 * mano normal se recoge como un bloque cualquiera.
 */
public class MissingnoBlock extends Block {
    public static final int HUECO = 5;

    public MissingnoBlock(Properties p) {
        super(p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand mano, BlockHitResult hit) {
        if (mano != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (p instanceof ServerPlayer sp) corromper(sp, (ServerLevel) l, pos);
        return InteractionResult.CONSUME;
    }

    /** Lo del bloque (también para la prueba): devuelve cuántos objetos ha sumado al sexto hueco. */
    public static int corromper(ServerPlayer sp, ServerLevel l, BlockPos pos) {
        ItemStack sexto = sp.getInventory().getItem(HUECO);
        int antes = sexto.getCount();
        int suma = 0;
        if (!sexto.isEmpty() && sexto.isStackable() && !(sexto.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof MissingnoBlock)) {
            sexto.setCount(sexto.getMaxStackSize());
            suma = sexto.getCount() - antes;
        }
        l.removeBlock(pos, false);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
        l.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.01);
        // un "grito" de Pokémon roto: dos sonidos del juego, uno muy grave y otro al revés de agudo
        l.playSound(null, pos, SoundEvents.ALLAY_HURT, SoundSource.BLOCKS, 0.7F, 0.5F);
        l.playSound(null, pos, SoundEvents.PARROT_IMITATE_ENDERMAN, SoundSource.BLOCKS, 0.6F, 1.9F);
        if (suma > 0) {
            sp.displayClientMessage(Component.translatable("block.miscelanea.missingno.suma", sexto.getHoverName(), suma).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            Logros.dar(sp, "missingno", "sexto");
        } else sp.displayClientMessage(Component.translatable("block.miscelanea.missingno.nada"), true);
        return suma;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable BlockGetter l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("block.miscelanea.missingno.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
