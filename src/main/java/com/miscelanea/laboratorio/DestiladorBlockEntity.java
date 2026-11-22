package com.miscelanea.laboratorio;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModPotions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que lleva dentro el destilador térmico: una cola de cosas que se están destilando (suavis seco para el sedante, o
 * una muestra de miedo, de mareo, de frío o de calor para hacer una poción) y las pociones ya listas (entre las dos
 * cosas, como mucho {@link #MAX}). Cada una tarda {@link #TICKS_POR_UNIDAD} ticks y solo avanza si hay fuego debajo. Se
 * saca con una botella de cristal.
 */
public class DestiladorBlockEntity extends BlockEntity {
    public static final int MAX = 4;
    public static final int TICKS_POR_UNIDAD = 400;

    private final List<String> cola = new ArrayList<>();
    private final List<ItemStack> salidas = new ArrayList<>();
    private int progreso;

    public DestiladorBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.DESTILADOR_BE.get(), p, s);
    }

    /** Cuántos suavis secos quedan por destilar. */
    public int suavis() {
        int n = 0;
        for (String s : cola) if (s.equals("suavis")) n++;
        return n;
    }

    public int pendientes() { return cola.size(); }
    public int listo() { return salidas.size(); }

    /** ¿Hay una fuente de calor justo debajo? */
    public static boolean calor(Level l, BlockPos p) {
        BlockPos b = p.below();
        BlockState s = l.getBlockState(b);
        if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.MAGMA_BLOCK)) return true;
        if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) return true;
        if (s.getBlock() instanceof AbstractFurnaceBlock && s.getValue(AbstractFurnaceBlock.LIT)) return true;
        return l.getFluidState(b).is(FluidTags.LAVA);
    }

    private boolean cabe() {
        return cola.size() + salidas.size() < MAX;
    }

    /** Mete un suavis seco. Devuelve false si está lleno. */
    public boolean meter() {
        if (!cabe()) return false;
        cola.add("suavis");
        setChanged();
        return true;
    }

    /** Qué se destila de una muestra (null si ese objeto no se destila). */
    private static String tarea(ItemStack s) {
        if (s.is(LabRegistro.MUESTRA_MIEDO.get())) return "miedo";
        if (s.is(LabRegistro.MUESTRA_MAREO.get())) return "mareo";
        if (s.is(LabRegistro.MUESTRA_FRIO.get())) return "frio";
        if (s.is(LabRegistro.MUESTRA_CALOR.get())) return "calor";
        return null;
    }

    /** Mete una muestra para hacer una poción. Devuelve false si no es una muestra o está lleno. */
    public boolean meterMuestra(ItemStack s) {
        String t = tarea(s);
        if (t == null || !cabe()) return false;
        cola.add(t);
        setChanged();
        return true;
    }

    private static Potion pocionDe(String tarea) {
        return switch (tarea) {
            case "miedo" -> ModPotions.MIEDO.get();
            case "mareo" -> ModPotions.MAREO.get();
            case "frio" -> Potions.SLOWNESS;
            case "calor" -> Potions.FIRE_RESISTANCE;
            default -> ModPotions.SEDANTE.get();
        };
    }

    /** Saca la primera poción lista como poción (vacío si no hay ninguna). */
    public ItemStack sacar() {
        if (salidas.isEmpty()) return ItemStack.EMPTY;
        setChanged();
        return salidas.remove(0);
    }

    public static void tick(Level l, BlockPos p, BlockState s, DestiladorBlockEntity be) {
        if (l.isClientSide || be.cola.isEmpty() || !calor(l, p)) return;
        be.progreso++;
        if (l instanceof ServerLevel sl && be.progreso % 10 == 0)
            sl.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5, 2, 0.15, 0.1, 0.15, 0.02);
        if (be.progreso >= TICKS_POR_UNIDAD) {
            be.progreso = 0;
            String t = be.cola.remove(0);
            be.salidas.add(PotionUtils.setPotion(new ItemStack(Items.POTION), pocionDe(t)));
            be.setChanged();
            l.playSound(null, p, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /** Clic derecho: suavis seco o una muestra para meter, botella de cristal para sacar y mano vacía para ver cómo va. */
    public void alInteractuar(Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (st.is(ModItems.SUAVIS_SECO.get()) || tarea(st) != null) {
            boolean eraMuestra = tarea(st) != null;
            boolean ok = eraMuestra ? meterMuestra(st) : meter();
            if (!ok) p.displayClientMessage(Component.translatable("laboratorio.miscelanea.destilador.lleno").withStyle(ChatFormatting.RED), true);
            else if (!p.getAbilities().instabuild) {
                st.shrink(1);
                if (eraMuestra) {   // la muestra deja el tubo de ensayo vacío
                    ItemStack tubo = new ItemStack(LabRegistro.TUBO_ENSAYO.get());
                    if (!p.getInventory().add(tubo)) p.drop(tubo, false);
                }
            }
        } else if (st.is(Items.GLASS_BOTTLE) && !salidas.isEmpty()) {
            ItemStack pocion = sacar();
            if (!p.getAbilities().instabuild) st.shrink(1);
            if (!p.getInventory().add(pocion)) p.drop(pocion, false);
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.destilador.estado", cola.size(), salidas.size(),
                    Component.translatable(calor(level, worldPosition) ? "laboratorio.miscelanea.destilador.caliente" : "laboratorio.miscelanea.destilador.frio")), true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag c = new ListTag();
        for (String s : cola) c.add(StringTag.valueOf(s));
        t.put("Cola", c);
        ListTag o = new ListTag();
        for (ItemStack s : salidas) o.add(s.save(new CompoundTag()));
        t.put("Salidas", o);
        t.putInt("Progreso", progreso);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        cola.clear();
        for (Tag x : t.getList("Cola", Tag.TAG_STRING)) cola.add(x.getAsString());
        // compatibilidad con lo guardado antes: Suavis y Listo eran números
        for (int i = 0; i < t.getInt("Suavis"); i++) cola.add("suavis");
        salidas.clear();
        for (Tag x : t.getList("Salidas", Tag.TAG_COMPOUND)) salidas.add(ItemStack.of((CompoundTag) x));
        for (int i = 0; i < t.getInt("Listo"); i++) salidas.add(PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.SEDANTE.get()));
        progreso = t.getInt("Progreso");
    }
}
