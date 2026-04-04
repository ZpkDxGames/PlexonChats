PlexonChats
A powerful, modern, and highly configurable chat management plugin for Paper servers.

Take full control over your server’s chat experience with PlexonChats. Designed to be lightweight and feature-rich, it offers everything from proximity-based local chat to interactive item showcasing, private messaging, and customizable player mentions—all fully tailored using modern MiniMessage formatting.

✨ Key Features
Global & Local Chat Channels: Seamlessly manage proximity-based Local chat (with configurable block radiuses) and a server-wide Global channel.

Quick Chat Shortcuts: Don't want to switch channels? Players can use customizable prefix shortcuts (like ! for Global) or quick commands (/g, /l) to send messages instantly.

Modern Formatting: Full support for Advntr MiniMessage formatting (Gradients, Hex colors, Click events, Hover texts) without the hassle of legacy color codes!

Smart Player Mentions: Grab a player's attention instantly! Type @playername to trigger customizable action-bar notifications and sound alerts for that player.

Interactive Chat Items ([item] / @hand): Allow players to show off their loot! Typing [item] or @hand in chat will display their held item, complete with interactive hover tooltips showing the item's precise lore and enchantments.

Private Messaging System: Built-in direct messaging (/msg, /tell, /w) with a quick /reply command for fluid conversations.

Interactive GUI: Let players manage their active chat channels effortlessly with a customizable in-game GUI (/chat gui).

Announcements: Broadcast important notices to your entire server with the /announce command.
Seamless Integrations: Optional out-of-the-box hooks for PlaceholderAPI and Vault to display ranks, prefixes, and custom placeholders directly in the chat!

💻 Commands
/chat <channel|gui|reload> - Main plugin command (plexonchats.use)
/g <message> - Quick Global chat (plexonchats.global)
/l <message> - Quick Local chat (plexonchats.local)
/msg <player> <message> (also /tell, /w) - Private messaging (plexonchats.tell)
/reply <message> (also /r) - Quick reply to your last message (plexonchats.tell)
/announce <message> (also /broadcast, /bc) - Server announcements (plexonchats.announce)

⚙️ Configuration & Customization
Almost everything in PlexonChats is configurable! From chat radii to gradient formats, sounds, and action bar alerts, the config.yml provides you with extensive freedom. Change the sounds played on mentions, customize the "no one is nearby" local chat message, or tweak the GUI's look and feel—it's completely up to you.

🔧 Requirements
Server Software: Paper 1.21.11+ (or forks)
Java: Java 25+
(Optional) PlaceholderAPI & Vault