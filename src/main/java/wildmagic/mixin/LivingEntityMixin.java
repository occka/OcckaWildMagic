package wildmagic.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wildmagic.server.WildMagicServerState;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void occkaWildMagic$checkCharm(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (!(self instanceof Player targetPlayer)) return;

        if (WildMagicServerState.isCharmed(attacker, targetPlayer)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
private void occkaWildMagic$checkHeroismPoison(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    LivingEntity self = (LivingEntity)(Object)this;
    if (!(self instanceof net.minecraft.server.level.ServerPlayer player)) return;
    if (!WildMagicServerState.isHeroismActive(player)) return;

    String damageKey = source.typeHolder().unwrapKey()
            .map(Object::toString)
            .orElse("");
    if (damageKey.contains("poison") || damageKey.contains("wither")) {
        cir.setReturnValue(false);
    }
}

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void occkaWildMagic$breakInvisibilityOnAttack(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker) {
            WildMagicServerState.breakInvisibility(attacker);
        }
    }
}