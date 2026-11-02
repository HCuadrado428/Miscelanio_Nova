package com.miscelanea.dardos;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * Partida de 301: cada turno son 3 dardos y se resta lo que hagas; hay que llegar justo a 0 (si te pasas, el turno no
 * cuenta). Sectores, dobles, triples y diana como en una de verdad.
 */
public class DianaDardosBlockEntity extends BlockEntity {
    public static final int[] SECTOR = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    /** Radios relativos (1 = borde de la zona que puntúa). */
    public static final float BULL = 0.07F, BULL25 = 0.15F, TRIPLE0 = 0.54F, TRIPLE1 = 0.62F, DOBLE0 = 0.9F, RADIO = 0.4F;
    public int quedan = 301, turnoInicio = 301, dardos;
    public float[] du = new float[3], dv = new float[3];
    public int[] dp = new int[3];
    public String nombre = "", ultimo = "";

    public DianaDardosBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.DIANA_DARDOS_BE.get(), p, s);
    }

    public Direction facing() {
        return getBlockState().getValue(DianaDardosBlock.FACING);
    }

    void nueva(Player p) {
        quedan = turnoInicio = 301;
        dardos = 0;
        nombre = p.getGameProfile().getName();
        ultimo = "";
        p.displayClientMessage(Component.translatable("miscelanea.dardos.nueva"), true);
        cambio();
    }

    /** Puntos y texto ("T20", "D5", "BULL"...) de un punto (u, v) en la cara, en bloques desde el centro. */
    public static Object[] puntuar(double u, double v) {
        double r = Math.hypot(u, v) / RADIO;
        if (r > 1) return new Object[]{0, "FUERA"};
        if (r < BULL) return new Object[]{50, "BULL"};
        if (r < BULL25) return new Object[]{25, "25"};
        double ang = Math.toDegrees(Math.atan2(u, v));
        int s = SECTOR[Math.floorMod((int) Math.floor((ang + 9) / 18), 20)];
        if (r >= TRIPLE0 && r < TRIPLE1) return new Object[]{s * 3, "T" + s};
        if (r >= DOBLE0) return new Object[]{s * 2, "D" + s};
        return new Object[]{s, String.valueOf(s)};
    }

    /** Un dardo clavado en la diana en el punto del mundo dado. */
    public void clavar(Player p, Vec3 donde) {
        Direction f = facing(), u = f.getCounterClockWise();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        double lu = (donde.x - c.x) * u.getStepX() + (donde.z - c.z) * u.getStepZ(), lv = donde.y - c.y;
        Object[] r = puntuar(lu, lv);
        int pts = (Integer) r[0];
        if (dardos >= 3) {
            dardos = 0;
            turnoInicio = quedan;
        }
        if (p != null && !p.getGameProfile().getName().equals(nombre)) {
            nombre = p.getGameProfile().getName();
            quedan = turnoInicio = 301;
            dardos = 0;
        }
        du[dardos] = (float) lu;
        dv[dardos] = (float) lv;
        dp[dardos] = pts;
        dardos++;
        ultimo = (String) r[1];
        level.playSound(null, worldPosition, ModSounds.DA_CLAVA.get(), SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
        int nuevo = quedan - pts;
        boolean finTurno = dardos == 3 || nuevo == 0;
        if (nuevo < 0 || nuevo == 1) {
            quedan = turnoInicio;
            dardos = 3;
            if (p != null) p.displayClientMessage(Component.translatable("miscelanea.dardos.pasado").withStyle(ChatFormatting.RED), true);
        } else if (nuevo == 0 || finTurno) {
            int turno = turnoInicio - nuevo;
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "dardos", turno);
            if (nuevo != 0) {
                quedan = nuevo;
                if (p != null) p.displayClientMessage(Component.translatable("miscelanea.dardos.tiro", r[1], pts, quedan), true);
                cambio();
                return;
            }
            quedan = 0;
            level.playSound(null, worldPosition, ModSounds.FE_PREMIO_GORDO.get(), SoundSource.BLOCKS, 1F, 1F);
            for (Player o : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(24)))
                o.displayClientMessage(Component.translatable("miscelanea.dardos.gana", nombre).withStyle(ChatFormatting.GOLD), false);
            quedan = turnoInicio = 301;
            dardos = 3;
        } else {
            quedan = nuevo;
            if (p != null) p.displayClientMessage(Component.translatable("miscelanea.dardos.tiro", r[1], pts, quedan), true);
            if (pts >= 50) level.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1.2F);
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
        t.putInt("q", quedan);
        t.putInt("ti", turnoInicio);
        t.putInt("n", dardos);
        for (int i = 0; i < 3; i++) {
            t.putFloat("u" + i, du[i]);
            t.putFloat("v" + i, dv[i]);
            t.putInt("p" + i, dp[i]);
        }
        t.putString("nom", nombre);
        t.putString("ult", ultimo);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        quedan = t.contains("q") ? t.getInt("q") : 301;
        turnoInicio = t.contains("ti") ? t.getInt("ti") : 301;
        dardos = t.getInt("n");
        for (int i = 0; i < 3; i++) {
            du[i] = t.getFloat("u" + i);
            dv[i] = t.getFloat("v" + i);
            dp[i] = t.getInt("p" + i);
        }
        nombre = t.getString("nom");
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
        return new AABB(worldPosition).inflate(1);
    }
}
