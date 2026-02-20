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
 * Token introspection service
 */

import type { IntrospectionResult } from '../types/auth';
import { AmGatewayClient } from '../clients/am-gateway';
import type { AmConfig } from '../types/config';
import type { Logger } from '../utils/logger';

export class IntrospectionService {
  private client: AmGatewayClient;

  constructor(config: AmConfig, logger: Logger) {
    this.client = new AmGatewayClient(config, logger);
  }

  /**
   * Introspect a token and return the result
   */
  async introspect(
    token: string,
    clientId: string,
    clientSecret: string,
    domainHrid?: string
  ): Promise<IntrospectionResult> {
    return this.client.introspectToken(token, clientId, clientSecret, domainHrid);
  }

  /**
   * Extract subject from introspection result
   * Priority: sub > client_id
   */
  extractSubject(result: IntrospectionResult): string | null {
    return result.sub || result.client_id || null;
  }
}
