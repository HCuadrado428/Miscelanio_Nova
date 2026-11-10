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
 * Estuche de violín: guarda una Thompson (y nada más), y además es un violín de verdad. Con un arco en la otra mano,
 * mantener clic derecho frota: la nota suena sostenida mientras se mantiene y cambia al mirar arriba o abajo (dos
 * octavas, 25 notas como el bloque musical); el HUD enseña la nota que va a sonar, para afinar. Con la Thompson dentro
 * suena desafinado (y es que no es un violín). Sin arco, clic derecho abre el estuche (la Thompson pasa a la mano) o lo
 * cierra con ella dentro (con la Thompson en la otra mano); agachado + clic derecho saca la Thompson. En el inventario,
 * como un saco: suelta la Thompson encima para guardarla, clic derecho con la mano vacía para sacarla.
 */
public class EstucheViolinItem extends Item {
    public static final String ARMA = "Arma";
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

    /** Los nombres de las 25 notas (de Fa♯3 a Fa♯5, como el bloque musical). */
    public static String nombreNota(int n) {
        String[] k = {"F♯", "G", "G♯", "A", "A♯", "B", "C", "C♯", "D", "D♯", "E", "F"};
        return k[Math.floorMod(n, 12)] + (3 + (n + 6) / 12);
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
        // demás) y otra delante, un poco por debajo de los ojos (sube al salir: así la ve también el que toca, en primera
        // persona, en el centro de la vista)
        var m = p.getViewVector(1F);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, p.getX(), p.getEyeY() + 0.7, p.getZ(), 0, n / 24.0, 0, 0, 1);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, p.getX() + m.x * 1.4, p.getEyeY() + m.y * 1.4 - 0.25, p.getZ() + m.z * 1.4, 0, n / 24.0, 0, 0, 1);
        l.gameEvent(p, net.minecraft.world.level.gameevent.GameEvent.NOTE_BLOCK_PLAY, p.position());
        tocadas++;
        ultimaNota = n;
        // Paganini del Oeste: ocho notas distintas seguidas con el arco (sin parar más de 3 s) y afinado
        long ahora = l.getGameTime();
        long[] e = ARCO.computeIfAbsent(p.getUUID(), k -> new long[4]);
        int mask = ahora - e[1] > 60 ? 0 : (int) e[2];
        mask |= 1 << n;
        e[0] = n;
        e[1] = ahora;
        e[2] = mask;
        if (p instanceof ServerPlayer sp) {
            if (!desafinado && Integer.bitCount(mask) >= 8) LogrosMiscelanea.dar(sp, "violin_paganini");
            if (desafinado) LogrosMiscelanea.dar(sp, "thompson_violin");
        }
    }

    /** ¿Lleva un arco en la otra mano? (con él, el estuche vacío se toca como un violín) */
    public static boolean conArco(Player p, InteractionHand hand) {
        InteractionHand otra = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return p.getItemInHand(otra).is(net.minecraft.world.item.Items.BOW);
    }

    /** Lo que se está tocando con el arco (la última nota y cuándo sonó), por jugador; no va en el NBT para no cortar el uso. */
    private static final java.util.Map<java.util.UUID, long[]> ARCO = new java.util.HashMap<>();

    public static void olvidar(java.util.UUID id) {
        ARCO.remove(id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        ItemStack arma = dentro(s);
        // con el arco en la otra mano: se frota mientras se mantiene el clic (agachado, con la Thompson dentro, se saca)
        if (conArco(p, hand) && !(p.isShiftKeyDown() && !arma.isEmpty())) {
            p.startUsingItem(hand);
            if (!level.isClientSide) tocar(p, s);
            return InteractionResultHolder.consume(s);
        }
        // sin arco: clic derecho abre el estuche (y la Thompson pasa a la mano) o lo cierra con ella dentro
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
        // vacío y sin nada que guardar: se abre y se cierra (clac)
        if (!level.isClientSide)
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.7F, 1.4F);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    /** Frotando: la nota suena sostenida (se repite antes de apagarse) y cambia en cuanto se mira más arriba o abajo. */
    @Override
    public void onUseTick(Level level, net.minecraft.world.entity.LivingEntity e, ItemStack s, int quedan) {
        if (level.isClientSide || !(e instanceof Player p)) return;
        if (!conArco(p, p.getUsedItemHand())) {
            p.stopUsingItem();
            return;
        }
        long[] t = ARCO.get(p.getUUID());
        long ahora = level.getGameTime();
        int n = nota(p.getXRot());
        if (t == null || t[0] != n || ahora - t[1] >= 14) tocar(p, s);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack s) {
        return net.minecraft.world.item.UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
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
