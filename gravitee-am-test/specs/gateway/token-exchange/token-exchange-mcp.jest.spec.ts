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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { parseJwt } from '@api-fixtures/jwt';
import {
  expectTokenIntrospectionMatchesMcpClient,
  setupTokenExchangeMcpFixture,
  TokenExchangeMcpFixture,
} from './fixtures/token-exchange-mcp-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let defaultFixture: TokenExchangeMcpFixture;

beforeAll(async () => {
  defaultFixture = await setupTokenExchangeMcpFixture();
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
});

describe('Token Exchange grant (MCP Server)', () => {
  it('should exchange subject token and keep expiration constraints', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken, expiresIn: subjectExpiresIn } = await obtainSubjectToken();

    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const exchangedToken = exchangeResponse.body;
    expect(exchangedToken.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(exchangedToken.refresh_token).toBeUndefined();
    expect(exchangedToken.expires_in).toBeLessThanOrEqual(subjectExpiresIn);
    expect(exchangedToken.token_type.toLowerCase()).toBe('bearer');

    const decoded = parseJwt(exchangedToken.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      exchangedToken.access_token,
      mcpServer.clientId!,
    );
  });

  it('should keep gis claim from subject token', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken();
    const subjectDecoded = parseJwt(subjectAccessToken);
    const subjectGis = subjectDecoded.payload['gis'];
    expect(subjectGis).toBeDefined();

    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const exchangedDecoded = parseJwt(exchangeResponse.body.access_token);
    expect(exchangedDecoded.payload['gis']).toBe(subjectGis);

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      exchangeResponse.body.access_token,
      mcpServer.clientId!,
    );
  });

  it('should exchange refresh token when allowed', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { refreshToken } = await obtainSubjectToken();
    expect(refreshToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${refreshToken}&subject_token_type=urn:ietf:params:oauth:token-type:refresh_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      response.body.access_token,
      mcpServer.clientId!,
    );
  });

  it('should exchange id token when allowed', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { idToken } = await obtainSubjectToken();
    expect(idToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${idToken}&subject_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      response.body.access_token,
      mcpServer.clientId!,
    );
  });

  it('should reject when requested_token_type is not supported', async () => {
    const { oidc, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_token_type=urn:ietf:params:oauth:token-type:refresh_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('requested_token_type');
  });

  it('should NOT include id_token in response when exchanging access token with openid scope', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(exchangeResponse.body.access_token).toBeDefined();
    expect(exchangeResponse.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(exchangeResponse.body.id_token).toBeUndefined();

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      exchangeResponse.body.access_token,
      mcpServer.clientId!,
    );
  });

  it('should exchange jwt subject token when allowed', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;
    const { accessToken: subjectAccessToken } = await obtainSubjectToken();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      response.body.access_token,
      mcpServer.clientId!,
    );
  });

  it('should reject when subject_token_type is not allowed', async () => {
    const { oidc, mcpServerBasicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:saml1`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('subject_token_type');
  });
});
