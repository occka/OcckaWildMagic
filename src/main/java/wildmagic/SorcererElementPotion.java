package wildmagic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import wildmagic.classdata.SorcererElement;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.server.WildMagicServerState;

public class SorcererElementPotion extends Item {
    private final SorcererElement element;

    public SorcererElementPotion(SorcererElement element, Properties properties) {
        super(properties);
        this.element = element;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        PlayerClassData data = WildMagicServerState.get(serverPlayer);
        if (!data.hasClass() || data.selectedClass() != WildMagicClass.SORCERER) {
            serverPlayer.sendSystemMessage(Component.literal("Только чародей может использовать это зелье"));
            return InteractionResult.FAIL;
        }

        WildMagicServerState.setSorcererElement(serverPlayer, element);
        player.getItemInHand(hand).shrink(1);

        String elementName = switch (element) {
            case FIRE -> "Огня (Красный дракон)";
            case ICE -> "Льда (Белый дракон)";
            case LIGHTNING -> "Молнии (Синий дракон)";
            case POISON -> "Яда (Чёрный дракон)";
            case THUNDER -> "Грома (Древний дракон)";
        };
        serverPlayer.sendSystemMessage(Component.literal("Стихия изменена: " + elementName));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.2F);

        return InteractionResult.SUCCESS;
    }
}