package org.by1337.bnms.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.by1337.bnms.remap.JarInput;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class PaperUtil {
    public void extractServerJar(File server, File workDir, Log log, File extractTo) throws MojoExecutionException, MojoFailureException, IOException {
        String javaHome = ProcessUtil.getJavaHome();
        ProcessBuilder processBuilder = new ProcessBuilder(javaHome, "-jar", server.getName());
        processBuilder.directory(workDir);

        Process process;
        int exitCode;
        try {
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }
            exitCode = process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to execute paperclip jar.", e);
        }
        if (exitCode != 0) {
            try {
                BufferedReader buf = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = buf.readLine()) != null) {
                    log.info("[paperclip] " + line);
                    if (line.contains("Java")) {
                        // Probably incorrect Java version. Instead of printing the message
                        // in paperclip which encourages downloading a new java version,
                        // which probably won't help in this case, we show a possible fix.
                        //
                        // The following regex might not be adapted for all paperclip versions,
                        // but an outdated java version is only really an issue on 1.17+
                        Matcher matcher = Pattern.compile("Java (?<number>\\d+)").matcher(line);
                        if (matcher.find()) {
                            String number = matcher.group("number");
                            throw new MojoFailureException("Failed to extract the server jar due to an outdated Java version." +
                                                           "\nPaperclip failed due to an outdated Java version." +
                                                           "\n" +
                                                           "\nTry changing the project's java version by for example adding the following to your pom.xml." +
                                                           "\n" +
                                                           "\n<properties>" +
                                                           "\n  <maven.compiler.source>" + number + "</maven.compiler.source>" +
                                                           "\n  <maven.compiler.target>" + number + "</maven.compiler.target>" +
                                                           "\n</properties>" +
                                                           "\n" +
                                                           "\nOr make sure the project is running Java " + number + " by going to Project Structure." +
                                                           "\n" +
                                                           "\nThis is only required for bnms:init, the java version can be downgraded again afterwards if desired."
                            );
                        } else {
                            throw new MojoExecutionException("Paperclip failed due to an outdated Java version." +
                                                             "\nPaperclip failed due to an outdated Java version." +
                                                             "\nSee the maven log for a more detailed error output." +
                                                             "\nPaperclip error log: " + line);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to fetch detailed error information from paperclip.", e);
            }
            throw new MojoExecutionException("Paperclip exited with exit code: " + exitCode);
        }


        JarFile jarFile = new JarFile(server);
        JarEntry entry = jarFile.getJarEntry("patch.properties");
        String version = null;
        boolean legacy = false;
        if (entry != null) {
            // legacy 1.14.4 -> 1.17.1
            String data = readEntryAsString(jarFile, entry);
            for (String string : data.split("\n")) {
                if (string.startsWith("version=")) {
                    version = string.replace("version=", "");
                    version = version.replaceAll("[^0-9.]", "");
                    break;
                }
            }
            legacy = true;
        } else if ((entry = jarFile.getJarEntry("version.json")) != null) {
            String data = readEntryAsString(jarFile, entry);
            JsonObject jsonObject = new Gson().fromJson(data, JsonObject.class);
            if (jsonObject.has("id")) {
                version = jsonObject.getAsJsonPrimitive("id").getAsString();
            } else {
                version = jsonObject.getAsJsonPrimitive("name").getAsString();
            }
        } else {
            jarFile.close();
            throw new IllegalStateException("Unknown version info!");
        }
        jarFile.close();
       // System.out.println(version);

        if (version == null) {
            throw new IllegalStateException("Failed to detect version!");
        }

        Files.deleteIfExists(workDir.toPath().resolve("cache").resolve("mojang_" + version + ".jar"));
        Files.deleteIfExists(workDir.toPath().resolve("logs").resolve("latest.log"));
        new File(workDir, "logs").delete();
        new File(workDir, "eula.txt").delete();
        new File(workDir, "server.properties").delete();
        if (legacy) {
            Files.move(workDir.toPath().resolve("cache").resolve("patched_" + version + ".jar"), extractTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            new File(workDir, "cache").delete();
        } else {
            new File(workDir, "plugins").delete();
            Files.move(workDir.toPath().resolve("versions").resolve(version).resolve(String.format("paper-%s.jar", version)), extractTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(workDir.toPath().resolve("versions").resolve(version));
            Files.deleteIfExists(workDir.toPath().resolve("versions"));
            Files.deleteIfExists(workDir.toPath().resolve("cache"));
            File lib = new File(workDir, "libraries");
            if (lib.exists()) {
                log.info("shading...");
                List<File> libsToShade = FileUtil.collectAllFiles(lib, f -> f.getName().endsWith(".jar"));
                shadeJars(libsToShade, extractTo, log);
            }
            FileUtil.deleteDirectory(lib);
        }
    }

    public static String readEntryAsString(JarFile jarFile, JarEntry entry) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();

        try (InputStream inputStream = jarFile.getInputStream(entry);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append(System.lineSeparator());
            }
        }

        return stringBuilder.toString();
    }

    public static void shadeJars(List<File> jarsToShade0, File shadeTo, Log log) throws IOException {
        File outputJar = new File(shadeTo + UUID.randomUUID().toString());
        Map<String, byte[]> entries = new HashMap<>();
        List<File> jarsToShade = new ArrayList<>(jarsToShade0);
        jarsToShade.add(shadeTo);
        for (File jarFile : jarsToShade) {
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entriesEnum = jar.entries();
                while (entriesEnum.hasMoreElements()) {
                    JarEntry entry = entriesEnum.nextElement();
                    if (!entry.isDirectory()) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] content = readAllBytes(is);
                            String shadedName = entry.getName();
                            if (entries.put(shadedName, content) != null) {
                                if (shadedName.endsWith(".class"))
                                    log.warn("duplicate " + shadedName);
                            }
                        }
                    }
                }
            }
        }

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar.toPath()))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                try {
                    jos.putNextEntry(jarEntry);
                    jos.write(entry.getValue());
                    jos.closeEntry();
                } catch (ZipException e) {
                    if (e.getMessage().contains("duplicate")) {
                        log.warn(e.getMessage());
                    } else {
                        throw e;
                    }
                }

            }
        }
        Files.move(outputJar.toPath(), shadeTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }


    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    public static String getPackaging(File patched) throws IOException {
        Pattern pattern = Pattern.compile("org/bukkit/craftbukkit/(?<package>v\\d+_\\d+_R\\d+)/");
        try (JarFile jar = new JarFile(patched)) {
            Enumeration<JarEntry> iterator = jar.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                Matcher matcher = pattern.matcher(entry.getName());
                if (matcher.find()) {
                    return matcher.group("package");
                }
            }
        }
        return null;
    }

}
