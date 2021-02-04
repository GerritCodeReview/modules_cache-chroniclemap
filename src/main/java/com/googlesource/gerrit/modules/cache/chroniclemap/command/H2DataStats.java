package com.googlesource.gerrit.modules.cache.chroniclemap.command;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class H2DataStats {
  public abstract long size();

  public abstract long avgKeySize();

  public abstract long avgValueSize();

  public static H2DataStats create(long size, long avgKeySize, long avgValueSize) {
    return new AutoValue_H2DataStats(size, avgKeySize, avgValueSize);
  }

  public static H2DataStats empty() {
    return new AutoValue_H2DataStats(0L, 0L, 0L);
  }

  public boolean isEmpty() {
    return size() == 0L;
  }
}
