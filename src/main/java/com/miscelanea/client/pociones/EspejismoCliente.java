package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Espejismo: a un jugador que imita a otro se le dibuja con la piel (y el modelo fino o ancho) y el nombre del imitado.
 * Se cancela el dibujo normal y se vuelve a dibujar la MISMA entidad (con todas sus animaciones, armadura y objetos)
 * con un PlayerRenderer propio que solo cambia la textura y la chapa del nombre. La piel sale de la lista de jugadores
 * conectados (PlayerInfo); si el imitado ya no está, de la caché de pieles (o la piel por defecto de su UUID).
 * Tu propia vista no cambia; el chat y la lista del Tab tampoco.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EspejismoCliente {
    private EspejismoCliente() {}

    private static Espejo ancho, fino;
    private static boolean dentro, fallo;
    /** Lo que se está dibujando ahora mismo (lo lee el renderer). */
    private static ResourceLocation pielActual;
    private static String nombreActual = "";

    /** PlayerRenderer que pinta otra piel y otro nombre; todo lo demás (capas, animaciones) es el de siempre. */
    private static final class Espejo extends PlayerRenderer {
        Espejo(EntityRendererProvider.Context c, boolean fino) {
            super(c, fino);
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractClientPlayer p) {
            return pielActual != null ? pielActual : super.getTextureLocation(p);
        }

        @Override
        protected void renderNameTag(AbstractClientPlayer p, Component nombre, PoseStack ps, MultiBufferSource buf, int luz) {
            super.renderNameTag(p, nombreActual.isEmpty() ? nombre : Component.literal(nombreActual), ps, buf, luz);
        }
    }

    private static Espejo renderer(boolean esFino) {
        if (fallo) return null;
        if (ancho == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                EntityRendererProvider.Context c = new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(), mc.getItemRenderer(),
                        mc.getBlockRenderer(), mc.getEntityRenderDispatcher().getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font);
                ancho = new Espejo(c, false);
                fino = new Espejo(c, true);
            } catch (RuntimeException ex) {
                Miscelanea.LOGGER.warn("Espejismo: no se pudo crear el renderer de jugador", ex);
                fallo = true;
                return null;
            }
        }
        return esFino ? fino : ancho;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dibujar(RenderPlayerEvent.Pre e) {
        if (dentro) return;
        Player p = e.getEntity();
        if (!(p instanceof AbstractClientPlayer acp)) return;
        Estados.Estado s = Estados.espejismo(p);
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener con = mc.getConnection();
        if (con == null) return;
        ResourceLocation piel;
        boolean esFino;
        PlayerInfo info = con.getPlayerInfo(s.espejo);
        if (info != null) {
            piel = info.getSkinLocation();
            esFino = "slim".equals(info.getModelName());
        } else {
            // ya no está conectado: la caché de pieles (si nunca se vio, la piel por defecto de su UUID)
            piel = mc.getSkinManager().getInsecureSkinLocation(new GameProfile(s.espejo, s.nombre.isEmpty() ? null : s.nombre));
            esFino = "slim".equals(DefaultPlayerSkin.getSkinModelName(s.espejo));
        }
        Espejo r = renderer(esFino);
        if (r == null) return;
        e.setCanceled(true);
        dentro = true;
        pielActual = piel;
        nombreActual = s.nombre;
        try {
            float yaw = Mth.lerp(e.getPartialTick(), p.yRotO, p.getYRot());
            r.render(acp, yaw, e.getPartialTick(), e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
        } catch (RuntimeException ex) {
            Miscelanea.LOGGER.warn("Espejismo: fallo al dibujar; se deja de imitar", ex);
            fallo = true;
        } finally {
            dentro = false;
            pielActual = null;
            nombreActual = "";
        }
    }

    /** Para las pruebas: el UUID que imita una entidad (null si ninguno). */
    public static UUID imita(Player p) {
        Estados.Estado s = Estados.espejismo(p);
        return s == null ? null : s.espejo;
    }
}
