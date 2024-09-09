package org.by1337.bnms.remap;

import org.by1337.bnms.util.SharedConstants;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

public class ClassHierarchy {
    private final Map<String, Set<ClassNode>> hierarchy = new HashMap<>();
    private final Map<String, Set<ClassNode>> hierarchyParent = new HashMap<>();
    private final JarInput jarInput;
    private final List<LibLoader> libLoaders;
    private final Set<String> seenMissingClass = new HashSet<>();

    public ClassHierarchy(JarInput jarInput, List<LibLoader> libLoaders) {
        this.jarInput = jarInput;
        this.libLoaders = libLoaders;
    }

    public void build() {
        for (ClassNode value : jarInput.getClasses().values()) {
            process(value);
        }
        for (String string : hierarchy.keySet().toArray(new String[0])) {
            ClassNode[] set = hierarchy.getOrDefault(string, new HashSet<>()).toArray(new ClassNode[0]);
            for (ClassNode classNode : set) {
                hierarchy.computeIfAbsent(string, k -> new HashSet<>()).addAll(hierarchy.getOrDefault(classNode.name, Collections.emptySet()));
            }
        }
        for (String string : hierarchyParent.keySet().toArray(new String[0])) {
            ClassNode[] set = hierarchyParent.getOrDefault(string, new HashSet<>()).toArray(new ClassNode[0]);
            for (ClassNode classNode : set) {
                hierarchyParent.computeIfAbsent(string, k -> new HashSet<>()).addAll(hierarchyParent.getOrDefault(classNode.name, Collections.emptySet()));
            }
        }
    }

    private void process(ClassNode classNode) {
        ClassNode superClass = findClassOrNull(classNode.superName);

        while (superClass != null) {
            hierarchy.computeIfAbsent(superClass.name, k -> new HashSet<>()).add(classNode);
            hierarchyParent.computeIfAbsent(classNode.name, k -> new HashSet<>()).add(superClass);
            superClass = findClassOrNull(superClass.superName);
        }
        Set<ClassNode> _interfaces = new HashSet<>();
        collectInterfaces(classNode, _interfaces);
        _interfaces.remove(classNode);
        for (ClassNode node : hierarchyParent.getOrDefault(classNode.name, new HashSet<>()).toArray(new ClassNode[0])) {
            collectInterfaces(node, _interfaces);
            _interfaces.remove(node);
            for (ClassNode anInterface : _interfaces) {
                hierarchy.computeIfAbsent(anInterface.name, k -> new HashSet<>()).add(classNode);
            }
            hierarchyParent.computeIfAbsent(classNode.name, k -> new HashSet<>()).addAll(_interfaces);
        }

    }
    private void collectInterfaces(ClassNode classNode, Set<ClassNode> classNodes){
        if (!classNodes.add(classNode)) return;
        if (classNode.interfaces == null) return;
        for (String anInterface : classNode.interfaces) {
            ClassNode _interface = findClassOrNull(anInterface);
            if (_interface != null){
                collectInterfaces(_interface, classNodes);
            }
        }
    }


    public ClassNode findClassOrNull(String clazz) {
        if (clazz == null) return null;
        for (LibLoader lib : libLoaders) {
            ClassNode node = lib.getClass(clazz);
            if (node != null) return node;
        }
        if (!seenMissingClass.contains(clazz)) {
            SharedConstants.LOGGER.warn("Missing class " + clazz);
            seenMissingClass.add(clazz);
        }
        return null;
    }

    @Override
    public String toString() {
        return toString(hierarchy) + "\n" + toString(hierarchyParent);
    }

    private String toString(Map<String, Set<ClassNode>> map) {
        StringBuilder sb = new StringBuilder("[\n");
        for (String string : map.keySet()) {
            sb.append("\t").append(string).append(" -> [ ");
            for (ClassNode classNode : map.get(string)) {
                sb.append(classNode.name).append(", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append(" ]");
            sb.append("\n");
        }
        sb.append("\n]");
        return sb.toString();
    }

    public Map<String, Set<ClassNode>> getHierarchy() {
        return hierarchy;
    }

    public Map<String, Set<ClassNode>> getHierarchyParent() {
        return hierarchyParent;
    }
}
