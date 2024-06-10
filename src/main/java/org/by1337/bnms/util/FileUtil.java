package org.by1337.bnms.util;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class FileUtil {
    public static File downloadFile(String fileURL, File saveFilePath) throws Exception {
        System.out.println("download " + saveFilePath.getName());
        if (saveFilePath.exists()) {
            if (checkSum(saveFilePath)) {
                System.out.println("skipped " + saveFilePath.getName());
                return saveFilePath;
            } else {
                System.out.println("re-downloading " + saveFilePath.getName());
            }
        }
        URL url = new URL(fileURL);
        URLConnection connection = url.openConnection();
        InputStream inputStream = connection.getInputStream();

        FileOutputStream outputStream = new FileOutputStream(saveFilePath);

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.close();

        createSum(saveFilePath);
        return saveFilePath;
    }

    public static void createSum(File file) throws IOException, NoSuchAlgorithmException {
        if (!file.exists()) throw new FileNotFoundException();
        File sum = new File(file.getPath() + ".sha1");
        Files.write(sum.toPath(), getSHA1Checksum(file.getPath()).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean checkSum(File file) throws IOException, NoSuchAlgorithmException {
        if (file.exists()) {
            File sum = new File(file.getPath() + ".sha1");
            if (sum.exists()) {
                byte[] arr = Files.readAllBytes(sum.toPath());
                return Arrays.equals(getSHA1Checksum(file.getPath()).getBytes(StandardCharsets.UTF_8), arr);
            }
        }
        return false;
    }


    public static String getSHA1Checksum(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] byteArray = new byte[1024];
            int bytesCount;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    public static List<File> collectAllFiles(File folder, Predicate<File> filter) {
        List<File> files = new ArrayList<>();
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                files.addAll(collectAllFiles(file, filter));
            } else {
                if (filter.test(file))
                    files.add(file);
            }
        }
        return files;
    }

    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteDirectory(f);
                f.delete();
            }
        } else {
            file.delete();
        }
    }

}
