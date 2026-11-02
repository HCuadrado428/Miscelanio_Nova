package com.miscelanea.feria;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Puñetazo al saco de boxeo: el clic izquierdo en la máquina en marcha golpea en vez de romperla. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class FeriaEventos {
    private FeriaEventos() {}

    @SubscribeEvent
    public static void golpe(PlayerInteractEvent.LeftClickBlock e) {
        BlockState s = e.getLevel().getBlockState(e.getPos());
        if (!(s.getBlock() instanceof MaquinaAltaBlock b) || b.tipo != MaquinaAltaBlock.BOXEO) return;
        BlockPos abajo = s.getValue(MaquinaAltaBlock.HALF) == DoubleBlockHalf.LOWER ? e.getPos() : e.getPos().below();
        if (!(e.getLevel().getBlockEntity(abajo) instanceof MaquinaAltaBlockEntity be) || !be.activo || be.fase != 1) return;
        e.setCanceled(true);
        if (e.getLevel().isClientSide) {
            // el cliente manda el instante exacto en que pegó (el servidor lo acepta si está a ±3 tics de su reloj)
            if (e.getEntity().getUUID().equals(be.jugador))
                com.miscelanea.network.ModNetwork.CHANNEL.sendToServer(new GanchoPacket(GanchoPacket.GOLPE, abajo, golpeT(e.getLevel().getGameTime(), be), 0));
        } else be.golpeBoxeo(e.getEntity(), -1);
    }

    /** Tics desde que empezó el juego de la máquina (cabe en un short). */
    static int golpeT(long ahora, MaquinaAltaBlockEntity be) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, ahora - be.inicio));
    }
}
