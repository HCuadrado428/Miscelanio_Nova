package com.miscelanea.compat.jei;

import com.miscelanea.Miscelanea;
import com.miscelanea.cocina.Recetas;
import com.miscelanea.registry.ModItems;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.tortura.saw.SawRegistro;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/**
 * Plugin de JEI (opcional: solo se carga si JEI está instalado; el mod no depende de él para nada más).
 * <ul>
 *   <li>"Fogón": las recetas sin orden de {@link Recetas} (paella, tortilla, gazpacho, churros...).</li>
 *   <li>"Máquinas de la feria": algodonera, palomitera y churrería.</li>
 *   <li>Páginas de información de los bloques y objetos importantes (cómo se usan, de dónde salen).</li>
 * </ul>
 */
@JeiPlugin
public class MiscelaneaJei implements IModPlugin {
    private static final ResourceLocation ID = new ResourceLocation(Miscelanea.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        IGuiHelper g = r.getJeiHelpers().getGuiHelper();
        r.addRecipeCategories(new FogonCategoria(g), new FeriaCategoria(g));
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        r.addRecipes(FogonCategoria.TIPO, Recetas.TODAS);
        r.addRecipes(FeriaCategoria.TIPO, List.of(
                new FeriaCategoria.Receta(new ItemStack(ModItems.ALGODONERA.get()), new ItemStack(Items.SUGAR), new ItemStack(ModItems.ALGODON.get()), "algodonera"),
                new FeriaCategoria.Receta(new ItemStack(ModItems.PALOMITERA.get()), new ItemStack(Items.WHEAT), new ItemStack(ModItems.PALOMITAS.get()), "palomitera"),
                new FeriaCategoria.Receta(new ItemStack(ModItems.CHURRERIA.get()), new ItemStack(ModItems.FICHA.get()), new ItemStack(ModItems.CHURROS.get(), 4), "churreria"),
                new FeriaCategoria.Receta(new ItemStack(ModItems.CHURRERIA.get()), new ItemStack(ModItems.FICHA.get()), new ItemStack(ModItems.CHURROS_CHOCOLATE.get()), "churreria_chocolate")));

        // páginas de información
        info(r, ModItems.FOGON, "fogon");
        info(r, ModItems.SEMILLAS_TOMATE, "huerto");
        info(r, ModItems.ARROZ, "huerto");
        info(r, ModItems.ALGODONERA, "algodonera");
        info(r, ModItems.PALOMITERA, "palomitera");
        info(r, ModItems.CHURRERIA, "churreria");
        info(r, ModItems.FICHA, "ficha");
        info(r, ModItems.MEDIGUN, "tinte_arma");
        info(r, ModItems.LANZATINTAS, "tinte_arma");
        info(r, ModItems.RODILLO, "tinte_arma");
        info(r, ModItems.BICHOPEDIA, "bichopedia");
        info(r, ModItems.RED, "bichopedia");
        info(r, TorturaRegistro.TRAMPA_OSO, "trampa_oso");
        info(r, TorturaRegistro.CAJA_JIGSAW_ITEM, "trampa_oso");
        info(r, AparatosRegistro.GANCHO_ITEM, "gancho");
        info(r, SawRegistro.CAJA_AGUA, "caja_agua");
        info(r, SawRegistro.TANQUE_ITEM, "caja_agua");
        info(r, com.miscelanea.dream.DreamRegistro.CALDERO_ITEM, "caldero_mezclas");
        info(r, com.miscelanea.dream.DreamRegistro.FRASCO_MEZCLA, "frasco_mezcla");
        info(r, com.miscelanea.dream.DreamRegistro.FRASCO_MEZCLA_ARROJADIZO, "frasco_mezcla");
        info(r, com.miscelanea.dream.DreamRegistro.ESENCIA, "esencia_dream");
        info(r, com.miscelanea.dream.DreamRegistro.VENENO, "veneno_dream");
        info(r, com.miscelanea.dream.DreamRegistro.ANILLO, "anillo_medianoche");
        info(r, com.miscelanea.glitch.GlitchRegistro.MUESTRA, "muestra_glitch");
        info(r, com.miscelanea.glitch.GlitchRegistro.FRASCO, "frasco_muestras");
        // ---- armas de fuego: no se fabrican, se encuentran
        info(r, ModItems.REVOLVER, "revolver_botin");
        info(r, ModItems.ESCOPETA_PRESION, "escopeta_botin");
        info(r, ModItems.RIFLE, "rifle_botin");
        info(r, ModItems.GATLING, "gatling_botin");
        info(r, ModItems.WINCHESTER, "winchester_botin");
        info(r, ModItems.DERRINGER, "derringer_botin");
        info(r, ModItems.THOMPSON, "thompson_botin");
        info(r, ModItems.MOSQUETE, "mosquete_botin");
        info(r, ModItems.CANON_MANO, "canon_botin");
        // ---- lo que hacen además de disparar (1.1.0)
        info(r, ModItems.GATLING, "gatling_utilidad");
        info(r, ModItems.WINCHESTER, "winchester_deposito");
        info(r, ModItems.DERRINGER, "derringer_manga");
        info(r, ModItems.ESTUCHE_VIOLIN, "estuche_violin");
        info(r, ModItems.MOSQUETE, "mosquete_bayoneta");
        info(r, ModItems.CANON_MANO, "canon_bloques");
        // la bayoneta del mosquete en la mesa: una receta de ejemplo por espada vanilla (y la de quitarla)
        java.util.List<net.minecraft.world.item.crafting.CraftingRecipe> bay = new java.util.ArrayList<>();
        net.minecraft.world.item.Item[] espadas = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
        for (net.minecraft.world.item.Item e : espadas) {
            ItemStack sin = new ItemStack(ModItems.MOSQUETE.get());
            com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(sin, ItemStack.EMPTY);
            ItemStack con = new ItemStack(ModItems.MOSQUETE.get());
            com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(con, new ItemStack(e));
            var id = new ResourceLocation(Miscelanea.MOD_ID, "jei/mosquete_bayoneta_" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e).getPath());
            bay.add(new net.minecraft.world.item.crafting.ShapelessRecipe(id, "mosquete_bayoneta", net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT, con,
                    net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                            net.minecraft.world.item.crafting.Ingredient.of(sin), net.minecraft.world.item.crafting.Ingredient.of(e))));
        }
        ItemStack con = new ItemStack(ModItems.MOSQUETE.get());
        com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(con, new ItemStack(Items.DIAMOND_SWORD));
        ItemStack sin = new ItemStack(ModItems.MOSQUETE.get());
        com.miscelanea.armasfuego.oeste.MosqueteItem.ponerBayoneta(sin, ItemStack.EMPTY);
        bay.add(new net.minecraft.world.item.crafting.ShapelessRecipe(new ResourceLocation(Miscelanea.MOD_ID, "jei/mosquete_quitar_bayoneta"), "mosquete_bayoneta",
                net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT, sin,
                net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY, net.minecraft.world.item.crafting.Ingredient.of(con))));
        r.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, bay);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addRecipeCatalyst(new ItemStack(ModItems.FOGON.get()), FogonCategoria.TIPO);
        r.addRecipeCatalyst(new ItemStack(ModItems.ALGODONERA.get()), FeriaCategoria.TIPO);
        r.addRecipeCatalyst(new ItemStack(ModItems.PALOMITERA.get()), FeriaCategoria.TIPO);
        r.addRecipeCatalyst(new ItemStack(ModItems.CHURRERIA.get()), FeriaCategoria.TIPO);
    }

    private static void info(IRecipeRegistration r, Supplier<? extends Item> item, String clave) {
        if (item instanceof RegistryObject<?> ro && !ro.isPresent()) return;
        r.addIngredientInfo(new ItemStack(item.get()), VanillaTypes.ITEM_STACK, Component.translatable("jei.miscelanea.info." + clave));
    }
}
