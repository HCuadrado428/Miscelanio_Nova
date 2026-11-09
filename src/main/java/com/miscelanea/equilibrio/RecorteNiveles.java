package com.miscelanea.equilibrio;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recorte de niveles imposibles: ningún encantamiento (vanilla o de otros mods) puede pasar de su máximo normal
 * ({@link Enchantment#getMaxLevel()}). Lo que lo pase (p. ej. Protección VII hecha con el libro de impregnación mágica
 * de Corail Tombstone) se deja en el máximo. Todo lo demás es como siempre: no se quita ningún encantamiento ni se
 * limita cuántos hay.
 * <ul>
 *   <li>Inventario de los jugadores: se mira cada segundo y se devuelve la experiencia de los niveles quitados.</li>
 *   <li>Libros encantados: igual, se recortan (siguen siendo el mismo libro).</li>
 *   <li>Mobs: aparecen con el equipo ya recortado (sin devolver nada).</li>
 * </ul>
 * Se ajusta en la config del servidor (sección "equilibrio": recortar_niveles y xp_por_nivel).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class RecorteNiveles {
    private RecorteNiveles() {}

    public static boolean activo() {
        return ConfigServidor.equilibrioRecortar();
    }

    /** Los modos del aspirafantasmas no son encantamientos de verdad: no se tocan. */
    private static boolean especial(Enchantment e) {
        return e.category == ModEnchantments.ASPIRAFANTASMAS;
    }

    /** Resultado de recortar un objeto: el objeto nuevo y los niveles quitados. */
    public record Recorte(ItemStack nuevo, int niveles) {}

    /** El objeto recortado, o null si no hay nada que recortar. */
    public static Recorte recortar(ItemStack st) {
        if (st.isEmpty()) return null;
        Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(st);
        if (m.isEmpty()) return null;
        Map<Enchantment, Integer> bien = new LinkedHashMap<>();
        int niveles = 0;
        for (Map.Entry<Enchantment, Integer> en : m.entrySet()) {
            Enchantment e = en.getKey();
            int l = en.getValue();
            int max = e.getMaxLevel();
            if (!especial(e) && l > max) {
                niveles += l - max;
                l = max;
            }
            bien.put(e, l);
        }
        if (niveles == 0) return null;
        ItemStack nuevo = st.copy();
        if (st.is(Items.ENCHANTED_BOOK)) {
            // setEnchantments en un libro solo sube niveles: se vacía y se vuelve a llenar
            nuevo.removeTagKey("StoredEnchantments");
            for (var en : bien.entrySet()) EnchantedBookItem.addEnchantment(nuevo, new EnchantmentInstance(en.getKey(), en.getValue()));
        } else {
            EnchantmentHelper.setEnchantments(bien, nuevo);
        }
        return new Recorte(nuevo, niveles);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7 || !activo()) return;
        recortarInventario(p);
    }

    /** Recorta todo el inventario del jugador; devuelve la experiencia (en puntos) que se le ha dado. */
    public static int recortarInventario(ServerPlayer p) {
        Inventory inv = p.getInventory();
        int niveles = 0, objetos = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            Recorte r = recortar(inv.getItem(i));
            if (r == null) continue;
            inv.setItem(i, r.nuevo());
            niveles += r.niveles();
            objetos++;
        }
        if (objetos == 0) return 0;
        int xp = niveles * ConfigServidor.equilibrioXpPorNivel();
        if (xp > 0) p.giveExperiencePoints(xp);
        p.sendSystemMessage(Component.translatable("miscelanea.recorte.niveles", objetos, xp).withStyle(ChatFormatting.LIGHT_PURPLE));
        return xp;
    }

    /** Los mobs aparecen con el equipo ya recortado. */
    @SubscribeEvent
    public static void mob(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m) || !activo()) return;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            Recorte r = recortar(m.getItemBySlot(s));
            if (r != null) m.setItemSlot(s, r.nuevo());
        }
    }
}
