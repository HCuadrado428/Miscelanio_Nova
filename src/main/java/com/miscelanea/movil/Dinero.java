package com.miscelanea.movil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * El dinero del banco. Con OmegaChips el saldo va en "chips" (1 chip = 1 moneda de cobre): cobre 1, hierro 9, oro 81,
 * diamante 729, y sus bloques valen 4 monedas. Sin OmegaChips se usan esmeraldas (1) y bloques de esmeralda (9).
 * Solo se usan los objetos (OmegaChips no tiene API): se leen por su id del registro.
 */
public final class Dinero {
    private Dinero() {}

    public record Billete(Item item, long valor) {}

    private static List<Billete> todos, monedas;
    private static Boolean omega;

    public static boolean omega() {
        if (omega == null) omega = ModList.get() != null && ModList.get().isLoaded("omegachips");
        return omega;
    }

    private static Item item(String id) {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return i == null || i == Items.AIR ? null : i;
    }

    private static void cargar() {
        if (todos != null) return;
        List<Billete> t = new ArrayList<>(), m = new ArrayList<>();
        if (omega()) {
            String[] n = {"diamond", "gold", "iron", "copper"};
            long[] v = {729, 81, 9, 1};
            for (int i = 0; i < n.length; i++) {
                Item b = item("omegachips:" + n[i] + "_coin_block"), c = item("omegachips:" + n[i] + "_coin");
                if (b != null) t.add(new Billete(b, v[i] * 4));
                if (c != null) {
                    t.add(new Billete(c, v[i]));
                    m.add(new Billete(c, v[i]));
                }
            }
        }
        if (m.isEmpty()) {
            t.clear();
            t.add(new Billete(Items.EMERALD_BLOCK, 9));
            t.add(new Billete(Items.EMERALD, 1));
            m.add(new Billete(Items.EMERALD_BLOCK, 9));
            m.add(new Billete(Items.EMERALD, 1));
            omega = false;
        }
        t.sort((a, b) -> Long.compare(b.valor, a.valor));
        m.sort((a, b) -> Long.compare(b.valor, a.valor));
        todos = t;
        monedas = m;
    }

    /** Monedas con las que se paga (de más a menos valor), para el desglose y el cambio. */
    public static List<Billete> monedas() {
        cargar();
        return monedas;
    }

    public static long valor(ItemStack s) {
        if (s.isEmpty()) return 0;
        cargar();
        for (Billete b : todos) if (s.is(b.item)) return b.valor * s.getCount();
        return 0;
    }

    public static long enInventario(ServerPlayer p) {
        long n = 0;
        for (ItemStack s : p.getInventory().items) n += valor(s);
        return n;
    }

    /**
     * Quita del inventario dinero por valor de 'n' (como mucho lo que lleve) y devuelve cuánto ha quitado. Se recogen
     * monedas de la más pequeña a la más grande y, si sobra, se devuelve el cambio.
     */
    public static long cobrar(ServerPlayer p, long n) {
        if (n <= 0) return 0;
        cargar();
        long tomado = 0;
        var inv = p.getInventory().items;
        for (int k = todos.size() - 1; k >= 0 && tomado < n; k--) {
            Billete b = todos.get(k);
            for (ItemStack s : inv) {
                if (tomado >= n) break;
                if (!s.is(b.item)) continue;
                long faltan = n - tomado;
                int q = (int) Math.min(s.getCount(), (faltan + b.valor - 1) / b.valor);
                s.shrink(q);
                tomado += q * b.valor;
            }
        }
        p.getInventory().setChanged();
        if (tomado > n) {
            dar(p, tomado - n);
            tomado = n;
        }
        return tomado;
    }

    /** Da 'n' en el menor número de monedas (lo que no cabe, al suelo). */
    public static void dar(ServerPlayer p, long n) {
        for (Billete b : monedas()) {
            long q = n / b.valor;
            n -= q * b.valor;
            while (q > 0) {
                int c = (int) Math.min(q, new ItemStack(b.item).getMaxStackSize());
                ItemStack s = new ItemStack(b.item, c);
                if (!p.getInventory().add(s) || !s.isEmpty()) p.drop(s, false);
                q -= c;
            }
        }
    }
}
