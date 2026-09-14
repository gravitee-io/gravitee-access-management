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

import { seedMapiData } from '../../seed';
import { seedTokenExchangeData } from '../../token-exchange-seed';

export async function seed(label: string): Promise<void> {
  await seedMapiData(label);
  // 4.13 registers a trusted issuer as a trusted domain of its own and carries the key-retrieval
  // limits at the top level. The `legacy` consumer domain still goes through the deprecated inline
  // list, which the Management API keeps accepting as a projection onto the same entities.
  await seedTokenExchangeData(label, { trustedIssuerApi: 'trusted-domain', keyRetrievalApi: 'key-retrieval-settings' });
}
