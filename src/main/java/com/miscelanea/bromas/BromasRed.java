package com.miscelanea.bromas;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Canal de red propio de las bromas y de la rueda de Catalina (miscelanea:bromas), para no mover la numeración de los
 * demás canales.
 * <ul>
 *   <li>{@link Cartel} (servidor → clientes): quién lleva el cartel de "PATÉAME" en la espalda.</li>
 *   <li>{@link QuitarCartel} (cliente → servidor): la víctima se quita el cartel (agachada + clic derecho con la mano vacía).</li>
 *   <li>{@link RuedaS2C} (servidor → víctima): empieza una ronda del minijuego del mareo, o fundido a negro.</li>
 *   <li>{@link RuedaC2S} (cliente → servidor): impulso con la rueda del ratón (captor) o resultado de una ronda (víctima).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BromasRed {
    private static final String PROTOCOLO = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "bromas"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private BromasRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(Cartel.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(Cartel::encode).decoder(Cartel::new).consumerMainThread(Cartel::handle).add();
            CANAL.messageBuilder(QuitarCartel.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(QuitarCartel::encode).decoder(QuitarCartel::new).consumerMainThread(QuitarCartel::handle).add();
            CANAL.messageBuilder(RuedaS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(RuedaS2C::encode).decoder(RuedaS2C::new).consumerMainThread(RuedaS2C::handle).add();
            CANAL.messageBuilder(RuedaC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(RuedaC2S::encode).decoder(RuedaC2S::new).consumerMainThread(RuedaC2S::handle).add();
        });
    }

    public static void a(ServerPlayer p, Object msg) {
        if (p.connection != null) CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** A quien ve a la entidad y a ella misma. */
    public static void alrededor(Entity e, Object msg) {
        CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }

    // ------------------------------------------------------------------ paquetes
    /** Servidor → clientes: la entidad lleva (o ya no) el cartel de "PATÉAME". */
    public static class Cartel {
        public final int entidad;
        public final boolean puesto;

        public Cartel(int entidad, boolean puesto) {
            this.entidad = entidad;
            this.puesto = puesto;
        }

        public Cartel(FriendlyByteBuf b) {
            this.entidad = b.readVarInt();
            this.puesto = b.readBoolean();
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entidad);
            b.writeBoolean(puesto);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.bromas.BromasCliente.cartel(entidad, puesto));
        }
    }

    /** Cliente → servidor: quitarse el cartel de la espalda. */
    public static class QuitarCartel {
        public QuitarCartel() {}

        public QuitarCartel(FriendlyByteBuf b) {}

        public void encode(FriendlyByteBuf b) {}

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Bromas.quitarCartelSolo(p);
        }
    }

    /** Servidor → víctima de la rueda. JUEGO: ronda de mareo (valor = duración en ticks, dificultad 0..1). FUNDIDO: a negro. PARAR. */
    public static class RuedaS2C {
        public static final int JUEGO = 1, FUNDIDO = 2, PARAR = 3;
        public final int tipo;
        public final BlockPos pos;
        public final int valor;
        public final float dificultad;

        public RuedaS2C(int tipo, BlockPos pos, int valor, float dificultad) {
            this.tipo = tipo;
            this.pos = pos;
            this.valor = valor;
            this.dificultad = dificultad;
        }

        public RuedaS2C(FriendlyByteBuf b) {
            this.tipo = b.readByte();
            this.pos = b.readBlockPos();
            this.valor = b.readVarInt();
            this.dificultad = b.readFloat();
        }

        public void encode(FriendlyByteBuf b) {
            b.writeByte(tipo);
            b.writeBlockPos(pos);
            b.writeVarInt(valor);
            b.writeFloat(dificultad);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.bromas.RuedaCliente.paquete(this));
        }
    }

    /** Cliente → servidor. IMPULSO: rueda del ratón mirando la rueda (valor ±1). RESULTADO: % de la ronda en el blanco (0..100). */
    public static class RuedaC2S {
        public static final int IMPULSO = 1, RESULTADO = 2;
        public final int tipo;
        public final BlockPos pos;
        public final int valor;

        public RuedaC2S(int tipo, BlockPos pos, int valor) {
            this.tipo = tipo;
            this.pos = pos;
            this.valor = valor;
        }

        public RuedaC2S(FriendlyByteBuf b) {
            this.tipo = b.readByte();
            this.pos = b.readBlockPos();
            this.valor = b.readVarInt();
        }

        public void encode(FriendlyByteBuf b) {
            b.writeByte(tipo);
            b.writeBlockPos(pos);
            b.writeVarInt(valor);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !p.level().isLoaded(pos)) return;
            if (!(p.level().getBlockEntity(pos) instanceof com.miscelanea.tortura.aparatos.RuedaBlockEntity be)) return;
            if (tipo == IMPULSO) {
                if (!be.esVictima(p) && p.distanceToSqr(be.centro()) < 64) be.impulso(p, valor > 0 ? 1 : -1, false);
            } else if (tipo == RESULTADO) {
                if (be.esVictima(p)) be.resultado(p, Math.max(0, Math.min(100, valor)));
            }
        }
    }
}
