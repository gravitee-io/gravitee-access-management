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

import { existsSync, readFileSync } from 'fs';
import { join, resolve } from 'path';

/**
 * Resolves the EE license the local stack runs with, in the base64 form AM expects for an
 * organization license.
 *
 * The same license reaches the stack two different ways, so this reads whichever is present:
 * - CI exports the license base64-encoded as `GRAVITEE_LICENSE_KEY`.
 * - Locally the compose dev overlay bind-mounts the raw binary license3j key at a fixed path.
 */

const STACK_LICENSE_KEY = ['docker', 'local-stack', 'dev', 'license', 'gravitee-universe-v4.key'];

const repoRoot = () => resolve(__dirname, '..', '..', '..', '..');

let cached: string | undefined;

/**
 * The stack's license as an unbroken base64 string.
 *
 * @throws if neither source is available.
 */
export const stackLicenseBase64 = (): string => {
  if (cached) {
    return cached;
  }

  // Trim defensively: a stray newline would fail AM's strict base64 decode with a confusing error.
  const fromEnv = process.env.GRAVITEE_LICENSE_KEY?.trim();
  if (fromEnv) {
    cached = fromEnv;
    return cached;
  }

  const keyPath = join(repoRoot(), ...STACK_LICENSE_KEY);
  if (!existsSync(keyPath)) {
    throw new Error(
      `No EE license available to push as an organization license. Set GRAVITEE_LICENSE_KEY (base64), ` +
        `or place the key at ${keyPath} — the same license the local stack requires to start.`,
    );
  }

  cached = readFileSync(keyPath).toString('base64');
  return cached;
};
