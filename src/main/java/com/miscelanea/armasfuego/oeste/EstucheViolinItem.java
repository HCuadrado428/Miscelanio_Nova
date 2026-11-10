package com.miscelanea.armasfuego.oeste;

import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Estuche de violín: guarda una Thompson (y nada más), y además es un violín de verdad. Clic derecho toca una nota,
 * como un bloque musical: la altura va con la vista (mirando arriba, aguda; abajo, grave; dos octavas). Con la
 * Thompson dentro suena desafinado (y es que no es un violín). Agachado + clic derecho lo abre y la Thompson pasa a la
 * mano (el estuche vacío, al inventario); con la Thompson en la otra mano, clic derecho la guarda. En el inventario,
 * como un saco: suelta la Thompson encima para guardarla, clic derecho con la mano vacía para sacarla. Al abrirlo
 * suena una nota desafinada: nadie ha tocado ese violín en su vida.
 */
public class EstucheViolinItem extends Item {
    public static final String ARMA = "Arma";
    /** NBT: las notas distintas de la tirada de ahora (máscara de 25 bits) y cuándo sonó la última. */
    public static final String NOTAS = "Notas", NOTA_T = "NotaT";
    /** Para la prueba: notas que se han tocado y la última. */
    public static volatile int tocadas, ultimaNota = -1;

    public EstucheViolinItem(Properties p) {
        super(p);
    }

    public static ItemStack dentro(ItemStack s) {
        return s.hasTag() && s.getTag().contains(ARMA) ? ItemStack.of(s.getTag().getCompound(ARMA)) : ItemStack.EMPTY;
    }

    public static void guardar(ItemStack estuche, ItemStack arma) {
        if (arma.isEmpty()) estuche.removeTagKey(ARMA);
        else estuche.getOrCreateTag().put(ARMA, arma.save(new net.minecraft.nbt.CompoundTag()));
    }

    /** Un estuche con una Thompson dentro (para el botín). */
    public static ItemStack con(ItemStack arma) {
        ItemStack e = new ItemStack(com.miscelanea.registry.ModItems.ESTUCHE_VIOLIN.get());
        guardar(e, arma);
        return e;
    }

    private static void nota(Player p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VIOLIN_DESAFINADO.get(), SoundSource.PLAYERS, 0.9F,
                0.85F + p.getRandom().nextFloat() * 0.3F);
        if (p instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "thompson_violin");
    }

    /** La nota (0..24, como el bloque musical) según la vista: mirando arriba del todo, la más aguda. */
    public static int nota(float xRot) {
        return Math.round(net.minecraft.util.Mth.clamp((90F - xRot) / 180F, 0F, 1F) * 24F);
    }

    /** Toca una nota (desafinada si la Thompson va dentro) con su notita de colores encima, como el bloque musical. */
    private static void tocar(Player p, ItemStack s) {
        int n = nota(p.getXRot());
        boolean desafinado = !dentro(s).isEmpty();
        float tono = (float) Math.pow(2.0, (n - 12) / 12.0);
        if (desafinado) tono *= (float) Math.pow(2.0, (p.getRandom().nextBoolean() ? 1 : -1) * (0.35 + p.getRandom().nextFloat() * 0.4) / 12.0);
        var l = (net.minecraft.server.level.ServerLevel) p.level();
        l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), desafinado ? ModSounds.VIOLIN_DESAFINADO.get() : ModSounds.VIOLIN_NOTA.get(), SoundSource.RECORDS,
                1.0F, tono);
        if (desafinado) l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.VIOLIN_NOTA.get(), SoundSource.RECORDS, 0.5F, tono * 1.03F);
        // la notita de colores (el color, el de la nota, como el bloque musical): una encima de la cabeza (la ven los
        // demás) y otra delante, junto al estuche (la ve también el que toca, en primera persona)
        var m = p.getViewVector(1F);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, p.getX(), p.getEyeY() + 0.7, p.getZ(), 0, n / 24.0, 0, 0, 1);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, p.getX() + m.x * 0.9, p.getEyeY() + m.y * 0.9 + 0.25, p.getZ() + m.z * 0.9, 0, n / 24.0, 0, 0, 1);
        l.gameEvent(p, net.minecraft.world.level.gameevent.GameEvent.NOTE_BLOCK_PLAY, p.position());
        tocadas++;
        ultimaNota = n;
        // Paganini del Oeste: ocho notas distintas seguidas (sin parar más de 3 s) y afinado
        var t = s.getOrCreateTag();
        long ahora = l.getGameTime();
        int mask = ahora - t.getLong(NOTA_T) > 60 ? 0 : t.getInt(NOTAS);
        mask |= 1 << n;
        t.putInt(NOTAS, mask);
        t.putLong(NOTA_T, ahora);
        if (p instanceof ServerPlayer sp) {
            if (!desafinado && Integer.bitCount(mask) >= 8) LogrosMiscelanea.dar(sp, "violin_paganini");
            if (desafinado) LogrosMiscelanea.dar(sp, "thompson_violin");
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        ItemStack arma = dentro(s);
        InteractionHand otra0 = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean guardar = arma.isEmpty() && p.getItemInHand(otra0).getItem() instanceof ThompsonItem;
        if (!guardar && !(p.isShiftKeyDown() && !arma.isEmpty())) {
            // el violín
            if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(s);
            if (!level.isClientSide) tocar(p, s);
            p.getCooldowns().addCooldown(this, com.miscelanea.armasfuego.comun.EquilibrioArmas.violinEnfriamiento());
            p.swing(hand);
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        if (!arma.isEmpty()) {
            if (!level.isClientSide) {
                nota(p);
                ItemStack vacio = s.copy();
                guardar(vacio, ItemStack.EMPTY);
                p.setItemInHand(hand, arma);
                if (!p.getInventory().add(vacio)) p.drop(vacio, false);
            }
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
        }
        InteractionHand otra = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack o = p.getItemInHand(otra);
        if (o.getItem() instanceof ThompsonItem) {
            if (!level.isClientSide) {
                nota(p);
                guardar(s, o.copy());
                p.setItemInHand(otra, ItemStack.EMPTY);
            }
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        return InteractionResultHolder.pass(s);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, Slot slot, ClickAction a, Player p, SlotAccess cursor) {
        if (a != ClickAction.SECONDARY || !slot.allowModification(p)) return false;
        ItemStack arma = dentro(s);
        if (otro.isEmpty() && !arma.isEmpty()) {
            cursor.set(arma);
            guardar(s, ItemStack.EMPTY);
            if (!p.level().isClientSide) nota(p);
            return true;
        }
        if (otro.getItem() instanceof ThompsonItem && arma.isEmpty()) {
            guardar(s, otro.copy());
            cursor.set(ItemStack.EMPTY);
            if (!p.level().isClientSide) nota(p);
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack s, Slot slot, ClickAction a, Player p) {
        if (a != ClickAction.SECONDARY) return false;
        ItemStack otro = slot.getItem();
        if (otro.getItem() instanceof ThompsonItem && dentro(s).isEmpty()) {
            guardar(s, otro.copy());
            slot.set(ItemStack.EMPTY);
            if (!p.level().isClientSide) nota(p);
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        ItemStack arma = dentro(s);
        l.add(arma.isEmpty() ? Component.translatable("item.miscelanea.estuche_violin.vacio").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.miscelanea.estuche_violin.lleva").withStyle(ChatFormatting.GOLD));
        l.add(Component.translatable("item.miscelanea.estuche_violin.violin").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.estuche_violin.ayuda").withStyle(ChatFormatting.DARK_GRAY));
    }
}
