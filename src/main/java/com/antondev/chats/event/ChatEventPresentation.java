package com.antondev.chats.event;

import com.antondev.chats.text.ComponentTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders validated configurable Chat Event cards without reparsing dynamic values as MiniMessage. */
public final class ChatEventPresentation {
    private final ComponentTemplate templates = new ComponentTemplate(MiniMessage.miniMessage());

    public List<Component> render(ChatEventConfig config, String state, Map<String, Component> values) {
        ChatEventConfig.Presentation presentation = config.presentation();
        Map<String, Component> context = new LinkedHashMap<>(values);
        context.put("separator", templates.render(presentation.separator(), Map.of()));
        ArrayList<Component> result = new ArrayList<>();
        for (int index = 0; index < presentation.blankLinesBefore(); index++) result.add(Component.empty());
        for (String line : presentation.card(state)) result.add(line.isEmpty() ? Component.empty() : templates.render(line, context));
        for (int index = 0; index < presentation.blankLinesAfter(); index++) result.add(Component.empty());
        return List.copyOf(result);
    }

    public List<Component> renderLines(List<String> lines, Map<String, Component> values) {
        ArrayList<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(line.isEmpty() ? Component.empty() : templates.render(line, values));
        return List.copyOf(result);
    }
}
