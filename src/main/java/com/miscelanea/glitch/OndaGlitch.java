package com.miscelanea.glitch;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La onda de corrupción de una muestra de glitch que se ha roto: una esfera que crece desde el centro en
 * {@link #DURACION} ticks. Cada bloque que alcanza (con algo de temblor en el frente) se vuelve un
 * {@link GlitchTemporalBlock}, que al rato desaparece (o vuelve a ser lo que era si {@code destruir_bloques = false}).
 * Los objetos tirados que pilla parpadean y se borran; a los seres vivos les deja el efecto «Glitch», náuseas, oscuridad
 * y un poco de daño, pero no los borra.
 * <p>
 * Seguridad (servidor multijugador): no toca bloques irrompibles ni inmunes al Wither (lecho de roca, barreras, marcos de
 * portal...), ni bloques con datos (cofres, hornos, carteles...) salvo que la configuración lo permita (y nunca en modo
 * visual, que no podría devolver su contenido), respeta la protección del spawn y las protecciones de otros mods (se
 * lanza un {@link BlockEvent.BreakEvent} por bloque en nombre de quien soltó la muestra) y reparte el trabajo entre ticks
 * con un máximo de bloques por tick para todo el mundo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OndaGlitch {
    /** Ticks que tarda el frente de la onda en llegar al borde de la esfera. */
    public static final int DURACION = 50;
    /** Datos persistentes de un objeto tirado: quién lo soltó (para las protecciones y el mensaje de muerte). */
    public static final String DUENO = "miscelanea_glitch_dueno";
    /** Datos persistentes de una muestra tirada que se ha tragado otra onda: ya no explota (sin reacciones en cadena). */
    public static final String SIN_MECHA = "miscelanea_glitch_sin_mecha";

    private static final List<OndaGlitch> ACTIVAS = new ArrayList<>();

    private record Punto(BlockPos pos, int t) {}

    private final ServerLevel level;
    private final Vec3 centro;
    private final int radio;
    @Nullable private final UUID dueno;
    private final boolean destruir, contenedores;
    private final float dano;
    private final List<Punto> puntos = new ArrayList<>();
    private int idx, t;
    private final Set<UUID> tocados = new HashSet<>();
    /** Objetos tirados que se ha tragado la onda y el tick en el que desaparecen (o dejan de parpadear). */
    private final Map<ItemEntity, Integer> objetos = new LinkedHashMap<>();

    private OndaGlitch(ServerLevel level, Vec3 centro, @Nullable UUID dueno) {
        this.level = level;
        this.centro = centro;
        this.dueno = dueno;
        this.radio = ConfigServidor.glitchRadio();
        this.destruir = ConfigServidor.glitchDestruirBloques();
        this.contenedores = ConfigServidor.glitchAfectarContenedores();
        this.dano = ConfigServidor.glitchDano();
        BlockPos c = BlockPos.containing(centro);
        var r = level.random;
        for (int dx = -radio; dx <= radio; dx++)
            for (int dy = -radio; dy <= radio; dy++)
                for (int dz = -radio; dz <= radio; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    double d = Math.sqrt(p.getCenter().distanceToSqr(centro));
                    if (d > radio + 0.3) continue;
                    // frente irregular: cada bloque llega un poco antes o un poco después que sus vecinos
                    int tp = (int) (d / Math.max(1, radio) * DURACION * (0.85 + r.nextFloat() * 0.3)) + r.nextInt(5);
                    puntos.add(new Punto(p, tp));
                }
        puntos.sort((a, b) -> Integer.compare(a.t, b.t));
    }

    /** Rompe una muestra en {@code centro}: estallido y empieza la onda. */
    public static void detonar(ServerLevel level, Vec3 centro, @Nullable UUID dueno) {
        level.playSound(null, centro.x, centro.y, centro.z, GlitchRegistro.ESTALLIDO.get(), SoundSource.BLOCKS, 2.5F, 0.9F + level.random.nextFloat() * 0.2F);
        level.sendParticles(GlitchRegistro.PARTICULA.get(), centro.x, centro.y, centro.z, 90, 0.8, 0.8, 0.8, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, centro.x, centro.y, centro.z, 40, 0.4, 0.4, 0.4, 0.4);
        ACTIVAS.add(new OndaGlitch(level, centro, dueno));
    }

    /** Si {@code pos} está dentro de una onda que sigue viva (para el frasco de muestras). */
    public static boolean dentro(Level level, Vec3 pos) {
        for (OndaGlitch o : ACTIVAS)
            if (o.level == level && o.centro.distanceToSqr(pos) <= (o.radio + 1.0) * (o.radio + 1.0)) return true;
        return false;
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl) || ACTIVAS.isEmpty()) return;
        int presupuesto = ConfigServidor.glitchBloquesPorTick();
        for (Iterator<OndaGlitch> it = ACTIVAS.iterator(); it.hasNext(); ) {
            OndaGlitch o = it.next();
            if (o.level != sl) continue;
            presupuesto = o.tick(presupuesto);
            if (o.acabada()) it.remove();
        }
    }

    private boolean acabada() {
        return idx >= puntos.size() && objetos.isEmpty() && t > DURACION + 10;
    }

    /** Un tick de la onda; devuelve el presupuesto de bloques que queda para las demás. */
    private int tick(int presupuesto) {
        t++;
        @Nullable Player jugador = jugador();
        BlockPos sonar = null;
        while (idx < puntos.size() && puntos.get(idx).t <= t && presupuesto > 0) {
            Punto p = puntos.get(idx++);
            presupuesto--;
            if (corromper(p.pos, jugador) && (sonar == null || level.random.nextInt(8) == 0)) sonar = p.pos;
        }
        if (sonar != null && t % 3 == 0)
            level.playSound(null, sonar, GlitchRegistro.CRUJIDO.get(), SoundSource.BLOCKS, 0.9F, 0.6F + level.random.nextFloat() * 0.9F);
        if (t % 2 == 0 && t <= DURACION + 6) entidades(jugador);
        objetos();
        return presupuesto;
    }

    @Nullable
    private Player jugador() {
        if (dueno == null) return null;
        MinecraftServer s = level.getServer();
        ServerPlayer p = s.getPlayerList().getPlayer(dueno);
        return p != null && p.level() == level ? p : null;
    }

    @Nullable
    private Entity causante() {
        return dueno == null ? null : level.getEntity(dueno);
    }

    private boolean corromper(BlockPos pos, @Nullable Player jugador) {
        if (!level.isLoaded(pos) || !level.isInWorldBounds(pos)) return false;
        BlockState st = level.getBlockState(pos);
        if (!sePuede(st, pos, jugador)) return false;
        // modo visual: dura un poco más, para que se vea bien antes de volver
        int espera = (destruir ? 6 : 20) + level.random.nextInt(destruir ? 30 : 40);
        GlitchTemporalBlock.corromper(level, pos, st, destruir, espera);
        if (level.random.nextInt(3) == 0)
            level.sendParticles(GlitchRegistro.PARTICULA.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.0);
        return true;
    }

    private boolean sePuede(BlockState st, BlockPos pos, @Nullable Player jugador) {
        if (st.isAir() || st.is(GlitchRegistro.GLITCH_TEMPORAL.get())) return false;
        if (st.getDestroySpeed(level, pos) < 0 || st.is(BlockTags.WITHER_IMMUNE)) return false;
        // bloques con datos (cofres, hornos, carteles, tolvas...): nunca en modo visual (al quitarlos sueltan lo que
        // llevan y no se podría devolver tal cual); en modo destrucción, solo si la configuración lo permite
        if (st.hasBlockEntity() && (!destruir || !contenedores)) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (jugador != null) {
            if (!level.mayInteract(jugador, pos)) return false;
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, st, jugador));
        }
        return !spawnProtegido(pos);
    }

    /** La protección del spawn del servidor dedicado, cuando no hay nadie a quien preguntar. */
    private boolean spawnProtegido(BlockPos pos) {
        MinecraftServer s = level.getServer();
        if (!s.isDedicatedServer() || level.dimension() != Level.OVERWORLD) return false;
        int r = s.getSpawnProtectionRadius();
        if (r <= 0 || s.getPlayerList().getOps().isEmpty()) return false;
        BlockPos sp = level.getSharedSpawnPos();
        return Math.max(Math.abs(pos.getX() - sp.getX()), Math.abs(pos.getZ() - sp.getZ())) <= r;
    }

    private void entidades(@Nullable Player jugador) {
        double frente = Math.min(radio, radio * (double) t / DURACION) + 0.6;
        AABB caja = new AABB(centro, centro).inflate(radio + 1.5);
        Entity causante = causante();
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, caja,
                x -> x.isAlive() && !x.isSpectator() && !(x instanceof ArmorStand))) {
            if (le.distanceToSqr(centro) > frente * frente || !tocados.add(le.getUUID())) continue;
            le.addEffect(new MobEffectInstance(GlitchRegistro.EFECTO.get(), 120, 0));
            le.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
            le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0));
            if (dano > 0) {
                le.invulnerableTime = 0;
                le.hurt(GlitchRegistro.dano(level, causante), dano);
            }
            level.sendParticles(GlitchRegistro.PARTICULA.get(), le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 14,
                    le.getBbWidth() * 0.5, le.getBbHeight() * 0.4, le.getBbWidth() * 0.5, 0.0);
            level.playSound(null, le.getX(), le.getY(), le.getZ(), GlitchRegistro.ESTATICA.get(), SoundSource.HOSTILE, 0.8F, 0.8F + level.random.nextFloat() * 0.5F);
        }
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, caja, Entity::isAlive)) {
            if (objetos.containsKey(ie) || ie.distanceToSqr(centro) > frente * frente) continue;
            objetos.put(ie, t + 6 + level.random.nextInt(16));
            ie.getPersistentData().putBoolean(SIN_MECHA, true);
            if (destruir) ie.setPickUpDelay(40);
        }
    }

    /** Los objetos tirados parpadean (contorno brillante a saltos) y, si se destruye, se borran. */
    private void objetos() {
        for (Iterator<Map.Entry<ItemEntity, Integer>> it = objetos.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ItemEntity ie = en.getKey();
            if (!ie.isAlive()) {
                it.remove();
                continue;
            }
            if (t >= en.getValue()) {
                ie.setGlowingTag(false);
                if (destruir) {
                    level.sendParticles(GlitchRegistro.PARTICULA.get(), ie.getX(), ie.getY() + 0.2, ie.getZ(), 8, 0.15, 0.15, 0.15, 0.0);
                    ie.discard();
                } else {
                    ie.getPersistentData().remove(SIN_MECHA);
                }
                it.remove();
                continue;
            }
            ie.setGlowingTag(level.random.nextInt(3) != 0);
            if (level.random.nextInt(3) == 0)
                level.sendParticles(GlitchRegistro.PARTICULA.get(), ie.getX(), ie.getY() + 0.2, ie.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
        }
    }

    // ------------------------------------------------------------------ eventos
    /** Quien suelta una muestra queda apuntado en el objeto tirado. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent e) {
        if (e.getEntity().getItem().is(GlitchRegistro.MUESTRA.get()))
            e.getEntity().getPersistentData().putUUID(DUENO, e.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload e) {
        ACTIVAS.removeIf(o -> o.level == e.getLevel());
    }

    /** Lo que ya está glitcheado se resuelve solo (va en el propio bloque); lo que falte de la onda se pierde. */
    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        ACTIVAS.clear();
    }
}
