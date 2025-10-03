# DEPRECATION NOTICE

GerritForge has decided to [change the license to BSL](https://gitenterprise.me/2025/09/30/re-licensing-gerritforge-plugins-welcome-to-gerrit-enterprise/)
therefore the Apache 2.0 version of this plugin is deprecated.
The recommended version of the multi-site plugin is on [GitHub](https://github.com/GerritForge/multi-site)
and the development continues on [GerritHub.io](https://review.gerrithub.io/admin/repos/GerritForge/multi-site,general).

# Persistent cache for Gerrit, based on ChronicleMap (DEPRECATED)

Non-blocking and super-fast on-disk cache libModule for [Gerrit Code Review](https://gerritcodereview.com)
based on [ChronicleMap on-disk implementation](https://github.com/OpenHFT/Chronicle-Map).

## How to build

This libModule is built like a Gerrit in-tree plugin, using Bazelisk. See the
[build instructions](src/main/resources/Documentation/build.md) for more details.


## Setup

* Install cache-chronicalmap module

Install the chronicle-map module into the `$GERRIT_SITE/lib` directory.

Add the cache-chroniclemap module to `$GERRIT_SITE/etc/gerrit.config` as follows:

```
[gerrit]
  installModule = com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheModule
```

Note that in order to run on JDK 17 (or newer) the following parameters needs to be added
to `$GERRIT_SITE/etc/gerrit.config`:

```
[container]
  javaOptions = --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED
  javaOptions = --add-exports=java.base/sun.nio.ch=ALL-UNNAMED
  javaOptions = --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED
  javaOptions = --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
  javaOptions = --add-opens=jdk.compiler/com.sun.tools.javac=ALL-UNNAMED
  javaOptions = --add-opens=java.base/java.lang=ALL-UNNAMED
  javaOptions = --add-opens=java.base/java.lang.reflect=ALL-UNNAMED
  javaOptions = --add-opens=java.base/java.io=ALL-UNNAMED
  javaOptions = --add-opens=java.base/java.util=ALL-UNNAMED
```

For further information and supported options, refer to [config](src/main/resources/Documentation/config.md)
documentation.

## Migration from H2 caches

You can check how to migrate from H2 to chronicle-map [here](src/main/resources/Documentation/migration.md).