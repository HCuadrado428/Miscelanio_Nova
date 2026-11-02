package com.miscelanea.tortura.efectos;

import com.miscelanea.armas.TemblorPacket;
import com.miscelanea.tortura.Cautivos;
import com.miscelanea.tortura.IncapCompat;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Collar explosivo (Fallout: New Vegas – Dead Money). Estado en los datos persistentes de quien lo lleva (se guarda con
 * el jugador o el mob):
 * <pre>
 * miscelanea_collar: { Id (UUID del collar), Captor (UUID, quien lo puso), Baliza: {X, Y, Z, Dim} (puesto a uno mismo),
 *                      Radio, Semilla (panel de cables), Errores, Alarma (ticks que quedan), Detona (ticks), Causa (UUID) }
 * </pre>
 * La "radio" es el captor o quien lleve el detonador enlazado (nunca el propio cautivo): cuenta el más cercano. Si se
 * lo puso uno mismo, una baliza fija donde se lo puso. Si se aleja más de {@link #RADIO} bloques empieza a pitar cada vez más
 * deprisa ({@link #ALARMA} ticks; menos si ya se equivocó de cable) y, si no vuelve, estalla. Sin emisor (captor
 * desconectado y nadie con el detonador) se queda dormido. El detonador también lo hace estallar o lo abre a distancia.
 * Desactivarlo: alicates + minijuego CORTAR_CABLES (el mismo panel en cada intento); dos cables malos, y revienta.
 */
public final class Collares {
    private Collares() {}

    public static final String CLAVE = "miscelanea_collar";
    public static final int RADIO = 24, ALARMA = 200, ALARMA_ERROR = 120, DETONA = 16;
    public static final float DIFICULTAD = 0.55F;
    public static final int SENAL_OK = 0, SENAL_NADA = 1, SENAL_BALIZA = 2, SENAL_OTRA_DIM = 3;

    public static boolean tiene(Entity e) {
        return e.getPersistentData().contains(CLAVE);
    }

    public static CompoundTag leer(Entity e) {
        return e.getPersistentData().getCompound(CLAVE);
    }

    private static void escribir(Entity e, CompoundTag d) {
        e.getPersistentData().put(CLAVE, d);
    }

    @Nullable
    public static UUID id(Entity e) {
        if (!tiene(e)) return null;
        CompoundTag d = leer(e);
        return d.hasUUID("Id") ? d.getUUID("Id") : null;
    }

    public static boolean detonando(Entity e) {
        return tiene(e) && leer(e).contains("Detona");
    }

    /** ¿Está inmovilizado? (grilletes, lazo, trampa, montado en algo: cepo, jaula, aparato...) */
    public static boolean cautivo(LivingEntity t) {
        return Cautivos.atado(t) || Cautivos.lazo(t) != 0 || Cautivos.conTrampa(t) || t.getVehicle() != null;
    }

    // ------------------------------------------------------------------ poner y quitar
    /** 'quien' le pone el collar a 'v' (a sí mismo con Shift + clic derecho). Devuelve true si se ha puesto. */
    public static boolean poner(Player quien, LivingEntity v, ItemStack st) {
        if (!(v.level() instanceof ServerLevel sl)) return false;
        if (tiene(v)) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.collar.ya").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (v != quien && v instanceof Player && !cautivo(v) && !quien.getAbilities().instabuild) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.collar.no_cautivo").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        CompoundTag d = new CompoundTag();
        UUID id = UUID.randomUUID();
        d.putUUID("Id", id);
        d.putInt("Radio", RADIO);
        d.putLong("Semilla", sl.getRandom().nextLong() | 1L);
        if (v == quien) {
            CompoundTag b = new CompoundTag();
            b.putDouble("X", v.getX());
            b.putDouble("Y", v.getY());
            b.putDouble("Z", v.getZ());
            b.putString("Dim", v.level().dimension().location().toString());
            d.put("Baliza", b);
        } else {
            d.putUUID("Captor", quien.getUUID());
        }
        escribir(v, d);
        sincronizar(v);
        if (v instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();   // que no desaparezca con el collar puesto
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), EfectosRegistro.COLLAR_CERRAR.get(), SoundSource.PLAYERS, 1F, 1F);
        if (!quien.getAbilities().instabuild) st.shrink(1);
        // el detonador enlazado: es la "radio" (quien lo lleve marca el radio) y sirve para hacerlo estallar o abrirlo
        ItemStack det = new ItemStack(EfectosRegistro.DETONADOR.get());
        CompoundTag t = det.getOrCreateTag();
        t.putUUID("Collar", id);
        t.putUUID("Victima", v.getUUID());
        t.putString("Nombre", v.getName().getString());
        ItemHandlerHelper.giveItemToPlayer(quien, det);
        quien.displayClientMessage(Component.translatable(v == quien ? "msg.miscelanea.collar.puesto_mismo" : "msg.miscelanea.collar.puesto",
                v.getDisplayName(), RADIO).withStyle(ChatFormatting.GOLD), true);
        if (v instanceof ServerPlayer sp && v != quien)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.collar.victima", RADIO).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        return true;
    }

    /** Lo abre (alicates, detonador...): suelta el collar junto a la víctima o se lo da a 'aQuien'. */
    public static void quitar(LivingEntity v, @Nullable Player aQuien) {
        if (!tiene(v)) return;
        v.getPersistentData().remove(CLAVE);
        sincronizar(v);
        if (v instanceof ServerPlayer sp && Qte.tipoActivo(sp) == QteTipo.CORTAR_CABLES) Qte.cancelar(sp);
        v.level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), EfectosRegistro.COLLAR_ABRIR.get(), SoundSource.PLAYERS, 1F, 1F);
        ItemStack c = new ItemStack(EfectosRegistro.COLLAR.get());
        if (aQuien != null) ItemHandlerHelper.giveItemToPlayer(aQuien, c);
        else v.spawnAtLocation(c, (float) (v.getEyeY() - v.getY()) * 0.6F);
    }

    /** Muerte por otra causa, cambio de cuerpo...: se borra sin más. */
    public static void borrar(LivingEntity v) {
        if (!tiene(v)) return;
        v.getPersistentData().remove(CLAVE);
        sincronizar(v);
    }

    // ------------------------------------------------------------------ tick (servidor)
    /** Dónde está la radio y si está fuera de alcance. */
    private record Senal(int tipo, double dist, boolean fuera) {}

    private static Senal senal(LivingEntity v, CompoundTag d) {
        MinecraftServer srv = v.getServer();
        int radio = d.getInt("Radio") <= 0 ? RADIO : d.getInt("Radio");
        UUID id = d.hasUUID("Id") ? d.getUUID("Id") : null;
        UUID captor = d.hasUUID("Captor") ? d.getUUID("Captor") : null;
        boolean alguno = false;
        double mejor = Double.MAX_VALUE;
        if (srv != null && id != null) {
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                if (p == v || p.isSpectator()) continue;
                boolean emisor = (captor != null && p.getUUID().equals(captor)) || DetonadorItem.lleva(p, id);
                if (!emisor) continue;
                alguno = true;
                if (p.level() == v.level()) mejor = Math.min(mejor, p.distanceTo(v));
            }
        }
        if (alguno) {
            if (mejor == Double.MAX_VALUE) return new Senal(SENAL_OTRA_DIM, -1, true);
            return new Senal(SENAL_OK, mejor, mejor > radio);
        }
        if (d.contains("Baliza")) {
            CompoundTag b = d.getCompound("Baliza");
            if (!v.level().dimension().location().toString().equals(b.getString("Dim"))) return new Senal(SENAL_OTRA_DIM, -1, true);
            double dist = v.position().distanceTo(new Vec3(b.getDouble("X"), b.getDouble("Y"), b.getDouble("Z")));
            return new Senal(SENAL_BALIZA, dist, dist > radio);
        }
        return new Senal(SENAL_NADA, -1, false);
    }

    public static void tick(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        CompoundTag d = leer(v);
        if (d.contains("Detona")) {
            int t = d.getInt("Detona") - 1;
            if (t <= 0) {
                explotar(v, sl, d);
                return;
            }
            d.putInt("Detona", t);
            escribir(v, d);
            return;
        }
        if (v.tickCount % 5 != 0) return;
        Senal s = senal(v, d);
        int alarma = d.contains("Alarma") ? d.getInt("Alarma") : -1;
        boolean cambio = false;
        if (s.fuera()) {
            if (alarma < 0) {
                alarma = d.getInt("Errores") > 0 ? ALARMA_ERROR : ALARMA;
                d.putInt("AlarmaMax", alarma);
                cambio = true;
                if (v instanceof ServerPlayer sp)
                    sp.displayClientMessage(Component.translatable("msg.miscelanea.collar.alarma").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), EfectosRegistro.COLLAR_ESTATICA.get(), SoundSource.PLAYERS, 0.8F, 1F);
            } else {
                alarma -= 5;
                if (alarma <= 0) {
                    d.remove("Alarma");
                    escribir(v, d);
                    detonar(v, null);
                    return;
                }
            }
            d.putInt("Alarma", alarma);
        } else if (alarma >= 0) {
            d.remove("Alarma");
            cambio = true;
            if (v instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("msg.miscelanea.collar.vuelve").withStyle(ChatFormatting.GREEN), true);
        }
        int antes = d.getInt("Senal");
        if (antes != s.tipo()) {
            d.putInt("Senal", s.tipo());
            cambio = true;
        }
        escribir(v, d);
        if (cambio) sincronizar(v);
        else if (v instanceof ServerPlayer sp && v.tickCount % 10 == 0) EfectosRed.a(sp, paquete(v, d, (float) s.dist()));
    }

    // ------------------------------------------------------------------ detonar y estallar
    /** Empieza la cuenta final (el tono continuo de Dead Money). 'causa' = quien lo detona (o null). */
    public static void detonar(LivingEntity v, @Nullable Entity causa) {
        if (!tiene(v) || !(v.level() instanceof ServerLevel sl)) return;
        CompoundTag d = leer(v);
        if (d.contains("Detona")) return;
        d.putInt("Detona", DETONA);
        d.remove("Alarma");
        if (causa != null && causa != v) d.putUUID("Causa", causa.getUUID());
        escribir(v, d);
        sincronizar(v);
        if (v instanceof ServerPlayer sp && Qte.activo(sp)) Qte.cancelar(sp);
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), EfectosRegistro.COLLAR_TONO.get(), SoundSource.PLAYERS, 1.6F, 1F);
    }

    /** Desconectarse con la cuenta final en marcha: revienta ya (antes de que se guarde). */
    public static void estallarYa(LivingEntity v) {
        if (detonando(v) && v.level() instanceof ServerLevel sl) explotar(v, sl, leer(v));
    }

    private static void explotar(LivingEntity v, ServerLevel sl, CompoundTag d) {
        UUID causaId = d.hasUUID("Causa") ? d.getUUID("Causa") : d.hasUUID("Captor") ? d.getUUID("Captor") : null;
        v.getPersistentData().remove(CLAVE);
        sincronizar(v);
        double x = v.getX(), y = v.getEyeY() - 0.3, z = v.getZ();
        Entity causa = null;
        if (causaId != null) {
            causa = sl.getEntity(causaId);
            if (causa == null) causa = sl.getServer().getPlayerList().getPlayer(causaId);
        }
        if (causa == v) causa = null;
        Holder<DamageType> tipo = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(EfectosRegistro.DANO_COLLAR);
        DamageSource src = causa != null ? new DamageSource(tipo, causa) : new DamageSource(tipo);
        IncapCompat.asegurar();
        v.invulnerableTime = 0;
        v.hurt(src, 1000F);
        if (v.isAlive() && !v.isDeadOrDying()) {
            v.setHealth(0F);
            v.die(src);
        }
        // la explosión no rompe bloques (NONE), pero empuja y hiere a quien esté cerca
        sl.explode(null, x, y, z, 2.2F, Level.ExplosionInteraction.NONE);
        sl.playSound(null, x, y, z, EfectosRegistro.COLLAR_ESTALLIDO.get(), SoundSource.PLAYERS, 3F, 1F);
        TemblorPacket.enviar(sl, new Vec3(x, y, z), 24, 2.6F, 18);
        SustoPacket sp = new SustoPacket(SustoPacket.ESTALLIDO, x, y, z);
        for (ServerPlayer p : sl.players()) if (p.position().distanceToSqr(x, y, z) < 64 * 64) EfectosRed.a(p, sp);
    }

    // ------------------------------------------------------------------ alicates: cortar cables
    /** 'quien' intenta desactivar el collar de 'v' (puede ser él mismo) con los alicates. */
    public static void cortarCables(ServerPlayer quien, LivingEntity v) {
        if (!tiene(v)) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.alicates.nada").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Qte.activo(quien)) return;
        if (detonando(v)) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.alicates.tarde").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        if (v != quien && quien.distanceTo(v) > 3.5) {
            quien.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag d = leer(v);
        UUID id = d.getUUID("Id");
        long semilla = d.getLong("Semilla") | 1L;
        quien.level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), EfectosRegistro.COLLAR_PITIDO.get(), SoundSource.PLAYERS, 0.6F, 0.7F);
        Qte.iniciar(quien, QteTipo.CORTAR_CABLES, QteParams.de(DIFICULTAD).semilla(semilla)
                .titulo(Component.translatable("qte.miscelanea.titulo.cables")).ancla(v.position(), 4.5), (j, r) -> {
            if (j == null || r == QteResultado.CANCELADO || !v.isAlive() || !id.equals(id(v)) || detonando(v)) return;
            if (r.exito()) {
                quitar(v, null);
                j.displayClientMessage(Component.translatable("msg.miscelanea.alicates.abierto").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                return;
            }
            CompoundTag d2 = leer(v);
            int err = d2.getInt("Errores") + 1;
            d2.putInt("Errores", err);
            escribir(v, d2);
            if (err >= 2) {
                j.displayClientMessage(Component.translatable("msg.miscelanea.alicates.boom").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                detonar(v, null);
            } else {
                j.displayClientMessage(Component.translatable("msg.miscelanea.alicates.error").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                j.level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), EfectosRegistro.COLLAR_ESTATICA.get(), SoundSource.PLAYERS, 1F, 1.3F);
                sincronizar(v);
            }
        });
    }

    // ------------------------------------------------------------------ buscar a la víctima de un detonador
    @Nullable
    public static LivingEntity buscar(MinecraftServer srv, UUID victima) {
        ServerPlayer p = srv.getPlayerList().getPlayer(victima);
        if (p != null) return p;
        for (ServerLevel l : srv.getAllLevels()) {
            Entity e = l.getEntity(victima);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    // ------------------------------------------------------------------ red
    static CollarPacket paquete(LivingEntity v, CompoundTag d, float dist) {
        return new CollarPacket(v.getId(), true, d.contains("Alarma") ? d.getInt("Alarma") : -1, d.getInt("AlarmaMax"),
                d.contains("Detona") ? d.getInt("Detona") : -1, d.getInt("Errores"), d.getInt("Senal"),
                d.getInt("Radio") <= 0 ? RADIO : d.getInt("Radio"), dist);
    }

    /** Manda el estado a quien ve a la entidad y a ella misma. */
    public static void sincronizar(LivingEntity v) {
        if (v.level().isClientSide) return;
        if (!tiene(v)) {
            EfectosRed.alrededor(v, CollarPacket.quitado(v.getId()));
            return;
        }
        CompoundTag d = leer(v);
        float dist = -1F;
        if (v instanceof ServerPlayer) dist = (float) senal(v, d).dist();
        EfectosRed.alrededor(v, paquete(v, d, dist));
    }

    public static void sincronizarCon(LivingEntity v, ServerPlayer quien) {
        if (tiene(v)) EfectosRed.a(quien, paquete(v, leer(v), -1F));
    }
}
