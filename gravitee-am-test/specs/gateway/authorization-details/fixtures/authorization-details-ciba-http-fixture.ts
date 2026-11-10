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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import {
  CIBA_TOKEN_POLL_INTERVAL_MS,
  CIBA_TOKEN_POLL_TIMEOUT_MS,
  isCibaTokenGrantedResponse,
} from '../../ciba-federation/fixtures/hint-domain-fixture';
import { setupTargetDomain, TARGET_USER, TargetDomainFixture } from '../../ciba-federation/fixtures/target-domain-fixture';
import { configureCibaApplication, createReferenceTypes, signRequestObject } from './authorization-details-ciba-fixture';

const CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';

export interface NotifiedRequest {
  response: any;
  findNotification: () => Promise<URLSearchParams | undefined>;
}

export interface AuthorizationDetailsCibaHttpFixture extends Fixture {
  targetDomain: TargetDomainFixture;
  /** Sends a backchannel authentication request with `openid` scope and the user's login hint, overridden by `params`. */
  authenticate: (params?: Record<string, string>) => Promise<NotifiedRequest>;
  /** Sends the request of {@link authenticate} with `claims` in a signed request object. */
  authenticateWithRequestObject: (claims: Record<string, unknown>) => Promise<NotifiedRequest>;
  pollTokenUntilGranted: (authReqId: string) => Promise<any>;
}

/** CIBA through the HTTP notifier of the CIBA federation target domain, whose device app approves every request. */
export const setupAuthorizationDetailsCibaHttpFixture = async (): Promise<AuthorizationDetailsCibaHttpFixture> => {
  const accessToken = await requestAdminAccessToken();
  const targetDomain = await setupTargetDomain(accessToken);
  try {
    const { domain, resourceApp } = targetDomain;
    await createReferenceTypes(domain.id, accessToken);
    await configureCibaApplication(domain.id, resourceApp.id, accessToken);

    const cibaEndpoint = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/oidc/ciba/authenticate`;
    const audience = `${process.env.AM_GATEWAY_URL}/${domain.hrid}/oidc`;
    const clientCredentials = { client_id: resourceApp.clientId, client_secret: resourceApp.clientSecret };

    // The binding message identifies the notification of each request
    const send = async (params: Record<string, string>, bindingMessage: string): Promise<NotifiedRequest> => {
      const response = await performPost(cibaEndpoint, '', new URLSearchParams({ ...clientCredentials, ...params }).toString(), {
        'Content-Type': 'application/x-www-form-urlencoded',
      });
      const findNotification = async () =>
        (await targetDomain.getNotifications()).find((notification) => notification.get('message') === bindingMessage);
      return { response, findNotification };
    };

    const authenticate = (params: Record<string, string> = {}) => {
      const bindingMessage = uniqueName('ciba-rar', true);
      return send({ scope: 'openid', login_hint: TARGET_USER.username, binding_message: bindingMessage, ...params }, bindingMessage);
    };

    const authenticateWithRequestObject = async (claims: Record<string, unknown>) => {
      const bindingMessage = uniqueName('ciba-rar', true);
      const request = await signRequestObject(resourceApp.clientId, audience, {
        scope: 'openid',
        login_hint: TARGET_USER.username,
        binding_message: bindingMessage,
        ...claims,
      });
      return send({ request }, bindingMessage);
    };

    const pollToken = (authReqId: string) =>
      performPost(
        targetDomain.oidcConfig.token_endpoint,
        '',
        new URLSearchParams({ ...clientCredentials, grant_type: CIBA_GRANT_TYPE, auth_req_id: authReqId }).toString(),
        { 'Content-Type': 'application/x-www-form-urlencoded' },
      );

    return {
      accessToken,
      targetDomain,
      authenticate,
      authenticateWithRequestObject,
      pollTokenUntilGranted: (authReqId) =>
        retryUntil(() => pollToken(authReqId), isCibaTokenGrantedResponse, {
          timeoutMillis: CIBA_TOKEN_POLL_TIMEOUT_MS,
          intervalMillis: CIBA_TOKEN_POLL_INTERVAL_MS,
        }),
      cleanUp: targetDomain.cleanUp,
    };
  } catch (error) {
    await targetDomain.cleanUp();
    throw error;
  }
};
