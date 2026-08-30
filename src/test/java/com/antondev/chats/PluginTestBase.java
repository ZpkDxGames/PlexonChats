package com.antondev.chats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.extension.ExtendWith(RequiredMockBukkitCoverage.class)
public abstract class PluginTestBase {
    protected ServerMock server;
    protected PlexonChats plugin;
    @BeforeEach void startPlugin() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonChats.class);
    }
    @AfterEach void stopPlugin() { MockBukkit.unmock(); }

    protected void config(Consumer<YamlConfiguration> edit) throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        var yaml = YamlConfiguration.loadConfiguration(file);
        edit.accept(yaml);
        yaml.save(file);
        assertTrue(plugin.reloadPlugin(), "Modified test configuration should load");
    }
    protected PlayerMock player(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(false);
        return player;
    }
    protected static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }
    protected static String next(PlayerMock player) { return plain(player.nextComponentMessage()); }
    protected static void drain(PlayerMock player) { while (player.nextComponentMessage() != null) {} }
}
