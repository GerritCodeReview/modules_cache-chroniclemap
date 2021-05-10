// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.googlesource.gerrit.modules.cache.chroniclemap;

import com.google.gerrit.auth.oauth.OAuthTokenCache;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.httpd.WebSessionManager;
import com.google.gerrit.server.account.CachedAccountDetails;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.BooleanCacheSerializer;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.EnumCacheSerializer;
import com.google.gerrit.server.cache.serialize.JavaCacheSerializer;
import com.google.gerrit.server.cache.serialize.ProtobufSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.gerrit.server.change.MergeabilityCacheImpl;
import com.google.gerrit.server.git.TagSetHolder;
import com.google.gerrit.server.notedb.ChangeNotesCache;
import com.google.gerrit.server.notedb.ChangeNotesState;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.IntraLineDiffKey;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.query.change.ConflictKey;
import com.google.inject.Singleton;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class CacheSerializers {
  // TODO: Add other caches, such as
  //  comment_context_5.dat
  //  gerrit_file_diff_4.dat
  //  git_file_diff_0.dat
  //  git_modified_files_1.dat
  //  modified_files_1.dat

  // XXX:
  // 1 - Some serializers are NOT public. How do we refer to them here?
  // 2 - Plugins can add persisted caches, how can a static list such this one cope with caches that
  // is not aware of?
  private static final Map<String, CacheSerializer<?>> keySerializers =
      Stream.of(
              new AbstractMap.SimpleEntry<>("web_sessions", StringCacheSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>(
                  "accounts", CachedAccountDetails.Key.Serializer.INSTANCE),
              new AbstractMap.SimpleEntry<>(
                  "oauth_tokens", OAuthTokenCache.AccountIdSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>(
                  "change_kind", new ChangeKindCacheImpl.Key.Serializer()),
              new AbstractMap.SimpleEntry<>(
                  "mergeability", MergeabilityCacheImpl.EntryKey.Serializer.INSTANCE),
              new AbstractMap.SimpleEntry<>(
                  "pure_revert", new ProtobufSerializer<>(Cache.PureRevertKeyProto.parser())),
              new AbstractMap.SimpleEntry<>("git_tags", StringCacheSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>(
                  "change_notes", ChangeNotesCache.Key.Serializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("diff", new JavaCacheSerializer<PatchListKey>()),
              new AbstractMap.SimpleEntry<>(
                  "diff_intraline", new JavaCacheSerializer<IntraLineDiffKey>()),
              new AbstractMap.SimpleEntry<>(
                  "diff_summary", new JavaCacheSerializer<DiffSummaryKey>()),
              new AbstractMap.SimpleEntry<>(
                  "persisted_projects",
                  new ProtobufSerializer<>(Cache.ProjectCacheKeyProto.parser())),
              new AbstractMap.SimpleEntry<>(
                  "groups_byuuid_persisted",
                  new ProtobufSerializer<>(Cache.GroupKeyProto.parser())),
              new AbstractMap.SimpleEntry<>("conflicts", ConflictKey.Serializer.INSTANCE))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

  private static final Map<String, CacheSerializer<?>> valueSerializers =
      Stream.of(
              new AbstractMap.SimpleEntry<>(
                  "web_sessions", new JavaCacheSerializer<WebSessionManager.Val>()),
              new AbstractMap.SimpleEntry<>("accounts", CachedAccountDetails.Serializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("oauth_tokens", new OAuthTokenCache.Serializer()),
              new AbstractMap.SimpleEntry<>(
                  "change_kind", new EnumCacheSerializer<>(ChangeKind.class)),
              new AbstractMap.SimpleEntry<>("mergeability", BooleanCacheSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("pure_revert", BooleanCacheSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("git_tags", TagSetHolder.Serializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("change_notes", ChangeNotesState.Serializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("diff", new JavaCacheSerializer<PatchListKey>()),
              new AbstractMap.SimpleEntry<>(
                  "diff_intraline", new JavaCacheSerializer<IntraLineDiffKey>()),
              new AbstractMap.SimpleEntry<>(
                  "diff_summary", new JavaCacheSerializer<DiffSummaryKey>()),
              new AbstractMap.SimpleEntry<>(
                  "persisted_projects", ProjectCacheImpl.PersistedProjectConfigSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>(
                  "groups_byuuid_persisted",
                  GroupCacheImpl.PersistedInternalGroupSerializer.INSTANCE),
              new AbstractMap.SimpleEntry<>("conflicts", BooleanCacheSerializer.INSTANCE))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

  @SuppressWarnings("unchecked")
  public static <K> CacheSerializer<K> getKeySerializer(String name) {
    if (keySerializers.containsKey(name)) {
      return (CacheSerializer<K>) keySerializers.get(name);
    }
    throw new IllegalStateException("Could not find key serializer for " + name);
  }

  @SuppressWarnings("unchecked")
  public static <V> CacheSerializer<V> getValueSerializer(String name) {
    if (valueSerializers.containsKey(name)) {
      return (CacheSerializer<V>) valueSerializers.get(name);
    }
    throw new IllegalStateException("Could not find value serializer for " + name);
  }
}
