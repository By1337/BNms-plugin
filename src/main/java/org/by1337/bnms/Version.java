package org.by1337.bnms;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.by1337.bnms.util.FileUtil;
import org.by1337.bnms.util.UrlUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class Version {
    private static final Log LOGGER = new SystemStreamLog();
    public static final String PAPER_SERVER = "https://api.papermc.io/v2/projects/paper/versions/{}/builds/{1}/downloads/paper-{}-{1}.jar";
    public static final String PAPER_BUILD_INFO = "https://api.papermc.io/v2/projects/paper/versions/";
    public static final String SPIGOT_VERSION_INFO = "https://hub.spigotmc.org/versions/";
    public static final String SPIGOT_CL_MAPPINGS = "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-{}-cl.csrg";
    public static final String SPIGOT_MEMBERS_MAPPINGS = "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-{}-members.csrg";
    private static final Map<String, Version> LOOKUP = new LinkedHashMap<>();
    private static String latestRelease;

    public static void initVersions(File cacheDir) throws IOException, NoSuchAlgorithmException {
        JsonObject versionList;
        Gson gson = new Gson();

        String json = UrlUtil.parsePage("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        versionList = gson.fromJson(json, JsonObject.class);

        boolean cahLoaded = loadCache(cacheDir);
        if (cahLoaded){
            return; // do not update //todo remove it when support for 1.16.5+ is available
        }

        latestRelease = versionList.getAsJsonObject("latest").getAsJsonPrimitive("release").getAsString();

        int index = 0;

        int x = 0;
        for (JsonElement element : versionList.getAsJsonArray("versions")) {
            JsonObject data = element.getAsJsonObject();
            if (!data.getAsJsonPrimitive("type").getAsString().equals("release")) continue;
            String version = data.getAsJsonPrimitive("id").getAsString();
            String url = data.getAsJsonPrimitive("url").getAsString();

            JsonObject versionInfo = gson.fromJson(UrlUtil.parsePage(url), JsonObject.class);

            JsonObject downloads = versionInfo.getAsJsonObject("downloads");
            String serverUrl = downloads.getAsJsonObject("server").getAsJsonPrimitive("url").getAsString();
            String serverMappingsUrl = downloads.getAsJsonObject("server_mappings").getAsJsonPrimitive("url").getAsString();

            Version version1 = new Version(serverUrl, serverMappingsUrl, version, index);

            LOOKUP.put(version1.id, version1);

            if (version.equalsIgnoreCase("1.14.4")) {
                break;
            }
            if (cahLoaded && x >= 2) {
                break; // update only the latest versions
            }
            index++;
            x++;
        }

        x = 0;
        for (Version value : LOOKUP.values()) {
            try {
                JsonObject spigotData = gson.fromJson(UrlUtil.parsePage(SPIGOT_VERSION_INFO + value.id + ".json"), JsonObject.class);
                value.spigotRefs = spigotData.getAsJsonObject("refs");
                String ref = value.spigotRefs.getAsJsonPrimitive("BuildData").getAsString();
                value.bukkitClUrl = SPIGOT_CL_MAPPINGS.replace("{}", value.id) + "?at=" + ref;
                value.bukkitMembersUrl = SPIGOT_MEMBERS_MAPPINGS.replace("{}", value.id) + "?at=" + ref;

                JsonObject paperData = gson.fromJson(UrlUtil.parsePage(PAPER_BUILD_INFO + value.id), JsonObject.class);

                int lastBuild = paperData.getAsJsonArray("builds").asList().stream()
                        .map(JsonElement::getAsInt)
                        .reduce(Integer::max)
                        .get();

                value.paperServer = PAPER_SERVER.replace("{}", value.id).replace("{1}", String.valueOf(lastBuild));

                if (cahLoaded && x >= 2) {
                    break; // update only the latest versions
                }
                x++;

            } catch (Throwable t) {
                LOGGER.warn("Failed to get spigot info! " + t.getMessage());
            }
        }
        createCache(cacheDir);
    }

    private static boolean loadCache(File dir) throws IOException, NoSuchAlgorithmException {
        // Map<String, Version> LOOKUP
        File file = new File(dir, "version.json");
        if (FileUtil.checkSum(file)) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Version>>() {
            }.getType();
            Map<String, Version> versionMap;
            try (FileReader fileReader = new FileReader(file)) {
                versionMap = gson.fromJson(fileReader, mapType);
            }
            LOOKUP.putAll(versionMap);
            return true;
        }
        return false;
    }

    private static void createCache(File dir) throws IOException, NoSuchAlgorithmException {
        File file = new File(dir, "version.json");
        Gson gson = new Gson();
        Files.write(file.toPath(), gson.toJson(LOOKUP).getBytes(StandardCharsets.UTF_8));
        FileUtil.createSum(file);
    }

    private final String serverUrl;
    private final String serverMappingsUrl;
    private final String id;
    private JsonObject spigotRefs;
    private String bukkitClUrl;
    private String bukkitMembersUrl;
    private String paperServer;
    private final int index;

    public Version(String serverUrl, String serverMappingsUrl, String id, int index) {
        this.serverUrl = serverUrl;
        this.serverMappingsUrl = serverMappingsUrl;
        this.id = id;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public static void load(File cache) throws IOException, NoSuchAlgorithmException {
        if (!cache.exists()) cache.mkdirs();
        initVersions(cache);
    }
    public static Version getByName(String name){
        for (Version value : LOOKUP.values()) {
            if (value.id.endsWith(name)) return value;
        }
        return null;
    }
    public static String getLatestRelease() {
        return latestRelease;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getServerMappingsUrl() {
        return serverMappingsUrl;
    }

    public String getId() {
        return id;
    }

    public JsonObject getSpigotRefs() {
        return spigotRefs;
    }

    public String getBukkitClUrl() {
        return bukkitClUrl;
    }

    public String getBukkitMembersUrl() {
        return bukkitMembersUrl;
    }

    public String getPaperclip() {
        return paperServer;
    }

    @Override
    public String toString() {
        return "Version{" +
               "serverUrl='" + serverUrl + '\'' +
               ", serverMappingsUrl='" + serverMappingsUrl + '\'' +
               ", id='" + id + '\'' +
               ", spigotRefs=" + spigotRefs +
               ", bukkitClUrl='" + bukkitClUrl + '\'' +
               ", bukkitMembersUrl='" + bukkitMembersUrl + '\'' +
               ", paperServer='" + paperServer + '\'' +
               '}';
    }
}
