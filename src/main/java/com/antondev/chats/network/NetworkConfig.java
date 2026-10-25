package com.antondev.chats.network;

import com.antondev.chats.protocol.Protocol;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Validate before config generation publication, including disabled bridge values. */
public final class NetworkConfig {
    private NetworkConfig() {}
    public static void validate(YamlConfiguration yaml) {
        String id=yaml.getString("network.instance-id","lobby");
        if(!Protocol.validServer(id))throw new IllegalArgumentException("Invalid network.instance-id");
        List<String> allowed=yaml.contains("network.allowed-server-ids")?yaml.getStringList("network.allowed-server-ids"):List.of("lobby","smp","towny");
        if(allowed.isEmpty()||allowed.size()>16||new HashSet<>(allowed).size()!=allowed.size()||!allowed.contains(id)||allowed.stream().anyMatch(value->!Protocol.validServer(value)))throw new IllegalArgumentException("Invalid network.allowed-server-ids");
        int capacity=yaml.getInt("network.queue-capacity",256);
        if(capacity<1||capacity>1024)throw new IllegalArgumentException("network.queue-capacity must be 1..1024");
        String keyFile=yaml.getString("network.authentication-key-file","network.key");
        if(!keyFile.matches("[a-zA-Z0-9_/-]+\\.key")||keyFile.startsWith("/")||keyFile.contains(".."))throw new IllegalArgumentException("network.authentication-key-file must be a relative .key path");
        Set<String> prefixes=new HashSet<>();
        for(String channel:List.of("local","global","universal")){
            String prefix=yaml.getString("channels."+channel+".shortcut-prefix","");
            if(prefix.length()>16||prefix.contains("\n")||prefix.contains("\r")||(!prefix.isEmpty()&&!prefixes.add(prefix)))throw new IllegalArgumentException("Channel shortcuts must be unique and at most 16 characters");
        }
        for(String server:allowed){String label=yaml.getString("network.server-labels."+server,server);if(label.isBlank()||label.length()>32||label.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid network server label");}
        if(yaml.getString("channels.universal.format","{server} {message}").indexOf("{server}")<0)throw new IllegalArgumentException("Universal format must include {server}");
    }
}
