package com.miscelanea.encantos;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La mesa de encantar de elegir (estilo Enchancement): sin azar. Con el objeto en la mano, clic derecho a la mesa: salen
 * los encantamientos que hay en los libros encantados de las estanterías cinceladas de alrededor (donde irían las
 * estanterías normales, con aire entre medias), los que le valen a ese objeto y caben en su límite. Se elige uno y
 * cuesta niveles y lapislázuli (más cuantos más tenga ya; menos si hay dos libros iguales). El libro no se gasta.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class MesaEncantar {
    private MesaEncantar() {}

    public record Opcion(Enchantment e, int niveles, int lapis) {}

    @SubscribeEvent
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (!Reglas.activo() || e.getHand() != InteractionHand.MAIN_HAND) return;
        Level l = e.getLevel();
        if (!(l.getBlockState(e.getPos()).getBlock() instanceof EnchantmentTableBlock)) return;
        Player p = e.getEntity();
        if (p.isShiftKeyDown() && !e.getItemStack().isEmpty()) return;     // poner bloques al lado
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(p instanceof ServerPlayer sp)) return;
        abrir(sp, e.getPos());
    }

    public static void abrir(ServerPlayer sp, BlockPos mesa) {
        ItemStack st = sp.getMainHandItem();
        if (st.isEmpty() || !st.isEnchantable() && EnchantmentHelper.getEnchantments(st).isEmpty() || st.is(Items.BOOK) || st.is(Items.ENCHANTED_BOOK)) {
            sp.displayClientMessage(Component.translatable("miscelanea.mesa.coge").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<Opcion> o = opciones(sp.serverLevel(), mesa, st);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new MesaPacket(mesa, o, libros(sp.serverLevel(), mesa).size()));
    }

    /** Encantamientos de los libros de las estanterías cinceladas (con cuántas copias hay de cada uno). */
    static Map<Enchantment, Integer> libros(ServerLevel l, BlockPos mesa) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (BlockPos off : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (!(l.getBlockEntity(mesa.offset(off)) instanceof ChiseledBookShelfBlockEntity b)) continue;
            if (!l.isEmptyBlock(mesa.offset(off.getX() / 2, off.getY(), off.getZ() / 2))) continue;
            for (int i = 0; i < b.getContainerSize(); i++) {
                ItemStack libro = b.getItem(i);
                if (!libro.is(Items.ENCHANTED_BOOK)) continue;
                for (Enchantment e : EnchantmentHelper.getEnchantments(libro).keySet()) m.merge(e, 1, Integer::sum);
            }
        }
        return m;
    }

    public static List<Opcion> opciones(ServerLevel l, BlockPos mesa, ItemStack st) {
        List<Opcion> o = new ArrayList<>();
        Map<Enchantment, Integer> ya = EnchantmentHelper.getEnchantments(st);
        int n = Reglas.cuenta(ya);
        boolean lleno = n >= Reglas.limite(st);
        for (Map.Entry<Enchantment, Integer> en : libros(l, mesa).entrySet()) {
            Enchantment e = en.getKey();
            if (!Reglas.permitido(e) || Reglas.especial(e) || ya.containsKey(e) || !(e.category.canEnchant(st.getItem()) || e.canEnchant(st))) continue;
            if (!EnchantmentHelper.isEnchantmentCompatible(ya.keySet(), e)) continue;
            if (lleno && !e.isCurse()) continue;
            int niveles = Math.max(2, 5 + 5 * n - (en.getValue() >= 2 ? 2 : 0));
            o.add(new Opcion(e, niveles, 1 + n));
        }
        return o;
    }

    /** El jugador ha elegido (llega del cliente): se comprueba todo otra vez. */
    public static void elegir(ServerPlayer sp, BlockPos mesa, String id) {
        ServerLevel l = sp.serverLevel();
        if (!(l.getBlockState(mesa).getBlock() instanceof EnchantmentTableBlock) || sp.distanceToSqr(mesa.getCenter()) > 64) return;
        ItemStack st = sp.getMainHandItem();
        Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(new net.minecraft.resources.ResourceLocation(id));
        if (e == null) return;
        Opcion op = null;
        for (Opcion o : opciones(l, mesa, st)) if (o.e() == e) op = o;
        if (op == null) return;
        boolean gratis = sp.getAbilities().instabuild;
        if (!gratis) {
            if (sp.experienceLevel < op.niveles()) {
                sp.displayClientMessage(Component.translatable("miscelanea.mesa.sin_niveles", op.niveles()).withStyle(ChatFormatting.RED), true);
                return;
            }
            int lapis = 0;
            for (ItemStack s : sp.getInventory().items) if (s.is(Items.LAPIS_LAZULI)) lapis += s.getCount();
            if (lapis < op.lapis()) {
                sp.displayClientMessage(Component.translatable("miscelanea.mesa.sin_lapis", op.lapis()).withStyle(ChatFormatting.RED), true);
                return;
            }
            int falta = op.lapis();
            for (ItemStack s : sp.getInventory().items) {
                if (falta <= 0) break;
                if (!s.is(Items.LAPIS_LAZULI)) continue;
                int q = Math.min(falta, s.getCount());
                s.shrink(q);
                falta -= q;
            }
            sp.giveExperienceLevels(-op.niveles());
        }
        st.enchant(e, e.getMaxLevel());
        sp.awardStat(net.minecraft.stats.Stats.ENCHANT_ITEM);
        CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, st, op.niveles());
        l.playSound(null, mesa, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1F, l.random.nextFloat() * 0.1F + 0.9F);
        abrir(sp, mesa);     // refresca la lista
    }

    /** Para la prueba: un libro de ese encantamiento. */
    public static ItemStack libro(Enchantment e) {
        ItemStack b = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(b, new net.minecraft.world.item.enchantment.EnchantmentInstance(e, e.getMaxLevel()));
        return b;
    }
}
