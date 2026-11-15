package com.miscelanea.armasfuego.comun;

import com.miscelanea.ConfigServidor;
import com.miscelanea.armasfuego.pistola.HuirDelTiro;
import com.miscelanea.registry.ModEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El ruido de los disparos. Cada arma se oye a una distancia distinta (un cañón de mano, mucho más que una Derringer; la
 * cerbatana, nada) y el Silenciador la reduce casi a nada. Lo que lo oye reacciona: los monstruos (y los golems de hierro) van
 * a ver de dónde ha venido, y los animales y aldeanos echan a correr. El sculk y el Warden lo oyen por los eventos del juego.
 */
public final class Ruido {
    private Ruido() {}

    /** Último tick en que cada jugador provocó ruido (las armas automáticas no lo repiten en cada bala). */
    private static final Map<UUID, Long> ULTIMO = new HashMap<>();
    private static final int ESPERA = 8;
    /** Como mucho, tantos mobs reaccionan a cada disparo (el coste no depende de cuántos haya). */
    private static final int MAX_MOBS = 64;

    /** A cuántos bloques se oye un disparo del arma, sin contar la config ni el Silenciador. */
    public static double base(Item i) {
        if (i instanceof com.miscelanea.armasfuego.oeste.DerringerItem) return 22;
        if (i instanceof com.miscelanea.armasfuego.escopeta.EscopetaPresionItem) return 35;
        if (i instanceof com.miscelanea.armasfuego.pistola.RevolverItem) return 40;
        if (i instanceof com.miscelanea.armasfuego.oeste.ThompsonItem) return 50;
        if (i instanceof com.miscelanea.armasfuego.oeste.WinchesterItem) return 55;
        if (i instanceof com.miscelanea.armasfuego.oeste.MosqueteItem) return 60;
        if (i instanceof com.miscelanea.armasfuego.gatling.GatlingItem) return 65;
        if (i instanceof com.miscelanea.armasfuego.rifle.RifleItem) return 70;
        if (i instanceof com.miscelanea.armasfuego.oeste.CanonManoItem) return 80;
        return 40;
    }

    /** ¿Lleva Silenciador? (el disparo casi no suena) */
    public static boolean silencioso(ItemStack arma) {
        return arma != null && !arma.isEmpty() && com.miscelanea.armasfuego.comun.ArmaFuegoItem.nivel(ModEnchantments.SILENCIADOR, arma) > 0;
    }

    /** A cuántos bloques se oye un disparo de esa arma (con la config y el Silenciador). */
    public static double radio(ItemStack arma) {
        double r = base(arma.getItem()) * ConfigServidor.ruidoMultiplicador();
        if (silencioso(arma)) r *= ConfigServidor.ruidoSilenciador();
        return r;
    }

    /** Un jugador ha disparado en {@code pos} con el arma que lleva en la mano. */
    public static void disparo(ServerLevel l, Entity quien, Vec3 pos) {
        if (!(quien instanceof Player p)) return;
        alertar(l, p, pos, radio(p.getMainHandItem()), false);
    }

    /** Lo mismo con un radio base propio (la Gatling, que no es un objeto en la mano). */
    public static void disparo(ServerLevel l, Entity quien, Vec3 pos, double radioBase) {
        if (!(quien instanceof Player p)) return;
        alertar(l, p, pos, radioBase * ConfigServidor.ruidoMultiplicador(), false);
    }

    /**
     * Hace que lo que hay a {@code radio} bloques de {@code pos} oiga un ruido hecho por {@code quien}. Con {@code seguro} nadie
     * se salta el aviso por azar (para las pruebas).
     */
    public static void alertar(ServerLevel l, Player quien, Vec3 pos, double radio, boolean seguro) {
        if (!ConfigServidor.ruido() || radio < 4 || quien.isSpectator()) return;
        long t = l.getGameTime();
        if (!seguro) {
            Long antes = ULTIMO.get(quien.getUUID());
            if (antes != null && t - antes < ESPERA) return;
            ULTIMO.put(quien.getUUID(), t);
        }
        int n = 0;
        for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(pos, pos).inflate(radio), e -> e.isAlive() && !e.isNoAi())) {
            if (++n > MAX_MOBS) break;
            if (m instanceof Warden || m instanceof EnderMan) continue;
            if (m instanceof TamableAnimal ta && ta.isTame()) continue;
            if (!(m instanceof PathfinderMob pm)) continue;
            double d = m.position().distanceTo(pos);
            // sin línea de vista, el ruido llega más corto (paredes, cuevas)
            double alcance = m.hasLineOfSight(quien) ? radio : radio * 0.6;
            if (d > alcance) continue;
            if (!seguro && l.random.nextDouble() > 0.3 + 0.7 * (1 - d / alcance)) continue;
            if (m instanceof Enemy || m instanceof IronGolem) {
                if (m.getTarget() == null) InvestigarRuido.avisar(m, pos, 300);
            } else if ((m instanceof AbstractVillager || m instanceof Animal) && ConfigServidor.ruidoAsusta() && d <= alcance * 0.7) {
                HuirDelTiro.asustar(pm, pos, 100);
            }
        }
    }
}
