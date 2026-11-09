package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Winchester 1873 de palanca, «el arma que conquistó el Oeste». Usa las mismas balas que el revólver (como el de
 * verdad con el Colt). Tras cada disparo hay que bajar y subir la palanca (lo hace solo, con su animación); a caballo
 * (o montado en lo que sea) la palanca se gira con una mano, en un giro completo del arma, y va algo más deprisa.
 * Se recarga bala a bala por la portilla del costado manteniendo agachado + clic derecho.
 */
public class WinchesterItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("winchester_disparo");

    public WinchesterItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad() {
        return EquilibrioArmas.winchesterCapacidad();
    }

    @Override
    public int ticksRecarga() {
        return EquilibrioArmas.winchesterRecarga();
    }

    @Override
    public boolean recargaPorBala() {
        return true;
    }

    @Override
    public boolean tieneMunicion(Player p) {
        return !buscar(p, x -> x.is(ModItems.REVOLVER_BALA.get())).isEmpty();
    }

    @Override
    protected int gastarMunicion(ServerPlayer p, ItemStack s) {
        ItemStack m = buscar(p, x -> x.is(ModItems.REVOLVER_BALA.get()));
        if (m.isEmpty()) return 0;
        m.shrink(1);
        return 1;
    }

    @Override
    public ItemStack iconoMunicion() {
        return new ItemStack(ModItems.REVOLVER_BALA.get());
    }

    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.WINCHESTER_CARGAR.get();
    }

    /** Ticks de la palanca: a caballo, con el giro, un 30 % menos. */
    public static int palanca(Player p) {
        int t = EquilibrioArmas.winchesterPalanca();
        return p.isPassenger() ? Math.max(2, Math.round(t * 0.7F)) : t;
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return palanca(p);
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        double alcance = EquilibrioArmas.winchesterAlcance();
        tiro(p, EquilibrioArmas.winchesterDano(), alcance, alcance * 0.5, 0.7F, 0.005, 0.2F, DANO, false, null);
        efectos(l, p, boca(p, 1.0, 0.25, 0.2), ModSounds.WINCHESTER_DISPARO.get(), 4.0F, 0.95F, 3, false);
        // la palanca: el casquillo sale por arriba a mitad del recorrido
        casquillo(l, p, boca(p, 0.35, 0.22, 0.12));
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.WINCHESTER_PALANCA.get(), SoundSource.PLAYERS, 0.8F, p.isPassenger() ? 1.15F : 1.0F);
        LogrosMiscelanea.dar(p, "winchester_disparo");
        if (p.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) LogrosMiscelanea.dar(p, "winchester_giro");
    }
}
