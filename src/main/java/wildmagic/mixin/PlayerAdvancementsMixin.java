package wildmagic.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wildmagic.server.WildMagicServerState;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Shadow
	public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

	@Inject(method = "award", at = @At("RETURN"))
	private void occkaWildMagic$awardClassExp(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() || !getOrStartProgress(advancement).isDone()) {
			return;
		}

		advancement.value().display().ifPresent(display -> {
			if (!display.shouldAnnounceChat()) return;
			int exp = display.getType() == net.minecraft.advancements.AdvancementType.CHALLENGE ? 4 : 1;
			WildMagicServerState.addAdvancementExp(player, exp);
		});
	}
}