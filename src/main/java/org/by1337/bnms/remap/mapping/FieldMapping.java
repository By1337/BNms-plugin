package org.by1337.bnms.remap.mapping;

import java.util.Objects;

public class FieldMapping implements Mapping {
    private String oldName;
    private String newName;
    private String ownerName;

    public FieldMapping(String oldName, String newName, String ownerName) {
        this.oldName = oldName;
        this.newName = newName;
        this.ownerName = ownerName;
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
        return ownerName;
    }
    @Override
    public String toString() {
        return ownerName + " " + oldName + " " + newName;
    }

    public void setOldName(String oldName) {
        this.oldName = oldName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldMapping that = (FieldMapping) o;
        return Objects.equals(oldName, that.oldName) && Objects.equals(newName, that.newName) && Objects.equals(ownerName, that.ownerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldName, newName, ownerName);
    }
}
