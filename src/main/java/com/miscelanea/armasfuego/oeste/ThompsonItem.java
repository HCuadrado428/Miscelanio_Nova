package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Thompson «Tommy gun» de los años 20 con cargador de tambor. Manteniendo el clic derecho dispara en ráfaga: cuanto
 * más larga, más sube y más dispersa (el retroceso se nota en la vista). El tambor se cambia entero (agachado +
 * mantener clic derecho con un cargador de tambor encima). Se guarda en un estuche de violín, claro.
 */
public class ThompsonItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("thompson_disparo");
    /** Balas seguidas de la ráfaga actual (sube la dispersión). */
    public static final String RAFAGA = "Rafaga";

    public ThompsonItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad() {
        return EquilibrioArmas.thompsonCapacidad();
    }

    @Override
    public int ticksRecarga() {
        return EquilibrioArmas.thompsonRecarga();
    }

    @Override
    public boolean tieneMunicion(Player p) {
        return !buscar(p, x -> x.is(ModItems.CARGADOR_THOMPSON.get())).isEmpty();
    }

    @Override
    protected int gastarMunicion(ServerPlayer p, ItemStack s) {
        ItemStack m = buscar(p, x -> x.is(ModItems.CARGADOR_THOMPSON.get()));
        if (m.isEmpty()) return 0;
        m.shrink(1);
        // las balas que quedaban en el tambor viejo se pierden (realismo medio: no se van sacando una a una)
        ponerBalas(s, 0);
        return capacidad();
    }

    @Override
    protected void empezarRecarga(ServerPlayer p, ItemStack s) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.THOMPSON_TAMBOR.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    @Override
    public ItemStack iconoMunicion() {
        return new ItemStack(ModItems.CARGADOR_THOMPSON.get());
    }

    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.THOMPSON_TAMBOR.get();
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return EquilibrioArmas.thompsonCadencia();
    }

    @Override
    protected float probAtasco(Player p, ItemStack s) {
        return EquilibrioArmas.encasquilladoBase() * 0.4F;   // por bala (tira muchas)
    }

    @Override
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        t.putInt(MODO, MODO_RAFAGA);
        t.putInt(RAFAGA, 0);
        p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        return InteractionResultHolder.consume(s);
    }

    @Override
    protected void rafaga(Level level, Player p, ItemStack s, int t) {
        int cad = EquilibrioArmas.thompsonCadencia();
        if (t % cad != 0) return;
        if (balas(s) <= 0 || atascada(s)) {
            intentar(level, p, s);   // clic
            p.stopUsingItem();
            return;
        }
        intentar(level, p, s);
    }

    public static int rafagaN(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(RAFAGA) : 0;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        int n = rafagaN(s) + 1;
        s.getOrCreateTag().putInt(RAFAGA, n);
        double disp = Math.min(0.09, 0.02 + 0.004 * n);
        double alcance = EquilibrioArmas.thompsonAlcance();
        tiro(p, EquilibrioArmas.thompsonDano(), alcance, 15, 0.6F, disp, 0.15F, DANO, true,
                n % 3 == 0 ? net.minecraft.core.particles.ParticleTypes.CRIT : null);
        efectos(l, p, boca(p, 1.0, 0.25, 0.2), ModSounds.THOMPSON_DISPARO.get(), 3.0F, 0.95F, n % 2, true);
        LogrosMiscelanea.dar(p, "thompson_disparo");
        if (n >= capacidad()) LogrosMiscelanea.dar(p, "thompson_tambor");
    }
}
