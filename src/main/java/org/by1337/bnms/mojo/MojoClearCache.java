package org.by1337.bnms.mojo;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.by1337.bnms.util.FileUtil;
import org.by1337.bnms.util.SharedConstants;

import java.io.File;

@Mojo(name = "clear-cache", defaultPhase = LifecyclePhase.PACKAGE)
public class MojoClearCache extends AbstractMojo {
    @Parameter(property = "version", required = true)
    String version;
    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    ArtifactRepository localRepository;

    @Override
    public void execute() {
        SharedConstants.LOGGER = getLog();
        File m2 = new File(localRepository.getBasedir()).getParentFile();
        File home = new File(m2, "bnmsCache");
        if (!home.exists()) {
            home.mkdirs();
        }
        try {

            File versionHome = new File(home, version);
            versionHome.mkdirs();
            getLog().info("deleting... " + versionHome.getPath());
            FileUtil.deleteDirectory(versionHome);

            File versionCache = new File(home, "versionCache");
            getLog().info("deleting... " + versionCache.getPath());
            FileUtil.deleteDirectory(versionCache);

        } catch (Exception e) {
            getLog().error(e);
        }
    }

}
