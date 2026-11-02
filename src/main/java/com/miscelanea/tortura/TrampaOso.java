package com.miscelanea.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.armas.TemblorPacket;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trampa de oso invertida (Saw / The Pig de Dead by Daylight).
 * <ul>
 *   <li>Se pone con el objeto en la cabeza de un jugador o mob cercano (o en la tuya con Shift + clic derecho al aire).</li>
 *   <li>60 s de cuenta atrás. Las cajas Jigsaw a 32 bloques o menos son el puzle: una (al azar) tiene la llave.
 *       Registrar una caja = un minijuego al azar (CIRCULO, SECUENCIA, MACHACAR o GANZUA). Éxito: la registras
 *       (llave o nada). Fallo: esa caja se atasca 5 s.</li>
 *   <li>Si no hay ninguna caja cerca: 90 s y se avisa al captor de que ponga cajas; la llave va a parar a una de las
 *       que se pongan después (se comprueba cada segundo). Si rompen la caja de la llave antes de encontrarla, la llave
 *       se mueve a otra caja sin registrar.</li>
 *   <li>Con la llave (la de ESA trampa, o una maestra del creativo) clic derecho para quitártela.</li>
 *   <li>A 0: EJECUCIÓN (~3 s). La víctima se queda clavada (no se mueve, no hace nada, no le afecta otro daño;
 *       desconectarse la mata en el acto) y forcejea con la trampa ({@link #EJEC_ESTALLIDO} ticks); entonces las
 *       mandíbulas revientan (PLASH, sangre, temblor de cámara cerca) y a los {@link #EJEC_MUERTE} ticks muere con el
 *       daño propio (y su mensaje). Lo visual lo hace el cliente (EjecucionCliente, AnimEjecucion, TorturaLayer).
 *       Con el mod Incapacitated, ver {@link IncapCompat}.</li>
 *   <li>En creativo: Shift + clic derecho a la víctima con la mano vacía (o con otra trampa) la quita.</li>
 *   <li>Desconectarse congela el reloj (se guarda en el jugador y sigue al volver). Morir de otra forma la quita.</li>
 * </ul>
 */
public final class TrampaOso {
    private TrampaOso() {}

    public static final int DURACION = 60 * 20, DURACION_SIN_CAJAS = 90 * 20, RADIO = 32, ESPERA_FALLO = 100;
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(Miscelanea.MOD_ID, "trampa_oso"));

    /** Ejecución: ticks desde que el reloj llega a 0 hasta que revienta la trampa y hasta la muerte. */
    public static final int EJEC_ESTALLIDO = 52, EJEC_MUERTE = 60;

    private static final String[] CLAVES = {"Trampa", "TrampaId", "TrampaCaptor", "TrampaCaja", "SinCajas", "Buscadas", "Esperas", "LlaveDada",
            "Ejecucion", "EjecSnap", "EjecX", "EjecZ"};

    // ------------------------------------------------------------------ poner / quitar
    public static boolean poner(LivingEntity v, @Nullable Player captor) {
        if (!(v.level() instanceof ServerLevel sl) || Cautivos.conTrampa(v) || !v.isAlive()) return false;
        CompoundTag d = Cautivos.leer(v);
        for (String k : CLAVES) d.remove(k);
        d.putInt("TrampaId", 1 + v.getRandom().nextInt(99998));
        if (captor != null) d.putUUID("TrampaCaptor", captor.getUUID());
        List<BlockPos> cajas = cajasCerca(sl, v.blockPosition());
        if (cajas.isEmpty()) {
            d.putBoolean("SinCajas", true);
            d.putInt("Trampa", DURACION_SIN_CAJAS);
        } else {
            d.putInt("Trampa", DURACION);
            d.putLong("TrampaCaja", cajas.get(v.getRandom().nextInt(cajas.size())).asLong());
        }
        Cautivos.escribir(v, d);
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_PONER.get(), SoundSource.PLAYERS, 1.2F, 1F);
        if (v instanceof Player pv) {
            pv.sendSystemMessage(Component.translatable("msg.miscelanea.trampa.hola").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            pv.sendSystemMessage(Component.translatable(cajas.isEmpty() ? "msg.miscelanea.trampa.reglas_sin_cajas" : "msg.miscelanea.trampa.reglas",
                    cajas.size()).withStyle(ChatFormatting.RED));
        }
        if (captor != null && captor != v) {
            if (cajas.isEmpty())
                captor.sendSystemMessage(Component.translatable("msg.miscelanea.trampa.captor_sin_cajas", RADIO).withStyle(ChatFormatting.GOLD));
            else
                captor.displayClientMessage(Component.translatable("msg.miscelanea.trampa.captor", v.getDisplayName(), cajas.size())
                        .withStyle(ChatFormatting.GOLD), true);
        }
        Cautivos.sincronizar(v);
        return true;
    }

    /** Quita la trampa (con la llave, en creativo o por cualquier otro motivo). */
    public static void quitar(LivingEntity v, boolean conLlave) {
        CompoundTag d = Cautivos.leer(v);
        if (d.contains("Ejecucion")) {
            // en plena ejecución solo se puede parar (creativo) antes de que reviente
            if (d.getBoolean("EjecSnap")) return;
            enviarEjecucion(v, false, 0);
        }
        for (String k : CLAVES) d.remove(k);
        Cautivos.escribir(v, d);
        v.level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_QUITAR.get(), SoundSource.PLAYERS, 1.1F, 1F);
        if (conLlave && v instanceof Player pv)
            pv.displayClientMessage(Component.translatable("msg.miscelanea.trampa.libre").withStyle(ChatFormatting.GREEN), true);
        Cautivos.sincronizar(v);
    }

    /** Abrir la trampa de 'victima' con una llave. */
    public static boolean abrirCon(Player usuario, LivingEntity victima, ItemStack llave) {
        if (!Cautivos.conTrampa(victima)) {
            usuario.displayClientMessage(Component.translatable(usuario == victima ? "msg.miscelanea.trampa.no_llevas"
                    : "msg.miscelanea.trampa.no_lleva").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (ejecutando(victima)) {
            usuario.displayClientMessage(Component.translatable("msg.miscelanea.trampa.tarde").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        int id = llave.getTag() == null ? 0 : llave.getTag().getInt("Trampa");
        if (id != 0 && id != Cautivos.leer(victima).getInt("TrampaId")) {
            usuario.displayClientMessage(Component.translatable("msg.miscelanea.trampa.no_encaja").withStyle(ChatFormatting.RED), true);
            victima.level().playSound(null, victima.getX(), victima.getEyeY(), victima.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1F, 1.6F);
            return false;
        }
        quitar(victima, true);
        if (!usuario.getAbilities().instabuild) llave.shrink(1);
        return true;
    }

    // ------------------------------------------------------------------ cada tick (servidor)
    public static void tick(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        CompoundTag d = Cautivos.leer(v);
        if (d.contains("Ejecucion")) {
            tickEjecucion(v, sl, d);
            return;
        }
        int t = d.getInt("Trampa");
        if (t <= 0) return;
        t--;
        if (t <= 0) {
            empezarEjecucion(v, sl, d);
            return;
        }
        boolean prisa = t <= 200;
        if (t % 20 == 0 || (prisa && t % 10 == 0))
            sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_TIC.get(), SoundSource.PLAYERS,
                    prisa ? 0.9F : 0.6F, prisa ? 1.25F + (200 - t) / 400F : 1F);
        d.putInt("Trampa", t);
        boolean cambio = false;
        if (t % 20 == 10 && !d.getBoolean("LlaveDada")) cambio = revisarLlave(v, sl, d);
        Cautivos.escribir(v, d);
        if (cambio || t % 100 == 0) Cautivos.sincronizar(v);
    }

    /** ¿Sigue existiendo la caja con la llave? Si no, (re)esconde la llave en otra. Devuelve si hay que avisar al cliente. */
    private static boolean revisarLlave(LivingEntity v, ServerLevel sl, CompoundTag d) {
        boolean sinCajas = d.getBoolean("SinCajas");
        if (!sinCajas && d.contains("TrampaCaja")) {
            BlockPos p = BlockPos.of(d.getLong("TrampaCaja"));
            if (!sl.isLoaded(p) || sl.getBlockEntity(p) instanceof CajaJigsawBlockEntity) return false;
        }
        List<BlockPos> cajas = cajasCerca(sl, v.blockPosition());
        if (cajas.isEmpty()) {
            if (!sinCajas) d.putBoolean("SinCajas", true);
            d.remove("TrampaCaja");
            return !sinCajas;
        }
        List<BlockPos> libres = new ArrayList<>();
        for (BlockPos c : cajas) if (!buscada(d, c)) libres.add(c);
        if (libres.isEmpty()) libres = cajas;
        d.putLong("TrampaCaja", libres.get(v.getRandom().nextInt(libres.size())).asLong());
        d.remove("SinCajas");
        if (sinCajas && v instanceof Player pv)
            pv.sendSystemMessage(Component.translatable("msg.miscelanea.trampa.cajas_puestas", cajas.size()).withStyle(ChatFormatting.RED));
        return sinCajas;
    }

    // ------------------------------------------------------------------ ejecución
    /** ¿Está en plena ejecución (el reloj ya llegó a 0 y aún no ha muerto)? */
    public static boolean ejecutando(@Nullable Entity e) {
        return e != null && Cautivos.tieneAlgo(e) && Cautivos.leer(e).contains("Ejecucion");
    }

    /** Ticks que lleva la ejecución (0 si no hay). */
    public static int transcurrido(LivingEntity v) {
        CompoundTag d = Cautivos.leer(v);
        if (!d.contains("Ejecucion")) return 0;
        return (int) Math.max(0L, Math.min(100000L, v.level().getGameTime() - d.getLong("Ejecucion")));
    }

    /** Avisa a la víctima y a quien la ve de que empieza (o se anula) la ejecución. */
    static void enviarEjecucion(LivingEntity v, boolean activa, int transcurrido) {
        if (v.level().isClientSide) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> v), new EjecucionPacket(v.getId(), activa, transcurrido));
    }

    /** A quien empieza a ver a la víctima a mitad de la secuencia. */
    public static void enviarEjecucionA(LivingEntity v, ServerPlayer quien) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> quien), new EjecucionPacket(v.getId(), true, transcurrido(v)));
    }

    private static void empezarEjecucion(LivingEntity v, ServerLevel sl, CompoundTag d) {
        d.putInt("Trampa", 1);   // sigue "puesta" (se ve y no se puede quitar) hasta que mata
        d.putLong("Ejecucion", sl.getGameTime());
        d.putDouble("EjecX", v.getX());
        d.putDouble("EjecZ", v.getZ());
        Cautivos.escribir(v, d);
        v.stopRiding();
        if (v.isUsingItem()) v.stopUsingItem();
        if (v instanceof ServerPlayer sp) {
            Qte.cancelar(sp);
            if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
            sp.displayClientMessage(Component.translatable("msg.miscelanea.trampa.ejecucion").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
        if (v instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_FORCEJEO.get(), SoundSource.PLAYERS, 1.6F, 1F);
        Cautivos.sincronizar(v);
        enviarEjecucion(v, true, 0);
    }

    private static void tickEjecucion(LivingEntity v, ServerLevel sl, CompoundTag d) {
        long t = sl.getGameTime() - d.getLong("Ejecucion");
        if (t < 0 || t > EJEC_MUERTE + 100) {
            // estado viejo (el servidor se cerró a mitad, o se ha cambiado la hora): se acaba ya
            matar(v, sl, d);
            return;
        }
        congelar(v, d);
        if (t >= EJEC_ESTALLIDO && !d.getBoolean("EjecSnap")) {
            d.putBoolean("EjecSnap", true);
            Cautivos.escribir(v, d);
            estallar(v, sl);
        }
        if (t >= EJEC_MUERTE) {
            matar(v, sl, d);
            return;
        }
        // tictac desbocado mientras forcejea
        if (t < EJEC_ESTALLIDO && t % 3 == 0)
            sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_TIC.get(), SoundSource.PLAYERS, 0.9F, 1.5F + t / (float) EJEC_ESTALLIDO * 0.5F);
    }

    /** Clavada en el sitio: sin moverse (se la devuelve si se aleja), sin usar nada, sin objetivo. */
    private static void congelar(LivingEntity v, CompoundTag d) {
        Vec3 dm = v.getDeltaMovement();
        v.setDeltaMovement(0, Math.min(0, dm.y), 0);
        v.setSprinting(false);
        v.setJumping(false);
        if (v.isUsingItem()) v.stopUsingItem();
        double ax = d.getDouble("EjecX"), az = d.getDouble("EjecZ");
        double dx = v.getX() - ax, dz = v.getZ() - az;
        if (dx * dx + dz * dz > 0.09) {
            if (v instanceof ServerPlayer sp) sp.connection.teleport(ax, sp.getY(), az, sp.getYRot(), sp.getXRot());
            else v.teleportTo(ax, v.getY(), az);
        }
        if (v instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
            m.getMoveControl().setWantedPosition(m.getX(), m.getY(), m.getZ(), 0);
            m.setXxa(0);
            m.setYya(0);
            m.setZza(0);
        }
    }

    /** El PLASH: lo que oye y nota todo el mundo (las partículas y las salpicaduras las pone cada cliente). */
    private static void estallar(LivingEntity v, ServerLevel sl) {
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_EJECUCION.get(), SoundSource.PLAYERS, 3.0F, 1F);
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), TorturaRegistro.TRAMPA_CHASQUIDO.get(), SoundSource.PLAYERS, 1.2F, 0.8F);
        sl.sendParticles(ParticleTypes.CRIT, v.getX(), v.getEyeY(), v.getZ(), 16, 0.25, 0.2, 0.25, 0.4);
        TemblorPacket.enviar(sl, v.getEyePosition(), 18, 2.4F, 16);
    }

    /** Muerte con el daño propio (su mensaje y el captor como "asesino"). */
    private static void matar(LivingEntity v, ServerLevel sl, CompoundTag d) {
        UUID captorId = d.hasUUID("TrampaCaptor") ? d.getUUID("TrampaCaptor") : null;
        for (String k : CLAVES) d.remove(k);
        Cautivos.escribir(v, d);
        Cautivos.sincronizar(v);
        Entity captor = null;
        if (captorId != null) {
            captor = sl.getEntity(captorId);
            if (captor == null) captor = sl.getServer().getPlayerList().getPlayer(captorId);
        }
        if (captor == v) captor = null;
        Holder<DamageType> tipo = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DANO);
        DamageSource src = captor != null ? new DamageSource(tipo, captor) : new DamageSource(tipo);
        IncapCompat.asegurar();   // por si un /incap config reload ha rehecho su lista
        v.invulnerableTime = 0;
        v.hurt(src, 1000F);
        if (v.isAlive() && !v.isDeadOrDying()) {
            // algo ha frenado la muerte (otro mod; p. ej. ya estaba "derribado"): sin cabeza no se sobrevive
            v.setHealth(0F);
            v.die(src);
        }
    }

    /** Desconectarse en plena ejecución: muere en el acto (antes de que se guarde el jugador). */
    public static void ejecutarYa(LivingEntity v) {
        if (v.level() instanceof ServerLevel sl && ejecutando(v)) matar(v, sl, Cautivos.leer(v));
    }

    /** Ha muerto de otra forma en plena ejecución (/kill...): se anula lo que quedaba. */
    public static void alMorir(LivingEntity v) {
        if (!ejecutando(v)) return;
        CompoundTag d = Cautivos.leer(v);
        boolean revento = d.getBoolean("EjecSnap");
        for (String k : CLAVES) d.remove(k);
        Cautivos.escribir(v, d);
        Cautivos.sincronizar(v);
        // si ya había reventado, la cabeza sigue sin estar (el cliente la quita al reaparecer o al desaparecer el cuerpo)
        if (!revento) enviarEjecucion(v, false, 0);
    }

    // ------------------------------------------------------------------ cajas
    /** Cajas Jigsaw cargadas a RADIO bloques o menos. */
    public static List<BlockPos> cajasCerca(ServerLevel sl, BlockPos centro) {
        List<BlockPos> out = new ArrayList<>();
        int c0x = (centro.getX() - RADIO) >> 4, c1x = (centro.getX() + RADIO) >> 4;
        int c0z = (centro.getZ() - RADIO) >> 4, c1z = (centro.getZ() + RADIO) >> 4;
        double r2 = (double) RADIO * RADIO;
        for (int cx = c0x; cx <= c1x; cx++)
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk ch = sl.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values())
                    if (be instanceof CajaJigsawBlockEntity && be.getBlockPos().distSqr(centro) <= r2) out.add(be.getBlockPos());
            }
        return out;
    }

    private static boolean buscada(CompoundTag d, BlockPos p) {
        ListTag l = d.getList("Buscadas", Tag.TAG_LONG);
        long v = p.asLong();
        for (int i = 0; i < l.size(); i++) if (((LongTag) l.get(i)).getAsLong() == v) return true;
        return false;
    }

    /** Clic derecho en una caja Jigsaw. */
    public static void buscar(ServerPlayer p, BlockPos pos) {
        if (!Cautivos.conTrampa(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.caja.cerrada").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Qte.activo(p)) return;
        CompoundTag d = Cautivos.leer(p);
        if (buscada(d, pos)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.caja.ya_vacia").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long espera = d.getCompound("Esperas").getLong(Long.toString(pos.asLong())) - p.level().getGameTime();
        if (espera > 0) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.caja.atascada", (espera + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        QteTipo[] tipos = {QteTipo.CIRCULO, QteTipo.SECUENCIA, QteTipo.MACHACAR, QteTipo.GANZUA};
        QteTipo tipo = tipos[p.getRandom().nextInt(tipos.length)];
        QteParams params = QteParams.de(0.35F + p.getRandom().nextFloat() * 0.25F).rondas(2)
                .titulo(Component.translatable("qte.miscelanea.titulo.caja")).ancla(Vec3.atCenterOf(pos), 4.5);
        p.level().playSound(null, pos, TorturaRegistro.CAJA_BUSCAR.get(), SoundSource.BLOCKS, 1F, 1F);
        Qte.iniciar(p, tipo, params, (j, r) -> resultadoCaja(j, pos, r));
    }

    private static void resultadoCaja(@Nullable ServerPlayer j, BlockPos pos, QteResultado r) {
        if (j == null || !Cautivos.conTrampa(j) || r == QteResultado.CANCELADO) return;
        CompoundTag d = Cautivos.leer(j);
        if (r.exito()) {
            ListTag l = d.getList("Buscadas", Tag.TAG_LONG);
            l.add(LongTag.valueOf(pos.asLong()));
            d.put("Buscadas", l);
            boolean llave = !d.getBoolean("LlaveDada") && d.contains("TrampaCaja") && d.getLong("TrampaCaja") == pos.asLong();
            if (llave) {
                d.putBoolean("LlaveDada", true);
                ItemStack k = new ItemStack(TorturaRegistro.LLAVE_TRAMPA.get());
                k.getOrCreateTag().putInt("Trampa", d.getInt("TrampaId"));
                ItemHandlerHelper.giveItemToPlayer(j, k);
                j.level().playSound(null, pos, TorturaRegistro.LLAVE_ENCONTRADA.get(), SoundSource.BLOCKS, 1.2F, 1F);
                j.displayClientMessage(Component.translatable("msg.miscelanea.caja.llave").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            } else {
                j.level().playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.8F, 0.7F);
                j.displayClientMessage(Component.translatable("msg.miscelanea.caja.vacia").withStyle(ChatFormatting.GRAY), true);
            }
        } else {
            CompoundTag es = d.getCompound("Esperas");
            es.putLong(Long.toString(pos.asLong()), j.level().getGameTime() + ESPERA_FALLO);
            d.put("Esperas", es);
            j.displayClientMessage(Component.translatable("msg.miscelanea.caja.atascada", ESPERA_FALLO / 20).withStyle(ChatFormatting.RED), true);
        }
        Cautivos.escribir(j, d);
    }
}
