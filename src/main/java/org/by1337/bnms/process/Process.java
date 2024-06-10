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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Process {
    private final Log log;
    private final File home;
    private final Version version;
    private File toObfClass;
    private File toObfMembers;
    private File toMojangClass;
    private File toMojangMembers;

    public Process(Log log, File home, Version version) {
        this.log = log;
        this.home = home;
        this.version = version;
    }

//    public static void main(String[] args) throws Exception {
//        File versionCash = new File("./versionCash");
//        versionCash.mkdirs();
//        Version.load(versionCash);
//        Version version1 = Version.getByName("1.19.4");
//
//        File data = new File("./test2/" + version1.getId());
//        data.mkdirs();
//        Process process = new Process(
//                new SystemStreamLog(),
//                data,
//                version1
//        );
//        process.getPaperMojang();
//    }

    public File getPaperMojang() throws Exception {
        File paper = new File(home, "paper-mojang.jar");
        if (!FileUtil.checkSum(paper)) {
            applyMappings(
                    getPaperMojangCl(),
                    paper,
                    getToMojangMembers()
            );
            FileUtil.createSum(paper);
        }
        return paper;
    }

    private File getPaperMojangCl() throws Exception {
        File paper = new File(home, "paper-mojang-cl.jar");
        if (!FileUtil.checkSum(paper)) {
            applyMappings(
                    getPaperObf(),
                    paper,
                    getToMojangClass()
            );
            FileUtil.createSum(paper);
        }
        return paper;
    }

    private File getPaperObf() throws Exception {
        File paper = new File(home, "paperObf.jar");
        if (!FileUtil.checkSum(paper)) {
            File mappings = reverseCsrg(getBukkitCl(), new File(home, "bukkit-cl.csrg.undo.csrg"));
            applyMappings(
                    getPaperUndoMembers(),
                    paper,
                    mappings
            );
            FileUtil.createSum(paper);
        }
        return paper;
    }

    private File getPaperUndoMembers() throws Exception {
        File paper = new File(home, "paper-undo-members.jar");
        if (!FileUtil.checkSum(paper)) {
            File bukkitMembers = getBukkitMembers();
            if (Files.size(bukkitMembers.toPath()) < 50_000) {
                // новые версиях нет таких мапингов
                // 1.18+
                return getPatchedPaper();
            }
            File mappings = reverseCsrg(getBukkitMembers(), new File(home, "bukkit-members.csrg.undo.csrg"));
            applyMappings(
                    getPatchedPaper(),
                    paper,
                    mappings
            );
            FileUtil.createSum(paper);
        }
        return paper;
    }

    private File applyMappings(File in, File out, File mappings) throws Exception {
        if (!FileUtil.checkSum(out)) {
            String javaHome = ProcessUtil.getJavaHome();
            ProcessUtil.executeCommand(home,
                    new String[]{
                            javaHome,
                            "-jar",
                            getSpecialSource().getName(),
                            "-i",
                            in.getName(),
                            "-m",
                            mappings.getName(),
                            "-o",
                            out.getName()
                    }
            );
            FileUtil.createSum(out);
        }
        return out;
    }

    private File getPatchedPaper() throws Exception {
        File patched = new File(home, "patched.jar");
        if (!FileUtil.checkSum(patched)) {
            PaperUtil paperUtil = new PaperUtil();
            paperUtil.extractServerJar(getPaperclip(), home, log, patched);
            FileUtil.createSum(patched);
        }
        return patched;
    }

    private File reverseCsrg(File file, File out) throws IOException, NoSuchAlgorithmException {
        if (!FileUtil.checkSum(out)) {
            List<Mapping> mappings = CsrgUtil.read(file);
            mappings.forEach(Mapping::reverse);
            saveMappings(mappings, out);
            FileUtil.createSum(out);
        }
        return out;
    }

    private File getPaperclip() throws Exception {
        return FileUtil.downloadFile(version.getPaperclip(), new File(home, "paperclip.jar"));
    }

    private File getServer() throws Exception {
        File server =  FileUtil.downloadFile(version.getServerUrl(), new File(home, "server.jar"));
        return server;//todo
    }

    private File getServerMappings() throws Exception {
        return FileUtil.downloadFile(version.getServerMappingsUrl(), new File(home, "server-mappings.txt"));
    }

    private File getBukkitCl() throws Exception {
        return FileUtil.downloadFile(version.getBukkitClUrl(), new File(home, "bukkit-cl.csrg"));
    }

    private File getBukkitMembers() throws Exception {
        return FileUtil.downloadFile(version.getBukkitMembersUrl(), new File(home, "bukkit-members.csrg"));
    }

    private File getSpecialSource() throws Exception {
        return FileUtil.downloadFile(Links.SPECIAL_SOURCE, new File(home, "SpecialSource.jar"));
    }

    private File getToObfClass() throws Exception {
        if (toObfClass == null) {
            convertProGuardToCsrg();
        }
        return toObfClass;
    }

    private File getToObfMembers() throws Exception {
        if (toObfMembers == null) {
            convertProGuardToCsrg();
        }
        return toObfMembers;
    }

    private File getToMojangClass() throws Exception {
        if (toMojangClass == null) {
            convertProGuardToCsrg();
        }
        return toMojangClass;
    }

    private File getToMojangMembers() throws Exception {
        if (toMojangMembers == null) {
            convertProGuardToCsrg();
        }
        return toMojangMembers;
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


            JarInput jarInput = new JarInput(getServer());
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

    private void saveMappings(List<Mapping> mappings, File to) throws IOException {
        List<String> strList = mappings.stream().map(Object::toString).collect(Collectors.toList());
        Files.write(to.toPath(), Joiner.on("\n").join(strList).getBytes(StandardCharsets.UTF_8));
    }

}
