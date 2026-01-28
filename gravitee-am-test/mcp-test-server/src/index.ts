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
 * Entry point for the MCP Server E2E
 */

import { loadConfig } from './config';
import { createLogger } from './utils/logger';
import { createServer } from './server';
import type { AppConfig } from './types/config';

function main(): void {
  try {
    // Load configuration
    const config: AppConfig = loadConfig();
    const logger = createLogger(config.server.logLevel);

    logger.info('Starting MCP Server E2E...');
    logger.info(`Configuration:`, {
      port: config.server.port,
      domainHrid: config.am.domainHrid,
      gatewayUrl: config.am.gatewayUrl,
    });

    // Create and start server
    const app = createServer(config, logger);

    app.listen(config.server.port, () => {
      logger.info(`🚀 MCP Server E2E started`);
      logger.info(`   Port: ${config.server.port}`);
      logger.info(`   Domain: ${config.am.domainHrid}`);
      logger.info(`   Gateway URL: ${config.am.gatewayUrl}`);
      logger.info(`\n📋 Available endpoints:`);
      logger.info(`   GET  /health - Health check`);
      logger.info(`   POST /admin/test-metadata - Inject test metadata`);
      logger.info(`   DELETE /admin/test-metadata - Clear test metadata`);
      logger.info('');
    }).on('error', (error: NodeJS.ErrnoException) => {
      if (error.code === 'EADDRINUSE') {
        logger.error(`Port ${config.server.port} is already in use. Please use a different port.`);
        process.exit(1);
      } else {
        logger.error('Failed to start server', error);
        process.exit(1);
      }
    });
  } catch (error) {
    // Logger not available yet, use console.error for startup failures
    console.error('❌ Failed to start server:', error);
    process.exit(1);
  }
}

main();
