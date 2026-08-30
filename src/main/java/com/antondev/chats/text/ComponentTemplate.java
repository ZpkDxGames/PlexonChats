package com.antondev.chats.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Values are components, never re-parsed MiniMessage source. */
public final class ComponentTemplate {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z0-9_]+)}|(%[a-zA-Z0-9_]+%)");
    private final MiniMessage miniMessage;
    public ComponentTemplate(MiniMessage miniMessage) { this.miniMessage = miniMessage; }

    public Component render(String source, Function<String, Component> resolver) {
        Matcher matcher = TOKEN.matcher(source);
        StringBuilder template = new StringBuilder();
        TagResolver.Builder tags = TagResolver.builder();
        Map<String, String> names = new HashMap<>();
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String name = names.get(key);
            if (name == null) {
                Component value = resolver.apply(key);
                if (value == null) {
                    matcher.appendReplacement(template, Matcher.quoteReplacement(matcher.group()));
                    continue;
                }
                name = "plexon_value_" + names.size();
                names.put(key, name);
                tags.resolver(Placeholder.component(name, value));
            }
            matcher.appendReplacement(template, "<" + name + ">");
        }
        matcher.appendTail(template);
        return miniMessage.deserialize(template.toString(), tags.build());
    }
    public Component render(String source, Map<String, Component> values) { return render(source, values::get); }
}
