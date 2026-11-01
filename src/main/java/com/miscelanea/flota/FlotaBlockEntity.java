package com.miscelanea.flota;

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

/** Una partida de hundir la flota sobre un tablero colocado en el mundo. */
public class FlotaBlockEntity extends BlockEntity {
    public FlotaPartida p = new FlotaPartida();
    private final Random rnd = new Random();
    private int esperaCpu;

    public FlotaBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.FLOTA_BE.get(), pos, s);
    }

    public Vec3 mundo(double tx, double ty) {
        Direction f = getBlockState().getValue(FlotaBlock.FACING), der = f.getClockWise();
        double u = (tx / FlotaGeo.N - 0.5) * FlotaGeo.LADO, v = (ty / FlotaGeo.N - 0.5) * FlotaGeo.LADO;
        return Vec3.atCenterOf(worldPosition).add(der.getStepX() * u - f.getStepX() * v, -0.5 + 1.5 / 16, der.getStepZ() * u - f.getStepZ() * v);
    }

    public double[] textura(Vec3 h) {
        Direction f = getBlockState().getValue(FlotaBlock.FACING), der = f.getClockWise();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double u = (h.x - cx) * der.getStepX() + (h.z - cz) * der.getStepZ();
        double v = -((h.x - cx) * f.getStepX() + (h.z - cz) * f.getStepZ());
        return new double[]{(u / FlotaGeo.LADO + 0.5) * FlotaGeo.N, (v / FlotaGeo.LADO + 0.5) * FlotaGeo.N};
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
        FlotaBotones.Boton b = FlotaBotones.en(p, tx, ty);
        if (b == null) {
            int[] mc = FlotaGeo.en(tx, ty);
            if (mc == null || yo < 0) return;
            if (p.fase == FlotaPartida.COLOCAR && mc[0] == yo) {
                if (p.tocarPropio(yo, mc[1])) {
                    sonar(SoundEvents.WOOD_HIT, 0.6F, 1.4F);
                    cambiado();
                }
            } else if (p.fase == FlotaPartida.JUGAR) {
                if (yo != p.turno) {
                    pl.displayClientMessage(Component.translatable("msg.miscelanea.imperio.no_turno").withStyle(ChatFormatting.GRAY), true);
                } else if (mc[0] == yo) {
                    pl.displayClientMessage(Component.translatable("msg.miscelanea.flota.tu_mar").withStyle(ChatFormatting.GRAY), true);
                } else if (p.disparar(mc[1], ahora)) {
                    sonar(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7F, 0.7F);
                    cambiado();
                }
            }
            return;
        }
        boolean hecho = true;
        switch (b.id()) {
            case FlotaBotones.UNIRSE -> hecho = p.unir(pl.getUUID(), pl.getGameProfile().getName(), rnd);
            case FlotaBotones.CPU -> hecho = yo >= 0 && p.anadirCpu(rnd);
            case FlotaBotones.EMPEZAR -> hecho = yo >= 0 && p.empezar();
            case FlotaBotones.RECOLOCAR -> {
                if (yo >= 0 && !p.js.get(yo).listo) {
                    FlotaPartida.aleatorio(p.js.get(yo), rnd);
                    p.js.get(yo).sel = -1;
                } else hecho = false;
            }
            case FlotaBotones.LISTO -> {
                if (yo >= 0 && !p.js.get(yo).listo) p.listo(yo, rnd, ahora);
                else hecho = false;
            }
            case FlotaBotones.NUEVA -> p = new FlotaPartida();
            case FlotaBotones.TERMINAR -> {
                if (yo >= 0) p.terminar();
                else hecho = false;
            }
            default -> hecho = false;
        }
        if (hecho) sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.8F, 1.2F);
        cambiado();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, FlotaBlockEntity be) {
        FlotaPartida p = be.p;
        long ahora = l.getGameTime();
        if (p.fase == FlotaPartida.JUGAR && p.actual().cpu() && p.puedeDisparar(ahora)) {
            if (++be.esperaCpu >= 12) {
                be.esperaCpu = 0;
                if (p.disparar(p.objetivoCpu(be.rnd), ahora)) {
                    be.sonar(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7F, 0.7F);
                    be.cambiado();
                }
            }
        }
        if (p.fase == FlotaPartida.FIN && p.ganador >= 0 && ahora == p.tDisparo + FlotaPartida.VUELO + 2) {
            be.sonar(SoundEvents.PLAYER_LEVELUP, 1F, 1F);
            for (ServerPlayer o : ((ServerLevel) l).getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12)))
                o.displayClientMessage(Component.translatable("msg.miscelanea.flota.gana", p.js.get(p.ganador).nombre).withStyle(ChatFormatting.GOLD), false);
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
        p = t.contains("partida") ? FlotaPartida.cargar(t.getCompound("partida")) : new FlotaPartida();
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
        public static final java.util.Set<FlotaBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    }
}
