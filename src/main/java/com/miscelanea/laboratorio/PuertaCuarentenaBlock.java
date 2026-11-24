package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * La puerta de cuarentena: una puerta con escáner. Solo se abre a mano si no llevas ningún efecto de suero encima (o si
 * llevas puesta la bata de laboratorio). Si no, suena un aviso y se queda cerrada.
 */
public class PuertaCuarentenaBlock extends DoorBlock {
    public PuertaCuarentenaBlock(Properties p) {
        super(p, BlockSetType.IRON);
    }

    /** ¿Pasa el escáner? */
    public static boolean esLimpio(Player p) {
        if (p.isCreative()) return true;
        if (p.getItemBySlot(EquipmentSlot.CHEST).is(LabRegistro.BATA.get())) return true;
        for (int i = 0; i < Sueros.TIPOS; i++) if (p.hasEffect(Sueros.efecto(i))) return false;
        return true;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult r) {
        if (!esLimpio(pl)) {
            if (!l.isClientSide) {
                l.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.0F, 0.6F);
                pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.cuarentena.sucio").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        BlockState nuevo = s.cycle(OPEN);
        l.setBlock(p, nuevo, 10);
        l.playSound(pl, p, nuevo.getValue(OPEN) ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1.0F, l.getRandom().nextFloat() * 0.1F + 0.9F);
        l.gameEvent(pl, nuevo.getValue(OPEN) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, p);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
