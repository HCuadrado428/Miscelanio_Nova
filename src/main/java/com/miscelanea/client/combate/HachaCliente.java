package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import com.miscelanea.hacha.AccionHacha;
import com.miscelanea.hacha.HachaItem;
import com.miscelanea.hacha.HachaPacket;
import com.miscelanea.hacha.PosturaPacket;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Controles y movimientos de Kratos con el Hacha Leviatán (cliente): combos ligero/pesado, tajo cargado, apuntar y lanzar,
 * llamar el hacha, Frost Awaken, escudo, rúnicos, esquivas y puñetazos. Cámara al hombro (God of War).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class HachaCliente {
    public static final KeyMapping APUNTAR = new KeyMapping("key.miscelanea.apuntar", KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE, LlaveCliente.CATEGORIA);
    public static final KeyMapping ESCUDO = new KeyMapping("key.miscelanea.escudo", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT, LlaveCliente.CATEGORIA);

    private static final Map<Integer, Byte> POSTURAS = new HashMap<>();
    private static byte postura, posturaEnviada;
    private static int etapaLigero, etapaPesado, etapaPuno, runas;
    private static long ultimoLigero, ultimoPesado, libreEn, ultimaEsquiva = -100, pesadoDesde = -1, rDesde = -1;
    private static boolean pendienteLigero;
    private static AccionHacha accion;
    private static long inicioAccion;
    private static Vec3 dirAccion = Vec3.ZERO;

    private HachaCliente() {}

    // ------------------------------------------------------------------------------------------------ estilos
    private static final AnimJugador.Estilo CON_HACHA = new AnimJugador.Estilo() {
        public AnimClip reposo() { return ClipsHacha.IDLE; }
        public AnimClip correr() { return ClipsHacha.CORRER; }
        public AnimClip aire() { return ClipsHacha.AIRE; }
        public AnimClip especial(AbstractClientPlayer p) { return clipPostura(p); }
    };
    private static final AnimJugador.Estilo SIN_HACHA = new AnimJugador.Estilo() {
        public AnimClip reposo() { return ClipsHacha.PUNOS; }
        public AnimClip correr() { return ClipsHacha.CORRER; }
        public AnimClip aire() { return ClipsHacha.PUNOS; }
        public AnimClip especial(AbstractClientPlayer p) { return clipPostura(p); }
    };

    private static AnimClip clipPostura(AbstractClientPlayer p) {
        byte v = p == Minecraft.getInstance().player ? postura : POSTURAS.getOrDefault(p.getId(), (byte) 0);
        return switch (v) {
            case PosturaPacket.APUNTAR -> ClipsHacha.APUNTAR;
            case PosturaPacket.ESCUDO -> ClipsHacha.ESCUDO;
            case PosturaPacket.CARGAR -> ClipsHacha.CARGA;
            case PosturaPacket.LLAMAR -> ClipsHacha.LLAMAR;
            default -> null;
        };
    }

    public static void init() {
        var anim = AnimJugador.ESTILO;
        AnimJugador.ESTILO = p -> p.getMainHandItem().getItem() instanceof HachaItem
                ? (HachaItem.lanzada(p.getMainHandItem()) ? SIN_HACHA : CON_HACHA) : anim.apply(p);
        var cam = CamaraCombate.ESTILO;
        CamaraCombate.ESTILO = p -> p.getMainHandItem().getItem() instanceof HachaItem
                ? (postura == PosturaPacket.APUNTAR ? CamaraCombate.GOD_OF_WAR_APUNTAR : CamaraCombate.GOD_OF_WAR) : cam.apply(p);
    }

    /** Para el escudo del brazo: ¿está este jugador con el escudo subido? */
    public static boolean escudoArriba(AbstractClientPlayer p) {
        byte v = p == Minecraft.getInstance().player ? postura : POSTURAS.getOrDefault(p.getId(), (byte) 0);
        return v == PosturaPacket.ESCUDO;
    }

    public static AnimClip clip(AccionHacha a) {
        return switch (a) {
            case LIGERO1 -> ClipsHacha.LIGERO1;
            case LIGERO2 -> ClipsHacha.LIGERO2;
            case LIGERO3 -> ClipsHacha.LIGERO3;
            case LIGERO4 -> ClipsHacha.LIGERO4;
            case PESADO1 -> ClipsHacha.PESADO1;
            case PESADO2 -> ClipsHacha.PESADO2;
            case CLEAVE -> ClipsHacha.CLEAVE;
            case SALTO -> ClipsHacha.SALTO;
            case LANZAR_LIGERO, LANZAR_PESADO -> ClipsHacha.LANZAR;
            case LLAMAR -> null;
            case ESQUIVA -> ClipsHacha.ESQUIVA;
            case VOLTERETA -> ClipsHacha.VOLTERETA;
            case GOLPE_ESQUIVA -> ClipsHacha.GOLPE_ESQUIVA;
            case PUNO1 -> ClipsHacha.PUNO1;
            case PUNO2 -> ClipsHacha.PUNO2;
            case PUNO3 -> ClipsHacha.PUNO3;
            case RUN_HEL -> ClipsHacha.RUN_HEL;
            case RUN_FRENZY -> ClipsHacha.RUN_FRENZY;
            case RUN_TEMPEST -> ClipsHacha.RUN_TEMPEST;
            case RUN_RIOS -> ClipsHacha.RUN_RIOS;
            case ESCARCHA -> ClipsHacha.ESCARCHA;
            case ATRAPAR -> ClipsHacha.ATRAPAR;
            case PARRY -> ClipsHacha.PARRY;
        };
    }

    /** Del servidor: postura (tipo 0) o acción (tipo 1) de un jugador. */
    public static void deServidor(int id, byte tipo, byte valor) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(id) instanceof AbstractClientPlayer p)) return;
        if (tipo == 0) {
            POSTURAS.put(id, valor);
            return;
        }
        AccionHacha a = AccionHacha.de(valor);
        if (p == mc.player && a != AccionHacha.ATRAPAR && a != AccionHacha.PARRY) return;
        AnimJugador.reproducir(p, clip(a), false);
    }

    private static ItemStack hacha(LocalPlayer p) {
        return p != null && p.getMainHandItem().getItem() instanceof HachaItem ? p.getMainHandItem() : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------------------------------------ entrada
    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ItemStack s = hacha(p);
        if (s.isEmpty()) return;
        boolean fuera = HachaItem.lanzada(s);
        if (e.isPickBlock()) {
            e.setCanceled(true);
            return;
        }
        if (e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            if (postura == PosturaPacket.APUNTAR && !fuera) hacer(p, AccionHacha.LANZAR_LIGERO, null);
            else if (postura == PosturaPacket.ESCUDO && !fuera) runico(p, true);
            else ligero(p, fuera);
        } else if (e.isUseItem()) {
            if (p.isShiftKeyDown()) return;   // Shift + clic derecho: usar bloques (cofres, puertas...)
            e.setCanceled(true);
            e.setSwingHand(false);
            if (fuera) return;
            if (postura == PosturaPacket.APUNTAR) {
                if (pesadoDesde < 0) hacer(p, AccionHacha.LANZAR_PESADO, null);
                pesadoDesde = p.tickCount;   // evita repetir mientras se mantiene
            } else if (postura == PosturaPacket.ESCUDO) {
                if (pesadoDesde < 0) runico(p, false);
                pesadoDesde = p.tickCount;
            } else if (pesadoDesde < 0) {
                pesadoDesde = p.tickCount;   // se decide al soltar: pesado o tajo cargado
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ItemStack s = hacha(p);
        if (s.isEmpty()) {
            if (postura != PosturaPacket.NADA) ponerPostura(PosturaPacket.NADA);
            CamaraCombate.encarar = false;
            accion = null;
            pesadoDesde = -1;
            rDesde = -1;
            while (APUNTAR.consumeClick()) {}
            while (ESCUDO.consumeClick()) {}
            return;
        }
        if (libreEn > p.tickCount + 200) libreEn = 0;   // jugador nuevo (respawn)
        boolean fuera = HachaItem.lanzada(s);
        // --- teclas de la Llave reutilizadas: Z = esquivar, V = fijar, R = llamar / Frost Awaken / cambiar runas
        while (LlaveCliente.CAMBIAR.consumeClick()) esquivar(p);
        while (LlaveCliente.FIJAR.consumeClick()) {
            if (CamaraCombate.fijado() != null) CamaraCombate.fijar(null);
            else CamaraCombate.fijar(LlaveCliente.objetivo(p, 18));
        }
        while (LlaveCliente.COMANDO.consumeClick()) {
            if (fuera) {
                ModNetwork.CHANNEL.sendToServer(new HachaPacket(AccionHacha.LLAMAR, CamaraCombate.yaw(), 0, -1));
                ultimaLlamada = p.tickCount;
            } else if (ESCUDO.isDown()) {
                runas = (runas + 1) % 2;
                p.displayClientMessage(Component.translatable("msg.miscelanea.runas" + runas), true);
            } else if (rDesde < 0) {
                rDesde = p.tickCount;
            }
        }
        // mantener R con el hacha en la mano = Frost Awaken
        if (rDesde >= 0) {
            if (!LlaveCliente.COMANDO.isDown()) rDesde = -1;
            else if (p.tickCount - rDesde >= 12 && !fuera) {
                rDesde = -1;
                hacer(p, AccionHacha.ESCARCHA, null);
            }
        }
        // --- pesado: al soltar el clic derecho (corto = combo pesado, largo = tajo cargado)
        boolean usa = mc.options.keyUse.isDown();
        if (pesadoDesde >= 0 && !usa) {
            long dur = p.tickCount - pesadoDesde;
            pesadoDesde = -1;
            if (postura == PosturaPacket.CARGAR) ponerPostura(PosturaPacket.NADA);
            if (!fuera && !APUNTAR.isDown() && !ESCUDO.isDown()) {
                if (dur >= 14) hacer(p, AccionHacha.CLEAVE, objetivo(p));
                else pesado(p);
            }
        }
        // --- posturas mantenidas
        byte nueva = PosturaPacket.NADA;
        if (!fuera && APUNTAR.isDown()) nueva = PosturaPacket.APUNTAR;
        else if (!fuera && ESCUDO.isDown()) nueva = PosturaPacket.ESCUDO;
        else if (!fuera && pesadoDesde >= 0 && p.tickCount - pesadoDesde >= 6) nueva = PosturaPacket.CARGAR;
        else if (fuera && p.tickCount - ultimaLlamada < 14) nueva = PosturaPacket.LLAMAR;
        if (nueva != postura) ponerPostura(nueva);
        CamaraCombate.encarar = postura == PosturaPacket.APUNTAR || postura == PosturaPacket.ESCUDO || postura == PosturaPacket.CARGAR;
        if (postura == PosturaPacket.CARGAR && p.tickCount % 2 == 0) {
            p.level().addParticle(ParticleTypes.SNOWFLAKE, p.getX() + (p.getRandom().nextDouble() - 0.5), p.getY() + 2.1, p.getZ() + (p.getRandom().nextDouble() - 0.5), 0, -0.02, 0);
        }
        if (HachaItem.escarcha(s, p.level()) && p.tickCount % 3 == 0) {
            Vec3 m = p.position().add(Vec3.directionFromRotation(0, p.getYRot() + 40).scale(0.6)).add(0, 0.8, 0);
            p.level().addParticle(ParticleTypes.SNOWFLAKE, m.x, m.y, m.z, 0, 0.01, 0);
        }
        if (pendienteLigero && p.tickCount >= libreEn) {
            pendienteLigero = false;
            ligero(p, fuera);
        }
        moverDuranteAccion(p);
    }

    private static long ultimaLlamada = -100;

    private static void ponerPostura(byte v) {
        postura = v;
        if (v != posturaEnviada) {
            posturaEnviada = v;
            ModNetwork.CHANNEL.sendToServer(new PosturaPacket(v));
        }
    }

    // ------------------------------------------------------------------------------------------------ acciones
    private static void ligero(LocalPlayer p, boolean fuera) {
        if (p.tickCount < libreEn) {
            if (libreEn - p.tickCount <= 5) pendienteLigero = true;
            return;
        }
        LivingEntity obj = objetivo(p);
        AccionHacha a;
        if (fuera) {
            if (p.tickCount - ultimoLigero > 16) etapaPuno = 0;
            a = etapaPuno == 0 ? AccionHacha.PUNO1 : etapaPuno == 1 ? AccionHacha.PUNO2 : AccionHacha.PUNO3;
            etapaPuno = (etapaPuno + 1) % 3;
        } else if (p.tickCount - ultimaEsquiva <= 7) {
            a = AccionHacha.GOLPE_ESQUIVA;
        } else if (p.isSprinting() && p.onGround()) {
            a = AccionHacha.SALTO;
        } else {
            if (p.tickCount - ultimoLigero > 18) etapaLigero = 0;
            a = switch (etapaLigero) {
                case 0 -> AccionHacha.LIGERO1;
                case 1 -> AccionHacha.LIGERO2;
                case 2 -> AccionHacha.LIGERO3;
                default -> AccionHacha.LIGERO4;
            };
            etapaLigero = (etapaLigero + 1) % 4;
        }
        ultimoLigero = p.tickCount;
        hacer(p, a, obj);
    }

    private static void pesado(LocalPlayer p) {
        if (p.tickCount < libreEn) return;
        if (p.tickCount - ultimoPesado > 22) etapaPesado = 0;
        AccionHacha a = etapaPesado == 0 ? AccionHacha.PESADO1 : AccionHacha.PESADO2;
        etapaPesado = (etapaPesado + 1) % 2;
        ultimoPesado = p.tickCount;
        hacer(p, a, objetivo(p));
    }

    private static void runico(LocalPlayer p, boolean ligero) {
        if (p.tickCount < libreEn) return;
        ItemStack s = hacha(p);
        long cd = s.hasTag() ? s.getTag().getLong(ligero ? HachaItem.CD_LIGERO : HachaItem.CD_PESADO) : 0;
        if (cd > p.level().getGameTime() && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.runa_recargando"), true);
            return;
        }
        AccionHacha a = ligero ? (runas == 0 ? AccionHacha.RUN_HEL : AccionHacha.RUN_TEMPEST)
                : (runas == 0 ? AccionHacha.RUN_FRENZY : AccionHacha.RUN_RIOS);
        hacer(p, a, objetivo(p));
    }

    private static void esquivar(LocalPlayer p) {
        boolean doble = p.tickCount - ultimaEsquiva <= 8;
        if (p.tickCount < libreEn && !doble) return;
        // dirección: hacia donde empujan las teclas (relativo a la cámara); si no, hacia atrás
        var in = p.input;
        float r = CamaraCombate.yaw() * Mth.DEG_TO_RAD;
        double mx = -Mth.sin(r) * in.forwardImpulse + Mth.cos(r) * in.leftImpulse;
        double mz = Mth.cos(r) * in.forwardImpulse + Mth.sin(r) * in.leftImpulse;
        Vec3 d = new Vec3(mx, 0, mz);
        d = d.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, CamaraCombate.yaw()).scale(-1) : d.normalize();
        ultimaEsquiva = p.tickCount;
        dirAccion = d;
        AccionHacha a = doble ? AccionHacha.VOLTERETA : AccionHacha.ESQUIVA;
        AnimJugador.reproducir(p, clip(a), false);
        accion = a;
        inicioAccion = p.tickCount;
        libreEn = p.tickCount + a.recuperacion;
        ModNetwork.CHANNEL.sendToServer(new HachaPacket(a, p.getYRot(), 0, -1));
    }

    private static void hacer(LocalPlayer p, AccionHacha a, LivingEntity obj) {
        float yaw = obj != null ? (float) (Mth.atan2(obj.getZ() - p.getZ(), obj.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F : CamaraCombate.yaw();
        float pitch = obj != null ? 0 : p.getXRot();
        if (a == AccionHacha.LANZAR_LIGERO || a == AccionHacha.LANZAR_PESADO) {
            // se lanza hacia el centro de la pantalla (la retícula)
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            yaw = cam.getYRot();
            pitch = cam.getXRot();
        }
        p.setYRot(yaw);
        p.yBodyRot = yaw;
        p.yHeadRot = yaw;
        boolean seMueve = a == AccionHacha.SALTO || a == AccionHacha.ESQUIVA || a == AccionHacha.VOLTERETA || a == AccionHacha.GOLPE_ESQUIVA;
        CamaraCombate.bloquear(a.recuperacion + 2, seMueve ? 0 : a.recuperacion);
        AnimJugador.reproducir(p, clip(a), false);
        accion = a;
        inicioAccion = p.tickCount;
        dirAccion = Vec3.directionFromRotation(0, yaw);
        libreEn = p.tickCount + a.recuperacion;
        ModNetwork.CHANNEL.sendToServer(new HachaPacket(a, yaw, pitch, obj != null ? obj.getId() : -1));
    }

    private static void moverDuranteAccion(LocalPlayer p) {
        if (accion == null) return;
        long t = p.tickCount - inicioAccion;
        Vec3 v = p.getDeltaMovement();
        switch (accion) {
            case ESQUIVA -> {
                if (t <= 2) p.setDeltaMovement(dirAccion.x * 0.75, Math.max(v.y, 0.05), dirAccion.z * 0.75);
            }
            case VOLTERETA -> {
                if (t <= 8) p.setDeltaMovement(dirAccion.x * 0.55, v.y, dirAccion.z * 0.55);
            }
            case SALTO -> {
                if (t == 1) p.setDeltaMovement(dirAccion.x * 0.7, 0.55, dirAccion.z * 0.7);
                if (t > 1 && t < 9) p.setDeltaMovement(dirAccion.x * 0.55, v.y, dirAccion.z * 0.55);
                p.resetFallDistance();
            }
            case GOLPE_ESQUIVA, LIGERO4, PESADO2 -> {
                if (t >= 3 && t <= 5) p.setDeltaMovement(dirAccion.x * 0.3, v.y, dirAccion.z * 0.3);
            }
            case RUN_FRENZY -> {
                if (t % 8 >= 5 && t % 8 <= 6 && t < 26) p.setDeltaMovement(dirAccion.x * 0.4, v.y, dirAccion.z * 0.4);
            }
            default -> {}
        }
        if (t > 40) accion = null;
    }

    private static LivingEntity objetivo(LocalPlayer p) {
        LivingEntity f = CamaraCombate.fijado();
        if (f != null && f.distanceTo(p) < 8) return f;
        // un poco de "imán": el enemigo cercano que hay delante de la cámara
        Vec3 delante = Vec3.directionFromRotation(0, CamaraCombate.yaw());
        LivingEntity mejor = null;
        double md = 99;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.5),
                e -> e != p && e.isAlive() && !(e instanceof ArmorStand) && !e.isInvisible())) {
            Vec3 d = e.position().subtract(p.position());
            Vec3 h = new Vec3(d.x, 0, d.z);
            if (h.lengthSqr() > 1e-4 && h.normalize().dot(delante) < 0.55) continue;
            double dist = e.distanceTo(p);
            if (dist < md) {
                md = dist;
                mejor = e;
            }
        }
        return mejor;
    }

    /** Llamado al pulsar R con el hacha lanzada (pose de llamar). */
    public static void llamada(LocalPlayer p) {
        ultimaLlamada = p.tickCount;
    }

    // ------------------------------------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ItemStack s = hacha(p);
        if (s.isEmpty() || mc.options.hideGui) return;
        var f = mc.font;
        long ahora = p.level().getGameTime();
        // retícula al apuntar
        if (postura == PosturaPacket.APUNTAR) {
            int cx = w / 2, cy = h / 2;
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2;
                int x = cx + (int) Math.round(Math.cos(a) * 7), y = cy + (int) Math.round(Math.sin(a) * 7);
                g.fill(x, y, x + 1, y + 1, 0xC0A8E0FF);
            }
        }
        // runas (abajo a la derecha)
        int x = w - 132, y = h - 46;
        String[] nombres = runas == 0 ? new String[]{"runa.miscelanea.hel", "runa.miscelanea.frenzy"} : new String[]{"runa.miscelanea.tempest", "runa.miscelanea.rios"};
        String[] cds = {HachaItem.CD_LIGERO, HachaItem.CD_PESADO};
        int[] total = {com.miscelanea.hacha.Leviatan.CD_LIGERO, com.miscelanea.hacha.Leviatan.CD_PESADO};
        for (int i = 0; i < 2; i++) {
            long cd = s.hasTag() ? s.getTag().getLong(cds[i]) - ahora : 0;
            int yy = y + i * 14;
            g.fill(x, yy, x + 124, yy + 12, 0xB0101418);
            if (cd > 0) g.fill(x, yy + 10, x + (int) (124 * (1 - cd / (float) total[i])), yy + 12, 0xFF5AA8E8);
            else g.fill(x, yy + 10, x + 124, yy + 12, 0xFF8FE0FF);
            g.drawString(f, (i == 0 ? "L " : "P ") + Component.translatable(nombres[i]).getString(), x + 3, yy + 2, cd > 0 ? 0xFF7A8A99 : 0xFFE8F4FF, false);
        }
        // Frost Awaken y hacha lanzada
        long esc = s.hasTag() ? s.getTag().getLong(HachaItem.ESCARCHA) - ahora : 0;
        if (esc > 0) {
            g.fill(x, y - 8, x + (int) (124 * esc / (float) com.miscelanea.hacha.Leviatan.ESCARCHA_TICKS), y - 5, 0xFFB8ECFF);
            g.drawString(f, Component.translatable("hud.miscelanea.escarcha"), x, y - 18, 0xFFB8ECFF, true);
        }
        if (HachaItem.lanzada(s)) g.drawString(f, Component.translatable("hud.miscelanea.hacha_lanzada"), w / 2 - 40, h - 60, 0xFFB8ECFF, true);
    }
}
