package com.miscelanea.oca;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Una partida de parchís sobre un tablero colocado en el mundo. */
public class OcaBlockEntity extends BlockEntity {
    public OcaPartida p = new OcaPartida();
    private final Random rnd = new Random();
    private int esperaCpu;
    /** Juega por quien se va de la mesa o no juega; votos para terminar antes de tiempo (no se guardan). */
    private final com.miscelanea.mesas.Turnos.Vigia vigia = new com.miscelanea.mesas.Turnos.Vigia();
    private final com.miscelanea.mesas.Turnos.Votacion votos = new com.miscelanea.mesas.Turnos.Votacion();

    public OcaBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.OCA_BE.get(), pos, s);
    }

    public Vec3 mundo(double tx, double ty) {
        Direction f = getBlockState().getValue(OcaBlock.FACING), der = f.getClockWise();
        double u = (tx / OcaGeo.N - 0.5) * OcaGeo.LADO, v = (ty / OcaGeo.N - 0.5) * OcaGeo.LADO;
        return Vec3.atCenterOf(worldPosition).add(der.getStepX() * u - f.getStepX() * v, -0.5 + 1.5 / 16, der.getStepZ() * u - f.getStepZ() * v);
    }

    public double[] textura(Vec3 h) {
        Direction f = getBlockState().getValue(OcaBlock.FACING), der = f.getClockWise();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double u = (h.x - cx) * der.getStepX() + (h.z - cz) * der.getStepZ();
        double v = -((h.x - cx) * f.getStepX() + (h.z - cz) * f.getStepZ());
        return new double[]{(u / OcaGeo.LADO + 0.5) * OcaGeo.N, (v / OcaGeo.LADO + 0.5) * OcaGeo.N};
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private int indice(ServerPlayer pl) {
        for (int i = 0; i < p.js.size(); i++) if (pl.getUUID().equals(p.js.get(i).uuid)) return i;
        return -1;
    }

    private void sonar(SoundEvent e, float vol, float tono) {
        level.playSound(null, worldPosition, e, SoundSource.BLOCKS, vol, tono);
    }

    public void clic(ServerPlayer pl, double tx, double ty) {
        long ahora = level.getGameTime();
        int yo = indice(pl);
        boolean miTurno = yo >= 0 && yo == p.turno && p.fase == OcaPartida.TIRAR;
        if (yo >= 0) vigia.despertar(pl.getUUID());
        OcaBotones.Boton b = OcaBotones.en(p, tx, ty);
        if (b == null) {
            if (miTurno) {
                // clic fácil: cualquier sitio del tablero tira el dado
                p.tirar(rnd, ahora);
                sonar(SoundEvents.BONE_BLOCK_HIT, 0.9F, 1.5F);
                cambiado();
            } else if (yo >= 0 && p.fase == OcaPartida.TIRAR)
                pl.displayClientMessage(Component.translatable("msg.miscelanea.imperio.no_turno").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean hecho = true;
        switch (b.id()) {
            case OcaBotones.UNIRSE -> hecho = p.unir(pl.getUUID(), pl.getGameProfile().getName());
            case OcaBotones.CPU -> hecho = yo >= 0 && p.anadirCpu();
            case OcaBotones.EMPEZAR -> hecho = yo >= 0 && p.empezar(rnd);
            case OcaBotones.NUEVA -> {
                int v = p.velocidad;
                p = new OcaPartida();
                p.velocidad = v;
                vigia.limpiar();
                votos.limpiar();
            }
            case OcaBotones.VELOCIDAD -> {
                if (yo >= 0 || p.fase == OcaPartida.LOBBY) p.cambiarVelocidad();
                else hecho = false;
            }
            case OcaBotones.TERMINAR -> {
                // hace falta que estén de acuerdo todos los humanos que siguen en la mesa
                if (yo < 0) hecho = false;
                else {
                    int faltan = votos.votar(level, worldPosition, pl.getUUID(), p.humanos());
                    if (faltan == 0) p.terminar();
                    else com.miscelanea.mesas.Turnos.avisar(level, worldPosition, com.miscelanea.mesas.Turnos.AVISO_VOTO,
                            pl.getGameProfile().getName(), faltan);
                }
            }
            default -> hecho = false;
        }
        if (hecho) sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.8F, 1.2F);
        cambiado();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, OcaBlockEntity be) {
        OcaPartida p = be.p;
        long ahora = l.getGameTime();
        int antes = p.fase;
        boolean cambio = p.tick(ahora);
        OcaPartida.Jugador a = p.fase == OcaPartida.TIRAR ? p.actual() : null;
        // si el humano de turno se ha ido (o no juega) se tira por él
        boolean porEl = a != null && !a.cpu() && be.vigia.vigilar(l, pos, a.uuid, a.nombre, p.tDado * 8 + p.turno);
        if (a == null || a.cpu()) be.vigia.vigilar(l, pos, null, "", 0);
        if (a != null && (a.cpu() || porEl)) {
            if (++be.esperaCpu >= p.esperaCpu()) {
                be.esperaCpu = 0;
                if (p.jugarPor(be.rnd, ahora)) {
                    cambio = true;
                    be.sonar(SoundEvents.BONE_BLOCK_HIT, 0.8F, 1.5F);
                }
            }
        }
        if (cambio) {
            if (p.fase == OcaPartida.FIN && antes != OcaPartida.FIN && p.ganador >= 0) {
                be.sonar(SoundEvents.PLAYER_LEVELUP, 1F, 1F);
                for (ServerPlayer o : ((ServerLevel) l).getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12)))
                    o.displayClientMessage(Component.translatable("msg.miscelanea.oca.gana", p.js.get(p.ganador).nombre).withStyle(ChatFormatting.GOLD), false);
            }
            be.cambiado();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.put("partida", p.guardar());
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        p = t.contains("partida") ? OcaPartida.cargar(t.getCompound("partida")) : new OcaPartida();
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
        return new AABB(worldPosition).inflate(1.2, 2, 1.2);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) Visibles.SET.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Visibles.SET.remove(this);
    }

    public static final class Visibles {
        public static final java.util.Set<OcaBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    }
}
