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
 * Shared utilities for MCP tool handlers
 * 
 * Provides common validation and metadata checking functions used across
 * all MCP tool handlers.
 */

import type { Response } from 'express';
import type { TestMetadataManager } from '../auth/test-metadata-manager';

/**
 * Check if test metadata is configured and return testId, or send 503 error
 * @returns testId if metadata is valid, null if error was sent
 */
export function requireTestMetadata(
  testMetadataManager: TestMetadataManager,
  res: Response
): string | null {
  if (!testMetadataManager.hasMetadata()) {
    res.status(503).json({
      error: 'Test metadata not configured',
    });
    return null;
  }

  return testMetadataManager.getTestId() ?? null;
}

/**
 * Validate that a value is a non-empty string
 * @returns validated string or null if invalid
 */
export function validateString(value: unknown): string | null {
  if (!value || typeof value !== 'string' || value.trim().length === 0) {
    return null;
  }
  return value;
}
