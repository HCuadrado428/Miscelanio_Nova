package com.miscelanea.glitch;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Muestra de glitch (solo de creativo): un frasco de poción normal y corriente con un trozo de «el glitch» dentro (el
 * líquido cambia de color a saltos; lo pinta {@code GlitchClienteMod}). En la mano suelta algún píxel y chisporrotea.
 * <p>
 * Si se suelta (Q, sacarla del inventario, morir con ella...) se vuelve inestable: tiembla, parpadea y sisea cada vez
 * más durante {@link #MECHA_TICKS} ticks, como un creeper, y luego se rompe y empieza una {@link OndaGlitch}. Si el
 * objeto tirado se destruye (lava, fuego, cactus, explosión) se rompe al momento. Con clic derecho se lanza como una
 * poción arrojadiza y se rompe donde choque.
 */
public class MuestraGlitchItem extends Item {
    /** Ticks de mecha desde que se suelta hasta que se rompe (1,5 s). */
    public static final int MECHA_TICKS = 30;
    /** En la muestra tirada: tick del mundo en el que empezó la mecha (el cliente la hace parpadear con eso). */
    public static final String TAG_INESTABLE = "inestable";
    /** Dónde se recogió con el frasco de muestras (solo para la descripción). */
    public static final String TAG_RECOGIDA = "recogida";
    /** Datos persistentes del objeto tirado. */
    private static final String MECHA = "miscelanea_glitch_mecha", ROTA = "miscelanea_glitch_rota",
            TEMBLOR_X = "miscelanea_glitch_tx", TEMBLOR_Z = "miscelanea_glitch_tz";

    public MuestraGlitchItem(Properties p) {
        super(p);
    }

    /** Una muestra recién recogida en {@code donde}. */
    public static ItemStack recogida(String donde) {
        ItemStack st = new ItemStack(GlitchRegistro.MUESTRA.get());
        st.getOrCreateTag().putString(TAG_RECOGIDA, donde);
        return st;
    }

    @Override
    public boolean isFoil(ItemStack st) {
        return true;
    }

    // ------------------------------------------------------------------ lanzarla
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            MuestraLanzadaEntity e = new MuestraLanzadaEntity(level, p);
            e.setItem(new ItemStack(GlitchRegistro.MUESTRA_LANZADA_ITEM.get()));
            e.shootFromRotation(p, p.getXRot(), p.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(e);
        }
        p.awardStat(Stats.ITEM_USED.get(this));
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }

    // ------------------------------------------------------------------ en la mano
    @Override
    public void inventoryTick(ItemStack st, Level level, Entity e, int slot, boolean seleccionado) {
        if (!level.isClientSide || !(e instanceof LivingEntity le)) return;
        boolean izquierda = le.getOffhandItem() == st;
        if (!seleccionado && !izquierda) return;
        var r = le.getRandom();
        if (r.nextInt(7) == 0) {
            // más o menos donde está la mano (la derecha o la izquierda según el brazo bueno)
            boolean derecha = (le.getMainArm() == HumanoidArm.RIGHT) != izquierda;
            Vec3 mira = le.getViewVector(1.0F);
            Vec3 lado = mira.cross(new Vec3(0, 1, 0)).normalize().scale(derecha ? 0.38 : -0.38);
            Vec3 m = le.getEyePosition().add(mira.scale(0.55)).add(lado).add(0, -0.4, 0);
            level.addParticle(GlitchRegistro.PARTICULA.get(), m.x + (r.nextDouble() - 0.5) * 0.25, m.y + (r.nextDouble() - 0.5) * 0.25,
                    m.z + (r.nextDouble() - 0.5) * 0.25, 0, 0, 0);
        }
        if (r.nextInt(260) == 0)
            level.playLocalSound(le.getX(), le.getY() + 1.0, le.getZ(), GlitchRegistro.ESTATICA.get(), SoundSource.PLAYERS,
                    0.12F, 0.8F + r.nextFloat() * 0.6F, false);
    }

    // ------------------------------------------------------------------ tirada en el suelo
    @Override
    public boolean onEntityItemUpdate(ItemStack st, ItemEntity ent) {
        Level level = ent.level();
        CompoundTag d = ent.getPersistentData();
        if (d.getBoolean(OndaGlitch.SIN_MECHA)) return false;
        int m = d.getInt(MECHA) + 1;
        d.putInt(MECHA, m);
        float k = Math.min(1.0F, m / (float) MECHA_TICKS);
        var r = level.random;
        if (level instanceof ServerLevel sl) {
            if (m == 1) {
                ent.setNeverPickUp();
                ItemStack c = st.copy();
                c.getOrCreateTag().putLong(TAG_INESTABLE, level.getGameTime());
                ent.setItem(c);
                level.playSound(null, ent.getX(), ent.getY(), ent.getZ(), GlitchRegistro.SISEO.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            if (m % 5 == 0)
                level.playSound(null, ent.getX(), ent.getY(), ent.getZ(), GlitchRegistro.ESTATICA.get(), SoundSource.NEUTRAL,
                        0.25F + 0.6F * k, 0.7F + 0.9F * k);
            if (m % 2 == 0)
                sl.sendParticles(GlitchRegistro.PARTICULA.get(), ent.getX(), ent.getY() + 0.25, ent.getZ(), 1 + (int) (k * 3), 0.15 + 0.3 * k, 0.15 + 0.2 * k, 0.15 + 0.3 * k, 0.0);
            if (m >= MECHA_TICKS) {
                romper(sl, ent);
                return true;
            }
        } else {
            // tiembla cada vez más (solo en el cliente: el servidor no se entera y no se mueve de verdad)
            double jx = (r.nextDouble() - 0.5) * 0.14 * k, jz = (r.nextDouble() - 0.5) * 0.14 * k;
            ent.setPos(ent.getX() - d.getDouble(TEMBLOR_X) + jx, ent.getY(), ent.getZ() - d.getDouble(TEMBLOR_Z) + jz);
            d.putDouble(TEMBLOR_X, jx);
            d.putDouble(TEMBLOR_Z, jz);
        }
        return false;
    }

    /** Se ha quemado, pinchado o volado: se rompe al momento. */
    @Override
    public void onDestroyed(ItemEntity ent) {
        if (ent.level() instanceof ServerLevel sl && !ent.getPersistentData().getBoolean(OndaGlitch.SIN_MECHA)) romper(sl, ent);
    }

    private static void romper(ServerLevel sl, ItemEntity ent) {
        CompoundTag d = ent.getPersistentData();
        if (d.getBoolean(ROTA)) return;
        d.putBoolean(ROTA, true);
        UUID dueno = d.hasUUID(OndaGlitch.DUENO) ? d.getUUID(OndaGlitch.DUENO) : null;
        sl.levelEvent(2002, ent.blockPosition(), GlitchRegistro.COLOR); // cristal roto y salpicadura, como una poción
        OndaGlitch.detonar(sl, ent.position().add(0, 0.25, 0), dueno);
        ent.discard();
    }

    // ------------------------------------------------------------------ descripción
    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.muestra_glitch.lore1").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.muestra_glitch.lore2").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.muestra_glitch.aviso").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("item.miscelanea.muestra_glitch.uso").withStyle(ChatFormatting.GRAY));
        CompoundTag t = st.getTag();
        if (t != null && t.contains(TAG_RECOGIDA))
            tip.add(Component.translatable("item.miscelanea.muestra_glitch.recogida", t.getString(TAG_RECOGIDA)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
