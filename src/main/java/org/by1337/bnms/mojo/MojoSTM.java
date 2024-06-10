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

import java.io.File;
import java.nio.file.Files;

@Mojo(name = "spigot-to-mojang", defaultPhase = LifecyclePhase.PACKAGE)
public class MojoSTM extends AbstractMojo {
    @Parameter(property = "version", required = true)
    String version;
    @Parameter( defaultValue = "${project}", required = true, readonly = true )
    MavenProject project;
    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    ArtifactRepository localRepository;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File m2 = new File(localRepository.getBasedir()).getParentFile();
        File home = new File(m2, "bnmsCash");
        if (!home.exists()) {
            home.mkdirs();
        }
        try {
            Version.load(new File(home, "versionCash"));
            Version v = Version.getByName(version);
            if (v == null) throw new IllegalStateException("Unknown version! " + version);

            if (v.getIndex() >= Version.getByName("1.16.5").getIndex()) {
                File versionHome = new File(home, version);
                versionHome.mkdirs();

                LegacyProcess legacyProcess = new LegacyProcess(getLog(), versionHome, v);
                legacyProcess.init();

                File target = new File(this.project.getBuild().getOutputDirectory()).getParentFile();

                File build = new File(target, project.getArtifactId() + "-" + project.getVersion() + ".jar");

                if (build.exists()){
                    File out = legacyProcess.createSpigotToMojang_(build);

                    Files.move(out.toPath(), new File(target, build.getName() + "-mojang.jar").toPath());
                }else {
                    getLog().error("Failed to get last build file!");
                }


            }

        } catch (Exception e) {
            getLog().error(e);
        }
    }
}
