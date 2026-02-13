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


import { describe, it, expect, beforeEach, jest } from '@jest/globals';
import { IntrospectionService } from '../introspection';
import { AmGatewayClient } from '../../clients/am-gateway';
import type { Logger } from '../../utils/logger';
import type { AmConfig } from '../../types/config';

jest.mock('../../clients/am-gateway');

describe('IntrospectionService', () => {
  let service: IntrospectionService;
  let mockClient: jest.Mocked<AmGatewayClient>;
  let mockLogger: Logger;
  let config: AmConfig;

  beforeEach(() => {
    mockLogger = {
      error: jest.fn(),
      warn: jest.fn(),
      info: jest.fn(),
      debug: jest.fn(),
    } as unknown as Logger;

    config = {
      gatewayUrl: 'http://gateway:8092',
      domainHrid: 'test-domain',
    };

    mockClient = {
      introspectToken: jest.fn(),
    } as unknown as jest.Mocked<AmGatewayClient>;

    (AmGatewayClient as jest.MockedClass<typeof AmGatewayClient>).mockImplementation(() => mockClient);

    service = new IntrospectionService(config, mockLogger);
  });

  describe('introspect', () => {
    it('should delegate to client with correct parameters', async () => {
      const mockResult = {
        active: true,
        sub: 'user-123',
        client_id: 'client-456',
      };

      mockClient.introspectToken.mockResolvedValue(mockResult);

      const result = await service.introspect('token-abc', 'client-id', 'client-secret');

      expect(mockClient.introspectToken).toHaveBeenCalledTimes(1);
      expect(mockClient.introspectToken).toHaveBeenCalledWith(
        'token-abc',
        'client-id',
        'client-secret',
        undefined // domainHrid override (optional)
      );
      expect(result).toEqual(mockResult);
    });

    it('should propagate errors from client', async () => {
      const error = new Error('Introspection failed');
      mockClient.introspectToken.mockRejectedValue(error);

      await expect(
        service.introspect('token-abc', 'client-id', 'client-secret')
      ).rejects.toThrow('Introspection failed');
    });
  });

  describe('extractSubject', () => {
    it('should return sub when present', () => {
      const result = service.extractSubject({
        active: true,
        sub: 'user-123',
      });

      expect(result).toBe('user-123');
    });

    it('should return client_id when sub is not present', () => {
      const result = service.extractSubject({
        active: true,
        client_id: 'client-456',
      });

      expect(result).toBe('client-456');
    });

    it('should return null when neither sub nor client_id is present', () => {
      const result = service.extractSubject({
        active: true,
      });

      expect(result).toBeNull();
    });

    it('should prefer sub over client_id when both are present', () => {
      const result = service.extractSubject({
        active: true,
        sub: 'user-123',
        client_id: 'client-456',
      });

      expect(result).toBe('user-123');
    });
  });
});
