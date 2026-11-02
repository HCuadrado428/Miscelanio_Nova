package com.miscelanea.arcade;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/** Recreativa: créditos metidos (se quedan en la máquina) y quién está jugando ahora. En el cliente guarda la última imagen recibida. */
public class RecreativaBlockEntity extends BlockEntity {
    public int creditos;
    @Nullable
    public UUID jugador;
    /** Compañero en la recreativa doble (jugador 2). */
    @Nullable
    public UUID jugador2;
    /** Recreativa enlazada con cable desde la que juega el jugador 2 (servidor). */
    @Nullable
    public BlockPos enlazada;
    public long ultimoFrame;     // servidor: para limitar el ritmo de retransmisión
    /** Servidor: partida en marcha empezada por el jugador 1 (gastando crédito), para validar su récord. */
    @Nullable
    public UUID partidaDe;
    public String partidaJuego = "";
    public long partidaInicio, partidaSemilla;
    /** Cliente: datos de pantalla (los gestiona el renderizador). */
    public Object pantallaCliente;

    public RecreativaBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.RECREATIVA_BE.get(), p, s);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("creditos", creditos);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        creditos = t.getInt("creditos");
    }

    public void empezarPartida(UUID quien, String juego, long ahora, long semilla) {
        partidaDe = quien;
        partidaJuego = juego;
        partidaInicio = ahora;
        partidaSemilla = semilla;
    }

    public void acabarPartida() {
        partidaDe = null;
        partidaJuego = "";
    }

    public boolean ocupadaPorOtro(ServerPlayer p) {
        if (jugador == null || jugador.equals(p.getUUID())) return false;
        ServerPlayer o = p.server.getPlayerList().getPlayer(jugador);
        return o != null && o.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5) < 64;
    }

    public boolean doble() {
        return getBlockState().getBlock() instanceof RecreativaBlock r && r.doble || getBlockState().getBlock() instanceof MesaCoctelBlock;
    }

    /** El jugador 2 se ha ido: la recreativa enlazada desde la que jugaba queda libre. */
    public void soltarEnlace(@Nullable UUID quien) {
        if (enlazada != null && level != null && level.getBlockEntity(enlazada) instanceof RecreativaBlockEntity v && v.jugador != null
                && (quien == null || v.jugador.equals(quien))) v.jugador = null;
        enlazada = null;
    }

    private static boolean cerca(Level l, BlockPos pos, @Nullable UUID u) {
        if (u == null) return false;
        ServerPlayer o = l.getServer().getPlayerList().getPlayer(u);
        return o != null && o.level() == l && o.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) <= 64;
    }

    /** Avisa a los dos jugadores de quién es su compañero (o de que se fue). */
    public void avisarCompaneros() {
        if (level == null || level.isClientSide) return;
        var lista = level.getServer().getPlayerList();
        ServerPlayer a = jugador == null ? null : lista.getPlayer(jugador), b = jugador2 == null ? null : lista.getPlayer(jugador2);
        if (a != null) com.miscelanea.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> a),
                new ArcadeAbrirPacket(worldPosition, creditos, false, 5, true, 0, "", b == null ? "" : b.getGameProfile().getName(), 1));
        if (b != null) com.miscelanea.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> b),
                new ArcadeAbrirPacket(worldPosition, creditos, false, 5, true, 0, "", a == null ? "" : a.getGameProfile().getName(), 1));
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, RecreativaBlockEntity be) {
        if (l.getGameTime() % 20 != 0) return;
        boolean cambio = false;
        if (be.jugador != null && !cerca(l, pos, be.jugador)) {
            be.jugador = null;
            be.acabarPartida();
            cambio = true;
        }
        if (be.jugador2 != null && !cerca(l, pos, be.jugador2) && !(be.enlazada != null && cerca(l, be.enlazada, be.jugador2))) {
            be.soltarEnlace(be.jugador2);
            be.jugador2 = null;
            cambio = true;
        }
        if (cambio) {
            be.avisarCompaneros();
            // sin jugador 1 no hay partida: el compañero también se queda fuera (se le avisa al cerrar)
            if (be.jugador == null) {
                be.soltarEnlace(be.jugador2);
                be.jugador2 = null;
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide && pantallaCliente instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
