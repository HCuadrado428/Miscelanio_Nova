package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Derringer de dos cañones, la pistola de bolsillo de los tahúres del póker del Oeste. Dos tiros, poco alcance (el
 * daño baja deprisa con la distancia) y sale rapidísimo: el primer tiro justo después de sacarla pega más. En tercera
 * persona no se ve en la mano (el as en la manga) hasta que dispara. Se recarga partiéndola por la bisagra: agachado +
 * mantener clic derecho mete las dos balas de golpe.
 */
public class DerringerItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("derringer_disparo");
    /** Ticks que se deja ver en tercera persona tras disparar. */
    public static final int VISIBLE = 60;
    /** Ventana del truco de tahúr (ticks desde que se saca). */
    public static final int DESENFUNDE = 12;

    public DerringerItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad() {
        return 2;
    }

    @Override
    public int ticksRecarga() {
        return 24;
    }

    @Override
    public boolean tieneMunicion(Player p) {
        return !buscar(p, x -> x.is(ModItems.BALA_DERRINGER.get())).isEmpty();
    }

    @Override
    protected int gastarMunicion(ServerPlayer p, ItemStack s) {
        int hace = capacidad() - balas(s), n = 0;
        while (n < hace) {
            ItemStack m = buscar(p, x -> x.is(ModItems.BALA_DERRINGER.get()));
            if (m.isEmpty()) break;
            m.shrink(1);
            n++;
        }
        return n;
    }

    @Override
    public ItemStack iconoMunicion() {
        return new ItemStack(ModItems.BALA_DERRINGER.get());
    }

    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.DERRINGER_ABRIR.get();
    }

    @Override
    protected void empezarRecarga(ServerPlayer p, ItemStack s) {
        // se parte por la bisagra y caen los casquillos gastados
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DERRINGER_ABRIR.get(), SoundSource.PLAYERS, 0.7F, 1.25F);
        for (int i = balas(s); i < capacidad(); i++) casquillo(l, p, boca(p, 0.45, 0.3, 0.3));
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return 5;
    }

    /** ¿Se ve en tercera persona? (solo justo después de disparar). */
    public static boolean visible(ItemStack s, Level l) {
        return l != null && l.getGameTime() - disparoT(s) < VISIBLE;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        boolean tahur = desdeSacado(p) <= DESENFUNDE;
        float dano = EquilibrioArmas.derringerDano() * (tahur ? EquilibrioArmas.derringerDesenfunde() : 1F);
        double alcance = EquilibrioArmas.derringerAlcance();
        var t = tiro(p, dano, alcance, 3, 0.4F, tahur ? 0.004 : 0.02, 0.15F, DANO, false, null);
        efectos(l, p, boca(p, 0.6, 0.3, 0.25), ModSounds.DERRINGER_DISPARO.get(), 2.5F, 1.1F, 2, false);
        if (tahur) {
            l.sendParticles(ParticleTypes.ENCHANTED_HIT, t.pos().x, t.pos().y, t.pos().z, 8, 0.1, 0.1, 0.1, 0.2);
            if (t.entidad() != null) LogrosMiscelanea.dar(p, "derringer_tahur");
        }
        LogrosMiscelanea.dar(p, "derringer_disparo");
    }
}
