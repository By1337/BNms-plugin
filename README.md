# Bnms-plugin

This plugin adds support for Paper NMS using Mojang mappings for versions 1.16.5 and below. Since versions 1.16.5 and below were developed using Bukkit mappings, many conflicts arose.

## Mapping Issues

Example of a conflict:
```java
public SectionPosition getPlayerMapSection() { return this.O(); } // Paper - OBFHELPER
public SectionPosition O() { return this.cj; }
```

If the method `O` has the original name `getPlayerMapSection`, a conflict arises due to identical names. To resolve this issue, an `_` is added to the original names.

Example:
![mappingExample.png](img%2FmappingExample.png)

This plugin is experimental, so errors are possible. Please report them in issues.

## Version Support

The plugin is designed for version 1.16.5 but also supports older versions. For example, a remapped 1.14.4 server may not run, whereas 1.16.5 runs stably.

## Remapped Server

The remapped server can be found at:
```
C:\Users\user\.m2\repository\org\by1337\nms\paper-nms\1.16.5\paper-nms-1.16.5.jar
```

## What Does This Plugin Remap?

- All libraries from `org/bukkit/craftbukkit/libs/` to `'/'` such as `org/apache`
- `org/bukkit/craftbukkit/v1_16_R3/` to `org/bukkit/craftbukkit/`
- Restores the original NMS structure

## Installation

First, add this plugin to your `pom.xml`:
```xml
<project>
    <pluginRepositories>
        <pluginRepository>
            <id>by1337-repo</id>
            <url>https://repo.by1337.space/repository/maven-releases/</url>
        </pluginRepository>
    </pluginRepositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.by1337.bnms</groupId>
                <artifactId>bnms-plugin</artifactId>
                <version>1.0-beta</version>
                <configuration>
                    <version>1.16.5</version>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>remap</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>org.by1337.nms</groupId>
            <artifactId>paper-nms</artifactId>
            <version>1.16.5</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### Preparing the Local Repository

Initially, `paper-nms` will not be in your local repository. To fix this, follow these steps:

1. ![step_1.png](img%2Fstep_1.png)
2. ![step_2.png](img%2Fstep_2.png)

After this, the plugin will perform the necessary actions, and `paper-nms` will appear in your local repository.

## Task Descriptions

### `remap`

Takes the latest build from the `target` folder and remaps it to Spigot mappings.

### `spigot-to-mojang`

This task is more interesting. If you already have a lot of code on Spigot mappings, you can run this task, and the plugin will take the latest build from the `target` folder, remap it to Mojang mappings, and you just need to decompile it and transfer it to the source code.

Follow these instructions to successfully install and use the Bnms-plugin. If you have any questions or issues, feel free to reach out in the issues section.