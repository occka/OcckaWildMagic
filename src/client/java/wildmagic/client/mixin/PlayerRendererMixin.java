package wildmagic.client.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class PlayerRendererMixin {

@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z", at = @At("HEAD"), cancellable = true)
private void occkaWildMagic$hideInvisiblePlayer(
        AvatarRenderState renderState,
        CallbackInfoReturnable<Boolean> cir) {
    wildmagic.OcckaWildMagic.LOGGER.info("shouldRenderLayers called, isInvisible={}", renderState.isInvisible);
    if (renderState.isInvisible) {
        cir.setReturnValue(false);
    }
}
}