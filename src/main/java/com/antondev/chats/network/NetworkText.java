package com.antondev.chats.network;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Color/decorations only; literal player section codes stay literal after a wire round-trip. */
public final class NetworkText {
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.builder().hexColors().build();
    private NetworkText() {}
    public static String encode(Component component) {
        Component escaped=component.replaceText(TextReplacementConfig.builder().match("[§\ue000]")
                .replacement((match,builder)->Component.text(match.group().equals("§")?"\ue0001":"\ue0000")).build());
        return LEGACY.serialize(escaped);
    }
    public static Component decode(String text) {
        return LEGACY.deserialize(text).replaceText(TextReplacementConfig.builder().match("\ue000[01]")
                .replacement((match,builder)->Component.text(match.group().charAt(1)=='1'?"§":"\ue000")).build());
    }
}
