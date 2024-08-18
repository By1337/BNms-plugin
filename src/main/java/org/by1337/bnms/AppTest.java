package org.by1337.bnms;

import com.google.common.base.Joiner;
import org.by1337.bnms.remap.JarInput;
import org.by1337.bnms.remap.LibLoader;
import org.by1337.bnms.remap.RuntimeLibLoader;
import org.by1337.bnms.remap.mapping.ClassMapping;
import org.by1337.bnms.remap.mapping.Mapping;
import org.by1337.bnms.util.CsrgUtil;
import org.by1337.bnms.util.FileUtil;
import org.by1337.bnms.util.ProGuardToCsrgMap;
import org.objectweb.asm.tree.ClassNode;

import javax.sql.rowset.spi.SyncResolver;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppTest {

    public static void main(String[] args) throws Exception {
        File home = new File("./ttest");
        if (!home.exists()) {
            home.mkdirs();
        }

        File bukkitCl = FileUtil.downloadFile("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.16.5-cl.csrg?at=f0a5ed1aeff8156ba4afa504e190c838dd1af50c", new File(home, "bukkit-1.16.5-cl.csrg"));
        File bukkitMembers = FileUtil.downloadFile("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.16.5-members.csrg?at=f0a5ed1aeff8156ba4afa504e190c838dd1af50c", new File(home, "bukkit-1.16.5-members.csrg"));

        File serverMappings = FileUtil.downloadFile("https://piston-data.mojang.com/v1/objects/41285beda6d251d190f2bf33beadd4fee187df7a/server.txt", new File(home, "server.txt"));
        File server = FileUtil.downloadFile("https://piston-data.mojang.com/v1/objects/0a269b5f2c5b93b1712d0f5dc43b6182b9ab254e/server.jar", new File(home, "server.jar"));

        File toObfClass = new File(home, "toObfClass.csrg");
        File toObfMembers = new File(home, "toObfMembers.csrg");

        File toMojangClass = new File(home, "toMojangClass.csrg");
        File toMojangMembers = new File(home, "toMojangMembers.csrg");
        if (
                !FileUtil.checkSum(toMojangMembers) ||
                !FileUtil.checkSum(toMojangClass) ||
                !FileUtil.checkSum(toObfMembers) ||
                !FileUtil.checkSum(toObfClass)
        ) {
            JarInput jarInput = new JarInput(server);
            List<LibLoader> libs = new ArrayList<>();
            libs.add(jarInput);
            libs.add(new RuntimeLibLoader());
            jarInput.buildClassHierarchy(libs);


            List<Mapping> result = ProGuardToCsrgMap.readProGuard(serverMappings, jarInput);

            saveMappings(result.stream().filter(m -> m instanceof ClassMapping).collect(Collectors.toList()), toObfClass);
            saveMappings(result.stream().filter(m -> !(m instanceof ClassMapping)).collect(Collectors.toList()), toObfMembers);

            result.forEach(Mapping::reverse);

            saveMappings(result.stream().filter(m -> m instanceof ClassMapping).collect(Collectors.toList()), toMojangClass);
            saveMappings(result.stream().filter(m -> !(m instanceof ClassMapping)).collect(Collectors.toList()), toMojangMembers);

            FileUtil.createSum(toMojangMembers);
            FileUtil.createSum(toMojangClass);
            FileUtil.createSum(toObfMembers);
            FileUtil.createSum(toObfClass);
        }

        List<Mapping> toMojangClassMappings = CsrgUtil.read(toMojangClass);
        List<Mapping> bukkitClMappings = CsrgUtil.read(bukkitCl);

        Map<String, String> spigotNameToPackage = new HashMap<>();

        for (Mapping mapping : bukkitClMappings) {
            if (mapping instanceof ClassMapping) {
                // com/mojang/math/Matrix3f
                String[] arr = splitToNameAndPackage(mapping.getNewName());

                String old = spigotNameToPackage.put(arr[0], arr[1]);
                if (old != null && !old.equals(arr[1])) {
                    throw new IllegalStateException("Conflict between " + old + "/" + arr[1] + " and " + arr[1] + "/" + arr[0]);
                }
            }
        }

        JarInput jarInput = new JarInput(new File(home, "paper.jar"));

        List<Mapping> undoRelocate = new ArrayList<>();

        String packaging = "v1_16_R3";

        for (ClassNode value : jarInput.getClasses().values()) {
            if (value.name.equals("net/minecraft/server/Main")) continue;
            if (value.name.startsWith("org/bukkit/craftbukkit/libs/")) {
                ClassMapping classMapping = new ClassMapping(
                        value.name,
                        value.name.substring("org/bukkit/craftbukkit/libs/".length())
                );
                undoRelocate.add(classMapping);
            } else if (value.name.startsWith("org/bukkit/craftbukkit/" + packaging + "/")) {
                ClassMapping classMapping = new ClassMapping(
                        value.name,
                        value.name.replace(("org/bukkit/craftbukkit/" + packaging + "/"), "org/bukkit/craftbukkit/")
                );
                undoRelocate.add(classMapping);
            } else if (value.name.startsWith("net/minecraft/server/" + packaging + "/")) {
                String[] arr = splitToNameAndPackage(value.name);
                String package_ = spigotNameToPackage.get(arr[0]);
                if (package_ == null && arr[0].contains("$")){
                    package_ = spigotNameToPackage.get(arr[0].split("\\$")[0]);
                }
                if (package_ != null) {
                    ClassMapping classMapping = new ClassMapping(
                            value.name,
                            package_ + "/" + arr[0]
                    );
                    undoRelocate.add(classMapping);
                } else {
                    ClassMapping classMapping = new ClassMapping(
                            value.name,
                            arr[0]
                    );
                    undoRelocate.add(classMapping);
                }
            }
        }

        saveMappings(undoRelocate, new File(home, "undoRelocate.csrg"));
    }

    private static String[] splitToNameAndPackage(String s) {
        String[] arr = s.split("/");
        String name = arr[arr.length - 1]; // Matrix3f
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length - 1; i++) {
            sb.append(arr[i]).append("/");
        }
        if (sb.length() != 0) {
            sb.setLength(sb.length() - 1);
        }
        String package_ = sb.toString();
        return new String[]{name, package_};
    }


    public static void saveMappings(List<Mapping> mappings, File to) throws IOException {
        List<String> strList = mappings.stream().map(Object::toString).collect(Collectors.toList());
        Files.write(to.toPath(), Joiner.on("\n").join(strList).getBytes(StandardCharsets.UTF_8));
    }
}
