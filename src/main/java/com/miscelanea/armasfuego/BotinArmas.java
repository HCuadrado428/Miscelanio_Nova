package com.miscelanea.armasfuego;

import com.google.common.base.Suppliers;
import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Las armas de fuego no se fabrican: se encuentran explorando. Un Global Loot Modifier (data/miscelanea/loot_modifiers/
 * armas_fuego.json) mete en los cofres de tres estructuras vanilla, con la probabilidad de la config del servidor
 * (sección armas.armas_de_fuego, se lee al abrir cada cofre):
 * <ul>
 *   <li>revólver de ruleta: aldeas (armero, herrero de herramientas y de armas; con menos probabilidad, las casas);</li>
 *   <li>escopeta de presión: barcos hundidos (provisiones, tesoro y mapas);</li>
 *   <li>rifle de catalejo: puestos de saqueadores.</li>
 * </ul>
 * Salen con algo de munición, a veces algo oxidadas y a veces con uno de sus encantamientos propios.
 */
public class BotinArmas extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Miscelanea.MOD_ID);

    public static final Supplier<Codec<BotinArmas>> CODEC = Suppliers.memoize(
            () -> RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, BotinArmas::new)));

    public static final RegistryObject<Codec<BotinArmas>> TIPO = SERIALIZERS.register("armas_fuego", CODEC);

    public static void registrar(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    public BotinArmas(LootItemCondition[] condiciones) {
        super(condiciones);
    }

    public enum Arma {REVOLVER, ESCOPETA, RIFLE, GATLING, WINCHESTER, DERRINGER, THOMPSON, MOSQUETE, CANON}

    /** Qué arma puede salir en esa tabla de botín (null: ninguna). */
    public static Arma arma(ResourceLocation id) {
        if (id == null || !"minecraft".equals(id.getNamespace())) return null;
        String p = id.getPath();
        if (p.equals("chests/pillager_outpost")) return Arma.RIFLE;
        if (p.startsWith("chests/shipwreck_")) return Arma.ESCOPETA;
        if (p.startsWith("chests/village/") && probabilidad(id) > 0) return Arma.REVOLVER;
        return armaNueva(p);
    }

    /** Segunda tanda: cada una en una estructura distinta (sin mirar la probabilidad: la usa probabilidad()). */
    private static Arma armaNueva(String p) {
        switch (p) {
            case "chests/desert_pyramid": return Arma.GATLING;
            case "chests/abandoned_mineshaft": return Arma.WINCHESTER;
            case "chests/buried_treasure": return Arma.DERRINGER;
            case "chests/woodland_mansion": return Arma.THOMPSON;
            case "chests/stronghold_corridor":
            case "chests/stronghold_crossing": return Arma.MOSQUETE;
            case "chests/bastion_other":
            case "chests/bastion_bridge": return Arma.CANON;
            default: return null;
        }
    }

    /** Probabilidad de que el arma salga en un cofre de esa tabla. */
    public static double probabilidad(ResourceLocation id) {
        String p = id.getPath();
        if (p.equals("chests/pillager_outpost")) return ConfigServidor.botinRifle();
        if (p.startsWith("chests/shipwreck_")) return ConfigServidor.botinEscopeta();
        Arma a = armaNueva(p);
        if (a != null) switch (a) {
            case GATLING: return com.miscelanea.armasfuego.comun.EquilibrioArmas.botinGatling();
            case WINCHESTER: return com.miscelanea.armasfuego.comun.EquilibrioArmas.botinWinchester();
            case DERRINGER: return com.miscelanea.armasfuego.comun.EquilibrioArmas.botinDerringer();
            case THOMPSON: return com.miscelanea.armasfuego.comun.EquilibrioArmas.botinThompson();
            case MOSQUETE: return com.miscelanea.armasfuego.comun.EquilibrioArmas.botinMosquete();
            case CANON: return com.miscelanea.armasfuego.comun.EquilibrioArmas.botinCanon();
            default: break;
        }
        if (p.startsWith("chests/village/")) {
            String n = p.substring("chests/village/".length());
            if (n.equals("village_armorer") || n.equals("village_toolsmith") || n.equals("village_weaponsmith"))
                return ConfigServidor.botinRevolverHerreros();
            if (n.endsWith("_house")) return ConfigServidor.botinRevolverCasas();
        }
        return 0;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> botin, LootContext ctx) {
        ResourceLocation id = ctx.getQueriedLootTableId();
        Arma a = arma(id);
        if (a == null) return botin;
        RandomSource r = ctx.getRandom();
        if (r.nextDouble() >= probabilidad(id)) return botin;
        botin.addAll(generar(a, r));
        return botin;
    }

    /** El arma (un poco oxidada a veces, y a veces encantada) y algo de su munición. */
    public static List<ItemStack> generar(Arma a, RandomSource r) {
        List<ItemStack> l = new ArrayList<>();
        float f = r.nextFloat();
        int oxido = f < 0.5F ? 0 : f < 0.85F ? 1 : 2;
        ItemStack arma;
        switch (a) {
            case REVOLVER -> {
                arma = new ItemStack(ModItems.REVOLVER.get());
                if (oxido > 0) arma.getOrCreateTag().putInt(com.miscelanea.armasfuego.pistola.Tambor.OXIDO, oxido);
                l.add(new ItemStack(ModItems.REVOLVER_BALA.get(), 3 + r.nextInt(6)));
                if (r.nextFloat() < 0.35F) {
                    var raras = List.of(ModItems.REVOLVER_BENGALA, ModItems.REVOLVER_DARDO, ModItems.REVOLVER_FOGUEO, ModItems.REVOLVER_IMAN, ModItems.REVOLVER_TIEMPO);
                    l.add(new ItemStack(raras.get(r.nextInt(raras.size())).get(), 1 + r.nextInt(3)));
                }
            }
            case ESCOPETA -> {
                arma = new ItemStack(ModItems.ESCOPETA_PRESION.get());
                if (oxido > 0) com.miscelanea.armasfuego.escopeta.Presion.ponerOxido(arma, oxido);
                var tipos = com.miscelanea.armasfuego.escopeta.TipoCartucho.values();
                l.add(new ItemStack(com.miscelanea.armasfuego.escopeta.TipoCartucho.GRAVA.item(), 2 + r.nextInt(5)));
                if (r.nextFloat() < 0.4F) l.add(new ItemStack(tipos[r.nextInt(tipos.length)].item(), 1 + r.nextInt(3)));
            }
            case GATLING -> {
                arma = new ItemStack(ModItems.GATLING.get());
                l.add(new ItemStack(ModItems.CINTA_GATLING.get(), 1 + r.nextInt(2)));
            }
            case WINCHESTER -> {
                arma = new ItemStack(ModItems.WINCHESTER.get());
                com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(arma, r.nextInt(5));
                l.add(new ItemStack(ModItems.REVOLVER_BALA.get(), 6 + r.nextInt(7)));
            }
            case DERRINGER -> {
                arma = new ItemStack(ModItems.DERRINGER.get());
                com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(arma, 2);
                l.add(new ItemStack(ModItems.BALA_DERRINGER.get(), 4 + r.nextInt(5)));
            }
            case THOMPSON -> {
                // sale dentro de su estuche de violín, claro
                ItemStack t = new ItemStack(ModItems.THOMPSON.get());
                com.miscelanea.armasfuego.comun.ArmaFuegoItem.ponerBalas(t, 10 + r.nextInt(30));
                l.add(new ItemStack(ModItems.CARGADOR_THOMPSON.get(), 1));
                l.add(0, com.miscelanea.armasfuego.oeste.EstucheViolinItem.con(t));
                return l;
            }
            case MOSQUETE -> {
                arma = new ItemStack(ModItems.MOSQUETE.get());
                l.add(new ItemStack(ModItems.BOLA_MOSQUETE.get(), 4 + r.nextInt(6)));
                l.add(new ItemStack(net.minecraft.world.item.Items.GUNPOWDER, 2 + r.nextInt(4)));
            }
            case CANON -> {
                arma = new ItemStack(ModItems.CANON_MANO.get());
                l.add(new ItemStack(net.minecraft.world.item.Items.GUNPOWDER, 4 + r.nextInt(5)));
            }
            default -> {
                arma = new ItemStack(ModItems.RIFLE.get());
                if (oxido > 0) arma.getOrCreateTag().putInt(com.miscelanea.armasfuego.rifle.Rifle.OXIDO, oxido);
                l.add(new ItemStack(ModItems.BALA_RIFLE.get(), 4 + r.nextInt(7)));
                if (r.nextFloat() < 0.25F) l.add(new ItemStack(ModItems.BALA_TRAZADORA.get(), 2 + r.nextInt(3)));
                if (r.nextFloat() < 0.2F) l.add(new ItemStack(ModItems.BALA_CUERDA.get(), 1 + r.nextInt(2)));
                if (r.nextFloat() < 0.15F) {
                    ItemStack pin = com.miscelanea.armasfuego.rifle.Pintura.bala(r.nextInt(16));
                    pin.setCount(2 + r.nextInt(3));
                    l.add(pin);
                }
            }
        }
        if (r.nextDouble() < ConfigServidor.botinEncantada()) {
            List<Enchantment> suyos = new ArrayList<>();
            for (var e : ForgeRegistries.ENCHANTMENTS.getEntries())
                if (Miscelanea.MOD_ID.equals(e.getKey().location().getNamespace()) && e.getValue().canEnchant(arma)) suyos.add(e.getValue());
            if (!suyos.isEmpty()) {
                Enchantment e = suyos.get(r.nextInt(suyos.size()));
                arma.enchant(e, 1 + r.nextInt(e.getMaxLevel()));
            }
        }
        l.add(0, arma);
        return l;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
