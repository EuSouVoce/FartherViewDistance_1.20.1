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
    private final JsonObject defaultMap = loadLang(Locale.ENGLISH);

    /**
     * @param sender 執行人
     * @param key    條目鑰匙
     * @return 語言條目
     */
    @SuppressWarnings("deprecation")
    public String get(CommandSender sender, String key) {
        if (sender instanceof Player) {
            try {
                // 1.16 以上
                return get(((Player) sender).locale(), key);
            } catch (NoSuchMethodError noSuchMethodError) {
                return get(parseLocale(((Player) sender).getLocale()), key);
            }
        } else {
            return get(Locale.ENGLISH, key);
        }
    }

    private static Locale parseLocale(String string) {
        String[] segments = string.split("_", 3);
        int length = segments.length;
        switch (length) {
            case 1:
                return new Locale(string);
            case 2:
                return new Locale(segments[0], segments[1]);
            case 3:
                return new Locale(segments[0], segments[1], segments[2]);
            default:
                return null;
        }
    }

    /**
     * @param locale 語言類型
     * @param key    條目鑰匙
     * @return 語言條目
     */
    public String get(Locale locale, String key) {
        JsonObject lang = fileMap.computeIfAbsent(locale, v -> loadLang(locale));
        JsonElement element = lang.get(key);
        if (element != null && !element.isJsonNull()) {
            return element.getAsString();
        } else {
            return defaultMap.get(key).getAsString();
        }
    }

    /**
     * @param locale 語言類型
     * @return 讀取語言文件
     */
    private JsonObject loadLang(Locale locale) {
        URL url = getClass().getClassLoader()
                .getResource("lang/" + locale.toString().toLowerCase(Locale.ROOT) + ".json");
        if (url == null)
            return new JsonObject();
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(true);
            return new Gson().fromJson(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8),
                    JsonObject.class);
        } catch (IOException exception) {
            return new JsonObject();
        }
    }
}
