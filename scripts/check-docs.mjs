import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';

const ignoredDirectories = new Set(['.git', 'node_modules', 'target']);

function markdownFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory() && !ignoredDirectories.has(entry.name)) {
      return markdownFiles(path);
    }
    return entry.isFile() && extname(entry.name) === '.md' ? [path] : [];
  });
}

const failures = [];
for (const file of markdownFiles('.')) {
  const markdown = readFileSync(file, 'utf8');
  for (const match of markdown.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const destination = match[1];
    if (/^(https?:\/\/|mailto:|#)/.test(destination)) {
      continue;
    }
    const relativePath = destination.replace(/#.*/, '');
    if (!existsSync(resolve(dirname(file), relativePath))) {
      failures.push(`${file}: missing ${destination}`);
    }
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log('Documentation links are valid.');
}
