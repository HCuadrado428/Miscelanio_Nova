package com.miscelanea.tortura.saw;

import com.miscelanea.tortura.TrampaOso;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * LA CAJA DE AGUA (Saw V). Una caja de cristal sucio con marco de angular oxidado, encajada en la cabeza (como la
 * trampa de oso) y unida con una manguera a un {@link TanqueAguaBlock}.
 * <ul>
 *   <li>Se pone con el objeto {@link CajaAguaItem} sobre un jugador o mob a 3 bloques o menos (o en ti con Shift + clic
 *       derecho al aire). Hace falta un tanque a {@link #RADIO_TANQUE} bloques o menos: se engancha al más cercano. La víctima
 *       queda sujeta a la manguera (no se aleja más de {@link #CORREA} bloques de donde se la pusieron).</li>
 *   <li>El captor tira de la palanca del tanque: empieza el juego (saludo de Jigsaw) y el agua sube durante
 *       {@link #LLENADO} ticks (~40 s). Cuando cubre la cara ({@link #CARA}) se acaba el aire de verdad y, sin aire, ahoga
 *       (2 de daño por segundo, daño "caja_agua", con su mensaje).</li>
 *   <li>La única forma de vaciarla: meter las manos por los agujeros de los lados (tecla "Meter las manos" o clic derecho al
 *       aire con la mano vacía). Cada vez: {@link #DANO_EMPUJE} de daño (cortes en los dos brazos, sangre en el agua) y el
 *       agua baja {@link #BAJADA}. Con {@link #EMPUJES} (unos 6 corazones en total) se vacía y se abre.</li>
 *   <li>Creativo: Shift + clic derecho a la víctima con la mano vacía la quita. Morir o cambiar de dimensión la quita;
 *       desconectarse la congela (se guarda en la entidad).</li>
 * </ul>
 */
public final class CajaAgua {
    private CajaAgua() {}

    public static final int RADIO_TANQUE = 10, LLENADO = 800, EMPUJES = 6, ESPERA_EMPUJE = 24, ABRIR_TICKS = 40;
    public static final float CARA = 0.56F, BAJADA = 0.17F, DANO_EMPUJE = 2.0F, DANO_AHOGO = 2.0F;
    public static final double CORREA = 1.6, ALCANCE = 3.0;
    public static final long SIN_TANQUE = Long.MIN_VALUE;

    // ------------------------------------------------------------------ consultas
    public static boolean tiene(LivingEntity v) {
        return Saw.tieneAlgo(v) && Saw.leer(v).contains("Caja");
    }

    private static CompoundTag caja(LivingEntity v) {
        return Saw.leer(v).getCompound("Caja");
    }

    private static void guardar(LivingEntity v, CompoundTag c) {
        CompoundTag d = Saw.leer(v);
        if (c == null) d.remove("Caja");
        else d.put("Caja", c);
        Saw.escribir(v, d);
    }

    // ------------------------------------------------------------------ poner / quitar
    /** Pone la caja (servidor). Devuelve si se ha puesto. */
    public static boolean poner(LivingEntity v, @Nullable Player captor) {
        if (!(v.level() instanceof ServerLevel sl) || !v.isAlive() || tiene(v)) return false;
        if (v instanceof Player p && p.isSpectator()) return false;
        if (!(v instanceof Player) && !(v instanceof Mob)) return false;
        if (TrampaOso.ejecutando(v) || v.getBbHeight() > 2.6F || v.getBbWidth() > 1.6F) {
            if (captor != null) captor.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.no_cabe").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        BlockPos tanque = tanqueCerca(sl, v.blockPosition());
        if (tanque == null) {
            if (captor != null)
                captor.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.sin_tanque", RADIO_TANQUE).withStyle(ChatFormatting.RED), true);
            return false;
        }
        v.stopRiding();
        CompoundTag c = new CompoundTag();
        c.putLong("Tanque", tanque.asLong());
        c.putFloat("Nivel", 0F);
        c.putInt("Aire", v.getMaxAirSupply());
        c.putDouble("AX", v.getX());
        c.putDouble("AY", v.getY());
        c.putDouble("AZ", v.getZ());
        if (captor != null) c.putUUID("Captor", captor.getUUID());
        guardar(v, c);
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), SawRegistro.CAJA_ABRIR.get(), SoundSource.PLAYERS, 1F, 0.8F);
        if (v instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.puesta").withStyle(ChatFormatting.DARK_RED), true);
        if (captor != null && captor != v)
            captor.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.captor", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        sincronizar(v);
        return true;
    }

    /** Quita la caja (exito = la ha vaciado y se ha abierto). */
    public static void quitar(LivingEntity v, boolean exito) {
        if (!tiene(v)) return;
        guardar(v, null);
        if (v.isAlive()) v.setAirSupply(v.getMaxAirSupply());
        if (!v.level().isClientSide) {
            v.level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), SawRegistro.CAJA_ABRIR.get(), SoundSource.PLAYERS, 1F, 1.1F);
            if (exito && v instanceof ServerPlayer sp)
                Saw.titulo(sp, Component.translatable("msg.miscelanea.saw.caja.libre").withStyle(ChatFormatting.GREEN),
                        Component.translatable("msg.miscelanea.saw.caja.libre_sub").withStyle(ChatFormatting.GRAY), 40);
        }
        sincronizar(v);
    }

    /** Muerte, cambio de dimensión...: se quita sin ruido. */
    public static void limpiar(LivingEntity v) {
        if (!tiene(v)) return;
        guardar(v, null);
        sincronizar(v);
    }

    // ------------------------------------------------------------------ el tanque
    @Nullable
    public static BlockPos tanqueCerca(ServerLevel sl, BlockPos centro) {
        BlockPos mejor = null;
        double dmin = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -RADIO_TANQUE; dx <= RADIO_TANQUE; dx++)
            for (int dy = -4; dy <= 4; dy++)
                for (int dz = -RADIO_TANQUE; dz <= RADIO_TANQUE; dz++) {
                    m.set(centro.getX() + dx, centro.getY() + dy, centro.getZ() + dz);
                    if (!sl.isLoaded(m) || !sl.getBlockState(m).is(SawRegistro.TANQUE.get())) continue;
                    double d = m.distSqr(centro);
                    if (d < dmin) {
                        dmin = d;
                        mejor = m.immutable();
                    }
                }
        return mejor;
    }

    /** Palanca del tanque: empieza el juego de todas las cajas enganchadas a él. Devuelve cuántas. */
    public static int empezar(ServerLevel sl, BlockPos tanque, @Nullable Player quien) {
        int n = 0;
        AABB zona = new AABB(tanque).inflate(RADIO_TANQUE + 6);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, zona, CajaAgua::tiene)) {
            CompoundTag c = caja(v);
            if (c.getLong("Tanque") != tanque.asLong() || c.getBoolean("Activa") || c.getInt("Abierta") > 0) continue;
            c.putBoolean("Activa", true);
            if (quien != null && !c.hasUUID("Captor")) c.putUUID("Captor", quien.getUUID());
            guardar(v, c);
            if (v instanceof ServerPlayer sp) Saw.saludo(sp, "caja");
            sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), SawRegistro.CAJA_LLENAR.get(), SoundSource.PLAYERS, 1F, 1F);
            sincronizar(v);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ cada tick (servidor)
    public static void tick(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        CompoundTag c = caja(v);
        int abierta = c.getInt("Abierta");
        if (abierta > 0) {
            c.putInt("Abierta", abierta + 1);
            guardar(v, c);
            if (abierta + 1 >= ABRIR_TICKS) quitar(v, true);
            return;
        }
        sujetar(v, c);
        boolean activa = c.getBoolean("Activa");
        float nivel = c.getFloat("Nivel");
        float antes = nivel;
        if (activa && nivel < 1F) {
            nivel = Math.min(1F, nivel + 1F / LLENADO);
            if (v.tickCount % 44 == 0)
                sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), SawRegistro.CAJA_LLENAR.get(), SoundSource.PLAYERS, 0.7F, 0.9F + nivel * 0.25F);
        }
        c.putFloat("Nivel", nivel);
        // el aire: de verdad, en cuanto el agua tapa la cara
        int aire = c.contains("Aire") ? c.getInt("Aire") : v.getMaxAirSupply();
        if (nivel >= CARA && !v.canBreatheUnderwater() && !(v instanceof Player p && p.getAbilities().invulnerable)) {
            aire--;
            if (aire <= -20) {
                aire = 0;
                c.putInt("Aire", aire);
                guardar(v, c);
                UUID captor = c.hasUUID("Captor") ? c.getUUID("Captor") : null;
                Aparatos.danar(v, SawRegistro.DANO_CAJA, DANO_AHOGO, captor);
                if (!v.isAlive()) return;
                c = caja(v);
            }
            if (v.tickCount % 30 == 0)
                sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), SawRegistro.CAJA_BURBUJAS.get(), SoundSource.PLAYERS, 0.8F, 0.9F + v.getRandom().nextFloat() * 0.2F);
            if (v.tickCount % 4 == 0)
                sl.sendParticles(ParticleTypes.BUBBLE, v.getX(), v.getEyeY() + 0.15, v.getZ(), 2, 0.12, 0.08, 0.12, 0.02);
        } else {
            aire = Math.min(v.getMaxAirSupply(), aire + 4);
        }
        c.putInt("Aire", aire);
        v.setAirSupply(aire);
        guardar(v, c);
        if (v.tickCount % 10 == 0 || (antes < CARA) != (nivel < CARA)) sincronizar(v);
    }

    /** Atada a la manguera: no se aleja del ancla, no monta en nada. */
    private static void sujetar(LivingEntity v, CompoundTag c) {
        if (v.isPassenger()) v.stopRiding();
        double ax = c.getDouble("AX"), ay = c.getDouble("AY"), az = c.getDouble("AZ");
        double dx = v.getX() - ax, dz = v.getZ() - az;
        double d2 = dx * dx + dz * dz;
        if (d2 > CORREA * CORREA || Math.abs(v.getY() - ay) > 3) {
            double k = d2 > 1.0E-6 ? CORREA * 0.9 / Math.sqrt(d2) : 0;
            double nx = ax + dx * k, nz = az + dz * k, ny = Math.abs(v.getY() - ay) > 3 ? ay : v.getY();
            if (v instanceof ServerPlayer sp) sp.connection.teleport(nx, ny, nz, sp.getYRot(), sp.getXRot());
            else v.teleportTo(nx, ny, nz);
            v.setDeltaMovement(Vec3.ZERO);
        }
        if (v instanceof Mob m && v.tickCount % 20 == 0) m.getNavigation().stop();
    }

    // ------------------------------------------------------------------ meter las manos
    public static void meterManos(ServerPlayer p) {
        if (!tiene(p) || !p.isAlive()) return;
        CompoundTag c = caja(p);
        if (c.getInt("Abierta") > 0) return;
        if (!c.getBoolean("Activa")) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.aun_no").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long ahora = p.level().getGameTime();
        if (ahora < c.getLong("Espera")) return;
        c.putLong("Espera", ahora + ESPERA_EMPUJE);
        int empujes = c.getInt("Empujes") + 1;
        c.putInt("Empujes", empujes);
        c.putFloat("Nivel", Math.max(0F, c.getFloat("Nivel") - BAJADA));
        c.putFloat("Sangre", Math.min(1F, c.getFloat("Sangre") + 0.17F));
        boolean listo = empujes >= EMPUJES;
        if (listo) {
            c.putBoolean("Activa", false);
            c.putFloat("Nivel", 0F);
            c.putInt("Abierta", 1);
        }
        guardar(p, c);
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, p.getX(), p.getEyeY(), p.getZ(), SawRegistro.CAJA_CUCHILLAS.get(), SoundSource.PLAYERS, 1.1F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        sl.playSound(null, p.getX(), p.getEyeY(), p.getZ(), SawRegistro.CAJA_VACIAR.get(), SoundSource.PLAYERS, listo ? 1.2F : 0.7F, 1F);
        p.swing(InteractionHand.MAIN_HAND, true);
        p.swing(InteractionHand.OFF_HAND, true);
        // sangre por los agujeros de los lados
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double lx = Math.cos(yaw) * 0.42, lz = Math.sin(yaw) * 0.42;
        DustParticleOptions rojo = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.1F);
        for (int s = -1; s <= 1; s += 2)
            sl.sendParticles(rojo, p.getX() + lx * s, p.getEyeY() - 0.1, p.getZ() + lz * s, 8, 0.06, 0.08, 0.06, 0.0);
        Aparatos.herida(p, Parte.BRAZO_DER, Tipo.CORTE, empujes >= 4 ? 3 : 2);
        Aparatos.herida(p, Parte.BRAZO_IZQ, Tipo.CORTE, empujes >= 4 ? 3 : 2);
        UUID captor = c.hasUUID("Captor") ? c.getUUID("Captor") : null;
        Aparatos.danar(p, SawRegistro.DANO_CUCHILLAS, DANO_EMPUJE, captor);
        if (!p.isAlive()) return;
        if (listo) {
            Saw.titulo(p, Component.translatable("msg.miscelanea.saw.caja.vacia").withStyle(ChatFormatting.GOLD),
                    Component.translatable("msg.miscelanea.saw.caja.vacia_sub").withStyle(ChatFormatting.GRAY), 30);
        } else {
            p.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.empuje", empujes, EMPUJES).withStyle(ChatFormatting.RED), true);
        }
        sincronizar(p);
    }

    // ------------------------------------------------------------------ sincronizar
    public static SawEstadoPacket paquete(LivingEntity v) {
        if (!tiene(v)) return new SawEstadoPacket(v.getId(), false, false, 0F, 0F, SIN_TANQUE, 0, 0);
        CompoundTag c = caja(v);
        return new SawEstadoPacket(v.getId(), true, c.getBoolean("Activa"), c.getFloat("Nivel"), c.getFloat("Sangre"),
                c.contains("Tanque") ? c.getLong("Tanque") : SIN_TANQUE, c.getInt("Empujes"), c.getInt("Abierta"));
    }

    public static void sincronizar(LivingEntity v) {
        if (v.level().isClientSide) return;
        SawRed.alrededor(v, paquete(v));
    }

    public static void sincronizarCon(LivingEntity v, ServerPlayer quien) {
        SawRed.a(quien, paquete(v));
    }
}
