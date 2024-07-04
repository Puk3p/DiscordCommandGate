package org.example;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class Config {
    private String discordToken;

    public void load() {
        Yaml yaml = new Yaml();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.yml");
                return;
            }
            Map<String, Object> data = yaml.load(input);
            if (data != null && data.containsKey("discord")) {
                Map<String, String> discordConfig = (Map<String, String>) data.get("discord");
                discordToken = discordConfig.get("token");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getDiscordToken() {
        return discordToken;
    }
}
