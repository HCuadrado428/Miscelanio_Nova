package com.miscelanea.espera.eventos;

import com.miscelanea.Miscelanea;
import com.miscelanea.espera.Espera;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Canal propio de lo del Espera (así no se toca el del mod): los nombres para los fantasmas y "he visto uno". */
public final class RedExtras {
    private RedExtras() {}

    private static final String PROTOCOLO = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Miscelanea.MOD_ID, "espera"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    static void registrar() {
        int id = 0;
        CANAL.messageBuilder(Nombres.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Nombres::encode).decoder(Nombres::new).consumerMainThread(Nombres::handle).add();
        CANAL.messageBuilder(Visto.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Visto::encode).decoder(Visto::new).consumerMainThread(Visto::handle).add();
    }

    static void enviar(ServerPlayer p, Object m) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    /** Servidor → cliente: gente del servidor que no está ahora en el Espera (para los jugadores fantasma). */
    public static final class Nombres {
        public final List<UUID> ids = new ArrayList<>();
        public final List<String> nombres = new ArrayList<>();

        public Nombres() {}

        public Nombres(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 64);
            for (int i = 0; i < n; i++) {
                ids.add(b.readUUID());
                nombres.add(b.readUtf(16));
            }
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                b.writeUUID(ids.get(i));
                b.writeUtf(nombres.get(i), 16);
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.espera.ExtrasCliente.nombres(ids, nombres));
        }
    }

    /** Cliente → servidor: he visto un jugador fantasma (para el logro). */
    public static final class Visto {
        public Visto() {}

        public Visto(FriendlyByteBuf b) {}

        public void encode(FriendlyByteBuf b) {}

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && Espera.en(p.level())) Logros.dar(p, "fantasma", "visto");
        }
    }
}
