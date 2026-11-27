package com.miscelanea.camaras;

import com.miscelanea.Miscelanea;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Agachado + clic en una tele, un monitor o una cámara llevando algo en alguna mano: Minecraft usaría el objeto y no el
 * bloque, así que no cambiaba de canal (bastaba una antorcha en la mano izquierda). Se usa el bloque, salvo que en la mano
 * derecha lleves un bloque (para poder seguir poniendo bloques al lado agachado). Va en los dos lados.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class CamarasEventos {
    private CamarasEventos() {}

    @SubscribeEvent
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Player p = e.getEntity();
        if (!p.isSecondaryUseActive() || (p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty())) return;
        if (p.getMainHandItem().getItem() instanceof BlockItem) return;
        Block b = e.getLevel().getBlockState(e.getPos()).getBlock();
        if (b instanceof MontadoBlock || b instanceof MonitorBlock) e.setUseBlock(Event.Result.ALLOW);
    }
}
