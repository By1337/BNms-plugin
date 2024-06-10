package org.by1337.bnms.util.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MavenRepositoryUtil {
    private final ArtifactRepository localRepository;
    private final ArtifactFactory artifactFactory;
    private final ArtifactInstaller artifactInstaller;
    private final Log log;

    public MavenRepositoryUtil(ArtifactRepository localRepository, ArtifactFactory artifactFactory, ArtifactInstaller artifactInstaller, Log log) {
        this.localRepository = localRepository;
        this.artifactFactory = artifactFactory;
        this.artifactInstaller = artifactInstaller;
        this.log = log;
    }
    public void installToMavenRepo(String gameVersion, Path mappedServerPath, Path pomPath) throws MojoExecutionException {
        StringBuilder pom = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n")
                .append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
                .append("  <modelVersion>4.0.0</modelVersion>\n")
                .append("  <groupId>").append("org.by1337.nms").append("</groupId>\n")
                .append("  <artifactId>").append("paper-nms").append("</artifactId>\n")
                .append("  <version>").append(gameVersion).append("</version>\n");
        pom.append("</project>\n");

        try {
            Files.write(pomPath, pom.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write pom.xml", e);
        }

        try {
            this.installViaArtifactInstaller(mappedServerPath, pomPath, gameVersion);
        } catch (ArtifactInstallationException e) {
            throw new MojoExecutionException("Failed to install mapped server jar to local repository.", e);
        }

        log.info("Installed into local repository");

        log.info("Cleaning up");
        try {
            Files.deleteIfExists(pomPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to clean up", e);
        }
    }
    private void installViaArtifactInstaller(Path artifactPath, Path pomPath, String gameVersion) throws ArtifactInstallationException {
        Artifact artifact = this.artifactFactory.createArtifactWithClassifier("org.by1337.nms", "paper-nms", gameVersion, "jar", null);

        ProjectArtifactMetadata pomMetadata = new ProjectArtifactMetadata(artifact, pomPath.toFile());
        artifact.addMetadata(pomMetadata);

        this.artifactInstaller.install(artifactPath.toFile(), artifact, this.localRepository);
    }
}
