package wildmagic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.server.WildMagicServerState;

public class MagicStaffItem extends Item {
    public MagicStaffItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        PlayerClassData data = WildMagicServerState.get(sp);
        if (!data.hasClass() || (data.selectedClass() != WildMagicClass.WIZARD && data.selectedClass() != WildMagicClass.DRUID)) {
            sp.sendSystemMessage(Component.literal("Только волшебник или друид может использовать посох"));
            return InteractionResult.FAIL;
        }
        if (data.mana() < 10) {
            sp.sendSystemMessage(Component.literal("Недостаточно маны (нужно 10)"));
            return InteractionResult.FAIL;
        }
        WildMagicServerState.consumeManaDirect(sp, 10);
        ((net.minecraft.server.level.ServerLevel) level).sendParticles(ParticleTypes.WITCH, sp.getX(), sp.getY() + 1.0D, sp.getZ(), 24, 0.45D, 0.65D, 0.45D, 0.02D);
        return InteractionResult.SUCCESS;
    }
}
