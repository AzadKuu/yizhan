package com.azadkuu.yizhan.service;

import com.azadkuu.yizhan.config.PluginConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemFilter {

    private static final Pattern DATA_ENTRY = Pattern.compile(
            "\"([A-Za-z0-9_.\\-]+:[A-Za-z0-9_./\\-]+)\"\\s*:\\s*(?:\"([^\"]*)\"|(-?[0-9]+(?:\\.[0-9]+)?[bBsSlLfFdD]?|true|false))");

    private final PluginConfig config;
    private final Consumer<String> debugLog;

    public ItemFilter(PluginConfig config, Consumer<String> debugLog) {
        this.config = config;
        this.debugLog = debugLog;
    }

    @SuppressWarnings("deprecation")
    public boolean isBlocked(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        Map<String, String> customData = parseCustomData(meta);

        PluginConfig.AllowedItem allowed = matchAllowed(meta, customData);
        if (allowed != null) {
            if (config.isDebug() && debugLog != null) {
                debugLog.accept("检查 " + item.getType().name()
                        + " => 放行(白名单 " + allowed.key() + "=" + allowed.value() + ")");
            }
            return false;
        }

        Set<String> namespaces = config.getBlockedNamespaces();
        Set<String> keys = config.getBlockedKeys();
        Set<String> materials = config.getBlockedMaterials();
        Set<String> found = new LinkedHashSet<>();
        String reason = null;
        if (!materials.isEmpty()
                && materials.contains(item.getType().name().toLowerCase(Locale.ROOT))) {
            reason = "命中原版材质=" + item.getType().name();
        }
        if (reason == null && (!namespaces.isEmpty() || !keys.isEmpty())) {
            for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                found.add(key.toString());
                if (reason == null && namespaces.contains(key.getNamespace().toLowerCase(Locale.ROOT))) {
                    reason = "命中命名空间=" + key.getNamespace() + " (PDC)";
                }
                if (reason == null && keys.contains(key.toString().toLowerCase(Locale.ROOT))) {
                    reason = "命中完整key=" + key + " (PDC)";
                }
            }
            for (Map.Entry<String, String> entry : customData.entrySet()) {
                String full = entry.getKey();
                found.add(full);
                int idx = full.indexOf(':');
                String ns = idx > 0 ? full.substring(0, idx) : "minecraft";
                if (reason == null && namespaces.contains(ns.toLowerCase(Locale.ROOT))) {
                    reason = "命中命名空间=" + ns + " (custom_data)";
                }
                if (reason == null && keys.contains(full.toLowerCase(Locale.ROOT))) {
                    reason = "命中完整key=" + full + " (custom_data)";
                }
            }
        }
        if (reason == null && config.isBlockCustomModelData() && meta.hasCustomModelData()) {
            reason = "命中custom-model-data=" + meta.getCustomModelData();
        }
        if (config.isDebug() && debugLog != null) {
            debugLog.accept("检查 " + item.getType().name()
                    + " 键=" + (found.isEmpty() ? "(无)" : found.toString())
                    + " CMD=" + (meta.hasCustomModelData() ? meta.getCustomModelData() : "-")
                    + " => " + (reason == null ? "放行" : "拦截(" + reason + ")"));
        }
        return reason != null;
    }

    @SuppressWarnings("deprecation")
    private PluginConfig.AllowedItem matchAllowed(ItemMeta meta, Map<String, String> customData) {
        if (config.getAllowedItems().isEmpty()) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (PluginConfig.AllowedItem allowed : config.getAllowedItems()) {
            for (NamespacedKey key : pdc.getKeys()) {
                if (key.toString().equalsIgnoreCase(allowed.key())) {
                    String value = pdc.get(key, PersistentDataType.STRING);
                    if (value != null && value.equals(allowed.value())) {
                        return allowed;
                    }
                }
            }
            for (Map.Entry<String, String> entry : customData.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(allowed.key())
                        && entry.getValue().equals(allowed.value())) {
                    return allowed;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    public Map<String, String> parseCustomData(ItemMeta meta) {
        Map<String, String> out = new LinkedHashMap<>();
        if (meta == null) {
            return out;
        }
        String body = extractCustomDataBody(meta.getAsString());
        if (body == null) {
            return out;
        }
        Matcher matcher = DATA_ENTRY.matcher(body);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            out.putIfAbsent(key, value == null ? "" : value);
        }
        return out;
    }

    private String extractCustomDataBody(String raw) {
        if (raw == null) {
            return null;
        }
        int idx = raw.indexOf("custom_data");
        if (idx < 0) {
            return null;
        }
        int start = raw.indexOf('{', idx);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' && (i == 0 || raw.charAt(i - 1) != '\\')) {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start + 1, i);
                }
            }
        }
        return null;
    }

    public List<String> listPersistentKeys(ItemStack item) {
        List<String> out = new ArrayList<>();
        if (item == null || item.getType().isAir()) {
            return out;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return out;
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            out.add(key.toString());
        }
        return out;
    }

    public String describe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "empty";
        }
        return item.getType().name();
    }

    public Component nameComponent(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Component.text("空");
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component display = meta.displayName();
            if (display != null) {
                return display;
            }
        }
        return Component.translatable(item.getType().translationKey());
    }
}
