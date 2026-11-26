package com.miscelanea.laboratorio;

import com.miscelanea.pociones.PocionesRegistro;
import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.aparatos.Aparatos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EL LABERINTO DE LA RATA. Un laberinto de 6 x 6 celdas (13 x 13 bloques, paredes de 3 de alto) que se construye delante
 * de la caja cuando empieza el experimento y se recoge cuando acaba. El sujeto (un cautivo del científico) entra por una
 * esquina, encogido con la poción de enano, y tiene que llegar al queso de la esquina contraria en dos minutos. Por el
 * camino hay baldosas con descarga suave. Se apunta la ficha (tiempo, descargas y resultado) y, con un tubo de ensayo,
 * una muestra de miedo; si llega, se lleva un queso de laboratorio.
 * <p>
 * El laberinto sale de una semilla (guardada), así que se puede volver a calcular para recogerlo o para ver dónde están
 * las baldosas aunque se haya reiniciado el servidor.
 */
public class LaberintoBlockEntity extends BlockEntity {
    public static final int CELDAS = 6, LADO = 2 * CELDAS + 1, ALTO = 3, DURACION = 20 * 120;
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "laberinto"));

    private int semilla = 1;
    private boolean activo;
    @Nullable private UUID sujeto, captor;
    private int tiempo, fallos;
    private long ultimaDescarga;

    public LaberintoBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.LABERINTO_BE.get(), p, s);
    }

    public boolean activo() { return activo; }
    public int fallos() { return fallos; }

    // ------------------------------------------------------------------ geometría
    private Direction frente() {
        return getBlockState().getValue(LaberintoBlock.FACING);
    }

    /** Del espacio del laberinto (u hacia delante, v hacia la derecha; 0..12) al mundo (a la altura del suelo). */
    public BlockPos mundo(int u, int v) {
        Direction f = frente(), d = f.getClockWise();
        return worldPosition.relative(f, 2 + u).relative(d, v - CELDAS);
    }

    /** Dónde están las paredes (true) de la rejilla, según la semilla (recorrido en profundidad). */
    private boolean[][] paredes() {
        boolean[][] pared = new boolean[LADO][LADO];
        for (boolean[] fila : pared) java.util.Arrays.fill(fila, true);
        RandomSource r = RandomSource.create(semilla);
        boolean[][] vista = new boolean[CELDAS][CELDAS];
        java.util.ArrayDeque<int[]> pila = new java.util.ArrayDeque<>();
        pila.push(new int[]{0, 0});
        vista[0][0] = true;
        pared[1][1] = false;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!pila.isEmpty()) {
            int[] c = pila.peek();
            List<int[]> vecinas = new ArrayList<>();
            for (int[] d : dirs) {
                int a = c[0] + d[0], b = c[1] + d[1];
                if (a >= 0 && b >= 0 && a < CELDAS && b < CELDAS && !vista[a][b]) vecinas.add(new int[]{a, b});
            }
            if (vecinas.isEmpty()) {
                pila.pop();
                continue;
            }
            int[] n = vecinas.get(r.nextInt(vecinas.size()));
            vista[n[0]][n[1]] = true;
            pared[2 * n[0] + 1][2 * n[1] + 1] = false;
            pared[c[0] + n[0] + 1][c[1] + n[1] + 1] = false;
            pila.push(n);
        }
        return pared;
    }

    /** Las baldosas con descarga (celdas de la rejilla, sin la de salida ni la de llegada). */
    public List<int[]> placas() {
        List<int[]> l = new ArrayList<>();
        RandomSource r = RandomSource.create(semilla * 31L + 7);
        for (int i = 0; i < 9; i++) {
            int a = r.nextInt(CELDAS), b = r.nextInt(CELDAS);
            if ((a == 0 && b == 0) || (a == CELDAS - 1 && b == CELDAS - 1)) continue;
            int[] c = {2 * a + 1, 2 * b + 1};
            boolean repetida = false;
            for (int[] o : l) if (o[0] == c[0] && o[1] == c[1]) repetida = true;
            if (!repetida) l.add(c);
        }
        return l;
    }

    public BlockPos posSalida() { return mundo(1, 1); }

    /** Laberintos en marcha (los mira {@link LaberintoProteccion} para que no se piquen ni se tapen sus paredes). */
    static final java.util.Set<LaberintoBlockEntity> ACTIVOS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** ¿Está {@code q} en el recinto del laberinto (paredes, pasillos o el suelo de debajo)? */
    public boolean enRecinto(BlockPos q) {
        BlockPos a = mundo(0, 0), b = mundo(LADO - 1, LADO - 1);
        return q.getX() >= Math.min(a.getX(), b.getX()) && q.getX() <= Math.max(a.getX(), b.getX())
                && q.getZ() >= Math.min(a.getZ(), b.getZ()) && q.getZ() <= Math.max(a.getZ(), b.getZ())
                && q.getY() >= a.getY() - 1 && q.getY() < a.getY() + ALTO;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ACTIVOS.remove(this);
    }
    public BlockPos posQueso() { return mundo(LADO - 2, LADO - 2); }
    public Vec3 centro() {
        BlockPos c = mundo(CELDAS, CELDAS);
        return new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
    }

    private boolean espacioLibre(Level l) {
        for (int u = 0; u < LADO; u++)
            for (int v = 0; v < LADO; v++) {
                BlockPos b = mundo(u, v);
                if (!l.getBlockState(b.below()).isSolid()) return false;
                for (int y = 0; y < ALTO; y++) if (!l.getBlockState(b.above(y)).isAir()) return false;
            }
        return true;
    }

    private void construir(Level l) {
        boolean[][] pared = paredes();
        BlockState a = Blocks.YELLOW_CONCRETE.defaultBlockState(), b = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int u = 0; u < LADO; u++)
            for (int v = 0; v < LADO; v++) {
                BlockPos p = mundo(u, v);
                if (pared[u][v]) for (int y = 0; y < ALTO; y++) l.setBlock(p.above(y), (y == ALTO - 1 ? b : a), 2);
            }
        for (int[] c : placas()) l.setBlock(mundo(c[0], c[1]), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), 2);
        l.setBlock(posQueso(), Blocks.HONEYCOMB_BLOCK.defaultBlockState(), 2);
    }

    /** Recoge el laberinto solo si hay uno en marcha (así no se borra lo que el jugador haya construido allí). */
    public void limpiarSiActivo() {
        if (activo) limpiar();
    }

    /** Recoge el laberinto (todo el recinto vuelve a ser aire). */
    
    public void limpiar() {
        if (level == null) return;
        for (int u = 0; u < LADO; u++)
            for (int v = 0; v < LADO; v++) {
                BlockPos p = mundo(u, v);
                for (int y = 0; y < ALTO; y++) if (!level.getBlockState(p.above(y)).isAir()) level.setBlock(p.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
    }

    // ------------------------------------------------------------------ manejo
    public void alUsar(Player p) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!LabConfig.estaciones()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.apagada").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (activo) {
            terminar(sl, "paro");
            return;
        }
        if (p.isShiftKeyDown()) {
            semilla = sl.random.nextInt(1_000_000) + 1;
            setChanged();
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.laberinto.nuevo").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LivingEntity c = Aparatos.cautivoDe(p);
        if (c == null) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.laberinto.sin_sujeto").withStyle(ChatFormatting.RED), true);
            return;
        }
        empezar(sl, p, c);
    }

    /** Construye el laberinto y mete al sujeto. Devuelve false si no hay sitio. */
    public boolean empezar(ServerLevel sl, Player cientifico, LivingEntity v) {
        if (activo) return false;
        if (!espacioLibre(sl)) {
            cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.laberinto.espacio").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Aparatos.en(v)) return false;
        construir(sl);
        Lazo.soltar(v, false);
        com.miscelanea.tortura.Grilletes.cadena(v, null);   // si no, la cadena tiraba de él hacia el científico
        BlockPos s = posSalida();
        v.teleportTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        v.addEffect(new MobEffectInstance(PocionesRegistro.ENANO.get(), DURACION + 200, 0, false, false, true));
        sujeto = v.getUUID();
        captor = cientifico.getUUID();
        activo = true;
        tiempo = fallos = 0;
        ultimaDescarga = 0;
        cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.laberinto.empieza").withStyle(ChatFormatting.YELLOW), true);
        sl.playSound(null, worldPosition, SoundEvents.UI_TOAST_IN, SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged();
        return true;
    }

    public static void tick(Level l, BlockPos p, BlockState s, LaberintoBlockEntity be) {
        if (!l.isClientSide) {
            if (be.activo) ACTIVOS.add(be);
            else ACTIVOS.remove(be);
        }
        if (!(l instanceof ServerLevel sl) || !be.activo) return;
        be.tiempo++;
        LivingEntity v = be.sujeto == null ? null : sl.getEntity(be.sujeto) instanceof LivingEntity le ? le : null;
        if (v == null || !v.isAlive()) {
            be.terminar(sl, "abandonado");
            return;
        }
        BlockPos pie = v.blockPosition();
        // la descarga de una baldosa
        for (int[] c : be.placas()) {
            if (pie.equals(be.mundo(c[0], c[1])) && sl.getGameTime() - be.ultimaDescarga > 30) {
                be.ultimaDescarga = sl.getGameTime();
                be.fallos++;
                Aparatos.danar(v, DANO, 1F, be.captor);
                v.setDeltaMovement(v.getDeltaMovement().add(0, 0.35, 0));
                v.hurtMarked = true;
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + 0.2, v.getZ(), 12, 0.2, 0.1, 0.2, 0.1);
                sl.playSound(null, v.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.3F, 1.6F);
            }
        }
        // el queso
        Vec3 q = Vec3.atCenterOf(be.posQueso());
        if (v.position().distanceToSqr(q) < 2.6) {
            be.terminar(sl, "llego");
            return;
        }
        if (be.tiempo >= DURACION) be.terminar(sl, "tiempo");
    }

    private void terminar(ServerLevel sl, String resultado) {
        activo = false;
        LivingEntity v = sujeto == null ? null : sl.getEntity(sujeto) instanceof LivingEntity le ? le : null;
        Player cientifico = captor == null ? null : sl.getServer().getPlayerList().getPlayer(captor);
        if (cientifico != null) {
            Map<String, String> lecturas = new LinkedHashMap<>();
            lecturas.put("tiempo", (tiempo / 20) + " s");
            lecturas.put("fallos", String.valueOf(fallos));
            lecturas.put("resultado", resultado);
            if (v != null) lecturas.put("pulso", Experimentos.pulso(v) + " ppm");
            Experimentos.anotar(cientifico, "laberinto", v instanceof Player pl ? pl : null, 0, lecturas);
            if (Experimentos.darMuestra(cientifico, LabRegistro.MUESTRA_MIEDO.get()))
                cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.muestra_obtenida", Component.translatable("item.miscelanea.muestra_miedo")), true);
        }
        if (v != null) {
            v.removeEffect(PocionesRegistro.ENANO.get());
            if ("llego".equals(resultado) && v instanceof Player pl) {
                ItemStack queso = new ItemStack(LabRegistro.QUESO.get());
                if (!pl.getInventory().add(queso)) pl.drop(queso, false);
                pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.laberinto.queso").withStyle(ChatFormatting.GOLD), true);
            }
            // fuera, delante de la caja, antes de recoger el laberinto
            BlockPos f = worldPosition.relative(frente().getOpposite());
            v.teleportTo(f.getX() + 0.5, f.getY(), f.getZ() + 0.5);
        }
        limpiar();
        sujeto = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Semilla", semilla);
        t.putBoolean("Activo", activo);
        t.putInt("Tiempo", tiempo);
        t.putInt("Fallos", fallos);
        if (sujeto != null) t.putUUID("Sujeto", sujeto);
        if (captor != null) t.putUUID("Captor", captor);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        semilla = t.getInt("Semilla");
        activo = t.getBoolean("Activo");
        tiempo = t.getInt("Tiempo");
        fallos = t.getInt("Fallos");
        sujeto = t.hasUUID("Sujeto") ? t.getUUID("Sujeto") : null;
        captor = t.hasUUID("Captor") ? t.getUUID("Captor") : null;
    }
}
