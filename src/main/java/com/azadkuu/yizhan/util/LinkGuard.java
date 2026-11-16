package com.azadkuu.yizhan.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 链接白名单 + 邮件组件净化。
 *
 * <p>两个职责：
 * <ul>
 *   <li>只有白名单域名的 http(s) 链接才允许做成可点击；</li>
 *   <li>投递物品前把组件里除「白名单 open_url」以外的 click 动作全部剥掉，
 *       避免有人寄一本写着 run_command 的书让收件人点。</li>
 * </ul>
 */
public final class LinkGuard {

    private static final Pattern URL = Pattern.compile("https?://[\\x21-\\x7E]+");
    private static final Pattern TRAILING = Pattern.compile("[),.;:!?'\"\\]]+$");

    private final List<String> domains;

    public LinkGuard(List<String> domains) {
        List<String> cleaned = new ArrayList<>();
        if (domains != null) {
            for (String domain : domains) {
                if (domain == null) {
                    continue;
                }
                String d = domain.trim().toLowerCase(Locale.ROOT);
                while (d.startsWith(".")) {
                    d = d.substring(1);
                }
                while (d.endsWith(".")) {
                    d = d.substring(0, d.length() - 1);
                }
                if (!d.isEmpty() && !cleaned.contains(d)) {
                    cleaned.add(d);
                }
            }
        }
        this.domains = cleaned;
    }

    public boolean isEnabled() {
        return !domains.isEmpty();
    }

    public List<String> domains() {
        return domains;
    }

    /** 从一段文字里取第一个白名单链接；没有则返回 null。 */
    public String firstAllowedUrl(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String url = TRAILING.matcher(matcher.group()).replaceAll("");
            if (isAllowed(url)) {
                return url;
            }
        }
        return null;
    }

    public boolean isAllowed(String url) {
        return isAllowedHost(url, domains);
    }

    public static boolean isAllowedHost(String url, List<String> domains) {
        if (url == null || url.isEmpty() || domains == null || domains.isEmpty()) {
            return false;
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /** 白名单链接 → 可点组件；非白名单 → 纯文本（不可点）。 */
    public static Component linkText(String url, List<String> domains) {
        if (isAllowedHost(url, domains)) {
            return Component.text(url, Style.style()
                    .clickEvent(ClickEvent.openUrl(url))
                    .decoration(TextDecoration.UNDERLINED, true)
                    .build());
        }
        return Component.text(url);
    }

    /** 投递前的净化：只保留白名单 open_url。 */
    public ItemStack sanitize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        boolean changed = false;

        Component display = meta.displayName();
        if (display != null) {
            Component safe = sanitize(display);
            if (!safe.equals(display)) {
                meta.displayName(safe);
                changed = true;
            }
        }
        Component itemName = meta.itemName();
        if (itemName != null) {
            Component safe = sanitize(itemName);
            if (!safe.equals(itemName)) {
                meta.itemName(safe);
                changed = true;
            }
        }
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            List<Component> safeLore = new ArrayList<>(lore.size());
            boolean loreChanged = false;
            for (Component line : lore) {
                Component safe = sanitize(line);
                if (!safe.equals(line)) {
                    loreChanged = true;
                }
                safeLore.add(safe);
            }
            if (loreChanged) {
                meta.lore(safeLore);
                changed = true;
            }
        }
        if (meta instanceof BookMeta book) {
            List<String> pages = book.getPages();
            if (pages != null && !pages.isEmpty()) {
                List<String> safePages = new ArrayList<>(pages.size());
                boolean pageChanged = false;
                for (String page : pages) {
                    String safe = sanitizePage(page);
                    if (!safe.equals(page)) {
                        pageChanged = true;
                    }
                    safePages.add(safe);
                }
                if (pageChanged) {
                    book.setPages(safePages);
                    changed = true;
                }
            }
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return item;
    }

    private String sanitizePage(String page) {
        if (page == null || page.isEmpty() || !page.startsWith("{")) {
            return page;
        }
        try {
            Component parsed = GsonComponentSerializer.gson().deserialize(page);
            Component safe = sanitize(parsed);
            if (safe.equals(parsed)) {
                return page;
            }
            return GsonComponentSerializer.gson().serialize(safe);
        } catch (RuntimeException ex) {
            return page;
        }
    }

    /** 递归剥掉不安全的 click 事件。 */
    public Component sanitize(Component component) {
        if (component == null) {
            return null;
        }
        Style style = component.style();
        ClickEvent click = style.clickEvent();
        Style newStyle = style;
        if (click != null && !(click.action() == ClickEvent.Action.OPEN_URL && isAllowed(click.value()))) {
            newStyle = style.clickEvent(null);
        }
        List<Component> children = component.children();
        Component result = component.style(newStyle);
        if (children.isEmpty()) {
            return result;
        }
        List<Component> mapped = new ArrayList<>(children.size());
        for (Component child : children) {
            mapped.add(sanitize(child));
        }
        return result.children(mapped);
    }
}
