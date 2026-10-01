package fr.lkdm.homelink.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every status and area message the player can see exists in English and French. */
class TranslationCompletenessTest {
    private static final Path LANG = Path.of(System.getProperty("homelink_quarry.projectDir", "."))
            .resolve("src/main/resources/assets/homelink_quarry/lang");

    private static JsonObject load(String language) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test void languagesShareTheSameKeys() throws IOException {
        assertEquals(load("en_us").keySet(), load("fr_fr").keySet());
    }

    @Test void everyStatusAndAreaResultIsTranslated() throws IOException {
        List<String> keys = new ArrayList<>();
        for (QuarryStatus status : QuarryStatus.values()) keys.add(status.key());
        for (AreaCheck check : AreaCheck.values()) keys.add(check.key());
        for (String language : List.of("en_us", "fr_fr")) {
            JsonObject lang = load(language);
            for (String key : keys) assertTrue(lang.has(key), language + " is missing " + key);
        }
    }
}
