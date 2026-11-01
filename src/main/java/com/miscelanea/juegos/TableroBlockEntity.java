package com.miscelanea.juegos;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Partida de ajedrez o damas en un tablero del mundo. Modos: 0 ajedrez vs CPU, 1 ajedrez 2 jugadores, 2 damas vs CPU, 3 damas 2 jugadores.
 * El primero que toca una pieza juega con blancas; en 2 jugadores el segundo con negras.
 */
public class TableroBlockEntity extends BlockEntity {
    public static final int MODOS = 4;
    public static final double LADO = 1.5, ZONA = 1.5 * 108 / 128;   // tamaño del tablero y de la zona de casillas (en bloques)

    public int modo;
    public int[] estado = new int[0];
    public UUID blancas, negras;
    public String nombreB = "", nombreN = "";
    public int sel = -1;
    public int ultDe = -1, ultA = -1;
    public long ultT;
    /** 0 jugando, 1 ganan blancas, 2 ganan negras, 3 tablas. */
    public int resultado;
    public int sinCapturas;
    private long cpuEn;
    private final Random rnd = new Random();

    public TableroBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.TABLERO_BE.get(), p, s);
    }

    public boolean ajedrez() {
        return modo < 2;
    }

    public boolean contraCpu() {
        return modo % 2 == 0;
    }

    public Ajedrez partidaAjedrez() {
        Ajedrez a = new Ajedrez();
        if (estado.length >= 67) a.cargar(estado);
        else a.inicial();
        return a;
    }

    public Damas partidaDamas() {
        Damas d = new Damas();
        if (estado.length >= 66) d.cargar(estado);
        else d.inicial();
        return d;
    }

    public boolean turnoBlancas() {
        return ajedrez() ? partidaAjedrez().blancas : partidaDamas().blancas;
    }

    /** Movimientos legales desde una casilla (lista de casillas destino). */
    public java.util.List<Integer> destinos(int desde) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        List<Integer> ms = ajedrez() ? partidaAjedrez().legales() : partidaDamas().legales();
        for (int m : ms) if ((m & 63) == desde) out.add((m >> 6) & 63);
        return out;
    }

    public void reiniciar(int nuevoModo) {
        modo = Math.floorMod(nuevoModo, MODOS);
        if (ajedrez()) {
            Ajedrez a = new Ajedrez();
            a.inicial();
            estado = a.guardar();
        } else {
            Damas d = new Damas();
            d.inicial();
            estado = d.guardar();
        }
        blancas = negras = null;
        nombreB = nombreN = "";
        sel = ultDe = ultA = -1;
        resultado = 0;
        sinCapturas = 0;
        cpuEn = 0;
        cambiado();
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ jugar
    public void clic(ServerPlayer p, int sq) {
        if (estado.length == 0) reiniciar(modo);
        if (resultado != 0) {
            reiniciar(modo);
            p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.nueva"), true);
            return;
        }
        UUID u = p.getUUID();
        if (blancas == null) {
            blancas = u;
            nombreB = p.getGameProfile().getName();
            if (contraCpu()) nombreN = "CPU";
        } else if (!contraCpu() && negras == null && !u.equals(blancas)) {
            negras = u;
            nombreN = p.getGameProfile().getName();
        }
        boolean tb = turnoBlancas();
        boolean suyo = tb ? u.equals(blancas) : (!contraCpu() && u.equals(negras));
        if (!suyo) {
            p.displayClientMessage(Component.translatable(u.equals(blancas) || u.equals(negras) ? "msg.miscelanea.tablero.no_turno" : "msg.miscelanea.tablero.ocupado")
                    .withStyle(ChatFormatting.GRAY), true);
            cambiado();
            return;
        }
        int pieza = ajedrez() ? partidaAjedrez().t[sq] : partidaDamas().t[sq];
        boolean propia = pieza != 0 && (ajedrez() ? !Ajedrez.esNegra(pieza) == tb : Damas.esBlanca(pieza) == tb);
        if (!ajedrez() && partidaDamas().obligada >= 0) sel = partidaDamas().obligada;
        if (sel >= 0 && destinos(sel).contains(sq)) {
            mover(sel, sq);
            return;
        }
        if (propia && !destinos(sq).isEmpty() && (ajedrez() || partidaDamas().obligada < 0)) {
            sel = sq;
            level.playSound(null, worldPosition, SoundEvents.WOOD_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
        } else if (propia) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.sin_movimientos").withStyle(ChatFormatting.GRAY), true);
        } else sel = -1;
        cambiado();
    }

    private void mover(int de, int a) {
        int captura;
        boolean sigueTurno = false;
        if (ajedrez()) {
            Ajedrez aj = partidaAjedrez();
            int m = -1;
            for (int x : aj.legales()) if ((x & 63) == de && ((x >> 6) & 63) == a) m = x;
            if (m < 0) return;
            captura = aj.hacer(m);
            estado = aj.guardar();
        } else {
            Damas d = partidaDamas();
            int m = -1;
            for (int x : d.legales()) if ((x & 63) == de && ((x >> 6) & 63) == a) m = x;
            if (m < 0) return;
            boolean antes = d.blancas;
            int cap = d.hacer(m);
            captura = cap >= 0 ? 1 : 0;
            sigueTurno = d.blancas == antes;
            if (cap >= 0) particulas(cap);
            estado = d.guardar();
        }
        sinCapturas = captura != 0 ? 0 : sinCapturas + 1;
        if (captura != 0 && ajedrez()) particulas(a);
        ultDe = de;
        ultA = a;
        ultT = level.getGameTime();
        sel = sigueTurno ? a : -1;
        level.playSound(null, worldPosition, captura != 0 ? SoundEvents.ARMOR_EQUIP_GENERIC : SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F,
                captura != 0 ? 1.3F : 1.5F + rnd.nextFloat() * 0.2F);
        comprobarFin();
        if (resultado == 0 && contraCpu() && !turnoBlancas()) cpuEn = level.getGameTime() + (sigueTurno ? 10 : 18);
        cambiado();
    }

    private void comprobarFin() {
        boolean tb = turnoBlancas();
        boolean sinMov;
        boolean jaque = false;
        if (ajedrez()) {
            Ajedrez aj = partidaAjedrez();
            sinMov = aj.legales().isEmpty();
            jaque = aj.enJaque(tb);
            if (!sinMov && jaque) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 0.7F, 1.2F);
        } else sinMov = partidaDamas().legales().isEmpty();
        if (sinMov) resultado = (ajedrez() && !jaque) ? 3 : (tb ? 2 : 1);
        else if (sinCapturas >= (ajedrez() ? 100 : 50)) resultado = 3;
        if (resultado != 0) {
            level.playSound(null, worldPosition, resultado == 3 ? SoundEvents.NOTE_BLOCK_CHIME.get() : SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1.0F);
            String clave = resultado == 3 ? "msg.miscelanea.tablero.tablas" : (ajedrez() ? "msg.miscelanea.tablero.mate" : "msg.miscelanea.tablero.gana");
            Component m = Component.translatable(clave, resultado == 1 ? nombreB : nombreN).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer o : ((ServerLevel) level).getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(12)))
                o.displayClientMessage(m, false);
            ((ServerLevel) level).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    40, 0.4, 0.3, 0.4, 0.3);
        }
    }

    private void particulas(int sq) {
        Vec3 v = centroCasilla(sq).add(0, 0.15, 0);
        ItemParticleOption op = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(turnoBlancas() ? Items.QUARTZ : Items.COAL));
        ((ServerLevel) level).sendParticles(op, v.x, v.y, v.z, 10, 0.05, 0.05, 0.05, 0.08);
    }

    /** Centro de una casilla en coordenadas del mundo. La fila 0 (blancas) queda del lado de quien colocó el tablero. */
    public Vec3 centroCasilla(int sq) {
        Direction f = getBlockState().getValue(TableroBlock.FACING);
        Direction der = f.getClockWise();
        double s = ZONA / 8;
        double u = ((sq & 7) - 3.5) * s, v = ((sq >> 3) - 3.5) * s;
        return Vec3.atCenterOf(worldPosition).add(der.getStepX() * u + f.getStepX() * v, -0.5 + 1.5 / 16, der.getStepZ() * u + f.getStepZ() * v);
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, TableroBlockEntity be) {
        if (be.cpuEn > 0 && l.getGameTime() >= be.cpuEn && be.resultado == 0) {
            be.cpuEn = 0;
            if (be.turnoBlancas()) return;
            int m = be.ajedrez() ? be.partidaAjedrez().mejor(3, be.rnd) : be.partidaDamas().mejor(6, be.rnd);
            if (m >= 0) be.mover(m & 63, (m >> 6) & 63);
        }
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("modo", modo);
        t.putIntArray("estado", estado);
        if (blancas != null) t.putUUID("blancas", blancas);
        if (negras != null) t.putUUID("negras", negras);
        t.putString("nb", nombreB);
        t.putString("nn", nombreN);
        t.putInt("sel", sel);
        t.putInt("ud", ultDe);
        t.putInt("ua", ultA);
        t.putLong("ut", ultT);
        t.putInt("res", resultado);
        t.putInt("sc", sinCapturas);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        modo = t.getInt("modo");
        estado = t.getIntArray("estado");
        blancas = t.hasUUID("blancas") ? t.getUUID("blancas") : null;
        negras = t.hasUUID("negras") ? t.getUUID("negras") : null;
        nombreB = t.getString("nb");
        nombreN = t.getString("nn");
        sel = t.contains("sel") ? t.getInt("sel") : -1;
        ultDe = t.contains("ud") ? t.getInt("ud") : -1;
        ultA = t.contains("ua") ? t.getInt("ua") : -1;
        ultT = t.getLong("ut");
        resultado = t.getInt("res");
        sinCapturas = t.getInt("sc");
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
        return new AABB(worldPosition).inflate(0.5, 1.2, 0.5);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) Visibles.agregar(this);
        if (level != null && !level.isClientSide && estado.length == 0) reiniciar(modo);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Visibles.quitar(this);
    }

    /** Tableros cargados en el cliente (para apuntar a las casillas sin recorrer bloques). */
    public static final class Visibles {
        public static final java.util.Set<TableroBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

        static void agregar(TableroBlockEntity b) {
            SET.add(b);
        }

        static void quitar(TableroBlockEntity b) {
            SET.remove(b);
        }
    }
}
