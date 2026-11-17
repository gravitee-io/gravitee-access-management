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
import { patchDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performDelete, performGet, performPatch, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { ApplicationAuthorizationDetailTypeSettings } from '@management-models/ApplicationAuthorizationDetailTypeSettings';
import { AUTHORIZATION_DETAILS_TEST, AuthorizationDetailsFixture, setupAuthorizationDetailsFixture } from './authorization-details-fixture';

export interface RegisteredClient {
  clientId: string;
  applicationId: string;
  registrationAccessToken: string;
  body: Record<string, any>;
}

type GatewayRequest = ReturnType<typeof performGet>;

export interface AuthorizationDetailsDcrFixture extends AuthorizationDetailsFixture {
  /** Sends a registration request with a redirect URI, overridden by `metadata`. */
  register: (metadata?: Record<string, unknown>) => GatewayRequest;
  registerClient: (metadata?: Record<string, unknown>) => Promise<RegisteredClient>;
  read: (client: RegisteredClient) => GatewayRequest;
  update: (client: RegisteredClient, metadata: Record<string, unknown>) => GatewayRequest;
  patch: (client: RegisteredClient, metadata: Record<string, unknown>) => GatewayRequest;
  remove: (client: RegisteredClient) => GatewayRequest;
  createTemplate: (types: ApplicationAuthorizationDetailTypeSettings[]) => Promise<Application>;
}

const JSON_CONTENT = { 'Content-type': 'application/json' };

export const toRegisteredClient = (response: any): RegisteredClient => ({
  clientId: response.body.client_id,
  applicationId: response.body.id,
  registrationAccessToken: response.body.registration_access_token,
  body: response.body,
});

export const expectInvalidClientMetadata = (response: any, description: string): void => {
  expect(response.status).toBe(400);
  expect(response.body).toEqual({ error: 'invalid_client_metadata', error_description: description });
};

export const setupAuthorizationDetailsDcrFixture = async (): Promise<AuthorizationDetailsDcrFixture> => {
  const fixture = await setupAuthorizationDetailsFixture();
  try {
    const { domain, accessToken } = fixture;
    await waitForSyncAfter(domain.id, () =>
      patchDomain(domain.id, accessToken, {
        oidc: {
          clientRegistrationSettings: {
            isDynamicClientRegistrationEnabled: true,
            isOpenDynamicClientRegistrationEnabled: true,
            isClientTemplateEnabled: true,
          },
        },
      }),
    );
    await waitForOidcReady(domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

    const registrationEndpoint = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/oidc/register`;
    const bearer = (client: RegisteredClient) => ({ Authorization: `Bearer ${client.registrationAccessToken}` });
    const register = (metadata: Record<string, unknown> = {}) =>
      performPost(registrationEndpoint, '', { redirect_uris: [AUTHORIZATION_DETAILS_TEST.REDIRECT_URI], ...metadata }, JSON_CONTENT);

    return {
      ...fixture,
      register,
      registerClient: async (metadata = {}) => toRegisteredClient(await register(metadata).expect(201)),
      read: (client) => performGet(registrationEndpoint, `/${client.clientId}`, bearer(client)),
      update: (client, metadata) =>
        performPut(
          registrationEndpoint,
          `/${client.clientId}`,
          { redirect_uris: [AUTHORIZATION_DETAILS_TEST.REDIRECT_URI], ...metadata },
          { ...JSON_CONTENT, ...bearer(client) },
        ),
      patch: (client, metadata) =>
        performPatch(registrationEndpoint, `/${client.clientId}`, metadata, { ...JSON_CONTENT, ...bearer(client) }),
      remove: (client) => performDelete(registrationEndpoint, `/${client.clientId}`, bearer(client)),
      createTemplate: async (types) => {
        const clientId = uniqueName('authz-details-template', true);
        const template = await createApplication(domain.id, accessToken, {
          name: clientId,
          type: 'WEB',
          clientId,
          redirectUris: [AUTHORIZATION_DETAILS_TEST.REDIRECT_URI],
        });
        await waitForSyncAfter(domain.id, () =>
          patchApplication(
            domain.id,
            accessToken,
            { template: true, settings: { oauth: { authorizationDetailTypes: types } } },
            template.id,
          ),
        );
        return template;
      },
    };
  } catch (error) {
    await fixture.cleanUp();
    throw error;
  }
};
