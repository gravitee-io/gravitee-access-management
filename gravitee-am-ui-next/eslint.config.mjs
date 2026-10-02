import { includeIgnoreFile } from '@eslint/compat';
import grapheneConfig from '@gravitee/graphene-core/eslint';
import prettier from 'eslint-config-prettier';
import { fileURLToPath } from 'node:url';

const gitignorePath = fileURLToPath(new URL('.gitignore', import.meta.url));

export default [
    includeIgnoreFile(gitignorePath),
    { ignores: ['.yarn/', 'rsbuild.config.ts', 'vitest.config.ts', 'postcss.config.mjs'] },
    ...grapheneConfig,
    prettier,
];
