package com.miscelanea.encantos;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Las reglas de los encantamientos (estilo Enchancement):
 * <ul>
 *   <li>Casi todos los vanilla están quitados: se quedan Toque de seda, Fortuna, Botín, Respiración, Afinidad acuática
 *       y las maldiciones. Los de otros mods se dejan (salvo que se quiten en la config).</li>
 *   <li>Como mucho 2 encantamientos en armas y herramientas y 4 en cada pieza de armadura (las maldiciones no cuentan).</li>
 *   <li>Ningún nivel por encima del máximo normal (lo que suba el libro de impregnación de Tombstone se recorta).</li>
 *   <li>Lo que ya había: al tenerlo en el inventario se le quita lo prohibido y lo que sobra, y se devuelve la
 *       experiencia. Un libro encantado que se queda sin nada se convierte en un libro de un encantamiento nuevo al azar
 *       (así los libros del botín sirven para la mesa de elegir).</li>
 *   <li>En el yunque no se pueden poner encantamientos (ni libros ni juntar objetos encantados); los aldeanos ya no
 *       venden libros ni cosas encantadas; los mobs aparecen con el equipo ya limpio.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Reglas {
    private Reglas() {}

    /** Los vanilla que se quedan. */
    public static final Set<String> VANILLA_PERMITIDOS = Set.of("silk_touch", "fortune", "looting", "respiration", "aqua_affinity",
            "binding_curse", "vanishing_curse");

    public static boolean activo() {
        return ConfigServidor.encantosActivo();
    }

    /** ¿Se puede tener este encantamiento? */
    public static boolean permitido(Enchantment e) {
        if (!activo()) return true;
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(e);
        if (id == null) return true;
        if (ConfigServidor.encantosProhibidosExtra().contains(id.toString())) return false;
        if (id.getNamespace().equals("minecraft")) return VANILLA_PERMITIDOS.contains(id.getPath());
        return true;
    }

    /** Los del aspirafantasmas (modos de la boquilla) no son encantamientos de verdad: ni cuentan ni se tocan. */
    static boolean especial(Enchantment e) {
        return e.category == ModEnchantments.ASPIRAFANTASMAS;
    }

    public static int limite(ItemStack st) {
        return st.getItem() instanceof ArmorItem || st.getItem().getEquipmentSlot(st) != null && st.getItem().getEquipmentSlot(st).getType() == EquipmentSlot.Type.ARMOR
                ? ConfigServidor.encantosLimiteArmadura() : ConfigServidor.encantosLimiteArmas();
    }

    /** Cuántos cuentan para el límite. */
    public static int cuenta(Map<Enchantment, Integer> m) {
        int n = 0;
        for (Enchantment e : m.keySet()) if (!e.isCurse() && !especial(e)) n++;
        return n;
    }

    /** Resultado de limpiar un objeto: el objeto nuevo (o null si no cambia) y los niveles quitados. */
    public record Limpieza(ItemStack nuevo, int niveles) {}

    public static Limpieza limpiar(ItemStack st) {
        if (!activo() || st.isEmpty()) return null;
        boolean libro = st.is(Items.ENCHANTED_BOOK);
        Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(st);
        if (m.isEmpty()) return null;
        Map<Enchantment, Integer> fuera = new LinkedHashMap<>();
        int niveles = 0;
        boolean cambia = false;
        for (Map.Entry<Enchantment, Integer> en : m.entrySet()) {
            Enchantment e = en.getKey();
            int l = en.getValue();
            if (especial(e)) {
                fuera.put(e, l);
                continue;
            }
            if (!permitido(e)) {
                niveles += Math.max(1, l);
                cambia = true;
                continue;
            }
            if (l > e.getMaxLevel()) {
                niveles += l - e.getMaxLevel();
                l = e.getMaxLevel();
                cambia = true;
            }
            fuera.put(e, l);
        }
        if (!libro) {
            // el límite: sobran los últimos (primero se quitan los vanilla que quedan, luego los demás)
            int lim = limite(st);
            while (cuenta(fuera) > lim) {
                Enchantment quitar = null;
                for (Enchantment e : fuera.keySet())
                    if (!e.isCurse() && !especial(e) && (quitar == null || !(e instanceof EncantoMisc))) quitar = e;
                if (quitar == null) break;
                niveles += fuera.remove(quitar);
                cambia = true;
            }
        }
        if (!cambia) return null;
        ItemStack nuevo;
        if (libro) {
            nuevo = new ItemStack(Items.ENCHANTED_BOOK);
            if (st.hasCustomHoverName()) nuevo.setHoverName(st.getHoverName());
            if (fuera.isEmpty()) {
                // un libro que se queda vacío: uno nuevo al azar (sin devolver experiencia)
                List<Enchantment> t = Encantos.todos();
                Enchantment e = t.get((int) (Math.abs(st.hashCode() * 31L + System.nanoTime()) % t.size()));
                EnchantedBookItem.addEnchantment(nuevo, new EnchantmentInstance(e, 1));
                return new Limpieza(nuevo, 0);
            }
            for (var en : fuera.entrySet()) EnchantedBookItem.addEnchantment(nuevo, new EnchantmentInstance(en.getKey(), en.getValue()));
        } else {
            nuevo = st.copy();
            EnchantmentHelper.setEnchantments(fuera, nuevo);
        }
        return new Limpieza(nuevo, niveles);
    }

    // ------------------------------------------------------------------ lo que ya hay: el inventario de cada jugador
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7 || !activo()) return;
        limpiarInventario(p);
    }

    public static void limpiarInventario(ServerPlayer p) {
        Inventory inv = p.getInventory();
        int niveles = 0, objetos = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            Limpieza l = limpiar(st);
            if (l == null) continue;
            inv.setItem(i, l.nuevo());
            niveles += l.niveles();
            objetos++;
        }
        if (objetos == 0) return;
        int xp = niveles * ConfigServidor.encantosXpPorNivel();
        if (xp > 0) p.giveExperiencePoints(xp);
        p.sendSystemMessage(Component.translatable("miscelanea.encantos.limpiado", objetos, xp).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Los mobs salen con el equipo ya limpio. */
    @SubscribeEvent
    public static void mob(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m) || !activo()) return;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            Limpieza l = limpiar(m.getItemBySlot(s));
            if (l != null) m.setItemSlot(s, l.nuevo());
        }
    }

    /** En el yunque no se encanta: ni libros, ni juntar dos cosas encantadas. */
    @SubscribeEvent
    public static void yunque(AnvilUpdateEvent e) {
        if (!activo()) return;
        if (!EnchantmentHelper.getEnchantments(e.getRight()).isEmpty()) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ comercio
    @SubscribeEvent
    public static void aldeanos(VillagerTradesEvent e) {
        if (!activo()) return;
        for (List<VillagerTrades.ItemListing> l : e.getTrades().values()) quitarEncantados(l);
    }

    @SubscribeEvent
    public static void vagabundo(WandererTradesEvent e) {
        if (!activo()) return;
        quitarEncantados(e.getGenericTrades());
        quitarEncantados(e.getRareTrades());
    }

    private static void quitarEncantados(List<VillagerTrades.ItemListing> l) {
        List<VillagerTrades.ItemListing> fuera = new ArrayList<>();
        for (VillagerTrades.ItemListing t : l) {
            // son clases privadas de VillagerTrades: se reconocen por el nombre (en Forge 1.20 las clases no se ofuscan)
            String n = t.getClass().getSimpleName();
            if (n.equals("EnchantBookForEmeralds") || n.equals("EnchantedItemForEmeralds")) fuera.add(t);
        }
        l.removeAll(fuera);
    }
}
