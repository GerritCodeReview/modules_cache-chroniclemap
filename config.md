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

```cache.<name>.entries```
: The number of entries that this cache is going to hold, _at most_

[Official docs](
https://www.javadoc.io/doc/net.openhft/chronicle-map/3.8.0/net/openhft/chronicle/map/ChronicleMapBuilder.html#entries-long-
)

```cache.<name>.maxBloatFactor```
: the maximum number of times this cache is allowed to grow in size beyond the
configured target number of entries.

Chronicle Map will allocate memory until the actual number of entries inserted
divided by the number configured through ChronicleMapBuilder.entries() is not
higher than the configured `maxBloatFactor`.

Chronicle Map works progressively slower when the actual size grows far beyond
the configured size, so the maximum possible maxBloatFactor() is artificially
limited to 1000. Default: *1*

[Official docs](
https://www.javadoc.io/doc/net.openhft/chronicle-map/3.8.0/net/openhft/chronicle/hash/ChronicleHashBuilder.html#maxBloatFactor-double-
)

## Tuning utilities

this module provide tooling to help you understand what configuration should be
suitable for chronicle-map.
More information in the [tuning](tuning.md) documentation.