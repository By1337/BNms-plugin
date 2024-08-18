package org.by1337.bnms.remap;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RuntimeLibLoader implements LibLoader {
    private Map<String, ClassNode> classes = new HashMap<>();

    @Override
    public ClassNode getClass(String name) {
        ClassNode node = classes.get(name);
        if (node != null) return node;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            if (loader == null) {
                return null;
            }
            String className = name + ".class";
            InputStream classStream = loader.getResourceAsStream(className);

            if (classStream == null) {
                return null;
            }

            ClassReader classReader = new ClassReader(classStream);
            node = new ClassNode();
            classReader.accept(node, 0);

            classes.put(name, node);
            return node;
        } catch (IOException ignore) {

        }

        return null;
    }
}
