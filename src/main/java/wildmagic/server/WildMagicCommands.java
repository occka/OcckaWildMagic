package wildmagic.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.WildMagicClass;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WildMagicCommands {
	private static final SuggestionProvider<CommandSourceStack> CLASS_SUGGESTIONS = (context, builder) -> {
		WildMagicClass.VALUES.forEach(clazz -> builder.suggest(clazz.id()));
		return builder.buildFuture();
	};
	private static final String TEAM_PREFIX = "wm_team_";
	private static final Map<UUID, UUID> TEAM_INVITES = new HashMap<>();

	private WildMagicCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("wildmagic")
				.then(Commands.literal("team")
						.executes(WildMagicCommands::teamHelp)
						.then(Commands.literal("invite")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(WildMagicCommands::inviteToTeam)))
						.then(Commands.literal("accept").executes(WildMagicCommands::acceptInvite))
						.then(Commands.literal("leave").executes(WildMagicCommands::leaveTeam)))
				.then(Commands.literal("leave").executes(WildMagicCommands::leaveTeam))
				.then(Commands.literal("class")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
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
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.then(Commands.literal("fill")
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(WildMagicCommands::fillMana))))));
	}

	private static int teamHelp(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("Команда: /wildmagic team invite <игрок>, /wildmagic team accept, /wildmagic team leave"), false);
		return 1;
	}

	private static int inviteToTeam(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer inviter = context.getSource().getPlayerOrException();
		ServerPlayer invited = EntityArgument.getPlayer(context, "player");
		if (inviter.getUUID().equals(invited.getUUID())) {
			context.getSource().sendFailure(Component.literal("Нельзя пригласить самого себя"));
			return 0;
		}
		TEAM_INVITES.put(invited.getUUID(), inviter.getUUID());
		invited.sendSystemMessage(Component.literal(inviter.getName().getString() + " приглашает вас в команду. Напишите /wildmagic team accept"));
		context.getSource().sendSuccess(() -> Component.literal("Приглашение отправлено игроку " + invited.getName().getString()), false);
		return 1;
	}

	private static int acceptInvite(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer invited = context.getSource().getPlayerOrException();
		UUID inviterId = TEAM_INVITES.remove(invited.getUUID());
		if (inviterId == null) {
			context.getSource().sendFailure(Component.literal("Нет активных приглашений в команду"));
			return 0;
		}
		ServerPlayer inviter = invited.level().getServer().getPlayerList().getPlayer(inviterId);
		if (inviter == null) {
			context.getSource().sendFailure(Component.literal("Пригласивший игрок не в сети"));
			return 0;
		}
		joinPlayersIntoTeam(inviter, invited);
		inviter.sendSystemMessage(Component.literal(invited.getName().getString() + " принял приглашение в команду"));
		invited.sendSystemMessage(Component.literal("Вы вступили в команду с " + inviter.getName().getString()));
		return 1;
	}

	private static int leaveTeam(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Scoreboard scoreboard = player.level().getServer().getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
		if (team == null || !team.getName().startsWith(TEAM_PREFIX)) {
			context.getSource().sendFailure(Component.literal("Вы не состоите в команде WildMagic"));
			return 0;
		}
		scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
		if (team.getPlayers().isEmpty()) {
			scoreboard.removePlayerTeam(team);
		}
		context.getSource().sendSuccess(() -> Component.literal("Вы вышли из команды"), false);
		return 1;
	}

	private static void joinPlayersIntoTeam(ServerPlayer inviter, ServerPlayer invited) {
		Scoreboard scoreboard = inviter.level().getServer().getScoreboard();
		PlayerTeam inviterTeam = scoreboard.getPlayersTeam(inviter.getScoreboardName());
		PlayerTeam invitedTeam = scoreboard.getPlayersTeam(invited.getScoreboardName());
		PlayerTeam targetTeam = inviterTeam;
		if (targetTeam == null || !targetTeam.getName().startsWith(TEAM_PREFIX)) {
			targetTeam = createWildMagicTeam(scoreboard, inviter.getUUID());
			scoreboard.addPlayerToTeam(inviter.getScoreboardName(), targetTeam);
		}
		if (invitedTeam != null && invitedTeam != targetTeam) {
			scoreboard.removePlayerFromTeam(invited.getScoreboardName(), invitedTeam);
			if (invitedTeam.getPlayers().isEmpty() && invitedTeam.getName().startsWith(TEAM_PREFIX)) {
				scoreboard.removePlayerTeam(invitedTeam);
			}
		}
		scoreboard.addPlayerToTeam(invited.getScoreboardName(), targetTeam);
	}

	private static PlayerTeam createWildMagicTeam(Scoreboard scoreboard, UUID ownerId) {
		String teamName = TEAM_PREFIX + ownerId.toString().substring(0, 8);
		PlayerTeam existing = scoreboard.getPlayerTeam(teamName);
		if (existing != null) {
			return existing;
		}
		PlayerTeam team = scoreboard.addPlayerTeam(teamName);
		team.setDisplayName(Component.literal("Отряд " + ownerId.toString().substring(0, 4)));
		team.setAllowFriendlyFire(true);
		team.setSeeFriendlyInvisibles(true);
		team.setColor(randomTeamColor(ownerId));
		return team;
	}

	private static ChatFormatting randomTeamColor(UUID seedId) {
		ChatFormatting[] colors = EnumSet.of(
				ChatFormatting.AQUA,
				ChatFormatting.BLUE,
				ChatFormatting.DARK_AQUA,
				ChatFormatting.DARK_GREEN,
				ChatFormatting.GOLD,
				ChatFormatting.GREEN,
				ChatFormatting.LIGHT_PURPLE,
				ChatFormatting.RED,
				ChatFormatting.YELLOW
		).toArray(ChatFormatting[]::new);
		int index = Math.floorMod(seedId.hashCode(), colors.length);
		return colors[index];
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
