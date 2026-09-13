package com.antondev.chats.event.discord;

import java.util.ArrayList;
import java.util.List;

/** Transport-neutral Discord embed model. Keeps Discord/JDA classes out of gameplay code. */
public record DiscordEventEmbed(
        String title,
        String description,
        int color,
        List<Field> fields,
        String footer,
        boolean timestamp,
        String thumbnailUrl) {

    public static final int COLOR_LIVE = 0x55B8FF;
    public static final int COLOR_SUCCESS = 0x57F287;
    public static final int COLOR_TIMEOUT = 0xFEE75C;
    public static final int COLOR_CANCELLED = 0x99AAB5;
    public static final int COLOR_ERROR = 0xED4245;

    public DiscordEventEmbed {
        title = limit(title, 256);
        description = limit(description, 4096);
        footer = limit(footer, 2048);
        thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl.strip();
        List<Field> copy = new ArrayList<>();
        if (fields != null) {
            for (Field field : fields) {
                if (field == null || copy.size() >= 25) break;
                copy.add(new Field(limit(field.name(), 256), limit(field.value(), 1024), field.inline()));
            }
        }
        fields = List.copyOf(copy);
    }

    public record Field(String name, String value, boolean inline) {
        public Field {
            name = limit(name, 256);
            value = limit(value, 1024);
        }
    }

    public static Field field(String name, String value, boolean inline) {
        return new Field(name, value, inline);
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        String text = value.strip();
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }
}
