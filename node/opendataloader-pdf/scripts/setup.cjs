const fs = require('fs');
const path = require('path');
const { globSync } = require('glob');

const rootDir = path.resolve(__dirname, '..');
const javaDir = path.resolve(rootDir, '../../java');
const sourceJarGlob = path
  .join(javaDir, 'opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar')
  .replace(/\\/g, '/');

console.log(`Searching for JAR file in: ${sourceJarGlob}`);

const sourceJarPaths = globSync(sourceJarGlob);
if (sourceJarPaths.length === 0) {
  console.error(
    "Could not find the JAR file. Please run 'mvn package' in the 'java/' directory first.",
  );
  process.exit(1);
}
if (sourceJarPaths.length > 1) {
  console.error(`Found multiple JAR files, expected one: ${sourceJarPaths}`);
  process.exit(1);
}

const sourceJarPath = sourceJarPaths[0];
console.log(`Found source JAR: ${sourceJarPath}`);

const destJarDir = path.join(rootDir, 'lib').replace(/\\/g, '/');
if (!fs.existsSync(destJarDir)) {
  fs.mkdirSync(destJarDir, { recursive: true });
}
const destJarPath = path.join(destJarDir, 'opendataloader-pdf-cli.jar').replace(/\\/g, '/');
console.log(`Copying JAR to ${destJarPath}`);
fs.copyFileSync(sourceJarPath, destJarPath);

// Copy README.md, LICENSE, NOTICE.md, and THIRD_PARTY
const readmeSrc = path.resolve(rootDir, '../../README.md');
const licenseSrc = path.resolve(rootDir, '../../LICENSE');
const noticeSrc = path.resolve(rootDir, '../../NOTICE.md');
const thirdPartySrc = path.resolve(rootDir, '../../THIRD_PARTY');

const readmeDest = path.join(rootDir, 'README.md').replace(/\\/g, '/');
const licenseDest = path.join(rootDir, 'LICENSE').replace(/\\/g, '/');
const noticeDest = path.join(rootDir, 'NOTICE.md').replace(/\\/g, '/');
const thirdPartyDest = path.join(rootDir, 'THIRD_PARTY').replace(/\\/g, '/');

console.log(`Copying README.md to ${readmeDest}`);
fs.copyFileSync(readmeSrc, readmeDest);

console.log(`Copying LICENSE to ${licenseDest}`);
fs.copyFileSync(licenseSrc, licenseDest);

console.log(`Copying NOTICE.md to ${noticeDest}`);
fs.copyFileSync(noticeSrc, noticeDest);

console.log(`Copying THIRD_PARTY directory to ${thirdPartyDest}`);
if (fs.existsSync(thirdPartyDest)) {
  fs.rmSync(thirdPartyDest, { recursive: true, force: true });
}
fs.cpSync(thirdPartySrc, thirdPartyDest, { recursive: true });

console.log('Package preparation complete.');
