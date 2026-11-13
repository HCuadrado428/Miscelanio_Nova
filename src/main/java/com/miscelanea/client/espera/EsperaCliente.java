package com.miscelanea.client.espera;

import com.miscelanea.Miscelanea;
import com.miscelanea.espera.Espera;
import com.miscelanea.espera.EsperaGenerador;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * El Espera en el cliente: sin cielo, sin nubes, sin sol ni estrellas (no se dibuja nada) y con una niebla corta y
 * oscura que se come lo de lejos. Además de dar el ambiente, ahorra: lo que tapa la niebla casi no cuesta.
 */
public final class EsperaCliente {
    private EsperaCliente() {}

    /** Hasta dónde se ve (bloques) en cada zona: al aire libre, un poco más que dentro. */
    static float distancia(EsperaGenerador.Zona z) {
        return switch (z) {
            case FERIA -> 34;
            case SALA -> 24;
            case RECREATIVA -> 22;
            case HOTEL -> 20;
            case SIN_TEXTURA -> 30;
            case PISCINA -> 26;
            case APARCAMIENTO -> 26;
            case COLEGIO -> 20;
            case CENTRO -> 30;
            case METRO -> 22;
        };
    }

    /** Color de la niebla de cada zona (r, g, b). */
    static float[] colorNiebla(EsperaGenerador.Zona z) {
        return switch (z) {
            case FERIA -> new float[]{0.07F, 0.05F, 0.10F};
            case SALA -> new float[]{0.12F, 0.11F, 0.065F};
            case RECREATIVA -> new float[]{0.05F, 0.02F, 0.09F};
            case HOTEL -> new float[]{0.09F, 0.06F, 0.035F};
            case SIN_TEXTURA -> new float[]{0.0F, 0.0F, 0.0F};
            case PISCINA -> new float[]{0.035F, 0.085F, 0.105F};
            case APARCAMIENTO -> new float[]{0.06F, 0.07F, 0.06F};
            case COLEGIO -> new float[]{0.05F, 0.055F, 0.08F};
            case CENTRO -> new float[]{0.09F, 0.075F, 0.065F};
            case METRO -> new float[]{0.055F, 0.058F, 0.062F};
        };
    }

    static final class Efectos extends DimensionSpecialEffects {
        Efectos() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 c, float brillo) {
            return c;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registro {
        @SubscribeEvent
        public static void efectos(RegisterDimensionSpecialEffectsEvent e) {
            e.register(new ResourceLocation(Miscelanea.MOD_ID, "espera"), new Efectos());
        }

        @SubscribeEvent
        public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(com.miscelanea.registry.ModEntities.PACIENTE.get(), PacienteRenderer::new);
        }
    }

    /**
     * La niebla: corta, del color de cada zona, y al pasar de una zona a otra el color y la distancia cambian poco a poco
     * (en un segundo, más o menos) en vez de saltar en la raya del chunk.
     */
    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
    public static final class Niebla {
        /** Lo que se está usando ahora (va hacia lo de la zona en la que se está); dist < 0 = aún nada. */
        static float dist = -1, r, g, b;
        private static long ultimo;

        static boolean aqui() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && Espera.en(mc.level);
        }

        static EsperaGenerador.Zona zona() {
            Minecraft mc = Minecraft.getInstance();
            var c = mc.gameRenderer.getMainCamera().getPosition();
            return EsperaGenerador.zonaEn(c.x, c.y, c.z);
        }

        /** Una vez por fotograma: acerca la niebla actual a la de la zona. */
        static void actualizar() {
            EsperaGenerador.Zona z = zona();
            float td = distancia(z);
            // en las salas más grandes (el atrio del centro comercial y la piscina olímpica) se ve más lejos: si no, la
            // niebla se come las escaleras mecánicas y el fondo de la piscina
            var c = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            var gs = com.miscelanea.espera.EsperaPlano.grande(com.miscelanea.espera.EsperaPlano.nivel(c.y), (int) Math.floor(c.x) >> 4, (int) Math.floor(c.z) >> 4);
            if (gs != null && (gs.tipo() == com.miscelanea.espera.EsperaPlano.Tipo.ATRIO || gs.tipo() == com.miscelanea.espera.EsperaPlano.Tipo.PISCINA_OLIMPICA)) td = 46;
            float[] tc = colorNiebla(z);
            long ahora = net.minecraft.Util.getMillis();
            float dt = (ahora - ultimo) / 1000F;
            ultimo = ahora;
            if (dist < 0 || dt > 1.5F || dt < 0) {
                dist = td;
                r = tc[0];
                g = tc[1];
                b = tc[2];
                return;
            }
            float k = 1 - (float) Math.exp(-dt / 0.45F);
            dist += (td - dist) * k;
            r += (tc[0] - r) * k;
            g += (tc[1] - g) * k;
            b += (tc[2] - b) * k;
        }

        /** Para la prueba: la distancia de niebla que se está usando ahora. */
        public static float distanciaActual() {
            return dist;
        }

        /** Para la prueba: sin niebla (para la foto desde arriba). */
        public static boolean sinNiebla;

        @SubscribeEvent
        public static void niebla(ViewportEvent.RenderFog e) {
            if (!aqui()) return;
            if (dist < 0) actualizar();
            if (sinNiebla) return;
            float lejos = Math.min(e.getFarPlaneDistance(), dist);
            e.setNearPlaneDistance(e.getMode() == FogRenderer.FogMode.FOG_SKY ? 0 : lejos * 0.15F);
            e.setFarPlaneDistance(lejos);
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void color(ViewportEvent.ComputeFogColor e) {
            if (!aqui()) {
                dist = -1;
                return;
            }
            actualizar();
            e.setRed(r);
            e.setGreen(g);
            e.setBlue(b);
        }
    }

    // ------------------------------------------------------------------ ambiente
    /**
     * El sonido del Espera, mezclado aquí (no en los sonidos: así se sabe lo que suena aunque no haya tarjeta de
     * sonido, como en la prueba):
     * <ul>
     *   <li>Un bucle de fondo por zona, sin posición; al cambiar de zona el de antes baja en 2 s y entra el nuevo. Cada
     *       zona lleva su ganancia para que todas suenen igual de fuerte (medidas en LUFS).</li>
     *   <li>De vez en cuando (cada 25-90 s), un susto lejos y con posición: pasos que se acercan por detrás, un timbre de
     *       turno, una puerta, unos golpes en una puerta... Siempre más bajo que el fondo y nunca dos a la vez.</li>
     *   <li>El ruido de la tele sin señal más cercana (si hay alguna a menos de 14 bloques).</li>
     * </ul>
     * Si Minecraft corta los sonidos (al cambiar de mundo o con /stopsound), el bucle se vuelve a poner.
     */
    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
    public static final class Ambiente {
        /** Volumen de mezcla (0-1) de cada zona y el sonido que lo toca. */
        private static final java.util.EnumMap<EsperaGenerador.Zona, Float> MEZCLA = new java.util.EnumMap<>(EsperaGenerador.Zona.class);
        private static final java.util.EnumMap<EsperaGenerador.Zona, Bucle> SONANDO = new java.util.EnumMap<>(EsperaGenerador.Zona.class);
        /** Sustos ya programados (tic en el que suenan) y cuántos han sonado. */
        private static final java.util.ArrayDeque<Susto> COLA = new java.util.ArrayDeque<>();
        private static int edad, proximo = 500, sustos;
        private static Object nivel;
        private static BucleTele tele;

        private record Susto(int t, net.minecraft.sounds.SoundEvent s, double x, double y, double z, float v, float p) {}

        static net.minecraft.sounds.SoundEvent sonido(EsperaGenerador.Zona z) {
            return switch (z) {
                case FERIA -> com.miscelanea.registry.ModSounds.ESPERA_FERIA.get();
                case SALA -> com.miscelanea.registry.ModSounds.ESPERA_SALA.get();
                case RECREATIVA -> com.miscelanea.registry.ModSounds.ESPERA_RECREATIVA.get();
                case HOTEL -> com.miscelanea.registry.ModSounds.ESPERA_HOTEL.get();
                case SIN_TEXTURA -> com.miscelanea.registry.ModSounds.ESPERA_SIN_TEXTURA.get();
                case PISCINA -> com.miscelanea.registry.ModSounds.ESPERA_PISCINA.get();
                case APARCAMIENTO -> com.miscelanea.registry.ModSounds.ESPERA_APARCAMIENTO.get();
                case COLEGIO -> com.miscelanea.registry.ModSounds.ESPERA_COLEGIO.get();
                case CENTRO -> com.miscelanea.registry.ModSounds.ESPERA_CENTRO.get();
                case METRO -> com.miscelanea.registry.ModSounds.ESPERA_METRO.get();
            };
        }

        /** Volumen de cada bucle a tope: todos igualados (medidos en LUFS: feria -24,6, hotel -26,5, recreativa -21,3,
         * sala -27, sin texturas -20,3, piscina -24,3, garaje -19, colegio -25,4, centro comercial -19,9 y metro -23,1). */
        static float ganancia(EsperaGenerador.Zona z) {
            return switch (z) {
                case FERIA -> 0.50F;
                case SALA -> 0.62F;
                case RECREATIVA -> 0.36F;
                case HOTEL -> 0.60F;
                case SIN_TEXTURA -> 0.33F;
                case PISCINA -> 0.48F;
                case APARCAMIENTO -> 0.26F;
                case COLEGIO -> 0.55F;
                case CENTRO -> 0.29F;
                case METRO -> 0.44F;
            };
        }

        static float volumen(EsperaGenerador.Zona z) {
            return MEZCLA.getOrDefault(z, 0F) * ganancia(z);
        }

        /** Para la prueba: qué ambientes suenan y a qué volumen, cuántos sustos han sonado y si hay una tele cerca. */
        public static String sonando() {
            StringBuilder b = new StringBuilder();
            MEZCLA.forEach((z, v) -> {
                if (v > 0) b.append(z).append(String.format("=%.2f ", volumen(z)));
            });
            if (b.length() == 0) b.append("ninguno ");
            b.append("sustos=").append(sustos);
            if (tele != null && !tele.isStopped()) b.append(" tele=").append(tele.pos.toShortString());
            return b.toString();
        }

        /** Para la prueba: adelanta el próximo susto. */
        public static void sustoYa() {
            proximo = 1;
        }

        @SubscribeEvent
        public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
            if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            LiminalCliente.Maniquies.tick();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != nivel) {
                // Minecraft para todos los sonidos al cambiar de mundo: lo que hubiera ya no suena
                nivel = mc.level;
                SONANDO.clear();
                COLA.clear();
                tele = null;
            }
            if (mc.isPaused()) return;
            edad++;
            EsperaGenerador.Zona aqui = mc.player != null && Niebla.aqui() ? Niebla.zona() : null;
            var sm = mc.getSoundManager();
            for (EsperaGenerador.Zona z : EsperaGenerador.Zona.values()) {
                float v = MEZCLA.getOrDefault(z, 0F), meta = z == aqui ? 1 : 0;
                v = meta > v ? Math.min(meta, v + 1 / 40F) : Math.max(meta, v - 1 / 40F);
                if (v <= 0) {
                    MEZCLA.remove(z);
                    Bucle b = SONANDO.remove(z);
                    if (b != null) b.parar();
                    continue;
                }
                MEZCLA.put(z, v);
                Bucle b = SONANDO.get(z);
                // si se ha cortado desde fuera (ha sonado y ya no está), se vuelve a poner
                if (b == null || b.isStopped() || (b.tocado && !sm.isActive(b))) {
                    b = new Bucle(z);
                    SONANDO.put(z, b);
                    sm.play(b);
                }
            }
            if (aqui == null) {
                COLA.clear();
                if (tele != null) tele.parar();
                tele = null;
                return;
            }
            // sustos
            if (--proximo <= 0) {
                planear(mc, aqui);
                proximo = 500 + mc.player.getRandom().nextInt(1300);
            }
            while (!COLA.isEmpty() && COLA.peek().t() <= edad) {
                Susto s = COLA.poll();
                mc.level.playLocalSound(s.x(), s.y(), s.z(), s.s(), net.minecraft.sounds.SoundSource.AMBIENT, s.v(), s.p(), false);
                sustos++;
            }
            if (edad % 10 == 0) teles(mc);
        }

        /** El eco de los pasos en las salas vacías (ver {@link LiminalCliente}). */
        @SubscribeEvent
        public static void sonido(net.minecraftforge.client.event.sound.PlaySoundEvent e) {
            LiminalCliente.Sonidos.sonido(e);
        }

        /** Programa un susto según la zona (todo con sonidos del juego, cambiados de tono). */
        private static void planear(Minecraft mc, EsperaGenerador.Zona z) {
            if (!COLA.isEmpty()) return;
            var p = mc.player;
            var r = p.getRandom();
            int tipo = r.nextInt(3);
            if (tipo == 0) {
                // pasos que vienen por detrás y se paran
                net.minecraft.sounds.SoundEvent paso = switch (z) {
                    case RECREATIVA, HOTEL -> net.minecraft.sounds.SoundEvents.WOOL_STEP;
                    case PISCINA, CENTRO -> net.minecraft.sounds.SoundEvents.CALCITE_STEP;
                    case SIN_TEXTURA -> net.minecraft.sounds.SoundEvents.GRASS_STEP;
                    default -> net.minecraft.sounds.SoundEvents.STONE_STEP;
                };
                var atras = p.getLookAngle().multiply(-1, 0, -1).normalize();
                int n = 4 + r.nextInt(4);
                for (int i = 0; i < n; i++) {
                    double d = 13 - i * 1.1;
                    COLA.add(new Susto(edad + 10 + i * 9, paso, p.getX() + atras.x * d + (i % 2 == 0 ? 0.3 : -0.3), p.getY(), p.getZ() + atras.z * d,
                            0.5F, 0.85F + r.nextFloat() * 0.1F));
                }
                return;
            }
            double ang = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 8;
            double x = p.getX() + Math.cos(ang) * d, y = p.getEyeY(), zz = p.getZ() + Math.sin(ang) * d;
            switch (z) {
                case FERIA -> {
                    if (tipo == 1) {
                        // tres notas del organillo, desafinadas, que no terminan la melodía
                        float[] notas = {0.75F, 0.63F, 0.56F};
                        for (int i = 0; i < 3; i++)
                            COLA.add(new Susto(edad + i * 12, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), x, y, zz, 0.45F, notas[i] * 0.97F));
                    } else
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, x, y, zz, 0.6F, 0.5F));   // una persiana metálica
                }
                case SALA -> {
                    if (tipo == 1) {
                        // el timbre del turno (nadie te va a llamar)
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), x, y + 1, zz, 0.5F, 1.19F));
                        COLA.add(new Susto(edad + 9, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), x, y + 1, zz, 0.5F, 0.94F));
                    } else
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.WOOD_PLACE, x, y - 1, zz, 0.5F, 0.55F));    // una silla que se arrastra
                }
                case RECREATIVA -> {
                    if (tipo == 1) {
                        // una máquina que arranca sola: la musiquilla de empezar partida, lejos
                        float[] notas = {1.0F, 1.26F, 1.5F, 2.0F};
                        for (int i = 0; i < 4; i++)
                            COLA.add(new Susto(edad + i * 3, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), x, y, zz, 0.35F, notas[i]));
                    } else
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.CHAIN_PLACE, x, y - 1, zz, 0.45F, 1.6F));   // una moneda que cae
                }
                case HOTEL -> {
                    if (tipo == 1) {
                        // tres golpes en una puerta
                        for (int i = 0; i < 3; i++)
                            COLA.add(new Susto(edad + i * 6, net.minecraft.sounds.SoundEvents.WOOD_HIT, x, y, zz, 0.7F, 0.7F));
                    } else
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, x, y, zz, 0.55F, 0.8F));
                }
                case SIN_TEXTURA -> {
                    if (tipo == 1)
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, x, y, zz, 0.5F, 0.5F));
                    else
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, x, y, zz, 0.3F, 0.5F));
                }
                case PISCINA -> {
                    if (tipo == 1)
                        // algo que cae al agua (al dedo de agua) al fondo de la piscina
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, x, y - 1, zz, 0.35F, 0.6F));
                    else
                        // el silbato del socorrista, lejos y desafinado
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_FLUTE.value(), x, y + 1, zz, 0.4F, 1.7F));
                }
                case APARCAMIENTO -> {
                    if (tipo == 1)
                        // la puerta de un coche que se cierra, al fondo de la planta
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, x, y, zz, 0.5F, 0.7F));
                    else {
                        // una alarma que empieza y se calla enseguida
                        for (int i = 0; i < 4; i++)
                            COLA.add(new Susto(edad + i * 7, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), x, y, zz, 0.3F, i % 2 == 0 ? 1.4F : 1.1F));
                    }
                }
                case COLEGIO -> {
                    if (tipo == 1) {
                        // un balón que bota en el gimnasio
                        for (int i = 0; i < 4; i++)
                            COLA.add(new Susto(edad + i * (9 - i * 2), net.minecraft.sounds.SoundEvents.WOOL_PLACE, x, y - 1, zz, 0.5F - i * 0.1F, 0.6F));
                    } else
                        // el timbre del recreo, ahogado, como si sonara en otro edificio
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), x, y + 2, zz, 0.35F, 0.6F));
                }
                case METRO -> {
                    if (tipo == 1)
                        // un tren que pasa por otro tunel y no llega nunca a este anden
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.MINECART_INSIDE, x, y, zz, 0.5F, 0.6F));
                    else
                        // el aviso de que se cierran las puertas, de un tren que no esta
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), x, y + 1, zz, 0.45F, 1.5F));
                }
                case CENTRO -> {
                    if (tipo == 1)
                        // una persiana que alguien sube un poco y vuelve a bajar
                        COLA.add(new Susto(edad, net.minecraft.sounds.SoundEvents.CHAIN_STEP, x, y, zz, 0.6F, 0.5F));
                    else
                        // el aviso de megafonía (las tres notas) sin aviso detrás
                        for (int i = 0; i < 3; i++)
                            COLA.add(new Susto(edad + i * 8, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), x, y + 3, zz, 0.35F, new float[]{1.19F, 0.94F, 0.79F}[i]));
                }
            }
        }

        /** El ruido de la tele más cercana que el cliente ha visto hace poco (las teles se apuntan solas al dibujarse). */
        private static void teles(Minecraft mc) {
            long ahora = System.currentTimeMillis();
            com.miscelanea.espera.TeleBlock.VISTAS.entrySet().removeIf(en -> ahora - en.getValue() > 10000);
            net.minecraft.core.BlockPos mejor = null;
            double dm = 14 * 14;
            for (var en : com.miscelanea.espera.TeleBlock.VISTAS.keySet()) {
                double d = en.distToCenterSqr(mc.player.position());
                if (d < dm && mc.level.getBlockState(en).getBlock() instanceof com.miscelanea.espera.TeleBlock) {
                    dm = d;
                    mejor = en;
                }
            }
            if (tele != null && (mejor == null || !tele.pos.equals(mejor) || tele.isStopped() || (tele.tocado && !mc.getSoundManager().isActive(tele)))) {
                tele.parar();
                tele = null;
            }
            if (mejor != null && tele == null) {
                tele = new BucleTele(mejor);
                mc.getSoundManager().play(tele);
            }
        }
    }

    /** El bucle de fondo de una zona: su volumen lo lleva {@link Ambiente} (aquí solo se copia). */
    static final class Bucle extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        final EsperaGenerador.Zona zona;
        /** Ha llegado a sonar (si luego deja de estar activo es que lo han cortado). */
        boolean tocado;

        Bucle(EsperaGenerador.Zona z) {
            super(Ambiente.sonido(z), net.minecraft.sounds.SoundSource.AMBIENT, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            zona = z;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = Math.max(0.001F, Ambiente.volumen(z));
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void parar() {
            stop();
        }

        @Override
        public void tick() {
            tocado = true;
            float v = Ambiente.volumen(zona);
            if (v <= 0) stop();
            else volume = v;
        }
    }

    /** El siseo de la tele sin señal, con posición (se oye a unos 10 bloques). */
    static final class BucleTele extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        final net.minecraft.core.BlockPos pos;
        boolean tocado;

        BucleTele(net.minecraft.core.BlockPos p) {
            super(com.miscelanea.registry.ModSounds.ESPERA_TELE.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            pos = p;
            looping = true;
            delay = 0;
            volume = 0.3F;
            x = p.getX() + 0.5;
            y = p.getY() + 0.5;
            z = p.getZ() + 0.5;
        }

        void parar() {
            stop();
        }

        @Override
        public void tick() {
            tocado = true;
        }
    }
}
