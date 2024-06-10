package org.by1337.bnms.util;

import org.by1337.bnms.remap.mapping.ClassMapping;
import org.by1337.bnms.remap.mapping.FieldMapping;
import org.by1337.bnms.remap.mapping.Mapping;
import org.by1337.bnms.remap.mapping.MethodMapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CsrgUtil {
    public static List<Mapping> read(File file) throws IOException {
        List<String> mappingRaw = Files.readAllLines(file.toPath());
        List<Mapping> mappings = new ArrayList<>();
        for (String string : mappingRaw) {
            if (string.startsWith("#")) continue;
            String[] args = string.split(" ");
            if (args.length == 2) {
                mappings.add(new ClassMapping(args[0], args[1]));
            } else if (args.length == 3) {
                mappings.add(new FieldMapping(args[1], args[2], args[0]));
            } else if (args.length == 4) {
                mappings.add(new MethodMapping(
                        args[1],
                        args[3],
                        args[0],
                        args[2]
                ));
            } else {
                throw new UnsupportedOperationException(string);
            }
        }
        return mappings;
    }
}
