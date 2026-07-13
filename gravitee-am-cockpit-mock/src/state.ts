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

import { randomUUID } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';

/**
 * The installation identity this mock presents to AM in the HELLO reply.
 *
 * AM's HelloReplyAdapter only consumes `installationId` + `installationStatus`
 * (persisted as COCKPIT_INSTALLATION_ID / COCKPIT_INSTALLATION_STATUS).
 * `installationType` is NOT read from the reply by AM — it is kept here for
 * completeness/persistence and echoed as a harmless extra field only.
 */
export interface InstallationState {
  installationId: string;
  installationStatus: string;
  installationType?: string;
}

export interface StateOptions {
  stateFile?: string;
  installationId?: string;
  installationStatus?: string;
  installationType?: string;
}

const DEFAULT_STATUS = 'ACCEPTED';

/**
 * Resolve the installation identity: start from the persisted file (if any),
 * apply explicit startup overrides (which always win), generate what is still
 * missing, then persist back when a state file was configured.
 */
export function loadInstallationState(opts: StateOptions): InstallationState {
  let state: Partial<InstallationState> = {};

  if (opts.stateFile && existsSync(opts.stateFile)) {
    try {
      const parsed = JSON.parse(readFileSync(opts.stateFile, 'utf-8'));
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        state = parsed;
      } else {
        console.warn(`[state] ${opts.stateFile} did not contain a JSON object, ignoring`);
      }
    } catch (err) {
      console.warn(`[state] could not read ${opts.stateFile}, ignoring: ${(err as Error).message}`);
    }
  }

  // Explicit CLI overrides take precedence over the persisted cache.
  if (opts.installationId) state.installationId = opts.installationId;
  if (opts.installationStatus) state.installationStatus = opts.installationStatus;
  if (opts.installationType) state.installationType = opts.installationType;

  const resolved: InstallationState = {
    installationId: state.installationId || randomUUID(),
    installationStatus: state.installationStatus || DEFAULT_STATUS,
    installationType: state.installationType,
  };

  if (opts.stateFile) {
    try {
      writeFileSync(opts.stateFile, JSON.stringify(resolved, null, 2));
    } catch (err) {
      console.warn(`[state] could not persist ${opts.stateFile}: ${(err as Error).message}`);
    }
  }

  return resolved;
}
