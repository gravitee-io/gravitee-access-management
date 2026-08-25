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
import type { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { performGet, requestToken, signInUser } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

const REDIRECT_URI = 'https://callback';

export interface ContextPathAuthFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  application: any;
  user: any;
  /** The context path the domain was moved to, with its leading slash. */
  newPath: string;
  /** The path the domain was served on before the change — its hrid. */
  originalPath: string;
  /**
   * Status returned by the original path's discovery endpoint *before* the context path changed.
   *
   * Without this, asserting that the old path stops being served proves nothing: a 404 would look
   * identical if the domain had never been reachable there in the first place.
   */
  originalPathStatusBeforeChange: number;
  originalOidcConfig: any;
  tokenMintedOnOriginalPath: string;
  domainOnOriginalPath: { hrid: string };
  /** Discovery document fetched from the domain after the context path changed. */
  openIdConfiguration: DomainOidcConfig;
  /**
   * A domain-shaped object whose `hrid` is the new context path.
   *
   * The shared `signInUser` helper asserts the login redirect points at
   * `${AM_GATEWAY_URL}/${domain.hrid}/login`, i.e. it assumes a domain is served on its hrid.
   * Changing the context path is precisely what breaks that assumption, so the helper is given
   * the new path instead. That turns its internal assertion into a useful one for this suite:
   * it confirms the login page moved along with the rest of the endpoints.
   */
  domainOnNewPath: { hrid: string };
}

export const setupContextPathAuthFixture = async (): Promise<ContextPathAuthFixture> => {
  const accessToken = await requestAdminAccessToken();

  const domain = await createDomain(
    accessToken,
    uniqueName('ctxpath-auth', true),
    'AM-2224 authenticate through an application after the context path changes',
  );
  if (!domain.id || !domain.hrid) {
    throw new Error('Domain create did not return id/hrid');
  }
  const originalPath = `/${domain.hrid}`;

  // Create the application and user before starting the domain, so the first sync picks them up.
  const idpSet = await getAllIdps(domain.id, accessToken);
  const application = await createApplication(domain.id, accessToken, {
    name: uniqueName('ctxpath-client', true),
    type: 'WEB',
    clientId: uniqueName('ctxpath-app', true),
    clientSecret: uniqueName('ctxpath-secret', true),
    redirectUris: [REDIRECT_URI],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // updateApplication does not return the secret; carry over the one from create
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const user = {
    username: uniqueName('CtxPathUser', true),
    password: 'SomeP@ssw0rd',
    firstName: 'Context',
    lastName: 'Path',
    email: 'ctxpath@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, accessToken, user);

  await startDomain(domain.id, accessToken);
  const startedOnOriginalPath = await waitForDomainStart(domain);
  const originalOidcConfig = startedOnOriginalPath.oidcConfig;

  // Mint a token on the original path, before anything moves.
  const preChangePostLogin = await signInUser({ hrid: domain.hrid }, application, user, originalOidcConfig);
  const preChangeTokenResponse = await requestToken(application, originalOidcConfig, preChangePostLogin);
  const tokenMintedOnOriginalPath = preChangeTokenResponse.body.access_token;

  // Record that the domain really is reachable on its original path, before moving it.
  const originalPathStatusBeforeChange = (
    await performGet(process.env.AM_GATEWAY_URL, `${originalPath}/oidc/.well-known/openid-configuration`)
  ).status;

  // Move the domain off its default path. Everything below is exercised against the new one.
  const newPath = `/${uniqueName('ctxpath', false)}`;
  const updated = await waitForSyncAfter(domain.id, () => patchDomain(domain.id, accessToken, { path: newPath }));
  expect(updated.path).toEqual(newPath);

  const openIdConfiguration = (await waitForOidcReady(newPath.slice(1))).body;

  return {
    accessToken,
    domain,
    application,
    user,
    newPath,
    originalPath,
    originalPathStatusBeforeChange,
    originalOidcConfig,
    tokenMintedOnOriginalPath,
    domainOnOriginalPath: { hrid: domain.hrid },
    openIdConfiguration,
    domainOnNewPath: { hrid: newPath.slice(1) },
    cleanUp: async () => {
      await safeDeleteDomain(domain.id, accessToken);
    },
  };
};
