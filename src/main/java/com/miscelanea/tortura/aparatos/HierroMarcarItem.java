package com.miscelanea.tortura.aparatos;

import com.miscelanea.tortura.ObjetoTortura;
import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.Heridas;
import com.miscelanea.tortura.heridas.HeridasDatos;
import com.miscelanea.tortura.heridas.Marca;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Hierro de marcar.
 * <ol>
 *   <li>DIBUJAR: Shift + clic derecho abre una cuadrícula de 8x8 para dibujar el símbolo (se guarda en el objeto y se ve
 *       en el tooltip).</li>
 *   <li>CALENTAR: mantén clic derecho mirando a un fuego, una fogata encendida, lava o un bloque de magma. La punta pasa
 *       de gris a ROJO, NARANJA y BLANCO ({@link #calor}: 0..1, se enfría sola). Solo marca en NARANJA: en frío o al rojo
 *       no marca y al blanco te quemas tú la mano.</li>
 *   <li>MARCAR: clic derecho a un cautivo sujeto (grilletes, lazo, trampa o un aparato): el símbolo queda quemado en su
 *       piel (pecho/espalda o el brazo, según donde apuntes) hasta que muera. Chisporroteo, humo y una quemadura.</li>
 * </ol>
 * NBT: Simbolo (long, bit y*8+x), Calor (float) y CalorT (tick del mundo en que se midió).
 */
public class HierroMarcarItem extends ObjetoTortura {
    public static final float ROJO = 0.35F, NARANJA = 0.6F, BLANCO = 0.85F;
    /** Lo que se enfría por tick, y lo que se calienta por tick sobre el fuego / la lava. */
    public static final float ENFRIA = 0.004F, CALIENTA_FUEGO = 0.009F, CALIENTA_LAVA = 0.016F;

    public HierroMarcarItem(Properties p) {
        super(p, 3);
    }

    // ------------------------------------------------------------------ datos
    public static long simbolo(ItemStack st) {
        CompoundTag t = st.getTag();
        return t == null ? 0L : t.getLong("Simbolo");
    }

    public static void ponerSimbolo(ItemStack st, long bits) {
        st.getOrCreateTag().putLong("Simbolo", bits);
    }

    /** Temperatura de la punta ahora (0 frío .. 1 al blanco). */
    public static float calor(ItemStack st, long ahora) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains("Calor")) return 0F;
        long dt = Math.max(0L, ahora - t.getLong("CalorT"));
        return Math.max(0F, t.getFloat("Calor") - dt * ENFRIA);
    }

    static void ponerCalor(ItemStack st, float c, long ahora) {
        CompoundTag t = st.getOrCreateTag();
        if (c <= 0F) {
            t.remove("Calor");
            t.remove("CalorT");
            return;
        }
        t.putFloat("Calor", Math.min(1F, c));
        t.putLong("CalorT", ahora);
    }

    /** 0 frío, 1 rojo, 2 naranja, 3 blanco. */
    public static int fase(float c) {
        return c >= BLANCO ? 3 : c >= NARANJA ? 2 : c >= ROJO ? 1 : 0;
    }

    // ------------------------------------------------------------------ fuego
    private static float fuegoDe(BlockState s) {
        if (s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.LAVA_CAULDRON)) return CALIENTA_LAVA;
        if (s.is(BlockTags.FIRE) || (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT))) return CALIENTA_FUEGO;
        if (s.is(Blocks.MAGMA_BLOCK)) return CALIENTA_FUEGO * 0.6F;
        return 0F;
    }

    /** Lo que calienta lo que mira el jugador (0 = nada). */
    static float fuegoMirado(Level l, Player p) {
        BlockHitResult hr = getPlayerPOVHitResult(l, p, ClipContext.Fluid.ANY);
        if (hr.getType() != HitResult.Type.BLOCK) return 0F;
        BlockPos b = hr.getBlockPos();
        float f = fuegoDe(l.getBlockState(b));
        f = Math.max(f, fuegoDe(l.getBlockState(b.relative(hr.getDirection()))));
        f = Math.max(f, fuegoDe(l.getBlockState(b.above())));
        return f;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (p.isShiftKeyDown()) {
            if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.aparatos.PantallaMarca.abrir(mano));
            return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
        }
        if (fuegoMirado(l, p) > 0F) {
            p.startUsingItem(mano);
            return InteractionResultHolder.consume(st);
        }
        if (!l.isClientSide)
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.al_fuego").withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.pass(st);
    }

    @Override
    public void onUseTick(Level l, LivingEntity e, ItemStack st, int quedan) {
        if (!(e instanceof Player p)) return;
        float f = fuegoMirado(l, p);
        if (f <= 0F) {
            p.stopUsingItem();
            return;
        }
        long ahora = l.getGameTime();
        if (l.isClientSide) {
            // brasas y humo en la punta (donde mira, sobre el fuego)
            BlockHitResult hr = getPlayerPOVHitResult(l, p, ClipContext.Fluid.ANY);
            Vec3 h = hr.getLocation();
            if (p.getRandom().nextInt(3) == 0) l.addParticle(ParticleTypes.SMOKE, h.x, h.y + 0.15, h.z, 0, 0.03, 0);
            if (fase(calor(st, ahora)) >= 1 && p.getRandom().nextInt(4) == 0)
                l.addParticle(ParticleTypes.LAVA, h.x, h.y + 0.1, h.z, 0, 0, 0);
            return;
        }
        float antes = calor(st, ahora);
        float c = Math.min(1F, antes + f + ENFRIA);
        ponerCalor(st, c, ahora);
        if (ahora % 12 == 0)
            l.playSound(null, p.getX(), p.getY() + 1, p.getZ(), AparatosRegistro.HIERRO_BRASA.get(), SoundSource.PLAYERS, 0.5F, 0.8F + c * 0.5F);
        int fa = fase(antes), fn = fase(c);
        if (fn != fa) {
            ChatFormatting col = fn == 3 ? ChatFormatting.WHITE : fn == 2 ? ChatFormatting.GOLD : ChatFormatting.RED;
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.fase" + fn).withStyle(col, ChatFormatting.BOLD), true);
        }
    }

    @Override
    public void inventoryTick(ItemStack st, Level l, Entity e, int hueco, boolean seleccionado) {
        if (l.isClientSide || l.getGameTime() % 20 != 0 || !(e instanceof Player p)) return;
        CompoundTag t = st.getTag();
        if (t == null || !t.contains("Calor")) return;
        float c = calor(st, l.getGameTime());
        if (c <= 0F) {
            ponerCalor(st, 0F, l.getGameTime());
            return;
        }
        boolean enMano = seleccionado || p.getOffhandItem() == st;
        if (c >= BLANCO && enMano && !p.isUsingItem()) quemarse(p, st, 1.5F);
    }

    /** Al blanco: el mango quema. */
    private static void quemarse(Player p, ItemStack st, float dano) {
        p.hurt(p.damageSources().inFire(), dano);
        boolean derecha = (p.getMainHandItem() == st) == (p.getMainArm() == HumanoidArm.RIGHT);
        Aparatos.herida(p, derecha ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, Tipo.QUEMADURA, 1);
        p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.te_quemas").withStyle(ChatFormatting.RED), true);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack viejo, ItemStack nuevo, boolean cambioHueco) {
        return cambioHueco || viejo.getItem() != nuevo.getItem();
    }

    // ------------------------------------------------------------------ marcar
    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity t, InteractionHand mano) {
        if (t == p) return InteractionResult.PASS;
        if (!(p.level() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        // (la mano: interactLivingEntity puede recibir una copia; se trabaja sobre la que lleva)
        ItemStack hierro = p.getItemInHand(mano);
        if (!(hierro.getItem() instanceof HierroMarcarItem)) hierro = st;
        if (!Aparatos.restringido(t)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.sujeto").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        long bits = simbolo(hierro);
        if (bits == 0L) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.sin_dibujo").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        long ahora = sl.getGameTime();
        float c = calor(hierro, ahora);
        int fase = fase(c);
        if (fase < 2) {
            p.displayClientMessage(Component.translatable(fase == 0 ? "msg.miscelanea.hierro.frio" : "msg.miscelanea.hierro.poco").withStyle(ChatFormatting.GRAY), true);
            if (fase == 1) sl.playSound(null, t.getX(), t.getY() + 1, t.getZ(), AparatosRegistro.HIERRO_CHISPORROTEO.get(), SoundSource.PLAYERS, 0.3F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        if (fase == 3) {
            // al blanco: se escurre de la mano y no marca bien
            quemarse(p, hierro, 2.5F);
            ponerCalor(hierro, c - 0.2F, ahora);
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.blanco").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        Vec3 punto = punto(p, t);
        if (t instanceof ServerPlayer v) {
            Sitio s = sitio(p, v, punto);
            if (Heridas.activo(sl)) {
                HeridasDatos d = Heridas.leer(v);
                d.ponerMarca(new Marca(s.parte, s.cara, bits));
                Herida h = Heridas.nueva(v, s.parte, Tipo.QUEMADURA, 1, s.cara, 128, 90, false);
                Heridas.agregar(d, h);
                Heridas.actualizar(v, d);
            }
            v.displayClientMessage(Component.translatable("msg.miscelanea.hierro.marcado_tu").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.marcado", v.getDisplayName(),
                    Component.translatable(s.parte.clave())).withStyle(ChatFormatting.GOLD), true);
        } else {
            p.displayClientMessage(Component.translatable("msg.miscelanea.hierro.mob", t.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        }
        t.invulnerableTime = 0;
        t.hurt(t.damageSources().generic(), 2.5F);
        sl.playSound(null, punto.x, punto.y, punto.z, AparatosRegistro.HIERRO_CHISPORROTEO.get(), SoundSource.PLAYERS, 1.2F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, punto.x, punto.y, punto.z, 8, 0.08, 0.08, 0.08, 0.02);
        sl.sendParticles(ParticleTypes.SMOKE, punto.x, punto.y, punto.z, 14, 0.12, 0.12, 0.12, 0.03);
        // el hierro se enfría al marcar: hay que volver a calentarlo
        ponerCalor(hierro, Math.min(c, NARANJA) - 0.3F, ahora);
        p.swing(mano, true);
        return InteractionResult.SUCCESS;
    }

    private static Vec3 punto(Player p, LivingEntity v) {
        Vec3 ojo = p.getEyePosition(), fin = ojo.add(p.getViewVector(1F).scale(6));
        Optional<Vec3> hit = v.getBoundingBox().inflate(0.15).clip(ojo, fin);
        return hit.orElse(v.position().add(0, v.getBbHeight() * 0.62, 0));
    }

    record Sitio(Parte parte, int cara) {}

    /** Pecho/espalda o brazo según donde se apunte (en el potro, tumbado boca arriba: siempre el pecho). */
    static Sitio sitio(Player p, LivingEntity v, Vec3 punto) {
        if (Aparatos.de(v) instanceof PotroBlockEntity) return new Sitio(Parte.TORSO, Herida.FRENTE);
        double yaw = Math.toRadians(v.yBodyRot);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), derX = -Math.cos(yaw), derZ = -Math.sin(yaw);
        double medio = Math.max(0.1, v.getBbWidth() * 0.5);
        Vec3 rel = punto.subtract(v.position());
        double lx = Mth.clamp((rel.x * derX + rel.z * derZ) / medio, -1, 1);
        double h = rel.y / Math.max(0.1, v.getBbHeight());
        Vec3 desde = p.position().subtract(v.position());
        boolean delante = desde.x * fwdX + desde.z * fwdZ >= 0;
        if (h > 0.39 && h < 0.8 && Math.abs(lx) > 0.5) return new Sitio(lx > 0 ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, Herida.FRENTE);
        return new Sitio(Parte.TORSO, delante ? Herida.FRENTE : Herida.ESPALDA);
    }

    // ------------------------------------------------------------------ tooltip
    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        super.appendHoverText(s, level, l, f);
        if (level != null) {
            int fase = fase(calor(s, level.getGameTime()));
            ChatFormatting col = fase == 3 ? ChatFormatting.WHITE : fase == 2 ? ChatFormatting.GOLD : fase == 1 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY;
            l.add(1, Component.translatable("item.miscelanea.hierro_marcar.temp", Component.translatable("msg.miscelanea.hierro.estado" + fase).withStyle(col))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (simbolo(s) == 0L) l.add(1, Component.translatable("item.miscelanea.hierro_marcar.sin_dibujo").withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack s) {
        long b = simbolo(s);
        return b == 0L ? Optional.empty() : Optional.of(new MarcaTooltip(b));
    }

    /** El dibujo para el tooltip (lo pinta el cliente: MarcaTooltipCliente). */
    public record MarcaTooltip(long bits) implements TooltipComponent {}
}
