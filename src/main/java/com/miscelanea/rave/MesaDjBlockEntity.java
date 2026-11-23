package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * La mesa de DJ: qué suena, desde cuándo, a qué volumen y hasta dónde llega (con los altavoces cercanos). El estado se
 * sincroniza con los clientes que tienen cargado su chunk, y cada uno se pone la música él solo (ver MusicaDj del cliente):
 * así se oye a mucha distancia sin trucos de volumen y se para en cuanto la mesa para.
 */
public class MesaDjBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String pista = "";
    private boolean sonando, bucle;
    private long inicio;
    private int volumen = 80, alcance = 32, altavoces;

    public MesaDjBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.MESA_BE.get(), p, s);
    }

    public String pista() { return pista; }
    public boolean sonando() { return sonando; }
    public boolean bucle() { return bucle; }
    public long inicio() { return inicio; }
    public int volumen() { return volumen; }
    public int alcance() { return alcance; }
    public int altavoces() { return altavoces; }

    // ------------------------------------------------------------------ servidor
    /** Una orden de la pantalla (ya validada la distancia en RaveRed). */
    void orden(ServerPlayer p, int accion, String texto, int numero) {
        switch (accion) {
            case RaveRed.DJ_PONER -> {
                if (Pistas.de(texto) == null) return;
                poner(texto);
            }
            case RaveRed.DJ_PARAR -> parar();
            case RaveRed.DJ_VOLUMEN -> {
                volumen = Math.max(0, Math.min(100, numero));
                cambiado();
            }
            case RaveRed.DJ_BUCLE -> {
                bucle = numero != 0;
                cambiado();
            }
            case RaveRed.DJ_LUCES_ON, RaveRed.DJ_LUCES_OFF -> {
                int n = luces(accion == RaveRed.DJ_LUCES_ON);
                p.displayClientMessage(Component.translatable("rave.miscelanea.dj.luces", n), true);
            }
            default -> {
            }
        }
    }

    /** Pone una pista desde el principio. */
    public void poner(String id) {
        if (level == null) return;
        pista = id;
        sonando = true;
        inicio = level.getGameTime();
        calcularAlcance();
        cambiado();
    }

    public void parar() {
        sonando = false;
        cambiado();
    }

    /** Enciende o apaga las luces de la rave a menos de 16 bloques; devuelve cuántas. */
    private int luces(boolean on) {
        if (level == null) return 0;
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(worldPosition.offset(-16, -6, -16), worldPosition.offset(16, 10, 16))) {
            BlockState s = level.getBlockState(q);
            if (s.hasProperty(LucesRave.ENCENDIDA) && s.getValue(LucesRave.ENCENDIDA) != on) {
                level.setBlock(q, s.setValue(LucesRave.ENCENDIDA, on), 3);
                n++;
            }
        }
        return n;
    }

    private void calcularAlcance() {
        if (level == null) return;
        int n = 0;
        for (AltavozBlockEntity a : AltavozBlockEntity.SERVIDOR)
            if (!a.isRemoved() && a.getLevel() == level && a.getBlockPos().distSqr(worldPosition) <= 24 * 24) n++;
        altavoces = n;
        alcance = Math.min(RaveConfig.alcanceMax(), RaveConfig.alcanceBase() + n * RaveConfig.porAltavoz());
    }

    private void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void tickServidor(Level l, BlockPos p, BlockState s, MesaDjBlockEntity be) {
        if (!be.sonando || !(l instanceof ServerLevel)) return;
        Pistas.Pista pi = Pistas.de(be.pista);
        long t = l.getGameTime();
        if (pi == null) {
            be.parar();
            return;
        }
        if (t - be.inicio > pi.ticks() + 10) {
            if (be.bucle) be.poner(be.pista);
            else be.parar();
            return;
        }
        // los altavoces se pueden poner o quitar con la música puesta
        if (t % 40 == 0) {
            int antes = be.alcance;
            be.calcularAlcance();
            if (be.alcance != antes) be.cambiado();
        }
    }

    public static void tickCliente(Level l, BlockPos p, BlockState s, MesaDjBlockEntity be) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.rave.MusicaDj.tick(be));
    }

    // ------------------------------------------------------------------ guardar y sincronizar
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putString("Pista", pista);
        t.putBoolean("Sonando", sonando);
        t.putBoolean("Bucle", bucle);
        t.putLong("Inicio", inicio);
        t.putInt("Volumen", volumen);
        t.putInt("Alcance", alcance);
        t.putInt("Altavoces", altavoces);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        pista = t.getString("Pista");
        sonando = t.getBoolean("Sonando");
        bucle = t.getBoolean("Bucle");
        inicio = t.getLong("Inicio");
        volumen = t.contains("Volumen") ? t.getInt("Volumen") : 80;
        alcance = t.contains("Alcance") ? t.getInt("Alcance") : 32;
        altavoces = t.getInt("Altavoces");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ GeckoLib (los platos giran en el modelo)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
