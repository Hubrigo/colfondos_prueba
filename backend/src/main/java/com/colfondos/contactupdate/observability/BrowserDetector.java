package com.colfondos.contactupdate.observability;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Familia y versión mayor del navegador para los indicadores por navegador (doc. 11). */
public final class BrowserDetector {

    private record Rule(Pattern pattern, String family) {
    }

    // El orden importa: el User-Agent de Edge incluye "Chrome" y el de Chrome incluye "Safari".
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("Edg/(\\d+)"), "Edge"),
            new Rule(Pattern.compile("(?:Chrome|CriOS)/(\\d+)"), "Chrome"),
            new Rule(Pattern.compile("(?:Firefox|FxiOS)/(\\d+)"), "Firefox"),
            new Rule(Pattern.compile("Version/(\\d+).*Safari/"), "Safari"));

    private BrowserDetector() {
    }

    public static String detect(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(userAgent);
            if (m.find()) {
                return rule.family() + " " + m.group(1);
            }
        }
        return "Other";
    }
}
