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
 * AM Gateway API client
 */

import axios, { type AxiosInstance, isAxiosError } from 'axios';
import type { IntrospectionResult } from '../types/auth';
import type { AmConfig } from '../types/config';
import type { Logger } from '../utils/logger';
import { UnauthorizedError } from '../utils/errors';

export class AmGatewayClient {
  private client: AxiosInstance;
  private domainHrid: string;
  private logger: Logger;

  constructor(config: AmConfig, logger: Logger) {
    this.domainHrid = config.domainHrid;
    this.logger = logger;
    this.client = axios.create({
      baseURL: config.gatewayUrl,
      timeout: 10000,
    });

    // Add request interceptor for logging
    this.client.interceptors.request.use(
      (config) => {
        logger.debug(`AM Gateway request: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        logger.error('AM Gateway request error', error);
        return Promise.reject(error);
      }
    );
  }

  /**
   * Introspect an OAuth 2.0 access token
   * 
   * @param token - The access token to introspect
   * @param clientId - OAuth client ID for Basic authentication
   * @param clientSecret - OAuth client secret for Basic authentication
   * @param domainHridOverride - Optional domain HRID override (used for E2E tests with transient domains)
   * @returns Introspection result with token metadata
   * @throws {UnauthorizedError} If token is invalid or authentication fails
   * @throws {Error} For other HTTP errors
   */
  async introspectToken(
    token: string,
    clientId: string,
    clientSecret: string,
    domainHridOverride?: string
  ): Promise<IntrospectionResult> {
    try {
      const domainHrid = domainHridOverride || this.domainHrid;
      const introspectionUrl = `/${domainHrid}/oauth/introspect`;
      this.logger.debug(`Introspecting token`, {
        domainHrid,
        hasOverride: !!domainHridOverride,
        url: introspectionUrl,
        clientId,
      });
      const response = await this.client.post<IntrospectionResult | IntrospectionResult[]>(
        introspectionUrl,
        new URLSearchParams({
          token,
          token_type_hint: 'access_token',
        }),
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Authorization': `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
          },
        }
      );

      // Handle array response: gateway may return [{...}] instead of {...}
      // Unwrap to single object to match expected interface
      const data = Array.isArray(response.data) ? response.data[0] : response.data;
      
      if (Array.isArray(response.data)) {
        this.logger.debug('Unwrapped array introspection response', data as Record<string, unknown>);
      } else {
        this.logger.debug('Introspection result', { active: data.active, sub: data.sub, client_id: data.client_id });
      }

      return data as IntrospectionResult;
    } catch (error) {
      if (isAxiosError(error)) {
        const status = error.response?.status;
        const statusText = error.response?.statusText;
        const responseData = error.response?.data;
        const domainHrid = domainHridOverride || this.domainHrid;

        this.logger.error('Token introspection failed', {
          status,
          statusText,
          data: responseData,
          domainHrid,
          introspectionUrl: `/${domainHrid}/oauth/introspect`,
          clientId,
        });

        // Map authentication/authorization failures to UnauthorizedError
        if (status === 401 || status === 403) {
          throw new UnauthorizedError(
            `Token introspection failed: ${status} ${statusText || ''}`.trim()
          );
        }

        // Other HTTP errors
        throw new Error(
          `Token introspection failed: ${status} ${statusText || ''}`.trim()
        );
      }
      
      // Non-HTTP errors (network, timeout, etc.)
      throw error;
    }
  }
}
