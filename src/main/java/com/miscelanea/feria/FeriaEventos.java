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
        if (!e.getLevel().isClientSide) be.golpeBoxeo(e.getEntity());
    }
}
