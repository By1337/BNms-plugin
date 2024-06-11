package org.by1337.bnms.mojo;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.by1337.bnms.Version;
import org.by1337.bnms.process.LegacyProcess;
import org.by1337.bnms.util.FileUtil;
import org.by1337.bnms.util.maven.MavenRepositoryUtil;

import java.io.File;
import java.util.UUID;

@Mojo(name = "init", defaultPhase = LifecyclePhase.PACKAGE)
public class MojoInit extends AbstractMojo {
    @Parameter(property = "version", required = true)
    String version;
    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    ArtifactRepository localRepository;
    @Component
    ArtifactFactory artifactFactory;
    @Component
    ArtifactInstaller artifactInstaller;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
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
                MavenRepositoryUtil util = new MavenRepositoryUtil(
                        localRepository,
                        artifactFactory,
                        artifactInstaller,
                        getLog()
                );
                File tempCache = new File(home, UUID.randomUUID().toString().replace("-", ""));
                tempCache.mkdirs();
                util.installToMavenRepo(
                        version,
                        legacyProcess.createRemappedPaper().toPath(),
                        tempCache.toPath().resolve("pom.xml")
                );
                FileUtil.deleteDirectory(tempCache);
            }

        } catch (Exception e) {
            getLog().error(e);
        }
    }

}
