package com.miscelanea.pociones;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor: con ecolocalización, el jugador ha agachado para dar una palmada (sonar propio). El servidor la
 * hace sonar para todos (también para él: así su cliente la convierte en una onda como cualquier otro sonido). El
 * tiempo mínimo entre palmadas (2 s) se comprueba aquí también.
 */
public class PalmadaPacket {
    private static final String CLAVE = "MiscelaneaPalmada";

    public PalmadaPacket() {}

    public PalmadaPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || !p.hasEffect(PocionesRegistro.ECOLOCALIZACION.get())) return;
        long ahora = p.level().getGameTime();
        if (ahora - p.getPersistentData().getLong(CLAVE) < 38) return;
        p.getPersistentData().putLong(CLAVE, ahora);
        p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), PocionesRegistro.PALMADA.get(), SoundSource.PLAYERS, 1.0F,
                0.95F + p.getRandom().nextFloat() * 0.1F);
    }
}
