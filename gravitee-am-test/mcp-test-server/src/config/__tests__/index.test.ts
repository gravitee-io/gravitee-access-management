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


import { describe, it, expect, beforeEach, afterEach } from '@jest/globals';
import { loadConfig } from '../index';

describe('loadConfig', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = {};
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  describe('successful loading', () => {
    it('should load all configuration from environment variables', () => {
      process.env.PORT = '3000';
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';
      process.env.DOMAIN_HRID = 'test-domain';
      process.env.LOG_LEVEL = 'debug';
      process.env.AUTHZEN_URL = 'http://authzen:8080';

      const config = loadConfig();

      expect(config.server.port).toBe(3000);
      expect(config.server.logLevel).toBe('debug');
      expect(config.am.gatewayUrl).toBe('http://gateway:8092');
      expect(config.am.domainHrid).toBe('test-domain');
      expect(config.authzen.url).toBe('http://authzen:8080');
    });

    it('should use default port 3001 when PORT not set', () => {
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';
      process.env.DOMAIN_HRID = 'test-domain';

      const config = loadConfig();

      expect(config.server.port).toBe(3001);
    });

    it('should use default log level "info" when LOG_LEVEL not set', () => {
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';
      process.env.DOMAIN_HRID = 'test-domain';

      const config = loadConfig();

      expect(config.server.logLevel).toBe('info');
    });

    it('should have undefined AuthZen URL when not provided', () => {
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';
      process.env.DOMAIN_HRID = 'test-domain';

      const config = loadConfig();

      expect(config.authzen.url).toBeUndefined();
    });
  });

  describe('validation errors', () => {
    it('should throw error when AM_GATEWAY_URL is missing', () => {
      process.env.DOMAIN_HRID = 'test-domain';

      expect(() => {
        loadConfig();
      }).toThrow();
    });

    it('should throw error when DOMAIN_HRID is missing', () => {
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';

      expect(() => {
        loadConfig();
      }).toThrow();
    });

    it('should throw error when AM_GATEWAY_URL is not a valid URL', () => {
      process.env.AM_GATEWAY_URL = 'not-a-url';
      process.env.DOMAIN_HRID = 'test-domain';

      expect(() => {
        loadConfig();
      }).toThrow();
    });

    it('should throw error when LOG_LEVEL is not a valid enum value', () => {
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';
      process.env.DOMAIN_HRID = 'test-domain';
      process.env.LOG_LEVEL = 'invalid-level';

      expect(() => {
        loadConfig();
      }).toThrow();
    });

    it('should throw error when PORT is not a positive integer', () => {
      process.env.PORT = '-1';
      process.env.AM_GATEWAY_URL = 'http://gateway:8092';
      process.env.DOMAIN_HRID = 'test-domain';

      expect(() => {
        loadConfig();
      }).toThrow();
    });
  });
});
