package org.by1337.bnms.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

public class LibInput implements LibLoader {
    private Map<String, ClassNode> classes = new HashMap<>();

    public LibInput(File file) {
        loadLib(file);
    }

    public void loadLib(File file) {
        try {
            JarFile jarFile = new JarFile(file);

            List<ClassNode> nodes = LoadClassUtil.loadClasses(jarFile, ClassReader.SKIP_CODE);
            nodes.forEach(node -> classes.put(node.name, node));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ClassNode getClass(String name) {
        return classes.get(name);
    }
}
