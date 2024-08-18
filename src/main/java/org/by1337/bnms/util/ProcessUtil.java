package org.by1337.bnms.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class ProcessUtil {
    public static void executeCommand(File directory, String[] command) {
        executeCommand(directory, command, () -> {});
    }
    public static void executeCommand(File directory, String[] command, Runnable beforeStart) {
        beforeStart.run();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (SharedConstants.DEBUG){
                    SharedConstants.LOGGER.info(line);
                }
            }
            int exitCode = process.waitFor();

        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
    public static String getJavaHome(){
        Path bin = Paths.get(System.getProperty("java.home"), "bin");
        Path javaPath = bin.resolve("java");
        if (!Files.exists(javaPath)) {
            javaPath = bin.resolve("java.exe");
        }
        String javaHome;
        if (Files.exists(javaPath)) {
            javaHome = javaPath.toAbsolutePath().toString();
        } else {
            javaHome = "java";
        }
        return javaHome;
    }
}
