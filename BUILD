load("//tools/bzl:junit.bzl", "junit_tests")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "cache-chroniclemap",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-Module: com.googlesource.gerrit.modules.cache.chroniclemap.CapabilityModule",
        "Gerrit-SshModule: com.googlesource.gerrit.modules.cache.chroniclemap.SSHCommandModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.modules.cache.chroniclemap.HttpModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "//lib:h2",
        "//lib/commons:io",
        "//proto:cache_java_proto",
        "@chronicle-algo//jar",
        "@chronicle-bytes//jar",
        "@chronicle-core//jar",
        "@chronicle-map//jar",
        "@chronicle-threads//jar",
        "@chronicle-values//jar",
        "@chronicle-wire//jar",
        "@commons-lang3//jar",
        "@dev-jna//jar",
        "@error-prone-annotations//jar",
        "@javapoet//jar",
        "@jna-platform//jar",
    ],
)

junit_tests(
    name = "cache-chroniclemap_tests",
    srcs = glob(
        ["src/test/java/**/*Test.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":cache-chroniclemap__plugin",
        ":chroniclemap-test-lib",
        "@chronicle-bytes//jar",
    ],
)

[junit_tests(
    name = f[:f.index(".")].replace("/", "_"),
    srcs = [f],
    tags = ["server"],
    deps = [
        ":cache-chroniclemap__plugin",
        ":chroniclemap-test-lib",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/server/cache/serialize",
        "//proto:cache_java_proto",
        "@chronicle-bytes//jar",
    ],
) for f in glob(["src/test/java/**/*IT.java"])]

java_library(
    name = "chroniclemap-test-lib",
    testonly = True,
    srcs = ["src/test/java/com/googlesource/gerrit/modules/cache/chroniclemap/TestPersistentCacheDef.java"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS,
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS,
)
