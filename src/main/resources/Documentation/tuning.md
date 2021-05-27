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

## Tune Chronicle-map caches

If you have already migrated to chronicle-map then already have `.dat` caches
available under the `cache` directory and you have provided suitable
configuration for the existing caches as explained in the [configuration](./config.md)
documentation.

However, situations might arise for which new caches will be created for which
no configuration has yet been provided: new persistent caches might be
introduced on new versions of Gerrit, or you might end-up using a plugin that
makes use of an additional cache, for example.

When this happens, you might little or no idea of what values should be provided
for those caches, such as average key size and average value size and you might
endup just relying on default values.

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
ssh -p 29418 admin@<gerrit-server> cache-chroniclemap tune-chroniclemap-caches
```

For each chronicle-map cache (i.e. `foo_1.dat` file) in the `cache` directory, a
new one will be created (i.e. `foo_1_tuned_<timestamp>.dat`).
The new cache will have these characteristics:
- Will have the same entries as the original cache.
- Will be configured with the *actual* average key size and values calculated by
  looking at the content of the original cache.

An output will also be generated with the new configuration that should be put
into `gerrit.config`, should you decide to use the new caches.

An example of the output is the following:

```
➜ ssh -p 29418 admin@localhost  cache-chroniclemap tune-chroniclemap-caches
[mergeability] average key size: 100% (11552/11552)
[mergeability] average value size: 100% (11552/11552)
[diff_summary] average key size: 100% (78674/78674)
[diff_summary] average value size: 100% (78674/78674)
[diff_intraline] average key size: 100% (5057/5057)
[diff_intraline] average value size: 100% (5057/5057)
[web_sessions] average key size: 100% (125/125)57)
[web_sessions] average value size: 100% (125/125)
[conflicts] average key size: 100% (3989/3989)
[conflicts] average value size: 100% (3989/3989)
[diff] average key size: 100% (85209/85209)9)
[diff] average value size: 100% (85209/85209)
[accounts] average key size: 100% (658/658)
[accounts] average value size: 100% (658/658)
[git_tags] average key size: 100% (740/740)
[git_tags] average value size: 100% (740/740)
[change_kind] average key size: 100% (101705/101705)
[change_kind] average value size: 100% (101705/101705)
[persisted_projects] average key size: 100% (47712/47712)
[persisted_projects] average value size: 100% (47712/47712)
[persisted_projects] migrate content: 100% (47712/47712)
****************************
** Chronicle-map template **
****************************

__CONFIG__
[cache "mergeability"]
	avgKeySize = 76
	avgValueSize = 4
	maxEntries = 3500000
	maxBloatFactor = 3
[cache "diff_summary"]
	avgKeySize = 96
	avgValueSize = 200
	maxEntries = 2000000
	maxBloatFactor = 3
[cache "diff_intraline"]
	avgKeySize = 502
	avgValueSize = 152
	maxEntries = 450000
	maxBloatFactor = 3
[cache "web_sessions"]
	avgKeySize = 41
	avgValueSize = 185
	maxEntries = 100000
	maxBloatFactor = 3
[cache "conflicts"]
	avgKeySize = 65
	avgValueSize = 4
	maxEntries = 1500000
	maxBloatFactor = 3
[cache "diff"]
	avgKeySize = 94
	avgValueSize = 849
	maxEntries = 300000
	maxBloatFactor = 3
[cache "accounts"]
	avgKeySize = 26
	avgValueSize = 209
	maxEntries = 100000
	maxBloatFactor = 3
[cache "git_tags"]
	avgKeySize = 26
	avgValueSize = 2969
	maxEntries = 10000
	maxBloatFactor = 3
[cache "change_kind"]
	avgKeySize = 55
	avgValueSize = 9
	maxEntries = 3600000
	maxBloatFactor = 3
[cache "persisted_projects"]
	avgKeySize = 48
	avgValueSize = 1764
	maxEntries = 200000
	maxBloatFactor = 3
```

The operation might take from seconds to minutes, depending on the size of the
caches.

Using the new caches requires things:
* Update the `gerrit.config` with the output produced by the command
* replace the existing caches with the new caches.
* restart gerrit