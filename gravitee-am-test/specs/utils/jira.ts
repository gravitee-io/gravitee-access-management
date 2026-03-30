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

/**
 * Link a Jest test to one or more Jira/Xray test case keys.
 *
 * Returns a formatted prefix string for use in test titles. The embedded key
 * is picked up by jest-junit's testCasePropertiesFile to emit `test_key` and
 * `issue` properties in JUnit XML — matching the Playwright `linkJira()` pattern.
 *
 * Usage:
 *   it(`${jira('AM-2225')} should use client_secret_basic`, async () => { ... });
 *
 * Produces test title: "[AM-2225] should use client_secret_basic"
 * Produces JUnit XML:
 *   <property name="test_key" value="AM-2225"/>
 *   <property name="issue" value="https://gravitee.atlassian.net/browse/AM-2225"/>
 */
export function jira(...keys: string[]): string {
  return `[${keys.join(', ')}]`;
}
