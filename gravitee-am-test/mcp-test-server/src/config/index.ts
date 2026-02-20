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
 * Configuration loading and validation
 * 
 * Loads and validates application configuration from environment variables.
 */

import { configSchema } from './schema';
import type { AppConfig } from '../types/config';

/**
 * Load and validate application configuration from environment variables
 * 
 * @returns Validated application configuration
 * @throws {Error} If required environment variables are missing or invalid
 */
export function loadConfig(): AppConfig {
  const env = process.env;
  
  // Validate environment variables
  const validated = configSchema.parse({
    PORT: env.PORT,
    AM_GATEWAY_URL: env.AM_GATEWAY_URL,
    DOMAIN_HRID: env.DOMAIN_HRID,
    AUTHZEN_URL: env.AUTHZEN_URL,
    LOG_LEVEL: env.LOG_LEVEL,
  });

  return {
    server: {
      port: validated.PORT,
      logLevel: validated.LOG_LEVEL,
    },
    am: {
      gatewayUrl: validated.AM_GATEWAY_URL,
      domainHrid: validated.DOMAIN_HRID,
    },
    authzen: {
      url: validated.AUTHZEN_URL,
    },
  };
}
