package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.pistola.BengalaEntity;
import com.miscelanea.armasfuego.pistola.Carga;
import com.miscelanea.armasfuego.pistola.Revolver;
import com.miscelanea.registry.ModEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModEnchantments;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Winchester 1873 de palanca, «el arma que conquistó el Oeste». Usa las balas del revólver (como la de verdad con el
 * Colt): la normal, la bengala, el dardo, la de fogueo, la imán y la de tiempo bala, y además las suyas: la de plata
 * (el doble contra los no muertos: para hombres lobo y vampiros, como en Castlevania), la de dinamita (estalla donde da,
 * sin romper bloques, como en Red Dead) y la de hielo (congela, como Sub-Zero). Se carga bala a bala por la portilla
 * mezclando tipos en el depósito tubular, y salen al revés de como entraron (la última que entra es la primera que
 * sale, como en la de verdad); el HUD enseña el depósito con el color de cada bala y la siguiente marcada. Tras cada
 * disparo baja y sube la palanca (a caballo, girándola con una mano). Encantamientos: Repetición (palanca más rápida),
 * Dead Eye (a caballo, recto y más fuerte), Carga rápida y Depósito largo (más balas).
 */
public class WinchesterItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("winchester_disparo");
    /** NBT: las balas del depósito en orden de entrada (la última es la que sale). */
    public static final String DEPOSITO = "Deposito";

    public WinchesterItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return EquilibrioArmas.winchesterCapacidad() + 3 * nivel(ModEnchantments.WINCHESTER_DEPOSITO, s);
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return Math.max(2, Math.round(EquilibrioArmas.winchesterRecarga() * (1 - 0.25F * nivel(ModEnchantments.WINCHESTER_CARGA_RAPIDA, s))));
    }

    @Override
    public boolean recargaPorBala() {
        return true;
    }

    /** ¿Es una bala de la Winchester? (las seis del revólver y las tres suyas) */
    public static boolean esBala(ItemStack m) {
        return Carga.de(m) != null || m.is(ModItems.BALA_PLATA.get()) || m.is(ModItems.BALA_DINAMITA.get()) || m.is(ModItems.BALA_HIELO.get());
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return esBala(m);
    }

    @Override
    protected Item municionNormal() {
        return ModItems.REVOLVER_BALA.get();
    }

    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.WINCHESTER_CARGAR.get();
    }

    // ------------------------------------------------------------------ el depósito tubular

    public static List<Item> deposito(ItemStack s) {
        return deposito(s, balas(s));
    }

    /** El depósito con {@code n} balas (si el NBT tiene menos, las que faltan son normales: las de antes del depósito). */
    private static List<Item> deposito(ItemStack s, int n) {
        List<Item> l = new ArrayList<>();
        if (s.hasTag() && s.getTag().contains(DEPOSITO)) {
            ListTag t = s.getTag().getList(DEPOSITO, Tag.TAG_STRING);
            for (int i = 0; i < t.size(); i++) {
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(t.getString(i)));
                if (it != null && it != Items.AIR) l.add(it);
            }
        }
        // las de antes del depósito (o del botín): todas del tipo que dice el arma (o normales)
        Item viejo = tipo(s);
        if (viejo == null || viejo == Items.AIR || !esBala(new ItemStack(viejo))) viejo = ModItems.REVOLVER_BALA.get();
        while (l.size() < n) l.add(0, viejo);
        while (l.size() > n) l.remove(0);
        return l;
    }

    public static void ponerDeposito(ItemStack s, List<Item> l) {
        ListTag t = new ListTag();
        for (Item it : l) t.add(StringTag.valueOf(ForgeRegistries.ITEMS.getKey(it).toString()));
        s.getOrCreateTag().put(DEPOSITO, t);
        ponerBalas(s, l.size());
        if (!l.isEmpty()) s.getOrCreateTag().putString(TIPO, ForgeRegistries.ITEMS.getKey(l.get(l.size() - 1)).toString());
    }

    /** Mete una bala en el depósito (por la portilla). */
    public static void meter(ItemStack s, Item bala) {
        List<Item> l = deposito(s);
        l.add(bala);
        ponerDeposito(s, l);
    }

    /** La que va a salir (la última que entró). */
    @Override
    public Item tipoActual(ItemStack s) {
        List<Item> l = deposito(s);
        return l.isEmpty() ? municionNormal() : l.get(l.size() - 1);
    }

    /** Color de cada bala en el HUD del depósito. */
    public static int color(Item it) {
        Carga c = Carga.de(new ItemStack(it));
        if (c != null) return c.color;
        if (it == ModItems.BALA_PLATA.get()) return 0xFFE6EAF0;
        if (it == ModItems.BALA_DINAMITA.get()) return 0xFFD42A1E;
        if (it == ModItems.BALA_HIELO.get()) return 0xFF8FE0FF;
        return 0xFFE0A548;
    }

    /** Se mezclan: cualquier bala vale. */
    @Override
    protected boolean mismoTipo(ItemStack s) {
        return false;
    }

    /** Cualquier bala de la Winchester, de la que haya (primero la de la otra mano): se mezclan. */
    @Override
    public ItemStack elegir(Player p, ItemStack s) {
        return buscar(p, WinchesterItem::esBala);
    }

    @Override
    protected int gastarMunicion(ServerPlayer p, ItemStack s) {
        ItemStack m = elegir(p, s);
        if (m.isEmpty()) return 0;
        meter(s, m.getItem());
        m.shrink(1);
        mezcla(p, s);
        return 0;   // ya lo ha contado meter()
    }

    @Override
    protected int cargaCreativa(ServerPlayer p, ItemStack s) {
        ItemStack m = elegir(p, s);
        meter(s, m.isEmpty() ? municionNormal() : m.getItem());
        mezcla(p, s);
        return 0;
    }

    private static void mezcla(ServerPlayer p, ItemStack s) {
        if (new HashSet<>(deposito(s)).size() >= 4) LogrosMiscelanea.dar(p, "winchester_mezcla");
    }

    /** Ticks de la palanca: a caballo, con el giro, un 30 % menos; Repetición, un 15 % menos por nivel. */
    public static int palanca(Player p, ItemStack s) {
        float t = EquilibrioArmas.winchesterPalanca() * (1 - 0.15F * nivel(ModEnchantments.WINCHESTER_REPETICION, s));
        if (p.isPassenger()) t *= 0.7F;
        return Math.max(2, Math.round(t));
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return palanca(p, s);
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        double alcance = EquilibrioArmas.winchesterAlcance();
        // sale la última que entró (en creativo no se gasta: sale siempre la misma)
        boolean gasta = !p.getAbilities().instabuild;
        List<Item> dep = deposito(s, balas(s) + (gasta ? 1 : 0));   // intentar() ya ha restado la que sale
        Item tipo = dep.isEmpty() ? municionNormal() : dep.get(dep.size() - 1);
        if (gasta && !dep.isEmpty()) {
            dep.remove(dep.size() - 1);
            ponerDeposito(s, dep);
        }
        boolean deadEye = p.isPassenger() && nivel(ModEnchantments.WINCHESTER_DEAD_EYE, s) > 0;
        float dano = EquilibrioArmas.winchesterDano() * (deadEye ? 1.25F : 1F);
        double disp = deadEye ? 0 : 0.005;
        Carga c = Carga.de(new ItemStack(tipo));
        Vec3 bocaT = boca(p, 1.0, 0.25, 0.2);
        if (c == Carga.FOGUEO) {
            // de fogueo: mucho ruido y nada de bala; los animales de alrededor salen corriendo
            efectos(l, p, bocaT, ModSounds.REVOLVER_FOGUEO.get(), 6.0F, 0.95F, 5, false);
            l.gameEvent(p, GameEvent.EXPLODE, bocaT);
            Revolver.asustar(l, p);
        } else if (c == Carga.BENGALA) {
            BengalaEntity b = new BengalaEntity(ModEntities.REVOLVER_BENGALA.get(), l);
            b.setOwner(p);
            b.setPos(bocaT.x, bocaT.y, bocaT.z);
            b.setDeltaMovement(Balistica.dispersar(p.getViewVector(1F), disp, p.getRandom()).scale(1.8));
            b.preparar(false);
            l.addFreshEntity(b);
            efectos(l, p, bocaT, ModSounds.WINCHESTER_DISPARO.get(), 4.0F, 0.8F, 3, false);
        } else {
            float d = c == Carga.DARDO ? Revolver.DANO_DARDO : c == Carga.IMAN ? Revolver.DANO_IMAN : c == Carga.TIEMPO ? Revolver.DANO_TIEMPO
                    : tipo == ModItems.BALA_HIELO.get() ? dano * 0.6F : dano;
            ParticleOptions estela = tipo == ModItems.BALA_PLATA.get() ? ParticleTypes.END_ROD : tipo == ModItems.BALA_HIELO.get() ? ParticleTypes.SNOWFLAKE
                    : c == Carga.TIEMPO ? ParticleTypes.REVERSE_PORTAL : null;
            var t = tiro(p, d, alcance, alcance * 0.5, 0.7F, disp, 0.2F, DANO, false, estela);
            LivingEntity v = t.entidad();
            if (tipo == ModItems.BALA_PLATA.get()) {
                // plata: otro tanto contra los no muertos (con el brillo de la plata)
                if (v != null && v.getMobType() == MobType.UNDEAD) {
                    Balistica.herir(v, p, dano, 0.2F, DANO, true);
                    l.sendParticles(ParticleTypes.END_ROD, t.pos().x, t.pos().y, t.pos().z, 10, 0.2, 0.3, 0.2, 0.05);
                    if (!v.isAlive()) LogrosMiscelanea.dar(p, "winchester_plata");
                }
            } else if (tipo == ModItems.BALA_DINAMITA.get()) {
                dinamita(p, t.pos(), v);
            } else if (tipo == ModItems.BALA_HIELO.get()) {
                if (v != null) hielo(p, v);
                l.sendParticles(ParticleTypes.SNOWFLAKE, t.pos().x, t.pos().y, t.pos().z, 12, 0.2, 0.2, 0.2, 0.05);
            } else if (c == Carga.DARDO && v != null) {
                Revolver.sedar(v);
                LogrosMiscelanea.dar(p, "revolver_sedante");
            } else if (c == Carga.IMAN) {
                Revolver.atraer(l, p, t.pos());
            } else if (c == Carga.TIEMPO && v != null) {
                Revolver.ralentizar(v);
            }
            efectos(l, p, bocaT, ModSounds.WINCHESTER_DISPARO.get(), 4.0F, c == Carga.DARDO ? 1.2F : 0.95F, 3, false);
        }
        // la palanca: el casquillo sale por arriba a mitad del recorrido
        casquillo(l, p, boca(p, 0.35, 0.22, 0.12));
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.WINCHESTER_PALANCA.get(), SoundSource.PLAYERS, 0.8F, p.isPassenger() ? 1.15F : 1.0F);
        LogrosMiscelanea.dar(p, "winchester_disparo");
        if (p.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) LogrosMiscelanea.dar(p, "winchester_giro");
    }

    /** Bala de hielo: congela un rato (como en la nieve en polvo) y apaga el fuego. */
    static void hielo(ServerPlayer p, LivingEntity v) {
        v.clearFire();
        v.setTicksFrozen(Math.max(v.getTicksFrozen(), v.getTicksRequiredToFreeze() + EquilibrioArmas.winchesterHielo()));
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EquilibrioArmas.winchesterHielo(), 2), p);
        p.serverLevel().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
        if (v != p) LogrosMiscelanea.dar(p, "winchester_hielo");
    }

    /** Bala de dinamita: un estallido pequeño donde da (sin romper bloques). */
    static void dinamita(ServerPlayer p, Vec3 q, LivingEntity directo) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 4, 0.2, 0.2, 0.2, 0.02);
        l.playSound(null, q.x, q.y, q.z, ModSounds.CANON_DISPARO.get(), SoundSource.PLAYERS, 1.2F, 1.7F);
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(2.2), e -> e.isAlive() && e != p && e != directo)) {
            double d = v.position().distanceTo(q);
            if (d > 2.2) continue;
            Balistica.herir(v, p, (float) (4 * (1 - d / 2.2)), 0F, DANO, false);
            Vec3 emp = v.position().subtract(q).normalize().scale(0.4);
            v.push(emp.x, 0.2, emp.z);
            v.hurtMarked = true;
        }
    }
}
