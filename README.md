# Persistent cache for Gerrit, based on ChronicleMap

Non-blocking and super-fast on-disk cache libModule for [Gerrit Code Review](https://gerritcodereview.com)
based on [ChronicleMap on-disk implementation](https://github.com/OpenHFT/Chronicle-Map).

## Why yet another persistent cache backend?

The default persistent cache backend in Gerrit is H2, which is good enough for
most setups. When the size and cardinality of the data grows, the H2 storage may
be too unreliable and subject to long waits for its internal reorganisation and
auto-vacuum processing.

The main points of improvements and design principles of the cache-chroniclemap backend are:
- Not requiring an external service
- Rely on the local filesystem, exactly as with H2
- Allow smooth migration of H2 data into the cache-chroniclemap storage
- Optimised for low-latency and concurrency

The drawbacks of cache-chroniclemap is relying on a static file allocation, which requires
planning and adjustments by the Gerrit administrator.

Cache-chroniclemap provides both the persistent cache implementation and the associated
tooling as SSH commands for:
- Storage resize
- Auto-tuning of the optimal key/value sizes from current data stored in the cache
- LRU eviction

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