import { readdir, readFile } from 'node:fs/promises';
import { relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const sourceRoot = resolve(frontendRoot, 'src');
const allowedExtensions = new Set(['.ts', '.html', '.scss', '.css']);
const maximumLines = 1000;
const violations = [];

async function inspectDirectory(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      await inspectDirectory(path);
      continue;
    }
    if (!allowedExtensions.has(path.slice(path.lastIndexOf('.')))) continue;

    const source = await readFile(path, 'utf8');
    const lines = source.length === 0
      ? 0
      : source.split(/\r\n|\n|\r/u).length - (/[\r\n]$/u.test(source) ? 1 : 0);
    if (lines > maximumLines) violations.push({ path, lines });
  }
}

await inspectDirectory(sourceRoot);
if (violations.length > 0) {
  for (const violation of violations) {
    console.error(`${relative(frontendRoot, violation.path)}: ${violation.lines} lines (maximum ${maximumLines})`);
  }
  process.exitCode = 1;
} else {
  console.log(`All frontend source files are within ${maximumLines} lines.`);
}
