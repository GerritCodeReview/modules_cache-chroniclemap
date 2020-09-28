load("//tools/bzl:maven_jar.bzl", "maven_jar")

# Ensure artifacts compatibility by selecting them from the Bill Of Materials
# https://search.maven.org/artifact/net.openhft/chronicle-bom/2.19.283/pom
def external_plugin_deps():
    maven_jar(
        name = "chronicle-map",
        artifact = "net.openhft:chronicle-map:3.19.40",
        sha1 = "820edb9aad86adb2a836b4f66a878d6101bbee54",
    )

    maven_jar(
        name = "chronicle-core",
        artifact = "net.openhft:chronicle-core:2.19.50",
        sha1 = "71234f0116eceda4034cddceb79cd924cea5c4be",
    )

    maven_jar(
        name = "chronicle-wire",
        artifact = "net.openhft:chronicle-wire:2.19.45",
        sha1 = "1ec0da34391b57a3c1809b1e139caf0371784bc4",
    )

    maven_jar(
        name = "chronicle-bytes",
        artifact = "net.openhft:chronicle-bytes:2.19.46",
        sha1 = "790a1c374f008f97202dd94ec8435edfce798cd0",
    )

    maven_jar(
        name = "chronicle-algo",
        artifact = "net.openhft:chronicle-algorithms:2.19.40",
        sha1 = "9445d2c48468a32c54d631e3908c4362a2bbac2c",
    )

    maven_jar(
        name = "chronicle-values",
        artifact = "net.openhft:chronicle-values:2.19.41",
        sha1 = "f8bb874bbd67ceabd5166510043b5473e66285f4",
    )

    maven_jar(
        name = "chronicle-threads",
        artifact = "net.openhft:chronicle-threads:2.19.47",
        sha1 = "ef25ab51b795551e4c7bf3f80acde9b5364a3641",
    )

    maven_jar(
        name = "javapoet",
        artifact = "com.squareup:javapoet:1.12.1",
        sha1 = "e0e49f502697522ef047470b117ff81edc9f9a07",
    )

    maven_jar(
        name = "jna-platform",
        artifact = "net.java.dev.jna:jna-platform:5.5.0",
        sha1 = "af38e7c4d0fc73c23ecd785443705bfdee5b90bf",
    )

    maven_jar(
        name = "dev-jna",
        artifact = "net.java.dev.jna:jna:5.5.0",
        sha1 = "0e0845217c4907822403912ad6828d8e0b256208",
    )
