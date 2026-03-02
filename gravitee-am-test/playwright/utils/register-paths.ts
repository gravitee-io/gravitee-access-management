/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import path from 'path';
import Module from 'module';

/**
 * Module resolution hook: resolves tsconfig path aliases and shims
 * @jest/globals for Playwright. Loaded in playwright.config.ts.
 *
 * Uses Module._resolveFilename (private Node API). If removed in a
 * future Node version, migrate aliases to tsconfig-paths/register
 * and the jest shim to module.register() (Node 20.6+).
 */

const projectRoot = path.resolve(__dirname, '../..');

// Path alias mappings (same as tsconfig.json)
const aliases: Record<string, string> = {
  '@management-apis/': 'api/management/apis/',
  '@management-models/': 'api/management/models/',
  '@management-commands/': 'api/commands/management/',
  '@gateway-apis/': 'api/gateway/apis/',
  '@gateway-commands/': 'api/commands/gateway/',
  '@utils-commands/': 'api/commands/utils/',
  '@utils/': 'api/utils/',
  '@api-fixtures/': 'api/fixtures/',
};

const shimPath = path.resolve(__dirname, 'jest-globals-shim');

// Monkey-patch Module._resolveFilename to handle aliases + jest shim
const originalResolve = (Module as any)._resolveFilename;
(Module as any)._resolveFilename = function (request: string, parent: any, isMain: boolean, options: any) {
  // Shim @jest/globals
  if (request === '@jest/globals') {
    return originalResolve.call(this, shimPath, parent, isMain, options);
  }

  // Resolve path aliases
  for (const [alias, target] of Object.entries(aliases)) {
    if (request.startsWith(alias)) {
      const resolved = path.join(projectRoot, target, request.slice(alias.length));
      return originalResolve.call(this, resolved, parent, isMain, options);
    }
  }

  return originalResolve.call(this, request, parent, isMain, options);
};
