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
 * Test metadata management for E2E tests
 * 
 * Manages test-specific metadata (testId, tokens, credentials, domainHrid) injected
 * by E2E tests via admin API. Provides thread-safe access to credentials and tokens
 * for authentication/authorization flows.
 */

import { ConflictError } from '../utils/errors';

const TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS = 300; // 5 minutes
const DEFAULT_TOKEN_EXPIRY_MS = 3_600_000; // 1 hour

/**
 * Test metadata injected by E2E tests via admin API.
 * Contains credentials and configuration for a single test run.
 */
export interface TestMetadata {
  testId: string;
  token: string;
  tokenExpiry: number;
  clientId: string;
  clientSecret: string;
  /**
   * Domain HRID used for gateway/AuthZen calls (e.g. /<domainHrid>/oauth/introspect).
   * This allows E2E tests to control which transient domain the MCP server targets.
   */
  domainHrid: string;
}

export class TestMetadataManager {
  private metadata: TestMetadata | null = null;

  /**
   * Inject test metadata via admin API
   * @throws {ConflictError} If metadata already exists
   */
  setMetadata(
    testId: string,
    token: string,
    clientId: string,
    clientSecret: string,
    expiresIn: number | undefined,
    domainHrid: string
  ): void {
    if (this.metadata !== null) {
      const currentTestId = this.metadata.testId;
      throw new ConflictError(`Test metadata already exists (testId: ${currentTestId})`);
    }

    const tokenExpiry = expiresIn
      ? Date.now() + (expiresIn - TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS) * 1000
      : Date.now() + DEFAULT_TOKEN_EXPIRY_MS;

    this.metadata = {
      testId,
      token,
      tokenExpiry,
      clientId,
      clientSecret,
      domainHrid,
    };
  }

  /**
   * Get client credentials for introspection
   */
  getClientCredentials(): { clientId: string; clientSecret: string } | null {
    if (!this.metadata) {
      return null;
    }
    return {
      clientId: this.metadata.clientId,
      clientSecret: this.metadata.clientSecret,
    };
  }

  /**
   * Get domain HRID for gateway/AuthZen calls.
   */
  getDomainHrid(): string | null {
    return this.metadata?.domainHrid ?? null;
  }

  /**
   * Clear test metadata
   */
  clearMetadata(): void {
    this.metadata = null;
  }

  /**
   * Get token for AuthZen calls
   */
  getToken(): string | null {
    if (this.metadata && Date.now() < this.metadata.tokenExpiry) {
      return this.metadata.token;
    }
    // Token expired or not set
    return null;
  }

  /**
   * Get testId for response inclusion
   */
  getTestId(): string | null {
    return this.metadata?.testId ?? null;
  }

  /**
   * Check if metadata exists and is valid
   */
  hasMetadata(): boolean {
    return this.metadata !== null && Date.now() < this.metadata.tokenExpiry;
  }

  /**
   * Check if metadata exists (for conflict detection)
   */
  isOccupied(): boolean {
    return this.metadata !== null;
  }

  /**
   * Get current testId if metadata exists (for error responses)
   */
  getCurrentTestId(): string | null {
    return this.metadata?.testId ?? null;
  }
}
