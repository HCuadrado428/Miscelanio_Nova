package com.miscelanea.laboratorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.multibloque.ParteBlockEntity;
import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Clics en las estaciones del laboratorio que Minecraft no llevaría a donde tienen que ir. Va en los dos lados: el cliente
 * decide qué hace con el clic y el servidor lo vuelve a decidir.
 * <ul>
 *   <li>Agachado + clic llevando algo en cualquiera de las dos manos: Minecraft usaría el objeto y no el bloque, así que ni se
 *       cambiaba el nivel ni se sacaba al sujeto. Se usa el bloque, pero solo cuando hace algo (en la consola siempre; en la
 *       cabina, si hay alguien dentro): si no, se puede seguir poniendo bloques al lado agachado.</li>
 *   <li>El pulsómetro, los sueros y el portapapeles sobre la cabina de una cápsula: la cabina envuelve al sujeto y el clic se lo
 *       lleva el bloque, así que nunca llegaban a él. Se usan con el sujeto de dentro.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class ConsolaEventos {
    private ConsolaEventos() {}

    /** Dónde se ha hecho clic: la estación (su bloque principal) y si ha sido en la consola. */
    private record Sitio(AparatoBlockEntity be, boolean consola) {}

    @SubscribeEvent
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Player p = e.getEntity();
        Sitio s = sitio(e.getLevel(), e.getPos());
        if (s == null || s.be.esVictima(p)) return;
        // objetos del laboratorio sobre el sujeto que está dentro de la cabina
        ItemStack mano = p.getMainHandItem();
        if (!s.consola && s.be.ocupado() && herramienta(mano.getItem())
                && e.getLevel().getEntity(s.be.victimaId()) instanceof LivingEntity v) {
            if (e.getLevel().isClientSide) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            InteractionResult r = mano.interactLivingEntity(p, v, InteractionHand.MAIN_HAND);
            if (r.consumesAction()) {
                e.setCanceled(true);
                e.setCancellationResult(r);
            }
            return;
        }
        // agachado con algo en alguna mano: que llegue al bloque, si en esa parte agachado + clic hace algo
        boolean algoEnLasManos = !mano.isEmpty() || !p.getOffhandItem().isEmpty();
        if (p.isSecondaryUseActive() && algoEnLasManos && (s.consola || s.be.ocupado())) e.setUseBlock(Event.Result.ALLOW);
    }

    private static boolean herramienta(Item i) {
        return i instanceof PulsometroItem || i instanceof SueroItem || i instanceof SueroRecuperacionItem || i instanceof PortapapelesItem;
    }

    /** La estación en la que se ha hecho clic: en su cabina, en su consola o en el hueco de arriba de una cápsula. */
    @Nullable
    private static Sitio sitio(Level l, BlockPos pos) {
        BlockState st = l.getBlockState(pos);
        BlockPos principal;
        boolean consola;
        if (st.getBlock() instanceof CapsulaBlock || st.getBlock() instanceof CentrifugaBlock) {
            consola = st.getValue(AparatoBlock.ANEXO);
            principal = AparatoBlock.principal(st, pos);
        } else if (l.getBlockEntity(pos) instanceof ParteBlockEntity pb && pb.maestro() != null && l.getBlockState(pb.maestro()).getBlock() instanceof CapsulaBlock) {
            consola = false;
            principal = pb.maestro();
        } else {
            return null;
        }
        return l.getBlockEntity(principal) instanceof AparatoBlockEntity be ? new Sitio(be, consola) : null;
    }
}
