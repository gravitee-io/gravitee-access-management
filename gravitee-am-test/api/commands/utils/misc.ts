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

import {waitFor} from '@management-commands/domain-management-commands';
import {expect} from '@jest/globals';
import {performGet} from '@gateway-commands/oauth-oidc-commands';

export async function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Wait for the promise to resolve, but not longer then `millis` ms
 */
export async function timeout<T>(millis: number, promise: Promise<T>): Promise<T | never> {
  let timeLimit = waitFor(millis).then((_) => {
    throw Error('timeout');
  });
  return Promise.race([timeLimit, promise]);
}

export type BasicResponse = { status: number; header: { [x: string]: string }, headers: { [x: string]: string } };

export async function followRedirect(redirectResponse: BasicResponse) {
  expect(redirectResponse.status).toBe(302);
  const headers = redirectResponse.header['set-cookie'] ? { Cookie: redirectResponse.header['set-cookie'] } : {};
  return performGet(redirectResponse.header['location'], '', headers);
}
