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
 * Express server setup
 */

import express, { type Express, type Request, type Response } from 'express';
import { z } from 'zod';
import { createHealthCheckHandler } from './health/health-check';
import { TestMetadataManager } from './auth/test-metadata-manager';
import { ConflictError, NotFoundError, ValidationError } from './utils/errors';
import { registerTools } from './tools';
import type { AppConfig } from './types/config';
import type { Logger } from './utils/logger';

const testMetadataSchema = z.object({
  testId: z.string().min(1),
  token: z.string().min(1),
  clientId: z.string().min(1),
  clientSecret: z.string().min(1),
  expiresIn: z.number().positive().optional(),
  domainHrid: z.string().min(1),
});

export function createServer(config: AppConfig, logger: Logger): Express {
  const app = express();
  
  // Middleware
  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  // Initialize test metadata manager
  const testMetadataManager = new TestMetadataManager();

  // Health check endpoint
  app.get('/health', createHealthCheckHandler(testMetadataManager));

  // Admin endpoints for test metadata management
  app.post('/admin/test-metadata', (req: Request, res: Response) => {
    try {
      // Validate request body using zod
      const validationResult = testMetadataSchema.safeParse(req.body);
      if (!validationResult.success) {
        const errors = validationResult.error.errors.map(e => `${e.path.join('.')}: ${e.message}`).join(', ');
        throw new ValidationError(`Invalid request body: ${errors}`);
      }

      const { testId, token, clientId, clientSecret, expiresIn, domainHrid } = validationResult.data;

      // TestMetadataManager.setMetadata() will throw ConflictError if metadata already exists
      testMetadataManager.setMetadata(testId, token, clientId, clientSecret, expiresIn, domainHrid);
      logger.info(`Test metadata injected: testId=${testId}, domainHrid=${domainHrid}`);

      res.json({
        success: true,
        testId,
      });
    } catch (error) {
      if (error instanceof ConflictError) {
        const currentTestId = testMetadataManager.getCurrentTestId();
        res.status(409).json({
          error: error.message,
          currentTestId: currentTestId ?? null,
        });
      } else if (error instanceof ValidationError) {
        res.status(400).json({ error: error.message });
      } else {
        logger.error('Error injecting test metadata', error);
        res.status(500).json({ error: 'Internal server error' });
      }
    }
  });

  app.delete('/admin/test-metadata', (_req: Request, res: Response) => {
    try {
      if (!testMetadataManager.isOccupied()) {
        throw new NotFoundError('No test metadata found');
      }

      const testId = testMetadataManager.getTestId();
      testMetadataManager.clearMetadata();
      logger.info(`Test metadata cleared: testId=${testId ?? 'unknown'}`);

      res.json({
        success: true,
      });
    } catch (error) {
      if (error instanceof NotFoundError) {
        res.status(404).json({ error: error.message });
      } else {
        logger.error('Error clearing test metadata', error);
        res.status(500).json({ error: 'Internal server error' });
      }
    }
  });

  // Register tools
  registerTools(app, config, logger, testMetadataManager);

  // Error handling middleware
  app.use((err: Error, _req: Request, res: Response, _next: express.NextFunction) => {
    logger.error('Unhandled error', err);
    res.status(500).json({ error: 'Internal server error' });
  });

  // 404 handler
  app.use((_req: Request, res: Response) => {
    res.status(404).json({ error: 'Not found' });
  });

  return app;
}
