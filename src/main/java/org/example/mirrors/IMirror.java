package org.example.mirrors;

public interface IMirror {
    String getName();
    void syncRepo();
    void verifyRepo();
    void checkRepo();
    void fixRepo();
    void checkDependencies();
    MirrorStatus getStatus();
}
