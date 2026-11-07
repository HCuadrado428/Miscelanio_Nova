package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Escopeta de corcho de feria: clic derecho dispara un tapón (inofensivo) hacia donde miras. */
public class EscopetaCorchoItem extends Item {
    private static final String PRESTADA = "MiscelaneaPrestada";

    public EscopetaCorchoItem(Properties p) {
        super(p);
    }

    /** La escopeta que presta la caseta de tiro (no se puede tirar y desaparece al acabar la partida). */
    public static ItemStack prestada() {
        ItemStack s = new ItemStack(com.miscelanea.registry.ModItems.ESCOPETA_CORCHO.get());
        s.getOrCreateTag().putBoolean(PRESTADA, true);
        return s;
    }

    public static boolean esPrestada(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(PRESTADA);
    }

    /** Recoge las escopetas prestadas del inventario (al acabar la partida). */
    public static void devolver(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (esPrestada(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        if (esPrestada(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    }

    @Override
    public void inventoryTick(ItemStack st, Level l, net.minecraft.world.entity.Entity e, int slot, boolean enMano) {
        // por si la partida se cortó (te fuiste, se rompió la caseta, saliste del servidor): sin partida, fuera
        if (l.isClientSide || !esPrestada(st) || !(e instanceof Player p) || l.getGameTime() % 20 != 0) return;
        if (CasetaTiroBlockEntity.buscar(l, p) == null) st.setCount(0);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack st, Player p) {
        // la prestada no se puede tirar ni dar (se queda en la caseta)
        return !esPrestada(st);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        p.getCooldowns().addCooldown(this, 7);
        Vec3 ojo = p.getEyePosition(), dir = p.getLookAngle();
        if (l.isClientSide) {
            for (int i = 1; i <= 8; i++) {
                Vec3 q = ojo.add(dir.scale(i * 0.6));
                l.addParticle(ParticleTypes.SMOKE, q.x, q.y - 0.1, q.z, 0, 0, 0);
            }
        } else {
            l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.FE_CORCHO.get(), SoundSource.PLAYERS, 0.8F, 0.9F + l.random.nextFloat() * 0.2F);
            CasetaTiroBlockEntity be = CasetaTiroBlockEntity.buscar(l, p);
            if (be != null) be.disparo(p, ojo, dir);
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }
}
