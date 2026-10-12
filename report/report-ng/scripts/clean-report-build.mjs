import { rm } from 'node:fs/promises';

await rm(new URL('../dist/report/', import.meta.url), { recursive: true, force: true });