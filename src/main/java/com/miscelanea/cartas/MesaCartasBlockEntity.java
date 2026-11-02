package com.miscelanea.cartas;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Partida de brisca a dos. El servidor lleva la baraja y las manos; a cada jugador solo le llega su mano (los demás
 * ven la mesa, el mazo y el triunfo). Gana quien pase de 60 puntos (de 120).
 */
public class MesaCartasBlockEntity extends BlockEntity {
    public static final int ESPERA = 0, JUGANDO = 1, FIN = 2, PAUSA_BAZA = 30, PAUSA_CPU = 22;
    public final UUID[] jugador = new UUID[2];
    public final String[] nombre = {"", ""};
    public final boolean[] cpu = new boolean[2];
    public final List<Integer> mazo = new ArrayList<>();
    @SuppressWarnings("unchecked")
    public final List<Integer>[] mano = new List[]{new ArrayList<Integer>(), new ArrayList<Integer>()};
    public final int[] mesa = {-1, -1}, puntos = new int[2], bazas = new int[2];
    public int triunfo = -1, turno, salida, estado = ESPERA, ultimaGana = -1;
    public long resolver = -1, cpuDesde;
    /** En el cliente: cuántas cartas tiene cada uno en la mano (la mano no se manda a los demás). */
    public final int[] enMano = new int[2];

    public MesaCartasBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.MESA_CARTAS_BE.get(), p, s);
    }

    public Direction facing() {
        return getBlockState().getValue(MesaCartasBlock.FACING);
    }

    public int asiento(Player p) {
        for (int i = 0; i < 2; i++) if (p.getUUID().equals(jugador[i])) return i;
        return -1;
    }

    void sentar(ServerPlayer p) {
        int a = asiento(p);
        if (a < 0) {
            for (int i = 0; i < 2 && a < 0; i++) {
                boolean libre = jugador[i] == null && !cpu[i];
                boolean ausente = jugador[i] != null && (level.getPlayerByUUID(jugador[i]) == null
                        || level.getPlayerByUUID(jugador[i]).distanceToSqr(worldPosition.getCenter()) > 24 * 24);
                if (libre || ausente || (cpu[i] && estado != JUGANDO)) {
                    a = i;
                    jugador[i] = p.getUUID();
                    nombre[i] = p.getGameProfile().getName();
                    cpu[i] = false;
                    if (estado == FIN) estado = ESPERA;
                }
            }
            if (a < 0) {
                p.displayClientMessage(Component.translatable("miscelanea.cartas.llena"), true);
                return;
            }
            if (estado == ESPERA && ocupados() == 2) empezar();
            cambio();
        }
        enviar(p, true);
    }

    int ocupados() {
        int n = 0;
        for (int i = 0; i < 2; i++) if (jugador[i] != null || cpu[i]) n++;
        return n;
    }

    void contraCpu(ServerPlayer p) {
        int a = asiento(p);
        if (a < 0 || estado == JUGANDO) return;
        int otro = 1 - a;
        if (jugador[otro] == null || estado == FIN) {
            jugador[otro] = null;
            cpu[otro] = true;
            nombre[otro] = Component.translatable("miscelanea.cartas.maquina").getString();
        }
        empezar();
        cambio();
    }

    void nueva(ServerPlayer p) {
        if (asiento(p) < 0 || estado == JUGANDO) return;
        if (ocupados() == 2) empezar();
        cambio();
    }

    void levantarse(ServerPlayer p) {
        int a = asiento(p);
        if (a < 0) return;
        jugador[a] = null;
        nombre[a] = "";
        if (estado == JUGANDO) {
            estado = FIN;
            for (Player o : cercanos()) o.displayClientMessage(Component.translatable("miscelanea.cartas.abandona", p.getGameProfile().getName()), false);
        }
        if (jugador[1 - a] == null) {
            cpu[0] = cpu[1] = false;
            estado = ESPERA;
        }
        cambio();
    }

    private void empezar() {
        mazo.clear();
        for (int c = 0; c < 40; c++) mazo.add(c);
        Collections.shuffle(mazo, new java.util.Random(level.random.nextLong()));
        for (int i = 0; i < 2; i++) {
            mano[i].clear();
            puntos[i] = bazas[i] = 0;
            mesa[i] = -1;
        }
        // el triunfo es la última carta del mazo (se ve boca arriba debajo)
        triunfo = mazo.get(mazo.size() - 1);
        for (int k = 0; k < 3; k++) for (int i = 0; i < 2; i++) mano[i].add(mazo.remove(0));
        salida = turno = level.random.nextInt(2);
        estado = JUGANDO;
        resolver = -1;
        ultimaGana = -1;
        cpuDesde = level.getGameTime();
        level.playSound(null, worldPosition, ModSounds.CA_BARAJAR.get(), SoundSource.BLOCKS, 1F, 1F);
    }

    void jugar(ServerPlayer p, int idx) {
        int a = asiento(p);
        if (a < 0) return;
        echar(a, idx);
    }

    private void echar(int a, int idx) {
        if (estado != JUGANDO || turno != a || resolver >= 0 || idx < 0 || idx >= mano[a].size() || mesa[a] >= 0) return;
        mesa[a] = mano[a].remove(idx);
        level.playSound(null, worldPosition, ModSounds.CA_CARTA.get(), SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
        if (mesa[1 - a] >= 0) resolver = level.getGameTime();
        else turno = 1 - a;
        cpuDesde = level.getGameTime();
        cambio();
    }

    public static void tick(Level l, BlockPos pos, BlockState s, MesaCartasBlockEntity be) {
        if (be.estado != JUGANDO) return;
        long ahora = l.getGameTime();
        if (be.resolver >= 0) {
            if (ahora - be.resolver >= PAUSA_BAZA) be.resolverBaza();
            return;
        }
        if (be.cpu[be.turno] && ahora - be.cpuDesde >= PAUSA_CPU) {
            int t = be.turno;
            be.echar(t, Baraja.elegirCpu(be.mano[t], be.mesa[1 - t], Baraja.palo(be.triunfo)));
        }
    }

    private void resolverBaza() {
        int primera = mesa[salida], segunda = mesa[1 - salida];
        int gana = Baraja.ganaSegunda(primera, segunda, Baraja.palo(triunfo)) ? 1 - salida : salida;
        puntos[gana] += Baraja.puntos(primera) + Baraja.puntos(segunda);
        bazas[gana]++;
        mesa[0] = mesa[1] = -1;
        ultimaGana = gana;
        // roba primero quien gana
        for (int k = 0; k < 2; k++) {
            int quien = k == 0 ? gana : 1 - gana;
            if (!mazo.isEmpty()) mano[quien].add(mazo.remove(0));
        }
        salida = turno = gana;
        resolver = -1;
        cpuDesde = level.getGameTime();
        level.playSound(null, worldPosition, ModSounds.CA_BAZA.get(), SoundSource.BLOCKS, 0.8F, 1F);
        if (mano[0].isEmpty() && mano[1].isEmpty()) terminar();
        cambio();
    }

    private void terminar() {
        estado = FIN;
        String txt;
        if (puntos[0] == puntos[1]) txt = Component.translatable("miscelanea.cartas.empate").getString();
        else {
            int g = puntos[0] > puntos[1] ? 0 : 1;
            txt = Component.translatable("miscelanea.cartas.gana", nombre[g], puntos[g], puntos[1 - g]).getString();
        }
        for (Player o : cercanos()) o.displayClientMessage(Component.literal(txt).withStyle(ChatFormatting.GOLD), false);
        level.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 0.8F, 1F);
    }

    List<Player> cercanos() {
        return level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(16));
    }

    void cambio() {
        setChanged();
        if (level == null) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        for (int i = 0; i < 2; i++)
            if (jugador[i] != null && level.getPlayerByUUID(jugador[i]) instanceof ServerPlayer sp) enviar(sp, false);
    }

    void enviar(ServerPlayer p, boolean abrir) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), CartasPacket.estado(this, asiento(p), abrir));
    }

    // ------------------------------------------------------------------ guardar
    private void guardarPublico(CompoundTag t) {
        for (int i = 0; i < 2; i++) {
            t.putString("nom" + i, nombre[i]);
            t.putBoolean("cpu" + i, cpu[i]);
            t.putInt("mano" + i, mano[i].size());
        }
        t.putIntArray("mesa", mesa);
        t.putIntArray("pts", puntos);
        t.putIntArray("bazas", bazas);
        t.putInt("triunfo", triunfo);
        t.putInt("mazo", mazo.size());
        t.putInt("turno", turno);
        t.putInt("salida", salida);
        t.putInt("estado", estado);
        t.putInt("ult", ultimaGana);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        guardarPublico(t);
        for (int i = 0; i < 2; i++) {
            if (jugador[i] != null) t.putUUID("jug" + i, jugador[i]);
            t.putIntArray("m" + i, mano[i].stream().mapToInt(Integer::intValue).toArray());
        }
        t.putIntArray("baraja", mazo.stream().mapToInt(Integer::intValue).toArray());
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        for (int i = 0; i < 2; i++) {
            nombre[i] = t.getString("nom" + i);
            cpu[i] = t.getBoolean("cpu" + i);
            enMano[i] = t.getInt("mano" + i);
            jugador[i] = t.hasUUID("jug" + i) ? t.getUUID("jug" + i) : null;
            if (t.contains("m" + i)) {
                mano[i].clear();
                for (int c : t.getIntArray("m" + i)) mano[i].add(c);
            }
        }
        int[] m = t.getIntArray("mesa");
        if (m.length == 2) System.arraycopy(m, 0, mesa, 0, 2);
        int[] pt = t.getIntArray("pts");
        if (pt.length == 2) System.arraycopy(pt, 0, puntos, 0, 2);
        int[] bz = t.getIntArray("bazas");
        if (bz.length == 2) System.arraycopy(bz, 0, bazas, 0, 2);
        triunfo = t.contains("triunfo") ? t.getInt("triunfo") : -1;
        turno = t.getInt("turno");
        salida = t.getInt("salida");
        estado = t.getInt("estado");
        ultimaGana = t.contains("ult") ? t.getInt("ult") : -1;
        if (t.contains("baraja")) {
            mazo.clear();
            for (int c : t.getIntArray("baraja")) mazo.add(c);
        } else if (level != null && level.isClientSide) {
            mazo.clear();
            for (int k = 0; k < t.getInt("mazo"); k++) mazo.add(-1);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        guardarPublico(t);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    public String nombreTurno() {
        return estado == JUGANDO ? nombre[turno] : null;
    }
}
