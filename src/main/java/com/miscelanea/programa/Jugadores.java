package com.miscelanea.programa;

import com.miscelanea.carcel.Ruido;
import com.miscelanea.registry.ModBlocks;
import com.miscelanea.registry.ModEffects;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado de cada jugador dentro de la prueba: cordura (3 barras), psicosis con el Skinner Man,
 * caído/desangrándose, rig y su recarga. Más las reglas del inventario (3 huecos) y el ruido al moverse.
 */
public final class Jugadores {
    public static final int CORDURA_MAX = 300;          // 3 barras de 100
    public static final int PSICOSIS_TICKS = 900;       // 45 s con el Skinner Man
    public static final int DESANGRARSE = 800;          // 40 s en el suelo
    public static final int REANIMAR = 60;              // 3 s manteniendo clic

    public static final class Estado {
        public int cordura = CORDURA_MAX;
        public int psicosis;            // ticks que quedan de psicosis (0 = no)
        public UUID skinner;
        public int caido;               // ticks que quedan para desangrarse (0 = de pie)
        public int reanimando;
        public long ultimoReanimar;
        public Rigs.Rig rig = Rigs.Rig.STUN;
        public int rigCd;               // ticks de recarga que quedan
        public int rigOff;              // ticks con el rig desactivado (porra de Coyle)
        public Vec3 ultimaPos = Vec3.ZERO;
    }

    private static final Map<UUID, Estado> ESTADOS = new HashMap<>();

    private Jugadores() {}

    public static Estado de(Player p) {
        return ESTADOS.computeIfAbsent(p.getUUID(), u -> new Estado());
    }

    public static void reiniciar(Player p, Rigs.Rig rig) {
        Estado e = new Estado();
        e.rig = rig;
        e.rigCd = 0;
        ESTADOS.put(p.getUUID(), e);
    }

    public static void quitar(Player p) {
        Estado e = ESTADOS.remove(p.getUUID());
        if (e != null && e.skinner != null && p.level() instanceof ServerLevel sl) {
            var s = sl.getEntity(e.skinner);
            if (s != null) s.discard();
        }
    }

    public static boolean caido(Player p) {
        Estado e = ESTADOS.get(p.getUUID());
        return e != null && e.caido > 0;
    }

    // ------------------------------------------------------------------ cordura

    public static void drenar(ServerPlayer p, int n) {
        Estado e = de(p);
        if (e.psicosis > 0 || e.caido > 0) return;
        e.cordura = Math.max(0, e.cordura - n);
        if (e.cordura == 0) empezarPsicosis(p, e);
    }

    public static void restaurarCordura(ServerPlayer p) {
        Estado e = de(p);
        e.cordura = CORDURA_MAX;
        terminarPsicosis(p, e);
    }

    private static void empezarPsicosis(ServerPlayer p, Estado e) {
        e.psicosis = PSICOSIS_TICKS;
        p.addEffect(new MobEffectInstance(ModEffects.PSICOSIS.get(), PSICOSIS_TICKS, 0, false, false, true));
        p.playNotifySound(SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.0F, 0.6F);
        ServerLevel lvl = (ServerLevel) p.level();
        // El Skinner Man aparece detrás de ti, a unos bloques
        Vec3 atras = p.getLookAngle().multiply(-1, 0, -1).normalize().scale(10).add(p.position());
        Mob s = ModEntities.SKINNER.get().spawn(lvl, BlockPos.containing(atras), MobSpawnType.EVENT);
        if (s instanceof com.miscelanea.carcel.enemigos.Skinner sk) {
            sk.setVictima(p.getUUID());
            e.skinner = sk.getUUID();
        }
    }

    private static void terminarPsicosis(ServerPlayer p, Estado e) {
        e.psicosis = 0;
        p.removeEffect(ModEffects.PSICOSIS.get());
        if (e.skinner != null) {
            var s = ((ServerLevel) p.level()).getEntity(e.skinner);
            if (s != null) s.discard();
            e.skinner = null;
        }
    }

    /** El Skinner Man te ha alcanzado. */
    public static void atrapadoPorSkinner(ServerPlayer p) {
        Estado e = de(p);
        p.hurt(p.damageSources().magic(), 8.0F);
        terminarPsicosis(p, e);
        e.cordura = CORDURA_MAX / 3;
    }

    // ------------------------------------------------------------------ caído / reanimar

    /** Golpe mortal: en cooperativo te quedas en el suelo; devuelve true si el jugador queda caído. */
    public static boolean caer(ServerPlayer p, int compañeros) {
        Estado e = de(p);
        if (compañeros <= 1 || e.caido > 0) return false;
        e.caido = DESANGRARSE;
        e.reanimando = 0;
        p.setHealth(4.0F);
        p.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
            if (!(m instanceof com.miscelanea.carcel.enemigos.Griton) && !(m instanceof com.miscelanea.carcel.enemigos.Jefe)) m.setTarget(null);
        }
        return true;
    }

    public static void levantar(ServerPlayer p) {
        Estado e = de(p);
        e.caido = 0;
        e.reanimando = 0;
        p.setHealth(8.0F);
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.6F);
    }

    /** Un compañero mantiene clic derecho sobre el caído (o usa la jeringuilla). */
    public static void reanimar(ServerPlayer quien, ServerPlayer caido, boolean jeringa) {
        Estado e = de(caido);
        if (e.caido <= 0) return;
        if (jeringa) {
            levantar(caido);
            return;
        }
        long t = quien.level().getGameTime();
        if (t - e.ultimoReanimar > 8) e.reanimando = 0;
        e.ultimoReanimar = t;
        e.reanimando += 4;   // el clic mantenido llega cada 4 ticks
        if (e.reanimando >= REANIMAR) levantar(caido);
    }

    // ------------------------------------------------------------------ cada tick

    public static void tick(ServerPlayer p, ServerLevel lvl) {
        Estado e = de(p);
        // La comida fija en 17: no hay regeneración natural, pero se puede correr
        p.getFoodData().setFoodLevel(17);
        p.getFoodData().setSaturation(0.0F);
        p.getFoodData().setExhaustion(0.0F);

        if (e.rigCd > 0) e.rigCd--;
        if (e.rigOff > 0) e.rigOff--;

        if (e.psicosis > 0) {
            if (--e.psicosis == 0) {
                terminarPsicosis(p, e);
                e.cordura = CORDURA_MAX;   // sobrevivir a la psicosis te devuelve la cordura
                p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        }

        if (e.caido > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 4, false, false, false));
            p.setSprinting(false);
            if (--e.caido == 0) Prueba.morir(p);
        }

        normalizarInventario(p);
        ruido(p, lvl, e);
    }

    private static void ruido(ServerPlayer p, ServerLevel lvl, Estado e) {
        Vec3 pos = p.position();
        boolean seMueve = pos.distanceToSqr(e.ultimaPos) > 0.0025;
        e.ultimaPos = pos;
        if (!seMueve || p.isCrouching() || com.miscelanea.carcel.Escondites.escondido(p)) return;
        if (p.isSprinting() && p.tickCount % 10 == 0) Ruido.alertar(lvl, pos, 14, p);
        else if (!p.isSprinting() && p.tickCount % 20 == 0) Ruido.alertar(lvl, pos, 5, p);
    }

    /** Las reglas del inventario de The Outlast Trials. */
    public static boolean esLlave(ItemStack s) {
        return s.is(ModItems.LLAVE.get());
    }

    public static boolean esGrande(ItemStack s) {
        return s.is(ModBlocks.BIDON.get().asItem());
    }

    /** ¿Cabe este objeto en los 3 huecos? (llaves y objetos grandes siempre). */
    public static boolean cabe(Player p, ItemStack s) {
        if (esLlave(s)) return true;
        if (esGrande(s)) return p.getOffhandItem().isEmpty();
        Inventory inv = p.getInventory();
        for (int i = 0; i < 3; i++) {
            ItemStack h = inv.getItem(i);
            if (h.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(h, s) && h.getCount() < h.getMaxStackSize()) return true;
        }
        return false;
    }

    private static void normalizarInventario(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) continue;
            if (esLlave(s)) {
                if (i < 9) moverA(inv, i, 9, 36);
            } else if (esGrande(s)) {
                inv.items.set(i, ItemStack.EMPTY);
                if (p.getOffhandItem().isEmpty()) p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, s);
                else p.drop(s, false);
            } else if (i >= 3) {
                inv.items.set(i, ItemStack.EMPTY);
                ItemStack resto = meterEnHuecos(inv, s);
                if (!resto.isEmpty()) p.drop(resto, false);
            }
        }
        ItemStack mano2 = p.getOffhandItem();
        if (!mano2.isEmpty() && !esGrande(mano2)) {
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack resto = meterEnHuecos(inv, mano2);
            if (!resto.isEmpty()) p.drop(resto, false);
        }
        // Cargar un bidón: más lento y sin correr
        if (esGrande(p.getOffhandItem()) || esGrande(p.getMainHandItem())) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false, false));
            p.setSprinting(false);
        }
    }

    private static void moverA(Inventory inv, int desde, int a, int b) {
        for (int j = a; j < b; j++) {
            if (inv.items.get(j).isEmpty()) {
                inv.items.set(j, inv.items.get(desde));
                inv.items.set(desde, ItemStack.EMPTY);
                return;
            }
        }
    }

    private static ItemStack meterEnHuecos(Inventory inv, ItemStack s) {
        for (int i = 0; i < 3 && !s.isEmpty(); i++) {
            ItemStack h = inv.items.get(i);
            if (ItemStack.isSameItemSameTags(h, s) && h.getCount() < h.getMaxStackSize()) {
                int n = Math.min(s.getCount(), h.getMaxStackSize() - h.getCount());
                h.grow(n);
                s.shrink(n);
            }
        }
        for (int i = 0; i < 3 && !s.isEmpty(); i++) {
            if (inv.items.get(i).isEmpty()) {
                inv.items.set(i, s.copy());
                s.setCount(0);
            }
        }
        return s;
    }
}
