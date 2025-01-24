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
import { waitFor } from '@management-commands/domain-management-commands';
import { timeout } from '@utils-commands/misc';

export async function withRetry(operation, retries = 50, delay = 100) {
  let success = false;
  while (!(success || retries === 0)) {
    try {
      const result = await operation();
      success = true;
      return result;
    } catch (e) {
      retries = retries - 1;
      if (retries === 0) {
        throw e;
      }
      await waitFor(delay);
    }
  }
}

export type RetryOptions<T> = {
  /**
   * Maximum time, in ms, to wait for the condition to pass. Use 0 or a negative value for no timeout
   */
  timeoutMillis?: number;
  /**
   * Max number of retries. Only used if there's no timeout
   */
  maxAttempts?: number;
  /**
   * How long to wait between attempts
   */
  intervalMillis?: number;
  /**
   * Optional callback called with the promise's result once the condition passes
   * @param t the value from the promise
   */
  onDone?: (t: T) => void;
  /**
   * Optional callback called with the promise's result each time the condition fails
   * @param t the value from the promise
   */
  onRetry?: (t: T) => void;
  /**
   *
   */
  onAttemptsExceeded?: (t: T) => void;
};

/**
 * Retry a promise until the success condition passes (e.g. wait until a request returns 200 when waiting for domain to start)
 * @param f - function returning the promise we want to wait for
 * @param cond - condition
 * @param options
 */
export async function retryUntil<T>(f: () => Promise<T>, cond: (t: T) => boolean, options: RetryOptions<T> = {}): Promise<T> {
  const { timeoutMillis = 0, maxAttempts = 100, intervalMillis = 250, onDone = () => {}, onRetry = () => {} } = options;
  let attempts = 0;
  const promise = new Promise<T>(async (resolve, reject) => {
    while (true) {
      let result = await f();
      attempts += 1;
      if (cond(result)) {
        onDone(result);
        resolve(result);
        break;
      } else if (timeoutMillis <= 0 && attempts > maxAttempts) {
        reject(result);
        break;
      } else {
        onRetry(result);
        await waitFor(intervalMillis);
      }
    }
  });
  if (timeoutMillis > 0) {
    return timeout(timeoutMillis, promise);
  } else {
    return promise;
  }
}
