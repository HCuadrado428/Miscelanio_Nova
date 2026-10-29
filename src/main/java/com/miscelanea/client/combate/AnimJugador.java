package com.miscelanea.client.combate;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

import java.util.WeakHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Capa de animación de combate (playerAnimator): mientras el jugador lleva un arma con "estilo" (Llave Espada, Hacha...)
 * todas sus animaciones cambian: postura de reposo, correr, en el aire y los ataques (clips).
 * Mezcla suave entre clips y entre posturas.
 */
public class AnimJugador implements IAnimation {
    // Por objeto jugador (no por id: al crearse el jugador su id aún no es el definitivo, se cambia después con setId)
    private static final Map<AbstractClientPlayer, AnimJugador> POR_JUGADOR = new WeakHashMap<>();

    /** Lo que define cada arma: qué clips usar como postura. */
    public interface Estilo {
        AnimClip reposo();
        AnimClip correr();
        AnimClip aire();
    }

    /** Devuelve el estilo del arma que lleva el jugador en la mano (o null). Lo rellena el cliente de cada arma. */
    public static Function<AbstractClientPlayer, Estilo> ESTILO = p -> null;

    private final AbstractClientPlayer player;
    private Estilo estilo;
    private AnimClip clip, postura;
    private float inicioClip;
    private final float[] actual = new float[AnimClip.N], desde = new float[AnimClip.N], tmp = new float[AnimClip.N];
    private float inicioMezcla = -100, durMezcla = 3;
    private boolean zurdo;
    private boolean aireCombate;

    public AnimJugador(AbstractClientPlayer player) {
        this.player = player;
        POR_JUGADOR.put(player, this);
    }

    public static AnimJugador de(AbstractClientPlayer p) {
        return POR_JUGADOR.get(p);
    }

    /** Reproduce un clip de ataque/acción. */
    public static void reproducir(AbstractClientPlayer p, AnimClip c, boolean aereo) {
        AnimJugador a = de(p);
        if (a == null || c == null) return;
        a.empezarMezcla(2.0F);
        a.clip = c;
        a.inicioClip = p.tickCount + mc().getFrameTime();
        if (aereo) a.aireCombate = true;
    }

    public static boolean ocupado(AbstractClientPlayer p) {
        AnimJugador a = de(p);
        return a != null && a.clip != null;
    }

    private static net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getInstance();
    }

    private void empezarMezcla(float dur) {
        System.arraycopy(actual, 0, desde, 0, AnimClip.N);
        // giros de cuerpo de vueltas completas: se quedan en el equivalente más cercano
        for (int j = 0; j < 3; j++) desde[j] = Mth.wrapDegrees(desde[j] * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        inicioMezcla = player.tickCount + mc().getFrameTime();
        durMezcla = dur;
    }

    @Override
    public boolean isActive() {
        estilo = ESTILO.apply(player);
        if (estilo == null) {
            clip = null;
            postura = null;
            return false;
        }
        return true;
    }

    @Override
    public void setupAnim(float tickDelta) {
        if (estilo == null) return;
        float ahora = player.tickCount + tickDelta;
        zurdo = player.getMainArm() == HumanoidArm.LEFT;
        if (player.onGround() || player.isInWater()) aireCombate = false;
        if (clip != null && ahora - inicioClip >= clip.dur) {
            clip = null;
            empezarMezcla(3.0F);
        }
        if (clip != null) {
            clip.muestra(ahora - inicioClip, tmp);
        } else {
            double vel = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
            AnimClip nueva = aireCombate && !player.onGround() ? estilo.aire() : vel > 0.09 ? estilo.correr() : estilo.reposo();
            if (nueva != postura) {
                if (postura != null) empezarMezcla(4.0F);
                postura = nueva;
            }
            float t = ahora;
            if (postura == estilo.correr()) {
                // al ritmo de las piernas de vanilla (periodo de 2π/0.6662 en "posición de paso")
                float pos = player.walkAnimation.position(tickDelta);
                t = pos / 9.4314F * postura.dur;
            }
            postura.muestra(t, tmp);
        }
        float k = Mth.clamp((ahora - inicioMezcla) / durMezcla, 0, 1);
        k = k * k * (3 - 2 * k);
        for (int j = 0; j < AnimClip.N; j++) {
            float a = desde[j], b = tmp[j];
            if (k >= 1 || Float.isNaN(a) || Float.isNaN(b)) actual[j] = b;
            else actual[j] = a + (b - a) * k;
        }
    }

    private Vec3f tres(int i, Vec3f base) {
        float x = actual[i], y = actual[i + 1], z = actual[i + 2];
        if (Float.isNaN(x)) return base;
        return new Vec3f(x, y, z);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        // jugadores zurdos: se intercambian los lados y se invierten los giros laterales
        String m = modelName;
        if (zurdo) {
            if (m.startsWith("right")) m = "left" + m.substring(5);
            else if (m.startsWith("left")) m = "right" + m.substring(4);
        }
        int i = switch (m) {
            case "rightArm" -> 6;
            case "leftArm" -> 9;
            case "rightLeg" -> 12;
            case "leftLeg" -> 15;
            case "rightItem" -> 18;
            default -> -1;
        };
        if (modelName.equals("body")) {
            if (type == TransformType.ROTATION) {
                Vec3f r = new Vec3f(nz(actual[0]), nz(actual[1]), nz(actual[2]));
                return zurdo ? new Vec3f(r.getX(), -r.getY(), -r.getZ()) : r;
            }
            if (type == TransformType.POSITION) {
                Vec3f p = new Vec3f(nz(actual[3]), nz(actual[4]), nz(actual[5]));
                return zurdo ? new Vec3f(-p.getX(), p.getY(), p.getZ()) : p;
            }
            return value0;
        }
        if (i < 0 || type != TransformType.ROTATION) return value0;
        Vec3f r = tres(i, value0);
        if (r == value0) return value0;
        return zurdo ? new Vec3f(r.getX(), -r.getY(), -r.getZ()) : r;
    }

    private static float nz(float v) {
        return Float.isNaN(v) ? 0 : v;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        return FirstPersonMode.NONE;
    }
}
