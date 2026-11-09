package com.miscelanea.armasfuego.comun;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base de las armas de fuego de mano de la segunda tanda (Winchester, Derringer, Thompson, mosquete y cañón de mano).
 * Controles comunes (con el arma en la mano principal):
 * <ul>
 *   <li>Clic derecho: disparar (la Thompson, manteniéndolo, en ráfaga).</li>
 *   <li>Agachado + mantener clic derecho: recargar (con animación; se corta si se suelta) o, si está encasquillada,
 *       desatascarla.</li>
 * </ul>
 * El estado va en el NBT del arma (así el cliente lo tiene para el HUD y las animaciones sin paquetes):
 * {@code Balas}, {@code Atasco}, {@code Modo} (lo que se hace mientras se mantiene el clic: recargar, desatascar o
 * ráfaga) y {@code DisparoT} (hora del mundo del último disparo, para el retroceso y las animaciones).
 */
public abstract class ArmaFuegoItem extends Item {
    public static final String BALAS = "Balas", ATASCO = "Atasco", MODO = "Modo", DISPARO_T = "DisparoT";
    public static final int MODO_NADA = 0, MODO_RECARGA = 1, MODO_DESATASCO = 2, MODO_RAFAGA = 3;
    /** Lo que se tarda en desatascar (ticks con el clic mantenido). */
    public static final int T_DESATASCO = 16;

    /** Cuándo se sacó el arma (para Desenfunde de la Derringer) y el último tick en que se vio en la mano. */
    static final Map<UUID, Long> SACADO = new HashMap<>(), VISTO = new HashMap<>();

    protected ArmaFuegoItem(Properties p) {
        super(p);
    }

    // ------------------------------------------------------------------ lo que define cada arma

    /** Balas que caben. */
    public abstract int capacidad();

    /** Ticks de un paso de recarga. */
    public abstract int ticksRecarga();

    /** Si cada paso mete una bala (Winchester) o se llena de golpe al acabar (las demás). */
    public boolean recargaPorBala() {
        return false;
    }

    /** ¿Tiene encima lo necesario para un paso de recarga? */
    public abstract boolean tieneMunicion(Player p);

    /** Gasta lo de un paso de recarga y devuelve cuántas balas mete. */
    protected abstract int gastarMunicion(ServerPlayer p, ItemStack s);

    /** El objeto que se enseña en el HUD junto al contador. */
    public abstract ItemStack iconoMunicion();

    /** El disparo de verdad (ya comprobado que hay bala, no está atascada ni en espera). */
    protected abstract void disparar(ServerPlayer p, ItemStack s);

    /** Ticks de espera tras un disparo (cooldown del objeto). */
    protected abstract int enfriamiento(Player p, ItemStack s);

    /** Probabilidad de que se encasquille un disparo. */
    protected float probAtasco(Player p, ItemStack s) {
        return EquilibrioArmas.encasquilladoBase();
    }

    /** Sonido del paso de recarga. */
    protected SoundEvent sonidoRecarga() {
        return ModSounds.REVOLVER_CARGAR.get();
    }

    // ------------------------------------------------------------------ estado

    public static int balas(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(BALAS) : 0;
    }

    public static void ponerBalas(ItemStack s, int n) {
        s.getOrCreateTag().putInt(BALAS, Math.max(0, n));
    }

    public static boolean atascada(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(ATASCO);
    }

    public static int modo(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(MODO) : 0;
    }

    public static long disparoT(ItemStack s) {
        return s.hasTag() ? s.getTag().getLong(DISPARO_T) : -1000;
    }

    /** Ticks desde el último disparo (para las animaciones). */
    public static float desdeDisparo(ItemStack s, Level l, float pt) {
        return l == null ? 1000 : l.getGameTime() - disparoT(s) + pt;
    }

    public boolean llena(ItemStack s) {
        return balas(s) >= capacidad();
    }

    // ------------------------------------------------------------------ controles

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        CompoundTag t = s.getOrCreateTag();
        if (p.isShiftKeyDown()) {
            if (atascada(s)) {
                t.putInt(MODO, MODO_DESATASCO);
                p.startUsingItem(hand);
                return InteractionResultHolder.consume(s);
            }
            if (llena(s) || !p.getAbilities().instabuild && !tieneMunicion(p)) {
                if (!level.isClientSide && !llena(s))
                    p.displayClientMessage(Component.translatable("msg.miscelanea.armafuego.sin_municion", iconoMunicion().getHoverName()).withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(s);
            }
            t.putInt(MODO, MODO_RECARGA);
            p.startUsingItem(hand);
            if (!level.isClientSide) empezarRecarga((ServerPlayer) p, s);
            return InteractionResultHolder.consume(s);
        }
        t.putInt(MODO, MODO_NADA);
        return apretarGatillo(level, p, s);
    }

    /** Clic derecho sin agacharse: por defecto, un disparo (semiautomática). */
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(s);
        if (!intentar(level, p, s)) return InteractionResultHolder.fail(s);
        p.getCooldowns().addCooldown(this, enfriamiento(p, s));
        return InteractionResultHolder.consume(s);
    }

    /**
     * Comprueba bala y atasco y dispara (en el servidor) o pide el retroceso (en el cliente). Devuelve si ha salido
     * algo o ha sonado el clic.
     */
    protected boolean intentar(Level level, Player p, ItemStack s) {
        if (balas(s) <= 0 || atascada(s)) {
            if (!level.isClientSide) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_VACIO.get(), SoundSource.PLAYERS, 0.7F, 1.15F);
                p.displayClientMessage(Component.translatable(atascada(s) ? "msg.miscelanea.armafuego.atascada" : "msg.miscelanea.armafuego.vacia")
                        .withStyle(atascada(s) ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            }
            return true;
        }
        if (level.isClientSide) {
            com.miscelanea.client.armasfuego.comun.FuegoCliente.disparoLocal(this, s);
            return true;
        }
        ServerPlayer sp = (ServerPlayer) p;
        if (p.getRandom().nextFloat() < probAtasco(p, s)) {
            atascar(sp, s);
            return true;
        }
        if (!p.getAbilities().instabuild) ponerBalas(s, balas(s) - 1);
        s.getOrCreateTag().putLong(DISPARO_T, level.getGameTime());
        disparar(sp, s);
        if (s.isDamageableItem()) s.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    /** Se encasquilla: la bala no sale (y no se gasta) hasta desatascarla. */
    protected void atascar(ServerPlayer p, ItemStack s) {
        s.getOrCreateTag().putBoolean(ATASCO, true);
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ARMAFUEGO_ATASCO.get(), SoundSource.PLAYERS, 0.9F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        Vec3 b = boca(p, 0.8, 0.3, 0.22);
        l.sendParticles(ParticleTypes.SMOKE, b.x, b.y, b.z, 3, 0.03, 0.03, 0.03, 0.01);
        p.displayClientMessage(Component.translatable("msg.miscelanea.armafuego.encasquillada").withStyle(ChatFormatting.GOLD), true);
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "armafuego_atasco");
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        if (!(e instanceof Player p)) return;
        int t = getUseDuration(s) - quedan;
        switch (modo(s)) {
            case MODO_RECARGA -> {
                if (level.isClientSide) return;
                int paso = ticksRecarga();
                if (t > 0 && t % paso == 0) {
                    if (llena(s) || !p.getAbilities().instabuild && !tieneMunicion(p)) {
                        p.stopUsingItem();
                        return;
                    }
                    int n = p.getAbilities().instabuild ? (recargaPorBala() ? 1 : capacidad()) : gastarMunicion((ServerPlayer) p, s);
                    ponerBalas(s, Math.min(capacidad(), balas(s) + n));
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), sonidoRecarga(), SoundSource.PLAYERS, 0.8F, 0.95F + p.getRandom().nextFloat() * 0.1F);
                    alRecargar((ServerPlayer) p, s);
                    if (!recargaPorBala() || llena(s)) p.stopUsingItem();
                }
            }
            case MODO_DESATASCO -> {
                if (level.isClientSide || t != T_DESATASCO) return;
                s.getOrCreateTag().remove(ATASCO);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.REVOLVER_AMARTILLAR.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
                p.stopUsingItem();
            }
            case MODO_RAFAGA -> rafaga(level, p, s, t);
            default -> {}
        }
    }

    /** Lo que se hace al empezar a recargar (sonido de abrir, casquillos fuera...). */
    protected void empezarRecarga(ServerPlayer p, ItemStack s) {}

    /** Tras cada paso de recarga. */
    protected void alRecargar(ServerPlayer p, ItemStack s) {}

    /** Ráfaga: cada tick con el clic mantenido (solo la Thompson). */
    protected void rafaga(Level level, Player p, ItemStack s, int t) {}

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int quedan) {
        if (s.hasTag()) s.getTag().putInt(MODO, MODO_NADA);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide || !(e instanceof ServerPlayer p)) return;
        if (sel && p.getMainHandItem() == s) {
            long ahora = level.getGameTime();
            Long visto = VISTO.put(p.getUUID(), ahora);
            if (visto == null || visto < ahora - 1) {
                SACADO.put(p.getUUID(), ahora);
                alSacar(p, s);
            }
        }
    }

    /** Recién sacada a la mano. */
    protected void alSacar(ServerPlayer p, ItemStack s) {}

    /** Ticks desde que se sacó (para Desenfunde). */
    protected static long desdeSacado(ServerPlayer p) {
        Long t = SACADO.get(p.getUUID());
        return t == null ? 1000 : p.serverLevel().getGameTime() - t;
    }

    public static void olvidar(UUID id) {
        SACADO.remove(id);
        VISTO.remove(id);
    }

    public static void limpiar() {
        SACADO.clear();
        VISTO.clear();
    }

    // ------------------------------------------------------------------ ayudas para disparar

    /** Dónde sale el tiro: {@code adelante}, {@code derecha} y {@code abajo} desde los ojos. */
    public static Vec3 boca(Entity p, double adelante, double derecha, double abajo) {
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        if (p instanceof Player pl && pl.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) der = der.scale(-1);
        return p.getEyePosition().add(mira.scale(adelante)).add(der.scale(derecha)).add(0, -abajo, 0);
    }

    /** Un tiro instantáneo de bala con caída de daño, impacto y herida. Devuelve el tiro. */
    protected static Balistica.Tiro tiro(ServerPlayer p, float dano, double alcance, double desdeCaida, float minCaida, double disp,
                                         float perf, ResourceKey<DamageType> tipo, boolean rafaga, @Nullable ParticleOptions estela) {
        ServerLevel l = p.serverLevel();
        Vec3 dir = Balistica.dispersar(p.getViewVector(1.0F), disp, p.getRandom());
        Balistica.Tiro t = Balistica.trazar(l, p, p.getEyePosition(), dir, alcance, boca(p, 0.8, 0.3, 0.22), estela);
        if (t.entidad() != null) {
            float d = dano * Balistica.caida(t.distancia(), desdeCaida, alcance, minCaida);
            if (Balistica.herir(t.entidad(), p, d, perf, tipo, rafaga)) {
                Vec3 emp = t.entidad().position().subtract(p.position()).normalize();
                t.entidad().knockback(rafaga ? 0.05 : 0.25, -emp.x, -emp.z);
            }
        }
        Balistica.impacto(l, p, t);
        return t;
    }

    /** Fogonazo, humo, sonido, ruido para el sculk y (si se pide) un casquillo que sale volando a la derecha. */
    public static void efectos(ServerLevel l, Entity p, Vec3 boca, SoundEvent snd, float vol, float tono, int humo, boolean casquillo) {
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 q = boca.add(mira.scale(0.25));
        l.sendParticles(ArmasFuegoRegistro.FOGONAZO.get(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        if (humo > 0) l.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, humo, 0.04, 0.04, 0.04, 0.01);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), snd, SoundSource.PLAYERS, vol, tono + l.random.nextFloat() * 0.08F);
        l.gameEvent(p, GameEvent.PROJECTILE_SHOOT, boca);
        if (casquillo) casquillo(l, p, boca.subtract(mira.scale(0.45)));
    }

    /** Un casquillo de latón (partícula barata con rebote) que sale a la derecha y hacia arriba. */
    public static void casquillo(ServerLevel l, Entity p, Vec3 desde) {
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 der = new Vec3(-mira.z, 0, mira.x);
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        Vec3 v = der.scale(0.12 + l.random.nextFloat() * 0.05).add(0, 0.14 + l.random.nextFloat() * 0.05, 0).add(p.getDeltaMovement());
        l.sendParticles(ArmasFuegoRegistro.CASQUILLO.get(), desde.x, desde.y, desde.z, 0, v.x, v.y, v.z, 1.0);
    }

    /** Busca en la otra mano y luego en el inventario algo que cumpla {@code cond}. */
    public static ItemStack buscar(Player p, java.util.function.Predicate<ItemStack> cond) {
        if (cond.test(p.getOffhandItem())) return p.getOffhandItem();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && cond.test(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    public static int contar(Player p, java.util.function.Predicate<ItemStack> cond) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && cond.test(s)) n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ varios

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack a, ItemStack b) {
        return a.getItem() != b.getItem();
    }

    @Override
    public boolean canContinueUsing(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable("msg.miscelanea.armafuego.balas", balas(s), capacidad(), iconoMunicion().getHoverName()).withStyle(ChatFormatting.GOLD));
        if (atascada(s)) l.add(Component.translatable("msg.miscelanea.armafuego.atascada").withStyle(ChatFormatting.RED));
        String id = getDescriptionId();
        l.add(Component.translatable(id + ".ayuda1").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable(id + ".ayuda2").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("msg.miscelanea.armafuego.ayuda_recarga").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new com.miscelanea.client.armasfuego.comun.FuegoCliente.Mano());
    }
}
