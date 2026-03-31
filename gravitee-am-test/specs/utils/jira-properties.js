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
 * jest-junit testcase properties file.
 *
 * Extracts a single AM-XXXX Jira key from the test title and emits
 * `test_key` and `issue` properties in JUnit XML for Xray traceability.
 *
 * Configured via `testCasePropertiesFile` in ci.config.js.
 */
const JIRA_BASE_URL = 'https://gravitee.atlassian.net/browse';
const JIRA_KEY_FORMAT = /AM-\d+/;

module.exports = (testResult) => {
  const title = testResult.fullName || testResult.title || '';
  const match = title.match(JIRA_KEY_FORMAT);
  if (!match) return {};
  const key = match[0];
  return {
    test_key: key,
    issue: `${JIRA_BASE_URL}/${key}`,
  };
};
