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
import { decodeJwt } from '@utils-commands/jwt';

export function validateAudienceClaim(token: string, expectedResources: string[]): void {
  const decoded = decodeJwt(token);
  expect(decoded).toBeDefined();
  const aud = decoded.aud;
  expect(aud).toBeDefined();
  const audArray: string[] = Array.isArray(aud) ? aud : [aud];
  expectedResources.forEach((r) => expect(audArray).toContain(r));
}

export function validateSuccessfulTokenResponse(response: any, expectedResources?: string[]): void {
  expect(response.body).toBeDefined();
  expect(response.body.access_token).toBeDefined();
  expect(String(response.body.token_type).toLowerCase()).toBe('bearer');
  expect(response.body.expires_in).toBeDefined();
  expect(response.body.error).toBeUndefined();
  expect(response.body.error_description).toBeUndefined();
  if (expectedResources && expectedResources.length > 0) {
    validateAudienceClaim(response.body.access_token, expectedResources);
  }
}

export function validateErrorResponse(response: any, expectedError: string = 'invalid_target'): void {
  expect(response.body.error).toBe(expectedError);
  expect(response.body.error_description).toContain('not recognized');
  expect(response.body.access_token).toBeUndefined();
  expect(response.body.token_type).toBeUndefined();
}


