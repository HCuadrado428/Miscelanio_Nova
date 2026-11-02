package com.miscelanea.torneo;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Torneo de eliminatorias: inscripción (hasta 16), sorteo con "pase directo" si no cuadran las potencias de 2, y
 * resultados que apuntan los propios jugadores del cruce (o quien organiza). Cuadro por rondas: en cada casilla el
 * índice del inscrito, -1 hueco (pase) y -2 por decidir.
 */
public class TorneoBlockEntity extends BlockEntity {
    public static final int INSCRIPCION = 0, EN_JUEGO = 1, TERMINADO = 2, MAX = 16;
    @Nullable public UUID organizador;
    public String titulo = "";
    public final List<UUID> ids = new ArrayList<>();
    public final List<String> nombres = new ArrayList<>();
    public final List<int[]> rondas = new ArrayList<>();
    public int estado = INSCRIPCION, campeon = -1;

    public TorneoBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.TORNEO_BE.get(), p, s);
    }

    public Direction facing() {
        return getBlockState().getValue(TorneoBlock.FACING);
    }

    boolean organiza(Player p) {
        return organizador == null || p.getUUID().equals(organizador) || p.hasPermissions(2);
    }

    void apuntar(ServerPlayer p) {
        if (estado != INSCRIPCION || ids.contains(p.getUUID())) return;
        if (ids.size() >= MAX) {
            p.displayClientMessage(Component.translatable("miscelanea.torneo.lleno"), true);
            return;
        }
        ids.add(p.getUUID());
        nombres.add(p.getGameProfile().getName());
        if (organizador == null) organizador = p.getUUID();
        level.playSound(null, worldPosition, ModSounds.CA_CARTA.get(), SoundSource.BLOCKS, 1F, 1.2F);
        cambio();
    }

    void borrar(ServerPlayer p) {
        int i = ids.indexOf(p.getUUID());
        if (estado != INSCRIPCION || i < 0) return;
        ids.remove(i);
        nombres.remove(i);
        cambio();
    }

    void empezar(ServerPlayer p) {
        if (estado != INSCRIPCION || !organiza(p)) return;
        if (ids.size() < 2) {
            p.displayClientMessage(Component.translatable("miscelanea.torneo.pocos"), true);
            return;
        }
        int tam = 2;
        while (tam < ids.size()) tam *= 2;
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) orden.add(i);
        Collections.shuffle(orden, new java.util.Random(level.random.nextLong()));
        // los pases directos se reparten: un hueco en cada cruce mientras sobren
        int[] r0 = new int[tam];
        int huecos = tam - ids.size(), k = 0;
        for (int m = 0; m < tam / 2; m++) {
            r0[2 * m] = orden.get(k++);
            r0[2 * m + 1] = huecos-- > 0 ? -1 : orden.get(k++);
        }
        rondas.clear();
        rondas.add(r0);
        for (int t = tam / 2; t >= 1; t /= 2) {
            int[] r = new int[t];
            java.util.Arrays.fill(r, -2);
            rondas.add(r);
        }
        estado = EN_JUEGO;
        avanzarPases();
        anunciar(Component.translatable("miscelanea.torneo.empieza", tituloVisible(), ids.size()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, worldPosition, ModSounds.FE_ARRANCA.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        cambio();
    }

    String tituloVisible() {
        return titulo.isBlank() ? Component.translatable("miscelanea.torneo.titulo").getString() : titulo;
    }

    private void avanzarPases() {
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (int r = 0; r + 1 < rondas.size(); r++) {
                int[] a = rondas.get(r), b = rondas.get(r + 1);
                for (int m = 0; m < b.length; m++) {
                    if (b[m] != -2) continue;
                    int x = a[2 * m], y = a[2 * m + 1];
                    if (x == -2 || y == -2) continue;
                    if (x == -1 || y == -1) {
                        b[m] = x == -1 ? y : x;
                        cambio = true;
                    }
                }
            }
        }
        int[] ult = rondas.get(rondas.size() - 1);
        if (ult[0] >= 0 && estado == EN_JUEGO) {
            estado = TERMINADO;
            campeon = ult[0];
        }
    }

    /** Apunta que gana la casilla (ronda, slot): pasa a la siguiente ronda. */
    void gana(ServerPlayer p, int ronda, int slot) {
        if (estado != EN_JUEGO || ronda < 0 || ronda + 1 >= rondas.size()) return;
        int[] a = rondas.get(ronda);
        if (slot < 0 || slot >= a.length) return;
        int m = slot / 2, rival = a[slot ^ 1], quien = a[slot];
        if (quien < 0 || rival < 0 || rondas.get(ronda + 1)[m] != -2) return;
        boolean juega = p.getUUID().equals(ids.get(quien)) || p.getUUID().equals(ids.get(rival));
        if (!juega && !organiza(p)) {
            p.displayClientMessage(Component.translatable("miscelanea.torneo.no_puedes"), true);
            return;
        }
        rondas.get(ronda + 1)[m] = quien;
        anunciar(Component.translatable("miscelanea.torneo.cruce", nombres.get(quien), nombres.get(rival)));
        avanzarPases();
        if (estado == TERMINADO) {
            anunciar(Component.translatable("miscelanea.torneo.campeon", nombres.get(campeon), tituloVisible()).withStyle(ChatFormatting.GOLD));
            level.playSound(null, worldPosition, ModSounds.FE_PREMIO_GORDO.get(), SoundSource.BLOCKS, 1F, 1F);
            if (level instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.FIREWORK, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.15);
        } else level.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 0.7F, 1F);
        cambio();
    }

    void reiniciar(ServerPlayer p) {
        if (!organiza(p)) return;
        ids.clear();
        nombres.clear();
        rondas.clear();
        estado = INSCRIPCION;
        campeon = -1;
        organizador = p.getUUID();
        cambio();
    }

    void titulo(ServerPlayer p, String t) {
        if (!organiza(p)) return;
        titulo = t.length() > 32 ? t.substring(0, 32) : t;
        cambio();
    }

    private void anunciar(Component c) {
        for (Player o : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(48))) o.displayClientMessage(c, false);
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (organizador != null) t.putUUID("org", organizador);
        t.putString("tit", titulo);
        ListTag l = new ListTag();
        for (int i = 0; i < ids.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", ids.get(i));
            c.putString("n", nombres.get(i));
            l.add(c);
        }
        t.put("ins", l);
        ListTag r = new ListTag();
        for (int[] a : rondas) {
            CompoundTag c = new CompoundTag();
            c.putIntArray("s", a);
            r.add(c);
        }
        t.put("rondas", r);
        t.putInt("estado", estado);
        t.putInt("camp", campeon);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        organizador = t.hasUUID("org") ? t.getUUID("org") : null;
        titulo = t.getString("tit");
        ids.clear();
        nombres.clear();
        for (Tag e : t.getList("ins", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            ids.add(c.getUUID("id"));
            nombres.add(c.getString("n"));
        }
        rondas.clear();
        for (Tag e : t.getList("rondas", Tag.TAG_COMPOUND)) rondas.add(((CompoundTag) e).getIntArray("s"));
        estado = t.getInt("estado");
        campeon = t.contains("camp") ? t.getInt("camp") : -1;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
