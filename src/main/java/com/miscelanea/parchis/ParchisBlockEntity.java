package com.miscelanea.parchis;

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
public class ParchisBlockEntity extends BlockEntity {
    public ParchisPartida p = new ParchisPartida();
    private final Random rnd = new Random();
    private int esperaCpu;

    public ParchisBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.PARCHIS_BE.get(), pos, s);
    }

    public Vec3 mundo(double tx, double ty) {
        Direction f = getBlockState().getValue(ParchisBlock.FACING), der = f.getClockWise();
        double u = (tx / ParchisGeo.N - 0.5) * ParchisGeo.LADO, v = (ty / ParchisGeo.N - 0.5) * ParchisGeo.LADO;
        return Vec3.atCenterOf(worldPosition).add(der.getStepX() * u - f.getStepX() * v, -0.5 + 1.5 / 16, der.getStepZ() * u - f.getStepZ() * v);
    }

    public double[] textura(Vec3 h) {
        Direction f = getBlockState().getValue(ParchisBlock.FACING), der = f.getClockWise();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double u = (h.x - cx) * der.getStepX() + (h.z - cz) * der.getStepZ();
        double v = -((h.x - cx) * f.getStepX() + (h.z - cz) * f.getStepZ());
        return new double[]{(u / ParchisGeo.LADO + 0.5) * ParchisGeo.N, (v / ParchisGeo.LADO + 0.5) * ParchisGeo.N};
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
        boolean miTurno = yo >= 0 && yo == p.turno && p.fase != ParchisPartida.LOBBY && p.fase != ParchisPartida.FIN;
        ParchisBotones.Boton b = ParchisBotones.en(p, tx, ty);
        if (b == null) {
            if (miTurno && p.fase == ParchisPartida.ELEGIR && ahora - p.tDado >= 12) {
                int f = ParchisGeo.fichaEn(p, p.colorActual(), tx, ty);
                if (f >= 0 && p.mover(f, ahora)) {
                    sonar(SoundEvents.WOOD_HIT, 0.7F, 1.6F);
                    cambiado();
                } else if (f >= 0) pl.displayClientMessage(Component.translatable("msg.miscelanea.parchis.no_mueve").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        boolean hecho = true;
        switch (b.id()) {
            case ParchisBotones.UNIRSE -> hecho = p.unir(pl.getUUID(), pl.getGameProfile().getName());
            case ParchisBotones.CPU -> hecho = yo >= 0 && p.anadirCpu();
            case ParchisBotones.EMPEZAR -> hecho = yo >= 0 && p.empezar(rnd);
            case ParchisBotones.NUEVA -> p = new ParchisPartida();
            case ParchisBotones.TERMINAR -> {
                if (yo >= 0) p.terminar();
                else hecho = false;
            }
            case ParchisBotones.TIRAR -> {
                if (!miTurno) {
                    pl.displayClientMessage(Component.translatable("msg.miscelanea.imperio.no_turno").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                p.tirar(rnd, ahora);
                sonar(SoundEvents.BONE_BLOCK_HIT, 0.9F, 1.5F);
            }
            default -> hecho = false;
        }
        if (hecho) sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.8F, 1.2F);
        cambiado();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, ParchisBlockEntity be) {
        ParchisPartida p = be.p;
        long ahora = l.getGameTime();
        int antes = p.fase;
        int metaAntes = 0;
        for (int[] c : p.prog) for (int q : c) if (q == ParchisPartida.META) metaAntes++;
        boolean cambio = p.tick(ahora);
        if (p.fase != ParchisPartida.LOBBY && p.fase != ParchisPartida.FIN && p.fase != ParchisPartida.MOVER) {
            if (p.actual().cpu()) {
                if (++be.esperaCpu >= 16) {
                    be.esperaCpu = 0;
                    int f0 = p.fase;
                    if (p.cpu(be.rnd, ahora)) {
                        cambio = true;
                        be.sonar(f0 == ParchisPartida.TIRAR ? SoundEvents.BONE_BLOCK_HIT : SoundEvents.WOOD_HIT, 0.8F, 1.5F);
                    }
                }
            } else if (p.automatico(ahora)) {
                cambio = true;
                be.sonar(SoundEvents.WOOD_HIT, 0.7F, 1.6F);
            }
        }
        if (cambio) {
            int metaAhora = 0;
            for (int[] c : p.prog) for (int q : c) if (q == ParchisPartida.META) metaAhora++;
            if (metaAhora > metaAntes) be.sonar(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
            if (p.fase == ParchisPartida.FIN && antes != ParchisPartida.FIN && p.ganador >= 0) {
                be.sonar(SoundEvents.PLAYER_LEVELUP, 1F, 1F);
                String n = p.deColor(p.ganador) != null ? p.deColor(p.ganador).nombre : "?";
                for (ServerPlayer o : ((ServerLevel) l).getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12)))
                    o.displayClientMessage(Component.translatable("msg.miscelanea.parchis.gana", n).withStyle(ChatFormatting.GOLD), false);
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
        p = t.contains("partida") ? ParchisPartida.cargar(t.getCompound("partida")) : new ParchisPartida();
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
        public static final java.util.Set<ParchisBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    }
}
