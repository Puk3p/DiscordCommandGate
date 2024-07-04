package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

import org.bukkit.ChatColor;


public class DiscordConfirm extends JavaPlugin implements CommandExecutor, Listener {
    private JDA jda;
    private final String discordChannelId = "1254808449229918339";
    private LuckPerms luckPerms;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Map<String, Player> pendingConfirmations = new HashMap<>();
    private Map<String, Message> pendingMessages = new HashMap<>();
    private Set<Player> blockedPlayers = new HashSet<>();
    private Map<String, String> discordToMinecraftMap = new HashMap<>();
    private Map<String, String> discordIDFromMinecraft = new HashMap<>();
    private Map<String, Integer> playerWarnings = new HashMap<>();
    private Map<UUID, PlayerData> playerData = new HashMap<>();
    private Set<UUID> frozenPlayers = new HashSet<>();
    private Map<String, ScheduledFuture<?>> pendingTasks = new HashMap<>();
    private CommandConfirmationManager confirmationManager;
    private Set<String> excludedGroups = new HashSet<>(Arrays.asList("manager", "administrator", "owner"));


    @Override
    public void onEnable() {
        Config config = new Config();
        config.load();
        String discordToken = config.getDiscordToken();

        if (discordToken == null || discordToken.isEmpty()) {
            getLogger().severe("Discord token not configured in config.yml. Please configure and restart.");
            this.setEnabled(false);
            return;
        }

        try {
            jda = JDABuilder.createDefault(discordToken).addEventListeners(new DiscordListener()).build();
            getLogger().info("Connected to Discord!");
        } catch (LoginException e) {
            getLogger().severe("Failed to login to Discord: " + e.getMessage());
            this.setEnabled(false);
            return;
        }

        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("Failed to load LuckPerms API: " + e.getMessage());
            this.setEnabled(false);
            return;
        }

        setupID_DISCORD();
        setupNicknameMap();
        printDiscordToMinecraftMap();
        PluginCommand grosuCommand = getCommand("grosu");
        if (grosuCommand != null) {
            grosuCommand.setExecutor(this);
        } else {
            getLogger().warning("Failed to register command 'grosu'.");
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin has been activated successfully!");
        checkAndCreateDataFolder();

        confirmationManager = new CommandConfirmationManager(60000);
    }

    @Override
    public void onDisable() {
        for (ScheduledFuture<?> task : pendingTasks.values()) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
        pendingTasks.clear();
        getLogger().info("Cleaned up all pending tasks.");
    }

    public void printDiscordToMinecraftMap() {
        getLogger().info("Discord to Minecraft map: " + discordToMinecraftMap.toString());
    }

    private void setupNicknameMap() {
        discordToMinecraftMap.put(".callmegeorge", "Puk3p");
        discordToMinecraftMap.put("mateif2", "mateif19");
        discordToMinecraftMap.put("redaroro", "redaroro");
        discordToMinecraftMap.put("andreiusq", "andreiusq");
        discordToMinecraftMap.put("me_naty23", "Rigged_Walrus");
        discordToMinecraftMap.put(".just.andr3w_", "Agamer1_");
        discordToMinecraftMap.put("andreighi_", "AndreiGhi_");
        discordToMinecraftMap.put("__unsainted", "unsainted__");
        discordToMinecraftMap.put("cipri5061", "x_CipriPlays_Ro");
        discordToMinecraftMap.put("kennt6", "CrazyZone");
        discordToMinecraftMap.put("elynox._.", "_DarkShadow_");
    }

    private void setupID_DISCORD() {
        discordIDFromMinecraft.put("Puk3p", "679024166565052441");
        discordIDFromMinecraft.put("mateif19", "809732706372026378");
        discordIDFromMinecraft.put("redaroro", "280020774775947264");
        discordIDFromMinecraft.put("andreiusq", "474773973650112533");
        discordIDFromMinecraft.put("Rigged_Walrus", "533012827707932683");
        discordIDFromMinecraft.put("Agamer1_", "1177948874825015356");
        discordIDFromMinecraft.put("AndreiGhi_", "376307718480199681");
        discordIDFromMinecraft.put("unsainted__", "808773626240434177");
        discordIDFromMinecraft.put("x_CipriPlays_Ro", "165093506262630400");
        discordIDFromMinecraft.put("CrazyZone", "265531073037991937");
        discordIDFromMinecraft.put("_DarkShadow_", "723541259196694831");
    }

    private String getDiscordIDFromMinecraftUsername(String minecraftUsername) {
        return discordIDFromMinecraft.get(minecraftUsername);
    }

    private void logCommandUsage(String playerName, String command, boolean confirmed) {
        File logFile = new File(getDataFolder(), "command_logs.txt");
        try (FileWriter fw = new FileWriter(logFile, true); PrintWriter pw = new PrintWriter(fw)) {
            String status = confirmed ? "confirmed" : "not confirmed";
            String logEntry = String.format("%s - Command: %s was %s", new Date(), command, status);
            pw.println(logEntry);
            pw.println();
        } catch (IOException e) {
            getLogger().severe("Could not write to command log file: " + e.getMessage());
        }
    }


    private void checkAndCreateDataFolder() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File logFile = new File(dataFolder, "command_logs.txt");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create command log file: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("grosu")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(player.getUniqueId());
                userFuture.thenAccept(user -> {
                    if (user.getPrimaryGroup().equalsIgnoreCase("operator") || user.getPrimaryGroup().equalsIgnoreCase("senior")) {
                        TextChannel channel = jda.getTextChannelById(discordChannelId);
                        if (channel != null) {
                            sendCommandRequestEmbed(channel, player.getName(), command.getName(), player);
                            pendingConfirmations.put(player.getName(), player);
                            pendingMessages.put(player.getName(), null);
                            blockPlayer(player);
                        } else {
                            sender.sendMessage("Canalul Discord nu a fost găsit.");
                        }
                    } else {
                        sender.sendMessage("Nu ai permisiunile necesare pentru a folosi această comandă.");
                    }
                });
                return true;
            } else {
                sender.sendMessage("Numai jucătorii pot folosi această comandă.");
            }
        }
        return false;
    }

    private void blockPlayer(Player player) {
        blockedPlayers.add(player);
        frozenPlayers.add(player.getUniqueId());
        hideChatForPlayer(player);

        String title = ChatColor.translateAlternateColorCodes('&', "&c&lCONFIRMA PE DISCORD!");
        String subtitle = ChatColor.translateAlternateColorCodes('&', "&7Comanda ta este în așteptare de confirmare.");

        player.sendTitle(title, subtitle, 10, 70, 20);
        getLogger().info("Player " + player.getName() + " has been blocked pending confirmation.");
    }

    private void unblockPlayer(Player player) {
        blockedPlayers.remove(player);
        pendingConfirmations.remove(player.getName());
        pendingMessages.remove(player.getName());
        showChatForPlayer(player);
        frozenPlayers.remove(player.getUniqueId());

        String title = ChatColor.translateAlternateColorCodes('&', "&a&lAI CONFIRMAT!");
        String subtitle = ChatColor.translateAlternateColorCodes('&', "&7Comanda ta a fost confirmată.");

        player.sendTitle(title, subtitle, 10, 70, 20);
        getLogger().info("Player " + player.getName() + " has been unblocked.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.contains(player.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
                player.sendMessage("§cNu te poți mișca până nu confirmi comanda pe Discord!");
            }
        }
    }


    private void hideChatForPlayer(Player player) {
        if (blockedPlayers.contains(player)) {
            getLogger().info("Player " + player.getName() + " is already hidden.");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hidePlayer(this, player);
        }
        player.sendMessage("Ai fost temporar restricționat până la confirmarea comenzii pe Discord.");
        getLogger().info("Player " + player.getName() + " has been hidden.");
    }


    private void sendCommandRequestEmbed(TextChannel channel, String userName, String command, Player player) {
        String discordID = getDiscordIDFromMinecraftUsername(userName);
        String PING_DISCORD = discordID != null ? "<@" + discordID + ">" : "";

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Cerere de Comandă");
        embed.setDescription("Utilizatorul " + userName + " (" + PING_DISCORD + ") dorește să execute comanda /" + command + ".");
        embed.addField("Confirmare", "Tastează !confirm pentru a confirma.", false);
        embed.setColor(Color.YELLOW);
        String iconUrl = "https://uploads.dailydot.com/2018/10/olli-the-polite-cat.jpg?q=65&auto=format&w=800&ar=2:1&fit=crop";
        embed.setFooter("Pucepul Aprobator", iconUrl);
        embed.setThumbnail("https://i.kym-cdn.com/entries/icons/original/000/043/403/cover3.jpg");

        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String currentDateAndTime = dateFormat.format(new Date());

        embed.addField("Dată și oră", currentDateAndTime, true);
        embed.addField("Sectiune", "**SURVIVAL**", true);
        embed.setTimestamp(new java.util.Date().toInstant());
        playerData.put(player.getUniqueId(), new PlayerData(command));

        channel.sendMessageEmbeds(embed.build()).queue(message -> {
            pendingConfirmations.put(player.getName(), player);
            pendingMessages.put(player.getName(), message);
            blockedPlayers.add(player);
            hideChatForPlayer(player);

            // Schedule the timeout task
            ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
                if (pendingConfirmations.containsKey(player.getName())) {
                    PlayerData pData = playerData.get(player.getUniqueId());

                    if (pData != null) {
                        logCommandUsage(player.getName(), pData.command, false);
                    }
                    pendingConfirmations.remove(player.getName());
                    pendingMessages.remove(player.getName());
                    blockedPlayers.remove(player);
                    showChatForPlayer(player);

                    int warnings = playerWarnings.getOrDefault(player.getName(), 0) + 1;
                    playerWarnings.put(player.getName(), warnings);

                    if (warnings >= 2) {
                        getServer().getScheduler().runTask(this, () -> {
                            unblockPlayer(player);
                            player.kickPlayer("Ai fost banat pentru neconfirmarea repetată a comenzilor!");
                            getServer().getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), "Neconfirmarea repetată a comenzilor.", null, null);

                            EmbedBuilder banEmbed = new EmbedBuilder();
                            banEmbed.setTitle("Cerere de Comandă - Banat");
                            banEmbed.setDescription("Utilizatorul " + player.getName() + " a fost banat pentru neconfirmarea repetată a comenzilor.");
                            Color RGB = new Color(239, 0, 255);
                            banEmbed.setColor(RGB);
                            banEmbed.setFooter("Pucepul Aprobator", iconUrl);
                            banEmbed.setThumbnail("https://a.pinatafarm.com/940x529/254350840f/white-cat-da2c837628aa5a4d253f3956efa6244f-meme.jpeg");

                            DateFormat dateFormat1 = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                            String currentDateAndTime1 = dateFormat1.format(new Date());

                            banEmbed.addField("Dată și oră", currentDateAndTime1, true);
                            banEmbed.addField("Sectiune", "**SURVIVAL**", true);
                            banEmbed.setTimestamp(new java.util.Date().toInstant());
                            message.editMessageEmbeds(banEmbed.build()).queue();

                            playerWarnings.remove(player.getName());
                        });
                    } else {
                        getServer().getScheduler().runTask(this, () -> {
                            unblockPlayer(player);
                            String discordID1 = getDiscordIDFromMinecraftUsername(userName);
                            String PING_DISCORD1 = discordID1 != null ? "<@" + discordID1 + ">" : "";

                            player.kickPlayer("Nu ai confirmat comanda în timp util!");
                            EmbedBuilder errorEmbed = new EmbedBuilder();
                            errorEmbed.setTitle("Cerere de Comandă - Neconfirmată");
                            errorEmbed.setDescription("Utilizatorul " + userName + " (" + PING_DISCORD1 + ") a încercat să execute comanda /" + command + " dar nu a confirmat-o.");
                            errorEmbed.setColor(Color.RED);
                            errorEmbed.setFooter("Pucepul Aprobator", iconUrl);
                            errorEmbed.setThumbnail("https://preview.redd.it/my-white-cat-looks-like-a-meme-and-her-cats-make-those-faces-v0-s3wr054razbb1.jpg?width=1080&crop=smart&auto=webp&s=61a9aa6a30a7300bf4d3d0ed3a711e21c973db58");

                            DateFormat dateFormat2 = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                            String currentDateAndTime2 = dateFormat2.format(new Date());

                            errorEmbed.addField("Dată și oră", currentDateAndTime2, true);
                            errorEmbed.addField("Sectiune", "**SURVIVAL**", true);
                            errorEmbed.setTimestamp(new java.util.Date().toInstant());
                            message.editMessageEmbeds(errorEmbed.build()).queue();
                        });
                    }
                }
            }, 15, TimeUnit.SECONDS);

            pendingTasks.put(player.getName(), timeoutTask);
        });
    }


    private void showChatForPlayer(Player player) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            player.showPlayer(this, p);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (blockedPlayers.contains(player)) {
            event.setCancelled(true);
            player.sendMessage("Nu poți trimite mesaje în chat până nu confirmi comanda pe Discord.");
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1);

        Set<String> monitoredCommands = new HashSet<>(Arrays.asList(
                "lp",
                "eco",
                "npc",
                "grosu",
                "giveall",
                "give",
                "giveall:giveall",
                "citizens",
                "citizens:citizens",
                "citizens:npc",
                "citizens:npc2",
                "citizens:template",
                "citizens:wp",
                "citizens:waypoints",
                "citizens:waypoint",
                "citizens:trc",
                "citizens:traitc",
                "citizens:trait",
                "citizens:tpl",
                "npc",
                "npc2",
                "template",
                "wp",
                "waypoints",
                "waypoint",
                "trc",
                "traitc",
                "trait",
                "tpl",
                "perm",
                "perms",
                "permission",
                "permissions",
                "luckperms",
                "luckperms:luckperms",
                "luckperms:lp",
                "luckperms:perm",
                "luckperms:perms",
                "luckperms:permission",
                "luckperms:permissions",
                "epay",
                "essentials:epay"
        ));


        String commandName = command.split(" ")[0].toLowerCase();

        if (monitoredCommands.contains(commandName)) {
            event.setCancelled(true);
            if (blockedPlayers.contains(player)) {
                player.sendMessage("Nu poți folosi comenzi până nu confirmi comanda anterioară pe Discord.");
                return;
            }

            CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(player.getUniqueId());
            userFuture.thenAccept(user -> {
                String primaryGroup = user.getPrimaryGroup();
                if (excludedGroups.contains(primaryGroup)) {
                    // Permite execuția comenzii direct, fără confirmare
                    Bukkit.getServer().getScheduler().runTask(DiscordConfirm.this, () -> {
                        boolean result = player.performCommand(command);
                        getLogger().info("Command execution result for " + player.getName() + ": " + result);
                    });
                } else if (primaryGroup.equalsIgnoreCase("operator") || primaryGroup.equalsIgnoreCase("senior")) {
                    TextChannel channel = jda.getTextChannelById(discordChannelId);
                    if (channel != null) {
                        sendCommandRequestEmbed(channel, player.getName(), command, player);
                        blockPlayer(player);
                    } else {
                        player.sendMessage("Canalul Discord nu a fost găsit.");
                    }
                } else {
                    player.sendMessage("Nu ai permisiunile necesare pentru a folosi această comandă.");
                }
            });
        }
    }



    public class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) {
                return;
            }

            if (!event.getChannel().getId().equals(discordChannelId)) {
                logDiscordMessage(event);
                return;
            }

            String[] contentParts = event.getMessage().getContentRaw().trim().split(" ");
            getLogger().info("Processing command: " + Arrays.toString(contentParts));
            if (contentParts[0].equalsIgnoreCase("!confirm") && contentParts.length == 1) {
                String discordUser = event.getAuthor().getName().toLowerCase().trim();
                String minecraftUsername = discordToMinecraftMap.get(discordUser);

                getLogger().info("Mapped Discord user " + discordUser + " to Minecraft user " + minecraftUsername);

                if (minecraftUsername != null && pendingConfirmations.containsKey(minecraftUsername)) {
                    Player player = pendingConfirmations.get(minecraftUsername);
                    PlayerData pData = playerData.get(player.getUniqueId());
                    if (player != null && pData != null) {
                        executeConfirmedCommand(player, pendingMessages.get(minecraftUsername));
                        event.getChannel().sendMessage("Comanda `" + pData.command + "` a fost confirmată și executată pentru " + minecraftUsername + ".").queue();
                    } else {
                        event.getChannel().sendMessage("Jucătorul nu este disponibil pentru confirmare sau comanda nu corespunde.").queue();
                    }
                }
            }
        }

        private void executeConfirmedCommand(Player player, Message confirmMessage) {
            getLogger().info("Attempting to execute command for " + player.getName());
            if (confirmMessage != null && pendingConfirmations.containsKey(player.getName())) {
                getLogger().info("Executing confirmed command for " + player.getName());

                // Cancel any pending timeout task
                ScheduledFuture<?> task = pendingTasks.remove(player.getName());
                if (task != null && !task.isDone()) {
                    task.cancel(true);
                }

                unblockPlayer(player);
                pendingConfirmations.remove(player.getName());
                pendingMessages.remove(player.getName());
                blockedPlayers.remove(player);

                sendSuccessEmbed(confirmMessage, player.getName());

                PlayerData pData = playerData.get(player.getUniqueId());
                if (pData != null) {
                    String command = pData.command;

                    // Verificăm dacă comanda a fost confirmată recent
                    if (!confirmationManager.isCommandConfirmed(command)) {
                        getLogger().info("Dispatching command '" + command + "' as player");
                        Bukkit.getServer().getScheduler().runTask(DiscordConfirm.this, () -> {
                            boolean result = player.performCommand(command);
                            getLogger().info("Command execution result for " + player.getName() + ": " + result);

                            logCommandUsage(player.getName(), command, true);

                            // Confirmăm comanda pentru a preveni reconfirmarea
                            confirmationManager.confirmCommand(command);
                        });
                    } else {
                        getLogger().info("Command '" + command + "' was recently confirmed. Skipping reconfirmation.");
                    }
                } else {
                    getLogger().warning("No command found for player " + player.getName());
                }
            } else {
                getLogger().warning("Attempt to execute confirmed command with invalid state for " + player.getName());
            }
        }



        private void logDiscordMessage(MessageReceivedEvent event) {
            File logFile = new File(getDataFolder(), "discord.txt");
            try (FileWriter fw = new FileWriter(logFile, true); PrintWriter pw = new PrintWriter(fw)) {
                String logEntry = String.format("%s - [%s] %s: %s", new Date(), event.getChannel().getName(), event.getAuthor().getName(), event.getMessage().getContentRaw());
                pw.println(logEntry);
                pw.println();
            } catch (IOException e) {
                getLogger().severe("Could not write to discord log file: " + e.getMessage());
            }
        }


        private void sendSuccessEmbed(Message message, String playerName) {
            if (message != null) {
                getLogger().info("Preparing to update message for " + playerName);
                EmbedBuilder successEmbed = new EmbedBuilder();
                successEmbed.setTitle("Comandă Confirmată");
                successEmbed.setDescription("Utilizatorul " + playerName + " a confirmat comanda și a fost executată.");
                successEmbed.setColor(new Color(0, 255, 0));
                successEmbed.setTimestamp(new java.util.Date().toInstant());
                successEmbed.setFooter("Pucepul Aprobator", "https://uploads.dailydot.com/2018/10/olli-the-polite-cat.jpg?q=65&auto=format&w=800&ar=2:1&fit=crop");
                successEmbed.setThumbnail("https://imgflip.com/s/meme/Smiling-Cat.jpg");

                DateFormat dateFormat3 = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                String currentDateAndTime3 = dateFormat3.format(new Date());

                successEmbed.addField("Dată și oră", currentDateAndTime3, true);
                successEmbed.addField("Sectiune", "**SURVIVAL**", true);
                message.editMessageEmbeds(successEmbed.build()).queue(
                        success -> getLogger().info("Message successfully updated for " + playerName),
                        failure -> getLogger().severe("Failed to update message for " + playerName + ": " + failure.getMessage())
                );
            } else {
                getLogger().warning("Attempted to send success embed with null message reference for " + playerName);
            }
        }
    }
}