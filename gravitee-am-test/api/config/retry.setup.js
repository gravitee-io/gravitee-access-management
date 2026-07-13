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
const retries = Number(process.env.JEST_RETRIES || 0);
if (retries > 0) {
  jest.retryTimes(retries, {
    logErrorsBeforeRetry: true,
    retryImmediately: false, // wait for all tests in the file to finish before retrying (see retryImmediatelyForThisFile to opt in)
    waitBeforeRetry: Number(process.env.JEST_RETRY_DELAY_MS || 3000),
  });
}
