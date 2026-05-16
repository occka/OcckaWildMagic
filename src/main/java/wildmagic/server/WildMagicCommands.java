package wildmagic.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.WildMagicClass;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

public final class WildMagicCommands {
	private static final SuggestionProvider<CommandSourceStack> CLASS_SUGGESTIONS = (context, builder) -> {
		WildMagicClass.VALUES.forEach(clazz -> builder.suggest(clazz.id()));
		return builder.buildFuture();
	};

	private WildMagicCommands() {
	}

	public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("wildmagic")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("class")
                    .then(Commands.literal("set")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .then(Commands.argument("class", StringArgumentType.word())
                                            .suggests(CLASS_SUGGESTIONS)
                                            .executes(context -> setClass(context, ClassProgression.MIN_LEVEL))
                                            .then(Commands.argument("level", IntegerArgumentType.integer(ClassProgression.MIN_LEVEL, ClassProgression.MAX_LEVEL))
                                                    .executes(context -> setClass(context, IntegerArgumentType.getInteger(context, "level"))))
                                            .then(Commands.literal("max")
                                                    .executes(context -> setClass(context, ClassProgression.MAX_LEVEL))))))
                    .then(Commands.literal("clear")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(WildMagicCommands::clearClass))))
            .then(Commands.literal("mana")
                    .then(Commands.literal("fill")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(WildMagicCommands::fillMana))))));
}

	private static int fillMana(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
    for (ServerPlayer player : targets) {
        WildMagicServerState.fillMana(player);
    }
    context.getSource().sendSuccess(() -> Component.literal("Мана заполнена игрокам: " + targets.size()), true);
    return targets.size();
}

	private static int setClass(CommandContext<CommandSourceStack> context, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String classId = StringArgumentType.getString(context, "class").toLowerCase(Locale.ROOT);
		Optional<WildMagicClass> selectedClass = WildMagicClass.VALUES.stream()
				.filter(clazz -> clazz.id().equals(classId))
				.findFirst();
		if (selectedClass.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Неизвестный класс: " + classId));
			return 0;
		}

		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		for (ServerPlayer player : targets) {
			WildMagicServerState.setClass(player, selectedClass.get(), level);
		}

		context.getSource().sendSuccess(() -> Component.literal("Класс " + selectedClass.get().displayName() + " выдан игрокам: " + targets.size() + ", уровень: " + level), true);
		return targets.size();
	}

	private static int clearClass(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		for (ServerPlayer player : targets) {
			WildMagicServerState.clearClass(player);
		}

		context.getSource().sendSuccess(() -> Component.literal("Класс сброшен игрокам: " + targets.size()), true);
		return targets.size();
	}
}
