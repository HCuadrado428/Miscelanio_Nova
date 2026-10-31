package com.miscelanea.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pesca con la caña de la Bichopedia: el pez se elige por el bioma del anzuelo, la hora y el tiempo; luego el minijuego. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Pesca {
    private record Pendiente(Especie especie, int cm, long hasta) {}

    private static final Map<UUID, Pendiente> PENDIENTES = new HashMap<>();

    private Pesca() {}

    @SubscribeEvent
    public static void pescado(ItemFishedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        boolean nuestra = p.getMainHandItem().getItem() instanceof CanaItem || p.getOffhandItem().getItem() instanceof CanaItem;
        if (!nuestra) return;
        e.setCanceled(true);
        e.damageRodBy(1);
        BlockPos pos = e.getHookEntity().blockPosition();
        List<Especie> posibles = new ArrayList<>();
        int total = 0;
        for (Especie s : Especie.values()) {
            if (s.esPez() && s.puede(p.level(), pos)) {
                posibles.add(s);
                total += s.peso;
            }
        }
        Especie elegida = Especie.CARPA;
        if (total > 0) {
            int r = p.getRandom().nextInt(total);
            for (Especie s : posibles) {
                r -= s.peso;
                if (r < 0) {
                    elegida = s;
                    break;
                }
            }
        }
        // tamaño: los grandes son raros
        float u = p.getRandom().nextFloat();
        int cm = elegida.cmMin + Math.round((elegida.cmMax - elegida.cmMin) * u * u);
        PENDIENTES.put(p.getUUID(), new Pendiente(elegida, cm, p.level().getGameTime() + 20 * 40));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PescaPacket(elegida.ordinal(), elegida.dificultad));
    }

    /** Resultado del minijuego. */
    public static void resultado(ServerPlayer p, boolean ganado) {
        Pendiente pe = PENDIENTES.remove(p.getUUID());
        if (pe == null || p.level().getGameTime() > pe.hasta()) return;
        if (!ganado) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.miscelanea.se_escapo"), true);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6F, 1.4F);
            return;
        }
        ItemStack pez = new ItemStack(ModItems.bicho(pe.especie()));
        pez.getOrCreateTag().putInt("cm", pe.cm());
        if (!p.getInventory().add(pez)) p.drop(pez, false);
        p.level().addFreshEntity(new ExperienceOrb(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), 2 + pe.especie().estrellas * 2));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 1.0F, 1.0F);
        Coleccion.atrapar(p, pe.especie(), pe.cm());
    }
}
