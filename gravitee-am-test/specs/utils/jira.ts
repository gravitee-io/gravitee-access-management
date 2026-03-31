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

const JIRA_KEY_FORMAT = /^AM-\d+$/;

/**
 * Tagged template that links a Jest test to a Jira/Xray test case key.
 *
 * The embedded key is picked up by jest-junit's testCasePropertiesFile
 * to emit `test_key` and `issue` properties in JUnit XML.
 *
 * Usage:
 *   it(jira`should use client_secret_basic ${'AM-2225'}`, async () => { ... });
 *
 * Produces test title: "should use client_secret_basic [AM-2225]"
 */
export function jira(strings: TemplateStringsArray, ...keys: string[]): string {
  for (const key of keys) {
    if (!JIRA_KEY_FORMAT.test(key)) {
      throw new Error(`Invalid Jira key format: "${key}" — expected AM-XXXX`);
    }
  }
  let result = strings[0];
  for (let i = 0; i < keys.length; i++) {
    result += `[${keys[i]}]${strings[i + 1]}`;
  }
  return result.trim();
}
