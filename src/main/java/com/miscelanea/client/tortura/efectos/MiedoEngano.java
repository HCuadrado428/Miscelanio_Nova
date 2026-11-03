package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Toxina del miedo: lo que ves de ti mismo miente. Todo es solo de dibujo: el inventario de verdad no se toca nunca.
 * <ul>
 *   <li>ARMADURA: mientras dura el miedo no la ves en ningún sitio: las casillas de armadura del inventario salen
 *       vacías (con su silueta de casilla vacía, sin tooltip), el muñeco del inventario y tu propio cuerpo en tercera
 *       persona van sin ella (capa de armadura y cabeza) y la barra de armadura del HUD no está.</li>
 *   <li>OBJETOS (etapa 2+): de vez en cuando, algunas casillas del inventario o de la barra rápida enseñan otra cosa un
 *       momento (carne podrida, huesos, calaveras, ojos de araña, tu propia cabeza) o una cantidad que no es.</li>
 *   <li>TOOLTIPS (etapa 2+): a veces el nombre del objeto dice otra cosa ("NO PUEDES ESCAPAR", "está detrás de ti"...).</li>
 *   <li>VIDA Y HAMBRE (etapa 2+): de vez en cuando la barra de vida parpadea (despacio) y se queda casi vacía, con los
 *       corazones temblando y el latido desbocado; el hambre también baja. A los pocos segundos vuelve a ser la de verdad.</li>
 *   <li>CHAT (etapa 2+): mensajes falsos de tus amigos conectados (o de ti mismo, o de Herobrine), Herobrine entra (y
 *       sale) de la partida, susurros con letras ofuscadas y, en la etapa 3, muertes falsas de otros jugadores.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoEngano {
    private MiedoEngano() {}

    private static final ResourceLocation ICONOS = new ResourceLocation("textures/gui/icons.png");
    private static final int ARMADURA_DESDE = 36, ARMADURA_HASTA = 39;

    private record Falso(ItemStack st, int hasta) {}

    private static final Map<Integer, Falso> FALSOS = new HashMap<>();
    private static int proxCambio = -1, proxChat = -1, proxVida = -1, vidaDesde = -1, vidaHasta = -1, vidaFalsa, herobrineSale = -1;
    private static boolean herobrineDentro;

    static void limpiar() {
        FALSOS.clear();
        proxCambio = proxChat = proxVida = -1;
        vidaDesde = vidaHasta = -1;
        if (herobrineDentro && herobrineSale >= 0) {
            // si entró, sale al acabarse (si no, se quedaría "dentro" para siempre)
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
                mc.gui.getChat().addMessage(Component.translatable("multiplayer.player.left", "Herobrine").withStyle(ChatFormatting.YELLOW));
        }
        herobrineDentro = false;
        herobrineSale = -1;
    }

    // ------------------------------------------------------------------ armadura (desde los mixins de las capas)
    /** ¿Se esconde la armadura de esta entidad? Solo la tuya, mientras tienes miedo. */
    public static boolean ocultarArmadura(@Nullable LivingEntity e) {
        return e != null && MiedoCliente.etapa() > 0 && e == Minecraft.getInstance().player;
    }

    private static boolean esMiInventario(@Nullable Slot s) {
        Minecraft mc = Minecraft.getInstance();
        return s != null && mc.player != null && s.container == mc.player.getInventory();
    }

    /** Lo que se dibuja en una casilla de una pantalla de inventario (mixin de AbstractContainerScreen.renderSlot). */
    public static ItemStack verSlot(Slot s) {
        ItemStack real = s.getItem();
        if (MiedoCliente.etapa() <= 0 || !esMiInventario(s)) return real;
        int i = s.getSlotIndex();
        if (i >= ARMADURA_DESDE && i <= ARMADURA_HASTA) return ItemStack.EMPTY;
        if (real.isEmpty()) return real;
        Falso f = FALSOS.get(i);
        return f != null ? f.st() : real;
    }

    /** El tooltip de una casilla de armadura "vacía" no sale (mixin de AbstractContainerScreen.renderTooltip). */
    public static boolean ocultarTooltip(@Nullable Slot s) {
        if (MiedoCliente.etapa() <= 0 || !esMiInventario(s)) return false;
        int i = s.getSlotIndex();
        return i >= ARMADURA_DESDE && i <= ARMADURA_HASTA;
    }

    /** Lo que se dibuja en la barra rápida (mixin de Gui.renderSlot): se reconoce la casilla por identidad. */
    public static ItemStack verHotbar(ItemStack st) {
        if (MiedoCliente.etapa() < 2 || FALSOS.isEmpty() || st.isEmpty()) return st;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return st;
        Inventory inv = p.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inv.items.get(i) == st) {
                Falso f = FALSOS.get(i);
                return f != null ? f.st() : st;
            }
        }
        return st;
    }

    private static ItemStack falso(RandomSource r, ItemStack real, LocalPlayer p) {
        int n = Math.max(1, Math.min(real.getCount(), 64));
        switch (r.nextInt(8)) {
            case 0:
                return new ItemStack(Items.ROTTEN_FLESH, n);
            case 1:
                return new ItemStack(Items.BONE, n);
            case 2:
                return new ItemStack(Items.SKELETON_SKULL);
            case 3:
                return new ItemStack(Items.WITHER_SKELETON_SKULL);
            case 4:
                return new ItemStack(Items.SPIDER_EYE, n);
            case 5: {
                // tu propia cabeza
                ItemStack c = new ItemStack(Items.PLAYER_HEAD);
                c.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), p.getGameProfile()));
                return c;
            }
            default: {
                // el mismo objeto con una cantidad que no es (o, si no se apila, carne podrida)
                int max = real.getMaxStackSize();
                if (max <= 1) return new ItemStack(Items.ROTTEN_FLESH);
                int otra = 1 + r.nextInt(max);
                if (otra == real.getCount()) otra = otra == 1 ? 2 : otra - 1;
                return real.copyWithCount(otra);
            }
        }
    }

    // ------------------------------------------------------------------ tick (lo llama MiedoCliente con el miedo puesto)
    static void tick(Minecraft mc, LocalPlayer p, RandomSource r, int etapa) {
        int reloj = MiedoCliente.reloj();
        FALSOS.values().removeIf(f -> f.hasta() <= reloj);
        if (herobrineSale >= 0 && reloj >= herobrineSale) {
            herobrineSale = -1;
            herobrineDentro = false;
            mc.gui.getChat().addMessage(Component.translatable("multiplayer.player.left", "Herobrine").withStyle(ChatFormatting.YELLOW));
        }
        if (etapa < 2) return;
        float ritmo = MiedoCliente.ritmo();
        // ---- objetos que no son lo que son
        boolean pantalla = mc.screen instanceof AbstractContainerScreen<?>;
        if (proxCambio < 0) proxCambio = reloj + 40;
        if (reloj >= proxCambio) {
            proxCambio = reloj + (int) ((pantalla ? 25 + r.nextInt(45) : 90 + r.nextInt(120)) / ritmo);
            int n = etapa >= 3 ? 1 + r.nextInt(3) : 1;
            Inventory inv = p.getInventory();
            for (int k = 0; k < n; k++) {
                int i = pantalla ? r.nextInt(inv.items.size()) : r.nextInt(Inventory.getSelectionSize());
                ItemStack real = inv.items.get(i);
                if (real.isEmpty()) continue;
                FALSOS.put(i, new Falso(falso(r, real, p), reloj + 14 + r.nextInt(30)));
            }
        }
        // ---- vida falsa (solo con vida de sobra: con poca, mentir sobre ella sería peligroso de verdad)
        if (proxVida < 0) proxVida = reloj + 100 + r.nextInt(200);
        if (reloj > vidaHasta && reloj >= proxVida && p.getHealth() > 8F) {
            vidaDesde = reloj;
            vidaHasta = reloj + 70 + r.nextInt(60);
            vidaFalsa = 1 + r.nextInt(4);
            proxVida = reloj + (int) ((320 + r.nextInt(320)) / ritmo);
        }
        // ---- chat
        if (proxChat < 0) proxChat = reloj + 160 + r.nextInt(240);
        if (reloj >= proxChat) {
            proxChat = reloj + (int) ((480 + r.nextInt(480)) / ritmo);
            chatFalso(mc, p, r, etapa);
        }
    }

    // ------------------------------------------------------------------ chat
    private static String basura(RandomSource r) {
        StringBuilder sb = new StringBuilder();
        int n = 3 + r.nextInt(6);
        for (int i = 0; i < n; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }

    private static void chatFalso(Minecraft mc, LocalPlayer p, RandomSource r, int etapa) {
        List<String> otros = new ArrayList<>();
        ClientPacketListener con = mc.getConnection();
        if (con != null)
            for (PlayerInfo pi : con.getOnlinePlayers())
                if (!pi.getProfile().getId().equals(p.getUUID())) otros.add(pi.getProfile().getName());
        String yo = p.getGameProfile().getName();
        String alguien = !otros.isEmpty() ? otros.get(r.nextInt(otros.size())) : r.nextBoolean() ? yo : "Herobrine";
        ChatComponent chat = mc.gui.getChat();
        int tipo = r.nextInt(etapa >= 3 ? 6 : 4);
        switch (tipo) {
            case 0, 1 -> chat.addMessage(Component.translatable("chat.type.text", alguien,
                    Component.translatable("miscelanea.miedo.chat." + (1 + r.nextInt(8)))));
            case 2 -> {
                // un susurro del "sistema", con trozos ofuscados
                MutableComponent m = Component.empty()
                        .append(Component.literal(basura(r)).withStyle(ChatFormatting.OBFUSCATED))
                        .append(" ")
                        .append(Component.translatable("miscelanea.miedo.susurro." + (1 + r.nextInt(6))))
                        .append(" ")
                        .append(Component.literal(basura(r)).withStyle(ChatFormatting.OBFUSCATED))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
                chat.addMessage(m);
                MiedoCliente.sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), MiedoCliente.alrededor(p, r, 0.6, 1.2, 0F, 360F), 0.7F, 0.8F);
            }
            case 3 -> {
                if (!herobrineDentro) {
                    herobrineDentro = true;
                    herobrineSale = MiedoCliente.reloj() + 300 + r.nextInt(400);
                    chat.addMessage(Component.translatable("multiplayer.player.joined", "Herobrine").withStyle(ChatFormatting.YELLOW));
                } else chat.addMessage(Component.translatable("chat.type.text", "Herobrine",
                        Component.translatable("miscelanea.miedo.chat." + (1 + r.nextInt(8)))));
            }
            default -> {
                // la muerte de otro (o la tuya, si no hay nadie más)
                String victima = !otros.isEmpty() ? otros.get(r.nextInt(otros.size())) : yo;
                Component c = switch (r.nextInt(3)) {
                    case 0 -> Component.translatable("death.attack.mob", victima, Component.translatable("entity.minecraft.warden"));
                    case 1 -> Component.translatable("death.fell.accident.generic", victima);
                    default -> Component.translatable("death.attack.generic", victima);
                };
                chat.addMessage(c);
            }
        }
    }

    // ------------------------------------------------------------------ tooltips
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent e) {
        if (MiedoCliente.etapa() < 2 || e.getToolTip().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getEntity() != mc.player) return;
        // estable mientras miras el mismo objeto (cambia cada 2,5 s): nada de texto que tiemble a cada fotograma
        int h = System.identityHashCode(e.getItemStack()) * 31 + (MiedoCliente.reloj() / 50) * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        if (Math.floorMod(h, MiedoCliente.etapa() >= 3 ? 3 : 5) != 0) return;
        int n = 1 + Math.floorMod(h >>> 4, 8);
        MutableComponent linea = Component.translatable("miscelanea.miedo.tooltip." + n).withStyle(ChatFormatting.DARK_RED);
        if (Math.floorMod(h >>> 9, 2) == 0) e.getToolTip().set(0, linea.withStyle(ChatFormatting.BOLD));
        else e.getToolTip().add(linea.withStyle(ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ HUD: armadura fuera, vida y hambre falsas
    static boolean vidaFalsaVisible() {
        int reloj = MiedoCliente.reloj();
        if (MiedoCliente.etapa() < 2 || vidaDesde < 0 || reloj > vidaHasta) return false;
        int t = reloj - vidaDesde;
        // primero parpadea entre la de verdad y la falsa (cada 7 ticks: 1,4 Hz) y luego se queda
        return t >= 28 || (t / 7) % 2 == 0;
    }

    private static int hambreFalsa(int real) {
        int t = MiedoCliente.reloj() - vidaDesde;
        return Math.max(0, real - 4 - Math.max(0, t - 20) / 8);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Pre e) {
        if (MiedoCliente.etapa() <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !(mc.gui instanceof ForgeGui gui)) return;
        ResourceLocation id = e.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())) {
            e.setCanceled(true);
            return;
        }
        if (!gui.shouldDrawSurvivalElements() || !vidaFalsaVisible()) return;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) && p.getMaxHealth() <= 20F && p.getAbsorptionAmount() <= 0F) {
            e.setCanceled(true);
            corazones(gui, e.getGuiGraphics(), p, w, h);
        } else if (id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) && !(p.getVehicle() instanceof LivingEntity)) {
            e.setCanceled(true);
            comida(gui, e.getGuiGraphics(), p, w, h);
        }
    }

    /** Los corazones de vanilla (icons.png), pero con la vida falsa y temblando como con la vida baja. */
    private static void corazones(ForgeGui gui, GuiGraphics g, LocalPlayer p, int w, int h) {
        gui.setupOverlayRenderState(true, false);
        int left = w / 2 - 91, top = h - gui.leftHeight;
        gui.leftHeight += 10;
        boolean hardcore = p.level().getLevelData().isHardcore();
        int v = hardcore ? 45 : 0;
        int contenedores = Mth.clamp(Mth.ceil(p.getMaxHealth() / 2F), 1, 10);
        RandomSource r = RandomSource.create(MiedoCliente.reloj() * 312871L);
        for (int i = 0; i < contenedores; i++) {
            int x = left + i * 8, y = top + r.nextInt(2);
            g.blit(ICONOS, x, y, 16, v, 9, 9);
            if (i * 2 + 1 < vidaFalsa) g.blit(ICONOS, x, y, 52, v, 9, 9);
            else if (i * 2 + 1 == vidaFalsa) g.blit(ICONOS, x, y, 61, v, 9, 9);
        }
    }

    private static void comida(ForgeGui gui, GuiGraphics g, LocalPlayer p, int w, int h) {
        gui.setupOverlayRenderState(true, false);
        int right = w / 2 + 91, top = h - gui.rightHeight;
        gui.rightHeight += 10;
        int nivel = hambreFalsa(p.getFoodData().getFoodLevel());
        RandomSource r = RandomSource.create(MiedoCliente.reloj() * 312871L + 7);
        for (int i = 0; i < 10; i++) {
            int idx = i * 2 + 1;
            int x = right - i * 8 - 9, y = top;
            if (nivel <= 6 && MiedoCliente.reloj() % (nivel * 3 + 1) == 0) y = top + (r.nextInt(3) - 1);
            g.blit(ICONOS, x, y, 16, 27, 9, 9);
            if (idx < nivel) g.blit(ICONOS, x, y, 52, 27, 9, 9);
            else if (idx == nivel) g.blit(ICONOS, x, y, 61, 27, 9, 9);
        }
    }
}
