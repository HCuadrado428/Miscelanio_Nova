---
name: networking
description: Paquetes de red cliente-servidor en Minecraft 1.20.1–1.21.11 para Fabric, NeoForge y Forge, con un patrón común reutilizable. Usar al enviar datos entre cliente y servidor.
---

# Networking

## Regla de oro
El cliente **nunca** es de fiar: valida en el servidor todo lo que llega (distancia al bloque, permisos, rangos). Ejecuta la lógica en el hilo principal (`context.enqueueWork(...)` / `server.execute(...)`), no en el hilo de red.

## ≥ 1.20.5: payloads vanilla (mismo record para todos los loaders)
```java
public record SetModePayload(BlockPos pos, int mode) implements CustomPacketPayload {
    public static final Type<SetModePayload> TYPE = new Type<>(ModIds.id("set_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetModePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetModePayload::pos,
        ByteBufCodecs.VAR_INT, SetModePayload::mode,
        SetModePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```
| | Registrar | Recibir | Enviar |
|---|---|---|---|
| **Fabric** | `PayloadTypeRegistry.playC2S().register(TYPE, CODEC)` (y `playS2C()` para el otro sentido) | `ServerPlayNetworking.registerGlobalReceiver(TYPE, (p, ctx) -> ctx.server().execute(...))` | `ClientPlayNetworking.send(payload)` / `ServerPlayNetworking.send(player, payload)` |
| **NeoForge** | `RegisterPayloadHandlersEvent` → `event.registrar("1").playToServer(TYPE, CODEC, handler)` | handler `(payload, IPayloadContext ctx)`; NeoForge ya lo ejecuta en el hilo principal por defecto | `PacketDistributor.sendToServer(payload)` / `sendToPlayer(player, payload)` (1.21.1: `ClientPacketDistributor` en versiones posteriores — compruébalo) |
| **Forge 1.21.x** | `ChannelBuilder` + `SimpleChannel` (API distinta de NeoForge) | | |

## 1.20.1 (Forge y Fabric)
- No hay `CustomPacketPayload`/`StreamCodec`. Se escribe a mano con `FriendlyByteBuf`.
- **Forge 1.20.1**: `NetworkRegistry.newSimpleChannel(id, () -> "1", "1"::equals, "1"::equals)`, `channel.messageBuilder(Msg.class, idx).encoder(...).decoder(...).consumerMainThread(...).add()`. Enviar: `CHANNEL.sendToServer(msg)` / `CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)`.
- **Fabric 1.20.1**: `ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buf, sender) -> {...})`, enviar con `ClientPlayNetworking.send(id, buf)`.

## Patrón multiversión recomendado
1. En `common/` define una interfaz `ModNetwork { void sendToServer(Object msg); void sendToPlayer(ServerPlayer p, Object msg); }` y los mensajes como records.
2. Implementa la interfaz por loader (y por versión con Stonecutter: `//? if >=1.20.5 {`).
3. La lógica de cada mensaje (`handle`) vive en común.

## Depurar
- "Unknown custom packet identifier": el paquete no está registrado en ese lado (cliente o servidor).
- Disconnect al entrar: el codec escribe/lee distinto número de campos.
