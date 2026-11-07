package com.miscelanea.client.baile.videoclip;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Tipos de dibujo del videoclip: luz que suma (focos, baldosas, destellos) sin escribir profundidad. */
final class TiposVideoclip extends RenderType {
    private TiposVideoclip(String n, VertexFormat f, VertexFormat.Mode m, int t, boolean a, boolean b, Runnable s, Runnable c) {
        super(n, f, m, t, a, b, s, c);
    }

    static final RenderType BRILLO = create("miscelanea_videoclip_brillo", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16,
            false, true, CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));

    /** Color sólido (la bola de espejos), con profundidad. */
    static final RenderType OPACO = create("miscelanea_videoclip_opaco", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 15,
            false, false, CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).createCompositeState(false));
}
