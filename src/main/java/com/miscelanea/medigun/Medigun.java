package com.miscelanea.medigun;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.MedigunPacket;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModEffects;
import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Lógica del servidor de la medigun: enganche del rayo, curación, sobrecura y ÜberCarga. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Medigun {
    public static final float MAX = 100.0F;
    public static final String CARGA = "Uber";
    public static final String UBER_HASTA = "UberHasta";
    public static final String UBER_ACTIVA = "UberActiva";
    public static final String CURANDO = "Curando";
    public static final String COLOR = "Color";
    public static final int COLOR_DEFECTO = 0xE0392C;

    /** Distancia para engancharse apuntando y distancia a la que se rompe el rayo. */
    private static final double ENGANCHE = 10.0, MAXIMA = 14.0;
    /** Ticks que aguanta el rayo sin ver al paciente (detrás de una esquina). */
    private static final int GRACIA_SIN_VISTA = 20;
    private static final int DURACION_UBER = 160;
    /** Sobrecura: hasta 4 corazones de absorción, que se van perdiendo poco a poco. */
    private static final float SOBRECURA_MAX = 8.0F;
    /** Segundos para llenar la ÜberCarga curando a alguien herido (el doble si ya está lleno de vida). */
    private static final float SEGUNDOS_CARGA = 40.0F;

    private record Enganche(UUID objetivo, int[] sinVista) {}

    private static final Map<UUID, Enganche> ENGANCHES = new HashMap<>();
    private static final Map<UUID, Long> ULTIMO_DANO = new HashMap<>();
    /** Absorción añadida por la medigun, y último tick en el que se curó a cada uno. */
    private static final Map<UUID, float[]> SOBRECURA = new HashMap<>();
    private static final Map<UUID, Long> ULTIMA_CURA = new HashMap<>();
    private static final Map<UUID, Long> ULTIMO_REBOTE = new HashMap<>();

    private Medigun() {}

    // ------------------------------------------------------------------ NBT

    public static float carga(ItemStack s) {
        return s.hasTag() ? s.getTag().getFloat(CARGA) : 0.0F;
    }

    public static boolean uberActiva(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(UBER_ACTIVA);
    }

    public static int color(ItemStack s) {
        return s.hasTag() && s.getTag().contains(COLOR) ? s.getTag().getInt(COLOR) : COLOR_DEFECTO;
    }

    public static int mezclar(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    /** Color vivo a partir del tinte (los colores de los tintes son algo apagados para un rayo). */
    public static int colorDeTinte(DyeColor dye) {
        float[] f = dye.getTextureDiffuseColors();
        if (dye == DyeColor.BLACK) return 0x2A2A30;
        float max = Math.max(f[0], Math.max(f[1], f[2]));
        float k = max > 0.01F ? Math.min(1.0F / max, 1.35F) : 1.0F;
        int r = Mth.clamp((int) (f[0] * k * 255), 0, 255);
        int g = Mth.clamp((int) (f[1] * k * 255), 0, 255);
        int b = Mth.clamp((int) (f[2] * k * 255), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ rayo

    /** Cada tick mientras se mantiene el clic derecho. */
    static void tick(ServerPlayer p, ItemStack gun) {
        ServerLevel level = p.serverLevel();
        LivingEntity t = objetivo(p);
        if (t != null && !sigueValido(p, t)) {
            soltar(p, gun);
            t = null;
        }
        if (t == null) {
            t = buscar(p);
            if (t == null) {
                if (gun.getOrCreateTag().getBoolean(CURANDO)) gun.getTag().remove(CURANDO);
                return;
            }
            ENGANCHES.put(p.getUUID(), new Enganche(t.getUUID(), new int[]{0}));
            gun.getOrCreateTag().putBoolean(CURANDO, true);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.MEDIGUN_CONECTAR.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            avisar(p, gun);
        }
        curar(p, t, gun);
        if (p.tickCount % 20 == 0) avisar(p, gun); // para los que acaban de llegar
    }

    private static LivingEntity objetivo(ServerPlayer p) {
        Enganche e = ENGANCHES.get(p.getUUID());
        if (e == null) return null;
        return p.serverLevel().getEntity(e.objetivo()) instanceof LivingEntity le ? le : null;
    }

    private static boolean sigueValido(ServerPlayer p, LivingEntity t) {
        if (!t.isAlive() || t.isSpectator() || t.level() != p.level()) return false;
        if (t.distanceTo(p) > MAXIMA) return false;
        Enganche e = ENGANCHES.get(p.getUUID());
        if (p.hasLineOfSight(t)) e.sinVista()[0] = 0;
        else if (++e.sinVista()[0] > GRACIA_SIN_VISTA) return false;
        return true;
    }

    /** El jugador más cerca de la línea de mira (no hace falta apuntarle con precisión). */
    private static LivingEntity buscar(ServerPlayer p) {
        Vec3 ojo = p.getEyePosition();
        Vec3 mira = p.getViewVector(1.0F);
        LivingEntity mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (Player o : p.serverLevel().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(ENGANCHE),
                o -> o != p && o.isAlive() && !o.isSpectator())) {
            Vec3 c = o.position().add(0, o.getBbHeight() * 0.55, 0);
            Vec3 d = c.subtract(ojo);
            double a = d.dot(mira);
            if (a <= 0.5 || a > ENGANCHE) continue;
            double perp = d.subtract(mira.scale(a)).length();
            if (perp > 0.75 + a * 0.04) continue;
            if (p.level().clip(new ClipContext(ojo, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType()
                    != HitResult.Type.MISS) continue;
            if (perp < mejorDist) {
                mejorDist = perp;
                mejor = o;
            }
        }
        return mejor;
    }

    private static void curar(ServerPlayer p, LivingEntity t, ItemStack gun) {
        long ahora = p.level().getGameTime();
        float max = t.getMaxHealth();
        // 2 de vida/s, que sube hasta 6/s si el paciente lleva 3 s sin recibir daño
        float sinDano = (ahora - ULTIMO_DANO.getOrDefault(t.getUUID(), -10000L)) / 20.0F;
        float porSegundo = 2.0F + Mth.clamp((sinDano - 3.0F) / 3.0F, 0.0F, 1.0F) * 4.0F;
        float cantidad = porSegundo / 20.0F;
        boolean herido = t.getHealth() < max - 0.01F;
        if (herido) {
            t.heal(cantidad);
        } else {
            float abs = t.getAbsorptionAmount();
            if (abs < SOBRECURA_MAX) {
                float mas = Math.min(cantidad * 0.5F, SOBRECURA_MAX - abs);
                t.setAbsorptionAmount(abs + mas);
                SOBRECURA.computeIfAbsent(t.getUUID(), k -> new float[1])[0] += mas;
            }
        }
        ULTIMA_CURA.put(t.getUUID(), ahora);

        CompoundTag tag = gun.getOrCreateTag();
        if (uberActiva(gun)) {
            t.addEffect(new MobEffectInstance(ModEffects.UBERCARGA.get(), 12, 0, false, false, true));
        } else {
            float antes = tag.getFloat(CARGA);
            if (antes < MAX) {
                float sube = MAX / (SEGUNDOS_CARGA * 20.0F) * (herido ? 1.0F : 0.5F);
                float ahoraCarga = Math.min(MAX, antes + sube);
                tag.putFloat(CARGA, ahoraCarga);
                if (ahoraCarga >= MAX) {
                    p.playNotifySound(ModSounds.UBER_LISTA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        }
    }

    /** Se suelta el clic derecho o se rompe el rayo. */
    static void soltar(ServerPlayer p, ItemStack gun) {
        boolean tenia = ENGANCHES.remove(p.getUUID()) != null;
        if (gun.hasTag()) gun.getTag().remove(CURANDO);
        if (tenia) avisar(p, gun);
    }

    // ------------------------------------------------------------------ ÜberCarga

    static void activarUber(ServerPlayer p, ItemStack gun) {
        CompoundTag tag = gun.getOrCreateTag();
        tag.putLong(UBER_HASTA, p.level().getGameTime() + DURACION_UBER);
        tag.putBoolean(UBER_ACTIVA, true);
        p.addEffect(new MobEffectInstance(ModEffects.UBERCARGA.get(), 12, 0, false, false, true));
        LivingEntity t = objetivo(p);
        if (t != null) t.addEffect(new MobEffectInstance(ModEffects.UBERCARGA.get(), 12, 0, false, false, true));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.UBER_ACTIVAR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        avisar(p, gun);
    }

    /** Cada tick desde inventoryTick: va gastando la ÜberCarga activa. */
    static void tickObjeto(LivingEntity dueno, ItemStack gun) {
        if (!gun.hasTag()) return;
        CompoundTag tag = gun.getTag();
        if (tag.getBoolean(UBER_ACTIVA)) {
            long quedan = tag.getLong(UBER_HASTA) - dueno.level().getGameTime();
            if (quedan <= 0) {
                tag.remove(UBER_ACTIVA);
                tag.remove(UBER_HASTA);
                tag.remove(CARGA);
                if (dueno instanceof ServerPlayer sp) avisar(sp, gun);
            } else {
                tag.putFloat(CARGA, MAX * quedan / DURACION_UBER);
                dueno.addEffect(new MobEffectInstance(ModEffects.UBERCARGA.get(), 12, 0, false, false, true));
            }
        }
        boolean usando = dueno.isUsingItem() && dueno.getUseItem() == gun;
        if (!usando && tag.getBoolean(CURANDO)) tag.remove(CURANDO);
    }

    private static void avisar(ServerPlayer p, ItemStack gun) {
        Enganche e = ENGANCHES.get(p.getUUID());
        int objetivo = -1;
        if (e != null && p.serverLevel().getEntity(e.objetivo()) != null) {
            objetivo = p.serverLevel().getEntity(e.objetivo()).getId();
        }
        int uber = 0;
        if (uberActiva(gun)) uber = (int) Math.max(0, gun.getTag().getLong(UBER_HASTA) - p.level().getGameTime());
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new MedigunPacket(p.getId(), objetivo, uber, color(gun)));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Player p && !p.level().isClientSide) {
            ULTIMO_DANO.put(p.getUUID(), p.level().getGameTime());
        }
    }

    /** Invulnerable durante la ÜberCarga (menos el vacío, /kill y similares). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !e.hasEffect(ModEffects.UBERCARGA.get())) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        long ahora = e.level().getGameTime();
        if (ahora - ULTIMO_REBOTE.getOrDefault(e.getUUID(), 0L) > 8) {
            ULTIMO_REBOTE.put(e.getUUID(), ahora);
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.UBER_REBOTE.get(), SoundSource.PLAYERS, 0.9F,
                    0.9F + e.getRandom().nextFloat() * 0.2F);
        }
    }

    /** La sobrecura se va perdiendo cuando nadie te cura. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SOBRECURA.isEmpty()) return;
        var server = event.getServer();
        if (server.getTickCount() % 5 != 0) return;
        Iterator<Map.Entry<UUID, float[]>> it = SOBRECURA.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            float[] s = en.getValue();
            if (p.level().getGameTime() - ULTIMA_CURA.getOrDefault(en.getKey(), 0L) < 20) continue;
            float quitar = Math.min(0.35F, s[0]);
            float abs = p.getAbsorptionAmount();
            p.setAbsorptionAmount(Math.max(0.0F, abs - quitar));
            s[0] -= quitar;
            if (s[0] <= 0.001F || abs <= 0.0F) it.remove();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ENGANCHES.remove(id);
        ULTIMO_DANO.remove(id);
        SOBRECURA.remove(id);
        ULTIMA_CURA.remove(id);
        ULTIMO_REBOTE.remove(id);
    }
}
