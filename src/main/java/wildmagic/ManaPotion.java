package wildmagic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import wildmagic.server.WildMagicServerState;

public class ManaPotion extends Item {
    private final int tier;

    public ManaPotion(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        int manaBonus = tier == 1 ? 80 : 120;
        long durationTicks = tier == 1 ? 20L * 60 * 5 : 20L * 60 * 10;

        WildMagicServerState.applyManaEffect(serverPlayer, manaBonus, durationTicks);
        player.getItemInHand(hand).shrink(1);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.GENERIC_DRINK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.0F);

        serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Максимальная мана +" + manaBonus + " на " + (durationTicks / 20 / 60) + " минут"));

        return InteractionResult.SUCCESS;
    }
}