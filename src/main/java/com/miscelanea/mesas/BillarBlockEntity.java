package com.miscelanea.mesas;

import com.miscelanea.network.ModNetwork;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Random;
import java.util.UUID;

/** Mesa de billar: dos jugadores (o uno contra la máquina) por turnos; el servidor mueve las bolas y lo cuenta a los que miran. */
public class BillarBlockEntity extends BlockEntity implements MesaGrandeBlock.MesaEntidad {
    public BillarPartida p = new BillarPartida();
    public final UUID[] jug = new UUID[2];
    public final String[] nombre = {"", ""};
    public boolean cpu;
    int esperaCpu;
    /** Ticks que lleva cada jugador lejos de la mesa o desconectado (a los 200 se le levanta). */
    private final int[] ausente = new int[2];
    final Random rnd = new Random();
    // cliente: posiciones del tic anterior para suavizar
    public final float[] antesX = new float[16], antesY = new float[16];
    public long ultimoFisica;

    public BillarBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlocks.BILLAR_BE.get(), pos, s);
        p.colocar(rnd);
    }

    public Direction facing() {
        return getBlockState().getValue(MesaGrandeBlock.FACING);
    }

    public boolean esCpu(int lado) {
        return lado == 1 && cpu;
    }

    public int lado(UUID u) {
        for (int i = 0; i < 2; i++) if (u.equals(jug[i])) return i;
        return -1;
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void aviso(ServerPlayer sp, String clave, Object... args) {
        sp.displayClientMessage(Component.translatable(clave, args).withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public void usar(ServerPlayer sp, Vec3 donde) {
        UUID u = sp.getUUID();
        int l = lado(u);
        if (p.fase == BillarPartida.FIN) {
            // cualquiera de los sentados puede empezar otra; si ya no queda nadie sentado, la mesa vuelve a estar libre
            if (l >= 0) {
                empezar();
                return;
            }
            if (jug[0] != null || jug[1] != null) {
                aviso(sp, "billar.miscelanea.ocupada");
                return;
            }
            reiniciar();
        }
        if (p.fase == BillarPartida.ESPERA) {
            if (l < 0 && jug[0] == null) {
                jug[0] = u;
                nombre[0] = sp.getGameProfile().getName();
                p.mensaje = "billar.miscelanea.msg.espera_rival";
                aviso(sp, "billar.miscelanea.unido");
            } else if (l < 0 && jug[1] == null) {
                jug[1] = u;
                nombre[1] = sp.getGameProfile().getName();
                cpu = false;
                empezar();
            } else if (l == 0 && sp.isShiftKeyDown()) {
                cpu = true;
                nombre[1] = "CPU";
                empezar();
            } else if (l == 0) aviso(sp, "billar.miscelanea.como_cpu");
            cambio();
            return;
        }
        if (l < 0) aviso(sp, "billar.miscelanea.ocupada");
        else if (sp.isShiftKeyDown() && p.fase == BillarPartida.APUNTA) {
            // abandonar (cualquiera de los dos)
            soltar(l);
            aviso(sp, "billar.miscelanea.reiniciada");
        }
    }

    /**
     * Levanta a un jugador. Si se va el segundo en plena partida, sigue la máquina en su lugar; si se va el primero y
     * queda un rival humano, este pasa a esperar un nuevo rival; si no queda nadie, la mesa queda libre.
     */
    void soltar(int l) {
        int otro = 1 - l;
        ausente[l] = 0;
        if (jug[otro] == null) {
            reiniciar();
            return;
        }
        if (l == 1 && (p.fase == BillarPartida.APUNTA || p.fase == BillarPartida.MUEVE)) {
            jug[1] = null;
            cpu = true;
            nombre[1] = "CPU";
            esperaCpu = 0;
            cambio();
            return;
        }
        UUID u = jug[otro];
        String n = nombre[otro];
        reiniciar();
        jug[0] = u;
        nombre[0] = n;
        p.mensaje = "billar.miscelanea.msg.espera_rival";
        cambio();
    }

    void empezar() {
        p = new BillarPartida();
        p.colocar(rnd);
        p.fase = BillarPartida.APUNTA;
        p.turno = 0;
        p.mensaje = "billar.miscelanea.msg.saque";
        esperaCpu = 0;
        if (level != null) level.playSound(null, worldPosition, ModSounds.BI_CHOQUE.get(), SoundSource.BLOCKS, 0.6F, 0.8F);
        cambio();
    }

    void reiniciar() {
        p = new BillarPartida();
        p.colocar(rnd);
        jug[0] = jug[1] = null;
        nombre[0] = nombre[1] = "";
        cpu = false;
        cambio();
    }

    public void tiro(ServerPlayer sp, float angulo, float fuerza) {
        if (p.fase != BillarPartida.APUNTA || lado(sp.getUUID()) != p.turno || esCpu(p.turno)) return;
        if (!Float.isFinite(angulo) || !Float.isFinite(fuerza)) return;
        tirar(angulo, fuerza);
    }

    void tirar(float angulo, float fuerza) {
        if (!p.tirar(angulo, fuerza)) return;
        if (level != null) level.playSound(null, worldPosition, ModSounds.BI_TACO.get(), SoundSource.BLOCKS, 0.4F + fuerza * 0.6F, 0.9F + fuerza * 0.2F);
        cambio();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, BillarBlockEntity be) {
        // quien se va de la mesa (o se desconecta) más de 200 ticks deja su sitio
        for (int i = 0; i < 2; i++) {
            if (be.jug[i] == null) {
                be.ausente[i] = 0;
                continue;
            }
            ServerPlayer sp = l.getServer().getPlayerList().getPlayer(be.jug[i]);
            boolean cerca = sp != null && sp.level() == l && sp.distanceToSqr(Vec3.atCenterOf(pos)) <= 12 * 12;
            be.ausente[i] = cerca ? 0 : be.ausente[i] + 1;
            if (be.ausente[i] > 200) be.soltar(i);
        }
        BillarPartida p = be.p;
        if (p.fase == BillarPartida.MUEVE) {
            boolean parado = p.tic();
            // sonidos de los choques de este tic
            if (p.choques > 0) l.playSound(null, pos, ModSounds.BI_CHOQUE.get(), SoundSource.BLOCKS, Math.min(1F, 0.2F + p.choqueFuerte * 0.25F), 0.9F + be.rnd.nextFloat() * 0.3F);
            if (p.bandas > 0) l.playSound(null, pos, ModSounds.BI_BANDA.get(), SoundSource.BLOCKS, 0.4F, 1F);
            if (p.troneras > 0) l.playSound(null, pos, ModSounds.BI_TRONERA.get(), SoundSource.BLOCKS, 0.8F, 1F);
            p.choques = p.bandas = p.troneras = 0;
            p.choqueFuerte = 0;
            if (parado) be.cambio();
            else ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> l.getChunkAt(pos)), new MesaFisicaPacket(pos, p.posiciones()));
        } else if (p.fase == BillarPartida.APUNTA && be.esCpu(p.turno)) {
            if (++be.esperaCpu > 50) {
                be.esperaCpu = 0;
                float[] t = p.tiroCpu(be.rnd, 0.8F);
                be.tirar(t[0], t[1]);
            }
        }
    }

    // ------------------------------------------------------------------ cliente
    @Override
    public void fisica(float[] d) {
        for (int i = 0; i < 16; i++) {
            antesX[i] = p.x[i];
            antesY[i] = p.y[i];
        }
        p.ponerPosiciones(d);
        if (d.length > 48) p.fase = (int) d[48];
        ultimoFisica = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ guardar y sincronizar
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        CompoundTag b = new CompoundTag();
        float[] pos = p.posiciones();
        int[] bits = new int[pos.length];
        for (int i = 0; i < pos.length; i++) bits[i] = Float.floatToIntBits(pos[i]);
        b.putIntArray("pos", bits);
        b.putInt("fase", p.fase == BillarPartida.MUEVE ? BillarPartida.APUNTA : p.fase);
        b.putInt("turno", p.turno);
        b.putInt("g0", p.grupo[0]);
        b.putInt("g1", p.grupo[1]);
        b.putInt("ganador", p.ganador);
        b.putString("msg", p.mensaje);
        t.put("billar", b);
        for (int i = 0; i < 2; i++) {
            if (jug[i] != null) t.putUUID("j" + i, jug[i]);
            t.putString("n" + i, nombre[i]);
        }
        t.putBoolean("cpu", cpu);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        if (t.contains("billar")) {
            CompoundTag b = t.getCompound("billar");
            int[] bits = b.getIntArray("pos");
            float[] pos = new float[bits.length];
            for (int i = 0; i < bits.length; i++) pos[i] = Float.intBitsToFloat(bits[i]);
            boolean moviaCliente = level != null && level.isClientSide && p.fase == BillarPartida.MUEVE && System.currentTimeMillis() - ultimoFisica < 300;
            if (!moviaCliente || b.getInt("fase") != BillarPartida.MUEVE) {
                p.ponerPosiciones(pos);
                for (int i = 0; i < 16; i++) {
                    antesX[i] = p.x[i];
                    antesY[i] = p.y[i];
                }
            }
            p.fase = b.getInt("fase");
            p.turno = b.getInt("turno");
            p.grupo[0] = b.getInt("g0");
            p.grupo[1] = b.getInt("g1");
            p.ganador = b.getInt("ganador");
            p.mensaje = b.getString("msg");
        }
        for (int i = 0; i < 2; i++) {
            jug[i] = t.hasUUID("j" + i) ? t.getUUID("j" + i) : null;
            nombre[i] = t.getString("n" + i);
        }
        cpu = t.getBoolean("cpu");
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
        return new AABB(worldPosition).inflate(5, 3, 5);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) Visibles.BILLAR.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Visibles.BILLAR.remove(this);
    }
}
