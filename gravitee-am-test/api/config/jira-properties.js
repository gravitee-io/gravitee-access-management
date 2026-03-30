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
 * Extracts AM-XXXX Jira keys from test titles and emits `test_key` and `issue`
 * properties in JUnit XML. This matches the Playwright `linkJira()` pattern
 * so both suites produce consistent Xray-compatible traceability.
 *
 * Configured via `testCasePropertiesFile` in ci.config.js.
 */
const JIRA_BASE_URL = 'https://gravitee.atlassian.net/browse';

module.exports = (testResult) => {
  const title = testResult.fullName || testResult.title || '';
  const keys = [...new Set(title.match(/AM-\d+/g) || [])];
  if (keys.length === 0) return {};

  const properties = {};
  if (keys.length === 1) {
    properties['test_key'] = keys[0];
    properties['issue'] = `${JIRA_BASE_URL}/${keys[0]}`;
  } else {
    keys.forEach((key, i) => {
      properties[`test_key_${i}`] = key;
      properties[`issue_${i}`] = `${JIRA_BASE_URL}/${key}`;
    });
  }
  return properties;
};
