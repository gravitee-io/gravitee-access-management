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
import { TestInfo } from '@playwright/test';

const JIRA_BASE_URL = 'https://gravitee.atlassian.net/browse';

/** Link a Playwright test to one or more Jira/Xray test keys for reporting. */
export function linkJira(testInfo: TestInfo, ...keys: string[]) {
  for (const key of keys) {
    testInfo.annotations.push({ type: 'issue', description: `${JIRA_BASE_URL}/${key}` });
    testInfo.annotations.push({ type: 'test_key', description: key });
  }
}
