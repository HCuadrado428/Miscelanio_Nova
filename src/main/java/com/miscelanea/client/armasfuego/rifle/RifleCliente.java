package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.rifle.Rifle;
import com.miscelanea.armasfuego.rifle.RifleAccionPacket;
import com.miscelanea.armasfuego.rifle.RifleItem;
import com.miscelanea.client.movil.MovilCaptura;
import com.miscelanea.client.movil.MovilCliente;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * El rifle en el cliente: el clic izquierdo (gatillo, cerrojo o foto), el culatazo, la respiración (mira quieta), el
 * telémetro del HUD y las fotos con el zoom del catalejo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class RifleCliente {
    private RifleCliente() {}

    /** Ticks seguidos con la mira quieta (con 40 se contiene la respiración). */
    public static int quietos;
    private static float ultYaw, ultPitch;
    private static long tDisparo;
    /** Telémetro: distancia (-1 = nada a tiro) y qué se mira. */
    public static double distancia = -1;
    public static Component objetivo = Component.empty();

    public static boolean firme() {
        return quietos >= Rifle.FIRME_TICKS;
    }

    static boolean conRifle(LocalPlayer p) {
        return p != null && p.getMainHandItem().getItem() instanceof RifleItem;
    }

    // ------------------------------------------------------------------ clics

    /** El clic izquierdo con el rifle no pica bloques ni pega: lo coge {@link #tick}. */
    @SubscribeEvent
    public static void clicIzquierdo(InputEvent.InteractionKeyMappingTriggered e) {
        if (e.isAttack() && conRifle(Minecraft.getInstance().player)) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (!conRifle(p)) {
            quietos = 0;
            distancia = -1;
            return;
        }
        // mientras se usa un objeto el juego tira los clics de ataque: se cogen aquí antes
        if (mc.screen == null && mc.getOverlay() == null) while (mc.options.keyAttack.consumeClick()) clic(p, p.getMainHandItem());
        if (p.isScoping()) {
            float d = Math.abs(p.getYRot() - ultYaw) + Math.abs(p.getXRot() - ultPitch);
            quietos = d < 0.12F ? quietos + 1 : 0;
            if (p.tickCount % 3 == 0) telemetro(mc, p);
        } else {
            quietos = 0;
            distancia = -1;
        }
        ultYaw = p.getYRot();
        ultPitch = p.getXRot();
    }

    /** Lo que hace el clic izquierdo con el rifle en la mano. */
    public static void clic(LocalPlayer p, ItemStack s) {
        if (p.isShiftKeyDown() && p.isScoping()) {
            foto();
            return;
        }
        if (p.isShiftKeyDown() || !Rifle.lista(s)) {
            if (Rifle.cerrojo(s) == 0) ModNetwork.CHANNEL.sendToServer(RifleAccionPacket.de(RifleAccionPacket.CERROJO, 0));
            return;
        }
        ModNetwork.CHANNEL.sendToServer(RifleAccionPacket.disparo(p.getYRot(), p.getXRot(), firme()));
        // culatazo: la vista sube (menos con la mira puesta)
        float sube = p.isScoping() ? 1.6F : 4.5F;
        p.setXRot(Math.max(-90F, p.getXRot() - sube));
        p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5F) * sube * 0.4F);
        ultYaw = p.getYRot();
        ultPitch = p.getXRot();
        quietos = 0;
        tDisparo = Util.getMillis();
    }

    /** Culatazo en primera persona (1 justo al disparar, 0 a los 250 ms). */
    static float retroceso(float pt) {
        long ms = Util.getMillis() - tDisparo;
        if (ms > 250) return 0;
        float t = ms / 250F;
        return (1 - t) * (1 - t);
    }

    // ------------------------------------------------------------------ telémetro

    private static void telemetro(Minecraft mc, LocalPlayer p) {
        double alcance = 256;
        Vec3 ojo = p.getEyePosition();
        Vec3 dir = p.getViewVector(1F);
        Vec3 fin = ojo.add(dir.scale(alcance));
        BlockHitResult bh = mc.level.clip(new ClipContext(ojo, fin, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, p));
        Vec3 hasta = bh.getType() == HitResult.Type.MISS ? fin : bh.getLocation();
        AABB caja = p.getBoundingBox().expandTowards(hasta.subtract(ojo)).inflate(1);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, ojo, hasta, caja, en -> !en.isSpectator() && en.isPickable(), ojo.distanceToSqr(hasta));
        if (eh != null) {
            Entity en = eh.getEntity();
            distancia = ojo.distanceTo(eh.getLocation());
            objetivo = en.getDisplayName();
        } else if (bh.getType() != HitResult.Type.MISS) {
            distancia = ojo.distanceTo(bh.getLocation());
            objetivo = mc.level.getBlockState(bh.getBlockPos()).getBlock().getName();
        } else {
            distancia = -1;
            objetivo = Component.empty();
        }
    }

    // ------------------------------------------------------------------ fotos con la mira

    private static int fotoFotogramas;
    private static long ultFoto;
    private static float zoomAntes;
    private static int filtroAntes;
    /** Para la prueba: cuántas fotos se han sacado y dónde ha ido la última. */
    public static int fotosHechas;
    public static String ultimaFoto = "";

    public static void foto() {
        if (Util.getMillis() - ultFoto < 1000 || fotoFotogramas > 0) return;
        ultFoto = Util.getMillis();
        zoomAntes = MovilCaptura.zoom;
        filtroAntes = MovilCaptura.filtro;
        fotoFotogramas = 8;
    }

    /** La cámara del móvil copia el centro de la pantalla (sin el HUD ni la mira) al dibujar el mundo: se le pide unos fotogramas. */
    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || fotoFotogramas <= 0) return;
        MovilCaptura.zoom = 1;
        MovilCaptura.filtro = 0;
        MovilCaptura.pedir(true);
        fotoFotogramas--;
        // los primeros fotogramas pueden tener una imagen vieja: se espera a que se copie la de ahora
        if (fotoFotogramas > 4) return;
        if (MovilCaptura.lista() || fotoFotogramas == 0) {
            fotoFotogramas = 0;
            byte[] jpg = MovilCaptura.foto(MovilCliente.maxBytesFoto);
            MovilCaptura.zoom = zoomAntes;
            MovilCaptura.filtro = filtroAntes;
            guardarFoto(jpg);
        }
    }

    private static void guardarFoto(byte[] jpg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (jpg == null) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.foto_error").withStyle(ChatFormatting.RED), true);
            return;
        }
        p.playSound(MovilRegistro.OBTURADOR.get(), 0.8F, 1.0F);
        fotosHechas++;
        if (p.getInventory().hasAnyMatching(MovilRegistro::esMovil)) {
            // con el móvil encima, a su galería (el servidor la guarda como cualquier foto del móvil)
            MovilRed.alServidor(MovilRed.C_FOTO_SUBIR, b -> b.writeByteArray(jpg));
            ultimaFoto = "movil";
            p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.foto_movil").withStyle(ChatFormatting.GREEN), true);
            return;
        }
        // sin móvil, a la carpeta de capturas del juego
        try {
            File dir = new File(mc.gameDirectory, "screenshots");
            dir.mkdirs();
            String nombre = "catalejo_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".jpg";
            Files.write(new File(dir, nombre).toPath(), jpg);
            ultimaFoto = nombre;
            p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.foto_capturas", nombre).withStyle(ChatFormatting.GREEN), true);
        } catch (Exception ex) {
            Miscelanea.LOGGER.warn("[Rifle] no se pudo guardar la foto", ex);
            p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.foto_error").withStyle(ChatFormatting.RED), true);
        }
    }
}
