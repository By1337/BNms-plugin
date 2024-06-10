package org.by1337.bnms.util;

import com.google.common.base.Joiner;
import org.by1337.bnms.remap.ClassHierarchy;
import org.by1337.bnms.remap.JarInput;
import org.by1337.bnms.remap.mapping.ClassMapping;
import org.by1337.bnms.remap.mapping.FieldMapping;
import org.by1337.bnms.remap.mapping.Mapping;
import org.by1337.bnms.remap.mapping.MethodMapping;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProGuardToCsrgMap {

    public static List<Mapping> readProGuard(File mappings, JarInput input) throws IOException {
        System.out.println("read pro guard mappings");
        List<Mapping> result = toCsrg(Files.readAllLines(mappings.toPath()).iterator());
        Set<Mapping> toAdd = generateMissingMappings(input, result);
        result.addAll(toAdd);
        return result;
    }

    public static Set<Mapping> generateMissingMappings(JarInput jarInput, List<Mapping> mappings) throws IOException {
        System.out.println("build class hierarchy");

        ClassHierarchy classHierarchy = jarInput.getHierarchy();

        Set<Mapping> toAdd = new HashSet<>();

        for (Mapping mapping : mappings) {
            if (mapping instanceof MethodMapping) {
                MethodMapping method = (MethodMapping) mapping;
                for (ClassNode classNode : classHierarchy.getHierarchy().getOrDefault(method.getOwner(), Collections.emptySet())) {
                    MethodMapping mapping1 = new MethodMapping(
                            method.getOldName(),
                            method.getNewName(),
                            classNode.name,
                            method.getDesc()
                    );

                    toAdd.add(mapping1);
                }
            } else if (mapping instanceof FieldMapping) {
                FieldMapping fieldMapping = (FieldMapping) mapping;
                for (ClassNode classNode : classHierarchy.getHierarchy().getOrDefault(fieldMapping.getOwner(), Collections.emptySet())) {
                    FieldMapping mapping1 = new FieldMapping(
                            fieldMapping.getOldName(),
                            fieldMapping.getNewName(),
                            classNode.name
                    );

                    toAdd.add(mapping1);
                }
            }
        }
        System.out.println("Generated " + toAdd.size() + " missing mappings!");
        return toAdd;
    }

    public static List<Mapping> toCsrg(Iterator<String> proGuardMapping) {
        List<Mapping> list = new ArrayList<>();
        ClassMapping lastClass = null;

        Pattern classPattern = Pattern.compile("^\\S+( -> )\\S+(:)$");
        Pattern fieldPattern = Pattern.compile("^[^:<>()\\s]+ [^:<>()\\s]+ -> [^:<>()\\s]+$");
        Pattern methodPattern = Pattern.compile("^[^<>()\\s]+\\s[^()\\s]+\\(?[^)\\s]+\\)\\s(->)\\s\\S+");

        while (proGuardMapping.hasNext()) {
            String line = proGuardMapping.next().trim();
            if (line.startsWith("#")) continue;
            if (classPattern.matcher(line).find()) {
                String[] args = line.split(" -> ");
                String obfName = args[1];
                obfName = obfName.substring(0, obfName.length() - 1);
                String mojangName = args[0];

                ClassMapping classMapping = new ClassMapping(
                        mojangName.replace(".", "/"),
                        obfName.replace(".", "/")
                );
                lastClass = classMapping;
                list.add(classMapping);
            } else if (fieldPattern.matcher(line).find()) {
                String[] args = line.replace(" -> ", " ").split(" ");
                String type = args[0];
                String mojangName = args[1];
                String obfName = args[2];
                FieldMapping fieldMapping = new FieldMapping(
                        mojangName,
                        obfName,
                        lastClass.getOldName()
                );
                list.add(fieldMapping);
            } else if (methodPattern.matcher(line).find()) {
                line = clearLineIndex(line);

                String[] args = line.replace(" -> ", " ").split(" ");
                String type = toJVMType(args[0]);
                String obfName = args[2];
                String descRaw = args[1];
                descRaw = descRaw.split("\\(")[1];
                descRaw = descRaw.substring(0, descRaw.length() - 1);
                String mojangName = args[1].split("\\(")[0];
                String desc = Joiner.on("").join(Arrays.stream(descRaw.split(",")).map(ProGuardToCsrgMap::toJVMType).collect(Collectors.toList()));
                desc = "(" + desc + ")" + type;
                MethodMapping mapping = new MethodMapping(
                        mojangName,
                        obfName,
                        lastClass.getOldName(),
                        desc
                );
                list.add(mapping);
            } else {
                throw new IllegalArgumentException("Неизвестный формат! " + line);
            }
        }

        return list;
    }

    private static String clearLineIndex(String s) {
        Pattern pattern = Pattern.compile("^\\d+(:)");
        String str = s;

        while (true) {
            Matcher m = pattern.matcher(str);
            if (m.find()) {
                str = str.replace(m.group(), "");
            } else {
                break;
            }
        }
        return str;
    }

    private static String toJVMType(String type) {
        if (type.isEmpty()) return "";
        switch (type) {
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "boolean":
                return "Z";
            case "void":
                return "V";
            default:
                if (type.endsWith("[]")) {
                    return "[" + toJVMType(type.substring(0, type.length() - 2));
                }
                String clazzType = type.replace('.', '/');

                return "L" + clazzType + ";";
        }
    }
}
