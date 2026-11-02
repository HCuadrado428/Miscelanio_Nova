package com.miscelanea.tortura.efectos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Detonador enlazado a un collar (lo crea el collar al ponerlo). Quien lo lleva es la "radio" del collar. Mantener clic
 * derecho {@link #CARGA} ticks: lo hace estallar (con confirmación: hay que aguantar). Shift + clic derecho: lo abre a
 * distancia (el collar vuelve a tu inventario y el detonador se gasta). NBT: Collar (UUID), Victima (UUID), Nombre.
 */
public class DetonadorItem extends ObjetoTortura {
    public static final int CARGA = 40;

    public DetonadorItem(Properties p) {
        super(p, 2);
    }

    @Nullable
    public static UUID collar(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.hasUUID("Collar") ? t.getUUID("Collar") : null;
    }

    @Nullable
    public static UUID victima(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.hasUUID("Victima") ? t.getUUID("Victima") : null;
    }

    /** ¿Lleva este jugador un detonador de ese collar (en cualquier hueco del inventario)? */
    public static boolean lleva(Player p, UUID collar) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof DetonadorItem && collar.equals(collar(s))) return true;
        }
        return false;
    }

    /** La víctima de este detonador, si sigue con ESE collar puesto (servidor). */
    @Nullable
    static LivingEntity objetivo(ServerPlayer p, ItemStack st) {
        UUID c = collar(st), v = victima(st);
        if (c == null || v == null || p.getServer() == null) return null;
        LivingEntity e = Collares.buscar(p.getServer(), v);
        return e != null && e.isAlive() && c.equals(Collares.id(e)) ? e : null;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (collar(st) == null) return InteractionResultHolder.pass(st);
        if (p.isShiftKeyDown()) {
            if (p instanceof ServerPlayer sp) {
                LivingEntity v = objetivo(sp, st);
                if (v == null) {
                    sp.displayClientMessage(Component.translatable("msg.miscelanea.detonador.sin_senal").withStyle(ChatFormatting.GRAY), true);
                } else {
                    Collares.quitar(v, sp);
                    sp.displayClientMessage(Component.translatable("msg.miscelanea.detonador.abierto", v.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
                    if (!sp.getAbilities().instabuild) st.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        p.startUsingItem(mano);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack st, int quedan) {
        if (level.isClientSide || !(e instanceof ServerPlayer p)) return;
        int t = getUseDuration(st) - quedan;
        if (t == 1) {
            if (objetivo(p, st) == null) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.detonador.sin_senal").withStyle(ChatFormatting.GRAY), true);
                p.stopUsingItem();
                return;
            }
        }
        if (t % 8 == 0)
            level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), EfectosRegistro.DETONADOR_CARGA.get(), SoundSource.PLAYERS, 0.7F, 0.8F + t / (float) CARGA);
        if (t >= CARGA) {
            LivingEntity v = objetivo(p, st);
            p.stopUsingItem();
            if (v == null) return;
            Collares.detonar(v, p);
            p.displayClientMessage(Component.translatable("msg.miscelanea.detonador.detonado", v.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        CompoundTag t = s.getTag();
        if (t != null && t.contains("Nombre"))
            l.add(Component.translatable("item.miscelanea.detonador.enlazado", t.getString("Nombre")).withStyle(ChatFormatting.RED));
        else l.add(Component.translatable("item.miscelanea.detonador.suelto").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(s, level, l, f);
    }
}
