package org.by1337.bnms.remap.mapping;

import java.util.Objects;

public class MethodMapping implements Mapping {
    private String oldName;
    private String newName;
    private String owner;
    private String desc;

    public MethodMapping(String oldName, String newName, String owner, String desc) {
        this.oldName = oldName;
        this.newName = newName;
        this.owner = owner;
        this.desc = desc;
    }

    public void reverse() {
        String tmp = oldName;
        oldName = newName;
        newName = tmp;
    }
    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public String getOwner() {
        return owner;
    }

    public String getDesc() {
        return desc;
    }
    @Override
    public String toString() {
        return owner + " " + oldName + " " + desc + " " + newName;
    }

    public void setOldName(String oldName) {
        this.oldName = oldName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodMapping mapping = (MethodMapping) o;
        return Objects.equals(oldName, mapping.oldName) && Objects.equals(newName, mapping.newName) && Objects.equals(owner, mapping.owner) && Objects.equals(desc, mapping.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldName, newName, owner, desc);
    }
}

