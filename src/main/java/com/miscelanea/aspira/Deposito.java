package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Todo lo que se guarda en la mochila: los mobs capturados (máx. 3) y el tanque de pociones.
 */
public final class Deposito {
    public static final int CAPACIDAD = 3;
    /** Mobs que se pueden meter en el depósito (data/miscelanea/tags/entity_types/capturable.json). */
    public static final TagKey<EntityType<?>> CAPTURABLE =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "capturable"));

    private static final String CAPTURADOS = "Capturados";
    private static final String TANQUE = "Tanque";
    private static final String CARGA = "Carga";

    private Deposito() {}

    // ---------- Mochila ----------

    public static boolean llevaMochila(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.MOCHILA.get());
    }

    public static ItemStack mochila(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(ModItems.MOCHILA.get()) ? chest : ItemStack.EMPTY;
    }

    // ---------- Capturas ----------

    public static boolean esCapturable(Entity e) {
        return e instanceof LivingEntity && !(e instanceof Player) && e.getType().is(CAPTURABLE);
    }

    public static int numCapturados(ItemStack mochila) {
        return mochila.hasTag() ? mochila.getTag().getList(CAPTURADOS, Tag.TAG_COMPOUND).size() : 0;
    }

    public static boolean lleno(ItemStack mochila) {
        return numCapturados(mochila) >= CAPACIDAD;
    }

    /** Guarda el mob en la mochila y lo quita del mundo. */
    public static boolean capturar(Player player, LivingEntity mob) {
        ItemStack mochila = mochila(player);
        if (mochila.isEmpty() || lleno(mochila) || !esCapturable(mob)) return false;

        CompoundTag data = new CompoundTag();
        mob.saveWithoutId(data);
        CompoundTag entry = new CompoundTag();
        entry.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        entry.put("data", data);

        ListTag list = mochila.getOrCreateTag().getList(CAPTURADOS, Tag.TAG_COMPOUND);
        list.add(entry);
        mochila.getTag().put(CAPTURADOS, list);

        if (mob.level() instanceof ServerLevel level) {
            Vec3 c = AspiraUtil.centro(mob);
            level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.02);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 0.5F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        mob.discard();
        return true;
    }

    /** Saca el último mob capturado en la posición y con la velocidad dadas. Devuelve el mob o null. */
    @Nullable
    public static Entity soltar(Player player, Vec3 pos, Vec3 velocidad) {
        return soltar(player, numCapturados(mochila(player)) - 1, pos, velocidad);
    }

    /** Captura i-ésima (la ventana de la mochila deja elegir cuál soltar). */
    public static CompoundTag captura(ItemStack mochila, int i) {
        if (!mochila.hasTag()) return null;
        ListTag list = mochila.getTag().getList(CAPTURADOS, Tag.TAG_COMPOUND);
        return i >= 0 && i < list.size() ? list.getCompound(i) : null;
    }

    @Nullable
    public static Entity soltar(Player player, int indice, Vec3 pos, Vec3 velocidad) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        ItemStack mochila = mochila(player);
        if (mochila.isEmpty() || indice < 0 || indice >= numCapturados(mochila)) return null;

        ListTag list = mochila.getTag().getList(CAPTURADOS, Tag.TAG_COMPOUND);
        CompoundTag entry = list.getCompound(indice);
        list.remove(indice);
        mochila.getTag().put(CAPTURADOS, list);

        CompoundTag tag = entry.getCompound("data").copy();
        tag.putString("id", entry.getString("id"));
        Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
            ent.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
            return ent;
        });
        if (e == null) return null;

        e.setDeltaMovement(velocidad);
        e.fallDistance = 0.0F;
        if (!level.addFreshEntity(e)) {
            // Si el UUID sigue existiendo (raro), le damos uno nuevo
            e.setUUID(java.util.UUID.randomUUID());
            level.addFreshEntity(e);
        }
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0F, 0.6F);
        return e;
    }

    /** Nombres de lo capturado para el tooltip. */
    public static List<Component> nombresCapturados(ItemStack mochila) {
        List<Component> out = new ArrayList<>();
        if (!mochila.hasTag()) return out;
        ListTag list = mochila.getTag().getList(CAPTURADOS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(entry.getString("id")));
            Component nombre = type.getDescription();
            CompoundTag data = entry.getCompound("data");
            if (data.contains("CustomName", Tag.TAG_STRING)) {
                Component custom = Component.Serializer.fromJson(data.getString("CustomName"));
                if (custom != null) nombre = custom;
            }
            out.add(nombre);
        }
        return out;
    }

    // ---------- Tanque de pociones (modo Alquimista) ----------

    /** Poción guardada (como poción persistente) o EMPTY. */
    public static ItemStack tanque(ItemStack mochila) {
        if (!mochila.hasTag() || !mochila.getTag().contains(TANQUE)) return ItemStack.EMPTY;
        return ItemStack.of(mochila.getTag().getCompound(TANQUE));
    }

    public static void setTanque(ItemStack mochila, ItemStack pocion) {
        mochila.getOrCreateTag().put(TANQUE, pocion.save(new CompoundTag()));
    }

    /** Cantidad de poción en el tanque (0–100). */
    public static float carga(ItemStack mochila) {
        return mochila.hasTag() ? mochila.getTag().getFloat(CARGA) : 0.0F;
    }

    public static void setCarga(ItemStack mochila, float carga) {
        mochila.getOrCreateTag().putFloat(CARGA, Math.min(100.0F, carga));
    }

    private static final int MAX_EFECTOS_TANQUE = 5;

    /**
     * Mete un efecto (negativo o neutro) en el tanque para expulsarlo después.
     * Si ya estaba, se queda con la mayor duración y potencia. Devuelve false si el tanque está lleno.
     */
    public static boolean anadirAlTanque(ItemStack mochila, MobEffectInstance efecto, float carga) {
        ItemStack tanque = tanque(mochila);
        if (tanque.isEmpty()) tanque = new ItemStack(Items.LINGERING_POTION);
        List<MobEffectInstance> efectos = new ArrayList<>(PotionUtils.getCustomEffects(tanque));
        boolean encontrado = false;
        for (int i = 0; i < efectos.size(); i++) {
            MobEffectInstance e = efectos.get(i);
            if (e.getEffect() == efecto.getEffect()) {
                efectos.set(i, new MobEffectInstance(e.getEffect(), Math.max(e.getDuration(), efecto.getDuration()),
                        Math.max(e.getAmplifier(), efecto.getAmplifier())));
                encontrado = true;
                break;
            }
        }
        if (!encontrado) {
            if (efectos.size() >= MAX_EFECTOS_TANQUE) return false;
            efectos.add(new MobEffectInstance(efecto.getEffect(), efecto.getDuration(), efecto.getAmplifier()));
        }
        PotionUtils.setCustomEffects(tanque, efectos);
        tanque.getOrCreateTag().putInt("CustomPotionColor", PotionUtils.getColor(efectos));
        setTanque(mochila, tanque);
        setCarga(mochila, carga(mochila) + carga);
        return true;
    }

    public static void vaciarTanque(ItemStack mochila) {
        if (!mochila.hasTag()) return;
        mochila.getTag().remove(TANQUE);
        mochila.getTag().remove(CARGA);
    }
}
