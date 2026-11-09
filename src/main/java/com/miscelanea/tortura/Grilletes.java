package com.miscelanea.tortura;

import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Grilletes (prisionero de Skyrim).
 * <ul>
 *   <li>Se ponen desde detrás y muy cerca (clic derecho con los grilletes). Con Shift, además, le enganchas la cadena.</li>
 *   <li>Atado: no puede atacar, usar objetos, poner ni romper bloques (todo cancelado en el servidor). Los mobs pierden
 *       su objetivo y dejan de atacar.</li>
 *   <li>Cadena: quien la sujeta arrastra al cautivo como con una correa (se rompe a más de 24 bloques o al cambiar de
 *       dimensión). Shift + clic derecho con los grilletes, o clic derecho con la cadena, la engancha / suelta.</li>
 *   <li>Escapar: usar una ganzúa = minijuego GANZUA (la cerradura tiene su punto dulce fijo). Las ganzúas se rompen.</li>
 *   <li>Quitar: quien los puso (o alguien en creativo) con clic derecho con los grilletes, o Shift + clic derecho con la mano vacía.</li>
 * </ul>
 */
public final class Grilletes {
    private Grilletes() {}

    public static final double DIST_PONER = 2.6, DIST_CADENA = 4.5, CADENA_LARGO = 3.0, CADENA_ROMPE = 24;
    private static final String[] CLAVES = {"Grilletes", "GrilletesCaptor", "Cadena", "Cerradura"};

    /** ¿Está 'captor' detrás de 'v' (respecto a hacia dónde mira)? */
    public static boolean detras(LivingEntity captor, LivingEntity v) {
        Vec3 frente = Vec3.directionFromRotation(0, v.getYHeadRot());
        Vec3 a = captor.position().subtract(v.position());
        a = new Vec3(a.x, 0, a.z);
        if (a.lengthSqr() < 1.0E-4) return true;
        return frente.dot(a.normalize()) < -0.25;
    }

    public static void poner(LivingEntity v, Player captor, boolean conCadena) {
        CompoundTag d = Cautivos.leer(v);
        d.putBoolean("Grilletes", true);
        d.putUUID("GrilletesCaptor", captor.getUUID());
        d.putLong("Cerradura", v.getRandom().nextLong() | 1L);
        if (conCadena) d.putUUID("Cadena", captor.getUUID());
        Cautivos.escribir(v, d);
        if (v instanceof Mob m) {
            m.setTarget(null);
            m.setAggressive(false);
            m.getNavigation().stop();
        }
        if (v instanceof ServerPlayer sp) Qte.cancelar(sp);
        v.level().playSound(null, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), TorturaRegistro.GRILLETES_PONER.get(), SoundSource.PLAYERS, 1F, 1F);
        if (v instanceof Player pv)
            pv.sendSystemMessage(Component.translatable("msg.miscelanea.grilletes.puestos").withStyle(ChatFormatting.RED));
        com.miscelanea.logros.LogrosMiscelanea.dar(captor, "grilletes");
        com.miscelanea.logros.LogrosMiscelanea.dar(v, "engrilletado");
        captor.displayClientMessage(Component.translatable(conCadena ? "msg.miscelanea.grilletes.puestos_cadena" : "msg.miscelanea.grilletes.puestos_captor",
                v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        Cautivos.sincronizar(v);
    }

    /**
     * Quita los grilletes. soltarObjeto: caen al suelo (al escapar). Si no, vuelven a 'quien' (si no está en creativo).
     */
    public static void quitar(LivingEntity v, @Nullable Player quien, boolean soltarObjeto) {
        CompoundTag d = Cautivos.leer(v);
        for (String k : CLAVES) d.remove(k);
        Cautivos.escribir(v, d);
        ItemStack g = new ItemStack(TorturaRegistro.GRILLETES.get());
        if (soltarObjeto) v.spawnAtLocation(g);
        else if (quien != null && !quien.getAbilities().instabuild) ItemHandlerHelper.giveItemToPlayer(quien, g);
        v.level().playSound(null, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), TorturaRegistro.GRILLETES_QUITAR.get(), SoundSource.PLAYERS, 1F, 1F);
        Cautivos.sincronizar(v);
    }

    public static boolean esCaptor(LivingEntity v, Player p) {
        CompoundTag d = Cautivos.leer(v);
        return d.hasUUID("GrilletesCaptor") && d.getUUID("GrilletesCaptor").equals(p.getUUID());
    }

    /** Engancha (holder != null) o suelta la cadena. */
    public static void cadena(LivingEntity v, @Nullable Entity holder) {
        CompoundTag d = Cautivos.leer(v);
        if (holder == null) d.remove("Cadena");
        else d.putUUID("Cadena", holder.getUUID());
        Cautivos.escribir(v, d);
        v.level().playSound(null, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), TorturaRegistro.CADENA_TINTINEO.get(), SoundSource.PLAYERS, 1F,
                holder == null ? 0.8F : 1.1F);
        Cautivos.sincronizar(v);
    }

    // ------------------------------------------------------------------ uso de los objetos (servidor)
    public static void usarGrilletes(Player p, ItemStack st, LivingEntity t) {
        if (t == p) return;
        if (!Cautivos.atado(t)) {
            if (p.distanceTo(t) > DIST_PONER) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            if (!detras(p, t)) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.detras").withStyle(ChatFormatting.RED), true);
                return;
            }
            poner(t, p, p.isShiftKeyDown());
            if (!p.getAbilities().instabuild) st.shrink(1);
            return;
        }
        if (p.isShiftKeyDown()) alternarCadena(p, t);
        else if (esCaptor(t, p) || p.getAbilities().instabuild) quitar(t, p, false);
        else p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.no_llave").withStyle(ChatFormatting.RED), true);
    }

    public static void alternarCadena(Player p, LivingEntity t) {
        if (!Cautivos.atado(t)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.cadena.sin_grilletes").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        UUID c = Cautivos.cadena(t);
        if (c != null && c.equals(p.getUUID())) {
            cadena(t, null);
            p.displayClientMessage(Component.translatable("msg.miscelanea.cadena.suelta").withStyle(ChatFormatting.GRAY), true);
        } else if (c != null && !esCaptor(t, p) && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.cadena.otro").withStyle(ChatFormatting.RED), true);
        } else if (p.distanceTo(t) > DIST_CADENA) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
        } else {
            cadena(t, p);
            p.displayClientMessage(Component.translatable("msg.miscelanea.cadena.enganchada", t.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Shift + clic derecho con la mano vacía: quien los puso (o creativo) se los quita. */
    public static boolean manoVacia(Player p, LivingEntity t) {
        if (!Cautivos.atado(t) || !(esCaptor(t, p) || p.getAbilities().instabuild)) return false;
        quitar(t, p, false);
        return true;
    }

    // ------------------------------------------------------------------ ganzúa
    public static void forzar(ServerPlayer p) {
        if (!Cautivos.atado(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.ganzua.nada").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Qte.activo(p)) return;
        long semilla = Cautivos.leer(p).getLong("Cerradura");
        Qte.iniciar(p, QteTipo.GANZUA, QteParams.de(0.55F).semilla(semilla == 0 ? 1 : semilla)
                .titulo(Component.translatable("qte.miscelanea.titulo.grilletes")), Grilletes::resultadoGanzua);
    }

    private static void resultadoGanzua(@Nullable ServerPlayer j, QteResultado r) {
        if (j == null || !Cautivos.atado(j)) return;
        if (r.exito()) {
            quitar(j, null, true);
            j.level().playSound(null, j.getX(), j.getY() + 1, j.getZ(), TorturaRegistro.CERRADURA_ABRIR.get(), SoundSource.PLAYERS, 1F, 1F);
            j.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.escapas").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        } else if (r == QteResultado.ROTO) {
            // el que juega ya oye su propia ganzúa romperse; los de alrededor también
            j.level().playSound(j, j.getX(), j.getY() + 1, j.getZ(), TorturaRegistro.GANZUA_ROTA.get(), SoundSource.PLAYERS, 1F, 1F);
            ItemStack g = ganzuaEnMano(j);
            if (!g.isEmpty() && !j.getAbilities().instabuild) g.shrink(1);
            if (!ganzuaEnMano(j).isEmpty()) {
                j.displayClientMessage(Component.translatable("msg.miscelanea.ganzua.rota_otra").withStyle(ChatFormatting.YELLOW), true);
                Qte.despues(24, () -> {
                    if (j.isAlive() && !j.hasDisconnected() && Cautivos.atado(j) && !ganzuaEnMano(j).isEmpty()) forzar(j);
                });
            } else {
                j.displayClientMessage(Component.translatable("msg.miscelanea.ganzua.sin").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    public static ItemStack ganzuaEnMano(Player p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.getItem() instanceof GanzuaItem) return s;
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ cada tick (servidor)
    public static void tick(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        if (v instanceof Mob m) {
            if (m.getTarget() != null) m.setTarget(null);
            if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if (m.isAggressive()) m.setAggressive(false);
        }
        UUID c = Cautivos.cadena(v);
        if (c == null) return;
        Entity h = sl.getEntity(c);
        if (h == null) {
            ServerPlayer otro = sl.getServer().getPlayerList().getPlayer(c);
            if (otro != null && otro.level() != v.level()) romper(v);
            return;   // desconectado o en un chunk sin cargar: la cadena se queda enganchada
        }
        if (!h.isAlive() || h.level() != v.level()) {
            romper(v);
            return;
        }
        double dist = v.distanceTo(h);
        if (dist > CADENA_ROMPE) {
            romper(v);
            return;
        }
        if (v.tickCount % 40 == 0) Cautivos.sincronizar(v);   // por si quien tira acaba de cargarse en el cliente
        if (dist > CADENA_LARGO) {
            Vec3 dir = h.position().subtract(v.position()).normalize();
            double k = Math.min(0.5, (dist - CADENA_LARGO) * 0.07);
            Vec3 nv = v.getDeltaMovement().add(dir.x * k, Math.max(0, dir.y) * k * 0.6, dir.z * k);
            double hz = Math.sqrt(nv.x * nv.x + nv.z * nv.z);
            if (hz > 0.9) nv = new Vec3(nv.x * 0.9 / hz, nv.y, nv.z * 0.9 / hz);
            if (v.horizontalCollision && v.onGround()) nv = new Vec3(nv.x, 0.42, nv.z);
            v.setDeltaMovement(nv);
            v.hurtMarked = true;
            if (v instanceof PathfinderMob pm && dist > CADENA_LARGO + 1 && v.tickCount % 10 == 0) pm.getNavigation().moveTo(h, 1.15);
            if (v.tickCount % 30 == 0)
                sl.playSound(null, v.getX(), v.getY() + 1, v.getZ(), TorturaRegistro.CADENA_TINTINEO.get(), SoundSource.PLAYERS, 0.35F, 0.9F + v.getRandom().nextFloat() * 0.3F);
        }
    }

    private static void romper(LivingEntity v) {
        cadena(v, null);
    }
}
