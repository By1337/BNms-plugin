package org.by1337.bnms.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class JarInput implements LibLoader {
    private Map<String, ClassNode> classes;
    private ClassHierarchy hierarchy;
    private final File input;

    public JarInput(String input) throws IOException {
        this(new File(input));
    }

    public JarInput(File input) throws IOException {
        this.input = input;
        if (!input.exists()) {
            throw new IllegalArgumentException("file does not exists!");
        }
        classes = new HashMap<>();
        for (ClassNode classNode : LoadClassUtil.loadClasses(new JarFile(input), ClassReader.EXPAND_FRAMES)) {
            classes.put(classNode.name, classNode);
        }
    }

    public void buildClassHierarchy(List<LibLoader> libLoaders) {
        hierarchy = new ClassHierarchy(this, libLoaders);
        hierarchy.build();
    }
    public Map<String, ClassNode> getClasses() {
        return classes;
    }

    @Override
    public ClassNode getClass(String name) {
        return classes.get(name);
    }

    public File getInput() {
        return input;
    }

    public ClassHierarchy getHierarchy() {
        return hierarchy;
    }

}
