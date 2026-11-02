package com.miscelanea.mesas;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModBlocks;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/** Air hockey: cada jugador coge la portería del extremo en que está; si falta uno, juega la máquina. Partido a 7. */
public class AirHockeyBlockEntity extends BlockEntity implements MesaGrandeBlock.MesaEntidad {
    public AirHockeyPartida p = new AirHockeyPartida();
    public final UUID[] jug = new UUID[2];
    public final String[] nombre = {"", ""};
    final long[] ultimoMando = new long[2];
    // cliente: estado del tic anterior para suavizar
    public float[] antes = new float[6];
    public long ultimoFisica;

    public AirHockeyBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.AIRHOCKEY_BE.get(), pos, s);
    }

    public Direction facing() {
        return getBlockState().getValue(MesaGrandeBlock.FACING);
    }

    public int lado(UUID u) {
        for (int i = 0; i < 2; i++) if (u.equals(jug[i])) return i;
        return -1;
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Coordenada v (a lo ancho) de un punto del mundo sobre esta mesa. */
    public double campoV(Vec3 w) {
        Direction f = facing();
        return (w.x - worldPosition.getX() - 0.5) * f.getStepX() + (w.z - worldPosition.getZ() - 0.5) * f.getStepZ() + 0.5;
    }

    @Override
    public void usar(ServerPlayer sp, Vec3 donde) {
        int l = lado(sp.getUUID());
        if (l >= 0) {
            if (p.fase == AirHockeyPartida.FIN) {
                p.empezar();
                cambio();
            } else sp.displayClientMessage(Component.translatable("airhockey.miscelanea.como_salir").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        // equipo del lado largo en el que está el jugador (rojos en el lado de quien puso la mesa, azules enfrente)
        int e = MesaGeo.campo(worldPosition, facing(), sp.position())[0] < AirHockeyPartida.LARGO / 2 ? 0 : 1;
        if (jug[e] != null) e = 1 - e;
        if (jug[e] != null) {
            sp.displayClientMessage(Component.translatable("airhockey.miscelanea.llena").withStyle(ChatFormatting.RED), true);
            return;
        }
        jug[e] = sp.getUUID();
        nombre[e] = sp.getGameProfile().getName();
        p.cpu[e] = false;
        p.cpu[1 - e] = jug[1 - e] == null;
        ultimoMando[e] = level.getGameTime();
        if (p.fase == AirHockeyPartida.ESPERA || p.fase == AirHockeyPartida.FIN) p.empezar();
        sp.displayClientMessage(Component.translatable("airhockey.miscelanea.unido", Component.translatable("airhockey.miscelanea.equipo" + e)).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, worldPosition, ModSounds.FE_ARRANCA.get(), SoundSource.BLOCKS, 0.8F, 1F);
        cambio();
    }

    public void mando(ServerPlayer sp, int m) {
        int l = lado(sp.getUUID());
        if (l < 0) return;
        if ((m & 16) != 0) {
            soltar(l);
            return;
        }
        p.mando[l] = m & 15;
        ultimoMando[l] = level.getGameTime();
    }

    void soltar(int l) {
        jug[l] = null;
        nombre[l] = "";
        p.mando[l] = 0;
        p.cpu[l] = true;
        if (jug[1 - l] == null) {
            p.fase = AirHockeyPartida.ESPERA;
            p.cpu[0] = p.cpu[1] = false;
        }
        cambio();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, AirHockeyBlockEntity be) {
        AirHockeyPartida p = be.p;
        // quien se aleja o deja de mandar deja la mesa
        for (int i = 0; i < 2; i++) {
            if (be.jug[i] == null) continue;
            ServerPlayer sp = l.getServer().getPlayerList().getPlayer(be.jug[i]);
            if (sp == null || sp.level() != l || sp.distanceToSqr(Vec3.atCenterOf(pos)) > 49 || l.getGameTime() - be.ultimoMando[i] > 200) be.soltar(i);
        }
        if (p.fase == AirHockeyPartida.ESPERA) return;
        int faseAntes = p.fase;
        p.tic();
        if (p.golpes > 0) l.playSound(null, pos, ModSounds.AH_GOLPE.get(), SoundSource.BLOCKS, 0.8F, 0.9F + l.random.nextFloat() * 0.3F);
        if (p.paredes > 0) l.playSound(null, pos, ModSounds.AH_BANDA.get(), SoundSource.BLOCKS, 0.5F, 1.1F + l.random.nextFloat() * 0.2F);
        if (p.gol > 0 && p.t == 0) {
            l.playSound(null, pos, ModSounds.AH_GOL.get(), SoundSource.BLOCKS, 1F, 1F);
            for (int i = 0; i < 2; i++) {
                ServerPlayer sp = be.jug[i] == null ? null : l.getServer().getPlayerList().getPlayer(be.jug[i]);
                if (sp != null) sp.displayClientMessage(Component.translatable("airhockey.miscelanea.gol", p.goles[0], p.goles[1]).withStyle(ChatFormatting.GOLD), true);
            }
        }
        p.golpes = p.paredes = 0;
        if (p.fase != AirHockeyPartida.GOL) p.gol = 0;
        if (p.fase != faseAntes) be.cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> l.getChunkAt(pos)), new MesaFisicaPacket(pos, p.estado()));
    }

    // ------------------------------------------------------------------ cliente
    @Override
    public void fisica(float[] d) {
        antes = p.estado();
        p.ponerEstado(d);
        ultimoFisica = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ guardar y sincronizar
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("g0", p.goles[0]);
        t.putInt("g1", p.goles[1]);
        t.putInt("fase", p.fase);
        t.putBoolean("cpu0", p.cpu[0]);
        t.putBoolean("cpu1", p.cpu[1]);
        for (int i = 0; i < 2; i++) {
            if (jug[i] != null) t.putUUID("j" + i, jug[i]);
            t.putString("n" + i, nombre[i]);
        }
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        p.goles[0] = t.getInt("g0");
        p.goles[1] = t.getInt("g1");
        p.fase = t.getInt("fase");
        p.cpu[0] = t.getBoolean("cpu0");
        p.cpu[1] = t.getBoolean("cpu1");
        for (int i = 0; i < 2; i++) {
            jug[i] = t.hasUUID("j" + i) ? t.getUUID("j" + i) : null;
            nombre[i] = t.getString("n" + i);
        }
        // al cargar el mundo nadie está agarrado a la mesa
        if (level == null || !level.isClientSide) {
            if (p.fase == AirHockeyPartida.JUEGA || p.fase == AirHockeyPartida.GOL) p.fase = AirHockeyPartida.ESPERA;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4, 3, 4);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) Visibles.AIRHOCKEY.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Visibles.AIRHOCKEY.remove(this);
    }
}
