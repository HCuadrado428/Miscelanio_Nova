package com.miscelanea.armasfuego.rifle;

import com.miscelanea.registry.ModEnchantments;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rifle de catalejo: estado en el NBT del arma (bala en la recámara, casquillo gastado, cerrojo en marcha, óxido del
 * cobre) y lo que pasa al disparar, al accionar el cerrojo y mientras se apunta.
 * <ul>
 *   <li>Recámara: id del cartucho cargado ("" = vacía). Gastada: hay un casquillo vacío dentro.</li>
 *   <li>El cerrojo se acciona a mano (clic izquierdo sin bala lista, o agachado + clic izquierdo): saca lo que haya
 *       en la recámara (el casquillo sale volando) y mete el siguiente cartucho (el de la otra mano si lo hay).</li>
 *   <li>El cobre se oxida con los disparos (como los bloques de cobre): más dispersión y algún fallo.</li>
 * </ul>
 */
public final class Rifle {
    private Rifle() {}

    public static final String RECAMARA = "Recamara", GASTADA = "Gastada", CERROJO = "Cerrojo", OXIDO = "Oxido", ENCERADO = "Encerado";
    /** Duración del ciclo del cerrojo (ticks) y en qué momento sale el casquillo y entra el cartucho nuevo. */
    public static final int CERROJO_TICKS = 16, T_EXPULSA = 11, T_CARGA = 5;
    /** Tiempo con la mira quieta para contener la respiración. */
    public static final int FIRME_TICKS = 40;
    /** Daño y cabeza: ahora en la config (EquilibrioArmas.rifleDano / rifleCabeza); estos son los de por defecto. */
    public static final float DANO = 9F, CABEZA = 1.6F, VELOCIDAD = 6F;
    public static final double GRAVEDAD = 0.012;
    /** Largo de la cuerda (y lo que añade cada nivel de Tirolinero). */
    public static final double CUERDA_MAX = 48, CUERDA_NIVEL = 16;
    public static final int BALA = 0, TRAZADORA = 1, CUERDA = 2, PINTURA = 3;
    /** Color de la bala de pintura que hay en la recámara. */
    public static final String RECAMARA_COLOR = "RecamaraColor";

    // ------------------------------------------------------------------ estado del arma

    public static String recamara(ItemStack s) {
        return s.hasTag() ? s.getTag().getString(RECAMARA) : "";
    }

    public static boolean gastada(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(GASTADA);
    }

    public static int cerrojo(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(CERROJO) : 0;
    }

    public static int oxido(ItemStack s) {
        return s.hasTag() ? Mth.clamp(s.getTag().getInt(OXIDO), 0, 3) : 0;
    }

    public static boolean encerado(ItemStack s) {
        return com.miscelanea.armasfuego.comun.Cobre.encerado(s);
    }

    /** ¿Hay un cartucho listo para disparar? */
    public static boolean lista(ItemStack s) {
        return cerrojo(s) == 0 && !recamara(s).isEmpty() && !gastada(s);
    }

    public static int nivel(ItemStack s, Enchantment e) {
        return EnchantmentHelper.getItemEnchantmentLevel(e, s);
    }

    /** Tipo de munición del cartucho (BALA, TRAZADORA, CUERDA) o -1. */
    public static int tipo(Item i) {
        if (i == ModItems.BALA_RIFLE.get()) return BALA;
        if (i == ModItems.BALA_TRAZADORA.get()) return TRAZADORA;
        if (i == ModItems.BALA_CUERDA.get()) return CUERDA;
        if (i == ModItems.BALA_PINTURA.get()) return PINTURA;
        return -1;
    }

    public static Item item(String id) {
        if (id == null || id.isEmpty()) return null;
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return i == null || tipo(i) < 0 ? null : i;
    }

    /** El siguiente cartucho: el de la otra mano si es munición del rifle; si no, el primero del inventario. */
    private static ItemStack buscarMunicion(Player p) {
        ItemStack otra = p.getOffhandItem();
        if (tipo(otra.getItem()) >= 0) return otra;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && tipo(s.getItem()) >= 0) return s;
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ cerrojo

    /** Empieza el ciclo del cerrojo (si no está ya en marcha). */
    public static void accionarCerrojo(ServerPlayer p, ItemStack s) {
        if (cerrojo(s) > 0) return;
        s.getOrCreateTag().putInt(CERROJO, CERROJO_TICKS);
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.9F, 1.6F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.5F, 1.9F);
    }

    /** Cada tick con el rifle en la mano: avanza el cerrojo. */
    public static void tickCerrojo(ServerPlayer p, ItemStack s) {
        int t = cerrojo(s);
        if (t <= 0) return;
        t--;
        s.getOrCreateTag().putInt(CERROJO, t);
        if (t == T_EXPULSA) expulsar(p, s);
        if (t == T_CARGA) cargar(p, s);
    }

    /** Saca lo que haya en la recámara: el casquillo vacío sale volando; un cartucho sin disparar vuelve al inventario. */
    private static void expulsar(ServerPlayer p, ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        Item vivo = gastada(s) ? null : item(recamara(s));
        boolean casquillo = gastada(s);
        t.putString(RECAMARA, "");
        t.putBoolean(GASTADA, false);
        if (vivo != null && !p.getAbilities().instabuild) {
            ItemStack devuelto = vivo == ModItems.BALA_PINTURA.get() ? Pintura.bala(t.getInt(RECAMARA_COLOR)) : new ItemStack(vivo);
            if (!p.getInventory().add(devuelto)) p.drop(devuelto, false);
        }
        if (!casquillo) return;
        ServerLevel l = p.serverLevel();
        Vec3 mira = p.getViewVector(1F);
        Vec3 derecha = new Vec3(-mira.z, 0, mira.x).normalize();
        Vec3 o = p.getEyePosition().add(mira.scale(0.45)).add(derecha.scale(0.25)).add(0, -0.25, 0);
        Vec3 v = derecha.scale(0.22 + p.getRandom().nextFloat() * 0.08).add(0, 0.22 + p.getRandom().nextFloat() * 0.06, 0).add(p.getDeltaMovement());
        if (p.getAbilities().instabuild) {
            // en creativo solo el casquillo de adorno (no se llena el suelo de objetos)
            l.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.CASQUILLO_RIFLE.get())), o.x, o.y, o.z, 0, v.x, v.y, v.z, 1.0);
        } else {
            ItemEntity e = new ItemEntity(l, o.x, o.y, o.z, new ItemStack(ModItems.CASQUILLO_RIFLE.get()), v.x, v.y, v.z);
            e.setPickUpDelay(30);
            l.addFreshEntity(e);
        }
        l.playSound(null, o.x, o.y, o.z, SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.35F, 2.0F);
    }

    private static void cargar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        ItemStack m = buscarMunicion(p);
        Item cargado = null;
        if (!m.isEmpty()) {
            cargado = m.getItem();
            if (cargado == ModItems.BALA_PINTURA.get()) s.getOrCreateTag().putInt(RECAMARA_COLOR, Pintura.color(m));
            if (!p.getAbilities().instabuild) m.shrink(1);
        } else if (p.getAbilities().instabuild) {
            cargado = ModItems.BALA_RIFLE.get();
        }
        if (cargado != null) {
            s.getOrCreateTag().putString(RECAMARA, ForgeRegistries.ITEMS.getKey(cargado).toString());
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.9F, 1.5F);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.45F, 2.0F);
    }

    // ------------------------------------------------------------------ disparo

    /**
     * Clic izquierdo con el rifle. firme = el cliente lleva 2 s con la mira quieta (Respiración contenida); la
     * dirección es la que tenía el jugador al hacer clic.
     */
    public static void disparar(ServerPlayer p, ItemStack s, float yRot, float xRot, boolean firme) {
        ServerLevel l = p.serverLevel();
        if (!lista(s)) {
            if (cerrojo(s) == 0) accionarCerrojo(p, s);   // sin bala: el clic acciona el cerrojo
            return;
        }
        Item municion = item(recamara(s));
        CompoundTag t = s.getOrCreateTag();
        int ox = oxido(s);
        // el cobre oxidado falla a veces: el cartucho no sale y hay que sacarlo con el cerrojo
        if (p.getRandom().nextFloat() < com.miscelanea.armasfuego.comun.Cobre.atasco(ox) * 1.4F + com.miscelanea.armasfuego.comun.EquilibrioArmas.encasquilladoBase()) {
            t.putBoolean(GASTADA, true);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.4F);
            return;
        }
        t.putBoolean(GASTADA, true);
        int tipo = municion == null ? BALA : tipo(municion);
        boolean apunta = p.isUsingItem() && p.getUseItem() == s;
        int resp = nivel(s, ModEnchantments.RESPIRACION.get());
        boolean contenida = apunta && firme && resp > 0;
        // dispersión (grados): a ojo mucha; con la mira poca; quieta nada. El óxido la sube.
        double disp = (apunta ? (firme ? 0.0 : 0.35) : 4.0) + ox * 0.4;
        float yr = yRot + (float) (p.getRandom().nextGaussian() * disp * 0.5);
        float xr = xRot + (float) (p.getRandom().nextGaussian() * disp * 0.5);
        Vec3 dir = Vec3.directionFromRotation(xr, yr);
        Vec3 ojo = p.getEyePosition();

        BalaRifle b = new BalaRifle(ModEntities.BALA_RIFLE.get(), l);
        b.setOwner(p);
        b.setPos(ojo.x, ojo.y - 0.08, ojo.z);
        // el cobre: el cañón gastado pega más
        float dano = com.miscelanea.armasfuego.comun.EquilibrioArmas.rifleDano() * com.miscelanea.armasfuego.comun.Cobre.dano(ox);
        // respiración contenida: x1,1 + 0,05 por nivel (antes 1,15 + 0,1: con la cabeza mataba de un tiro)
        if (contenida) dano *= 1.1F + 0.05F * resp;
        int atravesar = nivel(s, ModEnchantments.ATRAVESAR.get());
        int tirolinero = nivel(s, ModEnchantments.TIROLINERO.get());
        boolean silencio = nivel(s, ModEnchantments.SILENCIADOR.get()) > 0;
        // la cuerda se ata a los pies del tirador, por encima de su cabeza (se agarra saltando)
        Vec3 inicio = new Vec3(p.getX(), p.getY() + 2.9, p.getZ());
        b.preparar(tipo, dano, atravesar > 0 ? atravesar + 1 : 0, atravesar > 0, contenida, tirolinero, silencio, inicio,
                p.onGround() ? p.getOnPos() : null);
        if (tipo == PINTURA) b.ponerColor(t.getInt(RECAMARA_COLOR));
        b.setDeltaMovement(dir.scale(VELOCIDAD));
        b.setYRot(yr);
        b.setXRot(xr);
        l.addFreshEntity(b);

        // ruido: se oye a lo lejos y lo notan el sculk y el Warden (salvo con Silenciador)
        if (silencio) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.35F, 1.7F);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.6F, 0.6F);
        } else {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), com.miscelanea.registry.ModSounds.RIFLE_CATALEJO_DISPARO.get(), SoundSource.PLAYERS, 4.0F, 0.95F + p.getRandom().nextFloat() * 0.1F);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 4.0F, 0.6F);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
            l.gameEvent(p, GameEvent.PROJECTILE_SHOOT, p.position());
            Vec3 boca = ojo.add(dir.scale(1.2)).add(0, -0.15, 0);
            l.sendParticles(ParticleTypes.SMOKE, boca.x, boca.y, boca.z, 4, 0.04, 0.04, 0.04, 0.01);
            Vec3 fg = ojo.add(dir.scale(1.6)).add(0, -0.15, 0);
            l.sendParticles(com.miscelanea.armasfuego.comun.ArmasFuegoRegistro.FOGONAZO.get(), fg.x, fg.y, fg.z, 1, 0, 0, 0, 0);
        }
        t.putInt("Disparos", t.getInt("Disparos") + 1);
        // el cobre se oxida poco a poco con el uso (salvo encerado)
        // con cera se gasta un disparo de cera (y avisa cuando se acaba)
        if (encerado(s)) com.miscelanea.armasfuego.comun.Cobre.avisar(p, s, com.miscelanea.armasfuego.comun.Cobre.gastar(s, 30, false));
        else if (ox < 3 && p.getRandom().nextInt(30) == 0) t.putInt(OXIDO, ox + 1);
        s.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        APUNTE.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ mientras se apunta

    private static final class Apunte {
        int objetivo = -1, ticks;
    }

    private static final Map<UUID, Apunte> APUNTE = new HashMap<>();

    /** Cada tick con la mira puesta (servidor): Ojo de halcón marca lo que se mira un rato. */
    public static void apuntando(ServerPlayer p, ItemStack s) {
        int halcon = nivel(s, ModEnchantments.OJO_HALCON.get());
        if (halcon <= 0 || p.tickCount % 5 != 0) return;
        Apunte a = APUNTE.computeIfAbsent(p.getUUID(), k -> new Apunte());
        Entity e = mirado(p, 160);
        if (e == null || e.getId() != a.objetivo) {
            a.objetivo = e == null ? -1 : e.getId();
            a.ticks = 0;
            return;
        }
        a.ticks += 5;
        if (a.ticks >= 20 && e instanceof LivingEntity le) {
            MobEffectInstance ef = le.getEffect(MobEffects.GLOWING);
            if (ef == null || ef.getDuration() < 20) {
                // el brillo lo ven todos los jugadores (y con equipos, del color del equipo)
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100 + 100 * halcon, 0, false, false), p);
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.6F);
            }
        }
    }

    public static void dejarDeApuntar(Player p) {
        APUNTE.remove(p.getUUID());
    }

    /** La entidad que hay en el centro de la mira (sin atravesar bloques), o null. */
    public static Entity mirado(Player p, double alcance) {
        Vec3 ojo = p.getEyePosition();
        Vec3 dir = p.getViewVector(1F);
        Vec3 fin = ojo.add(dir.scale(alcance));
        HitResult bh = p.level().clip(new ClipContext(ojo, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) fin = bh.getLocation();
        AABB caja = p.getBoundingBox().expandTowards(dir.scale(alcance)).inflate(1);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, ojo, fin, caja, e -> !e.isSpectator() && e.isPickable(), ojo.distanceToSqr(fin));
        return eh == null ? null : eh.getEntity();
    }
}
