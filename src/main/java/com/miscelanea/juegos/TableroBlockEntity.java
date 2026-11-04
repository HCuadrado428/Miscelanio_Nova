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
    public static final int MODOS = 6;
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
    /** Ajedrez: claves de las posiciones desde la última captura (tablas por triple repetición). */
    private final java.util.List<Integer> repeticiones = new java.util.ArrayList<>();
    /** Servidor: último tic en que uno de los jugadores hizo algo (para dejar reiniciar a otros si abandonan). */
    private long actividad;
    /** Tras un minuto sin que los jugadores toquen nada, cualquiera puede cambiar o reiniciar la partida. */
    public static final int ABANDONO_TICS = 20 * 60;
    private final Random rnd = new Random();
    /** Reloj de turno: si el humano de turno no juega (o se va), la CPU mueve por él. */
    private final com.miscelanea.mesas.Turnos.Vigia vigia = new com.miscelanea.mesas.Turnos.Vigia();
    /** Lado (blancas) para el que está pensando la CPU. */
    private boolean pensandoBlancas;

    public TableroBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.TABLERO_BE.get(), p, s);
    }

    public boolean ajedrez() {
        return modo < 2;
    }

    /** 0 ajedrez, 1 damas, 2 reversi. */
    public int juego() {
        return Math.min(2, modo / 2);
    }

    public boolean reversi() {
        return juego() == 2;
    }

    public Reversi partidaReversi() {
        Reversi r = new Reversi();
        if (estado.length >= 68) r.cargar(estado);
        else r.inicial();
        return r;
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

    // ---- vista de solo lectura: se reconstruye únicamente cuando cambia el estado (antes se rehacía varias veces por fotograma)
    private int[] vistaDe;
    private int vistaJuego = -1;
    private Ajedrez vistaA;
    private Damas vistaD;
    private Reversi vistaR;
    private List<Integer> vistaLegales;

    private void vista() {
        if (vistaDe == estado && vistaJuego == juego()) return;
        vistaDe = estado;
        vistaJuego = juego();
        vistaLegales = null;
        vistaA = vistaJuego == 0 ? partidaAjedrez() : null;
        vistaD = vistaJuego == 1 ? partidaDamas() : null;
        vistaR = vistaJuego == 2 ? partidaReversi() : null;
    }

    /** Partida de ajedrez para consultar (no modificar). */
    public Ajedrez verAjedrez() {
        vista();
        return vistaA != null ? vistaA : partidaAjedrez();
    }

    /** Partida de damas para consultar (no modificar). */
    public Damas verDamas() {
        vista();
        return vistaD != null ? vistaD : partidaDamas();
    }

    /** Partida de reversi para consultar (no modificar). */
    public Reversi verReversi() {
        vista();
        return vistaR != null ? vistaR : partidaReversi();
    }

    /** Tablero de piezas del juego actual (solo lectura). */
    public int[] piezas() {
        return switch (juego()) {
            case 0 -> verAjedrez().t;
            case 1 -> verDamas().t;
            default -> verReversi().t;
        };
    }

    public List<Integer> legales() {
        vista();
        if (vistaLegales == null) vistaLegales = switch (vistaJuego) {
            case 0 -> vistaA.legales();
            case 1 -> vistaD.legales();
            default -> vistaR.legales();
        };
        return vistaLegales;
    }

    public boolean turnoBlancas() {
        return switch (juego()) {
            case 0 -> verAjedrez().blancas;
            case 1 -> verDamas().blancas;
            default -> verReversi().blancas;
        };
    }

    /** Movimientos legales desde una casilla (lista de casillas destino). */
    public java.util.List<Integer> destinos(int desde) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int m : legales()) if ((m & 63) == desde) out.add((m >> 6) & 63);
        return out;
    }

    /** Casillas de origen de las jugadas que llegan a una casilla. */
    public java.util.List<Integer> origenes(int hasta) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int m : legales()) if (((m >> 6) & 63) == hasta && !out.contains(m & 63)) out.add(m & 63);
        return out;
    }

    public void reiniciar(int nuevoModo) {
        modo = Math.floorMod(nuevoModo, MODOS);
        if (ajedrez()) {
            Ajedrez a = new Ajedrez();
            a.inicial();
            estado = a.guardar();
        } else if (reversi()) {
            Reversi r = new Reversi();
            r.inicial();
            estado = r.guardar();
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
        repeticiones.clear();
        if (ajedrez()) repeticiones.add(partidaAjedrez().clave());
        vigia.limpiar();
        cambiado();
    }

    /** Revancha con los mismos jugadores; en dos jugadores se cambian los colores. */
    private void revancha(ServerPlayer quien) {
        UUID b = blancas, n = negras;
        String nb = nombreB, nn = nombreN;
        reiniciar(modo);
        if (contraCpu() || n == null) {
            blancas = b;
            nombreB = nb;
            if (contraCpu()) nombreN = "CPU";
        } else {
            blancas = n;
            nombreB = nn;
            negras = b;
            nombreN = nb;
        }
        com.miscelanea.mesas.Turnos.avisar(level, worldPosition, "msg.miscelanea.mesa.revancha", quien.getGameProfile().getName());
        cambiado();
    }

    /** Humano al que le toca mover (null si es la CPU o el sitio está libre). */
    private UUID deTurno() {
        if (resultado != 0 || blancas == null || estado.length == 0) return null;
        if (turnoBlancas()) return blancas;
        return contraCpu() ? null : negras;
    }

    /** Identificador del juego actual para el marcador de victorias. */
    public String idJuego() {
        return switch (juego()) {
            case 0 -> Victorias.AJEDREZ;
            case 1 -> Victorias.DAMAS;
            default -> Victorias.REVERSI;
        };
    }

    /**
     * ¿Puede este jugador reiniciar o cambiar de juego? Sí si la partida terminó, si nadie ha empezado a jugar, si es uno de
     * los jugadores, o si los jugadores llevan un minuto sin tocar nada (partida abandonada).
     */
    public boolean puedeReiniciar(ServerPlayer p) {
        if (resultado != 0 || blancas == null) return true;
        UUID u = p.getUUID();
        if (u.equals(blancas) || u.equals(negras)) return true;
        return level == null || level.getGameTime() - Math.max(actividad, ultT) > ABANDONO_TICS;
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ jugar
    public void clic(ServerPlayer p, int sq) {
        if (estado.length == 0) reiniciar(modo);
        if (resultado != 0) {
            if (p.getUUID().equals(blancas) || p.getUUID().equals(negras)) {
                revancha(p);
                return;
            }
            reiniciar(modo);
            p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.nueva"), true);
            return;
        }
        UUID u = p.getUUID();
        vigia.despertar(u);
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
        if (u.equals(blancas) || u.equals(negras)) actividad = level.getGameTime();
        if (!suyo) {
            p.displayClientMessage(Component.translatable(u.equals(blancas) || u.equals(negras) ? "msg.miscelanea.tablero.no_turno" : "msg.miscelanea.tablero.ocupado")
                    .withStyle(ChatFormatting.GRAY), true);
            cambiado();
            return;
        }
        com.miscelanea.juegos.Gestos.hacer(p, com.miscelanea.juegos.Gestos.ALCANZAR, centroCasilla(sq));
        int pieza = piezas()[sq];
        boolean propia = !reversi() && pieza != 0 && (ajedrez() ? !Ajedrez.esNegra(pieza) == tb : Damas.esBlanca(pieza) == tb);
        if (juego() == 1 && verDamas().obligada >= 0) sel = verDamas().obligada;
        if (sel >= 0 && destinos(sel).contains(sq)) {
            mover(sel, sq);
            return;
        }
        if (!propia) {
            // clic fácil: tocar directamente el destino si solo una pieza puede ir allí
            List<Integer> os = origenes(sq);
            if (os.size() == 1) {
                mover(os.get(0), sq);
                return;
            }
            if (os.size() > 1) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.varias").withStyle(ChatFormatting.GRAY), true);
                sel = -1;
                cambiado();
                return;
            }
        }
        if (reversi()) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.reversi_no").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (propia && !destinos(sq).isEmpty() && (ajedrez() || verDamas().obligada < 0)) {
            sel = sq;
            level.playSound(null, worldPosition, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
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
            // una captura no tiene vuelta atrás: las posiciones de antes ya no se pueden repetir
            if (captura != 0) repeticiones.clear();
            if (repeticiones.size() < 400) repeticiones.add(aj.clave());
        } else if (reversi()) {
            Reversi r = partidaReversi();
            if (!r.legales().contains(de | (a << 6))) return;
            boolean antes = r.blancas;
            r.hacer(de | (a << 6));
            captura = 0;
            sigueTurno = r.blancas == antes;
            estado = r.guardar();
            if (sigueTurno) {
                Component m = Component.translatable("msg.miscelanea.tablero.pasa", antes ? nombreN : nombreB).withStyle(ChatFormatting.GRAY);
                for (ServerPlayer o : ((ServerLevel) level).getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(10)))
                    o.displayClientMessage(m, true);
            }
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
        sel = sigueTurno && !reversi() ? a : -1;
        if (reversi()) level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
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
        if (reversi()) {
            Reversi r = partidaReversi();
            if (r.terminado()) {
                int b = r.cuenta(1), n = r.cuenta(3);
                resultado = b > n ? 1 : n > b ? 2 : 3;
            }
            sinMov = false;
        } else if (ajedrez()) {
            Ajedrez aj = partidaAjedrez();
            sinMov = aj.legales().isEmpty();
            jaque = aj.enJaque(tb);
            if (!sinMov && jaque) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 0.7F, 1.2F);
            // tablas por triple repetición de la posición o porque a nadie le queda material para dar mate
            if (!sinMov) {
                int clave = aj.clave(), veces = 0;
                for (int k : repeticiones) if (k == clave) veces++;
                if (veces >= 3 || aj.materialInsuficiente()) resultado = 3;
            }
        } else sinMov = partidaDamas().legales().isEmpty();
        if (resultado != 0) {
            // ya decidido (tablas del ajedrez)
        } else if (sinMov) resultado = (ajedrez() && !jaque) ? 3 : (tb ? 2 : 1);
        else if (!reversi() && sinCapturas >= (ajedrez() ? 100 : 50)) resultado = 3;
        if (resultado != 0) {
            level.playSound(null, worldPosition, resultado == 3 ? SoundEvents.NOTE_BLOCK_CHIME.get() : SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.9F, 1.0F);
            String clave = resultado == 3 ? "msg.miscelanea.tablero.tablas" : (ajedrez() ? "msg.miscelanea.tablero.mate" : "msg.miscelanea.tablero.gana");
            Component m = Component.translatable(clave, resultado == 1 ? nombreB : nombreN).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer o : ((ServerLevel) level).getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(12)))
                o.displayClientMessage(m, false);
            ((ServerLevel) level).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    40, 0.4, 0.3, 0.4, 0.3);
            if (resultado != 3) {
                UUID ne = contraCpu() ? null : negras;
                com.miscelanea.juegos.Partidas.terminada(level, worldPosition, idJuego(), resultado == 1 ? blancas : ne, resultado == 1 ? ne : blancas);
            }
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

    /** La CPU piensa en un hilo aparte para no congelar el servidor; la jugada se aplica cuando termina. */
    private java.util.concurrent.CompletableFuture<Integer> pensando;
    private int[] pensandoSobre;

    public static void tickServidor(Level l, BlockPos pos, BlockState s, TableroBlockEntity be) {
        if (be.pensando != null) {
            if (!be.pensando.isDone()) return;
            int m;
            try {
                m = be.pensando.getNow(-1);
            } catch (Exception ex) {
                m = -1;
            }
            boolean vigente = be.pensandoSobre == be.estado && be.resultado == 0 && be.turnoBlancas() == be.pensandoBlancas;
            be.pensando = null;
            if (!vigente) return;
            if (m < 0 && !be.legales().isEmpty()) m = be.legales().get(0);
            if (m >= 0) be.mover(m & 63, (m >> 6) & 63);
            return;
        }
        // reloj de turno: si el humano de turno no mueve a tiempo (o se ha ido), la CPU mueve por él
        UUID humano = be.deTurno();
        boolean porEl = humano != null && be.vigia.vigilar(l, pos, humano, be.turnoBlancas() ? be.nombreB : be.nombreN, System.identityHashCode(be.estado));
        if (humano == null) be.vigia.vigilar(l, pos, null, "", 0);
        if (porEl && be.cpuEn == 0) be.cpuEn = l.getGameTime() + 10;
        if (be.cpuEn > 0 && l.getGameTime() >= be.cpuEn && be.resultado == 0) {
            be.cpuEn = 0;
            boolean cpuToca = (!be.turnoBlancas() && be.contraCpu()) || porEl;
            if (!cpuToca) return;
            be.pensandoBlancas = be.turnoBlancas();
            final int[] copia = be.estado.clone();
            final int juego = be.juego();
            final long semilla = be.rnd.nextLong();
            be.pensandoSobre = be.estado;
            be.pensando = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                Random r = new Random(semilla);
                if (juego == 0) {
                    Ajedrez a = new Ajedrez();
                    a.cargar(copia);
                    return a.mejor(3, r);
                }
                if (juego == 2) {
                    Reversi rv = new Reversi();
                    rv.cargar(copia);
                    return rv.mejor(5, r);
                }
                Damas d = new Damas();
                d.cargar(copia);
                return d.mejor(6, r);
            }, net.minecraft.Util.backgroundExecutor());
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
        t.putIntArray("rep", repeticiones.stream().mapToInt(Integer::intValue).toArray());
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
        repeticiones.clear();
        for (int k : t.getIntArray("rep")) repeticiones.add(k);
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
