## Migration from H2 Caches

This module provides an HTTP endpoiny to help converting existing cache from H2 to
chronicle-map, which requires the `Administrate Server` capability to be
executed.

The migration must be executed _before_ switching to use chronicle-map, while
Gerrit cache is still backed by H2.

The migration can be run online without any disruption of the Gerrit server.
However note that since the migration perform many, sequential reads from the H2
database, it will participate to the overall database load, so you should test
accordingly.

The migration should be performed as follows:

* Copy `cache-chroniclemap.jar` file in the `plugins/` directory.
* Wait for the pluginLoader to load the new plugin. You will see an entry in
the `error_log`:

```
INFO  com.google.gerrit.server.plugins.PluginLoader : Loaded plugin cache-chroniclemap
```

* You can now run the migration

```bash
curl -n 'http://localhost:8080/config/server/cache-chroniclemap~migrate?size-multiplier=FACTOR&bax-bloat-factor=MULTIPLIER'
```

This might require some time, depending on the size of the H2 caches and it will
terminate with the output of the configuration that should be places in
`etc/gerrit.config`in order to leverage the newly created caches correctly.

Output example:

```
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
    [cache "web_sessions"]
	maxEntries = 94852
	avgKeySize = 68
	avgValueSize = 382
	maxBloatFactor = 4
```

Optionally the HTTP command can receive the following additional arguments:

* --max-bloat-factor (-m) FACTOR

maximum number of times chronicle-map cache is allowed to grow in size.
*default:3*

*  --size-multiplier (-s) MULTIPLIER
Multiplicative factor for the number of entries allowed in chronicle-map.
*default:3*