package com.miscelanea.tortura.aparatos;

import com.miscelanea.tortura.Cautivos;
import com.miscelanea.tortura.Grilletes;
import com.miscelanea.tortura.IncapCompat;
import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.TrampaOso;
import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.Heridas;
import com.miscelanea.tortura.heridas.HeridasDatos;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Utilidades comunes de los aparatos: quién está metido en uno, a quién lleva un captor, preparar al cautivo antes de
 * meterlo (se le quitan el lazo y la cadena; los grilletes se quedan), daño propio y heridas en la skin.
 * <p>
 * En la víctima se guarda {@code miscelanea_aparato: {Pos}} en sus datos persistentes (servidor). En el cliente se mira
 * {@link AparatosVictimas}.
 */
public final class Aparatos {
    private Aparatos() {}

    public static final String CLAVE = "miscelanea_aparato";
    /** Víctimas que el propio aparato está bajando del asiento (el resto de desmontes se cancela). */
    static final Set<UUID> LIBERANDO = new HashSet<>();

    /** ¿Está metida en un aparato? (en el cliente, según lo sincronizado). */
    public static boolean en(@Nullable Entity e) {
        if (e == null) return false;
        if (e.level().isClientSide) return AparatosVictimas.en(e.getId());
        return e.getPersistentData().contains(CLAVE);
    }

    /** El aparato en el que está (servidor). Si la marca está rota (el aparato ya no existe o no es suyo) se borra. */
    @Nullable
    public static AparatoBlockEntity de(Entity e) {
        if (e.level().isClientSide || !e.getPersistentData().contains(CLAVE)) return null;
        BlockPos p = BlockPos.of(e.getPersistentData().getCompound(CLAVE).getLong("Pos"));
        if (!e.level().isLoaded(p)) return null;
        if (e.level().getBlockEntity(p) instanceof AparatoBlockEntity be && be.esVictima(e)) return be;
        e.getPersistentData().remove(CLAVE);
        return null;
    }

    static void marcar(LivingEntity v, BlockPos p) {
        CompoundTag d = new CompoundTag();
        d.putLong("Pos", p.asLong());
        v.getPersistentData().put(CLAVE, d);
    }

    static boolean esJefe(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || e instanceof EnderDragon || e instanceof WitherBoss
                || e instanceof Warden || e instanceof ElderGuardian;
    }

    /** ¿Cabe en un aparato? Jugadores (no espectadores) y mobs de tamaño humano o menor que no sean jefes. */
    public static boolean admisible(LivingEntity v) {
        if (!v.isAlive()) return false;
        if (v instanceof Player p) {
            if (p.isSpectator()) return false;
        } else if (!(v instanceof Mob)) return false;
        if (esJefe(v) || TrampaOso.ejecutando(v)) return false;
        return v.getBbHeight() <= 2.6F && v.getBbWidth() <= 1.6F;
    }

    /**
     * El cautivo que lleva este jugador: a hombros (lazo, maniatado), del que tira en el tira y afloja, el que va con su
     * cadena enganchada o uno suyo derribado/maniatado justo al lado.
     */
    @Nullable
    public static LivingEntity cautivoDe(Player p) {
        LivingEntity c = Lazo.cargado(p);
        if (c != null) return c;
        if (!(p.level() instanceof ServerLevel sl)) return null;
        UUID tirando = Lazo.tiraDe(p);
        if (tirando != null && sl.getEntity(tirando) instanceof LivingEntity le && le.distanceTo(p) < 10) return le;
        LivingEntity mejor = null;
        double dmin = Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12), x -> x != p && x.isAlive())) {
            boolean suyo = false;
            UUID cad = Cautivos.cadena(e);
            if (cad != null && cad.equals(p.getUUID())) suyo = true;
            else if (Lazo.estado(e) >= Lazo.DERRIBADO && Lazo.esCaptor(e, p) && e.distanceTo(p) < 4) suyo = true;
            if (!suyo || en(e)) continue;
            double d = e.distanceToSqr(p);
            if (d < dmin) {
                dmin = d;
                mejor = e;
            }
        }
        return mejor;
    }

    /** ¿Está sujeta de alguna forma? (grilletes, lazo derribado/maniatado, trampa o un aparato). Servidor. */
    public static boolean restringido(LivingEntity t) {
        return Cautivos.atado(t) || Lazo.estado(t) >= Lazo.DERRIBADO || Cautivos.conTrampa(t) || en(t);
    }

    /** Antes de meterlo: lo baja de hombros / caballo, le quita el lazo y la cadena (no los grilletes) y su minijuego. */
    static void prepararCautivo(LivingEntity v) {
        int lazo = Lazo.estado(v);
        if (lazo == Lazo.ENLAZADO) Lazo.romper(v, false);
        else if (lazo != 0) Lazo.soltar(v, false);
        if (Cautivos.cadena(v) != null) Grilletes.cadena(v, null);
        if (v.isPassenger()) {
            Entity veh = v.getVehicle();
            v.stopRiding();
            if (veh instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetPassengersPacket(sp));
        }
        if (v instanceof ServerPlayer sp) com.miscelanea.tortura.qte.Qte.cancelar(sp);
    }

    /** El aparato libre más cercano a 'centro' (cargado y a 'radio' bloques o menos). */
    @Nullable
    public static AparatoBlockEntity libreCerca(ServerLevel sl, BlockPos centro, double radio) {
        int r = (int) Math.ceil(radio);
        AparatoBlockEntity mejor = null;
        double dmin = radio * radio;
        for (int cx = (centro.getX() - r) >> 4; cx <= (centro.getX() + r) >> 4; cx++)
            for (int cz = (centro.getZ() - r) >> 4; cz <= (centro.getZ() + r) >> 4; cz++) {
                LevelChunk ch = sl.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof AparatoBlockEntity a) || a.ocupado()) continue;
                    double d = be.getBlockPos().distSqr(centro);
                    if (d <= dmin) {
                        dmin = d;
                        mejor = a;
                    }
                }
            }
        return mejor;
    }

    // ------------------------------------------------------------------ daño
    public static DamageSource fuente(Level l, ResourceKey<DamageType> k, @Nullable Entity causante) {
        Holder<DamageType> tipo = l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(k);
        return causante != null ? new DamageSource(tipo, causante) : new DamageSource(tipo);
    }

    /**
     * Daño del aparato. Mientras no sea mortal va sin atacante (así las heridas de la skin no le ponen además un "golpe"
     * de cuerpo a cuerpo); el golpe que mata lleva al captor, para el mensaje de muerte.
     */
    public static void danar(LivingEntity v, ResourceKey<DamageType> k, float dano, @Nullable UUID captor) {
        if (!(v.level() instanceof ServerLevel sl) || dano <= 0 || !v.isAlive()) return;
        Entity c = null;
        if (dano >= v.getHealth() + v.getAbsorptionAmount()) {
            IncapCompat.asegurar();   // también sin captor (caja de agua...)
            if (captor != null) {
                c = sl.getEntity(captor);
                if (c == null) c = sl.getServer().getPlayerList().getPlayer(captor);
                if (c == v) c = null;
            }
        }
        v.invulnerableTime = 0;
        v.hurt(fuente(sl, k, c), dano);
    }

    /** Mata en el acto con el daño del aparato (y su mensaje). */
    public static void matar(LivingEntity v, ResourceKey<DamageType> k, @Nullable UUID captor) {
        if (!(v.level() instanceof ServerLevel sl) || !v.isAlive()) return;
        Entity c = null;
        if (captor != null) {
            c = sl.getEntity(captor);
            if (c == null) c = sl.getServer().getPlayerList().getPlayer(captor);
            if (c == v) c = null;
        }
        DamageSource src = fuente(sl, k, c);
        IncapCompat.asegurar();
        v.invulnerableTime = 0;
        v.hurt(src, 1000F);
        if (v.isAlive() && !v.isDeadOrDying()) {
            v.setHealth(0F);
            v.die(src);
        }
    }

    /** Herida en la skin (solo jugadores y con las heridas activas). cara < 0 = al azar. */
    public static void herida(LivingEntity v, Parte parte, Tipo tipo, int gravedad) {
        if (!(v instanceof ServerPlayer sp) || !Heridas.activo(sp.level())) return;
        HeridasDatos d = Heridas.leer(sp);
        Herida h = Heridas.alAzar(sp, parte, tipo, Math.max(1, Math.min(3, gravedad)), false);
        Heridas.agregar(d, h);
        Heridas.actualizar(sp, d);
    }
}
