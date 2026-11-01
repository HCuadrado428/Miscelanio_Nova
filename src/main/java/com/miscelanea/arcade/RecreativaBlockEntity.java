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
    public long ultimoFrame;     // servidor: para limitar el ritmo de retransmisión
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

    public boolean ocupadaPorOtro(ServerPlayer p) {
        if (jugador == null || jugador.equals(p.getUUID())) return false;
        ServerPlayer o = p.server.getPlayerList().getPlayer(jugador);
        return o != null && o.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5) < 64;
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, RecreativaBlockEntity be) {
        if (be.jugador == null || l.getGameTime() % 20 != 0) return;
        ServerPlayer o = l.getServer().getPlayerList().getPlayer(be.jugador);
        if (o == null || o.level() != l || o.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > 64) be.jugador = null;
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
