package com.googlesource.gerrit.modules.cache.chroniclemap;

public interface CachedValue<V> {
  boolean isValid();
}
