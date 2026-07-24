/* eslint-disable */
// Links the local rn-pos-android-integration module into this example's
// node_modules so Metro can resolve AND hash it.
//
// Why this is needed: the example lives inside the module repo, so the module is an
// ANCESTOR of the example's project root. Since Expo SDK 56 / RN 0.85, Metro no
// longer crawls files in an ancestor watchFolder, so the classic
// `extraNodeModules: { name: ".." }` approach can no longer hash the module's build
// output. Instead we create a small package directory in node_modules containing a
// package.json plus a symlink to either build/ or src/ — a non-cyclic symlink Metro
// follows from inside the project root. Runs automatically via `postinstall`.
const fs = require('fs');
const path = require('path');

const moduleRoot = path.resolve(__dirname, '..', '..');
const pkg = require(path.join(moduleRoot, 'package.json'));
const target = path.resolve(__dirname, '..', 'node_modules', pkg.name);
const buildDir = path.join(moduleRoot, 'build');
const srcDir = path.join(moduleRoot, 'src');
const hasBuildOutput = fs.existsSync(path.join(buildDir, 'index.js'));

if (!hasBuildOutput && !fs.existsSync(path.join(srcDir, 'index.ts'))) {
  console.warn(
    `[link-local-module] ${pkg.name} has no build output or source entry.\n` +
      `  Expected either ${path.join(buildDir, 'index.js')} or ${path.join(srcDir, 'index.ts')}.`
  );
}

fs.rmSync(target, { recursive: true, force: true });
fs.mkdirSync(target, { recursive: true });

// EAS local builds archive git-tracked files, so the ignored root build/ output may
// be missing in production. In that case, point Metro at the source entry instead.
const linkedPkg = hasBuildOutput
  ? pkg
  : {
      ...pkg,
      main: 'src/index.ts',
      types: 'src/index.ts',
      exports: {
        '.': {
          types: './src/index.ts',
          default: './src/index.ts',
        },
        './package.json': './package.json',
      },
    };

fs.writeFileSync(path.join(target, 'package.json'), `${JSON.stringify(linkedPkg, null, 2)}\n`);

if (hasBuildOutput) {
  fs.symlinkSync(buildDir, path.join(target, 'build'), 'junction');
} else if (fs.existsSync(srcDir)) {
  fs.symlinkSync(srcDir, path.join(target, 'src'), 'junction');
}

console.log(`[link-local-module] linked ${pkg.name} -> ${hasBuildOutput ? buildDir : srcDir}`);
