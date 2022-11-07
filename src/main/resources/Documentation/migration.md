## Migration from H2 Caches

This module provides a REST API to help converting existing cache from H2 to
chronicle-map, which requires the `Administrate Caches` or `Administrate Server`
capabilities to be executed.

The migration must be executed _before_ switching to use chronicle-map, while
Gerrit cache is still backed by H2.

The migration can be run online without any disruption of the Gerrit server.
However note that since the migration perform many, sequential reads from the H2
database, it will participate to the overall database load, so you should test
accordingly.

The migration would do the following:
1. scan all existing cache key-value pairs
2. calculate the parameters for the new cache, if not already defined in gerrit.config.
3. create the new cache
4. read all existing key-value pairs and insert them into the new cache-chroniclemap files

> **NOTE**: The existing cache parameters are kept in `gerrit.config` only when they are all
> defined (avgKeySize, avgValueSize, maxEntries and maxBloatFactor), otherwise the
> migration process will recalculate them and create the new cache based on the new
> values.

The following caches will be migrated (if they exist and contain any data):

* accounts
* change_kind
* change_notes
* conflicts
* diff
* diff_intraline
* diff_summary
* git_tags
* mergeability
* oauth_token
* persisted_projects
* pure_revert
* web_sessions

The migration should be performed as follows:

* Copy `cache-chroniclemap.jar` file in the `plugins/` directory.
* Wait for the pluginLoader to load the new plugin. You will see an entry in
the `error_log`:

```
INFO  com.google.gerrit.server.plugins.PluginLoader : Loaded plugin cache-chroniclemap
```

* You can now run the migration

```bash
curl -v -XPUT -u <admin> '<gerrit>/a/plugins/cache-chroniclemap/migrate?[size-multiplier=FACTOR]&[bax-bloat-factor=MULTIPLIER]'
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
   	maxBloatFactor = 1
```

Optionally the REST endpoint can receive the following additional arguments:

* max-bloat-factor=FACTOR

maximum number of times chronicle-map cache is allowed to grow in size.
*default:3*

* size-multiplier=MULTIPLIER
Multiplicative factor for the number of entries allowed in chronicle-map.
*default:3*


### Reloading plugins with persistent caches backed by chroniclemap

When chroniclemap store is initiated for a cache it locks exclusively the
underlying file and keeps it until store is closed. Store is closed when Gerrit
is stopped (for core caches) or when plugin is unloaded through either REST or
SSH command. The later is problematic from the plugin `reload` command
perspective as by default it unloads old version of plugin once new version is
successfully loaded. Considering that old version holds the lock until it gets
unloaded new version load will not succeed. As a result the following (or
similar) error is visible in the log:

```
[2022-08-31T17:37:56.481+02:00] [SSH gerrit plugin reload test-cache-plugin (admin)] WARN  com.google.gerrit.server.plugins.PluginLoader : Cannot reload plugin test-cache-plugin
com.google.inject.CreationException: Unable to create injector, see the following errors:

1) [Guice/ErrorInCustomProvider]: ChronicleHashRecoveryFailedException: ChronicleFileLockException: Unable to acquire an exclusive file lock for gerrit/cache/test-cache-plugin.test_cache_0.dat. Make sure no other process is using the map.
  at CacheModule.bindCache(CacheModule.java:188)
      \_ installed by: Module -> TestCache$1
  while locating Cache<String, String> annotated with @Named(value="test_cache")

Learn more:
  https://github.com/google/guice/wiki/ERROR_IN_CUSTOM_PROVIDER
Caused by: ChronicleHashRecoveryFailedException: ChronicleFileLockException: Unable to acquire an exclusive file lock for gerrit/cache/test-cache-plugin.test_cache_0.dat. Make sure no other process is using the map.
  at ChronicleMapBuilder.openWithExistingFile(ChronicleMapBuilder.java:1937)
  at ChronicleMapBuilder.createWithFile(ChronicleMapBuilder.java:1706)
  at ChronicleMapBuilder.recoverPersistedTo(ChronicleMapBuilder.java:1622)
  ...
```

The following steps can be used in order to perform reload operation:

1. perform the plugin `reload` in two steps by calling `remove` first and
   following it with `add` command - the easiest way that doesn't require any
   code modification

2. add `Gerrit-ReloadMode: restart` to plugin's manifest so the when the plugin
   `reload` command is called Gerrit unloads the old version prior loading the
   new one - requires plugin's sources modification and build which might be
   not an option in certain cases