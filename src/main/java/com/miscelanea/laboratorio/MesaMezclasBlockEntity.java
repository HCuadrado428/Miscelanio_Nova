package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lo que hay en la mesa de mezclas: dos ingredientes y, cuando están los dos, 5 segundos de mezcla hasta que sale el
 * suero. Clic derecho con un ingrediente lo pone; con la mano vacía recoge el suero o, si no hay, el último ingrediente.
 */
public class MesaMezclasBlockEntity extends BlockEntity {
    public static final int TICKS_MEZCLA = 100;

    private ItemStack a = ItemStack.EMPTY, b = ItemStack.EMPTY, resultado = ItemStack.EMPTY;
    private int progreso;

    public MesaMezclasBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.MESA_BE.get(), p, s);
    }

    public ItemStack resultado() { return resultado; }
    public boolean mezclando() { return !a.isEmpty() && !b.isEmpty() && resultado.isEmpty(); }

    /** Pone un ingrediente (uno). Devuelve false si no sirve o no cabe. */
    public boolean poner(ItemStack s) {
        if (!Sueros.ingrediente(s) || !resultado.isEmpty()) return false;
        ItemStack uno = s.copy();
        uno.setCount(1);
        if (a.isEmpty()) a = uno;
        else if (b.isEmpty()) b = uno;
        else return false;
        progreso = 0;
        setChanged();
        return true;
    }

    public static void tick(Level l, BlockPos p, BlockState s, MesaMezclasBlockEntity be) {
        if (!(l instanceof ServerLevel sl) || !be.mezclando()) return;
        be.progreso++;
        if (be.progreso % 8 == 0) sl.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.02);
        if (be.progreso >= TICKS_MEZCLA) {
            be.resultado = Sueros.mezclar(be.a, be.b);
            be.a = be.b = ItemStack.EMPTY;
            be.progreso = 0;
            be.setChanged();
            sl.playSound(null, p, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.2F);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.0);
        }
    }

    public void alUsar(Player p) {
        ItemStack mano = p.getMainHandItem();
        if (!mano.isEmpty()) {
            if (poner(mano)) {
                if (!p.getAbilities().instabuild) mano.shrink(1);
            } else {
                p.displayClientMessage(Component.translatable(resultado.isEmpty() ? "laboratorio.miscelanea.mesa.no_sirve" : "laboratorio.miscelanea.mesa.recoge").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        ItemStack saca = !resultado.isEmpty() ? resultado : !b.isEmpty() ? b : a;
        if (saca.isEmpty()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.mesa.vacia").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (saca == resultado) resultado = ItemStack.EMPTY;
        else if (saca == b) b = ItemStack.EMPTY;
        else a = ItemStack.EMPTY;
        progreso = 0;
        if (!p.getInventory().add(saca)) p.drop(saca, false);
        setChanged();
    }

    /** Al romper la mesa, todo lo que tiene al suelo. */
    public void soltarTodo() {
        if (level == null || level.isClientSide) return;
        for (ItemStack s : new ItemStack[]{a, b, resultado})
            if (!s.isEmpty()) level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, s));
        a = b = resultado = ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.put("A", a.save(new CompoundTag()));
        t.put("B", b.save(new CompoundTag()));
        t.put("R", resultado.save(new CompoundTag()));
        t.putInt("Progreso", progreso);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        a = ItemStack.of(t.getCompound("A"));
        b = ItemStack.of(t.getCompound("B"));
        resultado = ItemStack.of(t.getCompound("R"));
        progreso = t.getInt("Progreso");
    }
}
