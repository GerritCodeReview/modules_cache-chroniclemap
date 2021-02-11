# Build

This plugin is built with Bazel in-tree build.

## Build in Gerrit tree

Create a symbolic link of the repsotiory source to the Gerrit source
tree /plugins/cache-chronicalmap directory, and the external_plugin_deps.bzl
dependencies linked to /plugins/external_plugin_deps.bzl.

Example:

```sh
git clone https://gerrit.googlesource.com/gerrit
git clone https://gerrit.googlesource.com/modules/cache-chroniclemap
cd gerrit/plugins
ln -s ../../cache-chroniclemap cache-chroniclemap
ln -sf ../../external_plugin_deps.bzl .
```

From the Gerrit source tree issue the command `bazelsk build plugins/cache-chroniclemap`.

Example:

```sh
bazelisk build plugins/cache-chroniclemap
```

The libModule jar file is created under `basel-bin/plugins/cache-chroniclemap/cache-chroniclemap.jar`

To execute the tests run `bazelisk test plugins/cache-chroniclemap/...` from the Gerrit source tree.

Example:

```sh
bazelisk test plugins/cache-chroniclemap/...
```