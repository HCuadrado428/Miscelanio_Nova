package com.miscelanea.client.espera;

import com.miscelanea.Miscelanea;
import com.miscelanea.espera.Espera;
import com.miscelanea.espera.eventos.BrujulaSalidaItem;
import com.miscelanea.espera.eventos.ExtrasRegistro;
import com.miscelanea.espera.eventos.RedExtras;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El Espera en el cliente, segunda parte (2.0.0): los restos de errores que solo ve cada uno y los jugadores
 * fantasma. Nada de esto existe en el servidor ni en el mundo: es todo de tu cliente.
 * <ul>
 *   <li><b>Chat corrupto</b> (cada 3-7 min): un mensaje de sistema roto, con letras cambiadas y trozos ilegibles: gente
 *       del servidor que "entra" o "sale", alguien que pregunta si hay alguien, el servidor guardando el mundo al 404 %,
 *       una excepción de Java...</li>
 *   <li><b>Sonidos rotos</b> (cada 1-3 min): un sonido del juego con el tono mal, cortado a medias o que se atasca y se
 *       repite, lejos y con posición.</li>
 *   <li><b>Jugadores fantasma</b> (uno cada 45-120 s, como mucho dos a la vez): a lo lejos, dentro de la niebla, alguien
 *       con el nombre y la skin de gente del servidor que no está aquí (o con un nombre inventado), quieto mirándote o
 *       andando. Si te acercas o lo miras fijamente un rato, ya no está. Solo existe en el mundo de tu cliente (el servidor no sabe nada de él).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ExtrasCliente {
    private ExtrasCliente() {}

    // ------------------------------------------------------------------ nombres (los manda el servidor)
    private static final List<UUID> IDS = new ArrayList<>();
    private static final List<String> NOMBRES = new ArrayList<>();
    /** Si no hay nadie más en el servidor: nombres inventados. */
    private static final String[] INVENTADOS = {"Jugador1", "Invitado_404", "copia_de_ti", "AFK_desde_2011", "SinNombre", "xX_Steve_Xx",
            "jugador_null", "TuPrimo", "Notch2", "Alex_backup", "nuevo_jugador(1)", "MissingNo"};

    public static void nombres(List<UUID> ids, List<String> nombres) {
        IDS.clear();
        NOMBRES.clear();
        IDS.addAll(ids);
        NOMBRES.addAll(nombres);
    }

    /** Un nombre de alguien (o inventado): {uuid, nombre}. */
    private static Object[] alguien(RandomSource r, @Nullable String evitar) {
        for (int i = 0; i < 4; i++) {
            Object[] o;
            if (!NOMBRES.isEmpty() && (r.nextInt(5) != 0 || NOMBRES.size() > 3)) {
                int k = r.nextInt(NOMBRES.size());
                o = new Object[]{IDS.get(k), NOMBRES.get(k)};
            } else {
                String n = INVENTADOS[r.nextInt(INVENTADOS.length)];
                o = new Object[]{UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(java.nio.charset.StandardCharsets.UTF_8)), n};
            }
            if (!o[1].equals(evitar)) return o;
        }
        return new Object[]{UUID.nameUUIDFromBytes("OfflinePlayer:Jugador1".getBytes()), "Jugador1"};
    }

    // ------------------------------------------------------------------ estado
    private static int edad, proxChat = 1600, proxSonido = 900, proxFantasma = 500;
    /** Para la prueba: que no salga ningún fantasma por su cuenta (se pisaría con los que saca ella). */
    public static volatile boolean sinSorpresas;
    private static int siguienteId;
    private static int chats, sonidos, fantasmasVistos, fantasmasSalidos;
    private static Object nivel;
    private record Programado(int t, Runnable r) {}
    private static final ArrayDeque<Programado> COLA = new ArrayDeque<>();
    private static final List<Fantasma> FANTASMAS = new ArrayList<>();

    static boolean aqui(Minecraft mc) {
        return mc.level != null && mc.player != null && Espera.en(mc.level);
    }

    /** Para la prueba. */
    public static String estado() {
        return "fantasmas=" + FANTASMAS.size() + " (salidos " + fantasmasSalidos + ", vistos " + fantasmasVistos + "), chat roto=" + chats
                + ", sonidos rotos=" + sonidos + ", nombres=" + NOMBRES.size() + (NOMBRES.isEmpty() ? " (inventados)" : " " + NOMBRES);
    }

    public static int fantasmas() {
        return FANTASMAS.size();
    }

    public static int fantasmasVistos() {
        return fantasmasVistos;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != nivel) {
            nivel = mc.level;
            FANTASMAS.clear();
            COLA.clear();
        }
        if (!aqui(mc)) {
            for (Fantasma f : FANTASMAS) quitar(mc, f);
            FANTASMAS.clear();
            COLA.clear();
            return;
        }
        if (mc.isPaused()) return;
        edad++;
        RandomSource r = mc.player.getRandom();
        while (!COLA.isEmpty() && COLA.peek().t() <= edad) COLA.poll().r().run();
        if (--proxChat <= 0) {
            proxChat = 3600 + r.nextInt(4800);
            chatRoto(mc, r, -1);
        }
        if (--proxSonido <= 0) {
            proxSonido = 1200 + r.nextInt(2400);
            sonidoRoto(mc, r, -1);
        }
        if (--proxFantasma <= 0) {
            proxFantasma = 900 + r.nextInt(1500);
            if (!sinSorpresas && (FANTASMAS.isEmpty() || (FANTASMAS.size() < 2 && r.nextInt(4) == 0))) aparecer(mc, r, null, null, false);
        }
        for (int i = FANTASMAS.size() - 1; i >= 0; i--)
            if (!FANTASMAS.get(i).tick(mc, r)) quitar(mc, FANTASMAS.remove(i));
    }

    // ------------------------------------------------------------------ chat corrupto
    private static final int CHATS = 16;

    /** Mete en el chat un mensaje de sistema roto (k < 0: uno al azar). Devuelve el texto (para la prueba). */
    public static String chatRoto(Minecraft mc, RandomSource r, int k) {
        if (k < 0) k = r.nextInt(CHATS);
        String quien = (String) alguien(r, null)[1];
        String yo = mc.player.getGameProfile().getName();
        String clave = "espera.miscelanea.chat." + k;
        String texto = I18n.get(clave, k == 8 || k == 15 ? yo : quien);
        ChatFormatting color = switch (k) {
            case 0, 1, 10 -> ChatFormatting.YELLOW;
            case 7, 11, 14 -> ChatFormatting.GRAY;
            case 8, 12 -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
        Component c = romper(texto, r, 0.05F + r.nextFloat() * 0.08F, color);
        mc.gui.getChat().addMessage(c);
        chats++;
        return c.getString();
    }

    /** Un mensaje del sistema de verdad, sin romper (cuando un fantasma "se va"). */
    private static void avisoSale(Minecraft mc, String quien) {
        mc.gui.getChat().addMessage(Component.translatable("espera.miscelanea.chat.1", quien).withStyle(ChatFormatting.YELLOW));
    }

    private static final String RUIDO = "█▓▒░#%&@$¿?¡!*~^¬§¤";

    /** Rompe un texto: letras cambiadas o repetidas, trozos ilegibles (los "§k" del juego) y algún carácter raro. */
    static MutableComponent romper(String t, RandomSource r, float p, ChatFormatting color) {
        MutableComponent out = Component.empty();
        StringBuilder normal = new StringBuilder();
        Style base = Style.EMPTY.withColor(color);
        char[] cs = t.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            float x = r.nextFloat();
            if (c == ' ' || x > p) {
                normal.append(c);
                continue;
            }
            int tipo = r.nextInt(5);
            if (tipo == 0 && i + 1 < cs.length && cs[i + 1] != ' ') {
                // dos letras cambiadas de sitio
                normal.append(cs[i + 1]).append(c);
                i++;
            } else if (tipo == 1) {
                normal.append(c).append(c).append(c);
            } else if (tipo == 2) {
                normal.append(RUIDO.charAt(r.nextInt(RUIDO.length())));
            } else {
                // un trozo ilegible (cambia solo)
                out.append(Component.literal(normal.toString()).withStyle(base));
                normal.setLength(0);
                int n = 1 + r.nextInt(3);
                StringBuilder raro = new StringBuilder();
                for (int j = 0; j < n && i < cs.length; j++, i++) raro.append(cs[i]);
                i--;
                out.append(Component.literal(raro.toString()).withStyle(base.withObfuscated(true)));
            }
        }
        out.append(Component.literal(normal.toString()).withStyle(base));
        return out;
    }

    // ------------------------------------------------------------------ sonidos rotos
    /** Un sonido del juego "roto": tono, cuánto dura antes de cortarse, cuántas veces se repite y cada cuánto. */
    private record Roto(SoundEvent s, float tono, int dura, int veces, int cada) {}

    private static Roto[] rotos() {
        return new Roto[]{
                new Roto(SoundEvents.VILLAGER_AMBIENT, 0.5F, 9, 1, 0),
                new Roto(SoundEvents.PLAYER_LEVELUP, 0.55F, 7, 1, 0),
                new Roto(SoundEvents.CHEST_OPEN, 0.5F, 6, 3, 4),
                new Roto(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 3, 6, 2),
                new Roto(SoundEvents.PLAYER_HURT, 1.7F, 4, 2, 5),
                new Roto(SoundEvents.WOODEN_DOOR_OPEN, 0.6F, 3, 4, 3),
                new Roto(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 2, 5, 6),
                new Roto(SoundEvents.CREEPER_PRIMED, 0.8F, 6, 1, 0),
                new Roto(SoundEvents.PLAYER_BURP, 0.45F, 20, 1, 0),
                new Roto(SoundEvents.MUSIC_DISC_CAT, 0.85F, 70, 1, 0),
                new Roto(SoundEvents.PORTAL_TRIGGER, 0.5F, 24, 1, 0),
                new Roto(SoundEvents.NOTE_BLOCK_HARP.value(), 0.84F, 6, 5, 6),
        };
    }

    /** Toca un sonido roto lejos, en una dirección al azar (k < 0: uno al azar). */
    public static void sonidoRoto(Minecraft mc, RandomSource r, int k) {
        Roto[] rs = rotos();
        Roto x = rs[k < 0 ? r.nextInt(rs.length) : Math.floorMod(k, rs.length)];
        LocalPlayer p = mc.player;
        double a = r.nextDouble() * Math.PI * 2, d = 5 + r.nextDouble() * 7;
        double sx = p.getX() + Math.cos(a) * d, sy = p.getEyeY(), sz = p.getZ() + Math.sin(a) * d;
        boolean escala = x.s() == SoundEvents.NOTE_BLOCK_HARP.value();
        float[] notas = {1.0F, 0.94F, 0.84F, 0.79F, 0.5F};
        for (int i = 0; i < x.veces(); i++) {
            float tono = escala ? notas[i % notas.length] : x.tono() * (1 + (r.nextFloat() - 0.5F) * 0.04F);
            int t = edad + i * x.cada();
            COLA.add(new Programado(t, () -> mc.getSoundManager().play(new Cortado(x.s(), sx, sy, sz, 0.45F, tono, x.dura()))));
        }
        sonidos++;
    }

    /** Un sonido que se corta a los tantos tics. */
    static final class Cortado extends AbstractTickableSoundInstance {
        private int vida;

        Cortado(SoundEvent s, double x, double y, double z, float v, float p, int vida) {
            super(s, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = v;
            this.pitch = p;
            this.vida = vida;
            this.attenuation = Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (--vida <= 0) stop();
        }
    }

    // ------------------------------------------------------------------ jugadores fantasma
    /** Pieles ya buscadas: uuid → {piel, "slim"/"default"}. */
    private static final Map<UUID, Object[]> PIELES = new HashMap<>();

    /** Un jugador de mentira con el perfil de alguien, solo en el mundo de este cliente. */
    static final class Sombra extends RemotePlayer {
        Sombra(ClientLevel l, GameProfile g) {
            super(l, g);
            this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        }

        @Override
        public ResourceLocation getSkinTextureLocation() {
            Object[] o = PIELES.get(getUUID());
            if (o != null && o[0] != null) return (ResourceLocation) o[0];
            var info = getPlayerInfo();
            return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(getUUID());
        }

        @Override
        public String getModelName() {
            Object[] o = PIELES.get(getUUID());
            if (o != null && o[1] != null) return (String) o[1];
            var info = getPlayerInfo();
            return info != null ? info.getModelName() : DefaultPlayerSkin.getSkinModelName(getUUID());
        }
    }

    /** Busca la skin de verdad de esa persona (en segundo plano; mientras, la de por defecto de su UUID). */
    private static void buscarPiel(Minecraft mc, GameProfile g) {
        if (PIELES.containsKey(g.getId())) return;
        PIELES.put(g.getId(), new Object[]{null, null});
        try {
            net.minecraft.world.level.block.entity.SkullBlockEntity.updateGameprofile(g, lleno -> {
                if (lleno == null) return;
                mc.getSkinManager().registerSkins(lleno, (tipo, sitio, tex) -> {
                    if (tipo != MinecraftProfileTexture.Type.SKIN) return;
                    String m = tex.getMetadata("model");
                    PIELES.put(g.getId(), new Object[]{sitio, m == null ? "default" : m});
                }, false);
            });
        } catch (Exception ex) {
            Miscelanea.LOGGER.debug("Espera: no se pudo buscar la skin de {}", g.getName(), ex);
        }
    }

    static final class Fantasma {
        final Sombra s;
        double x, y, z, xo, yo, zo;
        float rumbo;
        boolean anda, visto, fijo;
        int vida, mirada, veo = 1;

        Fantasma(Sombra s, double x, double y, double z) {
            this.s = s;
            this.x = xo = x;
            this.y = yo = y;
            this.z = zo = z;
            s.setPos(x, y, z);
            s.setOldPosAndRot();
        }

        /** false = ya no está. */
        boolean tick(Minecraft mc, RandomSource r) {
            LocalPlayer p = mc.player;
            ClientLevel l = mc.level;
            vida++;
            xo = x;
            yo = y;
            zo = z;
            double dx = p.getX() - x, dz = p.getZ() - z, dist = Math.sqrt(dx * dx + dz * dz);
            Vec3 ojo = p.getEyePosition();
            Vec3 hacia = new Vec3(x, y + 1.5, z).subtract(ojo).normalize();
            double dot = p.getViewVector(1F).dot(hacia);
            if (vida % 5 == 0) {
                var bh = l.clip(new ClipContext(ojo, new Vec3(x, y + 1.5, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                veo = bh.getType() == HitResult.Type.MISS ? 1 : 0;
            }
            if (dot > 0.992 && veo == 1) mirada++;
            else mirada = Math.max(0, mirada - 1);
            if (mirada >= 8) visto = true;
            if (!fijo) {
                boolean cerca = dist < 8;
                if (cerca) visto = true;
                if (cerca || mirada >= 36 || (vida > 1100 && dot < 0.6) || vida > 2400 || dist > 56) {
                    irse(mc, r);
                    return false;
                }
            }
            // andar: despacio, en línea recta; si no puede seguir, se para y te mira
            double paso = 0;
            if (anda) {
                double nx = x - Mth.sin(rumbo * Mth.DEG_TO_RAD) * 0.09, nz = z + Mth.cos(rumbo * Mth.DEG_TO_RAD) * 0.09;
                double ny = suelo(l, nx, y, nz);
                if (Double.isNaN(ny) || Math.abs(ny - y) > 0.6) anda = false;
                else {
                    paso = Math.sqrt((nx - x) * (nx - x) + (nz - z) * (nz - z));
                    x = nx;
                    y = ny;
                    z = nz;
                }
            }
            float yaw;
            if (anda) yaw = rumbo;
            else yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            s.yBodyRot = Mth.approachDegrees(s.yBodyRot, yaw, 6F);
            float cabeza = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            // la cabeza te sigue un poco aunque ande (no más de 50 grados del cuerpo)
            s.yHeadRot = s.yBodyRot + Mth.clamp(Mth.wrapDegrees(cabeza - s.yBodyRot), -50F, 50F);
            double dy = p.getEyeY() - (y + 1.62);
            float cabeceo = Mth.clamp((float) -(Mth.atan2(dy, Math.max(dist, 0.5)) * Mth.RAD_TO_DEG), -40F, 40F);
            // el mundo del cliente lo mueve (y calcula el paso de andar) hacia aquí
            s.lerpTo(x, y, z, yaw, cabeceo, 2, false);
            return true;
        }

        void irse(Minecraft mc, RandomSource r) {
            if (visto) {
                fantasmasVistos++;
                RedExtras.CANAL.sendToServer(new RedExtras.Visto());
                // a veces, al rato, "se va" de verdad
                if (r.nextInt(3) == 0) {
                    String n = s.getGameProfile().getName();
                    COLA.add(new Programado(edad + 30 + r.nextInt(40), () -> avisoSale(mc, n)));
                }
            }
        }
    }

    /** El suelo (altura de los pies) cerca de esa altura, o NaN. */
    static double suelo(ClientLevel l, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y + 0.5);
        for (int dy = 3; dy >= -4; dy--) {
            m.set(bx, by + dy, bz);
            BlockPos abajo = m.below();
            if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty() || !l.getBlockState(m.above()).getCollisionShape(l, m.above()).isEmpty()) continue;
            VoxelShape sh = l.getBlockState(abajo).getCollisionShape(l, abajo);
            if (sh.isEmpty()) continue;
            return abajo.getY() + sh.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    /**
     * Saca un fantasma: delante (para que lo descubras), lejos, dentro de la niebla y a la vista. Con {@code donde} (la
     * prueba) en ese sitio; {@code fijo} = no se va hasta que se le suelte.
     */
    public static boolean aparecer(Minecraft mc, RandomSource r, @Nullable Vec3 donde, @Nullable String nombre, boolean fijo) {
        LocalPlayer p = mc.player;
        ClientLevel l = mc.level;
        if (p == null || l == null) return false;
        float niebla = EsperaCliente.Niebla.distanciaActual();
        double lejos = Math.max(16, Math.min(26, (niebla > 0 ? niebla : 24) * 0.9));
        Vec3 sitio = donde;
        for (int i = 0; i < 10 && sitio == null; i++) {
            float a = p.getYRot() - 55F + r.nextFloat() * 110F;
            double d = 13 + r.nextDouble() * (lejos - 13);
            double fx = p.getX() - Mth.sin(a * Mth.DEG_TO_RAD) * d, fz = p.getZ() + Mth.cos(a * Mth.DEG_TO_RAD) * d;
            double fy = suelo(l, fx, p.getY(), fz);
            // en su mismo piso (no en el de arriba o el de abajo, por el hueco de una escalera)
            if (Double.isNaN(fy) || com.miscelanea.espera.EsperaPlano.nivel(fy) != com.miscelanea.espera.EsperaPlano.nivel(p.getY())) continue;
            var bh = l.clip(new ClipContext(p.getEyePosition(), new Vec3(fx, fy + 1.5, fz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) continue;
            sitio = new Vec3(fx, fy, fz);
        }
        if (sitio == null) return false;
        String evitar = FANTASMAS.isEmpty() ? null : FANTASMAS.get(0).s.getGameProfile().getName();
        Object[] o = nombre != null ? new Object[]{UUID.nameUUIDFromBytes(("OfflinePlayer:" + nombre).getBytes(java.nio.charset.StandardCharsets.UTF_8)), nombre}
                : alguien(r, evitar);
        GameProfile g = new GameProfile((UUID) o[0], (String) o[1]);
        Sombra s;
        try {
            s = new Sombra(l, g);
        } catch (Exception ex) {
            Miscelanea.LOGGER.warn("Espera: no se pudo crear un jugador fantasma", ex);
            proxFantasma = Integer.MAX_VALUE;
            return false;
        }
        s.setId(-7000 - (siguienteId++ & 0x3FF));
        buscarPiel(mc, g);
        Fantasma f = new Fantasma(s, sitio.x, sitio.y, sitio.z);
        s.moveTo(sitio.x, sitio.y, sitio.z, 0, 0);
        f.fijo = fijo;
        // la mitad andan (cruzando, de lado), la otra mitad se quedan quietos mirándote
        if (!fijo && r.nextBoolean()) {
            f.anda = true;
            double dx = p.getX() - sitio.x, dz = p.getZ() - sitio.z;
            f.rumbo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F + (r.nextBoolean() ? 90F : -90F) + (r.nextFloat() - 0.5F) * 30F;
        }
        float yaw = (float) (Mth.atan2(p.getZ() - sitio.z, p.getX() - sitio.x) * Mth.RAD_TO_DEG) - 90F;
        s.setYRot(f.anda ? f.rumbo : yaw);
        s.yBodyRot = s.yBodyRotO = s.getYRot();
        s.yHeadRot = s.yHeadRotO = yaw;
        try {
            // solo en el mundo de este cliente: el servidor no sabe nada de él
            l.addPlayer(s.getId(), s);
        } catch (Exception ex) {
            Miscelanea.LOGGER.warn("Espera: no se pudo meter un jugador fantasma", ex);
            proxFantasma = Integer.MAX_VALUE;
            return false;
        }
        FANTASMAS.add(f);
        fantasmasSalidos++;
        return true;
    }

    /** Para la prueba: fuera todos los fantasmas que haya (sin avisos ni logros). */
    public static void quitarTodos() {
        Minecraft mc = Minecraft.getInstance();
        for (Fantasma f : FANTASMAS) quitar(mc, f);
        FANTASMAS.clear();
    }

    /** Para la prueba: los fantasmas fijos ya se pueden ir (y dónde está el primero, o null). */
    @Nullable
    public static Vec3 soltarFijos() {
        for (Fantasma f : FANTASMAS) f.fijo = false;
        return FANTASMAS.isEmpty() ? null : new Vec3(FANTASMAS.get(0).x, FANTASMAS.get(0).y, FANTASMAS.get(0).z);
    }

    @Nullable
    public static Vec3 primero() {
        return FANTASMAS.isEmpty() ? null : new Vec3(FANTASMAS.get(0).x, FANTASMAS.get(0).y, FANTASMAS.get(0).z);
    }

    private static void quitar(Minecraft mc, Fantasma f) {
        if (mc.level != null && mc.level.getEntity(f.s.getId()) == f.s) mc.level.removeEntity(f.s.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }

    // ------------------------------------------------------------------ la brújula de salida
    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registro {
        @SubscribeEvent
        public static void preparar(FMLClientSetupEvent e) {
            e.enqueueWork(() -> ItemProperties.register(ExtrasRegistro.BRUJULA.get(), new ResourceLocation("angle"),
                    new CompassItemPropertyFunction((lvl, st, ent) -> {
                        BlockPos b = BrujulaSalidaItem.objetivo(st);
                        return b == null || lvl == null || !Espera.en(lvl) ? null : GlobalPos.of(lvl.dimension(), b);
                    })));
        }
    }
}
