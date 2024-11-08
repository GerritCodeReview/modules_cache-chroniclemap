load("//tools/bzl:maven_jar.bzl", "maven_jar")

# Ensure artifacts compatibility by selecting them from the Bill Of Materials
# https://github.com/OpenHFT/OpenHFT/blob/chronicle-bom-2.25ea62/chronicle-bom/pom.xml
def external_plugin_deps():
    maven_jar(
        name = "chronicle-map",
        artifact = "net.openhft:chronicle-map:3.25ea6",
        sha1 = "b5201c6e8722d69d707a42a619531229d82a0f0a",
    )

    maven_jar(
        name = "chronicle-core",
        artifact = "net.openhft:chronicle-core:2.25ea15",
        sha1 = "c3cc7ee02da28f9bc43cf300af197206e31acd11",
    )

    maven_jar(
        name = "chronicle-wire",
        artifact = "net.openhft:chronicle-wire:2.25ea16",
        sha1 = "145729b974cbd0258a94479b083e8ad545274d1c",
    )

    maven_jar(
        name = "chronicle-bytes",
        artifact = "net.openhft:chronicle-bytes:2.25ea11",
        sha1 = "60d3ba07f5917ad80b69c63cd0f7f8cf7e0d6370",
    )

    maven_jar(
        name = "chronicle-algo",
        artifact = "net.openhft:chronicle-algorithms:2.25ea0",
        sha1 = "f500a074fbd1ebbbc1a7bdf55fe89363526de577",
    )

    maven_jar(
        name = "chronicle-values",
        artifact = "net.openhft:chronicle-values:2.25ea4",
        sha1 = "5566b027e255d68528106f9e8ccfbff00245858a",
    )

    maven_jar(
        name = "chronicle-threads",
        artifact = "net.openhft:chronicle-threads:2.25ea7",
        sha1 = "483b68e6345a4d503812ba6ada1678cbf2ca4a33",
    )

    maven_jar(
        name = "chronicle-compiler",
        artifact = "net.openhft:compiler:2.25ea3",
        sha1 = "1131e8938c24396f814302e50749f31d83149e53",
    )

    maven_jar(
        name = "chronicle-affinity",
        artifact = "net.openhft:affinity:3.23.3",
        sha1 = "95700a7cc8ba3f47b9ab744f6f2862f6eb19995c",
    )

    maven_jar(
        name = "chronicle-posix",
        artifact = "net.openhft:posix:2.25ea0",
        sha1 = "e28bc0ce9989513e53cc7514f5886ee002606f39",
    )

    maven_jar(
        name = "javapoet",
        artifact = "com.squareup:javapoet:1.13.0",
        sha1 = "d6562d385049f35eb50403fa86bb11cce76b866a",
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
