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
import faker from 'faker';

faker.seed(Date.now() ^ (process.pid << 16) ^ (Math.random() * 0xffffffff));

/**
 * Suppresses console.log/warn/debug during fixture setup/teardown to keep
 * Playwright reporter output clean. Restores original console methods after.
 */
export async function quietly<T>(fn: () => Promise<T>): Promise<T> {
  const orig = { log: console.log, warn: console.warn, debug: console.debug };
  console.log = console.warn = console.debug = () => {};
  try {
    return await fn();
  } finally {
    Object.assign(console, orig);
  }
}

/**
 * Generates a unique, slugified test name using faker for collision avoidance.
 */
export const uniqueTestName = (prefix: string) =>
  faker.helpers.slugify(
    `${prefix}-${faker.commerce.productAdjective()}-${faker.animal.type()}-${faker.datatype.number({ min: 1, max: 10000 })}`,
  );
