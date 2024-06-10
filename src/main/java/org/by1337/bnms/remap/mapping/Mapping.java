package org.by1337.bnms.remap.mapping;

public interface Mapping {
    void reverse();
    String getNewName();
    void setNewName(String newName);
    String getOldName();
    void setOldName(String oldName);
    String getOwner();
}
