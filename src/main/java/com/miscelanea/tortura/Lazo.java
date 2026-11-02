package com.miscelanea.tortura;

import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lazo (Red Dead Redemption 2). Estados de la víctima (en {@link Cautivos}, clave "Lazo"):
 * <ol>
 *   <li>{@link #ENLAZADO}: tira y afloja. La cuerda une la mano del captor con el torso de la víctima y tiene un largo
 *       máximo que se acorta solo (más si el captor mantiene clic derecho). El captor juega BARRA_ZONA ("tensión"): si
 *       gana, la víctima cae derribada; si pierde, la cuerda se rompe. Si la víctima es un jugador juega MACHACAR: si
 *       gana antes, se suelta (si falla, vuelve a intentarlo). Un mob forcejea con una fuerza según su vida máxima (y la
 *       tensión es más difícil).</li>
 *   <li>{@link #DERRIBADO}: en el suelo unos segundos. El captor (o alguien en creativo) le hace clic derecho: ALTERNAR
 *       (A/D) para maniatarlo. Si nadie lo ata a tiempo, se levanta.</li>
 *   <li>{@link #MANIATADO}: tumbado, muñecas y tobillos atados; no anda, no pega, no usa nada. Se le puede cargar al
 *       hombro (Shift + clic derecho con la mano vacía; Shift otra vez lo deja en el suelo) y subir a un caballo (clic
 *       derecho al caballo mientras lo cargas; luego clic derecho al caballo para montar tú delante). Un jugador maniatado
 *       forcejea (MACHACAR) cada 20 s: si lo supera, puede soltarse con una probabilidad según lo rápido que fue; a los
 *       2 minutos se suelta solo. Los mobs no se sueltan solos.</li>
 * </ol>
 * En creativo, Shift + clic derecho con el lazo a alguien enlazado, derribado o maniatado lo suelta al momento.
 * Morir, desconectarse o cambiar de dimensión (cualquiera de los dos) lo limpia todo.
 */
public final class Lazo {
    private Lazo() {}

    public static final int ENLAZADO = 1, DERRIBADO = 2, MANIATADO = 3;
    public static final double ALCANCE_MIN = 5, ALCANCE_MAX = 16, LARGO_MIN = 2.2, ROMPE = 24, DIST_ATAR = 3.5;
    /** Ticks volteando para la carga completa; menos de MIN_TICKS no lanza. */
    public static final int CARGA_TICKS = 30, MIN_TICKS = 6;
    public static final int DERRIBO_TICKS = 10 * 20, MANIATADO_TICKS = 2 * 60 * 20, ESCAPE_CADA = 20 * 20, TIRON_MAX = 40 * 20;
    private static final UUID VELOCIDAD_ID = UUID.fromString("5b0c9e3a-6a43-4f0e-9a51-1a2f7c3e1d42");
    private static final String[] CLAVES = {"Lazo", "LazoCaptor", "LazoT"};

    /** Tira y afloja en curso (solo en memoria: si el servidor se reinicia, la cuerda se rompe). */
    private static final class Tiron {
        final UUID captor;
        final float fuerza;
        double largo;
        int ticks;
        float difVictima = 0.7F;

        Tiron(UUID captor, double largo, float fuerza) {
            this.captor = captor;
            this.largo = largo;
            this.fuerza = fuerza;
        }
    }

    /** Por víctima. */
    private static final Map<UUID, Tiron> TIRONES = new HashMap<>();
    /** Captor → id de su lazo volando. */
    private static final Map<UUID, Integer> LANZADOS = new HashMap<>();
    /** Víctimas que alguien está maniatando ahora (el tiempo de derribo se para). */
    private static final Set<UUID> ATANDO = new HashSet<>();
    /** Quien carga a alguien y ya ha dejado de agacharse desde que lo cogió (la siguiente vez que se agache, lo suelta). */
    private static final Set<UUID> SOLTAR_LISTO = new HashSet<>();

    // ------------------------------------------------------------------ consultas
    public static int estado(Entity e) {
        return Cautivos.lazo(e);
    }

    /** Derribado o maniatado: no se mueve. */
    public static boolean inmovil(Entity e) {
        return estado(e) >= DERRIBADO;
    }

    /** Cualquier estado del lazo: no puede atacar ni usar nada. */
    public static boolean sinManos(Entity e) {
        return estado(e) != 0;
    }

    /** ¿Tiene un lazo volando? (si la entidad ya no existe, p. ej. se descargó su chunk, se olvida) */
    public static boolean lanzado(Player p) {
        Integer id = LANZADOS.get(p.getUUID());
        if (id == null) return false;
        Entity e = p.level().getEntity(id);
        if (e instanceof LazoEntity && !e.isRemoved()) return true;
        LANZADOS.remove(p.getUUID());
        return false;
    }

    /** Víctima del tira y afloja de este captor (o null). */
    @Nullable
    public static UUID tiraDe(Player p) {
        for (Map.Entry<UUID, Tiron> en : TIRONES.entrySet()) if (en.getValue().captor.equals(p.getUUID())) return en.getKey();
        return null;
    }

    public static boolean esCaptor(Entity v, Player p) {
        UUID c = Cautivos.lazoCaptor(v);
        return c != null && c.equals(p.getUUID());
    }

    static boolean esJefe(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || e instanceof EnderDragon || e instanceof WitherBoss
                || e instanceof Warden || e instanceof ElderGuardian;
    }

    /** ¿Se puede enlazar? Jugadores (no espectadores) y mobs que no sean jefes, libres y que no vayan cargados por él. */
    public static boolean valido(Player captor, Entity e) {
        if (!(e instanceof LivingEntity le) || e == captor || !le.isAlive()) return false;
        if (e instanceof Player p) {
            if (p.isSpectator()) return false;
        } else if (!(e instanceof Mob)) return false;
        if (esJefe(le) || estado(le) != 0) return false;
        return e.getVehicle() != captor;
    }

    /** Fuerza con la que forcejea (0..1): jugadores 0,5; mobs según su vida máxima. */
    static float fuerza(LivingEntity v) {
        if (v instanceof Player) return 0.5F;
        return Mth.clamp((v.getMaxHealth() - 4F) / 60F, 0.05F, 1F);
    }

    // ------------------------------------------------------------------ lanzar
    public static void lanzar(ServerPlayer p, int usado) {
        if (usado < MIN_TICKS || tiraDe(p) != null || lanzado(p) || sinManos(p)) return;
        float carga = Mth.clamp(usado / (float) CARGA_TICKS, 0F, 1F);
        LazoEntity l = new LazoEntity(p.level(), p, carga);
        Vec3 ojo = p.getEyePosition(), mira = p.getLookAngle();
        l.setPos(ojo.x + mira.x * 0.5, ojo.y - 0.1 + mira.y * 0.5, ojo.z + mira.z * 0.5);
        l.shoot(mira.x, mira.y + 0.05, mira.z, 0.95F + 1.05F * carga, 0.5F);
        p.level().addFreshEntity(l);
        LANZADOS.put(p.getUUID(), l.getId());
        p.swing(p.getUsedItemHand() == null ? InteractionHand.MAIN_HAND : p.getUsedItemHand(), true);
        sonido(p, TorturaRegistro.LAZO_LANZAR.get(), 0.9F, 0.9F + carga * 0.25F);
    }

    static void alQuitarLazo(ServerPlayer p, LazoEntity l) {
        LANZADOS.remove(p.getUUID(), l.getId());
    }

    // ------------------------------------------------------------------ enlazar / tira y afloja
    public static void enlazar(ServerPlayer captor, LivingEntity v) {
        if (v.isPassenger()) v.stopRiding();   // lo saca del caballo (o de donde vaya)
        CompoundTag d = Cautivos.leer(v);
        for (String k : CLAVES) d.remove(k);
        d.putInt("Lazo", ENLAZADO);
        d.putUUID("LazoCaptor", captor.getUUID());
        Cautivos.escribir(v, d);
        Tiron t = new Tiron(captor.getUUID(), Mth.clamp(captor.distanceTo(v), LARGO_MIN + 1, ALCANCE_MAX), fuerza(v));
        TIRONES.put(v.getUUID(), t);
        calmar(v);
        if (v instanceof ServerPlayer sp) {
            Qte.cancelar(sp);
            sp.displayClientMessage(Component.translatable("msg.miscelanea.lazo.enlazado").withStyle(ChatFormatting.RED), true);
        }
        captor.displayClientMessage(Component.translatable("msg.miscelanea.lazo.atrapado", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        sonido(v, TorturaRegistro.LAZO_ATRAPAR.get(), 1F, 1F);
        Cautivos.sincronizar(v);
        // los minijuegos empiezan un pelín después (que se vea el lazo cerrarse)
        Qte.despues(8, () -> empezarTiron(captor, v, t));
    }

    private static boolean vigente(LivingEntity v, Tiron t) {
        return TIRONES.get(v.getUUID()) == t && estado(v) == ENLAZADO && v.isAlive();
    }

    private static void empezarTiron(ServerPlayer captor, LivingEntity v, Tiron t) {
        if (!vigente(v, t) || captor.hasDisconnected() || !captor.isAlive()) return;
        float dif = v instanceof Player ? 0.45F : 0.15F + 0.75F * t.fuerza;
        Qte.iniciar(captor, QteTipo.BARRA_ZONA, QteParams.de(dif).titulo(Component.translatable("qte.miscelanea.titulo.lazo_tension")),
                (p, r) -> resultadoCaptor(v, t, r));
        if (v instanceof ServerPlayer sp) forcejeoVictima(sp, t);
    }

    private static void forcejeoVictima(ServerPlayer sp, Tiron t) {
        if (!vigente(sp, t) || sp.hasDisconnected()) return;
        Qte.iniciar(sp, QteTipo.MACHACAR, QteParams.de(t.difVictima).titulo(Component.translatable("qte.miscelanea.titulo.lazo_soltarse")),
                (p, r) -> resultadoVictima(sp, t, r));
    }

    private static void resultadoCaptor(LivingEntity v, Tiron t, QteResultado r) {
        if (!vigente(v, t)) return;
        ServerPlayer captor = jugador(v, t.captor);
        if (r.exito() && captor != null) derribar(v, captor);
        else romper(v, true);
    }

    private static void resultadoVictima(ServerPlayer sp, Tiron t, QteResultado r) {
        if (!vigente(sp, t)) return;
        if (r.exito()) {
            romper(sp, true);
            sp.displayClientMessage(Component.translatable("msg.miscelanea.lazo.te_sueltas").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            return;
        }
        // falló (o se cortó): vuelve a intentarlo enseguida, cada vez un poco más difícil
        t.difVictima = Math.min(0.92F, t.difVictima + 0.05F);
        Qte.despues(r == QteResultado.CANCELADO ? 40 : 24, () -> forcejeoVictima(sp, t));
    }

    @Nullable
    private static ServerPlayer jugador(Entity cerca, UUID u) {
        if (cerca.level().getServer() == null) return null;
        return cerca.level().getServer().getPlayerList().getPlayer(u);
    }

    /** La cuerda se rompe (o se suelta) y la víctima queda libre. */
    public static void romper(LivingEntity v, boolean conSonido) {
        Tiron t = TIRONES.remove(v.getUUID());
        if (t != null) {
            ServerPlayer c = jugador(v, t.captor);
            if (c != null) {
                if (Qte.tipoActivo(c) == QteTipo.BARRA_ZONA) Qte.cancelar(c);
                c.displayClientMessage(Component.translatable("msg.miscelanea.lazo.roto").withStyle(ChatFormatting.GRAY), true);
                if (c.isUsingItem() && LazoItem.usando(c)) c.stopUsingItem();
            }
            if (v instanceof ServerPlayer sp && Qte.tipoActivo(sp) == QteTipo.MACHACAR) Qte.cancelar(sp);
        }
        if (conSonido) sonido(v, TorturaRegistro.LAZO_ROMPER.get(), 1F, 0.9F + v.getRandom().nextFloat() * 0.2F);
        limpiar(v);
    }

    private static void derribar(LivingEntity v, ServerPlayer captor) {
        TIRONES.remove(v.getUUID());
        if (v instanceof ServerPlayer sp) {
            if (Qte.tipoActivo(sp) == QteTipo.MACHACAR) Qte.cancelar(sp);
            sp.displayClientMessage(Component.translatable("msg.miscelanea.lazo.derribado_tu").withStyle(ChatFormatting.RED), true);
        }
        if (captor.isUsingItem() && LazoItem.usando(captor)) captor.stopUsingItem();
        CompoundTag d = Cautivos.leer(v);
        d.putInt("Lazo", DERRIBADO);
        d.putInt("LazoT", DERRIBO_TICKS);
        Cautivos.escribir(v, d);
        inmovilizar(v);
        // el último tirón: lo tumba hacia el captor
        Vec3 dir = captor.position().subtract(v.position());
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();
        v.setDeltaMovement(dir.x * 0.35, 0.15, dir.z * 0.35);
        v.hurtMarked = true;
        sonido(v, TorturaRegistro.LAZO_ATRAPAR.get(), 1.1F, 0.7F);
        captor.displayClientMessage(Component.translatable("msg.miscelanea.lazo.derribado", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        Cautivos.sincronizar(v);
    }

    // ------------------------------------------------------------------ maniatar
    /** El captor (o alguien en creativo) empieza a maniatar a un derribado: ALTERNAR. */
    public static void atar(ServerPlayer p, LivingEntity v) {
        if (estado(v) != DERRIBADO) return;
        if (!esCaptor(v, p) && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.lazo.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (p.distanceTo(v) > DIST_ATAR) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (ATANDO.contains(v.getUUID()) || Qte.activo(p)) return;
        ATANDO.add(v.getUUID());
        sonido(v, TorturaRegistro.LAZO_TENSION.get(), 0.6F, 1.3F);
        Qte.iniciar(p, QteTipo.ALTERNAR, QteParams.de(0.45F).titulo(Component.translatable("qte.miscelanea.titulo.lazo_atar"))
                .ancla(v.position(), DIST_ATAR + 1.5), (j, r) -> {
            ATANDO.remove(v.getUUID());
            if (j == null || !v.isAlive() || estado(v) != DERRIBADO) return;
            if (r.exito()) maniatar(v, j);
            else if (r == QteResultado.FALLO)
                j.displayClientMessage(Component.translatable("msg.miscelanea.lazo.atar_fallo").withStyle(ChatFormatting.YELLOW), true);
        });
    }

    public static void maniatar(LivingEntity v, Player captor) {
        CompoundTag d = Cautivos.leer(v);
        d.putInt("Lazo", MANIATADO);
        d.putUUID("LazoCaptor", captor.getUUID());
        d.putInt("LazoT", MANIATADO_TICKS);
        Cautivos.escribir(v, d);
        inmovilizar(v);
        sonido(v, TorturaRegistro.LAZO_ATAR.get(), 1F, 1F);
        if (v instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.lazo.maniatado_tu").withStyle(ChatFormatting.RED), true);
        captor.displayClientMessage(Component.translatable("msg.miscelanea.lazo.maniatado", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        Cautivos.sincronizar(v);
    }

    /** Lo suelta del todo (creativo, se acaba el tiempo o se escapa). */
    public static void soltar(LivingEntity v, boolean conSonido) {
        TIRONES.remove(v.getUUID());
        if (conSonido) sonido(v, TorturaRegistro.LAZO_ROMPER.get(), 0.7F, 1.2F);
        limpiar(v);
    }

    /** Quita todo el estado del lazo sin avisos (muerte, desconexión, dimensión...). */
    public static void limpiar(LivingEntity v) {
        TIRONES.remove(v.getUUID());
        ATANDO.remove(v.getUUID());
        if (v.getVehicle() instanceof Player h) bajar(v, h, false);
        boolean habia = estado(v) != 0;
        CompoundTag d = Cautivos.leer(v);
        for (String k : CLAVES) d.remove(k);
        Cautivos.escribir(v, d);
        quitarInmovil(v);
        if (habia) Cautivos.sincronizar(v);
    }

    // ------------------------------------------------------------------ cargar al hombro / caballo
    private static void avisarPasajeros(Entity vehiculo) {
        if (vehiculo instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetPassengersPacket(sp));
    }

    @Nullable
    public static LivingEntity cargado(Player p) {
        for (Entity e : p.getPassengers()) if (e instanceof LivingEntity le && estado(le) == MANIATADO) return le;
        return null;
    }

    public static void cargar(Player p, LivingEntity v) {
        if (v.isPassenger()) v.stopRiding();
        if (!v.startRiding(p, true)) return;
        avisarPasajeros(p);
        SOLTAR_LISTO.remove(p.getUUID());
        sonido(v, TorturaRegistro.LAZO_TENSION.get(), 0.6F, 0.8F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.lazo.cargado", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        Cautivos.sincronizar(v);
    }

    /** Lo baja del hombro. enfrente: lo deja delante del que lo llevaba (si cabe). */
    private static void bajar(LivingEntity v, Player h, boolean enfrente) {
        v.stopRiding();
        avisarPasajeros(h);
        SOLTAR_LISTO.remove(h.getUUID());
        if (!enfrente) return;
        Vec3 f = Vec3.directionFromRotation(0, h.getYRot()).scale(0.9);
        Vec3 pos = h.position().add(f);
        if (!v.level().noCollision(v, v.getBoundingBox().move(pos.subtract(v.position())))) pos = h.position();
        if (v instanceof ServerPlayer sp) sp.connection.teleport(pos.x, pos.y, pos.z, sp.getYRot(), sp.getXRot());
        else v.teleportTo(pos.x, pos.y, pos.z);
        sonido(v, TorturaRegistro.LAZO_ATRAPAR.get(), 0.6F, 0.8F);
    }

    /** Clic derecho a un caballo. Devuelve true si el lazo se ha encargado. */
    public static boolean caballo(Player p, AbstractHorse caballo, ItemStack st) {
        LivingEntity carga = cargado(p);
        if (carga != null) {
            // subirlo al caballo, atravesado detrás de la silla
            carga.stopRiding();
            avisarPasajeros(p);
            carga.startRiding(caballo, true);
            sonido(carga, TorturaRegistro.LAZO_ATRAPAR.get(), 0.7F, 0.9F);
            p.displayClientMessage(Component.translatable("msg.miscelanea.lazo.al_caballo", carga.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            Cautivos.sincronizar(carga);
            return true;
        }
        List<Entity> cautivos = new ArrayList<>();
        for (Entity e : caballo.getPassengers()) if (e instanceof LivingEntity le && estado(le) == MANIATADO) cautivos.add(e);
        if (cautivos.isEmpty() || cautivos.size() != caballo.getPassengers().size() || sinManos(p)) return false;
        if (p.isShiftKeyDown()) {
            // Shift + mano vacía: bajarlo del caballo al hombro
            if (!st.isEmpty() || !p.getPassengers().isEmpty()) return false;
            LivingEntity v = (LivingEntity) cautivos.get(0);
            if (!esCaptor(v, p) && !p.getAbilities().instabuild) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.lazo.no_tuyo").withStyle(ChatFormatting.RED), true);
                return true;
            }
            cargar(p, v);
            return true;
        }
        // montar delante: el jugador tiene que ser el primer pasajero (el que lleva las riendas)
        if (!caballo.isTamed() || p.isPassenger()) return false;
        for (Entity c : cautivos) c.stopRiding();
        p.startRiding(caballo, true);
        for (Entity c : cautivos) {
            c.startRiding(caballo, true);
            if (c instanceof LivingEntity le) Cautivos.sincronizar(le);
        }
        return true;
    }

    // ------------------------------------------------------------------ inmovilizar
    private static void calmar(LivingEntity v) {
        if (v instanceof Mob m) {
            if (m.getTarget() != null) m.setTarget(null);
            if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if (m.isAggressive()) m.setAggressive(false);
        }
    }

    private static void inmovilizar(LivingEntity v) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null && a.getModifier(VELOCIDAD_ID) == null)
            a.addTransientModifier(new AttributeModifier(VELOCIDAD_ID, "miscelanea_lazo", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (v instanceof Mob m) m.getNavigation().stop();
    }

    private static void quitarInmovil(LivingEntity v) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null && a.getModifier(VELOCIDAD_ID) != null) a.removeModifier(VELOCIDAD_ID);
    }

    private static void sonido(Entity e, SoundEvent s, float vol, float tono) {
        e.level().playSound(null, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), s, SoundSource.PLAYERS, vol, tono);
    }

    // ------------------------------------------------------------------ cada tick de la víctima (servidor)
    public static void tick(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        int est = estado(v);
        calmar(v);
        if (est == ENLAZADO) tickTiron(sl, v);
        else if (est == DERRIBADO) tickDerribado(v);
        else if (est == MANIATADO) tickManiatado(v);
    }

    private static void tickTiron(ServerLevel sl, LivingEntity v) {
        Tiron t = TIRONES.get(v.getUUID());
        ServerPlayer c = t == null ? null : jugador(v, t.captor);
        if (t == null || c == null || !c.isAlive() || c.level() != v.level() || c.distanceTo(v) > ROMPE || ++t.ticks > TIRON_MAX) {
            romper(v, t != null);
            return;
        }
        if (v.isPassenger()) v.stopRiding();
        boolean tirando = LazoItem.usando(c);
        t.largo = Math.max(LARGO_MIN, t.largo - (tirando ? 0.14 : 0.025));
        double dist = v.distanceTo(c);
        Vec3 dir = c.position().subtract(v.position());
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();
        if (dist > t.largo) {
            double k = Math.min(0.6, (dist - t.largo) * (tirando ? 0.16 : 0.09)) * (1.15 - t.fuerza * 0.55);
            Vec3 nv = v.getDeltaMovement().add(dir.x * k, Math.max(0, dir.y) * k * 0.5, dir.z * k);
            double hz = Math.sqrt(nv.x * nv.x + nv.z * nv.z);
            if (hz > 0.95) nv = new Vec3(nv.x * 0.95 / hz, nv.y, nv.z * 0.95 / hz);
            if (v.horizontalCollision && v.onGround()) nv = new Vec3(nv.x, 0.42, nv.z);
            v.setDeltaMovement(nv);
            v.hurtMarked = true;
        }
        if (v instanceof Mob m) {
            m.getNavigation().stop();
            // forcejeo: tirones hacia el lado contrario, más fuertes cuanto más vida tiene
            if (v.tickCount % 8 == 0) {
                float f = t.fuerza;
                double lado = (v.getRandom().nextDouble() - 0.5) * 0.5;
                Vec3 huye = new Vec3(-dir.x + -dir.z * lado, 0, -dir.z + dir.x * lado).normalize().scale(0.05 + 0.2 * f);
                v.setDeltaMovement(v.getDeltaMovement().add(huye.x, v.onGround() && v.getRandom().nextInt(3) == 0 ? 0.25 : 0, huye.z));
                v.hurtMarked = true;
                if (f > 0.45F && dist > t.largo - 0.5) {
                    // los grandes también arrastran al captor
                    c.setDeltaMovement(c.getDeltaMovement().add(-dir.x * 0.12 * f, 0, -dir.z * 0.12 * f));
                    c.hurtMarked = true;
                }
            }
        }
        if (t.ticks % 22 == 0) {
            Vec3 m = v.position().add(c.position()).scale(0.5);
            sl.playSound(null, m.x, m.y + 1, m.z, TorturaRegistro.LAZO_TENSION.get(), SoundSource.PLAYERS, tirando ? 0.7F : 0.4F,
                    0.85F + v.getRandom().nextFloat() * 0.3F);
        }
        if (v.tickCount % 40 == 0) Cautivos.sincronizar(v);
    }

    private static void quieto(LivingEntity v) {
        if (v instanceof Mob m) {
            m.getNavigation().stop();
            Vec3 dv = v.getDeltaMovement();
            v.setDeltaMovement(dv.x * 0.3, Math.min(dv.y, 0.0), dv.z * 0.3);
            m.setJumping(false);
        }
        inmovilizar(v);   // el modificador no se guarda: se vuelve a poner tras cargar el mundo
    }

    private static void tickDerribado(LivingEntity v) {
        quieto(v);
        if (ATANDO.contains(v.getUUID())) return;
        CompoundTag d = Cautivos.leer(v);
        int t = d.getInt("LazoT") - 1;
        if (t <= 0) {
            soltar(v, false);
            if (v instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("msg.miscelanea.lazo.te_levantas").withStyle(ChatFormatting.GREEN), true);
            return;
        }
        d.putInt("LazoT", t);
        Cautivos.escribir(v, d);
    }

    private static void tickManiatado(LivingEntity v) {
        quieto(v);
        Entity veh = v.getVehicle();
        if (veh instanceof Player h) {
            // a hombros: Shift otra vez (tras haberlo soltado) lo deja en el suelo
            if (!h.isAlive() || h.isSpectator()) bajar(v, h, false);
            else if (!h.isShiftKeyDown()) SOLTAR_LISTO.add(h.getUUID());
            else if (SOLTAR_LISTO.contains(h.getUUID())) {
                bajar(v, h, true);
                Cautivos.sincronizar(v);
                return;
            }
        }
        if (!(v instanceof ServerPlayer sp)) return;   // los mobs no se sueltan solos
        CompoundTag d = Cautivos.leer(v);
        int t = d.getInt("LazoT") - 1;
        if (t <= 0) {
            soltar(v, true);
            sp.displayClientMessage(Component.translatable("msg.miscelanea.lazo.te_sueltas").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            return;
        }
        d.putInt("LazoT", t);
        Cautivos.escribir(v, d);
        if (t % ESCAPE_CADA == 0 && !Qte.activo(sp)) forcejeoManiatado(sp);
        if (t % 100 == 0) Cautivos.sincronizar(v);
    }

    /** Forcejeo del maniatado: MACHACAR; si lo supera, se suelta con probabilidad según lo rápido que fue. */
    private static void forcejeoManiatado(ServerPlayer sp) {
        final float dif = 0.55F;
        Qte.iniciar(sp, QteTipo.MACHACAR, QteParams.de(dif).titulo(Component.translatable("qte.miscelanea.titulo.lazo_nudos")), (j, r) -> {
            if (j == null || estado(j) != MANIATADO || !r.exito()) return;
            long ms = Qte.ultimaDuracionMs(j);
            long min = QteLogica.minimoMs(QteTipo.MACHACAR, dif, 1, 0), max = QteLogica.limiteMs(QteTipo.MACHACAR, dif, 1, 0);
            float nota = ms < 0 ? 0.3F : Mth.clamp((max - ms) / (float) Math.max(1, max - min), 0F, 1F);
            float prob = 0.15F + 0.65F * nota;
            if (j.getRandom().nextFloat() < prob) {
                soltar(j, true);
                j.displayClientMessage(Component.translatable("msg.miscelanea.lazo.te_sueltas").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            } else {
                sonido(j, TorturaRegistro.LAZO_TENSION.get(), 0.6F, 1.1F);
                j.displayClientMessage(Component.translatable("msg.miscelanea.lazo.nudos_aguantan", Math.round(prob * 100)).withStyle(ChatFormatting.YELLOW), true);
            }
        });
    }

    // ------------------------------------------------------------------ uso sobre entidades (servidor)
    /**
     * Clic derecho de p sobre t. Devuelve true si el lazo se encarga (soltar en creativo, maniatar o cargar).
     * Debe dar la misma respuesta que {@link #interactuaria} en el cliente.
     */
    public static boolean interactuar(Player p, LivingEntity t, ItemStack st, InteractionHand mano) {
        int est = estado(t);
        if (est == 0 || sinManos(p)) return false;
        boolean lazo = st.getItem() instanceof LazoItem;
        if (lazo && p.isShiftKeyDown()) {
            if (!p.getAbilities().instabuild) return false;
            soltar(t, true);
            p.displayClientMessage(Component.translatable("msg.miscelanea.lazo.soltado", t.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (est == DERRIBADO && !p.isShiftKeyDown() && (lazo || (st.isEmpty() && mano == InteractionHand.MAIN_HAND))) {
            if (p instanceof ServerPlayer sp) atar(sp, t);
            return true;
        }
        if (est == MANIATADO && st.isEmpty() && mano == InteractionHand.MAIN_HAND && p.isShiftKeyDown()
                && t.getVehicle() != p && p.getPassengers().isEmpty() && !p.isPassenger()) {
            if (!esCaptor(t, p) && !p.getAbilities().instabuild) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.lazo.no_tuyo").withStyle(ChatFormatting.RED), true);
                return true;
            }
            cargar(p, t);
            return true;
        }
        return false;
    }

    /** Versión del cliente de {@link #interactuar} (con el estado sincronizado), para cancelar igual en los dos lados. */
    public static boolean interactuaria(Player p, int est, LivingEntity t, ItemStack st, InteractionHand mano) {
        if (est == 0) return false;
        boolean lazo = st.getItem() instanceof LazoItem;
        if (lazo && p.isShiftKeyDown()) return p.getAbilities().instabuild;
        if (est == DERRIBADO && !p.isShiftKeyDown() && (lazo || (st.isEmpty() && mano == InteractionHand.MAIN_HAND))) return true;
        return est == MANIATADO && st.isEmpty() && mano == InteractionHand.MAIN_HAND && p.isShiftKeyDown()
                && t.getVehicle() != p && p.getPassengers().isEmpty() && !p.isPassenger();
    }

    // ------------------------------------------------------------------ limpieza
    /** El jugador se va (desconexión o cambio de dimensión): como víctima queda libre; como captor suelta a los suyos. */
    public static void cortarTodo(ServerPlayer p) {
        if (estado(p) != 0) limpiar(p);
        alPerderCaptor(p);
    }

    /** El captor muere, se va o cambia de dimensión: se rompen sus cuerdas, suelta lo que lleve a hombros y se pierde el lazo volando. */
    public static void alPerderCaptor(ServerPlayer p) {
        LANZADOS.remove(p.getUUID());
        SOLTAR_LISTO.remove(p.getUUID());
        for (Map.Entry<UUID, Tiron> en : new ArrayList<>(TIRONES.entrySet())) {
            if (!en.getValue().captor.equals(p.getUUID())) continue;
            Entity v = null;
            for (ServerLevel sl : p.server.getAllLevels()) {   // al cambiar de dimensión la víctima se queda en la otra
                v = sl.getEntity(en.getKey());
                if (v != null) break;
            }
            if (v instanceof LivingEntity le) romper(le, true);
            else TIRONES.remove(en.getKey());
        }
        for (Entity e : new ArrayList<>(p.getPassengers()))
            if (e instanceof LivingEntity le && estado(le) == MANIATADO) bajar(le, p, false);
    }

    /** Al morir una entidad (víctima o captor). */
    public static void alMorir(LivingEntity e) {
        if (estado(e) != 0) limpiar(e);
        if (e instanceof ServerPlayer sp) alPerderCaptor(sp);
    }

    public static void limpiarMemoria() {
        TIRONES.clear();
        LANZADOS.clear();
        ATANDO.clear();
        SOLTAR_LISTO.clear();
    }
}
