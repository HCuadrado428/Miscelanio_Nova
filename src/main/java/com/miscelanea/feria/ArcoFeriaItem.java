package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Arco de feria: lo presta la caseta de tiro con arco. Mantén el clic derecho para tensarlo y suéltalo para disparar.
 * No dispara flechas de verdad: el servidor comprueba la puntería igual que con la escopeta de corcho.
 */
public class ArcoFeriaItem extends Item {
    private static final String PRESTADO = "MiscelaneaPrestada";
    /** Tics mínimos de tensado para que el tiro valga (un cuarto de segundo, como el arco normal). */
    private static final int MINIMO = 5;

    public ArcoFeriaItem(Properties p) {
        super(p);
    }

    /** El arco que presta la caseta (no se puede tirar y desaparece al acabar la partida). */
    public static ItemStack prestada() {
        ItemStack s = new ItemStack(com.miscelanea.registry.ModItems.ARCO_FERIA.get());
        s.getOrCreateTag().putBoolean(PRESTADO, true);
        return s;
    }

    public static boolean esPrestado(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(PRESTADO);
    }

    /** Recoge los arcos prestados del inventario (al acabar la partida). */
    public static void devolver(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (esPrestado(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        if (esPrestado(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    }

    @Override
    public void inventoryTick(ItemStack st, Level l, net.minecraft.world.entity.Entity e, int slot, boolean enMano) {
        // por si la partida se cortó (te fuiste, se rompió la caseta, saliste del servidor): sin partida, fuera
        if (l.isClientSide || !esPrestado(st) || !(e instanceof Player p) || l.getGameTime() % 20 != 0) return;
        if (CasetaTiroBlockEntity.buscar(l, p) == null) st.setCount(0);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack st, Player p) {
        return !esPrestado(st);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack st, Level l, LivingEntity e, int quedan) {
        if (l.isClientSide || !(e instanceof Player p)) return;
        if (getUseDuration(st) - quedan < MINIMO) return;
        p.getCooldowns().addCooldown(this, 6);
        l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.1F + l.random.nextFloat() * 0.2F);
        CasetaTiroBlockEntity be = CasetaTiroBlockEntity.buscar(l, p);
        Vec3 ojo = p.getEyePosition(), dir = p.getLookAngle();
        if (be != null && be.disparo(p, ojo, dir)) l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.FE_DIANA.get(), SoundSource.PLAYERS, 0.4F, 1.3F);
    }
}
