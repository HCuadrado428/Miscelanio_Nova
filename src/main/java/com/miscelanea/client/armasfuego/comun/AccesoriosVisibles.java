package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Cobre;
import com.miscelanea.armasfuego.escopeta.EscopetaPresionItem;
import com.miscelanea.armasfuego.oeste.CanonManoItem;
import com.miscelanea.armasfuego.oeste.DerringerItem;
import com.miscelanea.armasfuego.oeste.MosqueteItem;
import com.miscelanea.armasfuego.oeste.ThompsonItem;
import com.miscelanea.armasfuego.oeste.WinchesterItem;
import com.miscelanea.armasfuego.pistola.RevolverItem;
import com.miscelanea.armasfuego.pistola.Tambor;
import com.miscelanea.armasfuego.rifle.RifleItem;
import com.miscelanea.registry.ModEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Las piezas de cobre que lleva un arma por sus encantamientos (el accesorio ES el encantamiento): Silenciador (rifle, Derringer,
 * Winchester y Thompson), Compensador (Thompson), Cañón largo (revólver), Estrangulador (escopeta), Mira de cobre (mosquete),
 * Ánima rayada (cañón de mano) y Depósito largo (Winchester). Cada pieza es un modelo ya colocado en las coordenadas del arma
 * (herramientas/generar_accesorios.py), con la textura del óxido del arma, y se dibuja justo después del arma (mixin de
 * ItemRenderer.render) en todo menos el inventario.
 */
public final class AccesoriosVisibles {
    private AccesoriosVisibles() {}

    private static final String[] PIEZAS = {"silenciador_rifle", "silenciador_derringer", "silenciador_winchester", "silenciador_thompson",
            "compensador_thompson", "canon_largo_revolver", "estrangulador_escopeta", "mira_mosquete", "anima_canon", "deposito_winchester"};
    public static final List<ResourceLocation> MODELOS = new ArrayList<>();
    static {
        for (String p : PIEZAS) for (int ox = 0; ox < 4; ox++) MODELOS.add(rl(p, ox));
    }

    private static ResourceLocation rl(String pieza, int ox) {
        return new ResourceLocation(Miscelanea.MOD_ID, "item/armas3d/acc_" + pieza + "_" + ox);
    }

    private static boolean tiene(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.enchantment.Enchantment> e, ItemStack s) {
        return ArmaFuegoItem.nivel(e, s) > 0;
    }

    /** Las piezas que lleva (nombres de pieza). */
    static List<String> piezas(ItemStack s) {
        List<String> l = new ArrayList<>(2);
        Item i = s.getItem();
        if (!s.isEnchanted()) return l;
        boolean sil = tiene(ModEnchantments.SILENCIADOR, s);
        if (i instanceof RifleItem && sil) l.add("silenciador_rifle");
        else if (i instanceof DerringerItem && sil) l.add("silenciador_derringer");
        else if (i instanceof WinchesterItem) {
            if (sil) l.add("silenciador_winchester");
            if (tiene(ModEnchantments.WINCHESTER_DEPOSITO, s)) l.add("deposito_winchester");
        } else if (i instanceof ThompsonItem) {
            if (sil) l.add("silenciador_thompson");
            else if (tiene(ModEnchantments.THOMPSON_COMPENSADOR, s)) l.add("compensador_thompson");
        } else if (i instanceof RevolverItem && tiene(ModEnchantments.CANON_LARGO, s)) l.add("canon_largo_revolver");
        else if (i instanceof EscopetaPresionItem && tiene(ModEnchantments.ESCOPETA_ESTRANGULADOR, s)) l.add("estrangulador_escopeta");
        else if (i instanceof MosqueteItem && tiene(ModEnchantments.MOSQUETE_MIRA, s)) l.add("mira_mosquete");
        else if (i instanceof CanonManoItem && tiene(ModEnchantments.CANON_ANIMA, s)) l.add("anima_canon");
        return l;
    }

    public static void dibujar(ItemStack s, ItemDisplayContext ctx, boolean izq, PoseStack ps, MultiBufferSource buf, int luz, int overlay, BakedModel m) {
        if (Minecraft.getInstance().level == null) return;
        List<String> piezas = piezas(s);
        if (piezas.isEmpty()) return;
        int ox = Mth.clamp(s.getItem() instanceof RevolverItem ? Tambor.oxido(s) : Cobre.oxido(s), 0, 3);
        BakedModel real = m.applyTransform(ctx, new com.mojang.blaze3d.vertex.PoseStack(), false);
        ps.pushPose();
        // la misma colocación que hace el ItemRenderer con el modelo del arma: la perspectiva y el origen en la esquina
        real.getTransforms().getTransform(ctx).apply(izq, ps);
        ps.translate(-0.5, -0.5, -0.5);
        for (String p : piezas) {
            BakedModel bm = Minecraft.getInstance().getModelManager().getModel(rl(p, ox));
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm, 1F, 1F, 1F, luz, overlay);
        }
        ps.popPose();
    }
}
