# Tuning

Tuning chronicle-map correctly might be a daunting task:
How many entries does a particular cache instance need?
what is the average key and value for it?

Rather than leaving you only with the trial and error (or the guesswork)
approach, this module provides utilities to help you get started in the right
direction.

If you have not migrated to chronicle-map yet, then follow instructions on how
to analyze your existing H2 caches [here](#analyze-h2-caches).

In case you have already migrated to chronicle-map please follow instructions on
how to further tune existing .dat caches [here](#tune-chronicle-map-caches).

## Analyze H2 caches

Since chronicle-map is one of the first open-source alternatives to the H2
implementation, it is very likely that your Gerrit instance has been running
with the default H2 cache backend.

The idea is to read from the _actual_ H2 persisted files and output the
information that will be required to configure chronicle-map as an alternative.

You can do this _before_ installing cache-chroniclemap as a lib module so that
your Gerrit server will not need downtime. As follows:

* Drop `cache-chroniclemap.jar` file in the `plugins/` directory.
* Wait for the pluginLoader to acknowledge and load the new plugin. You will 
see an entry in the `error_log`:

```
INFO  com.google.gerrit.server.plugins.PluginLoader : Loaded plugin cache-chroniclemap
```

* You can now run an analysis on the current status of your H2 caches

```bash
ssh -p 29418 admin@<gerrit-server> cache-chroniclemap analyze-h2-caches
```

The result will be outputted on standard output in a git config format.
This is an example (the values are made up):

```
****************************
** Chronicle-map template **
****************************

[cache "diff_summary"]
	maxEntries = 101
	avgKeySize = 192
	avgValueSize = 1350
[cache "web_sessions"]
	maxEntries = 1
	avgKeySize = 68
	avgValueSize = 332
[cache "pure_revert"]
	maxEntries = 1
	avgKeySize = 112
	avgValueSize = 8
[cache "mergeability"]
	maxEntries = 101
	avgKeySize = 150
	avgValueSize = 8
[cache "diff"]
	maxEntries = 101
	avgKeySize = 188
	avgValueSize = 5035
[cache "persisted_projects"]
	maxEntries = 2
	avgKeySize = 88
	avgValueSize = 4489
[cache "accounts"]
	maxEntries = 5
	avgKeySize = 52
	avgValueSize = 505
```

Empty caches (if any) will not generate empty config stanzas, rather a warning
will be displayed on standard output.

For example:
```
WARN: Cache diff_intraline is empty, skipping
```

Please note that the generated configuration is not necessarily final and it
might still need adjustments:
* Since chronicle-map file size is pre-allocated, you might want to allow for
more entries.
* You might want account for uncertainty by specifying a `maxBloatFactor` greater
than 1.
* any other reason.

Once you gathered the information you wanted you might consider to remove the
plugin:

* Remove the jar from the `plugins` directory

```bash
rm plugins/cache-chroniclemap.jar
```

* Wait for the pluginLoader to acknowledge and unload the plugin. You will
see an entry in the `error_log`:

```
INFO  com.google.gerrit.server.plugins.PluginLoader : Unloading plugin cache-chroniclemap
```

## Auto-adjust Chronicle-map caches

If you have already migrated to chronicle-map then already have `.dat` caches
available under the `cache` directory, and you have provided suitable
configuration for the existing caches as explained in the [configuration](./config.md)
documentation.

However, situations might arise for which new caches will be created for which
no configuration has yet been provided: new persistent caches might be
introduced on new versions of Gerrit, or you might end-up using a plugin that
makes use of an additional cache, for example.

When this happens, you might have little or no idea of what values should be
provided for those caches, such as average key size and average value size, and
you have to rely on default values.

This plugin provides an SSH command that will help you analyze the current,
suboptimal, chronicle-map caches and migrate into new ones for which a more
realistic configuration is generated based on data.

* Symlink the `cache-chroniclemap.jar` file in the `plugins/` directory (from
  the `lib/` directory).
* Wait for the pluginLoader to acknowledge and load the new plugin. You will see
  an entry in the `error_log`:

```
INFO  com.google.gerrit.server.plugins.PluginLoader : Loaded plugin cache-chroniclemap
```

* You can now run an the tuning command:

```bash
ssh -p 29418 admin@<gerrit-server> cache-chroniclemap tune-chroniclemap-caches [--dry-run]
```

* --dry-run (Optional)

Calculate the average key and value size, but do not migrate current cache
data into new files

For each chronicle-map cache (i.e. `foo_1.dat` file) in the `cache` directory, a
new one will be created (i.e. `foo_1_tuned_<timestamp>.dat`).
The new cache will have these characteristics:
- Will have the same entries as the original cache.
- Will be configured with the *actual* average key size and values calculated by
  looking at the content of the original cache.

An output will also be generated with the new configuration that should be put
into `gerrit.config`, should you decide to use the new caches.

An example of the output is the following:

```bash
ssh -p 29418 admin@localhost cache-chroniclemap auto-adjust-caches
[mergeability] calculate average key/value size: 100% (849601/849601)
[diff_summary] calculate average key/value size: 100% (410894/410894)
[diff_intraline] calculate average key/value size: 100% (101868/101868)
[web_sessions] calculate average key/value size: 100% (1/1)
[conflicts] calculate average key/value size: 100% (364722/364722)
[diff] calculate average key/value size: 100% (72613/72613)
[accounts] calculate average key/value size: 100% (22614/22614)
[change_kind] calculate average key/value size: 100% (838009/838009)
[persisted_projects] calculate average key/value size: 100% (47385/47385)
[persisted_projects] migrate content: 100% (47385/47385)
****************************
** Chronicle-map template **
****************************

__CONFIG__
[cache "mergeability"]
        avgKeySize = 76
        avgValueSize = 5
        maxEntries = 3398404
        maxBloatFactor = 4
[cache "diff_summary"]
        avgKeySize = 96
        avgValueSize = 241
        maxEntries = 1643576
        maxBloatFactor = 4
[cache "diff_intraline"]
        avgKeySize = 503
        avgValueSize = 370
        maxEntries = 407472
        maxBloatFactor = 4
[cache "web_sessions"]
        avgKeySize = 41
        avgValueSize = 166
        maxEntries = 94852
        maxBloatFactor = 4
[cache "conflicts"]
        avgKeySize = 61
        avgValueSize = 5
        maxEntries = 1458888
        maxBloatFactor = 4
[cache "diff"]
        avgKeySize = 94
        avgValueSize = 571
        maxEntries = 290452
        maxBloatFactor = 4
[cache "accounts"]
        avgKeySize = 26
        avgValueSize = 90
        maxEntries = 90456
        maxBloatFactor = 4
[cache "change_kind"]
        avgKeySize = 55
        avgValueSize = 6
        maxEntries = 3352036
        maxBloatFactor = 4
[cache "persisted_projects"]
        avgKeySize = 49
        avgValueSize = 1770
        maxEntries = 189536
        maxBloatFactor = 4
```

The operation might take from seconds to minutes, depending on the size of the
caches and it could be performed periodically to assess how the cache data
evolves in respect to their current configuration.

Running the command against gerrithub data for an overall number of entries
of circa 3M, took ~2 mins (on a 2.6 GHz 6-Core Intel Core i7 with 16Gb or RAM).

Depending on the results you might find that the newly generated caches have
average key/value configurations that are substantially different from the
current ones. This might be just a by-product of how the Gerrit instance is
used, and of the different data that it generates (think about how the average
size of your diffs might change over time, for example).

You should consider replacing only those caches that have drifted away
considerably from the actual profile of the data they store (i.e. the values
currently in `gerrit.config` are substantially different from the output of
the `auto-adjust-caches` command).

Using the new caches requires things:
* Update the `gerrit.config` with the output produced by the command
* replace the existing caches with the new caches.
* restart gerrit

*Note*:
The `auto-adjust-caches` can be run online without any disruption of the Gerrit
server. However, note that since the migration perform many, sequential reads
from the cache, it will participate in the overall load of the system, so
you should test accordingly.

In an HA environment the tuning of the cache can be done on a single node and
then the caches can be copied over to other nodes.
For example, in a two nodes installation (gerrit-1 and gerrit-2):

- Run the `tune-chroniclemap-caches` on gerrit-2
- copy the `tuned` cache files to gerrit-1

For each cache `foo` you want to install/replace do:
1. Stop `gerrit-2`
2. replace the existing caches with the `tuned` ones.

```bash
  mv foo_1_tuned_<timestamp>.dat foo_1.dat
```

3. replace/add the `[cache "foo"]` stanza in the `gerrit.config`

```
  [cache "persisted_projects"]
  avgKeySize = 49
  avgValueSize = 1770
  maxEntries = 189536
  maxBloatFactor = 4
```
4. restart gerrit-2

Once you have tested gerrit-2 and you are happy with the results you can perform
steps *1.* to *4.* for `gerrit-1`.