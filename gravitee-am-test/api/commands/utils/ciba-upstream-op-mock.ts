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
import crossFetch from 'cross-fetch';
import { uniqueName } from '@utils-commands/misc';

const WIREMOCK_ADMIN = (process.env.SFR_URL || 'http://localhost:8181') + '/__admin';
const WIREMOCK_INTERNAL = process.env.INTERNAL_SFR_URL || 'http://wiremock:8080';

/** The upstream user rejects every request sent with this login hint, and approves any other. */
export const CIBA_UPSTREAM_REJECTING_LOGIN_HINT = 'rejecting-user';

export interface CibaUpstreamOpMock {
  /** As the gateway reaches it. */
  wellKnownUri: string;
  clientId: string;
  clientSecret: string;
  remove: () => Promise<void>;
}

/**
 * A WireMock OpenID Provider for the CIBA federation notifier, whose user answers at once. An approved request's token response
 * grants the `authorization_details` the notifier relayed, which the backchannel authentication response carries base64-encoded
 * in its `auth_req_id`. UserInfo returns the subject `upstream-user`.
 */
export const createCibaUpstreamOpMock = async (): Promise<CibaUpstreamOpMock> => {
  const basePath = `/ciba-upstream/${uniqueName('op', true)}`;
  const issuer = `${WIREMOCK_INTERNAL}${basePath}`;
  const json = (body: unknown, status = 200) => ({ status, headers: { 'Content-Type': 'application/json' }, jsonBody: body });
  const template = (body: string, status = 200) => ({ status, headers: { 'Content-Type': 'application/json' }, body });
  const token = { access_token: 'upstream-access-token', token_type: 'Bearer', expires_in: 300, id_token: 'upstream.id.token' };

  const stubs = [
    {
      request: { method: 'GET', urlPath: `${basePath}/.well-known/openid-configuration` },
      response: json({
        issuer,
        authorization_endpoint: `${issuer}/authorize`,
        token_endpoint: `${issuer}/token`,
        userinfo_endpoint: `${issuer}/userinfo`,
        jwks_uri: `${issuer}/jwks`,
        backchannel_authentication_endpoint: `${issuer}/bc-authorize`,
        response_types_supported: ['code'],
        subject_types_supported: ['public'],
        id_token_signing_alg_values_supported: ['RS256'],
      }),
    },
    { request: { method: 'GET', urlPath: `${basePath}/jwks` }, response: json({ keys: [] }) },
    {
      request: { method: 'GET', urlPath: `${basePath}/userinfo` },
      response: json({ sub: 'upstream-user', preferred_username: 'upstream-user', email: 'upstream.user@test.com' }),
    },
    {
      priority: 1,
      request: {
        method: 'POST',
        urlPath: `${basePath}/bc-authorize`,
        formParameters: { login_hint: { equalTo: CIBA_UPSTREAM_REJECTING_LOGIN_HINT } },
      },
      response: json({ auth_req_id: 'rejected', expires_in: 120, interval: 1 }),
    },
    {
      priority: 2,
      request: { method: 'POST', urlPath: `${basePath}/bc-authorize`, formParameters: { authorization_details: { absent: true } } },
      response: json({ auth_req_id: 'approved', expires_in: 120, interval: 1 }),
    },
    {
      priority: 3,
      request: { method: 'POST', urlPath: `${basePath}/bc-authorize` },
      response: template(
        `{{formData request.body 'form' urlDecode=true}}{"auth_req_id":"{{base64 form.authorization_details}}","expires_in":120,"interval":1}`,
      ),
    },
    {
      priority: 1,
      request: { method: 'POST', urlPath: `${basePath}/token`, formParameters: { auth_req_id: { equalTo: 'rejected' } } },
      response: json({ error: 'access_denied' }, 400),
    },
    {
      priority: 2,
      request: { method: 'POST', urlPath: `${basePath}/token`, formParameters: { auth_req_id: { equalTo: 'approved' } } },
      response: json(token),
    },
    {
      priority: 3,
      request: { method: 'POST', urlPath: `${basePath}/token` },
      response: template(
        `{{formData request.body 'form' urlDecode=true}}${JSON.stringify(token).slice(
          0,
          -1,
        )},"authorization_details":{{{base64 form.auth_req_id decode=true}}} }`,
      ),
    },
  ];

  const stubIds: string[] = [];
  const remove = async () => {
    for (const id of stubIds.splice(0)) {
      await crossFetch(`${WIREMOCK_ADMIN}/mappings/${id}`, { method: 'DELETE' });
    }
  };
  try {
    for (const stub of stubs) {
      const response = await crossFetch(`${WIREMOCK_ADMIN}/mappings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stub),
      });
      expect(response.status).toEqual(201);
      stubIds.push((await response.json()).id);
    }
  } catch (error) {
    await remove();
    throw error;
  }

  return {
    wellKnownUri: `${issuer}/.well-known/openid-configuration`,
    clientId: 'hint-domain',
    clientSecret: 'hint-domain-secret',
    remove,
  };
};
