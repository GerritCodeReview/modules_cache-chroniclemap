Metrics
=============

In addition to the [usual metrics](https://gerrit-review.googlesource.com/Documentation/metrics.html#_caches)
exposed by caches, chronicle-map emits additional metrics that might be useful
to monitor the state of the cache:

* cache/chroniclemap/percentagae_free_space_<cache-name>
  : the amount of free space left in the cache as a percentage.

  See the [official documentation](https://javadoc.io/static/net.openhft/chronicle-map/3.20.83/net/openhft/chronicle/map/ChronicleMap.html#percentageFreeSpace--)
  for more information.

* cache/chroniclemap/remaining_autoresizes_<cache-name>
  : the number of times the cache can automatically expand its capacity.

  See the [official documentation](https://javadoc.io/static/net.openhft/chronicle-map/3.20.83/net/openhft/chronicle/map/ChronicleMap.html#remainingAutoResizes--)
  for more information.

* cache/chroniclemap/hot_keys_capacity_<cache-name>
  : Constant number of hot keys for the cache that can be kept in memory.

* cache/chroniclemap/hot_keys_size_<cache-name>
  : The number of hot keys for the cache that are currently in memory.