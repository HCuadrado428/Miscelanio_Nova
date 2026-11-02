package com.miscelanea.feria;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Estado de una máquina alta. Coordenadas locales como en la caseta de tiro: x a lo ancho, z hacia el fondo.
 * <p>Gancho: con una ficha mueves la garra 20 s (WASD) y la sueltas (espacio). Baja, cierra, sube y lleva lo que haya
 * cogido a la trampilla... si no se le cae antes.
 * <p>Tragaperras: tres rodillos que paran uno detrás de otro; el resultado se decide al echar la ficha.
 * <p>Gachapón: giras la manivela y sale una cápsula sorpresa.
 */
public class MaquinaAltaBlockEntity extends BlockEntity {
    // ---- gancho
    public static final int PREMIOS = 7, MOVER = 400, BAJA = 22, CIERRA = 10, SUBE = 22, ABRE = 12;
    public static final float LIMITE = 0.33F, VEL_TRASLADO = 0.02F, TRAMPILLA_X = 0.28F, TRAMPILLA_Z = -0.28F;
    public static final float SUELO = 0.95F, ARRIBA = 1.72F;
    // ---- tragaperras: cereza, limón, campana, BAR, siete
    public static final int[] PESO = {5, 5, 3, 2, 1};
    public static final int[] PARA = {30, 42, 54};
    // ---- comunes
    @Nullable public UUID jugador;
    public boolean activo;
    public int fase;
    public long inicio, inicioBajada;
    public float gx, gz, bajaX, bajaZ;
    public int agarrado = -1, caeT = -1;
    public int[] pc = new int[PREMIOS];
    public float[] px = new float[PREMIOS], pz = new float[PREMIOS];
    public int[] rodillo = new int[3];
    public int colorCapsula;
    private boolean iniciado;

    public MaquinaAltaBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.MAQUINA_ALTA_BE.get(), p, s);
    }

    public int tipo() {
        return getBlockState().getBlock() instanceof MaquinaAltaBlock b ? b.tipo : 0;
    }

    public Direction facing() {
        return getBlockState().getValue(MaquinaAltaBlock.FACING);
    }

    public Vec3 mundo(double x, double y, double z) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(worldPosition.getX() + 0.5 + x * c + z * s, worldPosition.getY() + y, worldPosition.getZ() + 0.5 - x * s + z * c);
    }

    private void prepararPremios(RandomSource r) {
        for (int k = 0; k < PREMIOS; k++) nuevoPremio(k, r);
        iniciado = true;
    }

    private void nuevoPremio(int k, RandomSource r) {
        pc[k] = r.nextInt(4);
        for (int intento = 0; intento < 20; intento++) {
            float x = (r.nextFloat() * 2 - 1) * LIMITE, z = (r.nextFloat() * 2 - 1) * LIMITE;
            if (Math.hypot(x - TRAMPILLA_X, z - TRAMPILLA_Z) < 0.2) continue;
            boolean libre = true;
            for (int j = 0; j < PREMIOS; j++)
                if (j != k && pc[j] >= 0 && Math.hypot(x - px[j], z - pz[j]) < 0.16) libre = false;
            px[k] = x;
            pz[k] = z;
            if (libre) return;
        }
    }

    boolean ficha(Player p, ItemStack st) {
        if (!st.is(ModItems.FICHA.get())) {
            p.displayClientMessage(Component.translatable("miscelanea.maquina.ficha"), true);
            return false;
        }
        if (!p.getAbilities().instabuild) st.shrink(1);
        level.playSound(null, worldPosition, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1F);
        return true;
    }

    void usar(Player p, ItemStack st) {
        long ahora = level.getGameTime();
        RandomSource r = level.random;
        if (!iniciado && tipo() == MaquinaAltaBlock.GANCHO) prepararPremios(r);
        if (activo) {
            if (tipo() == MaquinaAltaBlock.GANCHO && fase == 1 && p.getUUID().equals(jugador) && p instanceof ServerPlayer sp)
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new GanchoPacket(GanchoPacket.ABRIR, worldPosition, 0, 0));
            return;
        }
        if (!ficha(p, st)) return;
        jugador = p.getUUID();
        activo = true;
        inicio = ahora;
        switch (tipo()) {
            case MaquinaAltaBlock.GANCHO -> {
                fase = 1;
                gx = TRAMPILLA_X;
                gz = TRAMPILLA_Z;
                agarrado = -1;
                caeT = -1;
                if (p instanceof ServerPlayer sp)
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new GanchoPacket(GanchoPacket.ABRIR, worldPosition, 0, 0));
                level.playSound(null, worldPosition, ModSounds.FE_GANCHO_MUSICA.get(), SoundSource.BLOCKS, 0.6F, 1F);
            }
            case MaquinaAltaBlock.TRAGAPERRAS -> {
                for (int i = 0; i < 3; i++) rodillo[i] = sorteo(r);
                level.playSound(null, worldPosition, ModSounds.FE_RODILLOS.get(), SoundSource.BLOCKS, 0.8F, 1F);
            }
            default -> {
                colorCapsula = r.nextInt(6);
                level.playSound(null, worldPosition, ModSounds.FE_MANIVELA.get(), SoundSource.BLOCKS, 0.9F, 1F);
            }
        }
        cambio();
    }

    static int sorteo(RandomSource r) {
        int total = 0;
        for (int w : PESO) total += w;
        int x = r.nextInt(total);
        for (int i = 0; i < PESO.length; i++) {
            x -= PESO[i];
            if (x < 0) return i;
        }
        return 0;
    }

    /** Fichas que paga una tirada. */
    public static int premio(int[] r) {
        if (r[0] == r[1] && r[1] == r[2]) return new int[]{5, 5, 8, 12, 25}[r[0]];
        int cerezas = 0;
        for (int v : r) if (v == 0) cerezas++;
        return cerezas >= 2 ? 2 : r[0] == 0 ? 1 : 0;
    }

    // ------------------------------------------------------------------ gancho
    void moverGarra(ServerPlayer p, float x, float z) {
        if (tipo() != MaquinaAltaBlock.GANCHO || !activo || fase != 1 || !p.getUUID().equals(jugador)) return;
        gx = Math.max(-LIMITE, Math.min(LIMITE, x));
        gz = Math.max(-LIMITE, Math.min(LIMITE, z));
        cambio();
    }

    void soltarGarra(ServerPlayer p) {
        if (tipo() != MaquinaAltaBlock.GANCHO || !activo || fase != 1 || p != null && !p.getUUID().equals(jugador)) return;
        fase = 2;
        inicioBajada = level.getGameTime();
        bajaX = gx;
        bajaZ = gz;
        level.playSound(null, worldPosition, ModSounds.FE_GANCHO.get(), SoundSource.BLOCKS, 0.8F, 1F);
        cambio();
    }

    public int traslado() {
        return (int) Math.ceil(Math.hypot(bajaX - TRAMPILLA_X, bajaZ - TRAMPILLA_Z) / VEL_TRASLADO);
    }

    public int duraBajada() {
        return BAJA + CIERRA + SUBE + traslado() + ABRE;
    }

    /** Garra en el instante t (tics desde que se soltó): {x, y, z, apertura 0..1}. */
    public float[] garra(float t) {
        if (fase != 2) return new float[]{gx, ARRIBA, gz, 1};
        float x = bajaX, z = bajaZ, y = ARRIBA, ab = 1;
        if (t < BAJA) y = ARRIBA - (ARRIBA - SUELO - 0.08F) * t / BAJA;
        else if (t < BAJA + CIERRA) {
            y = SUELO + 0.08F;
            ab = 1 - (t - BAJA) / CIERRA;
        } else if (t < BAJA + CIERRA + SUBE) {
            y = SUELO + 0.08F + (ARRIBA - SUELO - 0.08F) * (t - BAJA - CIERRA) / SUBE;
            ab = 0;
        } else {
            int tr = traslado();
            float k = Math.min(1, (t - BAJA - CIERRA - SUBE) / Math.max(1, tr));
            x = bajaX + (TRAMPILLA_X - bajaX) * k;
            z = bajaZ + (TRAMPILLA_Z - bajaZ) * k;
            ab = t < BAJA + CIERRA + SUBE + tr ? 0 : Math.min(1, (t - BAJA - CIERRA - SUBE - tr) / 5F);
        }
        return new float[]{x, y, z, ab};
    }

    private void tickGancho(ServerLevel l, long ahora, RandomSource r) {
        if (fase == 1) {
            if (ahora - inicio > MOVER) soltarGarra(null);
            return;
        }
        int t = (int) (ahora - inicioBajada);
        if (t == BAJA + CIERRA) {
            // ¿ha cogido algo?
            int mejor = -1;
            double cerca = 0.11;
            for (int k = 0; k < PREMIOS; k++) {
                if (pc[k] < 0) continue;
                double d = Math.hypot(px[k] - bajaX, pz[k] - bajaZ);
                if (d < cerca) {
                    cerca = d;
                    mejor = k;
                }
            }
            if (mejor >= 0 && r.nextFloat() < 0.6F) {
                agarrado = mejor;
                int fin = BAJA + CIERRA + SUBE + traslado();
                caeT = r.nextFloat() < 0.35F ? BAJA + CIERRA + 6 + r.nextInt(Math.max(1, fin - BAJA - CIERRA - 6)) : -1;
            }
            l.playSound(null, worldPosition, ModSounds.FE_GANCHO.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
            cambio();
        }
        if (agarrado >= 0 && caeT >= 0 && t == caeT) {
            float[] g = garra(t);
            px[agarrado] = Math.max(-LIMITE, Math.min(LIMITE, g[0]));
            pz[agarrado] = Math.max(-LIMITE, Math.min(LIMITE, g[2]));
            agarrado = -1;
            Player p = jugador != null ? l.getPlayerByUUID(jugador) : null;
            if (p != null) p.displayClientMessage(Component.translatable("miscelanea.gancho.casi"), true);
            l.playSound(null, worldPosition, SoundEvents.WOOL_FALL, SoundSource.BLOCKS, 1F, 1F);
            cambio();
        }
        if (t >= duraBajada() - ABRE + 4 && agarrado >= 0) {
            ItemStack st = new ItemStack(ModItems.PELUCHE.get());
            CompoundTag est = new CompoundTag();
            est.putString("color", String.valueOf(pc[agarrado]));
            st.getOrCreateTag().put("BlockStateTag", est);
            soltar(st, 0.3);
            pc[agarrado] = -1;
            agarrado = -1;
            l.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1F);
            Player p = jugador != null ? l.getPlayerByUUID(jugador) : null;
            if (p != null) p.displayClientMessage(Component.translatable("miscelanea.gancho.premio"), true);
            cambio();
        }
        if (t >= duraBajada()) {
            for (int k = 0; k < PREMIOS; k++) if (pc[k] < 0) nuevoPremio(k, r);
            activo = false;
            fase = 0;
            gx = TRAMPILLA_X;
            gz = TRAMPILLA_Z;
            cambio();
        }
    }

    private void soltar(ItemStack st, double y) {
        Vec3 v = mundo(0, y, -0.6);
        Direction f = facing();
        ItemEntity it = new ItemEntity(level, v.x, v.y, v.z, st);
        it.setDeltaMovement(f.getStepX() * 0.1, 0.15, f.getStepZ() * 0.1);
        level.addFreshEntity(it);
    }

    public static void tick(Level lv, BlockPos pos, BlockState s, MaquinaAltaBlockEntity be) {
        if (!be.activo) return;
        ServerLevel l = (ServerLevel) lv;
        long ahora = l.getGameTime();
        int t = (int) (ahora - be.inicio);
        RandomSource r = l.random;
        switch (be.tipo()) {
            case MaquinaAltaBlock.GANCHO -> be.tickGancho(l, ahora, r);
            case MaquinaAltaBlock.TRAGAPERRAS -> {
                for (int i = 0; i < 3; i++)
                    if (t == PARA[i]) l.playSound(null, pos, ModSounds.FE_RODILLO_PARA.get(), SoundSource.BLOCKS, 0.9F, 1F + i * 0.05F);
                if (t == PARA[2] + 6) {
                    int n = premio(be.rodillo);
                    Player p = be.jugador != null ? l.getPlayerByUUID(be.jugador) : null;
                    if (n > 0) {
                        for (int i = 0; i < n; i += 16) be.soltar(new ItemStack(ModItems.FICHA.get(), Math.min(16, n - i)), 0.3);
                        l.playSound(null, pos, n >= 12 ? ModSounds.FE_PREMIO_GORDO.get() : ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1F);
                        if (n >= 12) {
                            Vec3 c = be.mundo(0, 2.2, 0);
                            l.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.15);
                        }
                        if (p instanceof ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "traga", n);
                        if (p != null) p.displayClientMessage(Component.translatable(n >= 12 ? "miscelanea.tragaperras.gordo" : "miscelanea.tragaperras.premio", n), true);
                    } else if (p != null) p.displayClientMessage(Component.translatable("miscelanea.tragaperras.nada"), true);
                }
                if (t >= PARA[2] + 20) {
                    be.activo = false;
                    be.cambio();
                }
            }
            default -> {
                if (t == 26) l.playSound(null, pos, ModSounds.FE_CAPSULA.get(), SoundSource.BLOCKS, 0.9F, 1F);
                if (t == 34) {
                    ItemStack st = new ItemStack(ModItems.CAPSULA_GACHA.get());
                    st.getOrCreateTag().putInt("color", be.colorCapsula);
                    be.soltar(st, 0.35);
                    be.activo = false;
                    be.cambio();
                }
            }
        }
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (jugador != null) t.putUUID("jug", jugador);
        t.putBoolean("act", activo);
        t.putInt("fase", fase);
        t.putLong("ini", inicio);
        t.putLong("iniB", inicioBajada);
        t.putFloat("gx", gx);
        t.putFloat("gz", gz);
        t.putFloat("bx", bajaX);
        t.putFloat("bz", bajaZ);
        t.putInt("agarr", agarrado);
        t.putInt("cae", caeT);
        t.putIntArray("pc", pc);
        int[] xs = new int[PREMIOS], zs = new int[PREMIOS];
        for (int k = 0; k < PREMIOS; k++) {
            xs[k] = Math.round(px[k] * 1000);
            zs[k] = Math.round(pz[k] * 1000);
        }
        t.putIntArray("px", xs);
        t.putIntArray("pz", zs);
        t.putIntArray("rod", rodillo);
        t.putInt("cap", colorCapsula);
        t.putBoolean("ini0", iniciado);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        jugador = t.hasUUID("jug") ? t.getUUID("jug") : null;
        activo = t.getBoolean("act");
        fase = t.getInt("fase");
        inicio = t.getLong("ini");
        inicioBajada = t.getLong("iniB");
        gx = t.getFloat("gx");
        gz = t.getFloat("gz");
        bajaX = t.getFloat("bx");
        bajaZ = t.getFloat("bz");
        agarrado = t.getInt("agarr");
        caeT = t.getInt("cae");
        int[] a = t.getIntArray("pc"), xs = t.getIntArray("px"), zs = t.getIntArray("pz");
        if (a.length == PREMIOS && xs.length == PREMIOS && zs.length == PREMIOS) {
            pc = a;
            for (int k = 0; k < PREMIOS; k++) {
                px[k] = xs[k] / 1000F;
                pz[k] = zs[k] / 1000F;
            }
        }
        int[] rd = t.getIntArray("rod");
        if (rd.length == 3) rodillo = rd;
        colorCapsula = t.getInt("cap");
        iniciado = t.getBoolean("ini0");
    }

    @Override
    public CompoundTag getUpdateTag() {
        if (!iniciado && level != null && !level.isClientSide && tipo() == MaquinaAltaBlock.GANCHO) prepararPremios(level.random);
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2, 0).inflate(0.5);
    }
}
