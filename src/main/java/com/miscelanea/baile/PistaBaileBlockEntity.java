package com.miscelanea.baile;

import com.miscelanea.ConfigServidor;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quién baila en la pista y qué canción suena (el servidor avisa a los de alrededor y cada cliente pone la música en la
 * pista). Mientras una pista suena, ninguna otra a menos de {@link ConfigServidor#baileRadioPistas()} bloques puede
 * empezar una canción (para que no se mezclen las músicas); eso lo decide solo el servidor.
 */
public class PistaBaileBlockEntity extends BlockEntity {
    public UUID bailarin;
    /** Gramola: los que bailan detrás del protagonista (bailarines de fondo, en formación). */
    public final List<UUID> extras = new ArrayList<>();
    public static final int MAX_EXTRAS = 4;
    /** Id de la canción que suena (ver {@link CancionesBaile}) o null. */
    public String cancion;
    /** Tic del mundo en el que empezó la canción (también lo sabe el cliente, para los que llegan a mitad). */
    public long inicio;
    /** Servidor: dificultad elegida para la canción en marcha (para saber cuántos puntos se pueden sacar). */
    int dificultad = 1;
    /** Última canción que ha sonado (para puntuar a los de fondo, que terminan un poco después que el protagonista). */
    private String ultima;
    private long ultimaIni;
    private int ultimaDif = 1;
    // cliente: luces de las flechas
    public final long[] luz = new long[4];

    public PistaBaileBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.PISTA_BAILE_BE.get(), p, s);
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    boolean encima(ServerPlayer sp) {
        return sp.position().distanceToSqr(Vec3.atBottomCenterOf(worldPosition)) < 2.5;
    }

    /** ¿Es una gramola (videoclip) en vez de una pista? */
    public boolean gramola() {
        return getBlockState().getBlock() instanceof GramolaBlock;
    }

    public boolean participa(ServerPlayer sp) {
        return sp.getUUID().equals(bailarin) || extras.contains(sp.getUUID());
    }

    /**
     * Sitio de cada bailarín delante de la gramola: 0 = el protagonista (en el centro del segundo bloque delante), 1..4 = los de fondo, un
     * poco más atrás y a los lados (así nunca se pisan ni se atraviesan, por muy juntos que estén al hacer clic).
     */
    public static Vec3 hueco(BlockPos g, net.minecraft.core.Direction f, int i) {
        net.minecraft.core.Direction r = f.getClockWise();
        double adelante = i == 0 ? 2.0 : 1.2, lado = switch (i) {
            case 1 -> 1.7;
            case 2 -> -1.7;
            case 3 -> 3.2;
            case 4 -> -3.2;
            default -> 0;
        };
        return Vec3.atBottomCenterOf(g).add(f.getStepX() * adelante + r.getStepX() * lado, 0, f.getStepZ() * adelante + r.getStepZ() * lado);
    }

    private boolean libre(Vec3 v) {
        BlockPos p = BlockPos.containing(v);
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
    }

    /** Pone al jugador en su sitio mirando hacia fuera de la gramola. */
    private boolean colocar(ServerPlayer sp, int i) {
        net.minecraft.core.Direction f = getBlockState().getValue(PistaBaileBlock.FACING);
        Vec3 v = hueco(worldPosition, f, i);
        if (!libre(v)) return false;
        sp.teleportTo(sp.serverLevel(), v.x, v.y, v.z, f.toYRot(), 0);
        sp.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    private void usarGramola(ServerPlayer sp) {
        if (bailarin == null || sp.getUUID().equals(bailarin)) {
            if (bailarin == null && !colocar(sp, 0)) {
                sp.displayClientMessage(Component.translatable("gramola.miscelanea.sin_sitio").withStyle(ChatFormatting.RED), true);
                return;
            }
            bailarin = sp.getUUID();
            if (cancion == null) cambio();
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BailePacket(BailePacket.ABRIR, worldPosition, sp.getId(), 0, 0));
            BlockPos otra = otraSonando();
            if (otra != null) avisarOtra(sp, otra);
            return;
        }
        if (extras.contains(sp.getUUID())) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BailePacket(BailePacket.ABRIR, worldPosition, sp.getId(), 1, 0));
            return;
        }
        // unirse como bailarín de fondo: solo entre canción y canción, y con sitio libre
        if (cancion != null) {
            sp.displayClientMessage(Component.translatable("gramola.miscelanea.espera").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (extras.size() >= MAX_EXTRAS) {
            sp.displayClientMessage(Component.translatable("gramola.miscelanea.llena").withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean puesto = false;
        for (int i = 1; i <= MAX_EXTRAS && !puesto; i++) {
            final int hu = i;
            boolean ocupado = false;
            for (UUID u : extras) {
                ServerPlayer o = level.getServer().getPlayerList().getPlayer(u);
                if (o != null && o.position().distanceToSqr(hueco(worldPosition, getBlockState().getValue(PistaBaileBlock.FACING), hu)) < 0.5) ocupado = true;
            }
            if (!ocupado) puesto = colocar(sp, i);
        }
        if (!puesto) {
            sp.displayClientMessage(Component.translatable("gramola.miscelanea.sin_sitio").withStyle(ChatFormatting.RED), true);
            return;
        }
        extras.add(sp.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BailePacket(BailePacket.ABRIR, worldPosition, sp.getId(), 1, 0));
        ServerPlayer jefe = level.getServer().getPlayerList().getPlayer(bailarin);
        if (jefe != null) jefe.displayClientMessage(Component.translatable("gramola.miscelanea.se_une", sp.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    public void usar(ServerPlayer sp) {
        if (gramola()) {
            usarGramola(sp);
            return;
        }
        if (bailarin != null && !bailarin.equals(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("baile.miscelanea.ocupada").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!encima(sp)) {
            sp.displayClientMessage(Component.translatable("baile.miscelanea.subete").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        bailarin = sp.getUUID();
        parar();
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BailePacket(BailePacket.ABRIR, worldPosition, sp.getId(), 0, 0));
        BlockPos otra = otraSonando();
        if (otra != null) avisarOtra(sp, otra);
    }

    /** Otra pista con canción a menos del radio de la configuración (o null). */
    BlockPos otraSonando() {
        if (level == null) return null;
        return FuentesMusica.otra(level, worldPosition, ConfigServidor.baileRadioPistas());
    }

    private void avisarOtra(ServerPlayer sp, BlockPos otra) {
        int d = (int) Math.round(Math.sqrt(otra.distSqr(worldPosition)));
        sp.displayClientMessage(Component.translatable("baile.miscelanea.otra_sonando", d).withStyle(ChatFormatting.GOLD), true);
    }

    void empezar(ServerPlayer sp, String id, int d) {
        CancionesBaile.Cancion c = CancionesBaile.de(id);
        if (!sp.getUUID().equals(bailarin) || c == null) return;
        // si ya suena otra pista cerca, no se puede empezar (se mezclarían las dos músicas)
        BlockPos otra = otraSonando();
        if (otra != null) {
            avisarOtra(sp, otra);
            return;
        }
        parar();
        cancion = c.id;
        dificultad = Math.max(0, Math.min(2, d));
        inicio = level.getGameTime();
        ultima = c.id;
        ultimaIni = inicio;
        ultimaDif = dificultad;
        FuentesMusica.poner(level, worldPosition, Long.MAX_VALUE);
        // la música la pone cada cliente en la pista (sonido con posición que se corta solo si la pista desaparece)
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.EMPIEZA, worldPosition, sp.getId(), 0, dificultad, c.id));
        // en la gramola, los de fondo empiezan a la vez (con la misma dificultad)
        for (UUID u : extras) {
            ServerPlayer o = level.getServer().getPlayerList().getPlayer(u);
            if (o != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                    new BailePacket(BailePacket.EMPIEZA, worldPosition, o.getId(), 1, dificultad, c.id));
        }
        // logros: a bailar (en la gramola, también los de fondo)
        java.util.List<ServerPlayer> bailan = new java.util.ArrayList<>(java.util.List.of(sp));
        if (gramola())
            for (UUID u : extras) {
                ServerPlayer o = level.getServer().getPlayerList().getPlayer(u);
                if (o != null) bailan.add(o);
            }
        for (ServerPlayer o : bailan) {
            com.miscelanea.logros.LogrosMiscelanea.dar(o, "baile");
            if (gramola()) {
                com.miscelanea.logros.LogrosMiscelanea.dar(o, "videoclip");
                if (c.id.endsWith("weapon_of_choice")) com.miscelanea.logros.LogrosMiscelanea.dar(o, "walken");
            }
        }
    }

    /**
     * Máximo de puntos que se pueden haber hecho en esta canción y dificultad con el tiempo que ha pasado desde que empezó
     * (todas perfectas con el combo subiendo). -1 si no hay canción en marcha.
     */
    int maxPuntos() {
        CancionesBaile.Cancion c = CancionesBaile.de(cancion);
        if (c == null || level == null) return -1;
        // tiempo de la canción en pasos de 1/60 s (3 por tic), con 2 s de margen por el retraso de la red
        long t = (level.getGameTime() - inicio) * 3 + 120;
        return c.maxPuntos(dificultad, t);
    }

    /** Lo mismo para un bailarín de fondo: cuenta la última canción aunque el protagonista ya haya terminado (hasta 15 s). */
    int maxPuntosExtra() {
        CancionesBaile.Cancion c = CancionesBaile.de(ultima);
        if (c == null || level == null) return -1;
        long tics = level.getGameTime() - ultimaIni;
        if (tics > (long) (c.duracion * 20) + 300) return -1;
        return c.maxPuntos(ultimaDif, tics * 3 + 120);
    }

    String ultima() {
        return ultima;
    }

    int ultimaDif() {
        return ultimaDif;
    }

    /** Un bailarín de fondo se va (solo él: la música sigue para los demás). */
    void salirExtra(UUID u) {
        if (!extras.remove(u) || level.getServer() == null) return;
        ServerPlayer o = level.getServer().getPlayerList().getPlayer(u);
        if (o != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.PARA, worldPosition, o.getId(), 0, 1));
    }

    void parar() {
        // el cliente ve en los datos de la pista que ya no hay canción y para la música de esta pista (solo de esta)
        cancion = null;
        if (level != null && !level.isClientSide) FuentesMusica.quitar(level, worldPosition);
    }

    void salir() {
        ServerPlayer sp = bailarin == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(bailarin);
        parar();
        for (UUID u : new ArrayList<>(extras)) salirExtra(u);
        if (sp != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.PARA, worldPosition, sp.getId(), 0, 0));
        bailarin = null;
        cambio();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) FuentesMusica.quitar(level, worldPosition);
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, PistaBaileBlockEntity be) {
        if (be.bailarin == null || l.getGameTime() % 10 != 0) return;
        ServerPlayer sp = l.getServer().getPlayerList().getPlayer(be.bailarin);
        double lejos = be.gramola() ? 36 : 9;
        for (UUID u : new ArrayList<>(be.extras)) {
            ServerPlayer o = l.getServer().getPlayerList().getPlayer(u);
            if (o == null || o.level() != l || o.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > lejos) be.salirExtra(u);
        }
        if (sp == null || sp.level() != l || sp.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > lejos) {
            be.salir();
            return;
        }
        // la canción se acaba sola aunque el cliente no avise (así la pista deja de bloquear a las de alrededor)
        CancionesBaile.Cancion c = CancionesBaile.de(be.cancion);
        if (be.cancion != null && (c == null || l.getGameTime() - be.inicio > (long) (c.duracion * 20) + 200)) {
            be.parar();
            be.cambio();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        if (bailarin != null) t.putUUID("b", bailarin);
        if (cancion != null) t.putString("c", cancion);
        t.putLong("i", inicio);
        return t;
    }

    @Override
    public void handleUpdateTag(CompoundTag t) {
        bailarin = t.hasUUID("b") ? t.getUUID("b") : null;
        cancion = t.contains("c") ? t.getString("c") : null;
        inicio = t.getLong("i");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag());
    }
}
