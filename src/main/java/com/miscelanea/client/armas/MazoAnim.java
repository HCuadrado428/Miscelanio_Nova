package com.miscelanea.client.armas;

import com.miscelanea.armas.MazoItem;
import com.miscelanea.client.combate.AnimClip;
import com.miscelanea.client.combate.AnimJugador;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Posturas del jugador con el Gambito: al cargar el lanzamiento levanta el mazo por encima de la cabeza y en el
 * picado cae con el mazo en alto, las piernas recogidas y el cuerpo inclinado hacia delante. Solo mientras lo usa.
 */
public final class MazoAnim {
    private MazoAnim() {}

    private static final float N = Float.NaN;

    // cuerpo rot(3) pos(3), brazo dcho(3), brazo izdo(3), pierna dcha(3), pierna izda(3), ítem(3), cabeza(3)
    static final AnimClip CARGAR = new AnimClip("MAZO_CARGAR", 16, true, -1, -1,
            new float[]{0F, 8F, 16F}, new int[]{1, 1, 1},
            new float[][]{
                    {-0.18F, 0.25F, 0F, 0F, 0F, 0F, -2.85F, 0.15F, 0.25F, -2.55F, -0.15F, -0.35F, 0.25F, 0F, 0.12F, -0.35F, 0F, -0.12F, N, N, N, N, N, N},
                    {-0.22F, 0.28F, 0F, 0F, 0F, 0F, -2.95F, 0.18F, 0.28F, -2.65F, -0.15F, -0.38F, 0.25F, 0F, 0.12F, -0.35F, 0F, -0.12F, N, N, N, N, N, N},
                    {-0.18F, 0.25F, 0F, 0F, 0F, 0F, -2.85F, 0.15F, 0.25F, -2.55F, -0.15F, -0.35F, 0.25F, 0F, 0.12F, -0.35F, 0F, -0.12F, N, N, N, N, N, N}});
    static final AnimClip PICADO = new AnimClip("MAZO_PICADO", 6, true, -1, -1,
            new float[]{0F, 3F, 6F}, new int[]{1, 1, 1},
            new float[][]{
                    {0.35F, 0F, 0F, 0F, 0F, 0F, -3.05F, 0F, 0.12F, -2.9F, 0F, -0.2F, -0.9F, 0F, 0.1F, -0.4F, 0F, -0.1F, N, N, N, N, N, N},
                    {0.38F, 0F, 0F, 0F, 0F, 0F, -3.1F, 0F, 0.1F, -2.95F, 0F, -0.18F, -0.95F, 0F, 0.1F, -0.45F, 0F, -0.1F, N, N, N, N, N, N},
                    {0.35F, 0F, 0F, 0F, 0F, 0F, -3.05F, 0F, 0.12F, -2.9F, 0F, -0.2F, -0.9F, 0F, 0.1F, -0.4F, 0F, -0.1F, N, N, N, N, N, N}});

    private static final AnimJugador.Estilo ESTILO = new AnimJugador.Estilo() {
        @Override public AnimClip reposo() { return CARGAR; }
        @Override public AnimClip correr() { return CARGAR; }
        @Override public AnimClip aire() { return PICADO; }
        @Override public AnimClip especial(AbstractClientPlayer p) {
            ItemStack s = p.getUseItem();
            return s.hasTag() && s.getTag().getBoolean(MazoItem.PICADO) ? PICADO : CARGAR;
        }
    };

    public static void init() {
        var anterior = AnimJugador.ESTILO;
        AnimJugador.ESTILO = p -> p.isUsingItem() && p.getUseItem().getItem() instanceof MazoItem ? ESTILO : anterior.apply(p);
    }
}
