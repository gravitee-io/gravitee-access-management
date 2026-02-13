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
 * MCP tool handler: addTest
 * 
 * Adds a mock test. Used for E2E testing of the MCP server's
 * authentication and authorization flow.
 */

import type { Request, Response } from 'express';
import type { TestMetadataManager } from '../auth/test-metadata-manager';
import { requireTestMetadata, validateString } from './base-handler';

/**
 * Create handler for addTest tool
 * 
 * @param testMetadataManager - Manager for test metadata
 * @returns Express handler function
 */
export function createAddTestHandler(testMetadataManager: TestMetadataManager) {
  return (req: Request, res: Response): void => {
    const testId = requireTestMetadata(testMetadataManager, res);
    if (!testId) {
      return; // Error response already sent
    }

    const test = validateString(req.body.test);
    if (!test) {
      res.status(400).json({
        error: 'test is required and must be a non-empty string',
      });
      return;
    }

    res.json({
      testId,
      message: 'addTest tool responded',
      test,
    });
  };
}
