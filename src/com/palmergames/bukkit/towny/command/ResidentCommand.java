package com.palmergames.bukkit.towny.command;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyFormatter;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.SpawnType;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.Translation;
import com.palmergames.bukkit.towny.permissions.PermissionNodes;
import com.palmergames.bukkit.towny.permissions.TownyPermissionSource;
import com.palmergames.bukkit.towny.tasks.CooldownTimerTask;
import com.palmergames.bukkit.towny.tasks.CooldownTimerTask.CooldownType;
import com.palmergames.bukkit.towny.utils.NameUtil;
import com.palmergames.bukkit.towny.utils.SpawnUtil;
import com.palmergames.bukkit.util.BukkitTools;
import com.palmergames.bukkit.util.ChatTools;
import com.palmergames.bukkit.util.Colors;
import com.palmergames.util.StringMgmt;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Send a list of all towny resident help commands to player Command: /resident
 */

public class ResidentCommand extends BaseCommand implements CommandExecutor {

	private static Towny plugin;
	private static final List<String> residentTabCompletes = Arrays.asList(
		"friend",
		"list",
		"jail",
		"spawn",
		"toggle",
		"set",
		"tax"
	);
	
	private static final List<String> residentFriendTabCompletes = Arrays.asList(
		"add",
		"remove",
		"clear",
		"list"
	);

	private static final List<String> residentToggleTabCompletes = Arrays.asList(
		"pvp",
		"fire",
		"mobs",
		"explosion",
		"plotborder",
		"constantplotborder",
		"ignoreplots",
		"townclaim",
		"map",
		"spy",
		"reset",
		"clear"
	);
	
	private static final List<String> residentModeTabCompletes = Arrays.asList(
		"map",
		"townclaim",
		"townunclaim",
		"tc",
		"nc",
		"plotborder",
		"constantplotborder",
		"ignoreplots",
		"reset",
		"clear"
	);
	
	private static final List<String> residentConsoleTabCompletes = Arrays.asList(
		"?",
		"help",
		"list"
	);
	
	private static final List<String> residentSetTabCompletes = Arrays.asList(
		"perm",
		"mode"
	);
	
	private static final List<String> residentToggleChoices = Arrays.asList(
		"pvp",
		"fire",
		"mobs",
		"explosion"
	);
	
	private static final List<String> residentToggleModes = new ArrayList<>(residentToggleTabCompletes).stream()
		.filter(str -> !residentToggleChoices.contains(str))
		.collect(Collectors.toList());

	private static final List<String> residentToggleModesUnionToggles = Stream.concat(
		new ArrayList<>(residentToggleModes).stream(),
		BaseCommand.setOnOffCompletes.stream()
	).collect(Collectors.toList());

	public ResidentCommand(Towny instance) {

		plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		
		if (sender instanceof Player) {
			if (plugin.isError()) {
				TownyMessaging.sendMessage(sender, Colors.Rose + "[Towny Error] Locked in Safe mode!");
				return false;
			}
			Player player = (Player) sender;
			if (args == null) {
				HelpMenu.RESIDENT_HELP.send(player);
				parseResidentCommand(player, args);
			} else {
				parseResidentCommand(player, args);
			}

		} else
			try {
				parseResidentCommandForConsole(sender, args);
			} catch (TownyException e) {
				TownyMessaging.sendErrorMsg(sender, e.getMessage());
			}

		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

		if (sender instanceof Player) {
			switch (args[0].toLowerCase()) {
				case "toggle":
					if (args.length == 2) {
						return NameUtil.filterByStart(TownyCommandAddonAPI.getTabCompletes(CommandType.RESIDENT_TOGGLE, residentToggleTabCompletes), args[1]);
					} else if (args.length == 3 && residentToggleChoices.contains(args[1].toLowerCase())) {
						return NameUtil.filterByStart(BaseCommand.setOnOffCompletes, args[2]);
					} else if (args.length >= 3) {
						String prevArg = args[args.length - 2].toLowerCase();
						if (residentToggleModes.contains(prevArg)) {
							return NameUtil.filterByStart(residentToggleModesUnionToggles, args[args.length - 1]);
						} else if (BaseCommand.setOnOffCompletes.contains(prevArg)) {
							return NameUtil.filterByStart(residentToggleModes, args[args.length - 1]);
						}
					}
					break;
				case "set":
					if (args.length == 2)
						return NameUtil.filterByStart(TownyCommandAddonAPI.getTabCompletes(CommandType.RESIDENT_SET, residentSetTabCompletes), args[1]);
					if (args.length > 2) {
						if (TownyCommandAddonAPI.hasCommand(CommandType.RESIDENT_SET, args[1]))
							return NameUtil.filterByStart(TownyCommandAddonAPI.getAddonCommand(CommandType.RESIDENT_SET, args[1]).getTabCompletion(args.length-1), args[args.length-1]);

						switch (args[1].toLowerCase()) {
							case "mode":
								return NameUtil.filterByStart(residentModeTabCompletes, args[args.length - 1]);
							case "perm":
								return permTabComplete(StringMgmt.remArgs(args, 2));
						}
					}
					break;
				case "friend":
					switch (args.length) {
						case 2:
							return NameUtil.filterByStart(residentFriendTabCompletes, args[1]);
						case 3:
							if (args[1].equalsIgnoreCase("remove")) {
								Resident res = TownyUniverse.getInstance().getResident(((Player) sender).getUniqueId());
								if (res != null)
									return NameUtil.filterByStart(NameUtil.getNames(res.getFriends()), args[2]);
							} else {
								return getTownyStartingWith(args[2], "r");
							}
					}
					break;
				default:
					if (args.length == 1)
						return filterByStartOrGetTownyStartingWith(TownyCommandAddonAPI.getTabCompletes(CommandType.RESIDENT, residentTabCompletes), args[0], "r");
					else if (args.length > 1 && TownyCommandAddonAPI.hasCommand(CommandType.RESIDENT, args[0]))
						return NameUtil.filterByStart(TownyCommandAddonAPI.getAddonCommand(CommandType.RESIDENT, args[0]).getTabCompletion(args.length), args[args.length-1]);
			}
		} else if (args.length == 1){
				return filterByStartOrGetTownyStartingWith(residentConsoleTabCompletes, args[0], "r");
		}

		return Collections.emptyList();
	}

	@SuppressWarnings("static-access")
	private void parseResidentCommandForConsole(final CommandSender sender, String[] split) throws TownyException {

		if (split.length == 0 || split[0].equalsIgnoreCase("?") || split[0].equalsIgnoreCase("help")) {
			HelpMenu.RESIDENT_HELP_CONSOLE.send(sender);
		} else if (split[0].equalsIgnoreCase("list")) {
			listResidents(sender);

		} else {
			final Optional<Resident> resOpt = Optional.ofNullable(TownyUniverse.getInstance().getResident(split[0]));
			if (resOpt.isPresent()) {
				Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
					Player player = null;
					TownyMessaging.sendMessage(sender, TownyFormatter.getStatus(resOpt.get(), player));
				});
			}
			else {
				throw new TownyException(Translation.of("msg_err_not_registered_1", split[0]));
			}
		}
		
	}

	public void parseResidentCommand(final Player player, String[] split) {
		TownyPermissionSource permSource = TownyUniverse.getInstance().getPermissionSource();

		try {

			if (split.length == 0) {
				Resident res = getResidentOrThrow(player.getUniqueId());

				TownyMessaging.sendMessage(player, TownyFormatter.getStatus(res, player));
			} else if (split[0].equalsIgnoreCase("?") || split[0].equalsIgnoreCase("help")) {
				
				HelpMenu.RESIDENT_HELP.send(player);

			} else if (split[0].equalsIgnoreCase("list")) {

				if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_LIST.getNode()))
					throw new TownyException(Translation.of("msg_err_command_disable"));

				listResidents(player);

			} else if (split[0].equalsIgnoreCase("tax")) {

				if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_TAX.getNode()))
					throw new TownyException(Translation.of("msg_err_command_disable"));

				if (!TownyEconomyHandler.isActive())
					throw new TownyException(Translation.of("msg_err_no_economy"));
				
				Resident res = getResidentOrThrow(player.getUniqueId());
				
				TownyMessaging.sendMessage(player, TownyFormatter.getTaxStatus(res));

			} else if (split[0].equalsIgnoreCase("jail")) {

				if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_JAIL.getNode()))
					throw new TownyException(Translation.of("msg_err_command_disable"));

				if (!TownySettings.isAllowingBail()) {
					TownyMessaging.sendErrorMsg(player, Colors.Red + Translation.of("msg_err_bail_not_enabled"));
					return;
				}
				
				if (split.length == 1 ) {
					TownyMessaging.sendMessage(player, ChatTools.formatTitle("/resident jail"));
					TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident", "jail paybail", ""));
					TownyMessaging.sendMessage(player, Colors.LightBlue + Translation.of("msg_resident_bail_amount") + Colors.Green + TownyEconomyHandler.getFormattedBalance(TownySettings.getBailAmount()));
					TownyMessaging.sendMessage(player, Colors.LightBlue + Translation.of("msg_mayor_bail_amount") + Colors.Green + TownyEconomyHandler.getFormattedBalance(TownySettings.getBailAmountMayor()));
					TownyMessaging.sendMessage(player, Colors.LightBlue + Translation.of("msg_king_bail_amount") + Colors.Green + TownyEconomyHandler.getFormattedBalance(TownySettings.getBailAmountKing()));
					return;
				}

				Resident resident = getResidentOrThrow(player.getUniqueId());
				
				if (!resident.isJailed()) {
					TownyMessaging.sendErrorMsg(player, Translation.of("msg_err_you_aren't currently jailed"));
					return;
				}
				
				if (split[1].equalsIgnoreCase("paybail")) {
					double cost = TownySettings.getBailAmount();
					
					if (resident.isMayor())
						cost = TownySettings.getBailAmountMayor();
					if (resident.isKing())
						cost = TownySettings.getBailAmountKing();
					if (resident.getAccount().canPayFromHoldings(cost)) {
						Town JailTown = TownyUniverse.getInstance().getTown(resident.getJailTown());
						
						if (JailTown == null) {
							TownyMessaging.sendErrorMsg(player, Translation.of("msg_err_not_registered_1", resident.getJailTown()));
							return;
						}
						
						resident.getAccount().payTo(cost, JailTown, "Bail");
						resident.setJailed(false);
						resident.setJailSpawn(0);
						resident.setJailTown("");
						TownyMessaging.sendGlobalMessage(Colors.Red + player.getName() + Translation.of("msg_has_paid_bail"));
						player.teleport(resident.getTown().getSpawn());
						resident.save();
					} else {
						TownyMessaging.sendErrorMsg(player, Colors.Red + Translation.of("msg_err_unable_to_pay_bail"));
					}
				} else {
					TownyMessaging.sendMessage(player, ChatTools.formatTitle("/resident jail"));
					TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident", "jail paybail", ""));
					TownyMessaging.sendMessage(player, Colors.LightBlue + Translation.of("msg_resident_bail_amount") + Colors.Green + TownyEconomyHandler.getFormattedBalance(TownySettings.getBailAmount()));
					TownyMessaging.sendMessage(player, Colors.LightBlue + Translation.of("msg_mayor_bail_amount") + Colors.Green + TownyEconomyHandler.getFormattedBalance(TownySettings.getBailAmountMayor()));
					TownyMessaging.sendMessage(player, Colors.LightBlue + Translation.of("msg_king_bail_amount") + Colors.Green + TownyEconomyHandler.getFormattedBalance(TownySettings.getBailAmountKing()));					
				}

			} else if (split[0].equalsIgnoreCase("set")) {

				/*
				 * perms checked in method.
				 */
				String[] newSplit = StringMgmt.remFirstArg(split);
				residentSet(player, newSplit);

			} else if (split[0].equalsIgnoreCase("toggle")) {

				/*
				 * 
				 */
				String[] newSplit = StringMgmt.remFirstArg(split);
				residentToggle(player, newSplit);

			} else if (split[0].equalsIgnoreCase("friend")) {

				if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_FRIEND.getNode()))
					throw new TownyException(Translation.of("msg_err_command_disable"));

				String[] newSplit = StringMgmt.remFirstArg(split);
				residentFriend(player, newSplit, false, null);

			} else if (split[0].equalsIgnoreCase("spawn")) {

				if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_SPAWN.getNode()))
					throw new TownyException(Translation.of("msg_err_command_disable"));

				Resident res = getResidentOrThrow(player.getUniqueId());
				
				SpawnUtil.sendToTownySpawn(player, split, res, Translation.of("msg_err_cant_afford_tp"), false, false, SpawnType.RESIDENT);
			} else if (TownyCommandAddonAPI.hasCommand(CommandType.RESIDENT, split[0])) {
				TownyCommandAddonAPI.getAddonCommand(CommandType.RESIDENT, split[0]).execute(player, "resident", split);
			} else {
				final Resident resident = TownyUniverse.getInstance().getResidentOpt(split[0])
											.orElseThrow(() -> new TownyException(Translation.of("msg_err_not_registered_1", split[0])));

				if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_OTHERRESIDENT.getNode()) && (!resident.getName().equals(player.getName()))) {
					throw new TownyException(Translation.of("msg_err_command_disable"));
				}
				Bukkit.getScheduler().runTaskAsynchronously(Towny.getPlugin(),
					() -> TownyMessaging.sendMessage(player, TownyFormatter.getStatus(resident, player))
				);
			}

		} catch (Exception x) {
			TownyMessaging.sendErrorMsg(player, x.getMessage());
		}
	}

	/**
	 * Toggle modes for this player.
	 * 
	 * @param player
	 * @param newSplit
	 * @throws TownyException
	 */
	private void residentToggle(Player player, String[] newSplit) throws TownyException {
		TownyPermissionSource permSource = TownyUniverse.getInstance().getPermissionSource();

		Resident resident = TownyUniverse.getInstance().getResidentOpt(player.getUniqueId())
							.orElseThrow(() -> new TownyException(Translation.of("msg_err_not_registered_1", player.getName())));;

		if (newSplit.length == 0) {
			HelpMenu.RESIDENT_TOGGLE.send(player);

			TownyMessaging.sendMsg(resident, (Translation.of("msg_modes_set") + StringMgmt.join(resident.getModes(), ",")));
			return;

		}
		
		// Check if we're reseting before trying for nodes.
		if (newSplit[0].equalsIgnoreCase("reset") || newSplit[0].equalsIgnoreCase("clear")) {
			plugin.removePlayerMode(player);
			return;
		}
		
		if (!permSource.testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_TOGGLE.getNode(newSplit[0].toLowerCase())))
			throw new TownyException(Translation.of("msg_err_command_disable"));

		TownyPermission perm = resident.getPermissions();
		
		Optional<Boolean> choice = Optional.empty();
		if (newSplit.length == 2 && residentToggleChoices.contains(newSplit[0].toLowerCase())) {
			choice = BaseCommand.parseToggleChoice(newSplit[1]);
		}

		// Special case chat spy
		if (newSplit[0].equalsIgnoreCase("spy")) {
			
			if (!permSource.testPermission(player, PermissionNodes.TOWNY_CHAT_SPY.getNode(newSplit[0].toLowerCase())))
				throw new TownyException(Translation.of("msg_err_command_disable"));
			
			resident.toggleMode(newSplit, true);
			return;
			
		} else if (newSplit[0].equalsIgnoreCase("pvp")) {
			
			// Test to see if the pvp cooldown timer is active for the town this resident belongs to.
			if (TownySettings.getPVPCoolDownTime() > 0 && resident.hasTown()  && !permSource.testPermission(player, PermissionNodes.TOWNY_ADMIN.getNode())) {
				if (CooldownTimerTask.hasCooldown(resident.getTown().getName(), CooldownType.PVP))
					throw new TownyException(Translation.of("msg_err_cannot_toggle_pvp_x_seconds_remaining", CooldownTimerTask.getCooldownRemaining(resident.getTown().getName(), CooldownType.PVP))); 
				if (CooldownTimerTask.hasCooldown(resident.getName(), CooldownType.PVP))
					throw new TownyException(Translation.of("msg_err_cannot_toggle_pvp_x_seconds_remaining", CooldownTimerTask.getCooldownRemaining(resident.getName(), CooldownType.PVP)));

			}
			perm.pvp = choice.orElse(!perm.pvp);
			// Add a task for the resident.
			if (TownySettings.getPVPCoolDownTime() > 0 && !permSource.testPermission(player, PermissionNodes.TOWNY_ADMIN.getNode()))
				CooldownTimerTask.addCooldownTimer(resident.getName(), CooldownType.PVP);
		} else if (newSplit[0].equalsIgnoreCase("fire")) {
			perm.fire = choice.orElse(!perm.fire);
		} else if (newSplit[0].equalsIgnoreCase("explosion")) {
			perm.explosion = choice.orElse(!perm.explosion);
		} else if (newSplit[0].equalsIgnoreCase("mobs")) {
			perm.mobs = choice.orElse(!perm.mobs);
		} else if (TownyCommandAddonAPI.hasCommand(CommandType.RESIDENT_TOGGLE, newSplit[0])) {
			TownyCommandAddonAPI.getAddonCommand(CommandType.RESIDENT_TOGGLE, newSplit[0]).execute(player, "resident", newSplit);
		} else {

			resident.toggleMode(newSplit, true);
			return;

		}

		notifyPerms(player, perm);
		resident.save();

	}

	/**
	 * Show the player the new Permission settings after the toggle.
	 * 
	 * @param player
	 * @param perm
	 */
	private void notifyPerms(Player player, TownyPermission perm) {

		TownyMessaging.sendMsg(player, Translation.of("msg_set_perms"));
		TownyMessaging.sendMessage(player, Colors.Green + "PvP: " + ((perm.pvp) ? Colors.Red + "ON" : Colors.LightGreen + "OFF") + Colors.Green + "  Explosions: " + ((perm.explosion) ? Colors.Red + "ON" : Colors.LightGreen + "OFF") + Colors.Green + "  Firespread: " + ((perm.fire) ? Colors.Red + "ON" : Colors.LightGreen + "OFF") + Colors.Green + "  Mob Spawns: " + ((perm.mobs) ? Colors.Red + "ON" : Colors.LightGreen + "OFF"));

	}

	public void listResidents(Player player) {

		TownyMessaging.sendMessage(player, ChatTools.formatTitle(Translation.of("res_list")));
		String colour;
		ArrayList<String> formatedList = new ArrayList<>();
		for (Resident resident : TownyAPI.getInstance().getActiveResidents()) {
			if (player.canSee(BukkitTools.getPlayerExact(resident.getName()))) {
				if (resident.isKing())
					colour = Colors.Gold;
				else if (resident.isMayor())
					colour = Colors.LightBlue;
				else
					colour = Colors.White;
				formatedList.add(colour + resident.getName() + Colors.White);
			}
		}
		for (String line : ChatTools.list(formatedList))
			TownyMessaging.sendMessage(player, line);
	}
	
	public void listResidents(CommandSender sender) {

		TownyMessaging.sendMessage(sender, ChatTools.formatTitle(Translation.of("res_list")));
		String colour;
		ArrayList<String> formatedList = new ArrayList<>();
		for (Resident resident : TownyAPI.getInstance().getActiveResidents()) {
			if (resident.isKing())
				colour = Colors.Gold;
			else if (resident.isMayor())
				colour = Colors.LightBlue;
			else
				colour = Colors.White;
			formatedList.add(colour + resident.getName() + Colors.White);
		}
		for (String line : ChatTools.list(formatedList))
			TownyMessaging.sendMessage(sender, line);
	}

	/**
	 * 
	 * Command: /resident set [] ... []
	 * 
	 * @param player - Player.
	 * @param split  - Current command arguments.
	 * @throws TownyException - Exception.
	 */

	/*
	 * perm [resident/outsider] [build/destroy] [on/off]
	 */

	public void residentSet(Player player, String[] split) throws TownyException {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();

		if (split.length == 0) {
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident set", "perm ...", "'/resident set perm' " + Translation.of("res_5")));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident set", "mode ...", "'/resident set mode' " + Translation.of("res_5")));
		} else {
			Optional<Resident> resOpt = townyUniverse.getResidentOpt(player.getUniqueId());
			
			if (!resOpt.isPresent()) {
				TownyMessaging.sendErrorMsg(player, Translation.of("msg_err_not_registered_1", player.getName()));
				return;
			}

			Resident resident = resOpt.get();

			if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_RESIDENT_SET.getNode(split[0].toLowerCase())))
				throw new TownyException(Translation.of("msg_err_command_disable"));

			if (split[0].equalsIgnoreCase("perm")) {

				String[] newSplit = StringMgmt.remFirstArg(split);
				TownCommand.setTownBlockPermissions(player, resident, resident.getPermissions(), newSplit, true);

			} else if (split[0].equalsIgnoreCase("mode")) {

				String[] newSplit = StringMgmt.remFirstArg(split);
				setMode(player, newSplit);
			} else if (TownyCommandAddonAPI.hasCommand(CommandType.RESIDENT_SET, split[0])) {
				TownyCommandAddonAPI.getAddonCommand(CommandType.RESIDENT_SET, split[0]).execute(player, "resident", split);
			} else {

				TownyMessaging.sendErrorMsg(player, Translation.of("msg_err_invalid_property", "resident"));
				return;

			}
			
			resident.save();
		}
	}

	private void setMode(Player player, String[] split) {

		if (split.length == 0) {
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident set mode", "clear", ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident set mode", "[mode] ...[mode]", ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "map", "", Translation.of("mode_1")));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "townclaim", "", Translation.of("mode_2")));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "townunclaim", "", Translation.of("mode_3")));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "tc", "", Translation.of("mode_4")));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "nc", "", Translation.of("mode_5")));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "ignoreplots", "", ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "constantplotborder", "", ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Mode", "plotborder", "", ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("Eg", "/resident set mode", "map townclaim town nation general", ""));

			return;
		}

		if (split[0].equalsIgnoreCase("reset") || split[0].equalsIgnoreCase("clear")) {
			plugin.removePlayerMode(player);
			return;
		}

		List<String> list = Arrays.asList(split);
		if ((list.contains("spy")) && !TownyUniverse.getInstance().getPermissionSource().testPermission(player, PermissionNodes.TOWNY_CHAT_SPY.getNode())) {
			TownyMessaging.sendErrorMsg(player, Translation.of("msg_err_command_disable"));
			return;
		}

		plugin.setPlayerMode(player, split, true);

	}

	public static void residentFriend(Player player, String[] split, boolean admin, Resident resident) {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();

		if (split.length == 0) {
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident friend", "add " + Translation.of("res_2"), ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident friend", "remove " + Translation.of("res_2"), ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident friend", "list", ""));
			TownyMessaging.sendMessage(player, ChatTools.formatCommand("", "/resident friend", "clear", ""));
		} else {
			if (!admin) {
				Optional<Resident> resOpt = townyUniverse.getResidentOpt(player.getUniqueId());

				if (!resOpt.isPresent()) {
					TownyMessaging.sendErrorMsg(player, Translation.of("msg_err_not_registered_1", player.getName()));
					return;
				}
				
				resident = resOpt.get();
			}

			if (split[0].equalsIgnoreCase("add")) {

				String[] names = StringMgmt.remFirstArg(split);
				residentFriendAdd(player, resident, townyUniverse.getDataSource().getResidents(player, names));

			} else if (split[0].equalsIgnoreCase("remove")) {

				String[] names = StringMgmt.remFirstArg(split);
				residentFriendRemove(player, resident, townyUniverse.getDataSource().getResidents(player, names));
			
			} else if (split[0].equalsIgnoreCase("list")) {

				residentFriendList(player, resident);

			} else if (split[0].equalsIgnoreCase("clearlist") || split[0].equalsIgnoreCase("clear")) {

				residentFriendRemove(player, resident, resident.getFriends());

			}

		}
	}

	private static void residentFriendList(Player player, Resident resident) {
		
		TownyMessaging.sendMessage(player, ChatTools.formatTitle(Translation.of("friend_list")));
		String colour;
		ArrayList<String> formatedList = new ArrayList<>();
		for (Resident friends : resident.getFriends()) {
			if (friends.isKing())
				colour = Colors.Gold;
			else if (friends.isMayor())
				colour = Colors.LightBlue;
			else
				colour = Colors.White;
			formatedList.add(colour + friends.getName() + Colors.White);
		}
		for (String line : ChatTools.list(formatedList))
			TownyMessaging.sendMessage(player, line);
	}

	public static void residentFriendAdd(Player player, Resident resident, List<Resident> invited) {

		ArrayList<Resident> remove = new ArrayList<>();

		for (Resident newFriend : invited) {
			try {

				resident.addFriend(newFriend);
				plugin.deleteCache(newFriend.getName());

			} catch (AlreadyRegisteredException e) {

				remove.add(newFriend);

			}
		}

		/*
		 *  Remove any names from the list who were already listed as friends
		 */
		for (Resident newFriend : remove) {

			invited.remove(newFriend);

		}

		/*
		 * If we added any friends format the confirmation message.
		 */
		if (invited.size() > 0) {

			StringBuilder msg = new StringBuilder(Translation.of("res_friend_added"));

			for (Resident newFriend : invited) {

				msg.append(newFriend.getName()).append(", ");
				Player p = BukkitTools.getPlayer(newFriend.getName());

				if (p != null) {

					TownyMessaging.sendMsg(p, Translation.of("msg_friend_add", player.getName()));

				}

			}

			msg = new StringBuilder(msg.substring(0, msg.length() - 2));
			msg.append(Translation.of("msg_to_list"));
			TownyMessaging.sendMsg(player, msg.toString());
			resident.save();

		} else {

			TownyMessaging.sendErrorMsg(player, Translation.of("msg_invalid_name"));

		}
	}

	public static void residentFriendRemove(Player player, Resident resident, List<Resident> kicking) {

		List<Resident> remove = new ArrayList<>();
		List<Resident> toKick = new ArrayList<>(kicking);

		for (Resident friend : toKick) {
			resident.removeFriend(friend);
			plugin.deleteCache(friend.getName());
		}
		// remove invalid names so we don't try to send them messages
		if (remove.size() > 0)
			for (Resident friend : remove)
				toKick.remove(friend);

		if (toKick.size() > 0) {
			StringBuilder msg = new StringBuilder(Translation.of("msg_removed"));
			Player p;
			for (Resident member : toKick) {
				msg.append(member.getName()).append(", ");
				p = BukkitTools.getPlayer(member.getName());
				if (p != null)
					TownyMessaging.sendMsg(p, Translation.of("msg_friend_remove", player.getName()));
			}
			msg = new StringBuilder(msg.substring(0, msg.length() - 2));
			msg.append(Translation.of("msg_from_list"));
			TownyMessaging.sendMsg(player, msg.toString());
			resident.save();
		} else
			TownyMessaging.sendErrorMsg(player, Translation.of("msg_invalid_name"));

	}
}
