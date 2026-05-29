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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { Fixture } from '../../../test-fixture';
import { AutomationClient } from './automation-client';
import { AutomationDomainDef, buildAutomationDomainDef } from './automation-definitions';

export interface AutomationAuthFixture extends Fixture {
  accessToken: string;
  client: AutomationClient;
}

/**
 * Token-only fixture for tests that don't need a domain (auth/openapi).
 */
export const setupAutomationAuthFixture = async (): Promise<AutomationAuthFixture> => {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toMatch(JWT_FORMAT);

  return {
    accessToken,
    client: new AutomationClient(accessToken),
    cleanUp: async () => {},
  };
};

export interface AutomationDomainFixture extends AutomationAuthFixture {
  domainKey: string;
}

export interface AutomationDomainFixtureOptions {
  /** Prefix for the unique key; defaults to 'autodom'. */
  keyPrefix?: string;
  /** Extra fields merged into the domain definition. */
  overrides?: Partial<AutomationDomainDef>;
}

/**
 * Creates an automation-managed domain via PUT on the Automation API itself
 * and tears it down via DELETE on the Automation API (key-only).
 */
export const setupAutomationDomainFixture = async (
  options: AutomationDomainFixtureOptions = {},
): Promise<AutomationDomainFixture> => {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toMatch(JWT_FORMAT);
  const client = new AutomationClient(accessToken);

  const domainKey = uniqueName(options.keyPrefix ?? 'autodom', true).toLowerCase();
  let created = false;

  try {
    const definition = buildAutomationDomainDef({ key: domainKey, ...(options.overrides ?? {}) });
    const response = await client.putDomain(definition);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(domainKey);
    created = true;

    return {
      accessToken,
      client,
      domainKey,
      cleanUp: async () => {
        if (created) {
          await client.deleteDomain(domainKey);
        }
      },
    };
  } catch (error) {
    if (created) {
      try {
        await client.deleteDomain(domainKey);
      } catch (cleanupError) {
        console.error('Failed to clean up domain after fixture setup error:', cleanupError);
      }
    }
    throw error;
  }
};
