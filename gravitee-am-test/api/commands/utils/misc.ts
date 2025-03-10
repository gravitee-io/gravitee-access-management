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
import { expect, jest } from '@jest/globals';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import faker from 'faker';
import { BulkResponse } from '@management-models/BulkResponse';
import { BulkOperationResultObject } from '@management-models/BulkOperationResultObject';

/**
 * Optionally randomize the name to make it unique, making it easier to re-run a test with the same DB, e.g. while developing tests.
 * if AM_TEST_RANDOMIZE_NAMES env variable is set to true, returns the name with a random suffix.
 * Otherwise, just passes the name through.
 *
 * @param name the base name
 * @param forceRandom if true, always randomizes the name (regardless of the env var)
 */
export function uniqueName(name: string, forceRandom: boolean = false) {
  const randomize = forceRandom || process.env.AM_TEST_RANDOMIZE_NAMES?.toLowerCase() === 'true';
  if (randomize) {
    return faker.helpers.slugify(
      `${name}-${faker.commerce.productAdjective()}-${faker.animal.type()}-${faker.datatype.number({ min: 1, max: 10000 })}`,
    );
  } else {
    return name;
  }
}

export async function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Wait for the promise to resolve, but not longer then `millis` ms
 */
export async function timeout<T>(millis: number, promise: Promise<T>): Promise<T | never> {
  let timeLimit = waitFor(millis).then((_) => {
    throw Error('timeout after ' + millis + 'ms');
  });
  return Promise.race([timeLimit, promise]);
}

export type BasicResponse = {
  status: number;
  header: { [x: string]: string };
  headers: { [x: string]: string };
  text: string;
  [x: string]: any;
};

export function followRedirectTag(tag?: string) {
  return (redirectResponse: BasicResponse) => {
    if (redirectResponse.status != 302) {
      throw new Error(`expected 302 response, but got ${redirectResponse.status}. Full response: ${JSON.stringify(redirectResponse)}`);
    }
    const headers = redirectResponse.header['set-cookie'] ? { Cookie: redirectResponse.header['set-cookie'] } : {};
    if (tag) {
      console.log(`[${tag}] redirecting to ${redirectResponse.header['location']}`);
    } else {
      console.log(`redirecting to ${redirectResponse.header['location']}`);
    }
    return performGet(redirectResponse.header['location'], '', headers);
  };
}

export async function followRedirect(redirectResponse: BasicResponse) {
  return followRedirectTag()(redirectResponse);
}

export function checkBulkResponse(
  response: BulkResponse,
  expectedItems: number,
  allSuccessful: boolean,
  expectations: { [key: number]: { count: number; assertions: (result: BulkOperationResultObject) => void } },
) {
  expect(response.results).toHaveLength(expectedItems);
  expect(response.allSuccessful).toBe(allSuccessful);
  for (let [status, expectation] of Object.entries(expectations)) {
    const resultsWithStatus = response.results.filter((x) => x.httpStatus == Number(status));

    expect(resultsWithStatus).toHaveLength(expectation.count);
    for (let result of resultsWithStatus) {
      expectation.assertions(result);
    }
  }
}
