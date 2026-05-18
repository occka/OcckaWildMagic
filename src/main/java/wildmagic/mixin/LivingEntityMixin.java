package wildmagic.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wildmagic.server.WildMagicServerState;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private static final ThreadLocal<Boolean> SWORD_PROCESSING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void occkaWildMagic$checkCharm(net.minecraft.server.level.ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(source.getEntity() instanceof LivingEntity attacker))
            return;
        if (!(self instanceof Player targetPlayer))
            return;

        if (WildMagicServerState.isCharmed(attacker, targetPlayer)
                || WildMagicServerState.isFriendlySummonedUndead(attacker, targetPlayer)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void occkaWildMagic$checkSummonedUndeadFriendlyFire(net.minecraft.server.level.ServerLevel level,
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(source.getEntity() instanceof LivingEntity attacker))
            return;

        if (WildMagicServerState.isFriendlySummonedUndead(attacker, self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void occkaWildMagic$checkDeadOneImmunities(net.minecraft.server.level.ServerLevel level,
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.server.level.ServerPlayer player))
            return;
        if (!WildMagicServerState.isDeadOne(player))
            return;

        String damageKey = source.typeHolder().unwrapKey()
                .map(Object::toString)
                .orElse("");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)
                || damageKey.contains("poison")
                || damageKey.contains("wither")
                || source.getEntity() instanceof net.minecraft.world.entity.Mob) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void occkaWildMagic$checkHeroismPoison(net.minecraft.server.level.ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.server.level.ServerPlayer player))
            return;
        if (!WildMagicServerState.isHeroismActive(player))
            return;

        String damageKey = source.typeHolder().unwrapKey()
                .map(Object::toString)
                .orElse("");
        if (damageKey.contains("poison") || damageKey.contains("wither")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void occkaWildMagic$warlockHealingHurts(float amount, CallbackInfo ci) {
        if (amount <= 0.0F)
            return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.server.level.ServerPlayer player))
            return;
        if (!WildMagicServerState.isWarlock(player))
            return;

        // Блокируем только лечение от эффектов (Regeneration, Absorption и т.п.)
        // Еда вызывает heal() без активного эффекта регенерации в этот момент
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION))
            return;

        player.hurtServer(player.level(), player.damageSources().magic(), amount);
        ci.cancel();
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void occkaWildMagic$breakInvisibilityOnAttack(net.minecraft.server.level.ServerLevel level,
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker) {
            WildMagicServerState.breakInvisibility(attacker);
        }
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void occkaWildMagic$armorOfAgathys(net.minecraft.server.level.ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || amount <= 0.0F)
            return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.server.level.ServerPlayer player))
            return;
        if (!(source.getEntity() instanceof LivingEntity attacker))
            return;
        if (attacker == self)
            return;

        WildMagicServerState.onPlayerDamaged(player, attacker, level);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void occkaWildMagic$undeadTouch(net.minecraft.server.level.ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue())
            return;
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker))
            return;

        LivingEntity self = (LivingEntity) (Object) this;
        WildMagicServerState.applyWarlockUndeadTouch(attacker, self);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void occkaWildMagic$mordenkainenSwordBonus(net.minecraft.server.level.ServerLevel level,
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue())
            return;
        if (SWORD_PROCESSING.get())
            return;
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker))
            return;
        if (!source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK))
            return;
        if (!WildMagicServerState.isMordenkainenActive(attacker))
            return;

        LivingEntity self = (LivingEntity) (Object) this;
        SWORD_PROCESSING.set(true);
        try {
            float bonus = 5.0F + attacker.getRandom().nextFloat() * 10.0F;
            self.hurtServer(level, attacker.damageSources().playerAttack(attacker), bonus);
        } finally {
            SWORD_PROCESSING.set(false);
        }
    }
}
