Configuration
=============

Global configuration of the cache-chroniclemap libModule is done in the `gerrit.config` file in the
site's etc directory.

Information about gerrit caches mechanism can be found in the relevant
[documentation section](https://charm.cs.illinois.edu/gerrit/Documentation/config-gerrit.html#cache).

Chronicle-map supports most of the cache configuration parameters, such as:

* `maxAge`: Maximum age to keep an entry in the cache.
[Gerrit docs](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache.name.maxAge)

* `refreshAfterWrite`: Duration after which we asynchronously refresh the cached value.
[Gerrit docs](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache.name.refreshAfterWrite)

* `diskLimit`: Total size in bytes of the keys and values stored on disk.
Defaults are per-cache and can be found in the relevant documentation:
[Gerrit docs](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache.name.diskLimit)

  *NOTE*: a per gerrit documentation, a positive value is required to enable disk
  storage for the cache. However, the provided value cannot be used to limit the
  size of the file, since that is the result of chronicle-map pre-allocation and
  it is a function of the number of entries, average sizes and bloat factor,
  rather than the number of values stored in it.

Chronicle-map implementation however might require some additional configuration

## Configuration parameters

```cache.<name>.avgKeySize```
:   The average number of bytes to be allocated for the key of this cache.
If key is a boxed primitive type, a value interface or Byteable subclass, i. e.
if key size is known statically, it is automatically accounted by chronicle-map.
In this case, this value will be ignored.

[Official docs](
https://www.javadoc.io/doc/net.openhft/chronicle-map/3.8.0/net/openhft/chronicle/map/ChronicleMapBuilder.html#averageKeySize-double-
)

```cache.<name>.avgValueSize```
:   The average number of bytes to be allocated for a value of this cache.
If key is a boxed primitive type, a value interface or Byteable subclass, i. e.
if key size is known statically, it is automatically accounted by chronicle-map.
In this case, this value will be ignored.

[Official docs](
https://www.javadoc.io/doc/net.openhft/chronicle-map/3.8.0/net/openhft/chronicle/map/ChronicleMapBuilder.html#averageValueSize-double-
)

```cache.<name>.maxEntries```
: The number of entries that this cache is going to hold, _at most_.
The actual number of entries needs to be less or equal to this value.

[Official docs](
https://www.javadoc.io/doc/net.openhft/chronicle-map/3.8.0/net/openhft/chronicle/map/ChronicleMapBuilder.html#entries-long-
)

```cache.<name>.maxBloatFactor```
: the maximum number of times this cache is allowed to grow in size beyond the
configured target number of entries.

Set this value to the theoretical maximum of stored entries, divided by the
configured entries.

Chronicle Map will allocate memory until the actual number of entries inserted
divided by the number configured through `maxEntries` is not
higher than the configured `maxBloatFactor`.

Chronicle Map works progressively slower when the actual size grows far beyond
the configured size, so the maximum possible maxBloatFactor() is artificially
limited to 1000. Default: *1*

[Official docs](
https://www.javadoc.io/doc/net.openhft/chronicle-map/3.8.0/net/openhft/chronicle/hash/ChronicleHashBuilder.html#maxBloatFactor-double-
)

* `cache.<name>.percentageFreeSpaceEvictionThreshold`
: The percentage of free space in the last available expansion of chronicle-map
beyond which cold cache entries will start being evicted.

Since the eviction routine is scheduled as background task every 30 seconds,
this value should always be < 100. This is to allow for additional entries to be
inserted into the cache between the execution of two eviction runs.

How much that margin is, depends on how fast the cache can increase between two
eviction runs: caches that populate more quickly might need a lower value, and
vice-versa.

Default: *90*

* `cache.persistIndexEvery`
: Duration (in seconds if not specified) between caches keys index persist
operations.

Note that in order to avoid race condition between evict and persist operations
the latter is performed after the former is finished. Therefore the lowest
persist resolution is 30s. Smaller values will be rounded up to 30s whereas
higher values will be rounded down (if needed) to the closest multiple of 30s.
From practical point of view it means that persist operation will be performed
after every n-th evict operation is finished.
For instance if `cache.persistIndexEvery = 2m` then persist will be called
after every 4th eviction is finished.

Notes:
* regardless of `cache.persistIndexEvery` persist will be called on
  Gerrit termination unless there is one already in progress.
* persist operation is scheduled in a dedicated thread ('ChronicleMap-Index-0')

Default: *15m*

### Defaults

Unless overridden by configuration, sensible default values are be provided for
[standard caches](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache_names).

Please note that even though defaults allow an out-of-the-box usage of the
chronicle-map cache, they are not necessarily suitable for a production
environment.

A deep understanding of your Gerrit data is crucial to tune each cache
accordingly. Please refer to the [configuration](#configuration-parameters) to
understand how to choose sensible values.

These defaults have been retrieved by observing _actual_ key and value sizes as
explained in the
[official documentation](https://github.com/OpenHFT/Chronicle-Map/blob/master/docs/CM_Tutorial_Behaviour.adoc#example---monitor-chronicle-map-statistics)

They are based on the assumption that your Gerrit instance will not have more
than 1000 accounts overall, logged-in at the same time and no more than 1000
medium sized changes.

Information on which values each cache is actually initialized with, can be found
in the `error_log` at startup, in particular two values are also logged to help
gather important statistics about the current file cache status:

* `remainingAutoResizes`
: the number of times in the future the cache can automatically expand its capacity.
The limit to the number of times the map can expand is set via the `maxBloatFactor`.
if `remainingAutoResizes` drops to zero,this cache is no longer able to expand
and it will not be able to take more entries, failing with a `IllegalStateException`
[official documentation](https://javadoc.io/static/net.openhft/chronicle-map/3.20.83/net/openhft/chronicle/map/ChronicleMap.html#remainingAutoResizes--)

* `percentageFreeSpace`
: the amount of free space in the cache as a percentage. When the free space gets
 low ( around 5% ) the cache will automatically expand (see `remainingAutoResizes`).
 If the cache expands you will see an increase in the available free space.
[official documentation](https://javadoc.io/static/net.openhft/chronicle-map/3.20.83/net/openhft/chronicle/map/ChronicleMap.html#percentageFreeSpace--)

*Constraints*: [1-99]
*Default*: 50

These are the provided default values:

* `web_sessions`:
    * `avgKeySize`: 45 bytes
    * `avgValueSize`: 221 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 1

Allows up to 1000 users to be logged in.

* `change_notes`:
    * `avgKeySize`: 36 bytes
    * `avgValueSize`: 10240 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 2

Allow for a dozen review activities (votes, comments of medium length) to up to
1000 operations. maxBloatFactor allows to go twice over this threshold.

* `accounts`:
    * `avgKeySize`: 30 bytes
    * `avgValueSize`: 256 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 1

Allows to cache up to 1000 details of active users, including their display name,
preferences, mail, etc.

* `diff`:
    * `avgKeySize`: 98 bytes
    * `avgValueSize`: 10240 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 3

Allow for up to 1000 medium sized diffs between two commits to be cached.
maxBloatFactor allows to go three times over this threshold.

* `diff_intraline`:
    * `avgKeySize`: 512 bytes
    * `avgValueSize`: 2048 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 2

Allow for up to 1000 medium sized diffs between two files to be cached.
maxBloatFactor allows to go twice over this threshold.

* `external_ids_map`:
    * `avgKeySize`: 24 bytes
    * `avgValueSize`: 204800 bytes
    * `maxEntries`: 2
    * `maxBloatFactor`: 1

This cache holds a map of the parsed representation of all current external IDs.
It may temporarily contain 2 entries, but the second one is promptly expired.
This defaults allow to contain up to 1000 entries per map, roughly.

* `oauth_tokens`:
    * `avgKeySize`: 8 bytes
    * `avgValueSize`: 2048 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 1

caches information about the operation performed by a change relative to its
parent. Allow to cache up to 1000 entries.

* `mergeability`:
    * `avgKeySize`: 79 bytes
    * `avgValueSize`: 16 bytes
    * `maxEntries`: 65000
    * `maxBloatFactor`: 2

Caches information about the mergeability status of up to 1000 open changes.

* `pure_revert`:
    * `avgKeySize`: 55 bytes
    * `avgValueSize`: 16 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 1

Caches the result of checking if one change or commit is a pure/clean revert of
another, for up to 1000 entries.

* `persisted_projects`:
    * `avgKeySize`: 128 bytes
    * `avgValueSize`: 1024 bytes
    * `maxEntries`: 250
    * `maxBloatFactor`: 2

Caches the project description records from the refs/meta/config branch of each
project. Allow up to 250 projects to be cached.
maxBloatFactor allows to go twice over this threshold.

* `conflicts`:
    * `avgKeySize`: 70 bytes
    * `avgValueSize`: 16 bytes
    * `maxEntries`: 1000
    * `maxBloatFactor`: 1

Caches whether two commits are in conflict with each other.
Allows to hold up to 1000 conflicting changes.

* `GENERAL DEFAULTS`:

Caches that do not provide specific configuration in the `[cache "<name>"]`
stanza and are not listed above, will fallback to use generic defaults:

* `avgKeySize`: 128 bytes
* `avgValueSize`: 2048 bytes
* `maxEntries`: 1000
* `maxBloatFactor`: 1

### Gotchas

#### Configuration changes and persisted cache file

When reading the persisted cache file, chronicle-map assumes the configuration
that was used to create the file for the first time.
This means, that changing the configuration is not going to update entries,
average key size and average value size as your needs grow.

If you need more entries, or different key values, you'll need to generate a
brand new persistent cache (i.e. delete the old one).

More information on recovery can be found in the
[Official documentation](https://github.com/OpenHFT/Chronicle-Map/blob/master/docs/CM_Tutorial.adoc#recovery)

### Tuning

This module provides tooling to help understand how configuration should be
optimized for chronicle-map.
More information in the [tuning](tuning.md) documentation.
