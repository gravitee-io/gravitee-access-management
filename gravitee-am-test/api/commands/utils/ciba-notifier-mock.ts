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
const CALLBACK_DELAY_MS = 2000;

export type CibaNotifierOutcome = 'accept-all' | 'reject-all';

export interface CibaNotifierCallback {
  url: string;
  clientId: string;
  clientSecret?: string;
}

export interface CibaNotifierMock {
  notifierConfiguration: (outcome: CibaNotifierOutcome) => string;
  register: (callback: CibaNotifierCallback) => Promise<void>;
  remove: () => Promise<void>;
}

export const createCibaNotifierMock = (): CibaNotifierMock => {
  const basePath = `/ciba/${uniqueName('notifier', true)}`;
  let stubIds: string[] = [];

  const endpoint = (outcome: CibaNotifierOutcome) => `${WIREMOCK_INTERNAL}${basePath}/${outcome}`;

  const notifierConfiguration = (outcome: CibaNotifierOutcome) =>
    JSON.stringify({
      endpoint: endpoint(outcome),
      headerName: 'Authorization',
      connectTimeout: 5000,
      idleTimeout: 10000,
      maxPoolSize: 10,
    });

  const remove = async () => {
    for (const id of stubIds) {
      await crossFetch(`${WIREMOCK_ADMIN}/mappings/${id}`, { method: 'DELETE' });
    }
    stubIds = [];
  };

  const register = async (callback: CibaNotifierCallback) => {
    await remove();
    const credentials = Buffer.from(`${encodeURIComponent(callback.clientId)}:${callback.clientSecret ?? ''}`).toString('base64');
    for (const outcome of ['accept-all', 'reject-all'] as CibaNotifierOutcome[]) {
      const validated = outcome === 'accept-all';
      const response = await crossFetch(`${WIREMOCK_ADMIN}/mappings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          request: { method: 'POST', urlPath: `${basePath}/${outcome}` },
          response: {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
            body: `{{formData request.body 'form' urlDecode=true}}{"tid":"{{{form.tid}}}","state":"{{{form.state}}}"}`,
          },
          serveEventListeners: [
            {
              name: 'webhook',
              parameters: {
                method: 'POST',
                url: callback.url,
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded',
                  Authorization: `Basic ${credentials}`,
                },
                body: `{{formData originalRequest.body 'form' urlDecode=true}}tid={{urlEncode form.tid}}&state={{urlEncode form.state}}&validated=${validated}`,
                delay: { type: 'fixed', milliseconds: CALLBACK_DELAY_MS },
              },
            },
          ],
        }),
      });
      expect(response.status).toEqual(201);
      stubIds.push((await response.json()).id);
    }
  };

  return { notifierConfiguration, register, remove };
};
