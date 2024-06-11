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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LegacyProcess {
    private final Log log;
    private final File home;
    private final Version version;
    private File bukkitCl;
    private File bukkitMembers;
    private File serverMappings;
    private File server;
    private File toObfClass;
    private File toObfMembers;
    private File toMojangClass;
    private File toMojangMembers;
    private File undoRelocate;
    private File paperclip;
    private File patched;
    private File bukkitClUndo;
    private File bukkitMembersUndo;
    private File paperUndoRelocateJar;
    private File specialSourceJar;
    private File paperObfClassesJar;
    private File paperMojangClassesJar;
    private File toMojangMembersFixed;
    private File remappedPaperJar;
    private File relocation;
    private File toSpigotMembers;

    public LegacyProcess(Log log, File home, Version version) {
        this.log = log;
        this.home = home;
        this.version = version;
    }

    public void init() throws Exception {
        initFiles();
    }

    public File createRemappedPaper() throws Exception {
        generateBukkitClassesUndo();
        generateBukkitMembersUndo();
        undoRelocatePaper();
        undoBukkitClasses();
        // undoBukkitMembers(); :|
        toMojangClasses();
        createToMojangMembersFixed();
        applyToMojangMembers();
        return remappedPaperJar;
    }

    public File createSpigotToMojang_(File file) throws Exception {
        String javaHome = ProcessUtil.getJavaHome();
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        file.getAbsolutePath(),
                        "-m",
                        undoRelocate.getName(),
                        "-o",
                        "inputSTM-1.jar"
                }
        );
        generateBukkitClassesUndo();
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        "inputSTM-1.jar",
                        "-m",
                        bukkitClUndo.getName(),
                        "-o",
                        "inputSTM-2.jar"
                }
        );
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        "inputSTM-2.jar",
                        "-m",
                        toMojangClass.getName(),
                        "-o",
                        "inputSTM-3.jar"
                }
        );
        createToMojangMembersFixed();
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        "inputSTM-3.jar",
                        "-m",
                        toMojangMembersFixed.getName(),
                        "-o",
                        "resultSTM.jar"
                }
        );
        new File(home, "inputSTM-1.jar").delete();
        new File(home, "inputSTM-2.jar").delete();
        new File(home, "inputSTM-3.jar").delete();
        return new File(home, "resultSTM.jar");
    }

    public File createMojang_ToSpigot(File file) throws Exception {
        generateRelocation();
        generateToSpigotMembers();

        String javaHome = ProcessUtil.getJavaHome();
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        file.getAbsolutePath(),
                        "-m",
                        toSpigotMembers.getName(),
                        "-o",
                        "inputMTS-1.jar"
                }
        );
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        "inputMTS-1.jar",
                        "-m",
                        toObfClass.getName(),
                        "-o",
                        "inputMTS-2.jar"
                }
        );
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        "inputMTS-2.jar",
                        "-m",
                        bukkitCl.getName(),
                        "-o",
                        "inputMTS-3.jar"
                }
        );
        ProcessUtil.executeCommand(home,
                new String[]{
                        javaHome,
                        "-jar",
                        specialSourceJar.getName(),
                        "-i",
                        "inputMTS-3.jar",
                        "-m",
                        relocation.getName(),
                        "-o",
                        "resultMTS.jar"
                }
        );
        new File(home, "inputMTS-1.jar").delete();
        new File(home, "inputMTS-2.jar").delete();
        new File(home, "inputMTS-3.jar").delete();
        return new File(home, "resultMTS.jar");
    }

    private void generateToSpigotMembers() throws Exception {
        toSpigotMembers = new File(home, "toSpigotMembers.csrg");
        if (!FileUtil.checkSum(toSpigotMembers)) {

            createToMojangMembersFixed();
            List<Mapping> mappings = CsrgUtil.read(toMojangMembersFixed);
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, toSpigotMembers);
            FileUtil.createSum(toSpigotMembers);
        }
    }

    private void generateRelocation() throws IOException, NoSuchAlgorithmException {
        relocation = new File(home, "relocation.csrg");
        if (!FileUtil.checkSum(relocation)) {
            List<Mapping> mappings = CsrgUtil.read(undoRelocate);
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, relocation);
            FileUtil.createSum(relocation);
        }
    }

    public void applyToMojangMembers() throws IOException, NoSuchAlgorithmException {
        remappedPaperJar = new File(home, "paper-remapped.jar");
        if (!FileUtil.checkSum(remappedPaperJar)) {
            //"java -jar SpecialSource.jar -i %s -m %s -o %s"
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            specialSourceJar.getName(),
                            "-i",
                            paperMojangClassesJar.getName(),
                            "-m",
                            toMojangMembersFixed.getName(),
                            "-o",
                            remappedPaperJar.getName()
                    }
            );
            FileUtil.createSum(remappedPaperJar);
        }
    }

    public void createToMojangMembersFixed() throws Exception {
        toMojangMembersFixed = new File(home, "toMojangMembers.csrg.fixed.csrg");
        if (!FileUtil.checkSum(toMojangMembersFixed)) {
            convertProGuardToCsrg();
            List<Mapping> mappings = CsrgUtil.read(toMojangMembers);
            mappings.removeIf(m -> m instanceof ClassMapping);
            for (Mapping mapping : mappings) {
                if (mapping.getOldName().equals(mapping.getNewName())) continue;
                mapping.setNewName(mapping.getNewName() + "_");
            }
            saveMappings(mappings, toMojangMembersFixed);
            FileUtil.createSum(toMojangMembersFixed);
        }
    }

    public void toMojangClasses() throws IOException, NoSuchAlgorithmException {
        paperMojangClassesJar = new File(home, "paper-mojang-cl.jar");
        if (!FileUtil.checkSum(paperMojangClassesJar)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            specialSourceJar.getName(),
                            "-i",
                            paperObfClassesJar.getName(),
                            "-m",
                            toMojangClass.getName(),
                            "-o",
                            paperMojangClassesJar.getName()
                    }
            );
            FileUtil.createSum(paperMojangClassesJar);
        }
    }

    public void undoBukkitClasses() throws IOException, NoSuchAlgorithmException {
        paperObfClassesJar = new File(home, "paper-obf-cl.jar");
        if (!FileUtil.checkSum(paperObfClassesJar)) {
            String javaHome = ProcessUtil.getJavaHome();
            generateBukkitClassesUndo();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            specialSourceJar.getName(),
                            "-i",
                            paperUndoRelocateJar.getName(),
                            "-m",
                            bukkitClUndo.getName(),
                            "-o",
                            paperObfClassesJar.getName()
                    }
            );
            FileUtil.createSum(paperObfClassesJar);
        }
    }

    private void undoRelocatePaper() throws IOException, NoSuchAlgorithmException {
        paperUndoRelocateJar = new File(home, "paperUndoRelocate.jar");
        if (!FileUtil.checkSum(paperUndoRelocateJar)) {
            String javaHome = ProcessUtil.getJavaHome();
            //("java -jar SpecialSource.jar -i paper.jar -m 1.16.5undo-relocate.csrg -o paper.jar-step1.jar")
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            specialSourceJar.getName(),
                            "-i",
                            patched.getName(),
                            "-m",
                            undoRelocate.getName(),
                            "-o",
                            paperUndoRelocateJar.getName()
                    }
            );
            FileUtil.createSum(paperUndoRelocateJar);
        }
    }

    private void initFiles() throws Exception {
        bukkitCl = FileUtil.downloadFile(version.getBukkitClUrl(), new File(home, "bukkit-cl.csrg"));
        bukkitMembers = FileUtil.downloadFile(version.getBukkitMembersUrl(), new File(home, "bukkit-members.csrg"));

        serverMappings = FileUtil.downloadFile(version.getServerMappingsUrl(), new File(home, "server-mappings.txt"));
        server = FileUtil.downloadFile(version.getServerUrl(), new File(home, "server.jar"));
        paperclip = FileUtil.downloadFile(version.getPaperclip(), new File(home, "paperclip.jar"));
        specialSourceJar = FileUtil.downloadFile(Links.SPECIAL_SOURCE, new File(home, "SpecialSource.jar"));
        patched = new File(home, "patched.jar");
        if (!FileUtil.checkSum(patched)) {
            PaperUtil paperUtil = new PaperUtil();
            paperUtil.extractServerJar(paperclip, home, log, patched);
            FileUtil.createSum(patched);
        }
        convertProGuardToCsrg();
        generateUndoRelocate();
    }

    private void generateBukkitClassesUndo() throws IOException, NoSuchAlgorithmException {
        bukkitClUndo = new File(home, "bukkit-1.16.5-cl.csrg.undo.csrg");
        if (!FileUtil.checkSum(bukkitClUndo)) {
            List<Mapping> mappings = CsrgUtil.read(bukkitCl);
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, bukkitClUndo);
            FileUtil.createSum(bukkitClUndo);
        }
    }


    private void generateBukkitMembersUndo() throws IOException, NoSuchAlgorithmException {
        bukkitMembersUndo = new File(home, "bukkit-1.16.5-members.csrg.undo.csrg");
        if (!FileUtil.checkSum(bukkitMembersUndo)) {
            List<Mapping> mappings = CsrgUtil.read(bukkitMembers);
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, bukkitMembersUndo);
            FileUtil.createSum(bukkitMembersUndo);
        }
    }

    private void generateUndoRelocate() throws IOException, NoSuchAlgorithmException {
        undoRelocate = new File(home, "undoRelocate.csrg");
        if (!FileUtil.checkSum(undoRelocate)) {


            Map<String, String> spigotNameToPackage = new HashMap<>();

            if (version.getId().equals("1.16.5")) {
                List<Mapping> bukkitClMappings = CsrgUtil.read(bukkitCl);
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

            JarInput jarInput = new JarInput(patched);

            List<Mapping> undoRelocateMappings = new ArrayList<>();

            String packaging = PaperUtil.getPackaging(patched);
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

            saveMappings(undoRelocateMappings, this.undoRelocate);
            FileUtil.createSum(this.undoRelocate);
        }
    }

    private void convertProGuardToCsrg() throws Exception {
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

            server = FileUtil.downloadFile(version.getServerUrl(), new File(home, "server.jar"));
            JarInput jarInput = new JarInput(server);
            List<LibLoader> libs = new ArrayList<>();
            libs.add(jarInput);
            libs.add(new RuntimeLibLoader());
            jarInput.buildClassHierarchy(libs);

            serverMappings = FileUtil.downloadFile(version.getServerMappingsUrl(), new File(home, "server-mappings.txt"));
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
    }

    private void saveMappings(List<Mapping> mappings, File to) throws IOException {
        List<String> strList = mappings.stream().map(Object::toString).collect(Collectors.toList());
        Files.write(to.toPath(), Joiner.on("\n").join(strList).getBytes(StandardCharsets.UTF_8));
    }

    private String[] splitToNameAndPackage(String s) {
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

}
