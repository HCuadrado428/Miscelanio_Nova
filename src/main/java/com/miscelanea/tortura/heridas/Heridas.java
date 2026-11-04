package com.miscelanea.tortura.heridas;

import com.miscelanea.Miscelanea;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "Heridas en la skin": daño visible (al estilo Mortal Kombat) y extremidades tullidas (al estilo Fallout).
 * <p>
 * Servidor: al recibir daño un jugador se decide qué parte del cuerpo se lleva el golpe (por dónde le dio el
 * atacante o el proyectil) y qué tipo de herida deja (según la fuente del daño). Se guardan en los datos
 * persistentes del jugador, se curan solas con el tiempo (más deprisa con regeneración, durmiendo o vendadas)
 * y se sincronizan con él y con quien le está viendo para dibujarlas sobre su skin.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Heridas {
    public static final String CLAVE = "MiscelaneaHeridas";
    private static final UUID VELOCIDAD_ID = UUID.fromString("6b0f6c4e-2a8e-4f0e-9a51-6d1e2c7b9a01");
    private static final UUID ATAQUE_ID = UUID.fromString("6b0f6c4e-2a8e-4f0e-9a51-6d1e2c7b9a02");
    /** Para que el fuego, los cactus o las espinas no llenen el cuerpo de heridas tick a tick. */
    private static final Map<UUID, Long> ULTIMA_LENTA = new HashMap<>();

    private Heridas() {}

    // ------------------------------------------------------------------ datos

    public static boolean activo(Level level) {
        if (level == null) return false;
        return HeridasRed.REGLA == null || level.getGameRules().getBoolean(HeridasRed.REGLA);
    }

    public static HeridasDatos leer(Player p) {
        CompoundTag pd = p.getPersistentData();
        return pd.contains(CLAVE) ? HeridasDatos.cargar(pd.getCompound(CLAVE)) : new HeridasDatos();
    }

    public static void guardar(Player p, HeridasDatos d) {
        if (d.vacio()) p.getPersistentData().remove(CLAVE);
        else p.getPersistentData().put(CLAVE, d.guardar());
    }

    /** Guarda, aplica los efectos y avisa a él y a quien le ve. */
    public static void actualizar(ServerPlayer p, HeridasDatos d) {
        guardar(p, d);
        efectos(p, d);
        enviar(p, d);
    }

    public static void enviar(ServerPlayer p, HeridasDatos d) {
        if (p instanceof FakePlayer || p.connection == null) return;
        HeridasRed.CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new HeridasPacket(p.getId(), d));
    }

    public static void curarTodo(ServerPlayer p) {
        actualizar(p, new HeridasDatos());
    }

    /** Añade una herida respetando el máximo por parte: si ya no caben, la más floja de esa parte se agrava. */
    public static void agregar(HeridasDatos d, Herida h) {
        if (d.cuantas(h.parte) < h.parte.maxHeridas() && d.lista.size() < HeridasDatos.MAX_TOTAL) {
            d.lista.add(h);
            return;
        }
        Herida objetivo = null;
        for (Herida o : d.lista) {
            if (o.parte != h.parte) continue;
            if (objetivo == null || (o.tipo == h.tipo && objetivo.tipo != h.tipo) || (o.tipo == h.tipo) == (objetivo.tipo == h.tipo) && o.peso() < objetivo.peso())
                objetivo = o;
        }
        if (objetivo == null) return;
        if (objetivo.tipo != h.tipo && h.gravedad >= objetivo.gravedad) {
            objetivo.tipo = h.tipo;
            objetivo.especial = h.especial;
        }
        objetivo.gravedad = Math.min(3, Math.max(objetivo.gravedad, h.gravedad) + (objetivo.tipo == h.tipo ? 1 : 0));
        if (objetivo.tipo == Tipo.RASGUNO && objetivo.gravedad >= 2 && h.tipo == Tipo.CORTE) objetivo.tipo = Tipo.CORTE;
        objetivo.total = Math.max(objetivo.total, h.total);
        objetivo.restante = Math.max(objetivo.restante, h.total);
    }

    public static int duracion(Tipo t, int g, RandomSource r) {
        int d;
        if (t == Tipo.RASGUNO) d = 1200 + r.nextInt(1200);                // 1-2 min
        else if (g <= 1) d = 2400 + r.nextInt(1200);                      // 2-3 min
        else if (g == 2) d = 4800 + r.nextInt(2400);                      // 4-6 min
        else d = 7200 + r.nextInt(4800);                                  // 6-10 min
        return t == Tipo.QUEMADURA ? d * 5 / 4 : d;
    }

    public static Herida nueva(Player p, Parte parte, Tipo tipo, int g, int cara, int fx, int fy, boolean especial) {
        RandomSource r = p.getRandom();
        return new Herida(parte, tipo, g, cara, fx, fy, r.nextInt(), especial, duracion(tipo, g, r));
    }

    /** Herida en un sitio cualquiera de la parte (cara de delante la mitad de las veces). */
    public static Herida alAzar(Player p, Parte parte, Tipo tipo, int g, boolean especial) {
        RandomSource r = p.getRandom();
        int cara = caraAlAzar(parte, r);
        return nueva(p, parte, tipo, g, cara, r.nextInt(256), 30 + r.nextInt(196), especial);
    }

    private static int caraAlAzar(Parte parte, RandomSource r) {
        int k = r.nextInt(8);
        if (k < 4) return Herida.FRENTE;
        if (k < 6) return Herida.ESPALDA;
        if (parte.esExtremidad()) return parte.derecha() ? Herida.DERECHA : Herida.IZQUIERDA;   // la cara de fuera
        return k == 6 ? Herida.DERECHA : Herida.IZQUIERDA;
    }

    /** Vendar: quita la peor herida y pone una venda en su parte (anula su efecto y acelera el resto). */
    public static Parte vendar(ServerPlayer p) {
        HeridasDatos d = leer(p);
        Herida peor = d.peor();
        if (peor == null) return null;
        d.lista.remove(peor);
        d.vendas[peor.parte.ordinal()] = HeridasDatos.DURACION_VENDA;
        actualizar(p, d);
        return peor.parte;
    }

    // ------------------------------------------------------------------ al recibir daño

    @SubscribeEvent
    public static void alRecibirDano(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0.2F || p.isSpectator() || p instanceof FakePlayer) return;
        if (!activo(p.level())) return;
        HeridasDatos d = leer(p);
        int antes = d.lista.size();
        int sumaAntes = firma(d);
        try {
            heridasPorDano(p, e.getSource(), e.getAmount(), d);
        } catch (RuntimeException ex) {
            Miscelanea.LOGGER.debug("Heridas: no se pudo calcular la herida", ex);
            return;
        }
        if (d.lista.size() != antes || firma(d) != sumaAntes) actualizar(p, d);
    }

    private static int firma(HeridasDatos d) {
        int h = d.lista.size();
        for (Herida w : d.lista) h = h * 31 + w.gravedad * 7 + w.tipo.ordinal() + w.restante;
        return h;
    }

    private static int gravedad(float dano) {
        return dano < 4F ? 1 : dano < 8F ? 2 : 3;
    }

    private static boolean lenta(Player p, int ticks) {
        long ahora = p.level().getGameTime();
        Long ult = ULTIMA_LENTA.get(p.getUUID());
        if (ult != null && ahora - ult < ticks && ahora >= ult) return false;
        ULTIMA_LENTA.put(p.getUUID(), ahora);
        return true;
    }

    private static void heridasPorDano(ServerPlayer p, DamageSource s, float dano, HeridasDatos d) {
        RandomSource r = p.getRandom();
        int g = gravedad(dano);
        // --- mordisco de Dream: los colmillos en un lado del cuello (abajo de la cabeza, hacia delante), siempre el
        //     mismo lado para el mismo mordedor; el desangrado que deja no hace herida nueva
        if (s.is(com.miscelanea.dream.Mordiscos.DANO)) {
            Entity mordedor = s.getEntity();
            boolean der = mordedor != null ? (mordedor.getUUID().hashCode() & 1) == 0 : r.nextBoolean();
            int fx = der ? 165 + r.nextInt(80) : 10 + r.nextInt(80);
            agregar(d, nueva(p, Parte.CABEZA, Tipo.CORTE, Math.max(2, g), der ? Herida.DERECHA : Herida.IZQUIERDA, fx, 200 + r.nextInt(50), false));
            return;
        }
        if (s.is(com.miscelanea.dream.DesangradoEfecto.DANO)) return;
        // --- caídas: las dos piernas, por abajo
        if (s.is(DamageTypeTags.IS_FALL) && !s.is(DamageTypes.STALAGMITE)) {
            int gp = gravedad(dano / 1.5F);
            for (Parte pie : new Parte[]{Parte.PIERNA_DER, Parte.PIERNA_IZQ}) {
                if (dano < 2F && r.nextBoolean()) continue;
                agregar(d, nueva(p, pie, Tipo.MORATON, gp, r.nextInt(3) == 0 ? (pie.derecha() ? Herida.DERECHA : Herida.IZQUIERDA) : Herida.FRENTE,
                        r.nextInt(256), 150 + r.nextInt(100), false));
            }
            if (dano >= 10F) agregar(d, alAzar(p, r.nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ, Tipo.CORTE, 2, false));
            return;
        }
        // --- explosiones: varias partes, mezcla de golpes, quemaduras y metralla
        if (s.is(DamageTypeTags.IS_EXPLOSION)) {
            int n = 2 + (dano > 8F ? 1 : 0) + (dano > 14F ? 1 : 0);
            Vec3 desde = s.getSourcePosition();
            int ga = gravedad(dano / 1.6F);
            for (int i = 0; i < n; i++) {
                Parte parte = Parte.V[r.nextInt(Parte.V.length)];
                float t = r.nextFloat();
                Tipo tipo = t < 0.5F ? Tipo.MORATON : t < 0.85F ? Tipo.QUEMADURA : Tipo.CORTE;
                Herida h = alAzar(p, parte, tipo, ga, false);
                if (desde != null) h.cara = caraDesde(p, desde, parte);
                agregar(d, h);
            }
            return;
        }
        // --- rayos: quemadura en helecho (figura de Lichtenberg) en el pecho y en un brazo
        if (s.is(DamageTypeTags.IS_LIGHTNING)) {
            agregar(d, nueva(p, Parte.TORSO, Tipo.QUEMADURA, Math.max(2, g), Herida.FRENTE, 80 + r.nextInt(96), 60 + r.nextInt(100), true));
            agregar(d, alAzar(p, r.nextBoolean() ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, Tipo.QUEMADURA, Math.max(1, g - 1), true));
            return;
        }
        // --- lava y suelo de magma: por los pies
        if (s.is(DamageTypes.LAVA) || s.is(DamageTypes.HOT_FLOOR)) {
            if (!lenta(p, 40)) return;
            int gl = s.is(DamageTypes.LAVA) ? Math.max(2, g) : 1;
            for (Parte pie : new Parte[]{Parte.PIERNA_DER, Parte.PIERNA_IZQ})
                if (gl > 1 || r.nextBoolean())
                    agregar(d, nueva(p, pie, Tipo.QUEMADURA, gl, caraAlAzar(pie, r), r.nextInt(256), 140 + r.nextInt(110), false));
            if (s.is(DamageTypes.LAVA) && r.nextInt(3) == 0) agregar(d, alAzar(p, Parte.TORSO, Tipo.QUEMADURA, 1, false));
            return;
        }
        // --- fuego (arder, hogueras, bolas de fuego...)
        if (s.is(DamageTypeTags.IS_FIRE)) {
            Entity directo = s.getDirectEntity();
            if (directo != null && directo != p) {
                Golpe gol = desdePunto(p, directo.position(), directo.position().subtract(directo.getDeltaMovement().scale(4)));
                agregar(d, nueva(p, gol.parte, Tipo.QUEMADURA, g, gol.cara, gol.fx, gol.fy, false));
                return;
            }
            if (!lenta(p, 60)) return;
            agregar(d, alAzar(p, Parte.V[r.nextInt(Parte.V.length)], Tipo.QUEMADURA, 1, false));
            return;
        }
        // --- plantas que pinchan
        if (s.is(DamageTypes.SWEET_BERRY_BUSH)) {
            if (lenta(p, 30)) agregar(d, alAzar(p, r.nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ, Tipo.RASGUNO, 1, false));
            return;
        }
        if (s.is(DamageTypes.CACTUS)) {
            if (lenta(p, 30)) agregar(d, alAzar(p, Parte.V[r.nextInt(Parte.V.length)], Tipo.RASGUNO, 1, false));
            return;
        }
        // --- estalactitas y estalagmitas
        if (s.is(DamageTypes.FALLING_STALACTITE)) {
            agregar(d, alAzar(p, Parte.CABEZA, Tipo.CORTE, Math.max(2, g), false));
            return;
        }
        if (s.is(DamageTypes.STALAGMITE)) {
            agregar(d, alAzar(p, r.nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ, Tipo.CORTE, Math.max(2, g), false));
            return;
        }
        // --- yunques, bloques que caen, estamparse con la élitra
        if (s.is(DamageTypes.FALLING_ANVIL) || s.is(DamageTypes.FALLING_BLOCK)) {
            agregar(d, nueva(p, Parte.CABEZA, Tipo.MORATON, g, r.nextBoolean() ? Herida.FRENTE : Herida.ESPALDA, r.nextInt(256), r.nextInt(80), false));
            return;
        }
        if (s.is(DamageTypes.FLY_INTO_WALL)) {
            agregar(d, nueva(p, Parte.CABEZA, Tipo.MORATON, g, Herida.FRENTE, 64 + r.nextInt(128), r.nextInt(120), false));
            if (dano > 4F) agregar(d, alAzar(p, Parte.TORSO, Tipo.MORATON, gravedad(dano / 2F), false));
            return;
        }
        // --- espinas: al que pega le arañan la mano con la que pegó
        if (s.is(DamageTypes.THORNS)) {
            if (lenta(p, 30)) {
                Parte brazo = p.getMainArm() == HumanoidArm.RIGHT ? Parte.BRAZO_DER : Parte.BRAZO_IZQ;
                agregar(d, nueva(p, brazo, dano >= 3F ? Tipo.CORTE : Tipo.RASGUNO, 1, Herida.FRENTE, r.nextInt(256), 170 + r.nextInt(80), false));
            }
            return;
        }
        if (s.is(DamageTypes.STING)) {
            Golpe gol = golpeCuerpoACuerpo(p, s.getEntity());
            agregar(d, nueva(p, gol.parte, Tipo.MORATON, 1, gol.cara, gol.fx, gol.fy, false));
            return;
        }
        // --- proyectiles (flechas, tridentes, bolas de nieve con mala leche...)
        Entity directo = s.getDirectEntity();
        Entity atacante = s.getEntity();
        if (s.is(DamageTypeTags.IS_PROJECTILE) || (directo != null && directo != atacante && !(directo instanceof LivingEntity))) {
            if (directo == null) return;
            Tipo tipo = directo instanceof AbstractArrow ? Tipo.CORTE : Tipo.MORATON;
            if (tipo == Tipo.CORTE && dano < 2F) tipo = Tipo.RASGUNO;
            Vec3 desde = directo.position().subtract(directo.getDeltaMovement().scale(4));
            Golpe gol = desdePunto(p, directo.position(), desde);
            agregar(d, nueva(p, gol.parte, tipo, g, gol.cara, gol.fx, gol.fy, false));
            return;
        }
        // --- cuerpo a cuerpo
        if (atacante instanceof LivingEntity le) {
            Tipo tipo = Tipo.MORATON;
            boolean especial = false;
            ItemStack arma = le.getMainHandItem();
            Item it = arma.getItem();
            if (it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem || it instanceof ShearsItem
                    || it instanceof PickaxeItem || it instanceof HoeItem) {
                tipo = Tipo.CORTE;
            } else if (le instanceof PolarBear || le instanceof Cat || le instanceof Ocelot) {
                tipo = Tipo.RASGUNO;           // zarpazo: tres garras
                especial = true;
            } else if (le instanceof Spider || le instanceof Wolf || le instanceof Fox || le instanceof Silverfish || le instanceof Endermite
                    || le instanceof Phantom || le instanceof Hoglin || le instanceof Zoglin) {
                tipo = Tipo.CORTE;             // mordiscos y colmillos
            } else if (le instanceof Player && arma.isEmpty()) {
                especial = true;               // puñetazo: marca de nudillos
            }
            if (tipo == Tipo.CORTE && dano < 2F) tipo = Tipo.RASGUNO;
            if (tipo == Tipo.MORATON && dano < 1.5F && r.nextBoolean()) return;
            Golpe gol = golpeCuerpoACuerpo(p, le);
            agregar(d, nueva(p, gol.parte, tipo, g, gol.cara, gol.fx, gol.fy, especial));
        }
        // ahogarse, hambre, veneno, magia, el vacío... no dejan marca en la piel
    }

    // ------------------------------------------------------------------ dónde ha dado el golpe

    /** Parte del cuerpo, cara del cubo y posición dentro de la cara (0..255). */
    record Golpe(Parte parte, int cara, int fx, int fy) {}

    private static Golpe golpeCuerpoACuerpo(Player victima, Entity atacante) {
        if (atacante == null) {
            RandomSource r = victima.getRandom();
            Parte parte = Parte.V[r.nextInt(Parte.V.length)];
            return new Golpe(parte, caraAlAzar(parte, r), r.nextInt(256), r.nextInt(256));
        }
        // Un jugador da donde apunta: se cruza su mirada con la caja de la víctima
        if (atacante instanceof Player pa) {
            Vec3 ojo = pa.getEyePosition();
            Vec3 fin = ojo.add(pa.getViewVector(1.0F).scale(6.0));
            Optional<Vec3> punto = victima.getBoundingBox().inflate(0.1).clip(ojo, fin);
            if (punto.isPresent()) return desdePunto(victima, punto.get(), pa.position());
        }
        // Los mobs: los bajitos muerden las piernas; los altos reparten por todo el cuerpo (más al torso)
        RandomSource r = victima.getRandom();
        double H = victima.getBbHeight();
        double h;
        if (atacante.getBbHeight() < 1.0F) h = 0.04 + r.nextDouble() * 0.38;
        else h = Mth.clamp(0.58 + r.nextGaussian() * 0.2, 0.03, 0.97);
        double yaw = Math.toRadians(victima.yBodyRot);
        double lado = (r.nextDouble() * 2 - 1) * victima.getBbWidth() * 0.5;
        Vec3 punto = victima.position().add(-Math.cos(yaw) * lado, h * H, -Math.sin(yaw) * lado);
        return desdePunto(victima, punto, atacante.position());
    }

    private static int caraDesde(Player p, Vec3 desde, Parte parte) {
        double yaw = Math.toRadians(p.yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), derX = -Math.cos(yaw), derZ = -Math.sin(yaw);
        Vec3 dir = desde.subtract(p.position());
        double df = dir.x * fwdX + dir.z * fwdZ, dl = dir.x * derX + dir.z * derZ;
        int cara;
        if (Math.abs(df) >= Math.abs(dl)) cara = df >= 0 ? Herida.FRENTE : Herida.ESPALDA;
        else cara = dl >= 0 ? Herida.DERECHA : Herida.IZQUIERDA;
        // la cara de dentro de brazos y piernas casi no se ve: entonces por delante
        if (parte.esExtremidad() && ((cara == Herida.DERECHA && !parte.derecha()) || (cara == Herida.IZQUIERDA && parte.derecha())))
            cara = Herida.FRENTE;
        return cara;
    }

    /** A partir del punto de impacto (en el mundo) y de dónde venía el golpe. */
    static Golpe desdePunto(Player p, Vec3 punto, Vec3 desde) {
        RandomSource r = p.getRandom();
        double H = Math.max(0.1, p.getBbHeight());
        double medio = Math.max(0.1, p.getBbWidth() * 0.5);
        double yaw = Math.toRadians(p.yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), derX = -Math.cos(yaw), derZ = -Math.sin(yaw);
        Vec3 rel = punto.subtract(p.position());
        double lx = Mth.clamp((rel.x * derX + rel.z * derZ) / medio, -1, 1);   // + = lado derecho del jugador
        double lf = Mth.clamp((rel.x * fwdX + rel.z * fwdZ) / medio, -1, 1);   // + = por delante
        double h = Mth.clamp(rel.y / H, 0, 1);
        if (H < 1.0) {   // nadando, planeando o tumbado: no se distingue bien la altura
            Parte parte = Parte.V[r.nextInt(Parte.V.length)];
            return new Golpe(parte, caraDesde(p, desde, parte), r.nextInt(256), r.nextInt(256));
        }
        Parte parte;
        double fy;
        if (h < 0.39) {
            parte = lx > 0 ? Parte.PIERNA_DER : lx < 0 ? Parte.PIERNA_IZQ : (r.nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ);
            fy = 1 - h / 0.39;
        } else if (h < 0.76) {
            parte = Math.abs(lx) > 0.55 ? (lx > 0 ? Parte.BRAZO_DER : Parte.BRAZO_IZQ) : Parte.TORSO;
            fy = (0.76 - h) / 0.37;
        } else {
            parte = Parte.CABEZA;
            fy = (1 - h) / 0.24;
        }
        int cara = caraDesde(p, desde, parte);
        double fx;
        if (parte.esExtremidad()) fx = r.nextDouble();
        else if (cara == Herida.FRENTE) fx = 0.5 - lx / 1.1;
        else if (cara == Herida.ESPALDA) fx = 0.5 + lx / 1.1;
        else if (cara == Herida.DERECHA) fx = 0.5 + lf * 0.5;
        else fx = 0.5 - lf * 0.5;
        return new Golpe(parte, cara, (int) (Mth.clamp(fx, 0, 1) * 255), (int) (Mth.clamp(fy, 0, 1) * 255));
    }

    // ------------------------------------------------------------------ curación y efectos

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0 || p instanceof FakePlayer) return;
        if (!p.getPersistentData().contains(CLAVE)) {
            if (p.tickCount % 200 == 0) efectos(p, null);   // por si quedó algún modificador colgado
            return;
        }
        HeridasDatos d = leer(p);
        if (!activo(p.level())) {
            curarTodo(p);
            return;
        }
        boolean cambio = false;
        float ritmo = 1F;
        MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
        if (regen != null) ritmo += 1.5F * (regen.getAmplifier() + 1);
        if (p.isSleeping()) ritmo += 3F;
        for (int i = d.lista.size() - 1; i >= 0; i--) {
            Herida h = d.lista.get(i);
            float r = ritmo * (d.vendada(h.parte) ? 2F : 1F);
            h.restante -= Math.round(20 * r);
            if (h.restante <= 0) {
                d.lista.remove(i);
                cambio = true;
            }
        }
        for (int i = 0; i < d.vendas.length; i++) {
            if (d.vendas[i] > 0) {
                d.vendas[i] = Math.max(0, d.vendas[i] - 20);
                if (d.vendas[i] == 0) cambio = true;
            }
        }
        guardar(p, d);
        efectos(p, d);
        // se reenvía al cambiar y, si hay heridas, cada 10 s (el cliente las va curando a ojo entre medias)
        if (cambio || p.tickCount % 200 == 0) enviar(p, d);
    }

    /** Pierna tullida: más lento. Brazo tullido: pega más despacio (el temblor de puntería va en el cliente). */
    public static void efectos(ServerPlayer p, HeridasDatos d) {
        double vel = 0, atq = 0;
        if (d != null) {
            float a = d.nivel(Parte.PIERNA_DER), b = d.nivel(Parte.PIERNA_IZQ);
            vel = -Math.min(0.3, Math.max(a, b) * 0.25 + Math.min(a, b) * 0.08);
            Parte principal = p.getMainArm() == HumanoidArm.RIGHT ? Parte.BRAZO_DER : Parte.BRAZO_IZQ;
            Parte otro = principal == Parte.BRAZO_DER ? Parte.BRAZO_IZQ : Parte.BRAZO_DER;
            atq = -Math.min(0.3, d.nivel(principal) * 0.25 + d.nivel(otro) * 0.08);
        }
        modificador(p, Attributes.MOVEMENT_SPEED, VELOCIDAD_ID, "miscelanea_heridas_piernas", vel);
        modificador(p, Attributes.ATTACK_SPEED, ATAQUE_ID, "miscelanea_heridas_brazos", atq);
    }

    private static void modificador(Player p, Attribute atributo, UUID id, String nombre, double valor) {
        AttributeInstance a = p.getAttribute(atributo);
        if (a == null) return;
        AttributeModifier actual = a.getModifier(id);
        if (Math.abs(valor) < 0.005) {
            if (actual != null) a.removeModifier(id);
            return;
        }
        if (actual != null && Math.abs(actual.getAmount() - valor) < 0.004) return;
        if (actual != null) a.removeModifier(id);
        a.addTransientModifier(new AttributeModifier(id, nombre, valor, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Torso muy tocado: la vida se regenera más despacio. */
    @SubscribeEvent
    public static void curarse(LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.getPersistentData().contains(CLAVE) || !activo(p.level())) return;
        float torso = leer(p).nivel(Parte.TORSO);
        if (torso > 0.15F) e.setAmount(e.getAmount() * (1F - 0.5F * torso));
    }

    // ------------------------------------------------------------------ ciclo de vida y sincronización

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getPersistentData().contains(CLAVE)) curarTodo(p);
    }

    /** Volver del End no cura (al morir el jugador nuevo nace sin datos, así que no hace falta nada). */
    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        if (e.isWasDeath()) return;
        CompoundTag viejo = e.getOriginal().getPersistentData();
        if (viejo.contains(CLAVE)) e.getEntity().getPersistentData().put(CLAVE, viejo.getCompound(CLAVE).copy());
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) actualizar(p, leer(p));
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        ULTIMA_LENTA.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) actualizar(p, leer(p));
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) actualizar(p, leer(p));
    }

    /** Al empezar a ver a otro jugador te llegan sus heridas (siempre, aunque esté sano: así se borran las viejas). */
    @SubscribeEvent
    public static void verJugador(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer otro && e.getEntity() instanceof ServerPlayer yo) {
            HeridasRed.CANAL.send(PacketDistributor.PLAYER.with(() -> yo), new HeridasPacket(otro.getId(), leer(otro)));
        }
    }
}
