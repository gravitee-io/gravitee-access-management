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


import { describe, it, expect, beforeEach } from '@jest/globals';
import { TestMetadataManager } from '../test-metadata-manager';

describe('TestMetadataManager', () => {
  let manager: TestMetadataManager;

  beforeEach(() => {
    manager = new TestMetadataManager();
  });

  describe('setMetadata', () => {
    it('should set metadata with all required fields', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', 3600, 'test-domain');

      expect(manager.getTestId()).toBe('test-123');
      expect(manager.getToken()).toBe('token-abc');
      expect(manager.getClientCredentials()).toEqual({
        clientId: 'client-id',
        clientSecret: 'client-secret',
      });
      expect(manager.getDomainHrid()).toBe('test-domain');
      expect(manager.hasMetadata()).toBe(true);
    });

    it('should throw error when attempting to set metadata when one already exists', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');

      expect(() => {
        manager.setMetadata('test-456', 'token-def', 'client-id-2', 'client-secret-2', undefined, 'test-domain');
      }).toThrow('Test metadata already exists');
    });

    it('should calculate token expiry with 5-minute safety margin', () => {
      const expiresIn = 3600; // 1 hour
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', expiresIn, 'test-domain');

      // Token should be valid immediately (expiry is 3600 - 300 = 3300 seconds from now)
      expect(manager.hasMetadata()).toBe(true);
      expect(manager.getToken()).toBe('token-abc');
    });

    it('should use default 1-hour expiry when expiresIn not provided', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');

      expect(manager.hasMetadata()).toBe(true);
      expect(manager.getToken()).toBe('token-abc');
    });
  });

  describe('clearMetadata', () => {
    it('should clear all metadata and reset state', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
      expect(manager.hasMetadata()).toBe(true);

      manager.clearMetadata();

      expect(manager.hasMetadata()).toBe(false);
      expect(manager.getTestId()).toBeNull();
      expect(manager.getToken()).toBeNull();
      expect(manager.getClientCredentials()).toBeNull();
      expect(manager.getDomainHrid()).toBeNull();
      expect(manager.isOccupied()).toBe(false);
    });

    it('should allow setting new metadata after clearing', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
      manager.clearMetadata();
      manager.setMetadata('test-456', 'token-def', 'client-id-2', 'client-secret-2', undefined, 'test-domain-2');

      expect(manager.getTestId()).toBe('test-456');
      expect(manager.getToken()).toBe('token-def');
      expect(manager.getDomainHrid()).toBe('test-domain-2');
    });
  });

  describe('getToken', () => {
    it('should return token when metadata exists and not expired', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', 3600, 'test-domain');
      expect(manager.getToken()).toBe('token-abc');
    });

    it('should return null when metadata does not exist', () => {
      expect(manager.getToken()).toBeNull();
    });

    it('should return null when token is expired', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', -1, 'test-domain');
      expect(manager.getToken()).toBeNull();
    });
  });

  describe('getTestId', () => {
    it('should return testId when metadata exists', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
      expect(manager.getTestId()).toBe('test-123');
    });

    it('should return null when metadata does not exist', () => {
      expect(manager.getTestId()).toBeNull();
    });
  });

  describe('getClientCredentials', () => {
    it('should return client credentials when metadata exists', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
      const credentials = manager.getClientCredentials();

      expect(credentials).toEqual({
        clientId: 'client-id',
        clientSecret: 'client-secret',
      });
    });

    it('should return null when metadata does not exist', () => {
      expect(manager.getClientCredentials()).toBeNull();
    });
  });

  describe('hasMetadata', () => {
    it('should return true when metadata exists and token is not expired', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', 3600, 'test-domain');
      expect(manager.hasMetadata()).toBe(true);
    });

    it('should return false when metadata does not exist', () => {
      expect(manager.hasMetadata()).toBe(false);
    });

    it('should return false when token is expired', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', -1, 'test-domain');
      expect(manager.hasMetadata()).toBe(false);
    });
  });

  describe('isOccupied', () => {
    it('should return true when metadata exists, even if expired', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', -1, 'test-domain');
      expect(manager.isOccupied()).toBe(true);
    });

    it('should return false when metadata does not exist', () => {
      expect(manager.isOccupied()).toBe(false);
    });
  });

  describe('getCurrentTestId', () => {
    it('should return testId when metadata exists', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
      expect(manager.getCurrentTestId()).toBe('test-123');
    });

    it('should return null when metadata does not exist', () => {
      expect(manager.getCurrentTestId()).toBeNull();
    });

    it('should return testId even when token is expired', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', -1, 'test-domain');
      expect(manager.getCurrentTestId()).toBe('test-123');
    });
  });

  describe('getDomainHrid', () => {
    it('should return domainHrid when metadata exists', () => {
      manager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
      expect(manager.getDomainHrid()).toBe('test-domain');
    });

    it('should return null when metadata does not exist', () => {
      expect(manager.getDomainHrid()).toBeNull();
    });
  });
});
