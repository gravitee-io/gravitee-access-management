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
import { parseJwt } from '@api-fixtures/jwt';
import { setup } from '../../test-fixture';
import {
  ASSERTION_CLAIM,
  EL_CLAIM,
  JwtBearerSubjectFixture,
  MAPPED_CLAIM,
  setupJwtBearerSubjectFixture,
} from './fixtures/jwt-bearer-subject-fixture';

setup(180000);

const ID_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:id_token';

let fixture: JwtBearerSubjectFixture;

beforeAll(async () => {
  fixture = await setupJwtBearerSubjectFixture();
});

afterAll(async () => {
  await fixture?.cleanup();
});

/**
 * The reported flow: an external issuer signs an assertion, the JWT Bearer extension grant turns it
 * into a subject token, and token exchange issues the final token. The assertion is signed per
 * request, so the propagated value cannot come from static configuration.
 */
describe('Token Exchange over a JWT Bearer subject token (RFC 8693)', () => {
  it('should propagate an assertion claim through the JWT Bearer grant and the exchange', async () => {
    const claimValue = 'AGENT-101';

    const subjectToken = await fixture.obtainSubjectToken(claimValue);
    expect(parseJwt(subjectToken).payload[ASSERTION_CLAIM]).toEqual(claimValue);

    const body = await fixture.exchange(subjectToken);

    expect(parseJwt(body.access_token).payload[MAPPED_CLAIM]).toEqual(claimValue);
  });

  it('should propagate the value of each assertion, not a value fixed at configuration time', async () => {
    const firstToken = await fixture.obtainSubjectToken('AGENT-201');
    const secondToken = await fixture.obtainSubjectToken('AGENT-202');

    const first = await fixture.exchange(firstToken);
    const second = await fixture.exchange(secondToken);

    expect(parseJwt(first.access_token).payload[MAPPED_CLAIM]).toEqual('AGENT-201');
    expect(parseJwt(second.access_token).payload[MAPPED_CLAIM]).toEqual('AGENT-202');
  });

  it('should carry the assertion claim onto an exchanged id_token', async () => {
    const subjectToken = await fixture.obtainSubjectToken('AGENT-301');

    const body = await fixture.exchange(subjectToken, `&requested_token_type=${ID_TOKEN_TYPE}`);

    expect(body.issued_token_type).toEqual(ID_TOKEN_TYPE);
    expect(parseJwt(body.access_token).payload[MAPPED_CLAIM]).toEqual('AGENT-301');
  });

  it('should expose the JWT Bearer subject token claims to the expression language', async () => {
    const subjectToken = await fixture.obtainSubjectToken('AGENT-401');

    const body = await fixture.exchange(subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload[EL_CLAIM]).toEqual('AGENT-401');
    expect(exchanged.payload['sub']).toEqual(parseJwt(subjectToken).payload['sub']);
  });
});
