module.exports = function (api) {
  api.cache(true);
  return {
    presets: ["babel-preset-expo"],
    // react-native-worklets/plugin powers react-native-reanimated v4 and must be listed last.
    // Uniwind requires no Babel preset of its own.
    plugins: ["react-native-worklets/plugin"],
  };
};
