package com.miscelanea.armas;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lógica de la ballesta de repetición: cargador de 6 virotes guardado en el NBT (lista de flechas, así las
 * flechas con efecto conservan su efecto), disparo y recarga con palanca.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Ballesta {
    public static final String VIROTES = "Virotes", DISPARO = "Disparo", TIROS = "Tiros", ULTIMO = "UltimoT",
            RECARGA = "Recarga", RECARGA_K = "RecargaK";
    public static final int MAX = 6;
    /** Ticks entre virotes al mantener el clic (6 = 3,3 por segundo). */
    public static final int CADENCIA = 6;
    /** Duración de la animación de recarga (1,5 s). */
    public static final int T_RECARGA = 30;
    /** Etiqueta de entidad de los virotes (para saltarse la invulnerabilidad tras el golpe anterior). */
    public static final String ETIQUETA = "miscelanea_virote";
    private static final float VELOCIDAD = 3.0F;

    private Ballesta() {}

    public static int cargados(ItemStack s) {
        return s.hasTag() ? s.getTag().getList(VIROTES, Tag.TAG_COMPOUND).size() : 0;
    }

    public static boolean recargando(ItemStack s) {
        return s.hasTag() && s.getTag().getInt(RECARGA) > 0;
    }

    /** Flechas de cualquier tipo en el inventario (mano secundaria primero, luego el resto). */
    public static int flechas(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof ArrowItem) n += s.getCount();
        }
        return n;
    }

    private static ItemStack buscarFlecha(Player p) {
        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof ArrowItem) return off;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof ArrowItem) return s;
        }
        return ItemStack.EMPTY;
    }

    public static boolean puedeRecargar(Player p, ItemStack s) {
        return !recargando(s) && cargados(s) < MAX && (p.getAbilities().instabuild || flechas(p) > 0);
    }

    private static void sonido(Player p, SoundEvent e, float vol, float tono) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), e, SoundSource.PLAYERS, vol, tono);
    }

    /** Empieza la animación de recarga (servidor). Las flechas se cogen al final, por si la cancelas. */
    public static boolean iniciarRecarga(ServerPlayer p, ItemStack s) {
        if (!puedeRecargar(p, s)) return false;
        if (p.isUsingItem()) p.stopUsingItem();
        var t = s.getOrCreateTag();
        int faltan = MAX - cargados(s);
        int k = p.getAbilities().instabuild ? faltan : Math.min(faltan, flechas(p));
        t.putInt(RECARGA, T_RECARGA);
        t.putInt(RECARGA_K, k);
        t.putInt(DISPARO, 0);
        sonido(p, ModSounds.BALLESTA_PALANCA.get(), 0.9F, 0.9F);
        return true;
    }

    /** Un tick de la recarga mientras la llevas en la mano (servidor): sonidos y, al acabar, mete los virotes. */
    public static void tickRecarga(ServerPlayer p, ItemStack s) {
        var t = s.getOrCreateTag();
        int r = t.getInt(RECARGA) - 1;
        t.putInt(RECARGA, r);
        int e = T_RECARGA - r;                 // ticks transcurridos (1..30)
        int k = Math.min(5, t.getInt(RECARGA_K));
        if (e == 6) sonido(p, ModSounds.BALLESTA_CRUJIDO.get(), 0.8F, 1.1F);              // se abre la tapa
        if (e >= 9 && e < 9 + 2 * k && (e - 9) % 2 == 0)
            sonido(p, ModSounds.BALLESTA_VIROTE.get(), 0.7F, 0.9F + p.getRandom().nextFloat() * 0.25F);
        if (e == 21) sonido(p, ModSounds.BALLESTA_PALANCA.get(), 0.8F, 0.75F);           // cierra la tapa
        if (e == 24) sonido(p, ModSounds.BALLESTA_CRUJIDO.get(), 0.7F, 1.35F);           // palanca adelante
        if (e == 27) sonido(p, ModSounds.BALLESTA_PALANCA.get(), 1.0F, 1.15F);           // y atrás: tensada
        if (r <= 0) {
            t.remove(RECARGA);
            t.remove(RECARGA_K);
            cargar(p, s);
        }
    }

    public static void cancelarRecarga(ItemStack s) {
        if (s.hasTag()) {
            s.getTag().remove(RECARGA);
            s.getTag().remove(RECARGA_K);
        }
    }

    private static void cargar(Player p, ItemStack s) {
        var t = s.getOrCreateTag();
        ListTag lista = t.getList(VIROTES, Tag.TAG_COMPOUND);
        boolean creativo = p.getAbilities().instabuild;
        while (lista.size() < MAX) {
            ItemStack f = buscarFlecha(p);
            ItemStack uno;
            if (f.isEmpty()) {
                if (!creativo) break;
                uno = new ItemStack(Items.ARROW);
            } else {
                uno = f.copy();
                uno.setCount(1);
                if (!creativo) f.shrink(1);
            }
            lista.add(uno.save(new CompoundTag()));
        }
        t.put(VIROTES, lista);
    }

    /** Dispara el primer virote del cargador (servidor). */
    public static void disparar(ServerPlayer p, ItemStack s) {
        Level level = p.level();
        var t = s.getOrCreateTag();
        ListTag lista = t.getList(VIROTES, Tag.TAG_COMPOUND);
        if (lista.isEmpty()) return;
        ItemStack municion = ItemStack.of(lista.getCompound(0));
        lista.remove(0);
        t.put(VIROTES, lista);
        long ahora = level.getGameTime();
        boolean seguido = ahora - t.getLong(ULTIMO) <= CADENCIA + 4;
        t.putLong(ULTIMO, ahora);
        t.putInt(DISPARO, CADENCIA + 1);
        t.putInt(TIROS, t.getInt(TIROS) + 1);

        ArrowItem tipo = municion.getItem() instanceof ArrowItem a ? a : (ArrowItem) Items.ARROW;
        if (municion.isEmpty()) municion = new ItemStack(Items.ARROW);
        AbstractArrow f = tipo.createArrow(level, municion, p);
        f.setShotFromCrossbow(true);
        f.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        f.pickup = p.getAbilities().instabuild ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
        // tiro tenso y recto; algo de dispersión cuando disparas a ráfagas
        f.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, VELOCIDAD, seguido ? 2.2F : 0.4F);
        f.addTag(ETIQUETA);
        level.addFreshEntity(f);
        sonido(p, ModSounds.BALLESTA_DISPARO.get(), 1.0F, 0.95F + p.getRandom().nextFloat() * 0.12F);
        p.awardStat(Stats.ITEM_USED.get(s.getItem()));
    }

    /** Los virotes van a ráfagas: que el anterior no deje al blanco invulnerable para el siguiente. */
    @SubscribeEvent
    public static void impacto(ProjectileImpactEvent e) {
        if (e.getProjectile().level().isClientSide || !e.getProjectile().getTags().contains(ETIQUETA)) return;
        if (e.getRayTraceResult() instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity v) v.invulnerableTime = 0;
    }
}
