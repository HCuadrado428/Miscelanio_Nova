package com.miscelanea.arcade;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Tabla de los 10 mejores de cada juego de recreativa, para todo el servidor (se guarda con el mundo). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public class ArcadeRecords extends SavedData {
    private static final String NOMBRE = "miscelanea_arcade";
    private CompoundTag tablas = new CompoundTag();

    public static ArcadeRecords de(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(ArcadeRecords::cargar, ArcadeRecords::new, NOMBRE);
    }

    private static ArcadeRecords cargar(CompoundTag t) {
        ArcadeRecords r = new ArcadeRecords();
        r.tablas = t.getCompound("tablas");
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("tablas", tablas);
        return t;
    }

    /** Devuelve la posición (1..10) en la que entra, o 0 si no entra. */
    public int agregar(String juego, String nombre, int puntos) {
        ListTag l = tablas.getList(juego, Tag.TAG_COMPOUND);
        int pos = l.size();
        for (int i = 0; i < l.size(); i++)
            if (puntos > l.getCompound(i).getInt("p")) {
                pos = i;
                break;
            }
        if (pos >= 10) return 0;
        CompoundTag e = new CompoundTag();
        e.putString("n", nombre);
        e.putInt("p", puntos);
        l.add(pos, e);
        while (l.size() > 10) l.remove(l.size() - 1);
        tablas.put(juego, l);
        setDirty();
        return pos + 1;
    }

    public CompoundTag copia() {
        return tablas.copy();
    }

    public static void registrar(ServerPlayer p, String juego, int puntos) {
        if (!ArcadeJuegos.valido(juego) || puntos <= 0 || puntos > 99_999_999) return;
        ArcadeRecords r = de(p.server);
        int pos = r.agregar(juego, p.getGameProfile().getName(), puntos);
        if (pos == 0) return;
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new ArcadeRecordsPacket(r.copia()));
        if (pos == 1)
            p.server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.miscelanea.arcade.record_mundial", p.getDisplayName(),
                    Component.translatable("juego.miscelanea." + juego), puntos).withStyle(ChatFormatting.GOLD), false);
        else
            p.displayClientMessage(Component.translatable("msg.miscelanea.arcade.top", pos).withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ArcadeRecordsPacket(de(p.server).copia()));
    }
}
