package com.miscelanea.petanca;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Objetos de la petanca. BOLA y BOLICHE: mantén clic derecho para coger fuerza y suelta para lanzarlos en parábola.
 * METRO: mide qué bolas están más cerca del boliche y cuántos puntos se lleva quien gana la mano.
 */
public class PetancaItem extends Item {
    public static final int BOLA = 0, BOLICHE = 1, METRO = 2;
    public final int tipo;

    public PetancaItem(Properties p, int tipo) {
        super(p);
        this.tipo = tipo;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return tipo == METRO ? 0 : 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return tipo == METRO ? UseAnim.NONE : UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (tipo == METRO) {
            if (!l.isClientSide) medir((ServerLevel) l, p);
            return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
        }
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void releaseUsing(ItemStack st, Level l, LivingEntity e, int quedan) {
        if (!(e instanceof Player p) || l.isClientSide || tipo == METRO) return;
        float fuerza = Math.min(1, (getUseDuration(st) - quedan) / 25F);
        if (fuerza < 0.1F) return;
        Vec3 mira = p.getLookAngle();
        BolaPetanca b = new BolaPetanca(ModEntities.BOLA_PETANCA.get(), l);
        b.preparar(p, tipo == BOLICHE);
        // lanzamiento de petanca: por debajo, en arco bajo, hacia donde miras (en horizontal)
        Vec3 hor = new Vec3(mira.x, 0, mira.z);
        hor = hor.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : hor.normalize();
        // con gravedad 0.04/tick: fuerza mínima cae a ~2 bloques, fuerza máxima a ~9-10 (más rodada corta).
        // mirar hacia arriba hace un "globo" (más alto y algo más corto), mirar al suelo un tiro "a rodar".
        double arriba = Mth.clamp(mira.y, -0.6, 0.8);
        double alcance = (0.14 + 0.38 * fuerza) * (1 - 0.35 * Math.max(0, arriba));
        double sube = 0.10 + 0.14 * fuerza + 0.22 * Math.max(0, arriba) + 0.08 * Math.min(0, arriba);
        b.setPos(p.getX() + hor.x * 0.5, p.getY() + 0.7, p.getZ() + hor.z * 0.5);
        b.setDeltaMovement(hor.x * alcance, sube, hor.z * alcance);
        l.addFreshEntity(b);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DA_LANZA.get(), SoundSource.PLAYERS, 0.5F, 0.7F);
        if (!p.getAbilities().instabuild) st.shrink(1);
    }

    private void medir(ServerLevel l, Player p) {
        List<BolaPetanca> todas = l.getEntitiesOfClass(BolaPetanca.class, new AABB(p.blockPosition()).inflate(20));
        BolaPetanca boliche = todas.stream().filter(BolaPetanca::boliche).min(Comparator.comparingDouble(b -> b.distanceToSqr(p))).orElse(null);
        if (boliche == null) {
            p.displayClientMessage(Component.translatable("miscelanea.petanca.sin_boliche"), true);
            return;
        }
        List<BolaPetanca> bolas = new ArrayList<>();
        for (BolaPetanca b : todas) if (!b.boliche() && b.distanceToSqr(boliche) < 15 * 15) bolas.add(b);
        if (bolas.isEmpty()) {
            p.displayClientMessage(Component.translatable("miscelanea.petanca.sin_bolas"), true);
            return;
        }
        bolas.sort(Comparator.comparingDouble(b -> b.distanceToSqr(boliche)));
        // se agrupa por UUID del jugador (el nombre solo se usa para mostrarlo)
        Object ganaId = bolas.get(0).clave();
        String gana = bolas.get(0).dueno();
        int puntos = 0;
        for (BolaPetanca b : bolas) {
            if (!java.util.Objects.equals(b.clave(), ganaId)) break;
            puntos++;
        }
        p.sendSystemMessage(Component.translatable("miscelanea.petanca.medida").withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < Math.min(6, bolas.size()); i++) {
            BolaPetanca b = bolas.get(i);
            int cm = (int) Math.round(Math.sqrt(b.distanceToSqr(boliche)) * 100);
            p.sendSystemMessage(Component.literal((i + 1) + ". " + b.dueno() + " — " + cm + " cm").withStyle(i < puntos ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        p.sendSystemMessage(Component.translatable("miscelanea.petanca.puntos", gana, puntos).withStyle(ChatFormatting.YELLOW));
        // línea de puntitos del boliche a la mejor bola
        Vec3 a = boliche.position().add(0, 0.1, 0), c = bolas.get(0).position().add(0, 0.1, 0);
        for (int k = 0; k <= 12; k++) {
            Vec3 q = a.add(c.subtract(a).scale(k / 12.0));
            l.sendParticles(new DustParticleOptions(new Vector3f(1, 0.9F, 0.2F), 0.8F), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FE_PREMIO.get(), SoundSource.PLAYERS, 0.5F, 1.4F);
    }
}
