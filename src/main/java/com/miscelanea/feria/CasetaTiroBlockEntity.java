package com.miscelanea.feria;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Caseta de tiro: 30 segundos para derribar patos (1 punto, el dorado 5) y dianas (2) con la escopeta de corcho.
 * Las posiciones de los blancos salen del tiempo de juego, así que el servidor comprueba los tiros sin mandar nada
 * cada tic. Coordenadas locales: x a lo ancho, y arriba, z hacia el fondo de la caseta (el mostrador está en z=0).
 */
public class CasetaTiroBlockEntity extends BlockEntity {
    public static final int DURA = 600, PATOS = 5, DIANAS = 3, BLANCOS = PATOS + DIANAS, VUELVE = 60;
    public static final double ANCHO = 3.4, VISIBLE = 1.5;
    public static final double PATO_Y = 1.35, PATO_Z = 2.2, DIANA_Y = 2.2, DIANA_Z = 2.65;
    @Nullable public UUID jugador;
    public String nombre = "";
    public long inicio = -10000;
    public int puntos;
    public long[] caido = new long[BLANCOS];
    public int record;

    public CasetaTiroBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.CASETA_TIRO_BE.get(), p, s);
        java.util.Arrays.fill(caido, -10000);
    }

    public Direction facing() {
        return getBlockState().getValue(CasetaTiroBlock.FACING);
    }

    public boolean activa(long ahora) {
        return jugador != null && ahora - inicio < DURA;
    }

    /** Ángulo (radianes) que gira el sistema local para pasar a coordenadas del mundo. */
    public static float giro(Direction f) {
        return (float) Math.toRadians(180 - f.toYRot());
    }

    public Vec3 mundo(double x, double y, double z) {
        double a = giro(facing()), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(worldPosition.getX() + 0.5 + x * c + z * s, worldPosition.getY() + y, worldPosition.getZ() + 0.5 - x * s + z * c);
    }

    /** Posición x del blanco k en un instante; patos hacia la derecha, dianas hacia la izquierda y más deprisa. */
    public static double x(int k, double t) {
        double v, fase;
        if (k < PATOS) {
            v = 0.032;
            fase = ANCHO * k / PATOS;
        } else {
            v = -0.05;
            fase = ANCHO * (k - PATOS) / DIANAS;
        }
        double p = (fase + v * t) % ANCHO;
        if (p < 0) p += ANCHO;
        return p - ANCHO / 2;
    }

    public static boolean dorado(int k, long t) {
        return k == (int) ((t / 300) % PATOS);
    }

    public int valor(int k, long t) {
        return k < PATOS ? (dorado(k, t) ? 5 : 1) : 2;
    }

    public boolean visible(int k, double t) {
        return Math.abs(x(k, t)) < VISIBLE && t - caido[k] >= VUELVE;
    }

    public Vec3 centro(int k, double t) {
        return k < PATOS ? mundo(x(k, t), PATO_Y + 0.25, PATO_Z) : mundo(x(k, t), DIANA_Y + 0.28, DIANA_Z);
    }

    void empezar(Player p, ItemStack st) {
        long ahora = level.getGameTime();
        if (activa(ahora) && !p.getUUID().equals(jugador)) {
            p.displayClientMessage(Component.translatable("miscelanea.tiro.ocupada", nombre), true);
            return;
        }
        // como los demás puestos: cada partida cuesta una ficha
        if (!st.is(ModItems.FICHA.get())) {
            p.displayClientMessage(Component.translatable("miscelanea.maquina.ficha"), true);
            return;
        }
        if (!p.getAbilities().instabuild) st.shrink(1);
        level.playSound(null, worldPosition, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1F);
        jugador = p.getUUID();
        nombre = p.getGameProfile().getName();
        inicio = ahora;
        puntos = 0;
        java.util.Arrays.fill(caido, -10000);
        if (!p.getInventory().hasAnyMatching(s2 -> s2.is(ModItems.ESCOPETA_CORCHO.get())))
            p.getInventory().add(new ItemStack(ModItems.ESCOPETA_CORCHO.get()));
        p.displayClientMessage(Component.translatable("miscelanea.tiro.empieza"), true);
        level.playSound(null, worldPosition, ModSounds.FE_ARRANCA.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
        cambio();
    }

    /** Tiro desde ojo en dirección dir; devuelve true si ha dado a algo. */
    boolean disparo(Player p, Vec3 ojo, Vec3 dir) {
        long ahora = level.getGameTime();
        if (!activa(ahora) || !p.getUUID().equals(jugador)) return false;
        int mejor = -1;
        double cerca = 1e9;
        // se miran unos tics atrás también (lo que veía el jugador con algo de retraso)
        for (int k = 0; k < BLANCOS; k++)
            for (int atras = 0; atras <= 4; atras += 2) {
                double t = ahora - atras;
                if (!visible(k, t)) continue;
                Vec3 d = centro(k, t).subtract(ojo);
                double proj = d.dot(dir);
                if (proj <= 0) continue;
                double perp = d.subtract(dir.scale(proj)).length();
                if (perp < (k < PATOS ? 0.3 : 0.32) && proj < cerca) {
                    cerca = proj;
                    mejor = k;
                }
            }
        if (mejor < 0) return false;
        int v = valor(mejor, ahora);
        puntos += v;
        caido[mejor] = ahora;
        Vec3 c = centro(mejor, ahora);
        level.playSound(null, c.x, c.y, c.z, ModSounds.FE_DIANA.get(), SoundSource.BLOCKS, 1F, v >= 5 ? 1.5F : v == 2 ? 1.2F : 1F);
        if (level instanceof ServerLevel sl) sl.sendParticles(v >= 5 ? ParticleTypes.WAX_ON : ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.15, 0.15, 0.15, 0.1);
        cambio();
        return true;
    }

    public static void tick(Level l, BlockPos pos, BlockState s, CasetaTiroBlockEntity be) {
        if (be.jugador == null || l.getGameTime() - be.inicio < DURA) return;
        Player p = l.getPlayerByUUID(be.jugador);
        be.jugador = null;
        if (be.puntos > be.record) be.record = be.puntos;
        if (p != null) {
            p.displayClientMessage(Component.translatable("miscelanea.tiro.fin", be.puntos), false);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "tiro", be.puntos);
            ItemStack premio = ItemStack.EMPTY;
            if (be.puntos >= 20) {
                premio = new ItemStack(ModItems.PELUCHE.get());
                CompoundTag est = new CompoundTag();
                est.putString("color", String.valueOf(l.random.nextInt(4)));
                premio.getOrCreateTag().put("BlockStateTag", est);
                p.displayClientMessage(Component.translatable("miscelanea.tiro.premio_gordo"), false);
            } else if (be.puntos >= 8) {
                premio = new ItemStack(Items.COOKIE, 3);
                p.displayClientMessage(Component.translatable("miscelanea.tiro.premio"), false);
            } else p.displayClientMessage(Component.translatable("miscelanea.tiro.otra"), false);
            if (!premio.isEmpty() && !p.getInventory().add(premio)) p.drop(premio, false);
        }
        if (be.puntos >= 8 && l instanceof ServerLevel sl) {
            FeriaJuice.confeti(sl, be.mundo(0, 1.3, -0.2), be.puntos >= 20 ? 2 : 1);
            l.playSound(null, pos, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 0.9F, 1F);
        }
        l.playSound(null, pos, ModSounds.FE_PARA.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
        be.cambio();
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (jugador != null) t.putUUID("jug", jugador);
        t.putString("nom", nombre);
        t.putLong("ini", inicio);
        t.putInt("pts", puntos);
        t.putInt("rec", record);
        t.putLongArray("caido", caido);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        jugador = t.hasUUID("jug") ? t.getUUID("jug") : null;
        nombre = t.getString("nom");
        inicio = t.getLong("ini");
        puntos = t.getInt("pts");
        record = t.getInt("rec");
        long[] c = t.getLongArray("caido");
        if (c.length == BLANCOS) caido = c;
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
        return new AABB(worldPosition).inflate(3, 4, 3);
    }

    static CasetaTiroBlockEntity buscar(Level l, Player p) {
        BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-10, -4, -10), c.offset(10, 4, 10)))
            if (l.getBlockEntity(q) instanceof CasetaTiroBlockEntity be && p.getUUID().equals(be.jugador)) return be;
        return null;
    }
}
