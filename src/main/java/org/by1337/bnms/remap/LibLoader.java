package org.by1337.bnms.remap;

import org.objectweb.asm.tree.ClassNode;

public interface LibLoader {
    ClassNode getClass(String name);
}
