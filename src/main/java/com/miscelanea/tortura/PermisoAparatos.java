package com.miscelanea.tortura;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.miscelanea.tortura.saw.Saw;
import com.miscelanea.tortura.saw.SawRegistro;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interruptor general de la config ("tortura.aparatos_en_supervivencia"). Si está apagado, quien no está en creativo
 * no puede poner ni usar los aparatos de tortura ni ponerle a nadie grilletes, trampas, collares... Quien ya está
 * atrapado (en un aparato, con grilletes, trampa o un juego de Jigsaw) no se ve afectado: puede seguir escapando.
 * El pozo de agujas, el alambre de espino y la puerta de salida (la parte "de escapar" de los juegos) se pueden
 * seguir usando. Se comprueba en los dos lados (la config del servidor llega al cliente), así no hay clics fantasma.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PermisoAparatos {
    private PermisoAparatos() {}

    private static Set<Item> objetos;
    private static Set<Block> bloques;

    /** Los objetos que ponen un aparato o atrapan a alguien. */
    private static List<RegistryObject<Item>> lista() {
        return List.of(TorturaRegistro.TRAMPA_OSO, TorturaRegistro.CAJA_JIGSAW_ITEM, TorturaRegistro.GRILLETES, TorturaRegistro.CADENA,
                TorturaRegistro.LAZO, TorturaRegistro.JAULA_COLGANTE_ITEM, TorturaRegistro.CEPO_ITEM, TorturaRegistro.BOLA_PRESO,
                AparatosRegistro.DONCELLA_ITEM, AparatosRegistro.POTRO_ITEM, AparatosRegistro.SILLA_ITEM, AparatosRegistro.CORREAS,
                AparatosRegistro.HIERRO_MARCAR, AparatosRegistro.PENDULO_ITEM, AparatosRegistro.INMERSION_ITEM,
                AparatosRegistro.INTERROGATORIO_ITEM, AparatosRegistro.MALETIN, AparatosRegistro.GANCHO_ITEM,
                EfectosRegistro.COLLAR, EfectosRegistro.DETONADOR,
                SawRegistro.CAJA_AGUA, SawRegistro.TANQUE_ITEM, SawRegistro.POZO_ITEM, SawRegistro.ALAMBRE_ITEM, SawRegistro.SALIDA_ITEM);
    }

    private static void preparar() {
        if (objetos != null) return;
        Set<Item> o = new HashSet<>();
        Set<Block> b = new HashSet<>();
        for (RegistryObject<Item> r : lista()) {
            if (!r.isPresent()) continue;
            Item it = r.get();
            o.add(it);
            if (it instanceof BlockItem bi) b.add(bi.getBlock());
        }
        // los bloques "de escapar" se siguen pudiendo usar (no se pueden poner, eso sí)
        b.remove(SawRegistro.POZO.get());
        b.remove(SawRegistro.ALAMBRE.get());
        b.remove(SawRegistro.SALIDA.get());
        objetos = o;
        bloques = b;
    }

    /** ¿Se le niega a este jugador usar los aparatos? */
    private static boolean negado(Player p) {
        if (ConfigServidor.aparatosEnSupervivencia() || p.isCreative() || p.isSpectator()) return false;
        // quien ya está atrapado puede seguir intentando escapar
        return !(Cautivos.tieneAlgo(p) || Saw.tieneAlgo(p) || AparatosVictimas.en(p.getId()));
    }

    private static boolean objeto(ItemStack s) {
        preparar();
        return !s.isEmpty() && objetos.contains(s.getItem());
    }

    private static void aviso(Player p) {
        if (!p.level().isClientSide)
            p.displayClientMessage(Component.translatable("msg.miscelanea.tortura.desactivada").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clicBloque(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (!negado(p)) return;
        preparar();
        boolean aparato = bloques.contains(e.getLevel().getBlockState(e.getPos()).getBlock());
        if (aparato || objeto(e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clicObjeto(PlayerInteractEvent.RightClickItem e) {
        if (negado(e.getEntity()) && objeto(e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clicEntidad(PlayerInteractEvent.EntityInteract e) {
        if (negado(e.getEntity()) && objeto(e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(e.getEntity());
        }
    }
}
