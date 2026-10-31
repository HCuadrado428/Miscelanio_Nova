package com.miscelanea.consola;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;

/** Récords de la consola (guardados en el jugador, sobreviven a la muerte) y cartuchos escondidos en cofres. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Records {
    private static final String CLAVE = "MiscelaneaRecords";
    private static final int MAXIMO = 9_999_999;

    /** Cofre → probabilidad de que traiga un cartucho (y la mitad de eso, la consola). */
    private static final Map<String, Float> COFRES = Map.ofEntries(
            Map.entry("chests/simple_dungeon", 0.30F), Map.entry("chests/abandoned_mineshaft", 0.22F),
            Map.entry("chests/desert_pyramid", 0.25F), Map.entry("chests/jungle_temple", 0.30F),
            Map.entry("chests/stronghold_corridor", 0.25F), Map.entry("chests/stronghold_library", 0.35F),
            Map.entry("chests/shipwreck_treasure", 0.25F), Map.entry("chests/buried_treasure", 0.35F),
            Map.entry("chests/ancient_city", 0.30F), Map.entry("chests/woodland_mansion", 0.35F),
            Map.entry("chests/village/village_plains_house", 0.08F), Map.entry("chests/village/village_desert_house", 0.08F),
            Map.entry("chests/village/village_savanna_house", 0.08F), Map.entry("chests/village/village_taiga_house", 0.08F),
            Map.entry("chests/village/village_snowy_house", 0.08F), Map.entry("chests/pillager_outpost", 0.20F));

    private Records() {}

    private static CompoundTag datos(ServerPlayer p) {
        CompoundTag d = p.getPersistentData().getCompound(CLAVE);
        p.getPersistentData().put(CLAVE, d);
        return d;
    }

    public static void registrar(ServerPlayer p, Juego j, int puntos) {
        if (puntos <= 0 || puntos > MAXIMO) return;
        if (!tieneConsola(p)) return;
        CompoundTag d = datos(p);
        if (puntos <= d.getInt(j.id)) return;
        boolean habia = d.getInt(j.id) > 0;
        d.putInt(j.id, puntos);
        sync(p);
        if (habia) p.displayClientMessage(Component.translatable("msg.miscelanea.record",
                Component.translatable(j.clave()), puntos).withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean tieneConsola(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof ConsolaItem) return true;
        return false;
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RecordsPacket(datos(p).copy()));
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        CompoundTag v = e.getOriginal().getPersistentData();
        if (v.contains(CLAVE)) e.getEntity().getPersistentData().put(CLAVE, v.getCompound(CLAVE).copy());
    }

    @SubscribeEvent
    public static void botin(LootTableLoadEvent e) {
        ResourceLocation id = e.getName();
        if (!"minecraft".equals(id.getNamespace())) return;
        Float prob = COFRES.get(id.getPath());
        if (prob == null) return;
        LootPool.Builder cartuchos = LootPool.lootPool().name("miscelanea_cartuchos").setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(prob));
        for (Juego j : Juego.values()) cartuchos.add(LootItem.lootTableItem(ModItems.cartucho(j)).setWeight(j.incorporado ? 1 : 3));
        e.getTable().addPool(cartuchos.build());
        e.getTable().addPool(LootPool.lootPool().name("miscelanea_consola").setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(prob * 0.4F))
                .add(LootItem.lootTableItem(ModItems.CONSOLA.get())).build());
    }
}
