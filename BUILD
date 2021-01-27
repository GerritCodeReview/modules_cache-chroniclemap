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
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-SshModule: com.googlesource.gerrit.modules.cache.chroniclemap.command.SSHCommandModule",
    ],
    deps = [
        "@chronicle-map//jar",
        "@chronicle-core//jar",
        "@chronicle-wire//jar",
        "@chronicle-bytes//jar",
        "@chronicle-algo//jar",
        "@chronicle-values//jar",
        "@chronicle-threads//jar",
        "@javapoet//jar",
        "@jna-platform//jar",
        "@dev-jna//jar",
        "//lib:h2",
        "//lib/commons:io",
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
        "//plugins/cache-chroniclemap/src/test/java/com/googlesource/gerrit/modules/cache/chroniclemap/lib:chroniclemap-test-lib",
    ],
)

acceptance_tests(
    srcs = glob(["src/test/java/**/*IT.java"]),
    group = "server_cache",
    labels = ["server"],
    deps = [
        ":cache-chroniclemap__plugin",
        "//plugins/cache-chroniclemap/src/test/java/com/googlesource/gerrit/modules/cache/chroniclemap/lib:chroniclemap-test-lib",
    ],
)
