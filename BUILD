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
        "Gerrit-SshModule: com.googlesource.gerrit.modules.cache.chroniclemap.command.SSHCommandModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "//lib:h2",
        "//lib/commons:io",
        "@chronicle-algo//jar",
        "@chronicle-bytes//jar",
        "@chronicle-core//jar",
        "@chronicle-map//jar",
        "@chronicle-threads//jar",
        "@chronicle-values//jar",
        "@chronicle-wire//jar",
        "@dev-jna//jar",
        "@javapoet//jar",
        "@jna-platform//jar",
        "//java/com/google/gerrit/server/cache/h2",
    ],
)

junit_tests(
    name = "cache-chroniclemap_tests",
    srcs = glob(
        ["src/test/java/**/*Test.java"],
    ),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":cache-chroniclemap__plugin",
        "@chronicle-bytes//jar",
        ":chroniclemap-test-lib",
    ],
)

acceptance_tests(
    srcs = glob(["src/test/java/**/*IT.java"]),
    group = "server_cache",
    labels = ["server"],
    deps = [
        ":cache-chroniclemap__plugin",
        ":chroniclemap-test-lib",
    ],
)

java_library(
    name = "chroniclemap-test-lib",
    testonly = True,
    srcs = ["src/test/java/com/googlesource/gerrit/modules/cache/chroniclemap/TestPersistentCacheDef.java"],
    deps = PLUGIN_DEPS,
)