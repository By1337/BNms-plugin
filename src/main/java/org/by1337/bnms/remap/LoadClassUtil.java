package org.by1337.bnms.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class LoadClassUtil {
    public static List<ClassNode> loadClasses(JarFile jarFile, int readerType) throws IOException {
        List<ClassNode> nodes = new ArrayList<>();
        jarFile.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                final ClassReader classReader;
                try {
                    classReader = new ClassReader(
                            jarFile.getInputStream(entry));
                    final ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, readerType);
                    nodes.add(classNode);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        jarFile.close();
        return nodes;
    }
}
