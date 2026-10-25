package com.miscelanea.client.programa;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.CadaverBlock;
import com.miscelanea.carcel.CadaverBlockEntity;
import com.miscelanea.carcel.CarroChivato;
import com.miscelanea.carcel.GafasItem;
import com.miscelanea.carcel.MonitorBlock;
import com.miscelanea.carcel.Simbolo;
import com.miscelanea.programa.GeneradorBlock;
import com.miscelanea.programa.Programa;
import com.miscelanea.programa.PruebaEstadoPacket;
import com.miscelanea.programa.PruebaFinPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Interfaz de la prueba al estilo The Outlast Trials: quita la de Minecraft y pinta el objetivo,
 * la vida, los objetos, la batería de las gafas, la mirada fija (símbolos), el % de la ejecución,
 * la manivela del generador y las pantallas de inicio y final.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ProgramaHud {
    private static final ResourceLocation SIMBOLOS = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/programa_simbolos.png");
    private static final int MIRAR = 24;

    private static boolean activo;
    private static int etapa, a, b, manivela, muertes;
    private static long inicio;
    private static long introDesde = -1;
    private static PruebaFinPacket fin;
    private static long finDesde;
    private static final List<Integer> vistos = new ArrayList<>();
    private static final Map<BlockPos, Integer> cadaveres = new HashMap<>();
    private static BlockPos mirando;
    private static int mirarTicks, simboloMirado = -1;
    private static int ultimaEtapa = -1;
    private static long cambioEtapa;

    private static final Set<ResourceLocation> OCULTAR = Set.of(
            VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaGuiOverlay.HOTBAR.id(), VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaGuiOverlay.JUMP_BAR.id(), VanillaGuiOverlay.ITEM_NAME.id(),
            VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id());

    private ProgramaHud() {}

    public static boolean enPrograma() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == Programa.DIM;
    }

    // ------------------------------------------------------------------ red

    public static void estado(PruebaEstadoPacket p) {
        if (!p.activo) {
            activo = false;
            return;
        }
        if (p.intro || !activo) {
            vistos.clear();
            cadaveres.clear();
        }
        if (p.intro) introDesde = System.currentTimeMillis();
        activo = true;
        etapa = p.etapa;
        a = p.a;
        b = p.b;
        manivela = p.manivela;
        inicio = p.inicio;
        muertes = p.muertes;
        if (etapa != ultimaEtapa) {
            ultimaEtapa = etapa;
            cambioEtapa = System.currentTimeMillis();
        }
    }

    public static void fin(PruebaFinPacket p) {
        fin = p;
        finDesde = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ tick: mirada fija y ranuras

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !enPrograma()) return;
        // Solo 4 huecos de objetos, como en el juego
        var inv = mc.player.getInventory();
        if (inv.selected > 3) inv.selected = inv.selected >= 6 ? 3 : 0;

        HitResult hr = mc.player.pick(16.0, 1.0F, false);
        BlockPos pos = hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : null;
        int sim = -1;
        if (pos != null) {
            BlockState s = mc.level.getBlockState(pos);
            if (s.getBlock() instanceof MonitorBlock) sim = s.getValue(Simbolo.PROPIEDAD);
            else if (s.getBlock() instanceof CadaverBlock && mc.level.getBlockEntity(pos) instanceof CadaverBlockEntity be) sim = be.simbolo();
        }
        if (sim < 0) {
            mirando = null;
            mirarTicks = 0;
            return;
        }
        if (!pos.equals(mirando)) {
            mirando = pos.immutable();
            boolean ya = cadaveres.containsKey(mirando);
            mirarTicks = ya ? MIRAR : 0;
        }
        simboloMirado = sim;
        if (mirarTicks < MIRAR && ++mirarTicks == MIRAR) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 0.7F);
            if (mc.level.getBlockState(pos).getBlock() instanceof MonitorBlock) {
                if (!vistos.contains(sim)) vistos.add(sim);
            }
            cadaveres.put(mirando, sim);
        }
    }

    @SubscribeEvent
    public static void ocultar(RenderGuiOverlayEvent.Pre e) {
        if (enPrograma() && OCULTAR.contains(e.getOverlay().id())) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ dibujo

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long ahora = System.currentTimeMillis();
        Font f = mc.font;
        RenderSystem.enableBlend();

        if (enPrograma() && activo && !mc.options.hideGui) {
            vineta(g, mc, w, h);
            objetivo(g, f, mc, w, h, ahora);
            vida(g, mc, w, h);
            objetos(g, mc, f, w, h);
            bateria(g, mc, w, h);
            mirada(g, f, w, h);
            ejecucion(g, f, mc, w, h);
            generador(g, f, mc, w, h, partial);
        }
        if (introDesde > 0 && ahora - introDesde < 7000) intro(g, f, w, h, ahora - introDesde);
        if (fin != null && ahora - finDesde < 9000) pantallaFin(g, f, w, h, ahora - finDesde);
        RenderSystem.disableBlend();
    }

    private static void texto(GuiGraphics g, Font f, Component c, float x, float y, float escala, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(escala, escala, 1);
        g.drawString(f, c, 0, 0, color, true);
        g.pose().popPose();
    }

    private static void centrado(GuiGraphics g, Font f, Component c, float cx, float y, float escala, int color) {
        texto(g, f, c, cx - f.width(c) * escala / 2, y, escala, color);
    }

    private static int alfa(int color, float a) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private static void simbolo(GuiGraphics g, int sim, int x, int y, int tam, float a) {
        RenderSystem.setShaderColor(1, 1, 1, a);
        g.blit(SIMBOLOS, x, y, tam, tam, sim * 32, 0, 32, 32, 128, 32);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void objetivo(GuiGraphics g, Font f, Minecraft mc, int w, int h, long ahora) {
        int x = 16, y = 16;
        float aparece = Mth.clamp((ahora - cambioEtapa) / 600.0F, 0, 1);
        g.fill(x - 6, y - 2, x - 4, y + 34, alfa(0xFFFFFF, 0.8F * aparece));
        texto(g, f, Component.translatable("programa.miscelanea.objetivo"), x, y, 0.7F, alfa(0xA8A8A8, aparece));
        String k = "programa.miscelanea.obj." + etapa;
        texto(g, f, Component.translatable(k), x, y + 9, 1.1F, alfa(0xFFFFFF, aparece));
        Component sub = switch (etapa) {
            case 4 -> Component.translatable("programa.miscelanea.gen." + a, manivela, GeneradorBlock.ACIERTOS);
            case 5, 6 -> Component.translatable("programa.miscelanea.rejas", a, b);
            default -> Component.translatable(k + ".sub");
        };
        texto(g, f, sub, x, y + 23, 0.8F, alfa(0xC8C8C8, aparece));
        // símbolos vistos en las teles
        int sx = x;
        for (int sim : vistos) {
            g.fill(sx - 1, y + 35, sx + 15, y + 51, 0x70000000);
            simbolo(g, sim, sx, y + 36, 14, 0.95F);
            sx += 19;
        }
    }

    private static void vineta(GuiGraphics g, Minecraft mc, int w, int h) {
        float vida = mc.player.getHealth() / mc.player.getMaxHealth();
        if (vida > 0.4F) return;
        float pulso = 0.5F + 0.5F * Mth.sin(mc.player.tickCount * 0.3F);
        float a = (0.4F - vida) / 0.4F * (0.35F + 0.25F * pulso);
        for (int i = 0; i < 10; i++) {
            int m = i * Math.max(3, w / 120);
            int c = alfa(0x8A0000, a * (1 - i / 10.0F) * 0.5F);
            g.fill(0, 0, w, m, c);
            g.fill(0, h - m, w, h, c);
            g.fill(0, 0, m, h, c);
            g.fill(w - m, 0, w, h, c);
        }
    }

    private static void vida(GuiGraphics g, Minecraft mc, int w, int h) {
        int x = 16, y = h - 22, largo = 120;
        float v = mc.player.getHealth() / mc.player.getMaxHealth();
        g.fill(x - 1, y - 1, x + largo + 1, y + 5, 0x90000000);
        int col = v < 0.3F && (mc.player.tickCount / 6) % 2 == 0 ? 0xFFD03030 : 0xFFECECEC;
        g.fill(x, y, x + (int) (largo * v), y + 4, col);
        for (int i = 1; i < 4; i++) g.fill(x + largo * i / 4, y, x + largo * i / 4 + 1, y + 4, 0xC0000000);   // 4 segmentos
    }

    private static void objetos(GuiGraphics g, Minecraft mc, Font f, int w, int h) {
        var inv = mc.player.getInventory();
        int x = 16, y = h - 50;
        for (int i = 0; i < 4; i++) {
            int sx = x + i * 26;
            boolean sel = inv.selected == i;
            g.fill(sx, y, sx + 22, y + 22, sel ? 0xA0202020 : 0x80000000);
            int borde = sel ? 0xFFFFFFFF : 0x40FFFFFF;
            g.fill(sx, y, sx + 22, y + 1, borde);
            g.fill(sx, y + 21, sx + 22, y + 22, borde);
            g.fill(sx, y, sx + 1, y + 22, borde);
            g.fill(sx + 21, y, sx + 22, y + 22, borde);
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) {
                g.renderItem(st, sx + 3, y + 3);
                g.renderItemDecorations(f, st, sx + 3, y + 3);
            }
        }
    }

    private static void bateria(GuiGraphics g, Minecraft mc, int w, int h) {
        ItemStack casco = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(casco.getItem() instanceof GafasItem)) return;
        int x = w - 104, y = h - 22, largo = 80;
        boolean on = GafasItem.encendidas(casco);
        // icono de las gafas: dos oculares
        int ic = on ? 0xFF8CFF6A : 0xFFB0B0B0;
        g.fill(x - 18, y - 3, x - 11, y + 4, ic);
        g.fill(x - 9, y - 3, x - 2, y + 4, ic);
        g.fill(x - 16, y - 1, x - 13, y + 2, 0xFF101010);
        g.fill(x - 7, y - 1, x - 4, y + 2, 0xFF101010);
        int bt = GafasItem.bateria(casco);
        g.fill(x - 1, y - 1, x + largo + 1, y + 5, 0x90000000);
        boolean baja = bt < GafasItem.BATERIA_MAX / 5;
        int col = baja && (mc.player.tickCount / 8) % 2 == 0 ? 0xFFFF5040 : (on ? 0xFF8CFF6A : 0xFFDADADA);
        g.fill(x, y, x + largo * bt / GafasItem.BATERIA_MAX, y + 4, col);
    }

    private static void mirada(GuiGraphics g, Font f, int w, int h) {
        if (mirando == null || mirarTicks <= 0) return;
        int cx = w / 2, cy = h / 2;
        float p = Math.min(1, mirarTicks / (float) MIRAR);
        // anillo de progreso alrededor del punto de mira
        int n = 24;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + i * Math.PI * 2 / n;
            int px = cx + (int) Math.round(Math.cos(ang) * 11), py = cy + (int) Math.round(Math.sin(ang) * 11);
            g.fill(px - 1, py - 1, px + 1, py + 1, i < n * p ? 0xFFFFFFFF : 0x50FFFFFF);
        }
        if (p >= 1 && simboloMirado >= 0) {
            g.fill(cx - 17, cy + 18, cx + 17, cy + 52, 0x80000000);
            simbolo(g, simboloMirado, cx - 14, cy + 21, 28, 1.0F);
        }
    }

    private static void ejecucion(GuiGraphics g, Font f, Minecraft mc, int w, int h) {
        if (etapa != 6) return;
        CarroChivato carro = null;
        double mejor = 80 * 80;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof CarroChivato c && c.distanceToSqr(mc.player) < mejor) {
                mejor = c.distanceToSqr(mc.player);
                carro = c;
            }
        }
        float p = carro != null ? carro.progreso() : 0;
        int cx = w / 2;
        centrado(g, f, Component.literal((int) p + "%"), cx, 14, 3.0F, 0xFFFFFFFF);
        g.fill(cx - 80, 46, cx + 80, 49, 0x90000000);
        g.fill(cx - 80, 46, cx - 80 + (int) (160 * p / 100), 49, 0xFF7FC8FF);
    }

    private static void generador(GuiGraphics g, Font f, Minecraft mc, int w, int h, float partial) {
        if (etapa != 4 || a != 2) return;
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bh) || hr.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockState(bh.getBlockPos()).getBlock() instanceof GeneradorBlock)) return;
        int cx = w / 2, y = (int) (h * 0.66F), largo = 180;
        int x0 = cx - largo / 2;
        g.fill(x0 - 2, y - 2, x0 + largo + 2, y + 10, 0xA0000000);
        g.fill(x0, y, x0 + largo, y + 8, 0xFF2A2A2A);
        float[] z = GeneradorBlock.zona(manivela);
        g.fill(x0 + (int) (largo * z[0]), y, x0 + (int) (largo * z[1]), y + 8, 0xFF3FBF4A);
        long t = mc.level.getGameTime();
        float ag = Mth.lerp(partial, GeneradorBlock.aguja(t), GeneradorBlock.aguja(t + 1));
        int nx = x0 + (int) (largo * ag);
        g.fill(nx - 1, y - 4, nx + 1, y + 12, 0xFFFF3030);
        for (int i = 0; i < GeneradorBlock.ACIERTOS; i++) {
            int px = cx - 16 + i * 14;
            g.fill(px, y + 15, px + 8, y + 19, i < manivela ? 0xFF3FBF4A : 0x60FFFFFF);
        }
    }

    private static void intro(GuiGraphics g, Font f, int w, int h, long ms) {
        float a = ms < 1500 ? 1 : 1 - Mth.clamp((ms - 1500) / 1200.0F, 0, 1);
        g.fill(0, 0, w, h, alfa(0x000000, a));
        float t = ms < 600 ? ms / 600.0F : (ms > 5500 ? 1 - (ms - 5500) / 1500.0F : 1);
        t = Mth.clamp(t, 0, 1);
        int cy = h / 2 - 30;
        centrado(g, f, Component.translatable("programa.miscelanea.intro1"), w / 2F, cy, 1.0F, alfa(0xB0B0B0, t));
        centrado(g, f, Component.translatable("programa.miscelanea.intro2"), w / 2F, cy + 14, 3.2F, alfa(0xFFFFFF, t));
        g.fill(w / 2 - 90, cy + 47, w / 2 + 90, cy + 48, alfa(0xFFFFFF, t * 0.6F));
        centrado(g, f, Component.translatable("programa.miscelanea.intro3"), w / 2F, cy + 54, 1.0F, alfa(0xC8C8C8, t));
    }

    private static void pantallaFin(GuiGraphics g, Font f, int w, int h, long ms) {
        float a = Mth.clamp(ms / 800.0F, 0, 1) * (ms > 8000 ? 1 - (ms - 8000) / 1000.0F : 1);
        g.fill(0, 0, w, h, alfa(0x000000, a * 0.85F));
        int cy = h / 2 - 60;
        centrado(g, f, Component.translatable(fin.exito ? "programa.miscelanea.completada" : "programa.miscelanea.fallida"),
                w / 2F, cy, 2.0F, alfa(fin.exito ? 0xFFFFFF : 0xE03030, a));
        centrado(g, f, Component.literal(fin.nota), w / 2F, cy + 28, 6.0F, alfa(fin.exito ? 0xFFFFFF : 0xE03030, a));
        String tiempo = String.format("%d:%02d", fin.segundos / 60, fin.segundos % 60);
        centrado(g, f, Component.translatable("programa.miscelanea.resumen", tiempo, fin.muertes), w / 2F, cy + 88, 1.0F, alfa(0xC0C0C0, a));
    }
}
