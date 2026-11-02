package com.miscelanea.torre;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Torre de piezas: cada nivel es una máscara de 3 bits (qué piezas quedan). Los niveles pares van a lo largo del eje
 * X y los impares del Z. Al sacar una pieza se calcula el riesgo de cada nivel (un solo borde es casi seguro que se
 * cae; solo el centro, algo) y se tira el dado.
 */
public class TorreBlockEntity extends BlockEntity {
    public static final int NIVELES = 18;
    public static final float BASE = 0.9F, ALTO = 0.06F, ANCHO = 0.1F, LARGO = 0.3F;
    public List<Integer> niveles = new ArrayList<>();
    public long derrumbe = -1, movido = -1000, semilla;
    public float riesgo;
    public int sacadas;
    public String ultimo = "";

    public TorreBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.TORRE_BE.get(), p, s);
        montar();
    }

    void montar() {
        niveles.clear();
        for (int i = 0; i < NIVELES; i++) niveles.add(7);
        derrumbe = -1;
        sacadas = 0;
        riesgo = 0;
        ultimo = "";
    }

    static float riesgoNivel(int m) {
        return switch (m) {
            case 7 -> 0;
            case 3, 6 -> 0.01F;
            case 5 -> 0.006F;
            case 2 -> 0.07F;
            case 1, 4 -> 0.85F;
            default -> 1;
        };
    }

    float riesgoTotal() {
        float aguanta = 1;
        for (int i = 0; i < niveles.size() - 1; i++) aguanta *= 1 - riesgoNivel(niveles.get(i));
        aguanta *= 1 - Math.max(0, niveles.size() - NIVELES) * 0.004F;
        return 1 - aguanta;
    }

    void tocar(Player p, Vec3 donde) {
        long ahora = level.getGameTime();
        if (p.isSecondaryUseActive() || derrumbe >= 0) {
            if (derrumbe >= 0 && ahora - derrumbe < 30) return;
            montar();
            level.playSound(null, worldPosition, ModSounds.TO_CLAC.get(), SoundSource.BLOCKS, 1F, 0.7F);
            p.displayClientMessage(Component.translatable("miscelanea.torre.montada"), true);
            cambio();
            return;
        }
        if (ahora - movido < 8) return;
        double lx = donde.x - worldPosition.getX() - 0.5, ly = donde.y - worldPosition.getY() - BASE, lz = donde.z - worldPosition.getZ() - 0.5;
        int nivel = (int) Math.floor(ly / ALTO);
        if (nivel < 0 || nivel >= niveles.size()) return;
        double eje = nivel % 2 == 0 ? lz : lx;
        int pieza = Math.max(0, Math.min(2, (int) Math.floor((eje + LARGO / 2) / ANCHO)));
        int top = niveles.size() - 1;
        if (nivel == top || (nivel == top - 1 && niveles.get(top) != 7)) {
            p.displayClientMessage(Component.translatable("miscelanea.torre.arriba"), true);
            return;
        }
        int m = niveles.get(nivel);
        if ((m & (1 << pieza)) == 0) {
            // la pieza que se ve más cerca de donde has tocado
            int mejor = -1;
            for (int k = 0; k < 3; k++) if ((m & (1 << k)) != 0 && (mejor < 0 || Math.abs(k - pieza) < Math.abs(mejor - pieza))) mejor = k;
            if (mejor < 0) return;
            pieza = mejor;
        }
        niveles.set(nivel, m & ~(1 << pieza));
        // a lo alto
        int arriba = niveles.get(top);
        if (arriba == 7) niveles.add(2);
        else niveles.set(top, arriba | (1 << ((arriba & 2) == 0 ? 1 : (arriba & 1) == 0 ? 0 : 2)));
        sacadas++;
        movido = ahora;
        riesgo = riesgoTotal();
        ultimo = p.getGameProfile().getName();
        if (level.random.nextFloat() < riesgo) {
            derrumbe = ahora;
            semilla = level.random.nextLong();
            level.playSound(null, worldPosition, ModSounds.TO_DERRUMBE.get(), SoundSource.BLOCKS, 1.2F, 1F);
            for (Player o : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(16)))
                o.displayClientMessage(Component.translatable("miscelanea.torre.caida", ultimo, sacadas).withStyle(ChatFormatting.RED), false);
        } else {
            level.playSound(null, worldPosition, ModSounds.TO_CLAC.get(), SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.3F);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "torre", sacadas);
            if (riesgo > 0.3F) p.displayClientMessage(Component.translatable("miscelanea.torre.tiembla").withStyle(ChatFormatting.GOLD), true);
        }
        cambio();
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putIntArray("niv", niveles.stream().mapToInt(Integer::intValue).toArray());
        t.putLong("der", derrumbe);
        t.putLong("mov", movido);
        t.putLong("sem", semilla);
        t.putFloat("riesgo", riesgo);
        t.putInt("sac", sacadas);
        t.putString("ult", ultimo);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        int[] n = t.getIntArray("niv");
        if (n.length > 0) {
            niveles.clear();
            for (int v : n) niveles.add(v);
        }
        derrumbe = t.contains("der") ? t.getLong("der") : -1;
        movido = t.getLong("mov");
        semilla = t.getLong("sem");
        riesgo = t.getFloat("riesgo");
        sacadas = t.getInt("sac");
        ultimo = t.getString("ult");
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
        return new AABB(worldPosition).inflate(2, 3, 2);
    }
}
