package com.miscelanea.laboratorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.multibloque.ParteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Agachado + clic derecho en una estación del laboratorio llevando algo en la mano (el cuaderno, un tubo de ensayo...):
 * Minecraft usaría el objeto y no llegaría al bloque, así que ni se cambiaba el nivel ni se sacaba al sujeto. Aquí se le dice
 * que use el bloque. Va en los dos lados: el cliente decide si manda el clic y el servidor lo vuelve a decidir.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class ConsolaEventos {
    private ConsolaEventos() {}

    @SubscribeEvent
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getEntity().isSecondaryUseActive() || e.getEntity().getMainHandItem().isEmpty()) return;
        if (esEstacion(e.getLevel(), e.getPos())) e.setUseBlock(Event.Result.ALLOW);
    }

    /** ¿Es la cabina o la consola de una estación, o el hueco de arriba de una cápsula? */
    private static boolean esEstacion(Level l, BlockPos pos) {
        Block b = l.getBlockState(pos).getBlock();
        if (b instanceof CapsulaBlock || b instanceof CentrifugaBlock) return true;
        return l.getBlockEntity(pos) instanceof ParteBlockEntity pb && pb.maestro() != null && l.getBlockState(pb.maestro()).getBlock() instanceof CapsulaBlock;
    }
}
