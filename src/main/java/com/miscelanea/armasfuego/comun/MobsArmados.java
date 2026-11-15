package com.miscelanea.armasfuego.comun;

import com.miscelanea.ConfigServidor;
import com.miscelanea.armasfuego.oeste.DerringerItem;
import com.miscelanea.armasfuego.oeste.MosqueteItem;
import com.miscelanea.armasfuego.oeste.ThompsonItem;
import com.miscelanea.armasfuego.oeste.WinchesterItem;
import com.miscelanea.armasfuego.pistola.Revolver;
import com.miscelanea.armasfuego.pistola.RevolverItem;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Mobs con armas: los saqueadores (algunos, poco y configurable) y los zombis y husks de las estructuras que llevan una puesta
 * disparan de verdad con la misma balística que el jugador, y cada arma tiene su manera: el revólver dispara seis tiros
 * deprisa, la Winchester más despacio y más lejos, la Derringer solo dos y de cerca, la Thompson en ráfagas de tres y el
 * mosquete un solo tiro enorme cada mucho. Cada estructura reparte las suyas (la pirámide, mosquetes y Derringers; la mina,
 * revólveres y Winchesters). El daño y la cadencia de la config (sección mobs_armados) son los de la Winchester y las demás
 * armas los escalan. El arma se suelta con una probabilidad (por defecto, baja).
 */
public final class MobsArmados {
    private MobsArmados() {}

    /** Etiqueta: ya se ha tirado el dado para este mob (no se repite al recargar el mundo). */
    private static final String TIRADO = "miscelanea_tirado";
    private static final int RADIO = 64;
    /** Disparos hechos por mobs desde que arrancó el juego (lo mira la prueba del CI). */
    public static volatile int disparos;
    private static final Set<Mob> ARMADOS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Estado> ESTADOS = new WeakHashMap<>();

    /** Lo que recuerda cada mob armado: cargador, cuándo puede disparar, si apunta, recarga o está aturdido. */
    private static final class Estado {
        long proximo, apuntaDesde, disparaEn = -1, recargaIni = -1, recargaFin = -1, strafe, vistoHerido;
        int balas = -1, pendientes, reaccion;
        java.util.UUID objetivo;
        boolean aviso;
        // táctica: dónde se cubre y dónde se asoma, el modo y hasta cuándo, y el punto de flanqueo
        net.minecraft.core.BlockPos cobertura, asomo;
        Modo modo = Modo.ABIERTO;
        long modoHasta, buscaCobertura, flanqueoHasta;
        int disparosAsomado;
        Vec3 flanqueo;

        boolean tactico() {
            return modo != Modo.ABIERTO || flanqueo != null;
        }
    }

    /**
     * ABIERTO: lo de siempre (dispara, da pasos de lado, retrocede si te acercas). A_CUBIERTO: va corriendo a su
     * cobertura (para recargar o porque le han dado). CUBIERTO: quieto tras el bloque. ASOMANDO: sale a un sitio desde
     * el que te ve, dispara unas veces y vuelve.
     */
    private enum Modo { ABIERTO, A_CUBIERTO, CUBIERTO, ASOMANDO }

    /** Cómo dispara cada arma, relativo a la Winchester (daño y cadencia de la config). */
    private record Perfil(float dano, float cadencia, int balas, int recarga, double alcance, double disp, int rafaga,
                          ResourceKey<DamageType> tipo, SoundEvent sonido, float tono, float vol, SoundEvent cargar,
                          int aviso, int pasos, float retroceso) {}

    private static Perfil perfil(Item i) {
        if (i instanceof RevolverItem)
            return new Perfil(1.1F, 0.6F, 6, 70, 28, 1.0, 1, Revolver.DISPARO, ModSounds.REVOLVER_DISPARO.get(), 1.0F, 2.5F, ModSounds.REVOLVER_CARGAR.get(), 8, 6, 0.10F);
        if (i instanceof WinchesterItem)
            return new Perfil(1.0F, 1.0F, 6, 80, EquilibrioArmas.winchesterAlcance() * 0.5, 0.8, 1, WinchesterItem.DANO, ModSounds.WINCHESTER_DISPARO.get(), 0.95F, 3.0F, ModSounds.WINCHESTER_CARGAR.get(), 12, 6, 0.14F);
        if (i instanceof DerringerItem)
            return new Perfil(1.4F, 0.8F, 2, 70, 18, 1.3, 1, DerringerItem.DANO, ModSounds.DERRINGER_DISPARO.get(), 1.1F, 2.0F, ModSounds.REVOLVER_CARGAR.get(), 6, 2, 0.08F);
        if (i instanceof ThompsonItem)
            return new Perfil(0.55F, 1.0F, 30, 100, 26, 1.6, 3, ThompsonItem.DANO, ModSounds.THOMPSON_DISPARO.get(), 1.0F, 2.5F, ModSounds.WINCHESTER_CARGAR.get(), 8, 3, 0.05F);
        if (i instanceof MosqueteItem)
            return new Perfil(2.2F, 2.0F, 1, 100, 40, 0.5, 1, MosqueteItem.DANO, ModSounds.MOSQUETE_DISPARO.get(), 0.9F, 4.0F, ModSounds.MOSQUETE_BAQUETA.get(), 30, 4, 0.30F);
        return null;
    }

    /** ¿Lleva un arma de fuego de las que sabe usar? (revólver, Winchester, Derringer, Thompson o mosquete) */
    public static boolean armado(LivingEntity m) {
        return perfil(m.getMainHandItem().getItem()) != null;
    }

    public static void tick(MinecraftServer srv) {
        if (!ConfigServidor.mobsArmados()) return;
        if (srv.getTickCount() % 10 == 0) refrescar(srv);
        Iterator<Mob> it = ARMADOS.iterator();
        while (it.hasNext()) {
            Mob m = it.next();
            if (!m.isAlive() || m.isRemoved() || !armado(m) || !(m.level() instanceof ServerLevel l)) {
                it.remove();
                continue;
            }
            if (l.getNearestPlayer(m, RADIO) != null) disparar(l, m);
        }
    }

    /** Busca los saqueadores y zombis cercanos a los jugadores, tira el dado a los saqueadores nuevos y apunta a los armados. */
    private static void refrescar(MinecraftServer srv) {
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            ServerLevel l = p.serverLevel();
            for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RADIO), e -> e.isAlive() && (e instanceof Pillager || e instanceof Zombie))) {
                if (m instanceof Pillager pi && !m.getTags().contains(TIRADO)) tirar(pi);
                reponerArma(m);
                if (armado(m)) ARMADOS.add(m);
            }
        }
    }

    /**
     * Los mobs de las estructuras llevan la etiqueta miscelanea_arma_<arma>: al aparecer, vanilla puede cambiarles lo que llevan (a un
     * saqueador le pone una ballesta), así que si no llevan un arma de fuego se les pone la de la etiqueta.
     */
    private static void reponerArma(Mob m) {
        if (armado(m)) return;
        for (String t : m.getTags()) {
            if (!t.startsWith("miscelanea_arma_")) continue;
            Item arma = switch (t.substring("miscelanea_arma_".length())) {
                case "revolver" -> ModItems.REVOLVER.get();
                case "winchester" -> ModItems.WINCHESTER.get();
                case "derringer" -> ModItems.DERRINGER.get();
                case "thompson" -> ModItems.THOMPSON.get();
                case "mosquete" -> ModItems.MOSQUETE.get();
                default -> null;
            };
            if (arma == null) continue;
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(arma));
            m.setDropChance(EquipmentSlot.MAINHAND, ConfigServidor.mobsSuelta());
            return;
        }
    }

    /** Un saqueador nuevo: con la probabilidad de la config sale con un arma (revólver o Winchester). */
    private static void tirar(Raider m) {
        m.addTag(TIRADO);
        double prob = m.isPatrolLeader() ? ConfigServidor.mobsProbCapitan() : ConfigServidor.mobsProb();
        if (m.getRandom().nextDouble() >= prob) return;
        ItemStack arma;
        if (m.getRandom().nextFloat() < 0.4F) {
            arma = new ItemStack(ModItems.REVOLVER.get());
        } else {
            arma = new ItemStack(ModItems.WINCHESTER.get());
            WinchesterItem.ponerDeposito(arma, Collections.nCopies(6, ModItems.REVOLVER_BALA.get()));
        }
        arma.setDamageValue(arma.getMaxDamage() / 2 + m.getRandom().nextInt(Math.max(1, arma.getMaxDamage() / 3)));
        m.setItemSlot(EquipmentSlot.MAINHAND, arma);
        m.setDropChance(EquipmentSlot.MAINHAND, ConfigServidor.mobsSuelta());
    }

    /** Pone o quita la pose de recarga: el saqueador "carga" como con la ballesta; el zombi baja los brazos. */
    private static void posaRecarga(Mob m, boolean recargando) {
        if (m instanceof Pillager p) p.setChargingCrossbow(recargando);
        else if (recargando) m.setAggressive(false);
        else m.setAggressive(m.getTarget() != null);
    }

    private static void sonidoRecarga(ServerLevel l, Mob m, Perfil pf, int paso) {
        float tono = 0.8F + 0.4F * paso / Math.max(1, pf.pasos() - 1);
        l.playSound(null, m.getX(), m.getY(), m.getZ(), pf.cargar(), SoundSource.HOSTILE, 0.7F, tono);
        if (pf.pasos() >= 6 && paso % 2 == 0) ArmaFuegoItem.casquillo(l, m, m.getEyePosition().add(0, -0.4, 0));
    }

    private static void disparar(ServerLevel l, Mob m) {
        Perfil pf = perfil(m.getMainHandItem().getItem());
        if (pf == null) return;
        boolean saqueador = m instanceof Pillager;
        Estado e = ESTADOS.computeIfAbsent(m, k -> new Estado());
        if (e.balas < 0) e.balas = pf.balas();
        long t = l.getGameTime();
        LivingEntity v = m.getTarget();
        boolean objetivo = v != null && v.isAlive();
        // recargando: la pose, los sonidos paso a paso y el final; si le dan, se asusta y tarda un poco más
        if (e.recargaFin >= 0) {
            if (m.hurtTime > 0 && t - e.vistoHerido > 10) {
                e.vistoHerido = t;
                e.recargaFin += 8;
                l.playSound(null, m.getX(), m.getY(), m.getZ(), ModSounds.MOB_CLIC_SECO.get(), SoundSource.HOSTILE, 0.6F, 0.8F);
            }
            long dur = Math.max(1, e.recargaFin - e.recargaIni), paso = Math.max(1, dur / Math.max(1, pf.pasos()));
            if ((t - e.recargaIni) % paso == 0 && t > e.recargaIni && t < e.recargaFin) sonidoRecarga(l, m, pf, (int) ((t - e.recargaIni) / paso));
            if (t >= e.recargaFin) {
                e.recargaIni = e.recargaFin = -1;
                e.balas = pf.balas();
                e.proximo = t + 6;
                posaRecarga(m, false);
                if (e.modo == Modo.CUBIERTO) e.modoHasta = t;   // recargado: ya puede asomarse
                l.playSound(null, m.getX(), m.getY(), m.getZ(), ModSounds.MOB_MONTAR.get(), SoundSource.HOSTILE, 0.8F, 1.2F);
            }
            return;
        }
        // sin objetivo: aprovecha para recargar lo gastado
        if (!objetivo) {
            e.objetivo = null;
            e.disparaEn = -1;
            e.modo = Modo.ABIERTO;
            e.flanqueo = null;
            if (e.balas < pf.balas() && e.balas <= pf.balas() * 0.5) empezarRecarga(l, m, pf, e, t);
            return;
        }
        double alcance = pf.alcance();
        double dist = m.distanceTo(v);
        if (dist > alcance) return;
        m.getLookControl().setLookAt(v, 30F, 30F);
        boolean ruta = m.tickCount % 10 == 0;
        boolean vista = m.hasLineOfSight(v);
        if (!java.util.Objects.equals(e.objetivo, v.getUUID()) || !vista) {
            // objetivo nuevo (o lo ha perdido de vista): vuelve a apuntar desde cero y tarda en reaccionar
            if (vista) {
                e.objetivo = v.getUUID();
                e.apuntaDesde = t;
                e.reaccion = 12 + m.getRandom().nextInt(18);
                e.aviso = m.getRandom().nextFloat() < 0.35F;     // el primer tiro, de aviso (a un lado)
            }
            e.disparaEn = -1;
            if (saqueador && ruta && dist > 8 && !e.tactico()) m.getNavigation().moveTo(v, 1.0);
            if (!vista) {
                if (saqueador && ConfigServidor.mobsTactica()) tactica(l, m, v, e, pf, t, dist, false);
                return;
            }
        }
        boolean controla = saqueador && ConfigServidor.mobsTactica() && tactica(l, m, v, e, pf, t, dist, true);
        if (saqueador && ruta && !controla) {
            if (dist < 5) {
                // demasiado cerca: retrocede (y sigue disparando)
                Vec3 a = m.position().subtract(v.position()).normalize().scale(6);
                m.getNavigation().moveTo(m.getX() + a.x, m.getY(), m.getZ() + a.z, 1.2);
            } else if (dist > alcance * 0.7) m.getNavigation().moveTo(v, 1.0);
            else if (t >= e.strafe) {
                // un paso de lado de vez en cuando, para no ser un blanco quieto
                Vec3 d = v.position().subtract(m.position());
                Vec3 lado = new Vec3(-d.z, 0, d.x).normalize().scale((m.getRandom().nextBoolean() ? 1 : -1) * (3 + m.getRandom().nextInt(3)));
                m.getNavigation().moveTo(m.getX() + lado.x, m.getY(), m.getZ() + lado.z, 1.0);
                e.strafe = t + 40 + m.getRandom().nextInt(50);
            } else if (m.getNavigation().isDone()) m.getNavigation().stop();
        }
        // le han dado: se sobresalta, tarda en volver a apuntar
        if (m.hurtTime > 0 && t - e.vistoHerido > 10) {
            e.vistoHerido = t;
            e.proximo = Math.max(e.proximo, t + 8);
            e.disparaEn = -1;
        }
        if (t - e.apuntaDesde < e.reaccion || t < e.proximo) return;
        if (e.balas <= 0) {
            // recargar a cubierto: primero corre a su cobertura (si la encuentra), luego recarga
            if (saqueador && ConfigServidor.mobsTactica() && (e.modo == Modo.A_CUBIERTO || irACubierto(l, m, v, e, t, 50))) return;
            empezarRecarga(l, m, pf, e, t);
            return;
        }
        // el aviso: antes de disparar se oye el gatillo (y el mosquete echa chispa): da tiempo a moverse
        if (e.disparaEn < 0) {
            e.disparaEn = t + pf.aviso();
            if (pf.aviso() > 0) {
                l.playSound(null, m.getX(), m.getY(), m.getZ(), saqueadorSonidoAviso(pf), SoundSource.HOSTILE, 0.8F, 1.0F);
                Vec3 q = m.getEyePosition().add(m.getViewVector(1F).scale(0.8));
                l.sendParticles(pf.aviso() >= 20 ? net.minecraft.core.particles.ParticleTypes.SMALL_FLAME : net.minecraft.core.particles.ParticleTypes.CRIT, q.x, q.y - 0.2, q.z, 3, 0.05, 0.05, 0.05, 0.02);
            }
        }
        if (pf.aviso() >= 20 && (e.disparaEn - t) % 4 == 0 && t < e.disparaEn) {
            Vec3 q = m.getEyePosition().add(m.getViewVector(1F).scale(0.8));
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, q.x, q.y - 0.2, q.z, 1, 0.03, 0.03, 0.03, 0.01);
        }
        if (t < e.disparaEn) return;
        e.disparaEn = -1;
        // un fallo del arma: clic en seco y tiene que volver a montarla
        if (m.getRandom().nextFloat() < 0.03F) {
            l.playSound(null, m.getX(), m.getY(), m.getZ(), ModSounds.MOB_CLIC_SECO.get(), SoundSource.HOSTILE, 0.9F, 1.0F);
            e.proximo = t + 25;
            return;
        }
        e.balas--;
        disparos++;
        if (e.pendientes <= 0) e.pendientes = pf.rafaga();
        e.pendientes--;
        e.proximo = t + (e.pendientes > 0 ? 3 : Math.max(4, Math.round(ConfigServidor.mobsCadencia() * pf.cadencia())));
        Vec3 ojo = m.getEyePosition();
        Vec3 punto = v.position().add(0, v.getBbHeight() * 0.6, 0);
        if (e.aviso) {
            // el tiro de aviso: se va a un lado del objetivo
            Vec3 d = punto.subtract(ojo);
            punto = punto.add(new Vec3(-d.z, 0, d.x).normalize().scale(2.2 * (m.getRandom().nextBoolean() ? 1 : -1)));
            e.aviso = false;
        }
        Vec3 mira = punto.subtract(ojo).normalize();
        double base = switch (l.getDifficulty()) {
            case HARD -> 0.03;
            case NORMAL -> 0.06;
            default -> 0.10;
        };
        // fallan más cuanto más lejos, si el blanco se mueve, si no han tenido tiempo de afinar y si están heridos
        double afinado = Math.min(1.0, (t - e.apuntaDesde - e.reaccion) / 30.0);
        double mov = Math.min(1.0, v.getDeltaMovement().horizontalDistance() * 4.0);
        double disp = pf.disp() * base * (1 + dist / alcance * 1.5) * (1 + mov * 0.9) * (1.7 - 0.7 * afinado)
                * (m.getHealth() < m.getMaxHealth() * 0.35F ? 1.5 : 1.0) * (v.isCrouching() ? 1.25 : 1.0) * ConfigServidor.mobsFallo();
        Vec3 dir = Balistica.dispersar(mira, disp, m.getRandom());
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        Vec3 boca = ojo.add(mira.scale(0.8)).add(der.scale(0.3)).add(0, -0.2, 0);
        Balistica.Tiro tiro = Balistica.trazar(l, m, ojo, dir, alcance, boca, null);
        Balistica.trazo(l, boca, tiro.pos(), null);
        if (tiro.entidad() != null && !(tiro.entidad() instanceof Raider)) {
            float d = ConfigServidor.mobsDano() * pf.dano() * Balistica.caida(tiro.distancia(), alcance * 0.5, alcance, 0.6F);
            Balistica.herir(tiro.entidad(), m, d, 0.1F, pf.tipo(), pf.rafaga() > 1);
        }
        Balistica.impacto(l, m, tiro);
        ArmaFuegoItem.efectos(l, m, boca, pf.sonido(), pf.vol(), pf.tono(), 3, true);
        if (e.modo == Modo.ASOMANDO) e.disparosAsomado++;
        // el retroceso: el tirador se echa un poco hacia atrás
        m.setDeltaMovement(m.getDeltaMovement().add(mira.x * -pf.retroceso(), 0.02, mira.z * -pf.retroceso()));
    }

    // ------------------------------------------------------------------ táctica (saqueadores)

    /**
     * La táctica de un saqueador armado con objetivo. Devuelve true si este tick manda ella en el movimiento (entonces
     * no se dan los pasos de lado de siempre). {@code vista}: si ahora ve al objetivo.
     */
    private static boolean tactica(ServerLevel l, Mob m, LivingEntity v, Estado e, Perfil pf, long t, double dist, boolean vista) {
        // le han dado: a veces se cubre un rato (más cuanto más herido)
        if (m.hurtTime > 0 && e.modo == Modo.ABIERTO && m.getRandom().nextFloat() < (m.getHealth() < m.getMaxHealth() * 0.5F ? 0.7F : 0.35F))
            irACubierto(l, m, v, e, t, 30 + m.getRandom().nextInt(30));
        switch (e.modo) {
            case A_CUBIERTO -> {
                if (e.cobertura == null || t > e.modoHasta) {
                    e.modo = Modo.ABIERTO;
                    return false;
                }
                if (m.blockPosition().distManhattan(e.cobertura) <= 1 || m.getNavigation().isDone()) {
                    e.modo = Modo.CUBIERTO;
                    e.modoHasta = t + (e.balas <= 0 ? 0 : 30 + m.getRandom().nextInt(30));
                    m.getNavigation().stop();
                    if (e.balas <= 0) empezarRecarga(l, m, pf, e, t);
                } else if (m.tickCount % 10 == 0) {
                    m.getNavigation().moveTo(e.cobertura.getX() + 0.5, e.cobertura.getY(), e.cobertura.getZ() + 0.5, 1.25);
                }
                return true;
            }
            case CUBIERTO -> {
                m.getNavigation().stop();
                if (t < e.modoHasta || e.recargaFin >= 0) return true;
                // a asomarse: un sitio cerca de la cobertura desde el que se le vea
                e.asomo = buscar(l, m, v, e.cobertura, 3, true);
                if (e.asomo == null) {
                    e.modo = Modo.ABIERTO;
                    return false;
                }
                e.modo = Modo.ASOMANDO;
                e.disparosAsomado = 0;
                e.modoHasta = t + 80;
                m.getNavigation().moveTo(e.asomo.getX() + 0.5, e.asomo.getY(), e.asomo.getZ() + 0.5, 1.0);
                return true;
            }
            case ASOMANDO -> {
                if (m.tickCount % 10 == 0 && !vista && e.asomo != null)
                    m.getNavigation().moveTo(e.asomo.getX() + 0.5, e.asomo.getY(), e.asomo.getZ() + 0.5, 1.0);
                if (vista) m.getNavigation().stop();
                // unos tiros y vuelve a cubrirse (o se queda fuera si la cobertura ya no vale)
                if (e.disparosAsomado >= 2 + m.getRandom().nextInt(2) || t > e.modoHasta) {
                    if (e.cobertura != null && m.getRandom().nextFloat() < 0.6F) {
                        e.modo = Modo.A_CUBIERTO;
                        e.modoHasta = t + 60;
                    } else e.modo = Modo.ABIERTO;
                }
                return true;
            }
            default -> {
                return flanquear(l, m, v, e, t, dist);
            }
        }
    }

    /** Busca una cobertura y, si la hay, va hacia ella durante como mucho {@code hasta} ticks. */
    private static boolean irACubierto(ServerLevel l, Mob m, LivingEntity v, Estado e, long t, int hasta) {
        if (t < e.buscaCobertura) return false;
        e.buscaCobertura = t + 40;
        net.minecraft.core.BlockPos c = buscar(l, m, v, m.blockPosition(), 7, false);
        if (c == null) return false;
        e.cobertura = c;
        e.modo = Modo.A_CUBIERTO;
        e.modoHasta = t + Math.max(hasta, 60);
        m.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.25);
        return true;
    }

    /**
     * Busca un sitio donde estar de pie a como mucho {@code radio} de {@code centro}: con {@code verle} = false, uno
     * donde el objetivo no le vea la cabeza (cobertura); con true, uno donde sí (para asomarse). Prueba unos 60 sitios
     * al azar y se queda con el más cercano al mob.
     */
    private static net.minecraft.core.BlockPos buscar(ServerLevel l, Mob m, LivingEntity v, net.minecraft.core.BlockPos centro, int radio, boolean verle) {
        net.minecraft.core.BlockPos mejor = null;
        double mejorD = Double.MAX_VALUE;
        Vec3 ojoV = v.getEyePosition();
        for (int i = 0; i < 60; i++) {
            net.minecraft.core.BlockPos q = centro.offset(m.getRandom().nextInt(radio * 2 + 1) - radio, m.getRandom().nextInt(3) - 1,
                    m.getRandom().nextInt(radio * 2 + 1) - radio);
            if (!l.getBlockState(q).getCollisionShape(l, q).isEmpty() || !l.getBlockState(q.above()).getCollisionShape(l, q.above()).isEmpty()
                    || !l.getBlockState(q.below()).isFaceSturdy(l, q.below(), net.minecraft.core.Direction.UP)) continue;
            Vec3 cabeza = new Vec3(q.getX() + 0.5, q.getY() + m.getEyeHeight(), q.getZ() + 0.5);
            if (cabeza.distanceTo(ojoV) < 6) continue;
            boolean tapado = l.clip(new net.minecraft.world.level.ClipContext(ojoV, cabeza, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, v)).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
            if (tapado == verle) continue;
            double d = q.distSqr(m.blockPosition());
            if (d < mejorD) {
                mejorD = d;
                mejor = q;
            }
        }
        return mejor;
    }

    /**
     * Flanqueo: si hay varios saqueadores armados contra el mismo objetivo, uno de cada tres va de frente y los otros
     * dan un rodeo por la izquierda o por la derecha antes de disparar. El papel se reparte por el orden de los UUID.
     */
    private static boolean flanquear(ServerLevel l, Mob m, LivingEntity v, Estado e, long t, double dist) {
        if (e.flanqueo != null) {
            if (t > e.flanqueoHasta || m.position().distanceTo(e.flanqueo) < 2.5) {
                e.flanqueo = null;
                return false;
            }
            if (m.tickCount % 10 == 0) m.getNavigation().moveTo(e.flanqueo.x, e.flanqueo.y, e.flanqueo.z, 1.15);
            return true;
        }
        if (t < e.flanqueoHasta + 200 || m.tickCount % 20 != 0) return false;
        java.util.List<Mob> grupo = new java.util.ArrayList<>();
        for (Mob o : ARMADOS)
            if (o.level() == l && o instanceof Pillager && o.getTarget() == v && o.distanceTo(m) < 24) grupo.add(o);
        if (grupo.size() < 2) return false;
        grupo.sort(java.util.Comparator.comparing(Mob::getUUID));
        int papel = grupo.indexOf(m) % 3;
        if (papel == 0) return false;
        // el punto de rodeo: a 75 grados del frente, a la misma distancia
        Vec3 d = m.position().subtract(v.position());
        double ang = Math.toRadians(papel == 1 ? 75 : -75), c = Math.cos(ang), s2 = Math.sin(ang);
        Vec3 r = new Vec3(d.x * c - d.z * s2, 0, d.x * s2 + d.z * c);
        e.flanqueo = v.position().add(r.normalize().scale(Math.max(8, Math.min(dist, 14))));
        e.flanqueoHasta = t + 100;
        m.getNavigation().moveTo(e.flanqueo.x, e.flanqueo.y, e.flanqueo.z, 1.15);
        return true;
    }

    /**
     * Alerta: alguien ha disparado en {@code donde}. Los mobs armados sin objetivo que lo oyen (a menos de la distancia de
     * la config) acuden: si ha sido un jugador, a por él; si ha sido otro mob armado, a por el objetivo de ese mob.
     */
    public static void oirDisparo(ServerLevel l, net.minecraft.world.entity.Entity tirador, Vec3 donde) {
        int radio = ConfigServidor.mobsAlerta();
        if (radio <= 0 || !ConfigServidor.mobsArmados()) return;
        LivingEntity a = null;
        if (tirador instanceof net.minecraft.world.entity.player.Player pl) {
            if (pl.isCreative() || pl.isSpectator()) return;
            a = pl;
        } else if (tirador instanceof Mob mo && armado(mo)) a = mo.getTarget();
        if (a == null || !a.isAlive()) return;
        for (Mob m : ARMADOS) {
            if (m == tirador || m.level() != l || m.getTarget() != null || m.distanceToSqr(donde) > (double) radio * radio) continue;
            m.setTarget(a);
            m.getNavigation().moveTo(donde.x, donde.y, donde.z, 1.1);
        }
    }

    private static SoundEvent saqueadorSonidoAviso(Perfil pf) {
        return pf.aviso() >= 20 ? ModSounds.MOB_CHISPA.get()
                : pf.cargar() == ModSounds.WINCHESTER_CARGAR.get() ? ModSounds.WINCHESTER_PALANCA.get() : ModSounds.MOB_MONTAR.get();
    }

    private static void empezarRecarga(ServerLevel l, Mob m, Perfil pf, Estado e, long t) {
        e.recargaIni = t;
        e.recargaFin = t + pf.recarga();
        e.disparaEn = -1;
        posaRecarga(m, true);
        m.getNavigation().stop();
        sonidoRecarga(l, m, pf, 0);
    }
}
