package org.by1337.bnms.mojo;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.by1337.bnms.Version;
import org.by1337.bnms.process.LegacyProcess;
import org.by1337.bnms.util.SharedConstants;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Mojo(name = "remap", defaultPhase = LifecyclePhase.PACKAGE)
public class MojoRemap extends AbstractMojo {
    @Parameter(property = "version", required = true)
    String version;
    @Parameter( defaultValue = "${project}", required = true, readonly = true )
    MavenProject project;
    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    ArtifactRepository localRepository;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        SharedConstants.LOGGER = getLog();
        File m2 = new File(localRepository.getBasedir()).getParentFile();
        File home = new File(m2, "bnmsCache");
        if (!home.exists()) {
            home.mkdirs();
        }
        try {
            Version.load(new File(home, "versionCache"));
            Version v = Version.getByName(version);
            if (v == null) throw new IllegalStateException("Unknown version! " + version);

            if (v.getIndex() >= Version.getByName("1.16.5").getIndex()) {
                File versionHome = new File(home, version);
                versionHome.mkdirs();


                LegacyProcess legacyProcess = new LegacyProcess(getLog(), versionHome, v);
                legacyProcess.init();

                File input = this.project.getArtifact().getFile();

                File out = legacyProcess.createMojang_ToSpigot(input);

                Files.move(input.toPath(), new File(input.getParent(), input.getName() + "-mojang.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(out.toPath(), input.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception e) {
            getLog().error(e);
        }
    }
}
