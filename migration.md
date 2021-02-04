## Migration

This module provides an SSH command to help migrating from H2 to chronicle-map.

The migration should be executed _before_ switching to use chronicle-map, while
Gerrit cache is still backed by H2.

The migration can be run online without any disruption of the Gerrit server.
However note that since the migration perform many, sequential reads from the H2
database, it will participate to the overall database load, so you should test
accordingly.

The migration should be performed as follows:

* Drop `cache-chroniclemap.jar` file in the `plugins/` directory.
* Wait for the pluginLoader to acknowledge and load the new plugin. You will
see an entry in the `error_log`:

```
INFO  com.google.gerrit.server.plugins.PluginLoader : Loaded plugin cache-chroniclemap
```

* You can now run the migration

```bash
ssh -p 29418 admin@<gerrit-server> cache-chroniclemap migrate-h2-caches \
    [--max-bloat-factor FACTOR] \
    [--size-multiplier MULTIPLIER]
```

This might require some time, depending on the size of the H2 caches and it will
terminate with the output of the configuration that should be places in
`etc/gerrit.config`in order to leverage the newly created caches correctly.

For example:

```Migrating H2 caches to Chronicle-Map...
   * Size multiplier: 1
   * Max Bloat Factor: 1
   [diff]:                 100% (216/216)
   [persisted_projects]:   100% (3/3)
   [diff_summary]:         100% (216/216)
   [accounts]:             100% (2/2)
   [mergeability]:         100% (2444/2444)
   Complete!

   ****************************
   ** Chronicle-map template **
   ****************************

   [cache "diff"]
   	maxEntries = 216
   	avgKeySize = 188
   	avgValueSize = 796
   	maxBloatFactor = 1
   [cache "persisted_projects"]
   	maxEntries = 3
   	avgKeySize = 80
   	avgValueSize = 4087
   	maxBloatFactor = 1
   [cache "diff_summary"]
   	maxEntries = 216
   	avgKeySize = 192
   	avgValueSize = 254
   	maxBloatFactor = 1
   [cache "accounts"]
   	maxEntries = 2
   	avgKeySize = 52
   	avgValueSize = 194
   	maxBloatFactor = 1
   [cache "mergeability"]
   	maxEntries = 2444
   	avgKeySize = 150
   	avgValueSize = 20
   	maxBloatFactor = 1
```

Optionally the SSH command can receive the following additional arguments:

* --max-bloat-factor (-m) FACTOR

maximum number of times chronicle-map cache is allowed to grow in size.
*default:3*

*  --size-multiplier (-s) MULTIPLIER
Multiplicative factor for the number of entries allowed in chronicle-map.
*default:3*