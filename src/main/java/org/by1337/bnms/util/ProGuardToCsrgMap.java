package org.by1337.bnms.util;

import com.google.common.base.Joiner;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.by1337.bnms.remap.ClassHierarchy;
import org.by1337.bnms.remap.JarInput;
import org.by1337.bnms.remap.mapping.ClassMapping;
import org.by1337.bnms.remap.mapping.FieldMapping;
import org.by1337.bnms.remap.mapping.Mapping;
import org.by1337.bnms.remap.mapping.MethodMapping;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProGuardToCsrgMap {
    private static final Log LOGGER = new SystemStreamLog();
    private static final Set<String> seenUnknownClass = new HashSet<>();

    public static List<Mapping> readProGuard(File mappings, JarInput input) throws IOException {
        LOGGER.info("read pro guard mappings");
        List<Mapping> result = toCsrg(Files.readAllLines(mappings.toPath()).iterator());
        Set<Mapping> toAdd = generateMissingMappings(input, result);
        result.addAll(toAdd);
        return result;
    }

    private static String applyMappings(Type type, Map<String, String> mapping) {
        StringBuilder descBuilder = new StringBuilder("(");
        for (Type argumentType : type.getArgumentTypes()) {
            String d = argumentType.getDescriptor();
            descBuilder.append(applyMappingsToType(d, mapping));
        }
        descBuilder.append(")").append(applyMappingsToType(type.getReturnType().getDescriptor(), mapping));
        return descBuilder.toString();
    }

    private static String applyMappingsToType(String type, Map<String, String> mapping) {
        if (type.startsWith("[")) {
            return "[" + applyMappingsToType(type.substring(1), mapping);
        }
        if (type.startsWith("L") && type.endsWith(";")) {
            String cleanName = type.substring(1, type.length() - 1);
            return "L" + mapping.getOrDefault(cleanName, cleanName) + ";";
        }
        return type;
    }


    public static Set<Mapping> generateMissingMappings(JarInput jarInput, List<Mapping> mappings) throws IOException {
        LOGGER.info("build class hierarchy");

        ClassHierarchy classHierarchy = jarInput.getHierarchy();

        Set<Mapping> toAdd = new HashSet<>();

        Map<String, String> classMappingMojangToObf = new HashMap<>();
        Map<String, String> classMappingObfToMojang = new HashMap<>();
        for (Mapping mapping : mappings) {
            if (mapping instanceof ClassMapping) {
                ClassMapping classMapping1 = (ClassMapping) mapping;
                classMappingMojangToObf.put(classMapping1.getOldName(), classMapping1.getNewName());
                classMappingObfToMojang.put(classMapping1.getNewName(), classMapping1.getOldName());
            }
        }
        for (Mapping mapping : mappings) {
            if (mapping instanceof MethodMapping) {
                MethodMapping method = (MethodMapping) mapping;
                String obfName = classMappingMojangToObf.get(method.getOwner());
                if (obfName == null) {
                    if (seenUnknownClass.add(method.getOwner()))
                        LOGGER.warn("Unknown class " + method.getOwner());
                    continue;
                }
                ClassNode owner = jarInput.getClass(obfName);
                if (owner == null) {
                    if (seenUnknownClass.add(obfName))
                        LOGGER.warn("Missing class " + obfName);
                    continue;
                }

                String currentDesc = applyMappings(Type.getType(method.getDesc()), classMappingMojangToObf);
                MethodNode currentMethod = null;
                for (MethodNode methodNode : owner.methods) {
                    if (methodNode.name.equals(method.getNewName())) {
                        if (methodNode.desc.equals(currentDesc)) {
                            // System.out.println("find! " + method.getDesc() + " -> " + currentDesc);
                            currentMethod = methodNode;
                            break;
                        }

                    }
                }
                if (currentMethod == null) {
                    //  System.err.printf("Unknown method %s#%s%s as %s#%s%s \n", method.getOwner(), method.getOldName(), method.getDesc(), owner.name, method.getNewName(), currentDesc);
                    // in 1.16.5 mojang obfuscator removes unused methods
                    continue;
                }
                if (Modifier.isStatic(currentMethod.access) || Modifier.isPrivate(currentMethod.access)) {
                    continue; // skip static anf private methods
                }
                for (ClassNode classNode : classHierarchy.getHierarchy().getOrDefault(obfName, Collections.emptySet())) {
                    String cl = classMappingObfToMojang.getOrDefault(classNode.name, classNode.name);
                    MethodMapping mapping1 = new MethodMapping(
                            method.getOldName(),
                            method.getNewName(),
                            cl,
                            method.getDesc()
                    );

                    toAdd.add(mapping1);
                }
            } else if (mapping instanceof FieldMapping) {
                FieldMapping fieldMapping = (FieldMapping) mapping;
                String obfName = classMappingMojangToObf.get(fieldMapping.getOwner());
                if (obfName == null) {
                    if (seenUnknownClass.add(fieldMapping.getOwner()))
                        LOGGER.warn("Unknown class " + fieldMapping.getOwner());
                    continue;
                }
                ClassNode owner = jarInput.getClass(obfName);
                if (owner == null) {
                    if (seenUnknownClass.add(obfName))
                        LOGGER.warn("Missing class " + obfName);
                    continue;
                }
                FieldNode currentFieldNode = null;
                for (FieldNode field : owner.fields) {
                    if (field.name.equals(fieldMapping.getNewName())) {
                        currentFieldNode = field;
                        break;
                    }
                }
                if (currentFieldNode == null) {
                    break;
                }
                if (Modifier.isPrivate(currentFieldNode.access) || (Modifier.isStatic(currentFieldNode.access) && !Modifier.isInterface(owner.access))) {
                    continue;
                }
                for (ClassNode classNode : classHierarchy.getHierarchy().getOrDefault(obfName, Collections.emptySet())) {
                    String cl = classMappingObfToMojang.getOrDefault(classNode.name, classNode.name);
                    FieldMapping mapping1 = new FieldMapping(
                            fieldMapping.getOldName(),
                            fieldMapping.getNewName(),
                            cl
                    );

                    toAdd.add(mapping1);
                }
            }
        }
        LOGGER.info("Generated " + toAdd.size() + " missing mappings!");
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
                if (mojangName.equals(obfName)) continue;
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
                if (mojangName.equals(obfName)) continue;
                MethodMapping mapping = new MethodMapping(
                        mojangName,
                        obfName,
                        lastClass.getOldName(),
                        desc
                );
                list.add(mapping);
            } else {
                throw new IllegalArgumentException("Unknown format! " + line);
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
