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
import { setupDomainForTest, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { AuditEntry, auditsOfType } from '@management-commands/audit-management-commands';
import { createApplication, getApplication, patchApplication } from '@management-commands/application-management-commands';
import { Application } from '@management-models/Application';
import { ApplicationAuthorizationDetailTypeSettings } from '@management-models/ApplicationAuthorizationDetailTypeSettings';
import { Domain } from '@management-models/Domain';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';
import { Fixture } from '../../../test-fixture';
import { JWT_FORMAT } from '../../../utils/jwt-format';

export interface AuthorizationDetailTypeFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  /** A second domain, created on first call. */
  otherDomain: () => Promise<Domain>;
  /** A new empty domain on every call. */
  newDomain: () => Promise<Domain>;
}

export const APPLICATION_UPDATED_AUDIT = 'APPLICATION_UPDATED';
export const SYSTEM_ACTOR = 'system';

export const AUTHORIZATION_DETAIL_TYPE_TEST = {
  DOMAIN_NAME_PREFIX: 'adt-mgmt',
  DIALECT: 'https://json-schema.org/draft/2020-12/schema',
} as const;

export const validSchema = (type: string, extra: Record<string, unknown> = {}): string =>
  JSON.stringify({
    ...extra,
    type: 'object',
    properties: {
      type: { const: type },
      instructedAmount: { type: 'object', properties: { currency: { type: 'string' }, amount: { type: 'string' } } },
    },
    required: ['type'],
  });

export const uniqueType = (prefix = 'payment_initiation'): string => uniqueName(prefix, true);

export const newType = (type: string, overrides: Partial<NewAuthorizationDetailType> = {}): NewAuthorizationDetailType => ({
  type,
  name: 'Payment initiation',
  ...overrides,
});

/** Creates a SERVICE application. */
export const createTestApplication = (fixture: AuthorizationDetailTypeFixture, domain: Domain = fixture.domain): Promise<Application> =>
  createApplication(domain.id, fixture.accessToken, {
    name: uniqueName('adt-app', true),
    type: 'SERVICE',
    clientId: uniqueName('adt-app', true),
  });

export const setApplicationTypes = (
  fixture: AuthorizationDetailTypeFixture,
  app: Application,
  settings: ApplicationAuthorizationDetailTypeSettings[],
): Promise<Application> =>
  patchApplication(app.domain, fixture.accessToken, { settings: { oauth: { authorizationDetailTypes: settings } } }, app.id);

export const createTestApplicationWithTypes = async (
  fixture: AuthorizationDetailTypeFixture,
  settings: ApplicationAuthorizationDetailTypeSettings[],
  domain: Domain = fixture.domain,
): Promise<Application> => {
  const app = await createTestApplication(fixture, domain);
  await setApplicationTypes(fixture, app, settings);
  return app;
};

export const readApplicationTypes = async (
  fixture: AuthorizationDetailTypeFixture,
  app: Application,
): Promise<ApplicationAuthorizationDetailTypeSettings[]> =>
  (await getApplication(app.domain, fixture.accessToken, app.id)).settings.oauth.authorizationDetailTypes;

export const applicationUpdatesOf = async (
  fixture: AuthorizationDetailTypeFixture,
  app: Application,
  knownIds: Set<string>,
  actor?: string,
): Promise<AuditEntry[]> =>
  (await auditsOfType(app.domain, fixture.accessToken, APPLICATION_UPDATED_AUDIT)).filter(
    (a) =>
      !knownIds.has(a.id) &&
      a.target?.id === app.id &&
      a.outcome?.status?.toLowerCase() === 'success' &&
      (!actor || a.actor?.alternativeId === actor),
  );

export const waitForApplicationUpdateOf = async (
  fixture: AuthorizationDetailTypeFixture,
  app: Application,
  knownIds: Set<string>,
  actor?: string,
): Promise<AuditEntry> =>
  (
    await retryUntil(
      () => applicationUpdatesOf(fixture, app, knownIds, actor),
      (audits) => audits.length > 0,
      { timeoutMillis: 30000, intervalMillis: 500 },
    ).catch(() => {
      throw new Error(`No new ${APPLICATION_UPDATED_AUDIT} audit${actor ? ` by ${actor}` : ''} for application ${app.id} within 30000ms`);
    })
  )[0];

/** Creates probe types in the fixture domain until one is audited. */
export const waitForAuditReporter = async (fixture: AuthorizationDetailTypeFixture): Promise<void> => {
  await retryUntil(
    async () => {
      await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType('audit_probe')));
      return (await auditsOfType(fixture.domain.id, fixture.accessToken, 'AUTHORIZATION_DETAIL_TYPE_CREATED')).length;
    },
    (count) => count > 0,
    { timeoutMillis: 30000, intervalMillis: 1000 },
  );
};

export const setupAuthorizationDetailTypeFixture = async (): Promise<AuthorizationDetailTypeFixture> => {
  const domains: Domain[] = [];
  let accessToken: string | null = null;

  const cleanUp = async () => {
    for (const domain of domains) {
      await safeDeleteDomain(domain.id, accessToken);
    }
  };

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toMatch(JWT_FORMAT);

    const { domain } = await setupDomainForTest(uniqueName(AUTHORIZATION_DETAIL_TYPE_TEST.DOMAIN_NAME_PREFIX, true), {
      accessToken,
      waitForStart: false,
    });
    expect(domain.id).toEqual(expect.any(String));
    domains.push(domain);

    const newDomain = async () => {
      const created = (
        await setupDomainForTest(uniqueName(AUTHORIZATION_DETAIL_TYPE_TEST.DOMAIN_NAME_PREFIX, true), {
          accessToken,
          waitForStart: false,
        })
      ).domain;
      domains.push(created);
      return created;
    };

    let other: Domain | null = null;
    const otherDomain = async () => {
      if (!other) {
        other = await newDomain();
      }
      return other;
    };

    return { domain, accessToken, otherDomain, newDomain, cleanUp };
  } catch (error) {
    try {
      await cleanUp();
    } catch (cleanupError) {
      console.error('Failed to cleanup domains after setup failure:', cleanupError);
    }
    throw error;
  }
};
