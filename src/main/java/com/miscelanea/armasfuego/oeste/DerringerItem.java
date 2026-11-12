package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Derringer de dos cañones, la pistola de bolsillo de los tahúres del póker del Oeste. Dos tiros, poco alcance (el
 * daño baja deprisa con la distancia) y sale rapidísimo: el primer tiro justo después de sacarla pega más. En tercera
 * persona no se ve en la mano (el as en la manga) hasta que dispara. Se recarga partiéndola por la bisagra: agachado +
 * mantener clic derecho mete las dos balas de golpe.
 * <p>
 * El as en la manga de verdad (1.1.0): con la Derringer en cualquier hueco del inventario, la tecla de la manga (K por
 * defecto, en Opciones → Controles) la saca de golpe a la mano (lo que llevabas va a su hueco) y sale con el truco
 * del tahúr. Munición: la bala normal, la carta marcada, la de oro (GoldenEye) y la de humo (el truco del mago: una
 * nube de humo donde da y tú desapareces un momento).
 */
public class DerringerItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("derringer_disparo");
    /** Ticks que se deja ver en tercera persona tras disparar. */
    public static final int VISIBLE = 60;
    /** Ventana del truco de tahúr (ticks desde que se saca). */
    public static final int DESENFUNDE = 12;

    public DerringerItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return 2;
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return 24;
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.BALA_DERRINGER.get()) || m.is(ModItems.CARTA_MARCADA.get()) || m.is(ModItems.BALA_ORO.get()) || m.is(ModItems.BALA_HUMO.get());
    }

    @Override
    protected Item municionNormal() {
        return ModItems.BALA_DERRINGER.get();
    }

    @Override
    protected boolean llenaDeGolpe() {
        return true;
    }

    /** Ventana del truco de tahúr (más larga con As en la manga). */
    public static int desenfunde(ItemStack s) {
        return DESENFUNDE + 10 * nivel(com.miscelanea.registry.ModEnchantments.DERRINGER_AS, s);
    }

    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.DERRINGER_ABRIR.get();
    }

    @Override
    protected void empezarRecarga(ServerPlayer p, ItemStack s) {
        // se parte por la bisagra y caen los casquillos gastados
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DERRINGER_ABRIR.get(), SoundSource.PLAYERS, 0.7F, 1.25F);
        for (int i = balas(s); i < capacidad(s); i++) casquillo(l, p, boca(p, 0.45, 0.3, 0.3));
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return 5;
    }

    /** Cuándo sacó cada uno la Derringer de la manga (para el logro y la espera). */
    static final java.util.Map<java.util.UUID, Long> MANGA = new java.util.HashMap<>();

    /**
     * La manga: busca la Derringer en el inventario (fuera de la mano) y la cambia de golpe con lo que hay en la mano
     * (eso va al hueco donde estaba la Derringer). Sale con el desenfunde del tahúr; con Prestidigitador, además, se
     * mete sola una bala si le falta. Devuelve si la ha sacado.
     */
    public static boolean sacarDeLaManga(ServerPlayer p) {
        if (p.isSpectator() || p.isUsingItem() || p.getMainHandItem().getItem() instanceof DerringerItem) return false;
        long ahora = p.serverLevel().getGameTime();
        Long antes = MANGA.get(p.getUUID());
        if (antes != null && ahora - antes < EquilibrioArmas.derringerManga()) return false;
        var inv = p.getInventory();
        int hueco = -1;
        for (int i = 0; i < inv.items.size(); i++)
            if (i != inv.selected && inv.items.get(i).getItem() instanceof DerringerItem) { hueco = i; break; }
        boolean otraMano = hueco < 0 && p.getOffhandItem().getItem() instanceof DerringerItem;
        if (hueco < 0 && !otraMano) return false;
        ItemStack mano = p.getMainHandItem();
        ItemStack d;
        if (otraMano) {
            d = p.getOffhandItem();
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, mano);
        } else {
            d = inv.items.get(hueco);
            inv.items.set(hueco, mano);
        }
        inv.items.set(inv.selected, d);
        MANGA.put(p.getUUID(), ahora);
        // sale con el truco del tahúr: cuenta como recién sacada
        SACADO.put(p.getUUID(), ahora);
        VISTO.put(p.getUUID(), ahora);
        if (nivel(com.miscelanea.registry.ModEnchantments.DERRINGER_PRESTIDIGITADOR, d) > 0 && balas(d) < 2 && d.getItem() instanceof DerringerItem di) {
            ItemStack m = di.elegir(p, d);
            if (!m.isEmpty() || p.getAbilities().instabuild) {
                if (balas(d) == 0) d.getOrCreateTag().putString(TIPO, net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(m.isEmpty() ? di.municionNormal() : di.tipoDe(m)).toString());
                if (!p.getAbilities().instabuild) m.shrink(1);
                ponerBalas(d, balas(d) + 1);
            }
        }
        p.getCooldowns().removeCooldown(d.getItem());
        inv.setChanged();
        p.containerMenu.broadcastChanges();
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8F, 1.7F);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DERRINGER_ABRIR.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
        return true;
    }

    public static long desdeManga(ServerPlayer p) {
        Long t = MANGA.get(p.getUUID());
        return t == null ? 1000 : p.serverLevel().getGameTime() - t;
    }

    public static void olvidarManga(java.util.UUID id) {
        MANGA.remove(id);
    }

    /** ¿Se ve en tercera persona? (solo justo después de disparar). */
    public static boolean visible(ItemStack s, Level l) {
        return l != null && l.getGameTime() - disparoT(s) < VISIBLE;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        un(p, s);
        // Doble o nada: si quedaba el otro cañón, salen los dos a la vez
        if (nivel(com.miscelanea.registry.ModEnchantments.DERRINGER_DOBLE, s) > 0 && balas(s) > 0) {
            if (!p.getAbilities().instabuild) ponerBalas(s, balas(s) - 1);
            un(p, s);
        }
    }

    /** Bala de humo: una nube donde da (ciega a los de alrededor) y tú desapareces un momento. ¡Tachán! */
    private static void humo(ServerPlayer p, net.minecraft.world.phys.Vec3 q) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, q.x, q.y + 0.3, q.z, 14, 0.8, 0.5, 0.8, 0.01);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y + 0.3, q.z, 20, 0.9, 0.6, 0.9, 0.02);
        l.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 16, 0.3, 0.5, 0.3, 0.05);
        l.playSound(null, q.x, q.y, q.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        for (var v : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(q, q).inflate(3), e -> e.isAlive() && e != p)) {
            v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 60, 0), p);
            if (v instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p) m.setTarget(null);
        }
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 60, 0, false, false));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
        LogrosMiscelanea.dar(p, "derringer_humo");
    }

    private void un(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        boolean tahur = desdeSacado(p) <= desenfunde(s);
        float as = 0.15F * nivel(com.miscelanea.registry.ModEnchantments.DERRINGER_AS, s);
        float dano = EquilibrioArmas.derringerDano() * (tahur ? EquilibrioArmas.derringerDesenfunde() + as : 1F);
        Item tipo = tipoActual(s);
        if (tipo == ModItems.BALA_ORO.get()) dano *= 2.5F;            // la pistola de oro de GoldenEye
        else if (tipo == ModItems.CARTA_MARCADA.get()) dano *= 0.6F;
        else if (tipo == ModItems.BALA_HUMO.get()) dano *= 0.3F;
        double alcance = EquilibrioArmas.derringerAlcance();
        var t = tiro(p, dano, alcance, 3, 0.4F, tahur ? 0.004 : 0.02, 0.15F, DANO, false, tipo == ModItems.BALA_ORO.get() ? ParticleTypes.WAX_ON : null);
        if (t.entidad() != null) {
            if (tipo == ModItems.CARTA_MARCADA.get()) {
                // la carta marcada: el que la recibe brilla un rato para todos (y tiene mala suerte)
                t.entidad().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 160, 0, false, false), p);
                t.entidad().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.UNLUCK, 600, 0), p);
                LogrosMiscelanea.dar(p, "derringer_carta");
            } else if (tipo == ModItems.BALA_ORO.get() && !t.entidad().isAlive()) LogrosMiscelanea.dar(p, "derringer_oro");
            if (desdeManga(p) <= 20) LogrosMiscelanea.dar(p, "derringer_manga");
        }
        if (tipo == ModItems.BALA_HUMO.get()) humo(p, t.pos());
        efectos(l, p, boca(p, 0.6, 0.3, 0.25), ModSounds.DERRINGER_DISPARO.get(), 2.5F, 1.1F, 2, false);
        if (tahur) {
            l.sendParticles(ParticleTypes.ENCHANTED_HIT, t.pos().x, t.pos().y, t.pos().z, 8, 0.1, 0.1, 0.1, 0.2);
            if (t.entidad() != null) LogrosMiscelanea.dar(p, "derringer_tahur");
        }
        LogrosMiscelanea.dar(p, "derringer_disparo");
    }
}
