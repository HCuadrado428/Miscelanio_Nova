package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clic derecho normal: aspirar. Incluye:
 * - atraer ítems, experiencia, mobs y jugadores en un cono,
 * - el tira y afloja para capturar mobs pequeños,
 * - recoger a la víctima del desatascador y lanzarla/estamparla al soltar,
 * - usar el desatascador clavado en un bloque como gancho.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Succion {
    public static final double ALCANCE = 8.0;
    public static final double COS_CONO = 0.82; // ~35°
    private static final double DIST_AGARRE = 2.5;
    /** Distancia a la boca a la que la aspiradora empieza a hacer daño. */
    private static final double DIST_BOCA = 1.8;
    private static final float DANO_BOCA = 3.0F;

    /** Estado del tira y afloja de cada jugador. */
    private static final class Captura {
        final LivingEntity mob;
        final float max;
        float resistencia;
        int dir;          // +1 tira a la derecha, -1 a la izquierda
        int cambioEn;     // ticks hasta que cambie de lado
        float ultimoYaw;

        Captura(LivingEntity mob, float yaw) {
            this.mob = mob;
            this.max = 40.0F + mob.getMaxHealth() * 8.0F;
            this.resistencia = max;
            this.dir = mob.getRandom().nextBoolean() ? 1 : -1;
            this.cambioEn = 30 + mob.getRandom().nextInt(30);
            this.ultimoYaw = yaw;
            // Si está aturdido por un Destello, cuesta la mitad
            MobEffectInstance lento = mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (lento != null && lento.getAmplifier() >= 5) this.resistencia *= 0.5F;
        }
    }

    private static final Map<UUID, Captura> CAPTURAS = new HashMap<>();
    /** El desatascador activo de cada jugador. */
    private static final Map<UUID, DesatascadorEntity> DESATASCADORES = new HashMap<>();

    private Succion() {}

    // ------------------------------------------------------------------ desatascador

    public static void registrarDesatascador(Player player, DesatascadorEntity nuevo) {
        DesatascadorEntity viejo = DESATASCADORES.put(player.getUUID(), nuevo);
        if (viejo != null && viejo.isAlive()) viejo.discard();
    }

    @Nullable
    public static DesatascadorEntity desatascador(Player player) {
        DesatascadorEntity d = DESATASCADORES.get(player.getUUID());
        if (d == null || !d.isAlive()) {
            DESATASCADORES.remove(player.getUUID());
            return null;
        }
        return d;
    }

    /** Dónde se "sujeta" a la víctima mientras se aspira con el desatascador. */
    private static Vec3 puntoSujecion(Player player) {
        return player.getEyePosition().add(player.getViewVector(1.0F).scale(3.0)).add(0, -0.5, 0);
    }

    // ------------------------------------------------------------------ tick principal

    public static void tick(Player player, int usado) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 boca = AspiraUtil.boca(player);

        // (el zumbido en bucle lo pone el cliente: client/AspiraCliente)
        particulas(level, player, boca);

        // 1) Desatascador: tiene prioridad sobre todo lo demás
        DesatascadorEntity des = desatascador(player);
        if (des != null) {
            LivingEntity victima = des.pegadoA();
            if (victima != null) {
                tirarDeCuerda(level, player, des, victima);
                return;
            }
            if (des.enBloque()) {
                // Gancho: el jugador vuela hacia la ventosa
                Vec3 d = des.position().subtract(player.position());
                if (d.length() < 1.5) {
                    des.discard();
                } else {
                    Vec3 v = player.getDeltaMovement().scale(0.6).add(d.normalize().scale(0.55));
                    AspiraUtil.empujar(player, v);
                }
                return;
            }
        }

        // 2) Tira y afloja en curso
        Captura cap = CAPTURAS.get(player.getUUID());
        if (cap != null) {
            tickCaptura(level, player, cap, boca);
            return;
        }

        // 3) Aspiración normal
        for (Entity e : AspiraUtil.enCono(player, Entity.class, ALCANCE, COS_CONO)) {
            Vec3 c = AspiraUtil.centro(e);
            double dist = c.distanceTo(boca);
            Vec3 haciaBoca = boca.subtract(c).normalize();

            if (e instanceof ItemEntity item) {
                AspiraUtil.empujar(item, haciaBoca.scale(0.45));
                if (dist < 1.6) {
                    item.setNoPickUpDelay();
                    item.playerTouch(player);
                }
            } else if (e instanceof ExperienceOrb orb) {
                AspiraUtil.empujar(orb, haciaBoca.scale(0.45));
                if (dist < 1.6) orb.playerTouch(player);
            } else if (e instanceof LivingEntity le && !(e instanceof ArmorStand)) {
                if (Deposito.esCapturable(le) && dist < DIST_AGARRE && !Deposito.lleno(Deposito.mochila(player))) {
                    CAPTURAS.put(player.getUUID(), new Captura(le, player.getYRot()));
                    level.playSound(null, le.getX(), le.getY(), le.getZ(),
                            SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.7F);
                    return;
                }
                double fuerza = 0.05 + 0.12 * (1.0 - dist / ALCANCE);
                if (le.getBbWidth() >= 1.4F || le.getMaxHealth() >= 100.0F) fuerza *= 0.3; // gordos y jefes
                Vec3 v = le.getDeltaMovement().add(haciaBoca.scale(fuerza));
                if (v.length() > 0.8) v = v.normalize().scale(0.8);
                AspiraUtil.empujar(le, v);

                // Ha llegado a la boca: la aspiradora le hace daño (1,5 corazones cada medio segundo)
                if (dist < DIST_BOCA && le.invulnerableTime <= 10) {
                    if (le.hurt(level.damageSources().playerAttack(player), DANO_BOCA)) {
                        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.2);
                        level.playSound(null, le.getX(), le.getY(), le.getZ(),
                                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 0.6F);
                    }
                }
            }
        }
    }

    /** Distancia a la que la víctima queda "agarrada" delante de la boquilla. */
    private static final double DIST_AGARRE_CUERDA = 1.4;

    /**
     * Recoger la cuerda: cuanto más cerca está la víctima, más recta (tensa) va la cuerda y más fuerte tira.
     * Al llegar queda agarrada mientras se mantenga el clic, cargando fuerza de lanzamiento.
     */
    private static void tirarDeCuerda(ServerLevel level, Player player, DesatascadorEntity des, LivingEntity victima) {
        Vec3 objetivo = puntoSujecion(player);
        Vec3 c = AspiraUtil.centro(victima);
        double dist = c.distanceTo(objetivo);
        boolean agarrado = des.agarrado() ? dist < 3.0 : dist < DIST_AGARRE_CUERDA;

        if (agarrado) {
            // Sujeta delante de la boquilla, siguiendo hacia donde miras
            Vec3 v = objetivo.subtract(c).scale(0.6);
            if (v.length() > 1.5) v = v.normalize().scale(1.5);
            AspiraUtil.empujar(victima, v);
            des.tirar(1.0F, true);
            registrarCamara(player);

            float carga = des.carga();
            if (carga >= 1.0F && victima.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.1);
            }
            return;
        }

        // Tensión: 0 a la distancia máxima de la cuerda, 1 al llegar
        float tension = (float) Mth.clamp(1.0 - (dist - DIST_AGARRE_CUERDA) / (DesatascadorEntity.LONGITUD_CUERDA * 0.6), 0.1, 1.0);
        double fuerza = 0.15 + 0.75 * tension * tension;
        if (victima.getBbWidth() >= 1.4F || victima.getMaxHealth() >= 100.0F) fuerza *= 0.4;
        Vec3 haciaBoca = objetivo.subtract(c).normalize();
        Vec3 v = victima.getDeltaMovement().scale(0.5).add(haciaBoca.scale(fuerza));
        if (v.length() > 1.3) v = v.normalize().scale(1.3);
        AspiraUtil.empujar(victima, v);
        des.tirar(tension, false);
    }

    private static void tickCaptura(ServerLevel level, Player player, Captura cap, Vec3 boca) {
        LivingEntity mob = cap.mob;
        if (!mob.isAlive() || mob.distanceTo(player) > 6.0) {
            CAPTURAS.remove(player.getUUID());
            enviarHud(player, mob.getId(), -1.0F, 0, false);
            return;
        }

        Vec3 derecha = AspiraUtil.derecha(player);

        // El mob se resiste hacia un lado; cambia de lado cada 1,5–3 s
        if (--cap.cambioEn <= 0) {
            cap.dir = -cap.dir;
            cap.cambioEn = 30 + mob.getRandom().nextInt(30);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.WOOL_BREAK, SoundSource.HOSTILE, 0.8F, 1.6F);
        }

        // ¿El jugador gira la cámara en dirección contraria al tirón?
        float yaw = player.getYRot();
        float delta = Mth.wrapDegrees(yaw - cap.ultimoYaw);
        cap.ultimoYaw = yaw;
        boolean tirando = delta * cap.dir < 0 && Math.abs(delta) > 0.5F;

        if (tirando) {
            cap.resistencia -= 1.0F + Math.min(Math.abs(delta), 15.0F) * 0.35F;
        } else {
            cap.resistencia = Math.min(cap.max, cap.resistencia + 0.3F);
            // El mob arrastra al jugador hacia su lado
            AspiraUtil.empujar(player, player.getDeltaMovement().add(derecha.scale(cap.dir * 0.06)));
        }
        cap.resistencia -= 0.15F; // la succión siempre va ganando un poquito

        // Mantener al mob delante de la boquilla, retorciéndose
        double meneo = Math.sin(mob.tickCount * 0.6) * 0.15;
        Vec3 objetivo = boca.add(player.getViewVector(1.0F).scale(1.4)).add(derecha.scale(cap.dir * (0.6 + meneo)));
        Vec3 v = objetivo.subtract(AspiraUtil.centro(mob)).scale(0.4);
        AspiraUtil.empujar(mob, v);

        Vec3 c = AspiraUtil.centro(mob);
        if (mob.tickCount % 3 == 0) {
            level.sendParticles(tirando ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.SMOKE, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.05);
        }

        // HUD gráfico (sin texto) en el cliente
        enviarHud(player, mob.getId(), Mth.clamp(cap.resistencia / cap.max, 0.0F, 1.0F), cap.dir, tirando);

        if (cap.resistencia <= 0.0F) {
            CAPTURAS.remove(player.getUUID());
            Deposito.capturar(player, mob);
            enviarHud(player, mob.getId(), -1.0F, 1, true);
        }
    }

    private static void particulas(ServerLevel level, Player player, Vec3 boca) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 derecha = AspiraUtil.derecha(player);
        Vec3 arriba = derecha.cross(look);
        for (int i = 0; i < 2; i++) {
            double d = 2.5 + player.getRandom().nextDouble() * 5.0;
            double spread = d * 0.55;
            Vec3 p = boca.add(look.scale(d))
                    .add(derecha.scale((player.getRandom().nextDouble() - 0.5) * spread))
                    .add(arriba.scale((player.getRandom().nextDouble() - 0.5) * spread));
            AspiraUtil.aire(level, p, boca.add(look.scale(0.3)), true);
        }
    }

    // ------------------------------------------------------------------ al soltar el clic

    /** Lanza o estampa a la víctima del desatascador según hacia dónde mire el jugador. */
    public static void soltar(Player player) {
        DesatascadorEntity des = desatascador(player);
        float camara = velocidadCamara(player);
        CAMARA.remove(player.getUUID());
        if (des == null) return;
        LivingEntity victima = des.pegadoA();
        if (victima == null) return;
        // Solo se lanza si estaba agarrada delante de la boquilla
        if (!des.agarrado()) return;

        Vec3 look = player.getViewVector(1.0F);
        float pitch = player.getXRot(); // positivo = mirando abajo
        ServerLevel level = (ServerLevel) player.level();

        // Cuanto más tiempo agarrada, más fuerte: x1 → x1,8
        double fCarga = 1.0 + 0.8 * des.carga();
        // Latigazo de cámara al soltar: 0°/tick = x1 ... 72°/tick o más = x7
        double fCamara = 1.0 + Math.min(camara / 12.0, 6.0);
        double velocidad = 1.6 * fCarga * fCamara;
        float potencia = (float) (fCarga * Math.min(fCamara, 3.0));

        if (pitch > 40.0F) {
            // ¡ESTAMPAR!
            Impactos.lanzar(victima, Impactos.Tipo.ESTAMPAR, player, new Vec3(look.x * 0.1, -1.0, look.z * 0.1), velocidad * 1.5, potencia);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
        } else if (pitch < -40.0F) {
            // ¡A LAS NUBES!
            Impactos.lanzar(victima, Impactos.Tipo.VUELO, player, new Vec3(look.x * 0.25, 1.0, look.z * 0.25), velocidad, potencia);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.8F);
        } else {
            // Lanzamiento hacia donde miras (con un poco de elevación)
            Impactos.lanzar(victima, Impactos.Tipo.PROYECTIL, player, look.add(0, 0.15, 0), velocidad, potencia);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        if (fCamara >= 3.0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        victima.fallDistance = 0.0F;
        des.discard();
    }

    // ------------------------------------------------------------------ velocidad de la cámara

    /** [yaw anterior, pitch anterior, v0, v1, v2, índice] por jugador, mientras sujeta a alguien. */
    private static final Map<UUID, float[]> CAMARA = new HashMap<>();

    private static void registrarCamara(Player player) {
        float[] c = CAMARA.get(player.getUUID());
        float yaw = player.getYRot(), pitch = player.getXRot();
        if (c == null) {
            CAMARA.put(player.getUUID(), new float[]{yaw, pitch, 0, 0, 0, 0});
            return;
        }
        float dy = Mth.wrapDegrees(yaw - c[0]);
        float dp = pitch - c[1];
        int i = (int) c[5];
        c[2 + i] = Mth.sqrt(dy * dy + dp * dp);
        c[5] = (i + 1) % 3;
        c[0] = yaw;
        c[1] = pitch;
    }

    /** Máximo giro de cámara (grados por tick) de los últimos 3 ticks. */
    private static float velocidadCamara(Player player) {
        float[] c = CAMARA.get(player.getUUID());
        if (c == null) return 0.0F;
        // incluir el último giro, por si se suelta justo en el mismo tick del latigazo
        float dy = Mth.wrapDegrees(player.getYRot() - c[0]);
        float dp = player.getXRot() - c[1];
        float ahora = Mth.sqrt(dy * dy + dp * dp);
        return Math.max(ahora, Math.max(c[2], Math.max(c[3], c[4])));
    }

    /** Cuando se deja de aspirar por lo que sea: se acaba el tira y afloja. */
    public static void parar(Player player) {
        CAMARA.remove(player.getUUID());
        Captura c = CAPTURAS.remove(player.getUUID());
        if (c != null) enviarHud(player, c.mob.getId(), -1.0F, 0, false);
    }

    private static void enviarHud(Player player, int mob, float resistencia, int dir, boolean tirando) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.miscelanea.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    new com.miscelanea.network.CapturaPacket(mob, resistencia, dir, tirando));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CAPTURAS.remove(event.getEntity().getUUID());
        DesatascadorEntity d = DESATASCADORES.remove(event.getEntity().getUUID());
        if (d != null) d.discard();
    }
}
