package com.miscelanea.bichos;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Lo que has atrapado y donado al museo (y tus récords de tamaño de peces). Se guarda en el jugador y no se pierde al morir. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Coleccion {
    private static final String CLAVE = "MiscelaneaBichopedia";

    private Coleccion() {}

    private static CompoundTag datos(ServerPlayer p) {
        CompoundTag d = p.getPersistentData().getCompound(CLAVE);
        p.getPersistentData().put(CLAVE, d);
        return d;
    }

    /** Has atrapado/pescado un ejemplar. Devuelve true si es nuevo en la Bichopedia. */
    public static boolean atrapar(ServerPlayer p, Especie e, int cm) {
        CompoundTag d = datos(p);
        CompoundTag at = d.getCompound("atrapados");
        boolean nuevo = !at.contains(e.id());
        int antes = at.getInt(e.id());
        if (nuevo || cm > antes) at.putInt(e.id(), Math.max(1, cm));
        d.put("atrapados", at);
        Component nombre = Component.translatable("item.miscelanea." + e.item());
        if (e.esPez())
            p.displayClientMessage(Component.translatable("msg.miscelanea.pescado", nombre, cm).withStyle(ChatFormatting.AQUA), false);
        else
            p.displayClientMessage(Component.translatable("msg.miscelanea.atrapado", nombre).withStyle(ChatFormatting.GREEN), false);
        if (nuevo) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.nueva_entrada").withStyle(ChatFormatting.GOLD), false);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        } else if (e.esPez() && cm > antes) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.record", cm).withStyle(ChatFormatting.GOLD), false);
        }
        sync(p);
        return nuevo;
    }

    /** Donación al museo. Devuelve true si es la primera vez que donas esa especie. */
    public static boolean donar(ServerPlayer p, Especie e) {
        CompoundTag d = datos(p);
        CompoundTag don = d.getCompound("donados");
        boolean nuevo = !don.contains(e.id());
        don.putBoolean(e.id(), true);
        d.put("donados", don);
        CompoundTag at = d.getCompound("atrapados");
        if (!at.contains(e.id())) at.putInt(e.id(), 1);
        d.put("atrapados", at);
        sync(p);
        return nuevo;
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ColeccionPacket(datos(p).copy()));
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        CompoundTag v = e.getOriginal().getPersistentData();
        if (v.contains(CLAVE)) e.getEntity().getPersistentData().put(CLAVE, v.getCompound(CLAVE).copy());
    }
}
