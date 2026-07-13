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
import fetch from 'cross-fetch';
import { jest } from '@jest/globals';

const DEFAULT_TEST_TIMEOUT = 20000;

export interface Fixture {
  accessToken?: string;
  cleanUp(): Promise<void>;
}

export const setup = (timeout: number = DEFAULT_TEST_TIMEOUT): void => {
  globalThis.fetch = fetch;
  jest.setTimeout(timeout);
};

/**
 * Retries (api/config/retry.setup.js) are deferred by default: a failed test is
 * retried after the file's other tests finish, which assumes tests in the file
 * are order-independent. Order-dependent files must call this at the top of the
 * file so failed tests are retried in place, before the flow moves on.
 */
export const retryImmediatelyForThisFile = (): void => {
  const retries = Number(process.env.JEST_RETRIES || 0);
  if (retries > 0) {
    jest.retryTimes(retries, {
      logErrorsBeforeRetry: true,
      retryImmediately: true,
      waitBeforeRetry: Number(process.env.JEST_RETRY_DELAY_MS || 3000),
    });
  }
};
