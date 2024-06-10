package org.by1337.bnms.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class UrlUtil {
    public  static String parsePage(String url0) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(url0);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");

            int code = connection.getResponseCode();

            if (code == 200) {
                try (InputStream inputStream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    return String.join("\n", reader.lines().collect(Collectors.toList()));
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        throw new IOException(url0);
    }
}
