package org.by1337.bnms.process;

import com.google.common.base.Joiner;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.by1337.bnms.Links;
import org.by1337.bnms.Version;
import org.by1337.bnms.remap.JarInput;
import org.by1337.bnms.remap.LibLoader;
import org.by1337.bnms.remap.RuntimeLibLoader;
import org.by1337.bnms.remap.mapping.ClassMapping;
import org.by1337.bnms.remap.mapping.Mapping;
import org.by1337.bnms.util.*;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LegacyProcessV2 {
    private final Log log;
    private final File home;
    private final Version version;
    private MojangMappings mojangMappings;

    public LegacyProcessV2(Log log, File home, Version version) {
        this.log = log;
        this.home = home;
        this.version = version;
    }

    public static void main(String[] args) throws Exception {
        File home = new File("./TEST");
        home.mkdirs();
        File versionCacheDir = new File(home, "versionCacheDir");
        versionCacheDir.mkdirs();
        File work = new File(home, "work");
        work.mkdirs();
        Version.load(versionCacheDir);

        Version version1 = Version.getByName("1.16.5");

        LegacyProcessV2 processV2 = new LegacyProcessV2(
                new SystemStreamLog(),
                work,
                version1
        );
        delete(new File(work, "toObfClass.csrg"));
        delete(new File(work, "toObfMembers.csrg"));
        delete(new File(work, "undoRelocate.csrg"));
        delete(new File(work, "unrelocatedPaper.jar"));
        delete(new File(work, "paper-remapped.jar"));
        delete(new File(work, "bukkit-1.16.5-cl.csrg.undo.csrg"));
        delete(new File(work, "bukkit-1.16.5-members.csrg.undo.csrg"));
        delete(new File(work, "paper-mojang-cl.jar"));
        delete(new File(work, "paper-obf.jar"));
        delete(new File(work, "paper-obf-cl.jar"));
        delete(new File(work, "toMojangClass.csrg"));
        delete(new File(work, "toMojangMembers.csrg"));
        delete(new File(work, "toMojangMembers.csrg.fixed.csrg"));
        for (File file : work.listFiles()) {
            if (file.getName().endsWith(".sha1")){
                File f = new File(work, file.getName().replace(".sha1", ""));
                if (!f.exists()) delete(file);
            }
        }

        processV2.getPaperRemapped();
       // processV2.getMojangMappings();
    }
    private static void delete(File file){
        if (file.exists()){
            file.delete();
        }
    }

    private File getPaperRemapped() throws Exception {
        File file = new File(home, "paper-remapped.jar");
        if (!FileUtil.checkSum(file)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            getSpecialSource().getName(),
                            "-i",
                            getPaperMojangClasses().getName(),
                            "-m",
                            getToMojangMembersFixed().getName(),
                            "-o",
                            file.getName()
                    }
            );
            FileUtil.createSum(file);
        }
        return file;
    }
    private File getPaperMojangClasses() throws Exception {
        File file = new File(home, "paper-mojang-cl.jar");
        if (!FileUtil.checkSum(file)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            getSpecialSource().getName(),
                            "-i",
                            getPaperObfCl().getName(),
                            "-m",
                            getMojangMappings().toMojangClass.getName(),
                            "-o",
                            file.getName()
                    }
            );
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getToMojangMembersFixed() throws Exception {
        File file = new File(home, "toMojangMembers.csrg.fixed.csrg");
        if (!FileUtil.checkSum(file)) {
            List<Mapping> mappings = CsrgUtil.read(getMojangMappings().toMojangMembers);
            mappings.removeIf(m -> m instanceof ClassMapping);
            for (Mapping mapping : mappings) {
                if (mapping.getOldName().equals(mapping.getNewName())) continue;
                mapping.setNewName(mapping.getNewName() + "_");
            }
            saveMappings(mappings, file);
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getPaperObf() throws Exception {
        // todo
        File file = new File(home, "paper-obf.jar");
        if (!FileUtil.checkSum(file)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            getSpecialSource().getName(),
                            "-i",
                            getPaperObfCl().getName(),
                            "-m",
                            getBukkitMembersUndo().getName(),
                            "-o",
                            file.getName()
                    }
            );
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getPaperObfCl() throws Exception {
        File file = new File(home, "paper-obf-cl.jar");
        if (!FileUtil.checkSum(file)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            getSpecialSource().getName(),
                            "-i",
                            getUnrelocatedPaper().getName(),
                            "-m",
                            getBukkitClUndo().getName(),
                            "-o",
                            file.getName()
                    }
            );
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getBukkitClUndo() throws Exception {
        File file = new File(home, "bukkit-1.16.5-cl.csrg.undo.csrg");
        if (!FileUtil.checkSum(file)) {
            List<Mapping> mappings = CsrgUtil.read(getBukkitCl());
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, file);
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getBukkitMembersUndo() throws Exception {
        File file = new File(home, "bukkit-1.16.5-members.csrg.undo.csrg");
        if (!FileUtil.checkSum(file)) {
            List<Mapping> mappings = CsrgUtil.read(getBukkitMembers());
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, file);
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getUnrelocatedPaper() throws Exception {
        File file = new File(home, "unrelocatedPaper.jar");
        if (!FileUtil.checkSum(file)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            getSpecialSource().getName(),
                            "-i",
                            getPatched().getName(),
                            "-m",
                            getUndoRelocateMappings().getName(),
                            "-o",
                            file.getName()
                    }
            );
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getUndoRelocateMappings() throws Exception {
        File undoRelocate = new File(home, "undoRelocate.csrg");
        if (!FileUtil.checkSum(undoRelocate)) {
            Map<String, String> spigotNameToPackage = new HashMap<>();

            if (version.getId().equals("1.16.5")) {
                List<Mapping> bukkitClMappings = CsrgUtil.read(getBukkitCl());
                for (Mapping mapping : bukkitClMappings) {
                    if (mapping instanceof ClassMapping) {
                        String[] arr = splitToNameAndPackage(mapping.getNewName());

                        String old = spigotNameToPackage.put(arr[0], arr[1]);
                        if (old != null && !old.equals(arr[1])) {
                            throw new IllegalStateException("Конфликт между " + old + "/" + arr[1] + " и " + arr[1] + "/" + arr[0]);
                        }
                    }
                }
            }

            JarInput jarInput = new JarInput(getPatched());

            List<Mapping> undoRelocateMappings = new ArrayList<>();

            String packaging = PaperUtil.getPackaging(getPatched());
            if (packaging == null) {
                log.error("Failed to get packaging!");
                packaging = "v1_16_R3";
            }

            for (ClassNode value : jarInput.getClasses().values()) {
                if (value.name.endsWith("/Main") || value.name.contains("/Main$")) continue;
                if (value.name.startsWith("org/bukkit/craftbukkit/libs/")) {
                    ClassMapping classMapping = new ClassMapping(
                            value.name,
                            value.name.substring("org/bukkit/craftbukkit/libs/".length())
                    );
                    undoRelocateMappings.add(classMapping);
                } else if (value.name.startsWith("org/bukkit/craftbukkit/" + packaging + "/")) {
                    ClassMapping classMapping = new ClassMapping(
                            value.name,
                            value.name.replace(("org/bukkit/craftbukkit/" + packaging + "/"), "org/bukkit/craftbukkit/")
                    );
                    undoRelocateMappings.add(classMapping);
                } else if (value.name.startsWith("net/minecraft/server/" + packaging + "/")) {
                    if (!version.getId().equals("1.16.5")) {
                        String name = splitToNameAndPackage(value.name)[0];
                        if (name.equals("MinecraftServer") || name.startsWith("MinecraftServer$")) {
                            String old = name;
                            name = "net/minecraft/server/MinecraftServer";
                            if (old.contains("$")) {
                                name += "$" + old.split("\\$")[1];
                            }
                        }
                        ClassMapping classMapping = new ClassMapping(
                                value.name,
                                name
                        );
                        undoRelocateMappings.add(classMapping);
                        continue;
                    }
                    String[] arr = splitToNameAndPackage(value.name);
                    String package_ = spigotNameToPackage.get(arr[0]);
                    if (package_ == null && arr[0].contains("$")) {
                        package_ = spigotNameToPackage.get(arr[0].split("\\$")[0]);
                    }
                    if (package_ != null) {
                        ClassMapping classMapping = new ClassMapping(
                                value.name,
                                package_.isEmpty() ? "" :
                                        package_ + "/" + arr[0]
                        );
                        undoRelocateMappings.add(classMapping);
                    } else if (arr[0].length() <= 3 || (arr[0].contains("$") && arr[0].split("\\$")[0].length() <= 3)) {
                        ClassMapping classMapping = new ClassMapping(
                                value.name,
                                arr[0]
                        );
                        undoRelocateMappings.add(classMapping);
                    } else {
                        ClassMapping classMapping = new ClassMapping(
                                value.name,
                                "net/minecraft/server/" + arr[0]
                        );
                        undoRelocateMappings.add(classMapping);
                    }
                }
            }

            saveMappings(undoRelocateMappings, undoRelocate);
            FileUtil.createSum(undoRelocate);
        }
        return undoRelocate;
    }

    private MojangMappings getMojangMappings() throws Exception {
        if (mojangMappings == null) {
            mojangMappings = new MojangMappings();
        }
        return mojangMappings;
    }

    private File getPatched() throws Exception {
        File file = new File(home, "patched.jar");
        if (!FileUtil.checkSum(file)) {
            PaperUtil paperUtil = new PaperUtil();
            paperUtil.extractServerJar(getPaperclip(), home, log, file);
            FileUtil.createSum(file);
        }
        return file;
    }

    private File getSpecialSource() throws Exception {
        File file = new File(home, "SpecialSource.jar");
        if (!FileUtil.checkSum(file)) {
            FileUtil.downloadFile(Links.SPECIAL_SOURCE, new File(home, "SpecialSource.jar"));
        }
        return file;
    }

    private File getPaperclip() throws Exception {
        File file = new File(home, "paperclip.jar");
        if (!FileUtil.checkSum(file)) {
            FileUtil.downloadFile(version.getPaperclip(), file);
        }
        return file;
    }

    private File getVanillaServer() throws Exception {
        File file = new File(home, "vanillaServer.jar");
        if (!FileUtil.checkSum(file)) {
            FileUtil.downloadFile(version.getServerUrl(), file);
        }
        return file;
    }

    private File getServerMappings() throws Exception {
        File file = new File(home, "mojang-server-mappings.txt");
        if (!FileUtil.checkSum(file)) {
            FileUtil.downloadFile(version.getServerMappingsUrl(), file);
        }
        return file;
    }

    private File getBukkitMembers() throws Exception {
        File file = new File(home, "bukkit-1.16.5-members.csrg");
        if (!FileUtil.checkSum(file)) {
            FileUtil.downloadFile(version.getBukkitMembersUrl(), file);
        }
        return file;
    }

    private File getBukkitCl() throws Exception {
        File file = new File(home, "bukkit-1.16.5-cl.csrg");
        if (!FileUtil.checkSum(file)) {
            FileUtil.downloadFile(version.getBukkitClUrl(), file);
        }
        return file;
    }

    private class MojangMappings {
        private final File toObfClass;
        private final File toObfMembers;
        private final File toMojangClass;
        private final File toMojangMembers;

        public MojangMappings() throws Exception {
            toObfClass = new File(home, "toObfClass.csrg");
            toObfMembers = new File(home, "toObfMembers.csrg");

            toMojangClass = new File(home, "toMojangClass.csrg");
            toMojangMembers = new File(home, "toMojangMembers.csrg");

            if (
                    !FileUtil.checkSum(toMojangMembers) ||
                    !FileUtil.checkSum(toMojangClass) ||
                    !FileUtil.checkSum(toObfMembers) ||
                    !FileUtil.checkSum(toObfClass)
            ) {
                JarInput jarInput = new JarInput(getVanillaServer());
                List<LibLoader> libs = new ArrayList<>();
                libs.add(jarInput);
                libs.add(new RuntimeLibLoader());
                jarInput.buildClassHierarchy(libs);

                List<Mapping> result = ProGuardToCsrgMap.readProGuard(getServerMappings(), jarInput);

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
        }
    }

    private void saveMappings(List<Mapping> mappings, File to) throws IOException {
        List<String> strList = mappings.stream().map(Object::toString).collect(Collectors.toList());
        Files.write(to.toPath(), Joiner.on("\n").join(strList).getBytes(StandardCharsets.UTF_8));
    }

    private String[] splitToNameAndPackage(String s) {
        String[] arr = s.split("/");
        String name = arr[arr.length - 1];
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
}