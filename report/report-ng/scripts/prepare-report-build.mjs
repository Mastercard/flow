import { appendFile, copyFile, readFile, readdir, writeFile } from 'node:fs/promises';
import { basename, join } from 'node:path';
import { transformAsync } from '@babel/core';
import transformDynamicImport from '@babel/plugin-transform-dynamic-import';
import transformModulesSystemJs from '@babel/plugin-transform-modules-systemjs';

const outputDirectory = new URL('../dist/report/', import.meta.url);
const entries = await readdir(outputDirectory);
const scripts = entries.filter(name => name.endsWith('.js'));

for (const name of scripts) {
  const path = new URL(name, outputDirectory);
  const source = await readFile(path, 'utf8');
  const transformed = await transformAsync(source, {
    babelrc: false,
    comments: false,
    compact: true,
    configFile: false,
    filename: name,
    plugins: [transformDynamicImport, transformModulesSystemJs],
    sourceMaps: false,
    sourceType: 'module',
  });
  if (!transformed?.code) {
    throw new Error(`Failed to transform ${name}`);
  }
  await writeFile(path, transformed.code, 'utf8');
}

const indexPath = new URL('index.html', outputDirectory);
let index = await readFile(indexPath, 'utf8');
const entryScripts = [...index.matchAll(/<script src="([^"]+\.js)" type="module"><\/script>/g)]
  .map(match => match[1]);
if (entryScripts.length === 0) {
  throw new Error('Failed to find application entry scripts');
}

index = index.replace(/<script src="[^"]+\.js" type="module"><\/script>/g, '');
index = index.replace(
  '</body>',
  '<script src="system.min.js"></script>'
    + '<script type="systemjs-module" src="entry.js"></script>'
    + '</body>',
);
await writeFile(indexPath, index, 'utf8');

const dependencies = entryScripts.map(name => `"./${basename(name)}"`).join(',');
const setters = entryScripts.map(() => 'function(){}').join(',');
await writeFile(
  new URL('entry.js', outputDirectory),
  `System.register([${dependencies}],function(){return{setters:[${setters}],execute:function(){}}});`,
  'utf8',
);

await copyFile(
  join(process.cwd(), 'node_modules', 'systemjs', 'dist', 'system.min.js'),
  new URL('system.min.js', outputDirectory),
);

const systemJsLicense = await readFile(
  join(process.cwd(), 'node_modules', 'systemjs', 'LICENSE'),
  'utf8',
);
await appendFile(
  new URL('3rdpartylicenses.txt', outputDirectory),
  `\n\nSystemJS 6.15.1\n${systemJsLicense}`,
  'utf8',
);