package com.miscelanea.client.arcade;

import com.miscelanea.Miscelanea;
import com.miscelanea.arcade.ArcadeAbrirPacket;
import com.miscelanea.arcade.ArcadeJuegos;
import com.miscelanea.arcade.ArcadeRecordsPacket;
import com.miscelanea.arcade.RecreativaBlockEntity;
import com.miscelanea.client.consola.AstroPatrulla;
import com.miscelanea.client.consola.Lcd;
import com.miscelanea.client.consola.LcdTextura;
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

/** Cliente de las recreativas: abrir la pantalla, recibir lo que juegan otros y el modo demostración. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ArcadeCliente {
    /** Pantalla recibida de otro jugador. */
    public static final class Remota implements AutoCloseable {
        public final Lcd lcd = new Lcd();
        public LcdTextura tex;
        public long ultimo;

        @Override
        public void close() {
            if (tex != null) tex.cerrar();
            tex = null;
        }
    }

    public static final Lcd DEMO = new Lcd();
    public static LcdTextura demoTex;
    static boolean demoUsada;
    private static int t;
    private static final float[][] ESTRELLAS = new float[50][3];

    static {
        java.util.Random r = new java.util.Random(3);
        for (float[] s : ESTRELLAS) {
            s[0] = r.nextInt(Lcd.W);
            s[1] = r.nextInt(Lcd.H);
            s[2] = r.nextInt(3);
        }
    }

    private ArcadeCliente() {}

    public static void abrir(ArcadeAbrirPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.modo == 0) mc.setScreen(new ArcadeScreen(m.pos, m.creditos, m.libre));
        else if (mc.screen instanceof ArcadeScreen s) s.respuesta(m);
    }

    public static void frame(BlockPos pos, byte[] datos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof RecreativaBlockEntity be)) return;
        if (!(be.pantallaCliente instanceof Remota r)) {
            r = new Remota();
            be.pantallaCliente = r;
        }
        if (!Trama.decodificar(datos, r.lcd)) return;
        if (r.tex == null) r.tex = new LcdTextura("arcade_" + pos.asLong());
        r.tex.subir(r.lcd);
        r.ultimo = System.currentTimeMillis();
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
        Lcd l = DEMO;
        l.font = Minecraft.getInstance().font;
        l.paleta = AstroPatrulla.PALETA;
        l.empezar();
        l.rect(0, 0, Lcd.W, Lcd.H, 0);
        for (float[] s : ESTRELLAS) {
            s[1] = (s[1] + 0.4F + s[2] * 0.6F) % Lcd.H;
            l.px((int) s[0], (int) s[1], s[2] == 2 ? 4 : s[2] == 1 ? 5 : 6);
        }
        int fase = (t / 140) % (ArcadeJuegos.IDS.length + 1);
        if (fase == 0) {
            l.rect(0, 30, Lcd.W, 34, 12);
            l.rect(0, 32, Lcd.W, 30, 1);
            l.textoC("MISCELÁNEA", 80, 37, 13);
            l.textoC("ARCADE", 80, 49, 9);
            // nave de adorno cruzando
            int x = (t * 2) % (Lcd.W + 40) - 20;
            l.rect(x, 90, 8, 3, 4);
            l.rect(x + 2, 88, 4, 7, 3);
        } else {
            String id = ArcadeJuegos.IDS[fase - 1];
            l.rect(0, 6, Lcd.W, 13, 12);
            l.textoC(I18n.get("juego.miscelanea." + id), 80, 9, 15);
            ListTag tb = tabla(id);
            if (tb.isEmpty()) l.textoC(I18n.get("consola.miscelanea.sin_records"), 80, 60, 5);
            for (int i = 0; i < Math.min(8, tb.size()); i++) {
                CompoundTag c = tb.getCompound(i);
                int y = 26 + i * 11;
                int col = i == 0 ? 9 : i < 3 ? 3 : 4;
                l.texto((i + 1) + ".", 10, y, col);
                String n = c.getString("n");
                l.texto(n.length() > 12 ? n.substring(0, 12) : n, 26, y, col);
                l.textoD(Integer.toString(c.getInt("p")), 152, y, col);
            }
        }
        if ((t / 15) % 2 == 0) l.textoC(I18n.get("consola.miscelanea.inserta_ficha"), 80, 124, 9);
        if (demoTex == null) demoTex = new LcdTextura("arcade_demo");
        demoTex.subir(l);
    }
}
