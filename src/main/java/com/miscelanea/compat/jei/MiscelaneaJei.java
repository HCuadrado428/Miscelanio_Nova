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
        info(r, SawRegistro.POZO_ITEM, "pozo");
        info(r, SawRegistro.ALAMBRE_ITEM, "alambre");
        info(r, SawRegistro.TENAZAS, "alambre");
        info(r, SawRegistro.SALIDA_ITEM, "salida");
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
