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
    /** NBT: cuándo acabó la última recarga (para no avisar de que no queda munición justo al terminar). */
    public static final String FIN_CARGA = "FinCarga";
    public static final int MODO_NADA = 0, MODO_RECARGA = 1, MODO_DESATASCO = 2, MODO_RAFAGA = 3;
    /** Lo que se tarda en desatascar (ticks con el clic mantenido). */
    public static final int T_DESATASCO = 16;

    /** Cuándo se sacó el arma (para Desenfunde de la Derringer) y el último tick en que se vio en la mano. */
    protected static final Map<UUID, Long> SACADO = new HashMap<>(), VISTO = new HashMap<>();

    protected ArmaFuegoItem(Properties p) {
        super(p);
    }

    // ------------------------------------------------------------------ lo que define cada arma

    /** NBT: la munición que hay dentro (id del objeto). */
    public static final String TIPO = "Tipo";

    /** Balas que caben (con sus encantamientos). */
    public abstract int capacidad(ItemStack s);

    /** Ticks de un paso de recarga (con sus encantamientos). */
    public abstract int ticksRecarga(ItemStack s);

    /** Si cada paso mete una bala (Winchester) o se llena de golpe al acabar (las demás). */
    public boolean recargaPorBala() {
        return false;
    }

    /** ¿Es munición de esta arma? (la normal y las especiales) */
    protected abstract boolean acepta(ItemStack m);

    /** La munición normal (la que sale si no hay nada dentro). */
    protected abstract Item municionNormal();

    /** El tipo de munición de un objeto (el cañón cuenta cualquier roca como el bolaño normal). */
    protected Item tipoDe(ItemStack m) {
        return m.getItem();
    }

    /** Balas que mete cada objeto de munición (un tambor entero, o una). */
    protected int balasPorObjeto(ItemStack s) {
        return 1;
    }

    /** Si al recargar se meten de golpe todas las que quepan, una por objeto (la Derringer). */
    protected boolean llenaDeGolpe() {
        return false;
    }

    /** Pólvora que hace falta además de la munición en cada recarga (mosquete y cañón). */
    protected int polvora() {
        return 0;
    }

    /** Si al recargar se cambia todo (tambor, cinta): no hace falta que sea del mismo tipo que lo que hay. */
    protected boolean cambiaEntera() {
        return balasPorObjeto(ItemStack.EMPTY) > 1;
    }

    /** Para el nombre con el óxido («erosionada» o «erosionado»). */
    protected boolean femenino() {
        return true;
    }

    /** Disparos para pasar de fase de óxido (como el revólver: 40). */
    protected int usosPorFase() {
        return 40;
    }

    public static Item tipo(ItemStack s) {
        if (!s.hasTag() || !s.getTag().contains(TIPO)) return null;
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(s.getTag().getString(TIPO)));
    }

    /**
     * ¿La recarga necesita la misma munición que la que hay dentro? (entonces el aviso dice cuál falta). Si está vacía,
     * se cambia entera o se mezcla, vale cualquiera: el aviso es «no te quedan balas».
     */
    protected boolean mismoTipo(ItemStack s) {
        return balas(s) > 0 && !cambiaEntera();
    }

    /** La munición que hay dentro (o la normal). */
    public Item tipoActual(ItemStack s) {
        Item t = tipo(s);
        return t == null || t == net.minecraft.world.item.Items.AIR ? municionNormal() : t;
    }

    /** El objeto que se enseña en el HUD junto al contador. */
    public ItemStack iconoMunicion(ItemStack s) {
        return new ItemStack(tipoActual(s));
    }

    /** La munición que se va a meter: de la misma que hay si queda algo; si no, la de la otra mano o la primera que haya. */
    public ItemStack elegir(Player p, ItemStack s) {
        if (balas(s) > 0 && !cambiaEntera()) {
            Item t = tipoActual(s);
            return buscar(p, x -> acepta(x) && tipoDe(x) == t);
        }
        return buscar(p, this::acepta);
    }

    /** Lleno, pero con un tambor (o cinta) de otra munición a mano: se puede cambiar. */
    protected boolean cambiaTipo(Player p, ItemStack s) {
        if (!cambiaEntera()) return false;
        ItemStack m = elegir(p, s);
        return !m.isEmpty() && tipoDe(m) != tipoActual(s);
    }

    public boolean tieneMunicion(Player p, ItemStack s) {
        return !elegir(p, s).isEmpty() && contar(p, x -> x.is(net.minecraft.world.item.Items.GUNPOWDER)) >= polvora();
    }

    /** Gasta lo de un paso de recarga y devuelve cuántas balas mete. */
    protected int gastarMunicion(ServerPlayer p, ItemStack s) {
        ItemStack m = elegir(p, s);
        if (m.isEmpty()) return 0;
        Item t = tipoDe(m);
        if (t != tipoActual(s) && cambiaEntera()) ponerBalas(s, 0);   // el tambor o la cinta viejos se quitan
        s.getOrCreateTag().putString(TIPO, net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(t).toString());
        for (int k = 0; k < polvora(); k++) buscar(p, x -> x.is(net.minecraft.world.item.Items.GUNPOWDER)).shrink(1);
        if (!llenaDeGolpe()) {
            m.shrink(1);
            return balasPorObjeto(s);
        }
        int n = 0, hueco = capacidad(s) - balas(s);
        while (n < hueco && !m.isEmpty()) {
            m.shrink(1);
            n++;
            if (m.isEmpty()) m = buscar(p, x -> acepta(x) && tipoDe(x) == t);
        }
        return n;
    }

    public static int nivel(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.enchantment.Enchantment> e, ItemStack s) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(e.get(), s);
    }

    /** El disparo de verdad (ya comprobado que hay bala, no está atascada ni en espera). */
    protected abstract void disparar(ServerPlayer p, ItemStack s);

    /** Ticks de espera tras un disparo (cooldown del objeto). */
    protected abstract int enfriamiento(Player p, ItemStack s);

    /** Probabilidad de que se encasquille un disparo (más cuanto más oxidado, como las tres primeras). */
    protected float probAtasco(Player p, ItemStack s) {
        if (EquilibrioArmas.sinAtascosPrueba) return 0F;
        return EquilibrioArmas.encasquilladoBase() + Cobre.atasco(Cobre.oxido(s));
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
        return balas(s) >= capacidad(s);
    }

    // ------------------------------------------------------------------ controles

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        CompoundTag t = s.getOrCreateTag();
        if (p.isShiftKeyDown()) {
            // agachado con un panal o un hacha en la otra mano: encerar o rascar el cobre (como el revólver)
            ItemStack otra = p.getOffhandItem();
            if (Cobre.esPanal(otra) || Cobre.esHacha(otra)) {
                if (!level.isClientSide) {
                    Vec3 b = boca(p, 0.6, 0.3, 0.25);
                    if (Cobre.esPanal(otra)) Cobre.encerar(p, s, otra, b);
                    else Cobre.rascar(p, s, otra, b);
                }
                return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
            }
            if (atascada(s)) {
                t.putInt(MODO, MODO_DESATASCO);
                p.startUsingItem(hand);
                return InteractionResultHolder.consume(s);
            }
            if (llena(s) && !cambiaTipo(p, s) || !p.getAbilities().instabuild && !tieneMunicion(p, s)) {
                // sin munición: se avisa, salvo si la recarga acaba de terminar (el botón seguía pulsado: nada que decir)
                // (mientras se mantenga pulsado, el cliente lo repite cada pocos ticks: cada repetición alarga el silencio)
                boolean recien = level.getGameTime() - t.getLong(FIN_CARGA) <= 10;
                if (recien && !level.isClientSide) t.putLong(FIN_CARGA, level.getGameTime());
                if (!level.isClientSide && !llena(s) && !recien) {
                    Component m = mismoTipo(s) ? Component.translatable("msg.miscelanea.armafuego.sin_municion", iconoMunicion(s).getHoverName())
                            : Component.translatable("msg.miscelanea.armafuego.sin_balas");
                    p.displayClientMessage(m.copy().withStyle(ChatFormatting.GRAY), true);
                }
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
            // la hora del disparo también en el cliente: la palanca y el culatazo empiezan sin esperar al servidor
            s.getOrCreateTag().putLong(DISPARO_T, level.getGameTime());
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
        Cobre.avisar(sp, s, Cobre.gastar(s, usosPorFase(), p.isInWaterOrRain()));
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
                int paso = ticksRecarga(s);
                if (t > 0 && t % paso == 0) {
                    if (llena(s) && !cambiaTipo(p, s) || !p.getAbilities().instabuild && !tieneMunicion(p, s)) {
                        s.getOrCreateTag().putLong(FIN_CARGA, level.getGameTime());
                        p.stopUsingItem();
                        return;
                    }
                    int n = p.getAbilities().instabuild ? cargaCreativa((ServerPlayer) p, s) : gastarMunicion((ServerPlayer) p, s);
                    ponerBalas(s, Math.min(capacidad(s), balas(s) + n));
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), sonidoRecarga(), SoundSource.PLAYERS, 0.8F, 0.95F + p.getRandom().nextFloat() * 0.1F);
                    alRecargar((ServerPlayer) p, s);
                    if (!recargaPorBala() || llena(s) || !p.getAbilities().instabuild && !tieneMunicion(p, s)) {
                        s.getOrCreateTag().putLong(FIN_CARGA, level.getGameTime());
                        p.stopUsingItem();
                    }
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

    /** Un paso de recarga en creativo (sin gastar nada): devuelve las balas que mete. */
    protected int cargaCreativa(ServerPlayer p, ItemStack s) {
        if (!s.getOrCreateTag().contains(TIPO)) {
            ItemStack m = elegir(p, s);
            Item ti = m.isEmpty() ? municionNormal() : tipoDe(m);
            s.getOrCreateTag().putString(TIPO, net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ti).toString());
        }
        return recargaPorBala() ? 1 : capacidad(s);
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
        // el cobre: más disperso cuanto más oxidado, pero el cañón gastado pega más y perfora más armadura
        ItemStack arma = p.getMainHandItem();
        disp += Cobre.dispersion(Cobre.oxido(arma));
        dano *= Cobre.dano(arma);
        perf = Math.min(1F, perf + Cobre.perforacion(arma));
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
        return 12;
    }

    @Override
    public boolean isValidRepairItem(ItemStack s, ItemStack material) {
        return material.is(net.minecraft.world.item.Items.COPPER_INGOT);
    }

    @Override
    public Component getName(ItemStack s) {
        return Cobre.nombre(Component.translatable(getDescriptionId(s)), s, femenino());
    }

    /** En el inventario, como un saco: un panal encima lo encera y un hacha lo rasca (clic derecho). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction a,
                                            Player p, net.minecraft.world.entity.SlotAccess cursor) {
        if (a != net.minecraft.world.inventory.ClickAction.SECONDARY || !slot.allowModification(p) || otro.isEmpty()) return false;
        if (Cobre.esPanal(otro)) return Cobre.encerar(p, s, otro, null);
        if (Cobre.esHacha(otro)) return Cobre.rascar(p, s, otro, null);
        return false;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable("msg.miscelanea.armafuego.balas", balas(s), capacidad(s), iconoMunicion(s).getHoverName()).withStyle(ChatFormatting.GOLD));
        l.add(Cobre.estado(s));
        Cobre.tooltip(s, l);
        if (atascada(s)) l.add(Component.translatable("msg.miscelanea.armafuego.atascada").withStyle(ChatFormatting.RED));
        String id = getDescriptionId();
        l.add(Component.translatable(id + ".ayuda1").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable(id + ".ayuda2").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("msg.miscelanea.armafuego.ayuda_recarga").withStyle(ChatFormatting.DARK_GRAY));
        l.add(Component.translatable("msg.miscelanea.armafuego.ayuda_cobre").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new com.miscelanea.client.armasfuego.comun.FuegoCliente.Mano());
    }
}
