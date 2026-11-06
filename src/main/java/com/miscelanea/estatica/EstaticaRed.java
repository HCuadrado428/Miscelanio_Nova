package com.miscelanea.estatica;

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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Canal de red propio de la estática ("miscelanea:estatica") con todos sus paquetes. Los de servidor → cliente llaman a
 * {@code com.miscelanea.client.estatica.EstaticaRedCliente} (solo en el cliente, por DistExecutor).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EstaticaRed {
    private static final String PROTOCOLO = "2";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "estatica"), () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    /** Máximo de bytes de una grabación de voz (8 kHz, 8 bits: 3,5 s), por debajo del límite de los paquetes del cliente. */
    public static final int MAX_GRABACION = 28000;

    private EstaticaRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(Estado.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Estado::encode).decoder(Estado::new).consumerMainThread(Estado::handle).add();
            CANAL.messageBuilder(Cine.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Cine::encode).decoder(Cine::new).consumerMainThread(Cine::handle).add();
            CANAL.messageBuilder(Fx.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Fx::encode).decoder(Fx::new).consumerMainThread(Fx::handle).add();
            CANAL.messageBuilder(Nieve.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Nieve::encode).decoder(Nieve::new).consumerMainThread(Nieve::handle).add();
            CANAL.messageBuilder(Pantallas.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Pantallas::encode).decoder(Pantallas::new).consumerMainThread(Pantallas::handle).add();
            CANAL.messageBuilder(Charco.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Charco::encode).decoder(Charco::new).consumerMainThread(Charco::handle).add();
            CANAL.messageBuilder(Imitar.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Imitar::encode).decoder(Imitar::new).consumerMainThread(Imitar::handle).add();
            CANAL.messageBuilder(Tentaculo.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Tentaculo::encode).decoder(Tentaculo::new).consumerMainThread(Tentaculo::handle).add();
            CANAL.messageBuilder(Usar.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(Usar::encode).decoder(Usar::new).consumerMainThread(Usar::handle).add();
            CANAL.messageBuilder(SalirPantalla.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(SalirPantalla::encode).decoder(SalirPantalla::new).consumerMainThread(SalirPantalla::handle).add();
            CANAL.messageBuilder(Grabacion.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(Grabacion::encode).decoder(Grabacion::new).consumerMainThread(Grabacion::handle).add();
            CANAL.messageBuilder(VerPantalla.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(VerPantalla::encode).decoder(VerPantalla::new).consumerMainThread(VerPantalla::handle).add();
            CANAL.messageBuilder(CaraPantalla.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(CaraPantalla::encode).decoder(CaraPantalla::new).consumerMainThread(CaraPantalla::handle).add();
            CANAL.messageBuilder(SenalMsg.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(SenalMsg::encode).decoder(SenalMsg::new).consumerMainThread(SenalMsg::handle).add();
            CANAL.messageBuilder(Pantallazo.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Pantallazo::encode).decoder(Pantallazo::new).consumerMainThread(Pantallazo::handle).add();
            CANAL.messageBuilder(Clon.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Clon::encode).decoder(Clon::new).consumerMainThread(Clon::handle).add();
            CANAL.messageBuilder(AccionTent.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(AccionTent::encode).decoder(AccionTent::new).consumerMainThread(AccionTent::handle).add();
        });
    }

    public static void a(ServerPlayer p, Object msg) {
        if (p.connection != null) CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** A quien ve a la entidad y a ella misma. */
    public static void alrededor(Entity e, Object msg) {
        CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }

    /** A los jugadores a menos de r bloques de un punto. */
    public static void cerca(net.minecraft.server.level.ServerLevel l, double x, double y, double z, double r, Object msg) {
        CANAL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(x, y, z, r, l.dimension())), msg);
    }

    // ================================================================== servidor → cliente
    /** Quién es de estática (vanilla no manda los efectos de los demás): semilla de la disposición y banderas. */
    public static class Estado {
        public static final int ACTIVO = 1, DENTRO = 2, FUNDIDO = 4;
        public final int id, semilla, banderas;

        public Estado(int id, int semilla, int banderas) {
            this.id = id;
            this.semilla = semilla;
            this.banderas = banderas;
        }

        public Estado(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readInt(), b.readByte());
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeInt(semilla);
            b.writeByte(banderas);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.estado(this));
        }
    }

    /** Empieza la transformación de alguien (pies y frente al empezar, para colocar la cámara). */
    public static class Cine {
        public final int id, semilla, transcurrido;
        public final double x, y, z;
        public final float yaw;

        public Cine(int id, int semilla, int transcurrido, double x, double y, double z, float yaw) {
            this.id = id;
            this.semilla = semilla;
            this.transcurrido = transcurrido;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        public Cine(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeInt(semilla);
            b.writeVarInt(transcurrido);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeFloat(yaw);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.cine(this));
        }
    }

    /** Efectos visuales sueltos (estelas, estallidos, chispazos, golpes, apagones, ocultar a alguien un momento). */
    public static class Fx {
        public static final int ESTELA = 0, ESTALLIDO = 1, CHISPAZO = 2, GOLPE_SUELO = 3, APAGON = 4, OCULTAR = 5, FRAGMENTO = 6,
                ARCO = 7, ATURDIDO = 8, NOVA = 9,
                /** silueta que se queda y explota (extra = yaw) */ ECO = 10, /** zona de Sin señal (extra = radio x10, x2 = ticks) */ ZONA = 11,
                /** líneas de pausa sobre alguien (extra = ticks) */ PAUSA = 12, /** se rompe la pausa */ PLAY = 13;
        public final int tipo, id, extra;
        public final double x, y, z, x2, y2, z2;

        public Fx(int tipo, int id, double x, double y, double z, double x2, double y2, double z2, int extra) {
            this.tipo = tipo;
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.extra = extra;
        }

        public static Fx en(int tipo, int id, net.minecraft.world.phys.Vec3 p, int extra) {
            return new Fx(tipo, id, p.x, p.y, p.z, p.x, p.y, p.z, extra);
        }

        public Fx(FriendlyByteBuf b) {
            this(b.readByte(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt());
        }

        void encode(FriendlyByteBuf b) {
            b.writeByte(tipo);
            b.writeVarInt(id);
            b.writeFloat((float) x);
            b.writeFloat((float) y);
            b.writeFloat((float) z);
            b.writeFloat((float) x2);
            b.writeFloat((float) y2);
            b.writeFloat((float) z2);
            b.writeVarInt(extra);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.fx(this));
        }
    }

    /** Nieve en la pantalla de quien lo recibe (toque o charco) y, si 'corromper' > 0, sus objetos se ven corruptos. */
    public static class Nieve {
        public final int ticks, corromper;
        public final boolean borde;

        public Nieve(int ticks, boolean borde, int corromper) {
            this.ticks = ticks;
            this.borde = borde;
            this.corromper = corromper;
        }

        public Nieve(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readBoolean(), b.readVarInt());
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(ticks);
            b.writeBoolean(borde);
            b.writeVarInt(corromper);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.nieve(this));
        }
    }

    /** Dentro de las pantallas: por cuál ha entrado y las de salida (con hacia dónde miran). Lista vacía = ha salido. */
    public static class Pantallas {
        public final BlockPos entrada;
        public final List<BlockPos> destinos;
        public final byte[] caras;
        public final int maxTicks;

        public Pantallas(BlockPos entrada, List<BlockPos> destinos, byte[] caras, int maxTicks) {
            this.entrada = entrada;
            this.destinos = destinos;
            this.caras = caras;
            this.maxTicks = maxTicks;
        }

        public Pantallas(FriendlyByteBuf b) {
            entrada = b.readBlockPos();
            int n = Math.min(64, b.readVarInt());
            destinos = new ArrayList<>(n);
            caras = new byte[n];
            for (int i = 0; i < n; i++) {
                destinos.add(b.readBlockPos());
                caras[i] = b.readByte();
            }
            maxTicks = b.readVarInt();
        }

        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(entrada);
            b.writeVarInt(destinos.size());
            for (int i = 0; i < destinos.size(); i++) {
                b.writeBlockPos(destinos.get(i));
                b.writeByte(caras[i]);
            }
            b.writeVarInt(maxTicks);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.pantallas(this));
        }
    }

    /** Un charco de estática nuevo (lo dibuja el cliente; no es un bloque). */
    public static class Charco {
        public final double x, y, z;
        public final int vida, semilla;

        public Charco(double x, double y, double z, int vida, int semilla) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vida = vida;
            this.semilla = semilla;
        }

        public Charco(FriendlyByteBuf b) {
            this(b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readInt());
        }

        void encode(FriendlyByteBuf b) {
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeVarInt(vida);
            b.writeInt(semilla);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.charco(this));
        }
    }

    /** Una voz imitada que suena desde un punto (8 kHz, mu-law de 8 bits). */
    public static class Imitar {
        public final double x, y, z;
        public final byte[] datos;

        public Imitar(double x, double y, double z, byte[] datos) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.datos = datos;
        }

        public Imitar(FriendlyByteBuf b) {
            this(b.readDouble(), b.readDouble(), b.readDouble(), b.readByteArray(MAX_GRABACION));
        }

        void encode(FriendlyByteBuf b) {
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeByteArray(datos);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.imitar(this));
        }
    }

    /** Tentáculos de estática de 'atacante' a 'objetivo' (tipo = Poder.ordinal()). */
    public static class Tentaculo {
        /** inicio: ticks que ya han pasado (al cambiar de agarrar a estampar/lanzar/atraer, los tentáculos ya están puestos). */
        public final int atacante, objetivo, tipo, duracion, inicio;

        public Tentaculo(int atacante, int objetivo, int tipo, int duracion, int inicio) {
            this.atacante = atacante;
            this.objetivo = objetivo;
            this.tipo = tipo;
            this.duracion = duracion;
            this.inicio = inicio;
        }

        public Tentaculo(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), b.readByte(), b.readVarInt(), b.readVarInt());
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(atacante);
            b.writeVarInt(objetivo);
            b.writeByte(tipo);
            b.writeVarInt(duracion);
            b.writeVarInt(inicio);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.tentaculo(this));
        }
    }

    // ================================================================== cliente → servidor
    /** Usar un poder de la rueda; 'pos' (pantallas) y 'objetivo' (tentáculos, arrastrar) son pistas que el servidor comprueba. */
    public static class Usar {
        public final int poder, objetivo;
        public final BlockPos pos;

        public Usar(int poder, BlockPos pos, int objetivo) {
            this.poder = poder;
            this.pos = pos;
            this.objetivo = objetivo;
        }

        public Usar(FriendlyByteBuf b) {
            this(b.readByte(), b.readBlockPos(), b.readVarInt());
        }

        void encode(FriendlyByteBuf b) {
            b.writeByte(poder);
            b.writeBlockPos(pos);
            b.writeVarInt(objetivo);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            if (p != null) Poderes.usar(p, Poder.de(poder), pos, objetivo);
        }
    }

    /** Salir de las pantallas por el destino 'indice' (-1 = por donde entró). */
    public static class SalirPantalla {
        public final int indice;

        public SalirPantalla(int indice) {
            this.indice = indice;
        }

        public SalirPantalla(FriendlyByteBuf b) {
            this(b.readVarInt() - 1);
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(indice + 1);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            if (p != null) PantallasServidor.salir(p, indice);
        }
    }

    /** La voz grabada que se quiere imitar desde un bloque. */
    public static class Grabacion {
        public final BlockPos pos;
        public final byte[] datos;

        public Grabacion(BlockPos pos, byte[] datos) {
            this.pos = pos;
            this.datos = datos;
        }

        public Grabacion(FriendlyByteBuf b) {
            this(b.readBlockPos(), b.readByteArray(MAX_GRABACION));
        }

        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeByteArray(datos);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            if (p != null) Poderes.imitar(p, pos, datos);
        }
    }

    /** Por qué pantalla está mirando ahora (dentro de las pantallas): -1 = la de entrada, si no el índice del destino. */
    public static class VerPantalla {
        public final int indice;

        public VerPantalla(int indice) {
            this.indice = indice;
        }

        public VerPantalla(FriendlyByteBuf b) {
            this(b.readVarInt() - 1);
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(indice + 1);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            if (p != null) PantallasServidor.ver(p, indice);
        }
    }

    /** La cara de alguien de estática sale en una pantalla (activa = false: ya no está en ninguna). */
    public static class CaraPantalla {
        public final int id, semilla;
        public final java.util.UUID quien;
        public final BlockPos pos;
        public final boolean activa;

        public CaraPantalla(int id, java.util.UUID quien, int semilla, BlockPos pos, boolean activa) {
            this.id = id;
            this.quien = quien;
            this.semilla = semilla;
            this.pos = pos;
            this.activa = activa;
        }

        public CaraPantalla(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUUID(), b.readInt(), b.readBlockPos(), b.readBoolean());
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeUUID(quien);
            b.writeInt(semilla);
            b.writeBlockPos(pos);
            b.writeBoolean(activa);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.cara(this));
        }
    }
    /** Cuánta Señal tiene el jugador local (0-100). */
    public static class SenalMsg {
        public final int valor;

        public SenalMsg(int valor) {
            this.valor = valor;
        }

        public SenalMsg(FriendlyByteBuf b) {
            this(b.readByte());
        }

        void encode(FriendlyByteBuf b) {
            b.writeByte(valor);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.senal(this));
        }
    }

    /** Lo que ve en su pantalla quien lo recibe: el vacío de Canal perdido, la pausa, sin señal o el rebobinado. */
    public static class Pantallazo {
        public static final int VACIO = 0, PAUSA = 1, SIN_SENAL = 2, REBOBINAR = 3;
        public final int tipo, ticks, extra;

        public Pantallazo(int tipo, int ticks, int extra) {
            this.tipo = tipo;
            this.ticks = ticks;
            this.extra = extra;
        }

        public Pantallazo(FriendlyByteBuf b) {
            this(b.readByte(), b.readVarInt(), b.readInt());
        }

        void encode(FriendlyByteBuf b) {
            b.writeByte(tipo);
            b.writeVarInt(ticks);
            b.writeInt(extra);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.pantallazo(this));
        }
    }

    /** Clon de Grabación: el camino que repite (un punto por tick) y en qué ticks da un golpe. */
    public static class Clon {
        public final int dueno, semilla;
        public final float[] x, y, z, yaw;
        public final boolean[] golpe;

        public Clon(int dueno, int semilla, float[] x, float[] y, float[] z, float[] yaw, boolean[] golpe) {
            this.dueno = dueno;
            this.semilla = semilla;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.golpe = golpe;
        }

        public Clon(FriendlyByteBuf b) {
            dueno = b.readVarInt();
            semilla = b.readInt();
            int n = Math.min(200, b.readVarInt());
            x = new float[n];
            y = new float[n];
            z = new float[n];
            yaw = new float[n];
            golpe = new boolean[n];
            for (int i = 0; i < n; i++) {
                x[i] = b.readFloat();
                y[i] = b.readFloat();
                z[i] = b.readFloat();
                yaw[i] = b.readFloat();
                golpe[i] = b.readBoolean();
            }
        }

        void encode(FriendlyByteBuf b) {
            b.writeVarInt(dueno);
            b.writeInt(semilla);
            b.writeVarInt(x.length);
            for (int i = 0; i < x.length; i++) {
                b.writeFloat(x[i]);
                b.writeFloat(y[i]);
                b.writeFloat(z[i]);
                b.writeFloat(yaw[i]);
                b.writeBoolean(golpe[i]);
            }
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.estatica.EstaticaRedCliente.clon(this));
        }
    }

    /** Con alguien agarrado por los tentáculos: 1 = estampar (clic izquierdo), 2 = lanzar (clic derecho). */
    public static class AccionTent {
        public final int modo;

        public AccionTent(int modo) {
            this.modo = modo;
        }

        public AccionTent(FriendlyByteBuf b) {
            this(b.readByte());
        }

        void encode(FriendlyByteBuf b) {
            b.writeByte(modo);
        }

        void handle(Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            if (p != null) Tentaculos.elegir(p, modo == 1 ? Poder.ESTAMPAR : Poder.LANZAR);
        }
    }
}
