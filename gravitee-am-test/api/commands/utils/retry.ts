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
import {waitFor} from '@management-commands/domain-management-commands';


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
