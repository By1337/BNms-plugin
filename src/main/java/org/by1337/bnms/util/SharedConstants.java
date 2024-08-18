package org.by1337.bnms.util;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

public class SharedConstants {
    public static final boolean DEBUG = false;
    public static Log LOGGER = new SystemStreamLog();
}
