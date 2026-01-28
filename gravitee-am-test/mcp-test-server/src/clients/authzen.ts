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
 * AuthZen API client
 */

import axios, { type AxiosInstance, isAxiosError } from 'axios';
import type { AuthZenRequest, AuthZenResponse } from '../types/auth';
import type { AuthZenConfig, AmConfig } from '../types/config';
import type { Logger } from '../utils/logger';
import { AuthZenAuthenticationError, AuthZenRequestError } from '../utils/errors';

export class AuthZenClient {
  private client: AxiosInstance;
  private logger: Logger;

  constructor(amConfig: AmConfig, authzenConfig: AuthZenConfig, logger: Logger) {
    this.logger = logger;
    
    // Use AuthZen URL if provided, otherwise use Gateway URL
    const baseURL = authzenConfig.url || amConfig.gatewayUrl;
    
    this.client = axios.create({
      baseURL,
      timeout: 10000,
    });

    // Add request interceptor for logging
    this.client.interceptors.request.use(
      (config) => {
        logger.debug(`AuthZen request: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        logger.error('AuthZen request error', error);
        return Promise.reject(error);
      }
    );
  }

  /**
   * Check authorization decision via AuthZen
   * 
   * @param request - AuthZen evaluation request with subject, resource, and action
   * @param mcpServerToken - Bearer token for MCP server authentication
   * @param domainHrid - Domain HRID used for AuthZen path (e.g. /<domainHrid>/access/v1/evaluation)
   * @returns Authorization decision (true = PERMIT, false = DENY)
   * @throws {AuthZenAuthenticationError} If MCP server token is invalid (401)
   * @throws {AuthZenRequestError} For other HTTP errors
   */
  async checkAuthorization(
    request: AuthZenRequest,
    mcpServerToken: string,
    domainHrid: string
  ): Promise<AuthZenResponse> {
    try {
      this.logger.debug(`Checking authorization with domainHrid=${domainHrid}`, {
        subject: request.subject.id,
        resource: request.resource.id,
        action: request.action.name,
      });
      const response = await this.client.post<AuthZenResponse>(
        `/${domainHrid}/access/v1/evaluation`,
        request,
        {
          headers: {
            'Authorization': `Bearer ${mcpServerToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      return response.data;
    } catch (error) {
      if (isAxiosError(error)) {
        const status = error.response?.status;
        const statusText = error.response?.statusText;

        this.logger.error('AuthZen request failed', {
          status,
          statusText,
          subject: request.subject.id,
          resource: request.resource.id,
          action: request.action.name,
        });

        if (status === 401) {
          throw new AuthZenAuthenticationError('Invalid MCP server token');
        }
        
        // 403 Forbidden means authorization denied - return explicit DENY
        if (status === 403) {
          return { decision: false };
        }

        // Other HTTP errors
        throw new AuthZenRequestError(
          `AuthZen request failed: ${status} ${statusText || ''}`.trim(),
          status || 500
        );
      }
      
      // Non-HTTP errors (network, timeout, etc.)
      throw error;
    }
  }
}
