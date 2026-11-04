package com.miscelanea.client.dream;

import com.miscelanea.dream.AnilloMedianocheItem;
import com.miscelanea.dream.DreamRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente del anillo de medianoche: la pose (brazo en alto con el anillo), las chispas de grieta que giran alrededor,
 * el zumbido que sube mientras se carga (lo oyen todos los que están cerca) y el fundido a negro de la pantalla.
 */
public final class AnilloCliente {
    private AnilloCliente() {}

    private static final DustColorTransitionOptions GRIETA = new DustColorTransitionOptions(
            new Vector3f(1.0F, 0.12F, 0.18F), new Vector3f(0.45F, 0.05F, 0.75F), 0.9F);
    private static final DustColorTransitionOptions BRASA = new DustColorTransitionOptions(
            new Vector3f(1.0F, 0.55F, 0.6F), new Vector3f(0.8F, 0.05F, 0.12F), 0.6F);

    public static HumanoidModel.ArmPose POSE;
    /** Entidades que ya tienen su zumbido sonando (y el último tick en que sonó). */
    private static final Map<Integer, Integer> SONANDO = new HashMap<>();

    /** Fundido: cuándo se terminó de cargar (esperando el viaje) y cuándo se llegó. */
    private static long esperando = -1, llegada = -1;

    public static void init() {
        POSE = HumanoidModel.ArmPose.create("MISCELANEA_ANILLO", false, (model, e, brazo) -> {
            float k = progreso(e, Minecraft.getInstance().getFrameTime());
            var parte = brazo == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            float lado = brazo == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            float t = e.tickCount + Minecraft.getInstance().getFrameTime();
            // el brazo sube hacia delante y arriba, con el puño (y el anillo) hacia el cielo; tiembla al final
            parte.xRot = -1.45F - 0.95F * k + model.head.xRot * 0.3F + Mth.sin(t * 2.7F) * 0.04F * k * k;
            parte.yRot = -0.22F * lado + model.head.yRot * 0.4F;
            parte.zRot = 0.08F * lado + Mth.sin(t * 3.1F) * 0.03F * k * k;
        });
    }

    private static float progreso(LivingEntity e, float pt) {
        if (!e.isUsingItem() || !(e.getUseItem().getItem() instanceof AnilloMedianocheItem)) return 0.0F;
        float usado = AnilloMedianocheItem.CARGA - e.getUseItemRemainingTicks() + pt;
        return Mth.clamp(usado / AnilloMedianocheItem.CARGA, 0.0F, 1.0F);
    }

    public static HumanoidModel.ArmPose pose(LivingEntity e, InteractionHand h, ItemStack st) {
        if (POSE != null && e.isUsingItem() && e.getUsedItemHand() == h && e.getUseItem().getItem() == st.getItem()) return POSE;
        return null;
    }

    /** Primera persona: la mano sube y se adelanta con el anillo, temblando cada vez más. */
    public static boolean manoPrimeraPersona(PoseStack ps, LocalPlayer pl, HumanoidArm brazo, ItemStack st, float pt, float equip) {
        if (!pl.isUsingItem() || pl.getUseItem().getItem() != st.getItem()) return false;
        float k = progreso(pl, pt);
        float s = k * k * (3.0F - 2.0F * k);
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        float t = pl.tickCount + pt;
        ps.translate(i * (0.56F - 0.2F * s), -0.52F + 0.32F * s + Mth.sin(t * 2.9F) * 0.008F * k, -0.72F - 0.12F * s);
        ps.mulPose(Axis.XP.rotationDegrees(25.0F * s));
        ps.mulPose(Axis.ZP.rotationDegrees(i * (8.0F * s + Mth.sin(t * 3.3F) * 1.5F * k)));
        return true;
    }

    /** Cada tick de carga (en todos los clientes que ven al jugador): partículas y el zumbido. */
    public static void cargando(LivingEntity e, float k) {
        Level l = e.level();
        RandomSource r = l.random;
        float t = e.tickCount;
        // chispas de grieta girando alrededor, cada vez más cerca y más deprisa
        int n = 1 + (int) (k * 4);
        double radio = 1.1 - 0.65 * k;
        for (int j = 0; j < n; j++) {
            double a = t * (0.25 + 0.35 * k) + j * (Math.PI * 2 / n) + r.nextDouble() * 0.3;
            double y = e.getY() + 0.1 + ((t * 0.06 + j * 0.37) % 1.0) * 2.0;
            l.addParticle(GRIETA, e.getX() + Math.cos(a) * radio, y, e.getZ() + Math.sin(a) * radio,
                    -Math.sin(a) * 0.05, 0.02, Math.cos(a) * 0.05);
        }
        // brasas en la mano del anillo
        Vec3 mano = e.getEyePosition().add(e.getLookAngle().scale(0.55)).add(0, 0.35 * k - 0.2, 0);
        if (r.nextFloat() < 0.4F + k) l.addParticle(BRASA, mano.x + r.nextGaussian() * 0.06, mano.y + r.nextGaussian() * 0.06,
                mano.z + r.nextGaussian() * 0.06, 0, 0.01, 0);
        if (k > 0.5F && r.nextFloat() < k * 0.5F)
            l.addParticle(ParticleTypes.REVERSE_PORTAL, e.getX() + r.nextGaussian() * 0.4, e.getY() + r.nextDouble() * 1.8,
                    e.getZ() + r.nextGaussian() * 0.4, 0, 0.05, 0);
        if (k > 0.85F && r.nextInt(3) == 0)
            l.addParticle(ParticleTypes.ELECTRIC_SPARK, mano.x, mano.y, mano.z, r.nextGaussian() * 0.1, r.nextGaussian() * 0.1, r.nextGaussian() * 0.1);
        Integer ultimo = SONANDO.get(e.getId());
        if (ultimo == null || e.tickCount - ultimo > 4 || e.tickCount < ultimo) {
            SONANDO.put(e.getId(), e.tickCount);
            Minecraft.getInstance().getSoundManager().play(new Zumbido(e));
        }
        if (e == Minecraft.getInstance().player && k >= 0.97F) esperando = Util.getMillis();
    }

    /** El servidor avisa de que hemos llegado: la pantalla vuelve desde negro. */
    public static void llegada(boolean ida) {
        llegada = Util.getMillis();
        esperando = -1;
    }

    /** Fundido a negro (con un tinte púrpura) mientras se carga, y vuelta desde negro al llegar. */
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long ahora = Util.getMillis();
        float a = 0.0F;
        float k = progreso(mc.player, pt);
        if (k > 0.3F) {
            float x = (k - 0.3F) / 0.7F;
            a = x * x * 0.95F;
        }
        if (esperando >= 0) {
            // ya cargado, esperando al viaje: negro (si no llega en 1,5 s es que ha fallado y se aclara)
            long d = ahora - esperando;
            a = Math.max(a, d < 1500 ? 0.95F : Math.max(0.0F, 0.95F - (d - 1500) / 600.0F));
            if (d > 2200) esperando = -1;
        }
        if (llegada >= 0) {
            long d = ahora - llegada;
            if (d > 1600) llegada = -1;
            else a = Math.max(a, 0.95F * (1.0F - Mth.clamp((d - 250) / 1350.0F, 0.0F, 1.0F)));
        }
        if (a <= 0.005F) return;
        int al = Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255);
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, (al << 24) | 0x07000C);
        // borde rojo de grieta, más fuerte al final
        int borde = Math.round(Mth.clamp(a * 0.8F, 0.0F, 1.0F) * 255);
        g.fillGradient(0, 0, w, h / 4, (borde << 24) | 0x5A0414, 0x005A0414);
        g.fillGradient(0, h - h / 4, w, h, 0x005A0414, (borde << 24) | 0x5A0414);
        RenderSystem.disableBlend();
    }

    /** Zumbido de carga que sigue al jugador, sube de tono y se corta en cuanto suelta el clic. */
    private static final class Zumbido extends AbstractTickableSoundInstance {
        private final LivingEntity e;

        Zumbido(LivingEntity e) {
            super(DreamRegistro.ANILLO_CARGA.get(), SoundSource.PLAYERS, azar());
            this.e = e;
            this.x = e.getX();
            this.y = e.getY() + 1;
            this.z = e.getZ();
            this.volume = 0.2F;
            this.pitch = 0.8F;
        }

        private static RandomSource azar() {
            return RandomSource.create();
        }

        @Override
        public void tick() {
            float k = progreso(e, 0.0F);
            if (e.isRemoved() || k <= 0.0F) {
                SONANDO.remove(e.getId());
                stop();
                return;
            }
            SONANDO.put(e.getId(), e.tickCount);
            x = e.getX();
            y = e.getY() + 1;
            z = e.getZ();
            volume = 0.25F + 0.75F * k;
            pitch = 0.8F + 0.5F * k;
        }
    }
}
