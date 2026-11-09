package com.miscelanea.dream;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La caza de Dream (el manhunt), lado servidor:
 * <ul>
 *   <li><b>Marca:</b> la presa que se suelta de un mordisco queda marcada (ConfigServidor.marcaTicks), y Dream también
 *       marca a quien le da un puñetazo con la mano vacía. Una presa por Dream; la nueva sustituye a la vieja.</li>
 *   <li>Mientras dure, si la presa está cerca y Dream no está al sol: Velocidad e Impulso. A Dream le llega cada medio
 *       segundo dónde está su presa (el cliente dibuja el rastro de pisadas, la flecha del HUD y el latido; nada flota en
 *       el mundo) y a la presa, que la cazan (un ojo en el HUD).</li>
 *   <li><b>Se nota que viene:</b> Dream gruñe y olfatea de vez en cuando (agachado no), suelta babas verdes y a los que le
 *       ven les llega que es Dream o un infectado ({@link AuraPacket}) para los ojos que brillan en la oscuridad.</li>
 *   <li><b>Debilidades:</b> el sol ({@link #alSol}), la leche ({@link #leche}: quita la infección, el veneno acumulado
 *       y la parálisis, y deja el Desangrado en la mitad y un nivel menos), el fuego y el Gambito (en Mordiscos).</li>
 *   <li>Los mobs infectados envenenan un poco con sus golpes.</li>
 * </ul>
 * Todo va en un tic de cada 10 (marcas) y uno de cada 20 (presencia): no pesa nada.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Persecucion {
    private Persecucion() {}

    private static final class Marca {
        LivingEntity presa;
        final UUID id;
        long fin;

        Marca(LivingEntity presa, long fin) {
            this.presa = presa;
            this.id = presa.getUUID();
            this.fin = fin;
        }
    }

    private static final Map<UUID, Marca> MARCAS = new HashMap<>();
    private static final DustParticleOptions BABA = new DustParticleOptions(new Vector3f(0.18F, 0.42F, 0.08F), 0.7F);

    // ------------------------------------------------------------------ consultas

    /** Le da el sol de lleno (de día, a cielo abierto y sin lluvia) y eso le debilita. */
    public static boolean alSol(LivingEntity e) {
        if (!ConfigServidor.debilidadSol()) return false;
        Level l = e.level();
        if (!l.dimensionType().hasSkyLight() || l.dimensionType().hasFixedTime() || !l.isDay()) return false;
        BlockPos cabeza = BlockPos.containing(e.getX(), e.getEyeY(), e.getZ());
        return !l.isRainingAt(cabeza) && l.canSeeSky(cabeza);
    }

    /** A quién persigue este Dream (null si a nadie). */
    @Nullable
    public static LivingEntity presa(Player p) {
        Marca m = MARCAS.get(p.getUUID());
        return m == null ? null : m.presa;
    }

    // ------------------------------------------------------------------ marcar

    public static void marcar(ServerPlayer p, LivingEntity v) {
        if (!ConfigServidor.persecucionActivada() || !p.hasEffect(DreamRegistro.MORDISCO.get()) || !v.isAlive() || v == p) return;
        if (v instanceof Player vp && (vp.isCreative() || vp.isSpectator())) return;
        Marca vieja = MARCAS.get(p.getUUID());
        boolean nueva = vieja == null || !vieja.id.equals(v.getUUID());
        if (vieja != null && nueva) finPresa(vieja);
        long fin = Mordiscos.tick() + ConfigServidor.marcaTicks();
        if (nueva) MARCAS.put(p.getUUID(), new Marca(v, fin));
        else {
            vieja.fin = fin;
            vieja.presa = v;
        }
        enviar(p, MARCAS.get(p.getUUID()));
        if (nueva) {
            p.displayClientMessage(Component.translatable("miscelanea.persecucion.marcada", v.getDisplayName()), true);
            p.playNotifySound(SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 0.8F, 1.3F);
            if (v instanceof ServerPlayer vp) {
                vp.displayClientMessage(Component.translatable("miscelanea.persecucion.cazado"), true);
                vp.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    public static void desmarcar(ServerPlayer p) {
        Marca m = MARCAS.remove(p.getUUID());
        if (m != null) {
            finPresa(m);
            DreamRed.a(p, PersecucionPacket.fin(PersecucionPacket.Tipo.FIN_CAZADOR));
        }
    }

    private static void finPresa(Marca m) {
        if (m.presa instanceof ServerPlayer vp) {
            // ¿la caza otro Dream? entonces sigue marcada
            for (Marca o : MARCAS.values()) if (o != m && o.id.equals(m.id)) return;
            DreamRed.a(vp, PersecucionPacket.fin(PersecucionPacket.Tipo.FIN_PRESA));
        }
    }

    private static void enviar(ServerPlayer p, Marca m) {
        LivingEntity v = m.presa;
        int resto = (int) Math.max(0, m.fin - Mordiscos.tick());
        DreamRed.a(p, new PersecucionPacket(PersecucionPacket.Tipo.CAZADOR, v.getId(), v.getX(), v.getY(), v.getZ(), resto,
                ConfigServidor.latidoDistancia(), ConfigServidor.rastroActivado()));
        if (v instanceof ServerPlayer vp)
            DreamRed.a(vp, new PersecucionPacket(PersecucionPacket.Tipo.PRESA, p.getId(), 0, 0, 0, resto, 0, false));
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = e.getServer();
        long t = Mordiscos.tick();
        if (!MARCAS.isEmpty() && t % 10 == 0) marcas(srv, t);
        if (t % 20 == 7 && ConfigServidor.dreamPresencia()) presencia(srv);
    }

    private static void marcas(MinecraftServer srv, long t) {
        boolean activada = ConfigServidor.persecucionActivada();
        for (Map.Entry<UUID, Marca> en : new ArrayList<>(MARCAS.entrySet())) {
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            Marca m = en.getValue();
            // un jugador que reaparece es otra entidad: se vuelve a buscar
            if (m.presa.isRemoved() && m.presa instanceof Player) {
                ServerPlayer nuevo = srv.getPlayerList().getPlayer(m.id);
                if (nuevo != null) m.presa = nuevo;
            }
            LivingEntity v = m.presa;
            if (p == null || !activada || t >= m.fin || !p.hasEffect(DreamRegistro.MORDISCO.get()) || !p.isAlive()
                    || v.isRemoved() || !v.isAlive() || v.level() != p.level() || (v instanceof Player vp && (vp.isCreative() || vp.isSpectator()))) {
                MARCAS.remove(en.getKey());
                finPresa(m);
                if (p != null) DreamRed.a(p, PersecucionPacket.fin(PersecucionPacket.Tipo.FIN_CAZADOR));
                continue;
            }
            enviar(p, m);
            double dist = p.distanceTo(v);
            if (dist <= ConfigServidor.persecucionDistancia() && !alSol(p) && !Mordiscos.mordiendo(p)) {
                if (ConfigServidor.persecucionVelocidad() >= 0)
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, ConfigServidor.persecucionVelocidad(), true, false, true));
                if (ConfigServidor.persecucionSalto() >= 0)
                    p.addEffect(new MobEffectInstance(MobEffects.JUMP, 25, ConfigServidor.persecucionSalto(), true, false, true));
            }
        }
    }

    private static void presencia(MinecraftServer srv) {
        boolean contagio = ConfigServidor.contagioActivado();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            boolean dream = p.hasEffect(DreamRegistro.MORDISCO.get());
            boolean infectado = !dream && contagio && p.hasEffect(DreamRegistro.INFECCION.get());
            if (!dream && !infectado) continue;
            DreamRed.alrededor(p, new AuraPacket(p.getId(), dream ? 1 : 2, 45));
            ServerLevel l = p.serverLevel();
            if (p.isShiftKeyDown() || p.isInvisible()) continue; // agachado va en silencio
            // babas verdes que le caen de la boca (las ven los demás; a uno mismo no le tapan la vista)
            Vec3 boca = p.getEyePosition().add(p.getLookAngle().multiply(1, 0, 1).scale(0.3)).subtract(0, 0.3, 0);
            for (ServerPlayer o : l.players())
                if (o != p && o.distanceToSqr(p) < 32 * 32)
                    l.sendParticles(o, BABA, false, boca.x, boca.y, boca.z, dream ? 3 : 1, 0.06, 0.08, 0.06, 0.0);
            if (dream && p.getRandom().nextInt(6) == 0) {
                // gruñido o husmeo (a veces respira fuerte)
                int s = p.getRandom().nextInt(3);
                var son = s == 0 ? SoundEvents.WOLF_GROWL : s == 1 ? SoundEvents.WARDEN_SNIFF : SoundEvents.RAVAGER_AMBIENT;
                float tono = s == 0 ? 0.5F : s == 1 ? 1.25F : 1.6F;
                l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), son, SoundSource.HOSTILE, s == 2 ? 0.35F : 0.7F, tono + p.getRandom().nextFloat() * 0.1F);
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    /** Un puñetazo con la mano vacía marca a la presa. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof LivingEntity v)) return;
        if (!p.getMainHandItem().isEmpty() || !p.hasEffect(DreamRegistro.MORDISCO.get()) || Mordiscos.esVictima(p)) return;
        marcar(p, v);
    }

    /** La leche. */
    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (!e.getEntity().level().isClientSide && e.getItem().is(Items.MILK_BUCKET)) leche(e.getEntity());
    }

    /**
     * Lo que hace la leche con lo de Dream (además de lo de vanilla, que ya quita la infección, la parálisis y el resto):
     * fuera el veneno acumulado, y el Desangrado se queda en la mitad y un nivel menos (no se cura del todo).
     */
    public static void leche(LivingEntity e) {
        Mordiscos.quitarDosis(e);
        e.removeEffect(DreamRegistro.INFECCION.get());
        MobEffectInstance d = e.getEffect(DreamRegistro.DESANGRADO.get());
        if (d != null && !d.isInfiniteDuration()) {
            e.removeEffect(DreamRegistro.DESANGRADO.get());
            int dur = d.getDuration() / 2;
            if (dur >= 20) e.addEffect(new MobEffectInstance(DreamRegistro.DESANGRADO.get(), dur, Math.max(0, d.getAmplifier() - 1),
                    d.isAmbient(), d.isVisible(), d.showIcon()));
        }
    }

    /** Los mobs infectados envenenan un poco con sus golpes. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !ConfigServidor.contagioActivado()) return;
        if (e.getSource().getEntity() instanceof Mob mob && e.getSource().getDirectEntity() == mob && mob.hasEffect(DreamRegistro.INFECCION.get())) {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), mob);
            v.addEffect(new MobEffectInstance(DreamRegistro.DESANGRADO.get(), 80, 0, false, false, true), mob);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Marca m = MARCAS.remove(e.getEntity().getUUID());
        if (m != null) finPresa(m);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        MARCAS.clear();
    }
}
