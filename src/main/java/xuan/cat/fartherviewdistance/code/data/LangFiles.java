package xuan.cat.fartherviewdistance.code.data;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 語言文件 */
public final class LangFiles {

    /** 全部語言文件 */
    private final Map<Locale, JsonObject> fileMap = new ConcurrentHashMap<>();
    /** 預設語言文件 */
    private final JsonObject defaultMap = this.loadLang(Locale.ENGLISH);

    /**
     * @param sender 執行人
     * @param key    條目鑰匙
     * @return 語言條目
     */
    @SuppressWarnings("deprecation")
    public String get(final CommandSender sender, final String key) {
        if (sender instanceof Player) {
            try {
                // 1.16 以上
                return this.get(((Player) sender).locale(), key);
            } catch (final NoSuchMethodError noSuchMethodError) {
                return this.get(LangFiles.parseLocale(((Player) sender).getLocale()), key);
            }
        } else {
            return this.get(Locale.ENGLISH, key);
        }
    }

    @SuppressWarnings("deprecation")
    private static Locale parseLocale(final String string) {
        final String[] segments = string.split("_", 3);
        final int length = segments.length;
        return switch (length) {
            case 1 -> new Locale(string);
            case 2 -> new Locale(segments[0], segments[1]);
            case 3 -> new Locale(segments[0], segments[1], segments[2]);
            default -> null;
        };
    }

    /**
     * @param locale 語言類型
     * @param key    條目鑰匙
     * @return 語言條目
     */
    public String get(final Locale locale, final String key) {
        final JsonObject lang = this.fileMap.computeIfAbsent(locale, v -> this.loadLang(locale));
        final JsonElement element = lang.get(key);
        if (element != null && !element.isJsonNull()) {
            return element.getAsString();
        } else {
            return this.defaultMap.get(key).getAsString();
        }
    }

    /**
     * @param locale 語言類型
     * @return 讀取語言文件
     */
    private JsonObject loadLang(final Locale locale) {
        final URL url = this.getClass().getClassLoader()
                .getResource("lang/" + locale.toString().toLowerCase(Locale.ROOT) + ".json");
        if (url == null)
            return new JsonObject();
        try {
            final URLConnection connection = url.openConnection();
            connection.setUseCaches(true);
            return new Gson().fromJson(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8),
                    JsonObject.class);
        } catch (final IOException exception) {
            return new JsonObject();
        }
    }
}
