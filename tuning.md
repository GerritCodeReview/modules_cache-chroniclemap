# Tuning

Tuning chronicle-map correctly might be a daunting task:
How many entries does a particular cache instance need?
what is the average key and value for it?

Rather than leaving you only with the trial and error (or the guesswork)
approach, this module provides a utility to help you get started in the right
direction.

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
	entries = 101
	avgKeySize = 192
	avgValueSize = 1350
[cache "web_sessions"]
	entries = 1
	avgKeySize = 68
	avgValueSize = 332
[cache "pure_revert"]
	entries = 1
	avgKeySize = 112
	avgValueSize = 8
[cache "mergeability"]
	entries = 101
	avgKeySize = 150
	avgValueSize = 8
[cache "diff"]
	entries = 101
	avgKeySize = 188
	avgValueSize = 5035
[cache "persisted_projects"]
	entries = 2
	avgKeySize = 88
	avgValueSize = 4489
[cache "accounts"]
	entries = 5
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

