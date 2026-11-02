package com.miscelanea.client.arcade;

import com.miscelanea.Miscelanea;
import com.miscelanea.arcade.ArcadeAbrirPacket;
import com.miscelanea.arcade.ArcadeJuegos;
import com.miscelanea.arcade.ArcadeRecordsPacket;
import com.miscelanea.arcade.RecreativaBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cliente de las recreativas: abrir la pantalla, recibir lo que juegan otros, mandos del compañero y el modo demostración. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ArcadeCliente {
    /** Pantalla recibida de otro jugador. */
    public static final class Remota implements AutoCloseable {
        public final Pantalla pantalla = new Pantalla();
        public PantallaTextura tex;
        public long ultimo;

        @Override
        public void close() {
            if (tex != null) tex.cerrar();
            tex = null;
        }
    }

    public static final Pantalla DEMO = new Pantalla();
    public static PantallaTextura demoTex;
    static boolean demoUsada;
    private static int t;
    private static final float[][] ESTRELLAS = new float[80][3];

    static {
        java.util.Random r = new java.util.Random(3);
        for (float[] s : ESTRELLAS) {
            s[0] = r.nextInt(Pantalla.W);
            s[1] = r.nextInt(Pantalla.H);
            s[2] = r.nextInt(3);
        }
    }

    private ArcadeCliente() {}

    public static void abrir(ArcadeAbrirPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.modo == 0) mc.setScreen(new ArcadeScreen(m.pos, m.creditos, m.libre, m.doble, false, "").fijar(m.juego));
        else if (m.modo == 4) mc.setScreen(new ArcadeScreen(m.pos, m.creditos, m.libre, m.doble, true, m.companero, m.propia));
        else if (mc.screen instanceof ArcadeScreen s && s.pos.equals(m.pos)) s.respuesta(m);
    }

    public static void entrada(BlockPos pos, int paso, int mascara) {
        if (Minecraft.getInstance().screen instanceof ArcadeScreen s && s.pos.equals(pos)) s.entradaRemota(paso, mascara);
    }

    public static void frame(BlockPos pos, byte[] datos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof RecreativaBlockEntity be)) return;
        Remota r;
        if (be.pantallaCliente instanceof Remota existente) r = existente;
        else {
            r = new Remota();
            be.pantallaCliente = r;
        }
        if (!Trama.decodificar(datos, r.pantalla)) return;
        if (r.tex == null) r.tex = new PantallaTextura("remota_" + pos.asLong());
        r.tex.subir(r.pantalla);
        r.ultimo = System.currentTimeMillis();
        if (mc.screen instanceof ArcadeScreen s && s.pos.equals(pos)) s.frameRemoto(r);
    }

    public static ListTag tabla(String juego) {
        return ArcadeRecordsPacket.cliente.getList(juego, Tag.TAG_COMPOUND);
    }

    // ------------------------------------------------------------------ modo demostración (lo que se ve cuando nadie juega)
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) return;
        t++;
        if (!demoUsada) return;
        demoUsada = false;
        Pantalla l = DEMO;
        l.font = Minecraft.getInstance().font;
        l.empezar();
        // degradado de noche con estrellas
        for (int y = 0; y < Pantalla.H; y++) l.rect(0, y, Pantalla.W, 1, y < 60 ? 33 : y < 120 ? 34 : 41);
        for (float[] s : ESTRELLAS) {
            s[1] = (s[1] + 0.3F + s[2] * 0.5F) % Pantalla.H;
            l.px((int) s[0], (int) s[1], s[2] == 2 ? 7 : s[2] == 1 ? 39 : 4);
        }
        int fase = (t / 140) % (ArcadeJuegos.IDS.length + 1);
        if (fase == 0) {
            l.rect(0, 52, Pantalla.W, 46, 45);
            l.rect(0, 54, Pantalla.W, 42, 42);
            l.textoC("MISCELÁNEA ARCADE", 128, 62, 20);
            l.textoC(I18n.get("consola.miscelanea.arcade_lema"), 128, 78, 39);
            int x = (t * 2) % (Pantalla.W + 40) - 20;
            l.rect(x, 130, 10, 4, 7);
            l.rect(x + 3, 127, 4, 10, 63);
        } else {
            String id = ArcadeJuegos.IDS[fase - 1];
            l.rect(0, 10, Pantalla.W, 16, 42);
            l.textoC(I18n.get("juego.miscelanea." + id), 128, 14, 20);
            ListTag tb = tabla(id);
            if (tb.isEmpty()) l.textoC(I18n.get("consola.miscelanea.sin_records"), 128, 90, 6);
            for (int i = 0; i < Math.min(10, tb.size()); i++) {
                CompoundTag c = tb.getCompound(i);
                int y = 34 + i * 13;
                int col = i == 0 ? 19 : i < 3 ? 39 : 6;
                l.texto((i + 1) + ".", 30, y, col);
                String n = c.getString("n");
                l.texto(n.length() > 16 ? n.substring(0, 16) : n, 52, y, col);
                l.textoD(Integer.toString(c.getInt("p")), 226, y, col);
            }
        }
        if ((t / 15) % 2 == 0) l.textoC(I18n.get("consola.miscelanea.inserta_ficha"), 128, 176, 19);
        if (demoTex == null) demoTex = new PantallaTextura("demo");
        demoTex.subir(l);
    }
}
