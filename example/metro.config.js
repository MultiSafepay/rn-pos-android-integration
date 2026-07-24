/* eslint-disable */
// Learn more https://docs.expo.io/guides/customizing-metro
const { getDefaultConfig } = require("expo/metro-config");
const { withUniwindConfig } = require("uniwind/metro");
const path = require("path");

const config = getDefaultConfig(__dirname);
const moduleRoot = path.resolve(__dirname, "..");

// The local module (rn-pos-android-integration) lives in the repo root, i.e. an
// ANCESTOR of this example. Under SDK 56 / RN 0.85, Metro no longer crawls files
// in an ancestor watchFolder, so it can't hash the module's build output.
// scripts/link-local-module.js links the built module into ./node_modules (a
// non-cyclic symlink Metro can follow from inside the project). The module has
// no runtime deps of its own, so we block the repo-root node_modules entirely and
// let it borrow the example app's (SDK 56) copies of expo / react / react-native.
config.resolver.blockList = [
  ...Array.from(config.resolver.blockList ?? []),
  new RegExp(`^${path.resolve(moduleRoot, "node_modules").replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}/.*$`),
];

config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, "./node_modules"),
  path.resolve(moduleRoot, "node_modules"),
];

// Watch the repo root so the dev server picks up changes to the module's build output.
config.watchFolders = [moduleRoot];

config.transformer.getTransformOptions = async () => ({
  transform: {
    experimentalImportSupport: false,
    inlineRequires: true,
  },
});

// withUniwindConfig must be the outermost wrapper of the Metro config.
module.exports = withUniwindConfig(config, {
  cssEntryFile: "./src/global.css",
  dtsFile: "./src/uniwind-types.d.ts",
});
