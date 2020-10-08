package com.googlesource.gerrit.modules.cache.chroniclemap;

public final class Invalid<V> implements CachedValue<V> {
  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    return true;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
