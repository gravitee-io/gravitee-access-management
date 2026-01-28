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
import axios, { isAxiosError } from 'axios';
import { AmGatewayClient } from '../am-gateway';
import type { Logger } from '../../utils/logger';
import type { AmConfig } from '../../types/config';

jest.mock('axios');
const mockedAxios = axios as jest.Mocked<typeof axios>;
const mockedIsAxiosError = isAxiosError as jest.MockedFunction<typeof isAxiosError>;

describe('AmGatewayClient', () => {
  let client: AmGatewayClient;
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

    mockedAxios.create.mockReturnValue({
      post: jest.fn(),
      interceptors: {
        request: { use: jest.fn() },
        response: { use: jest.fn() },
      },
    } as any);

    mockedIsAxiosError.mockReturnValue(false);

    client = new AmGatewayClient(config, mockLogger);
  });

  describe('introspectToken', () => {
    it('should make POST request with correct endpoint and Basic Auth', async () => {
      const mockResponse = {
        data: {
          active: true,
          sub: 'user-123',
          client_id: 'client-456',
        },
      };

      const mockPost = jest.fn<() => Promise<typeof mockResponse>>().mockResolvedValue(mockResponse);
      (client as any).client = { post: mockPost };

      const result = await client.introspectToken('token-abc', 'client-id', 'client-secret');

      expect(mockPost).toHaveBeenCalledTimes(1);
      expect(mockPost).toHaveBeenCalledWith(
        '/test-domain/oauth/introspect',
        expect.any(URLSearchParams),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/x-www-form-urlencoded',
            'Authorization': expect.stringContaining('Basic'),
          }),
        })
      );

      // Verify Basic Auth encoding
      const callArgs: any = mockPost.mock.calls[0];
      const authHeader = callArgs[2]?.headers?.Authorization;
      const base64Auth = authHeader.replace('Basic ', '');
      const decoded = Buffer.from(base64Auth, 'base64').toString('utf-8');
      expect(decoded).toBe('client-id:client-secret');

      expect(result).toEqual(mockResponse.data);
    });

    it('should include token and token_type_hint in request body', async () => {
      const mockResponse = { data: { active: true } };
      const mockPost = jest.fn<(...args: any[]) => Promise<typeof mockResponse>>().mockResolvedValue(mockResponse);
      (client as any).client = { post: mockPost };

      await client.introspectToken('token-abc', 'client-id', 'client-secret');

      const callArgs: any = mockPost.mock.calls[0];
      const urlParams = callArgs[1] as URLSearchParams;
      expect(urlParams.get('token')).toBe('token-abc');
      expect(urlParams.get('token_type_hint')).toBe('access_token');
    });

    it('should throw descriptive error on axios error with status code (non-auth)', async () => {
      const axiosError: any = {
        response: {
          status: 500,
          statusText: 'Internal Server Error',
        },
      };
      const mockPost = jest.fn<() => Promise<never>>().mockRejectedValue(axiosError);
      (client as any).client = { post: mockPost };
      mockedIsAxiosError.mockReturnValue(true);

      await expect(
        client.introspectToken('token-abc', 'client-id', 'client-secret')
      ).rejects.toThrow('Token introspection failed: 500 Internal Server Error');
    });

    it('should map 401/403 axios errors to UnauthorizedError', async () => {
      const axiosError: any = {
        response: {
          status: 401,
          statusText: 'Unauthorized',
        },
      };
      const mockPost = jest.fn<() => Promise<never>>().mockRejectedValue(axiosError);
      (client as any).client = { post: mockPost };
      mockedIsAxiosError.mockReturnValue(true);

      await expect(
        client.introspectToken('token-abc', 'client-id', 'client-secret')
      ).rejects.toMatchObject({ name: 'UnauthorizedError' });
    });

    it('should propagate non-axios errors unchanged', async () => {
      const error = new Error('Network error');
      const mockPost = jest.fn<() => Promise<never>>().mockRejectedValue(error);
      (client as any).client = { post: mockPost };
      mockedIsAxiosError.mockReturnValue(false);

      await expect(
        client.introspectToken('token-abc', 'client-id', 'client-secret')
      ).rejects.toThrow('Network error');
    });
  });
});
