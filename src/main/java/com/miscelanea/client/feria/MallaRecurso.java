package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mallas de la feria que no son cajas: la cápsula de la noria (huevo abierto), la cesta del pulpo, adornos del tiovivo...
 * Vienen ya hechas (cuadriláteros con normales suaves) en assets/miscelanea/mallas/feria/*.bin, generadas con un script,
 * y aquí se leen y se pasan por cualquier VertexConsumer (normalmente la {@link MallaFeria.Grabadora}, para hornearlas una
 * vez y luego solo emitirlas cada fotograma).
 * <p>Formato (big endian): int 'MFE1', short nº de materiales; por material: UTF textura (dentro de block/feria; '#' se
 * cambia por el número de variante de color), byte luz (1 = bombillas: se emiten aparte, a plena luz y teñidas), int nº de
 * cuadriláteros; por cuadrilátero: short etiqueta (nº de bombilla) y 4 vértices de: 3 short posición (/512, en bloques),
 * 2 short uv (/1024, en píxeles 0..16 del sprite), 3 byte normal (/127) y 1 byte sombra (/255, oclusión ya horneada).
 */
final class MallaRecurso {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAGIA = 0x4D464531;

    private record Material(String tex, boolean luz, short[] etiquetas, float[] v) {}

    private static final Map<String, List<Material>> DATOS = new HashMap<>();
    private static final Map<String, MallaFeria> HORNEADAS = new HashMap<>();
    private static BakedModel ref;
    private static int generacion;

    private MallaRecurso() {}

    /** Cambia cada vez que se recargan los recursos (cambian las uv del atlas y quizá los propios ficheros). */
    static int generacion() {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(FeriaPiezas.BOMBILLA);
        if (m != ref) {
            ref = m;
            DATOS.clear();
            HORNEADAS.clear();
            generacion++;
        }
        return generacion;
    }

    private static List<Material> datos(String nombre) {
        generacion();
        return DATOS.computeIfAbsent(nombre, MallaRecurso::leer);
    }

    private static List<Material> leer(String nombre) {
        ResourceLocation rl = new ResourceLocation(Miscelanea.MOD_ID, "mallas/feria/" + nombre + ".bin");
        List<Material> l = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Minecraft.getInstance().getResourceManager().open(rl)))) {
            if (in.readInt() != MAGIA) throw new IOException("no es una malla de la feria");
            int nm = in.readShort();
            for (int i = 0; i < nm; i++) {
                String tex = in.readUTF();
                boolean luz = in.readByte() != 0;
                int nq = in.readInt();
                short[] et = new short[nq];
                float[] v = new float[nq * 4 * 9];
                int k = 0;
                for (int q = 0; q < nq; q++) {
                    et[q] = in.readShort();
                    for (int j = 0; j < 4; j++) {
                        v[k++] = in.readShort() / 512F;
                        v[k++] = in.readShort() / 512F;
                        v[k++] = in.readShort() / 512F;
                        v[k++] = in.readShort() / 1024F;
                        v[k++] = in.readShort() / 1024F;
                        v[k++] = in.readByte() / 127F;
                        v[k++] = in.readByte() / 127F;
                        v[k++] = in.readByte() / 127F;
                        v[k++] = in.readUnsignedByte() / 255F;
                    }
                }
                l.add(new Material(tex, luz, et, v));
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("No se pudo leer la malla de la feria {}", rl, e);
        }
        return l;
    }

    /**
     * Pasa la malla {@code nombre} (variante de color {@code variante}) por {@code vc} con la pose dada. {@code luces}:
     * solo las bombillas (con su etiqueta en la grabadora) o solo lo demás. La luz se pone al emitir lo horneado.
     */
    static void dibujar(PoseStack ps, VertexConsumer vc, String nombre, int variante, boolean luces) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        String var = Integer.toString(variante);
        for (Material mat : datos(nombre)) {
            if (mat.luz != luces) continue;
            TextureAtlasSprite sp = atlas.getSprite(new ResourceLocation(Miscelanea.MOD_ID, "block/feria/" + mat.tex.replace("#", var)));
            float[] v = mat.v;
            for (int q = 0; q < mat.etiquetas.length; q++) {
                if (vc instanceof MallaFeria.Grabadora g) g.etiqueta = mat.etiquetas[q];
                for (int j = 0; j < 4; j++) {
                    int b = (q * 4 + j) * 9;
                    float s = v[b + 8];
                    vc.vertex(m, v[b], v[b + 1], v[b + 2]).color(s, s, s, 1F).uv(sp.getU(v[b + 3]), sp.getV(v[b + 4]))
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nm, v[b + 5], v[b + 6], v[b + 7]).endVertex();
                }
            }
        }
    }

    /** La malla ya horneada (sin pose: en sus propias coordenadas), guardada hasta que se recarguen los recursos. */
    static MallaFeria horneada(String nombre, int variante, boolean luces) {
        generacion();
        return HORNEADAS.computeIfAbsent(nombre + "/" + variante + "/" + luces, k -> {
            MallaFeria.Grabadora g = new MallaFeria.Grabadora();
            dibujar(new PoseStack(), g, nombre, variante, luces);
            return g.terminar();
        });
    }
}
