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
 * Lo que hay dentro de la incubadora: una muestra (o un queso) que crece durante {@link #TICKS} ticks y se convierte en
 * un suero sin etiquetar (el mismo que saldría de mezclarla consigo misma). Clic derecho con la muestra para meterla;
 * con la mano vacía se recoge el suero (o la muestra, si aún no se ha hecho).
 */
public class IncubadoraBlockEntity extends BlockEntity {
    public static final int TICKS = 1200;

    private ItemStack dentro = ItemStack.EMPTY, resultado = ItemStack.EMPTY;
    private int progreso;

    public IncubadoraBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.INCUBADORA_BE.get(), p, s);
    }

    public ItemStack resultado() { return resultado; }
    public boolean trabajando() { return !dentro.isEmpty(); }

    /** Mete una muestra (no sirven los sueros: esos ya están hechos). */
    public boolean poner(ItemStack s) {
        if (!dentro.isEmpty() || !resultado.isEmpty() || !Sueros.ingrediente(s) || s.is(LabRegistro.SUERO.get())) return false;
        dentro = s.copy();
        dentro.setCount(1);
        progreso = 0;
        setChanged();
        return true;
    }

    public static void tick(Level l, BlockPos p, BlockState s, IncubadoraBlockEntity be) {
        if (!(l instanceof ServerLevel sl) || be.dentro.isEmpty()) return;
        be.progreso++;
        if (be.progreso % 20 == 0) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.0);
        if (be.progreso >= TICKS) {
            if (be.dentro.is(LabRegistro.QUESO.get())) be.resultado = new ItemStack(LabRegistro.SUERO_RECUP.get());
            else if (be.dentro.is(com.miscelanea.glitch.GlitchRegistro.MUESTRA.get())) be.resultado = Sueros.crear(9, false);   // suero de glitch
            else be.resultado = Sueros.mezclar(be.dentro, be.dentro);
            be.dentro = ItemStack.EMPTY;
            be.progreso = 0;
            be.setChanged();
            sl.playSound(null, p, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
    }

    public void alUsar(Player p) {
        ItemStack mano = p.getMainHandItem();
        if (!mano.isEmpty()) {
            if (poner(mano)) {
                if (!p.getAbilities().instabuild) mano.shrink(1);
            } else {
                p.displayClientMessage(Component.translatable(resultado.isEmpty() && dentro.isEmpty() ? "laboratorio.miscelanea.incubadora.no_sirve" : "laboratorio.miscelanea.incubadora.ocupada").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        ItemStack saca = !resultado.isEmpty() ? resultado : dentro;
        if (saca.isEmpty()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.incubadora.vacia").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (saca == resultado) resultado = ItemStack.EMPTY;
        else dentro = ItemStack.EMPTY;
        progreso = 0;
        if (!p.getInventory().add(saca)) p.drop(saca, false);
        setChanged();
    }

    /** Saca lo que haya hecho (o la muestra, si aún no): para las pruebas y para recoger sin pasar por la mano. */
    public ItemStack recoger() {
        ItemStack s = !resultado.isEmpty() ? resultado : dentro;
        resultado = dentro = ItemStack.EMPTY;
        progreso = 0;
        setChanged();
        return s;
    }

    public void soltarTodo() {
        if (level == null || level.isClientSide) return;
        for (ItemStack s : new ItemStack[]{dentro, resultado})
            if (!s.isEmpty()) level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, s));
        dentro = resultado = ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.put("Dentro", dentro.save(new CompoundTag()));
        t.put("Resultado", resultado.save(new CompoundTag()));
        t.putInt("Progreso", progreso);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        dentro = ItemStack.of(t.getCompound("Dentro"));
        resultado = ItemStack.of(t.getCompound("Resultado"));
        progreso = t.getInt("Progreso");
    }
}
