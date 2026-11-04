package com.miscelanea.cuatro;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Partida de cuatro en raya. Modos: 0 contra CPU fácil, 1 contra CPU difícil, 2 dos jugadores.
 * Quien suelta primero juega con rojas; en dos jugadores, el segundo con amarillas.
 */
public class CuatroBlockEntity extends BlockEntity {
    public static final int MODOS = 3;
    /** Geometría del bastidor en unidades de modelo (16 = 1 bloque). */
    public static final double CELDA = 3.2, X0 = -3.2, Y0 = 2.4;

    public Cuatro j = new Cuatro();
    public int modo;
    public UUID rojo, amarillo;
    public String nombreR = "", nombreA = "";
    public int ultCol = -1, ultFila = -1;
    public long ultT;
    public int[] linea;
    /** 0 jugando, 1 ganan rojas, 2 ganan amarillas, 3 tablas. */
    public int resultado;
    private long cpuEn;
    private CompletableFuture<Integer> pensando;
    private int jugadasAlPensar, ladoAlPensar;
    private final Random rnd = new Random();
    /** Reloj de turno: si el humano de turno no juega (o se va), la CPU juega por él. */
    private final com.miscelanea.mesas.Turnos.Vigia vigia = new com.miscelanea.mesas.Turnos.Vigia();

    public CuatroBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.CUATRO_BE.get(), p, s);
    }

    public boolean contraCpu() {
        return modo < 2;
    }

    public void reiniciar(int nuevoModo) {
        modo = Math.floorMod(nuevoModo, MODOS);
        j = new Cuatro();
        rojo = amarillo = null;
        nombreR = nombreA = "";
        ultCol = ultFila = -1;
        linea = null;
        resultado = 0;
        cpuEn = 0;
        pensando = null;
        vigia.limpiar();
        cambiado();
    }

    /** Revancha con los mismos jugadores; en dos jugadores se cambian los colores (empieza el otro). */
    private void revancha(ServerPlayer quien) {
        UUID r = rojo, a = amarillo;
        String nr = nombreR, na = nombreA;
        reiniciar(modo);
        if (contraCpu()) {
            rojo = r;
            nombreR = nr;
            nombreA = "CPU";
        } else {
            rojo = a;
            nombreR = na;
            amarillo = r;
            nombreA = nr;
            if (rojo == null) {
                rojo = amarillo;
                nombreR = nombreA;
                amarillo = null;
                nombreA = "";
            }
        }
        com.miscelanea.mesas.Turnos.avisar(level, worldPosition, "msg.miscelanea.mesa.revancha", quien.getGameProfile().getName());
        cambiado();
    }

    /** Jugador humano al que le toca (null si es la CPU o el sitio está libre). */
    private UUID deTurno() {
        if (resultado != 0 || rojo == null) return null;
        if (j.turno == 1) return rojo;
        return contraCpu() ? null : amarillo;
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Ticks sin jugadas tras los que cualquiera puede reiniciar una partida ajena (60 s). */
    public static final int INACTIVO = 1200;

    /** Solo los participantes pueden reiniciar; los demás, si no hay partida o lleva un rato parada. */
    public boolean puedeReiniciar(net.minecraft.world.entity.player.Player p) {
        if (rojo == null) return true;
        UUID u = p.getUUID();
        if (u.equals(rojo) || u.equals(amarillo)) return true;
        return level != null && level.getGameTime() - ultT >= INACTIVO;
    }

    public void clic(ServerPlayer p, int col) {
        if (resultado != 0) {
            if (p.getUUID().equals(rojo) || p.getUUID().equals(amarillo)) {
                revancha(p);
                return;
            }
            if (!puedeReiniciar(p)) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.ocupado").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            int m = modo;
            reiniciar(m);
            p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.nueva"), true);
            return;
        }
        UUID u = p.getUUID();
        vigia.despertar(u);
        if (rojo == null) {
            rojo = u;
            nombreR = p.getGameProfile().getName();
            if (contraCpu()) nombreA = "CPU";
        } else if (!contraCpu() && amarillo == null && !u.equals(rojo)) {
            amarillo = u;
            nombreA = p.getGameProfile().getName();
        }
        boolean suyo = j.turno == 1 ? u.equals(rojo) : (!contraCpu() && u.equals(amarillo));
        if (!suyo || pensando != null) {
            p.displayClientMessage(Component.translatable(u.equals(rojo) || u.equals(amarillo) ? "msg.miscelanea.tablero.no_turno" : "msg.miscelanea.tablero.ocupado")
                    .withStyle(ChatFormatting.GRAY), true);
            cambiado();
            return;
        }
        if (!j.puede(col)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.cuatro.llena").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        com.miscelanea.juegos.Gestos.hacer(p, com.miscelanea.juegos.Gestos.ALCANZAR, mundo(X0 + CELDA * (col + 0.5), Y0 + CELDA * 6.3));
        soltar(col);
    }

    private void soltar(int col) {
        int f = j.soltar(col);
        if (f < 0) return;
        ultCol = col;
        ultFila = f;
        ultT = level.getGameTime();
        level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.6F + rnd.nextFloat() * 0.2F);
        linea = j.linea();
        if (linea != null || j.lleno()) {
            resultado = linea != null ? j.t[linea[0]] : 3;
            level.playSound(null, worldPosition, resultado == 3 ? SoundEvents.NOTE_BLOCK_CHIME.get() : SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1F);
            String n = resultado == 1 ? nombreR : nombreA;
            Component m = resultado == 3 ? Component.translatable("msg.miscelanea.tablero.tablas")
                    : Component.translatable("msg.miscelanea.cuatro.gana", n);
            for (ServerPlayer o : ((ServerLevel) level).getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(12)))
                o.displayClientMessage(m.copy().withStyle(ChatFormatting.GOLD), false);
            Vec3 c = Vec3.atCenterOf(worldPosition);
            ((ServerLevel) level).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 0.4, c.z, 40, 0.4, 0.4, 0.4, 0.3);
            if (resultado != 3) {
                UUID am = contraCpu() ? null : amarillo;
                com.miscelanea.juegos.Partidas.terminada(level, worldPosition, com.miscelanea.juegos.Victorias.CUATRO,
                        resultado == 1 ? rojo : am, resultado == 1 ? am : rojo);
            }
        } else if (contraCpu() && j.turno == 2) cpuEn = level.getGameTime() + 14;
        cambiado();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, CuatroBlockEntity be) {
        if (be.pensando != null) {
            if (!be.pensando.isDone()) return;
            int c;
            try {
                c = be.pensando.getNow(-1);
            } catch (Exception e) {
                c = -1;
            }
            be.pensando = null;
            if (be.resultado != 0 || be.j.turno != be.ladoAlPensar || be.j.jugadas != be.jugadasAlPensar) return;
            if (!be.j.puede(c)) for (int k = 0; k < Cuatro.COLS; k++) if (be.j.puede(k)) {
                c = k;
                break;
            }
            be.soltar(c);
            return;
        }
        // cpuEn y pensando no se guardan: si tras recargar le toca a la CPU, se reprograma
        if (be.cpuEn == 0 && be.contraCpu() && be.j.turno == 2 && be.resultado == 0 && be.rojo != null)
            be.cpuEn = l.getGameTime() + 14;
        // reloj de turno: si el humano de turno no juega a tiempo (o se ha ido), la CPU suelta por él
        UUID humano = be.deTurno();
        boolean porEl = humano != null && be.vigia.vigilar(l, pos, humano, be.j.turno == 1 ? be.nombreR : be.nombreA, be.j.jugadas);
        if (humano == null) be.vigia.vigilar(l, pos, null, "", 0);
        if (porEl && be.cpuEn == 0) be.cpuEn = l.getGameTime() + 14;
        else if (!porEl && !(be.j.turno == 2 && be.contraCpu())) be.cpuEn = 0;
        if (be.cpuEn > 0 && l.getGameTime() >= be.cpuEn && be.resultado == 0 && ((be.j.turno == 2 && be.contraCpu()) || porEl)) {
            be.cpuEn = 0;
            be.ladoAlPensar = be.j.turno;
            Cuatro copia = new Cuatro();
            copia.cargar(be.j.guardar());
            long semilla = be.rnd.nextLong();
            boolean dificil = be.modo == 1;
            be.jugadasAlPensar = be.j.jugadas;
            be.pensando = CompletableFuture.supplyAsync(() -> {
                Random r = new Random(semilla);
                if (!dificil && r.nextInt(4) == 0) {
                    int c;
                    do c = r.nextInt(Cuatro.COLS);
                    while (!copia.puede(c));
                    return c;
                }
                return copia.mejor(dificil ? 8 : 2, r);
            }, net.minecraft.Util.backgroundExecutor());
        }
    }

    /** Punto del mundo de una celda (centro del bastidor en profundidad). */
    public Vec3 mundo(double x, double y) {
        Direction f = getBlockState().getValue(CuatroBlock.FACING), der = f.getClockWise();
        Vec3 c = Vec3.atBottomCenterOf(worldPosition);
        return c.add(der.getStepX() * (x - 8) / 16, y / 16, der.getStepZ() * (x - 8) / 16);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("modo", modo);
        t.putIntArray("j", j.guardar());
        if (rojo != null) t.putUUID("rojo", rojo);
        if (amarillo != null) t.putUUID("amarillo", amarillo);
        t.putString("nr", nombreR);
        t.putString("na", nombreA);
        t.putIntArray("ult", new int[]{ultCol, ultFila, resultado});
        t.putLong("ut", ultT);
        if (linea != null) t.putIntArray("linea", linea);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        modo = t.getInt("modo");
        j = new Cuatro();
        j.cargar(t.getIntArray("j"));
        rojo = t.hasUUID("rojo") ? t.getUUID("rojo") : null;
        amarillo = t.hasUUID("amarillo") ? t.getUUID("amarillo") : null;
        nombreR = t.getString("nr");
        nombreA = t.getString("na");
        int[] u = t.getIntArray("ult");
        if (u.length == 3) {
            ultCol = u[0];
            ultFila = u[1];
            resultado = u[2];
        }
        ultT = t.getLong("ut");
        linea = t.contains("linea") ? t.getIntArray("linea") : null;
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
        return new AABB(worldPosition).inflate(0.6, 1.5, 0.6);
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
        public static final java.util.Set<CuatroBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    }
}
