package com.miscelanea.imperio;

import com.miscelanea.mesas.Turnos;
import com.miscelanea.registry.ModBlocks;
import com.miscelanea.registry.ModSounds;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Una partida del juego de propiedades sobre un tablero colocado en el mundo. */
public class ImperioBlockEntity extends BlockEntity {
    public Partida p = new Partida();
    private final Random rnd = new Random();
    private int esperaCpu;
    /** Juega por quien se va de la mesa o no juega; votos para terminar antes de tiempo (no se guardan). */
    private final Turnos.Vigia vigia = new Turnos.Vigia(2F);
    private final Turnos.Votacion votos = new Turnos.Votacion();

    public ImperioBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.IMPERIO_BE.get(), pos, s);
    }

    /** Coordenadas de textura (0..512) → mundo (sobre el tablero). */
    public Vec3 mundo(double tx, double ty) {
        Direction f = getBlockState().getValue(ImperioBlock.FACING), der = f.getClockWise();
        double u = (tx / ImperioGeo.N - 0.5) * ImperioGeo.LADO, v = (ty / ImperioGeo.N - 0.5) * ImperioGeo.LADO;
        return Vec3.atCenterOf(worldPosition).add(der.getStepX() * u - f.getStepX() * v, -0.5 + 1.5 / 16, der.getStepZ() * u - f.getStepZ() * v);
    }

    /** Mundo → coordenadas de textura. */
    public double[] textura(Vec3 h) {
        Direction f = getBlockState().getValue(ImperioBlock.FACING), der = f.getClockWise();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double u = (h.x - cx) * der.getStepX() + (h.z - cz) * der.getStepZ();
        double v = -((h.x - cx) * f.getStepX() + (h.z - cz) * f.getStepZ());
        return new double[]{(u / ImperioGeo.LADO + 0.5) * ImperioGeo.N, (v / ImperioGeo.LADO + 0.5) * ImperioGeo.N};
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private int indice(ServerPlayer pl) {
        for (int i = 0; i < p.js.size(); i++) if (pl.getUUID().equals(p.js.get(i).uuid)) return i;
        return -1;
    }

    private void sonar(net.minecraft.sounds.SoundEvent e, float tono) {
        level.playSound(null, worldPosition, e, SoundSource.BLOCKS, 0.8F, tono);
    }

    public void clic(ServerPlayer pl, double tx, double ty) {
        long ahora = level.getGameTime();
        int yo = indice(pl);
        if (yo >= 0) vigia.despertar(pl.getUUID());
        ImperioBotones.Boton b = ImperioBotones.en(p, tx, ty);
        int c = ImperioGeo.casillaEn(tx, ty);
        boolean miTurno = yo >= 0 && yo == p.turno && p.fase != Partida.LOBBY;
        Vec3 punto = mundo(tx, ty);
        if (b == null) {
            if (c >= 0 && miTurno && Casillas.comprable(c) && p.fase != Partida.TIRAR) {
                p.sel = p.sel == c ? -1 : c;
                sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.6F);
                cambiado();
            } else if (miTurno && p.fase == Partida.TIRAR && (c < 0 || !Casillas.comprable(c) || p.dueno[c] != yo)) {
                // clic fácil: cualquier sitio del tablero tira los dados
                p.tirar(rnd, ahora);
                sonar(SoundEvents.BONE_BLOCK_HIT, 1.4F);
                com.miscelanea.juegos.Gestos.hacer(pl, com.miscelanea.juegos.Gestos.DADOS, punto);
                cambiado();
            } else if (c >= 0 && miTurno && Casillas.comprable(c)) {
                p.sel = p.sel == c ? -1 : c;
                sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.6F);
                cambiado();
            }
            return;
        }
        boolean hecho = true;
        switch (b.id()) {
            case ImperioBotones.UNIRSE -> hecho = p.unir(pl.getUUID(), pl.getGameProfile().getName());
            case ImperioBotones.CPU -> hecho = yo >= 0 && p.anadirCpu();
            case ImperioBotones.RONDAS -> p.cambiarLimite();
            case ImperioBotones.VELOCIDAD -> {
                if (yo >= 0 || p.fase == Partida.LOBBY) p.cambiarVelocidad();
                else hecho = false;
            }
            case ImperioBotones.EMPEZAR -> hecho = yo >= 0 && p.empezar(rnd);
            case ImperioBotones.REVANCHA -> {
                if (yo < 0) hecho = false;
                else {
                    p = p.revancha(rnd);
                    vigia.limpiar();
                    votos.limpiar();
                    Turnos.avisar(level, worldPosition, "msg.miscelanea.mesa.revancha", pl.getGameProfile().getName());
                }
            }
            case ImperioBotones.NUEVA -> {
                Partida n = new Partida();
                n.limite = p.limite;
                p = n;
                vigia.limpiar();
                votos.limpiar();
            }
            case ImperioBotones.TERMINAR -> {
                // acabar y contar patrimonios: hace falta que estén de acuerdo todos los humanos que siguen en la mesa
                if (yo < 0 || p.js.get(yo).fuera) hecho = false;
                else {
                    int faltan = votos.votar(level, worldPosition, pl.getUUID(), p.humanos());
                    if (faltan == 0) p.terminarPorRondas();
                    else Turnos.avisar(level, worldPosition, Turnos.AVISO_VOTO, pl.getGameProfile().getName(), faltan);
                }
            }
            default -> {
                if (!miTurno) {
                    pl.displayClientMessage(Component.translatable("msg.miscelanea.imperio.no_turno").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                switch (b.id()) {
                    case ImperioBotones.TIRAR -> {
                        p.tirar(rnd, ahora);
                        sonar(SoundEvents.BONE_BLOCK_HIT, 1.4F);
                        com.miscelanea.juegos.Gestos.hacer(pl, com.miscelanea.juegos.Gestos.DADOS, punto);
                        hecho = false;
                    }
                    case ImperioBotones.PAGAR_CARCEL -> p.pagarCarcel();
                    case ImperioBotones.CARTA_CARCEL -> p.usarCarta();
                    case ImperioBotones.COMPRAR -> {
                        p.comprar();
                        sonar(SoundEvents.VILLAGER_YES, 1.0F);
                    }
                    case ImperioBotones.NO_COMPRAR -> p.noComprar();
                    case ImperioBotones.FIN_TURNO -> p.terminarTurno();
                    case ImperioBotones.BANCARROTA -> p.bancarrota();
                    case ImperioBotones.CONSTRUIR -> {
                        p.construir(p.sel);
                        sonar(SoundEvents.WOOD_PLACE, 1.2F);
                    }
                    case ImperioBotones.VENDER -> p.vender(yo, p.sel);
                    case ImperioBotones.HIPOTECAR -> p.hipotecar(yo, p.sel);
                    case ImperioBotones.DESHIPOTECAR -> p.deshipotecar(yo, p.sel);
                    default -> hecho = false;
                }
            }
        }
        if (hecho) {
            sonar(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.2F);
            com.miscelanea.juegos.Gestos.hacer(pl, com.miscelanea.juegos.Gestos.ALCANZAR, punto);
        }
        cambiado();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, ImperioBlockEntity be) {
        Partida p = be.p;
        long ahora = l.getGameTime();
        int antes = p.fase;
        String cartaAntes = p.carta;
        long tCartaAntes = p.tCarta;
        boolean cambio = p.tick(ahora, be.rnd);
        if (p.fase != Partida.MOVER && p.actual() != null && p.fase != Partida.LOBBY && p.fase != Partida.FIN) {
            Partida.Jugador a = p.actual();
            // si el humano de turno se ha ido (o no juega) la CPU juega por él
            boolean porEl = !a.cpu() && be.vigia.vigilar(l, pos, a.uuid, a.nombre, p.tDados * 64 + p.turno * 8L + p.fase);
            if (a.cpu() || porEl) {
                if (++be.esperaCpu >= p.esperaCpu()) {
                    be.esperaCpu = 0;
                    cambio |= p.jugarPor(be.rnd, ahora);
                }
            }
        }
        if (cambio) {
            if (p.tCarta != tCartaAntes) be.sonar(SoundEvents.BOOK_PAGE_TURN, 1.0F);
            if (antes == Partida.MOVER && p.fase != Partida.MOVER) be.sonar(SoundEvents.WOOD_PLACE, 1.5F);
            if (p.fase == Partida.FIN && antes != Partida.FIN) {
                be.sonar(SoundEvents.PLAYER_LEVELUP, 1.0F);
                for (ServerPlayer o : ((ServerLevel) l).getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12)))
                    o.displayClientMessage(Component.translatable("msg.miscelanea.imperio.fin").withStyle(ChatFormatting.GOLD), false);
                int g = p.ganador();
                com.miscelanea.juegos.Partidas.terminada(l, pos, com.miscelanea.juegos.Victorias.IMPERIO, g >= 0 ? p.js.get(g).uuid : null,
                        p.humanos().toArray(new java.util.UUID[0]));
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
        p = t.contains("partida") ? Partida.cargar(t.getCompound("partida")) : new Partida();
    }

    /** A los clientes no se les manda el orden de los mazos de cartas. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.put("partida", p.guardarPublico());
        return t;
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
        public static final java.util.Set<ImperioBlockEntity> SET = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    }
}
