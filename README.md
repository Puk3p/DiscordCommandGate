# DiscordCommandGate

**Overview**
The DiscordConfirm plugin integrates Minecraft with Discord to enhance security and administration by requiring certain player commands to be confirmed via Discord before execution. This plugin is particularly useful for servers where moderation and control over sensitive commands are critical. It uses the LuckPerms API for permission management and JDA (Java Discord API) for Discord integration.

**Features**

⚪ Command Confirmation: Commands that can potentially affect the server's operation or player's experience need to be confirmed by the player on a designated Discord channel. This adds an extra layer of security and ensures that powerful commands are not misused.

⚪ Role-Based Access Control: The plugin checks the player's role via LuckPerms. Only players with specific roles such as "operator" or "senior" are required to confirm their commands on Discord.

⚪ Discord Integration: Utilizes JDA to send command confirmation requests to a specific Discord channel. Players receive notifications in Discord and can confirm commands by reacting to these messages.

⚪ User Mapping: Maintains a map of Discord IDs to Minecraft usernames, ensuring that commands are confirmed by the correct player on Discord.

⚪ Command Logging: Every command that requires confirmation is logged both on the server and optionally in a Discord channel for audit purposes.

⚪ Player Restriction: Players who have commands pending confirmation are temporarily restricted from performing certain actions in the game, like moving or executing other commands, which is configurable.

**Installation**
  1. Prerequisites:

Minecraft server (Spigot, Bukkit, or any compatible fork)
JDA (Java Discord API) library
LuckPerms plugin installed
Setup:

Place the DiscordConfirm.jar file in your server's plugins directory.
Restart the server or load the plugin dynamically.
Configuration:

Configure the config.yml file with your Discord bot token and channel ID where confirmation requests will be sent.
Update the mapping of Minecraft usernames to Discord IDs according to your server's user base.
Usage
Commands:
/grosu: Example command that requires confirmation via Discord. You can modify the plugin to include more commands based on your needs.
Discord:
Players will see embedded messages in the designated Discord channel asking for confirmation. They can confirm by typing !confirm in the channel.
Permissions
Use LuckPerms to manage who has the ability to require command confirmations and who is subject to these confirmations.
Contributing
Contributions are welcome. Please fork the repository, make your changes, and submit a pull request.

Support
If you encounter any issues or require assistance, please open an issue on the GitHub repository.

License
This project is licensed under the MIT License - see the LICENSE file for details.
