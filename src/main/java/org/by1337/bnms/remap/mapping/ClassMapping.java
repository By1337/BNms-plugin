package org.by1337.bnms.remap.mapping;

import java.util.Objects;

public class ClassMapping implements Mapping {
    private String oldName;
    private String newName;

    public ClassMapping(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public void reverse() {
        String tmp = oldName;
        oldName = newName;
        newName = tmp;
    }

    @Override
    public String toString() {
        return oldName + " " + newName;
    }

    public void setOldName(String oldName) {
        this.oldName = oldName;
    }

    @Override
    public String getOwner() {
        return getOldName();
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassMapping that = (ClassMapping) o;
        return Objects.equals(oldName, that.oldName) && Objects.equals(newName, that.newName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldName, newName);
    }
}
