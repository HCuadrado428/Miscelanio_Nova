package com.miscelanea.domino;

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

/** Una partida de dominó sobre una mesa colocada en el mundo. */
public class DominoBlockEntity extends BlockEntity {
    public DominoPartida p = new DominoPartida();
    private final Random rnd = new Random();
    private int esperaCpu;

    public DominoBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.DOMINO_BE.get(), pos, s);
    }

    public Vec3 mundo(double tx, double ty) {
        Direction f = getBlockState().getValue(DominoBlock.FACING), der = f.getClockWise();
        double u = (tx / DominoGeo.N - 0.5) * DominoGeo.LADO, v = (ty / DominoGeo.N - 0.5) * DominoGeo.LADO;
        return Vec3.atCenterOf(worldPosition).add(der.getStepX() * u - f.getStepX() * v, -0.5 + 1.5 / 16, der.getStepZ() * u - f.getStepZ() * v);
    }

    public double[] textura(Vec3 h) {
        Direction f = getBlockState().getValue(DominoBlock.FACING), der = f.getClockWise();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double u = (h.x - cx) * der.getStepX() + (h.z - cz) * der.getStepZ();
        double v = -((h.x - cx) * f.getStepX() + (h.z - cz) * f.getStepZ());
        return new double[]{(u / DominoGeo.LADO + 0.5) * DominoGeo.N, (v / DominoGeo.LADO + 0.5) * DominoGeo.N};
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
        DominoBotones.Boton b = DominoBotones.en(p, tx, ty);
        if (b == null) {
            if (yo < 0 || p.fase != DominoPartida.JUGAR) return;
            if (yo != p.turno) {
                pl.displayClientMessage(Component.translatable("msg.miscelanea.imperio.no_turno").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            DominoPartida.Jugador j = p.actual();
            int k = DominoGeo.manoEn(j, tx, ty);
            if (k >= 0) {
                int f = j.mano.get(k);
                boolean iz = p.encaja(f, -1), de = p.encaja(f, 1);
                if (!iz && !de) {
                    pl.displayClientMessage(Component.translatable("msg.miscelanea.domino.no_encaja").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                if (iz && de && p.extremo(-1) != p.extremo(1)) {
                    p.sel = p.sel == f ? -1 : f;
                    sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.5F, 1.7F);
                    cambiado();
                    return;
                }
                jugar(f, iz ? -1 : 1, ahora);
                return;
            }
            if (Math.hypot(tx - DominoGeo.POZO_X, ty - DominoGeo.POZO_Y) < 34 && p.robar()) {
                sonar(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.4F);
                cambiado();
                return;
            }
            if (p.sel >= 0) {
                // clic fácil: la ficha elegida va al extremo más cercano al clic
                DominoGeo.Cadena c = DominoGeo.cadena(p);
                double di = Math.hypot(tx - c.hueco[0][0], ty - c.hueco[0][1]), dd = Math.hypot(tx - c.hueco[1][0], ty - c.hueco[1][1]);
                jugar(p.sel, di <= dd ? -1 : 1, ahora);
            }
            return;
        }
        boolean hecho = true;
        switch (b.id()) {
            case DominoBotones.UNIRSE -> hecho = p.unir(pl.getUUID(), pl.getGameProfile().getName());
            case DominoBotones.CPU -> hecho = yo >= 0 && p.anadirCpu();
            case DominoBotones.EMPEZAR -> hecho = yo >= 0 && p.empezar(rnd, ahora);
            case DominoBotones.NUEVA -> p = new DominoPartida();
            case DominoBotones.TERMINAR -> {
                if (yo >= 0) p.terminar();
                else hecho = false;
            }
            default -> hecho = false;
        }
        if (hecho) sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.8F, 1.2F);
        cambiado();
    }

    private void jugar(int f, int lado, long ahora) {
        int fase = p.fase;
        if (p.jugar(f, lado, ahora)) {
            sonar(SoundEvents.STONE_PLACE, 0.8F, 1.7F);
            if (p.fase == DominoPartida.FIN && fase != DominoPartida.FIN) fin();
            cambiado();
        }
    }

    private void fin() {
        sonar(SoundEvents.PLAYER_LEVELUP, 1F, 1F);
        if (p.ganador < 0) return;
        for (ServerPlayer o : ((ServerLevel) level).getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(12)))
            o.displayClientMessage(Component.translatable("msg.miscelanea.domino.gana", p.js.get(p.ganador).nombre).withStyle(ChatFormatting.GOLD), false);
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, DominoBlockEntity be) {
        DominoPartida p = be.p;
        long ahora = l.getGameTime();
        if (p.fase != DominoPartida.JUGAR) return;
        DominoPartida.Jugador j = p.actual();
        boolean atascado = !p.puedeJugar(j);
        if (!j.cpu() && !atascado) {
            be.esperaCpu = 0;
            return;
        }
        if (++be.esperaCpu < (j.cpu() ? 18 : 22)) return;
        be.esperaCpu = 0;
        int fase = p.fase;
        boolean hecho = j.cpu() ? p.cpu(ahora) : (p.robar() || p.pasarSiHaceFalta());
        if (hecho) {
            be.sonar(p.tUlt == ahora ? SoundEvents.STONE_PLACE : SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.6F);
            if (p.fase == DominoPartida.FIN && fase != DominoPartida.FIN) be.fin();
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
        p = t.contains("partida") ? DominoPartida.cargar(t.getCompound("partida")) : new DominoPartida();
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
        public static final java.util.Set<DominoBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    }
}
