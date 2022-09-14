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

* cache/chroniclemap/max_autoresizes_<cache-name>
  : The maximum number of times the cache can automatically expand its capacity.

* cache/chroniclemap/keys_index_size_<cache-name>
  : The number of index keys for the cache that are currently in memory.

* cache/chroniclemap/keys_index_add_latency_<cache-name>
  : The latency of adding cache key to an index.

* cache/chroniclemap/keys_index_remove_and_consume_older_than_latency_<cache-name>
  : The latency of removing and consuming all keys older than expiration time for an index.

* cache/chroniclemap/keys_index_remove_lru_key_latency_<cache-name>
  : The latency of removing and consuming LRU key from an index.

* cache/chroniclemap/keys_index_restore_latency_<cache-name>
  : The latency of restoring an index from a file (performed once during the plugin start).

* cache/chroniclemap/keys_index_persist_latency_<cache-name>
  : The latency of persisting an index to a file.

* cache/chroniclemap/store_serialize_latency_<cache-name>
  : The latency of serializing entries in chronicle-map store

* cache/chroniclemap/store_deserialize_latency_<cache-name>
  : The latency of deserializing entries from chronicle-map store

* cache/chroniclemap/store_put_failures_<cache-name>
  : The number of errors caught when inserting entries in chronicle-map store

* cache/chroniclemap/keys_index_restore_failures_<cache-name>
  : The number of errors caught when restore cache index from file operation was performed

* cache/chroniclemap/keys_index_persist_failures_<cache-name>
  : The number of errors caught when persist cache index to file operation was performed

* cache/chroniclemap/caches_without_chroniclemap_configuration
  : The number of caches that have no chronicle map configuration provided and fall back to defaults

* cache/chroniclemap/cache_without_chroniclemap_configuration_<cache-name>
  : It will be created only for cache that has no dedicated chronicle-map configuration and falls
    back to defaults and in such case it will have value 1.