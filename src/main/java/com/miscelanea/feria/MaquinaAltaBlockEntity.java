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
    /** Una de cada tantas veces, la garra saca un peluche de alguien en vez del osito. */
    private static final int PELUCHE_RARO = 40;
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
    // ---- canastas, boxeo y martillo
    public static final int CANASTA_DURA = 600;
    /** Balones de la máquina (no se pueden duplicar: se cogen de la bandeja y vuelven a ella rodando por la rampa). */
    public static final int BALONES = 3;
    /** Aro: radio, altura y distancia al fondo (locales). */
    public static final double ARO_R = 0.19, ARO_Y = 1.6, ARO_Z = 0.2;
    /** Margen (tics) que se acepta entre lo que vio el cliente al golpear y el reloj del servidor. */
    public static final int MARGEN_GOLPE = 3;
    public int puntos, valor;
    public int bandeja = BALONES;
    public long golpeT = -1000;
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

    /** Dirección local → mundo (sin desplazar). */
    public Vec3 mundoDir(Vec3 v) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    /** Punto del mundo → coordenadas locales de la máquina. */
    public Vec3 local(Vec3 w) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        double dx = w.x - worldPosition.getX() - 0.5, dz = w.z - worldPosition.getZ() - 0.5;
        return new Vec3(dx * c - dz * s, w.y - worldPosition.getY(), dx * s + dz * c);
    }

    /** Dirección del mundo → local. */
    public Vec3 localDir(Vec3 v) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
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
        if (activo && tipo() == MaquinaAltaBlock.MARTILLO && fase == 1 && p.getUUID().equals(jugador)) {
            // normalmente el cliente ya ha mandado el instante que vio (GanchoPacket.GOLPE); esto es por si no llega
            golpeMartillo(p, -1);
            return;
        }
        if (activo && tipo() == MaquinaAltaBlock.CANASTA && p.getUUID().equals(jugador)) {
            darBalon(p);
            return;
        }
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
            case MaquinaAltaBlock.CANASTA -> {
                puntos = 0;
                bandeja = BALONES;
                darBalon(p);
                p.displayClientMessage(Component.translatable("miscelanea.canasta.empieza"), true);
                level.playSound(null, worldPosition, ModSounds.FE_ARRANCA.get(), SoundSource.BLOCKS, 0.7F, 1.3F);
            }
            case MaquinaAltaBlock.BOXEO -> {
                fase = 1;
                valor = 0;
                p.displayClientMessage(Component.translatable("miscelanea.boxeo.empieza"), true);
                level.playSound(null, worldPosition, ModSounds.FE_MANIVELA.get(), SoundSource.BLOCKS, 0.8F, 0.7F);
            }
            case MaquinaAltaBlock.MARTILLO -> {
                fase = 1;
                valor = 0;
                p.displayClientMessage(Component.translatable("miscelanea.martillo.empieza"), true);
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
            // de vez en cuando, muy de vez en cuando, la garra saca un peluche de alguien en vez del osito
            boolean raro = l.getRandom().nextInt(PELUCHE_RARO) == 0;
            ItemStack st;
            CompoundTag est = new CompoundTag();
            if (raro) {
                st = new ItemStack(Peluches.alAzar(l.getRandom()).get());
            } else {
                st = new ItemStack(ModItems.PELUCHE.get());
                est.putString("color", String.valueOf(pc[agarrado]));
            }
            st.getOrCreateTag().put("BlockStateTag", est);
            soltar(st, 0.3);
            pc[agarrado] = -1;
            agarrado = -1;
            l.playSound(null, worldPosition, raro ? ModSounds.FE_PREMIO_GORDO.get() : ModSounds.FE_PREMIO.get(),
                    SoundSource.BLOCKS, 1F, 1F);
            FeriaJuice.confeti(l, mundo(0, 1.3, -0.55), raro ? 6 : 2);
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
                        FeriaJuice.confeti(l, be.mundo(0, 1.6, -0.55), n >= 12 ? 2 : 1);
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
            case MaquinaAltaBlock.CANASTA -> be.tickCanasta(l, t);
            case MaquinaAltaBlock.BOXEO -> {
                if (be.fase == 1 && t > 400) {
                    be.activo = false;
                    be.cambio();
                }
                if (be.fase == 2 && ahora - be.golpeT == 30) be.premioFuerza(l, "boxeo", be.valor, 900, 700);
                if (be.fase == 2 && ahora - be.golpeT > 90) {
                    be.activo = false;
                    be.fase = 0;
                    be.cambio();
                }
            }
            case MaquinaAltaBlock.MARTILLO -> {
                if (be.fase == 1 && t > 400) {
                    be.activo = false;
                    be.cambio();
                }
                if (be.fase == 2 && ahora - be.golpeT == 16) be.premioFuerza(l, "martillo", be.valor * 10, 970, 800);
                if (be.fase == 2 && ahora - be.golpeT > 70) {
                    be.activo = false;
                    be.fase = 0;
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

    // ------------------------------------------------------------------ canastas
    /** Coge un balón de la bandeja (si queda alguno). Va marcado con esta máquina y esta partida. */
    private void darBalon(Player p) {
        if (bandeja <= 0) return;
        ItemStack st = BalonItem.nuevo(this);
        if (p.getMainHandItem().isEmpty()) p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
        else if (!p.getInventory().add(st)) return;
        bandeja--;
        level.playSound(null, worldPosition, ModSounds.FE_BOTE.get(), SoundSource.BLOCKS, 0.4F, 1.3F);
        cambio();
    }

    /** Un balón de esta partida vuelve a la bandeja (ha bajado por la rampa, se ha salido o lo han tirado al suelo). */
    void devolverBalon(long juego) {
        if (!activo || tipo() != MaquinaAltaBlock.CANASTA || juego != inicio) return;
        bandeja = Math.min(BALONES, bandeja + 1);
        cambio();
    }

    /** Al acabar: los balones se quedan en la máquina (se quitan del inventario del jugador). */
    private void quitarBalones(@Nullable Player p) {
        if (p == null) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(ModItems.BALON.get()) && BalonItem.deMaquina(st, worldPosition)) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Centro del aro (local) en el instante t del juego: en los últimos 10 s se mueve de lado a lado. */
    public static double[] aro(float t) {
        double x = t > 400 && t < CANASTA_DURA ? Math.sin((t - 400) * 0.07) * 0.2 : 0;
        return new double[]{x, ARO_Y, ARO_Z};
    }

    /** El balón ha pasado hacia abajo por dentro del aro. */
    void anotar(BalonEntity b) {
        if (!activo || tipo() != MaquinaAltaBlock.CANASTA) return;
        int v = b.distancia > 2.6 ? 3 : 2;
        puntos += v;
        double[] a = aro(level.getGameTime() - inicio);
        Vec3 c = mundo(a[0], a[1], a[2]);
        level.playSound(null, c.x, c.y, c.z, ModSounds.FE_CANASTA.get(), SoundSource.BLOCKS, 1F, v == 3 ? 1.2F : 1F);
        if (level instanceof ServerLevel l) l.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 8, 0.15, 0.1, 0.15, 0);
        cambio();
    }

    private void tickCanasta(ServerLevel l, int t) {
        if (t >= CANASTA_DURA) {
            activo = false;
            bandeja = BALONES;
            Player p = jugador != null ? l.getPlayerByUUID(jugador) : null;
            quitarBalones(p);
            if (p != null) {
                p.displayClientMessage(Component.translatable("miscelanea.canasta.fin", puntos), false);
                if (p instanceof ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "canasta", puntos);
                ItemStack premio = puntos >= 24 ? peluche(l) : puntos >= 12 ? ChapaItem.de(l.random.nextInt(ChapaItem.ORO)) : ItemStack.EMPTY;
                if (!premio.isEmpty()) {
                    soltar(premio, 0.3);
                    FeriaJuice.confeti(l, mundo(0, 2.0, -0.3), puntos >= 24 ? 2 : 1);
                    l.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1F);
                }
            }
            l.playSound(null, worldPosition, ModSounds.FE_PARA.get(), SoundSource.BLOCKS, 0.7F, 1.3F);
            cambio();
            return;
        }
        if (t % 20 == 0) cambio();
    }

    private ItemStack peluche(ServerLevel l) {
        ItemStack st = new ItemStack(ModItems.PELUCHE.get());
        CompoundTag est = new CompoundTag();
        est.putString("color", String.valueOf(l.random.nextInt(4)));
        st.getOrCreateTag().put("BlockStateTag", est);
        return st;
    }

    // ------------------------------------------------------------------ boxeo y martillo
    /**
     * Instante (tics desde que empezó) en que golpeó el jugador: el que vio su cliente si se le manda (tCliente >= 0)
     * y está a ±{@link #MARGEN_GOLPE} del reloj del servidor; si no, el del servidor (recortado al margen).
     */
    private int instanteGolpe(int tCliente) {
        int ts = (int) (level.getGameTime() - inicio);
        if (tCliente < 0) return ts;
        return Math.max(ts - MARGEN_GOLPE, Math.min(ts + MARGEN_GOLPE, tCliente));
    }

    /** Medidor del boxeo (0..1): sube y baja solo; hay que pegar arriba del todo. */
    public static float medidorBoxeo(float t) {
        return (float) ((1 - Math.cos(t * 0.23)) / 2);
    }

    /**
     * Puñetazo al saco. La fuerza sale sobre todo de pegar en el momento justo (medidor arriba) y con el golpe cargado;
     * correr, saltar (crítico) y el arma solo dan un pequeño extra con tope. Así una espada de netherite no regala el premio.
     */
    public void golpeBoxeo(Player p, int tCliente) {
        if (!activo || fase != 1 || tipo() != MaquinaAltaBlock.BOXEO || !p.getUUID().equals(jugador)) return;
        float carga = p.getAttackStrengthScale(0.5F);
        float m = medidorBoxeo(instanteGolpe(tCliente));
        double dano = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double extra = (p.isSprinting() ? 0.03 : 0) + (p.fallDistance > 0 && !p.onGround() ? 0.05 : 0) + Math.min(0.04, Math.max(0, dano - 1) * 0.006);
        double f = carga * carga * (0.2 + 0.7 * m * m) + extra * carga;
        valor = (int) Math.max(1, Math.min(999, f * 1000 + level.random.nextInt(41) - 20));
        fase = 2;
        golpeT = level.getGameTime();
        level.playSound(null, worldPosition, ModSounds.FE_PUNETAZO.get(), SoundSource.BLOCKS, 1.2F, 1F);
        p.resetAttackStrengthTicker();
        cambio();
    }

    /** Valor del medidor del martillo (0..100) en un instante. */
    public float medidor(float tiempo) {
        return (float) ((1 - Math.cos((tiempo - inicio) * 0.19)) / 2 * 100);
    }

    /** Mazazo: vale lo que marque el medidor en el instante en que el jugador hizo clic (visto por su cliente). */
    void golpeMartillo(Player p, int tCliente) {
        if (!activo || fase != 1 || tipo() != MaquinaAltaBlock.MARTILLO || !p.getUUID().equals(jugador)) return;
        valor = Math.round(medidor(inicio + instanteGolpe(tCliente)));
        fase = 2;
        golpeT = level.getGameTime();
        level.playSound(null, worldPosition, ModSounds.FE_MAZAZO.get(), SoundSource.BLOCKS, 1.2F, 1F);
        cambio();
    }

    /** Golpe mandado por el cliente con el instante que vio (GanchoPacket.GOLPE). */
    void golpeCliente(ServerPlayer p, int tCliente) {
        if (tipo() == MaquinaAltaBlock.MARTILLO) golpeMartillo(p, tCliente);
        else if (tipo() == MaquinaAltaBlock.BOXEO) golpeBoxeo(p, tCliente);
    }

    void premioFuerza(ServerLevel l, String juego, int puntos, int gordo, int premio) {
        Player p = jugador != null ? l.getPlayerByUUID(jugador) : null;
        if (puntos >= gordo) {
            l.playSound(null, worldPosition, ModSounds.FE_CAMPANA.get(), SoundSource.BLOCKS, 1.5F, 1F);
            Vec3 c = mundo(0, juego.equals("martillo") ? 4.4 : 2.2, 0);
            l.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 25, 0.3, 0.3, 0.3, 0.12);
            soltar(peluche(l), 0.3);
            FeriaJuice.confeti(l, c, 2);
        } else if (puntos >= premio) {
            soltar(ChapaItem.de(l.random.nextInt(ChapaItem.ORO)), 0.3);
            FeriaJuice.confeti(l, mundo(0, 1.4, -0.5), 1);
        }
        if (p != null) {
            p.displayClientMessage(Component.translatable("miscelanea." + juego + ".resultado", puntos), true);
            if (p instanceof ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, juego, puntos);
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
        t.putInt("pts", puntos);
        t.putInt("val", valor);
        t.putInt("band", bandeja);
        t.putLong("golpe", golpeT);
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
        puntos = t.getInt("pts");
        valor = t.getInt("val");
        bandeja = t.contains("band") ? t.getInt("band") : BALONES;
        golpeT = t.contains("golpe") ? t.getLong("golpe") : -1000;
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
        return new AABB(worldPosition).expandTowards(0, tipo() == MaquinaAltaBlock.MARTILLO ? 5 : 2, 0).inflate(0.5);
    }
}
