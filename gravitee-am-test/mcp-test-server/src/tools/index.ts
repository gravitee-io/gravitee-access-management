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
 * MCP tool registration
 * 
 * Registers all MCP tools (getTests, addTest, deleteTest) with Express routes
 * and authentication middleware.
 */

import { createGetTestsHandler } from './getTests';
import { createAddTestHandler } from './addTest';
import { createDeleteTestHandler } from './deleteTest';
import type { TestMetadataManager } from '../auth/test-metadata-manager';
import type { Express } from 'express';
import { createAuthMiddleware } from '../middleware/auth';
import type { AppConfig } from '../types/config';
import type { Logger } from '../utils/logger';

/**
 * Register all MCP tools with Express app
 * 
 * @param app - Express application instance
 * @param config - Application configuration
 * @param logger - Logger instance
 * @param testMetadataManager - Manager for test metadata
 */
export function registerTools(
  app: Express,
  config: AppConfig,
  logger: Logger,
  testMetadataManager: TestMetadataManager
): void {
  // Create auth middleware
  const authMiddleware = createAuthMiddleware(config, logger, testMetadataManager);

  // Register tools with auth middleware
  app.post(
    '/tools/getTests',
    authMiddleware,
    createGetTestsHandler(testMetadataManager)
  );

  app.post(
    '/tools/addTest',
    authMiddleware,
    createAddTestHandler(testMetadataManager)
  );

  app.post(
    '/tools/deleteTest',
    authMiddleware,
    createDeleteTestHandler(testMetadataManager)
  );

  logger.info('Tools registered: getTests, addTest, deleteTest');
}
