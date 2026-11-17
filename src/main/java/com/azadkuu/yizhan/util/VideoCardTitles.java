package com.azadkuu.yizhan.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 尽力抓一个 B 站标题，抓不到就返回 null，由调用方退回默认标题。 */
public final class VideoCardTitles {

    private static final Pattern BV = Pattern.compile("(BV[0-9A-Za-z]{10})");
    private static final Pattern PAGE_TITLE = Pattern.compile("<title>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern API_TITLE = Pattern.compile("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private VideoCardTitles() {
    }

    public static String fetch(String url) {
        try {
            HttpResponse<String> response = get(url);
            if (response == null) {
                return null;
            }
            String body = response.body();
            Matcher page = PAGE_TITLE.matcher(body == null ? "" : body);
            if (page.find()) {
                String title = clean(page.group(1));
                if (!title.isBlank()) {
                    return title;
                }
            }
            Matcher bv = BV.matcher(String.valueOf(response.uri()));
            if (!bv.find()) {
                bv = BV.matcher(body == null ? "" : body);
            }
            if (bv.find()) {
                HttpResponse<String> api = get("https://api.bilibili.com/x/web-interface/view?bvid=" + bv.group(1));
                if (api != null && api.body() != null) {
                    Matcher m = API_TITLE.matcher(api.body());
                    if (m.find()) {
                        String title = clean(m.group(1));
                        if (!title.isBlank()) {
                            return title;
                        }
                    }
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static HttpResponse<String> get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return response;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\\/", "/").replace("\\\"", "\"");
        text = text.replace("_哔哩哔哩_bilibili", "").replace("_哔哩哔哩", "");
        return text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim();
    }
}
