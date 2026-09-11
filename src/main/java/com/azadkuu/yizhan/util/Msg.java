package com.azadkuu.yizhan.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {
    }

    public static Component component(String raw) {
        return LEGACY.deserialize(raw == null ? "" : raw);
    }

    public static String color(String raw) {
        return LEGACY.serialize(component(raw));
    }

    public static void send(CommandSender sender, String prefix, String raw) {
        sender.sendMessage(component(prefix + raw));
    }

    public static void send(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }

    public static Component join(Component... parts) {
        Component result = Component.empty();
        for (Component part : parts) {
            if (part != null) {
                result = result.append(part);
            }
        }
        return result;
    }
}
