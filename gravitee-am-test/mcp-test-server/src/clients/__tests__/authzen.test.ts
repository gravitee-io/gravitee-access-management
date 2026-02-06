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
import { AuthZenClient } from '../authzen';
import { AuthZenAuthenticationError, AuthZenRequestError } from '../../utils/errors';
import type { Logger } from '../../utils/logger';
import type { AmConfig, AuthZenConfig } from '../../types/config';

jest.mock('axios');
const mockedAxios = axios as jest.Mocked<typeof axios>;
const mockedIsAxiosError = isAxiosError as jest.MockedFunction<typeof isAxiosError>;

describe('AuthZenClient', () => {
  let client: AuthZenClient;
  let mockLogger: Logger;
  let amConfig: AmConfig;
  let authzenConfig: AuthZenConfig;

  beforeEach(() => {
    mockLogger = {
      error: jest.fn(),
      warn: jest.fn(),
      info: jest.fn(),
      debug: jest.fn(),
    } as unknown as Logger;

    amConfig = {
      gatewayUrl: 'http://gateway:8092',
      domainHrid: 'test-domain',
    };

    authzenConfig = {
      url: 'http://gateway:8092',
    };

    mockedAxios.create.mockReturnValue({
      post: jest.fn(),
      interceptors: {
        request: { use: jest.fn() },
        response: { use: jest.fn() },
      },
    } as any);

    mockedIsAxiosError.mockReturnValue(false);

    client = new AuthZenClient(amConfig, authzenConfig, mockLogger);
  });

  describe('checkAuthorization', () => {
    const authRequest = {
      subject: { type: 'user', id: 'alice' },
      resource: { type: 'tool', id: 'mcp_tool_getTests' },
      action: { name: 'can_access' },
    };
    const mcpServerToken = 'mcp-server-token';

    it('should make POST request with correct endpoint, body, and Bearer token', async () => {
      const mockResponse = {
        data: {
          decision: true,
        },
      };

      const mockPost = jest.fn<() => Promise<typeof mockResponse>>().mockResolvedValue(mockResponse);
      (client as any).client = { post: mockPost };

      const result = await client.checkAuthorization(authRequest, mcpServerToken, 'test-domain');

      expect(mockPost).toHaveBeenCalledTimes(1);
      expect(mockPost).toHaveBeenCalledWith(
        '/test-domain/access/v1/evaluation',
        authRequest,
        {
          headers: {
            'Authorization': `Bearer ${mcpServerToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      expect(result).toEqual(mockResponse.data);
    });

    it('should use AuthZen URL when provided in config', () => {
      const customAuthzenConfig: AuthZenConfig = {
        url: 'http://authzen:8080',
      };
      new AuthZenClient(amConfig, customAuthzenConfig, mockLogger);

      expect(mockedAxios.create).toHaveBeenCalledWith(
        expect.objectContaining({
          baseURL: 'http://authzen:8080',
        })
      );
    });

    it('should fallback to Gateway URL when AuthZen URL not provided', () => {
      const noUrlConfig: AuthZenConfig = {};
      new AuthZenClient(amConfig, noUrlConfig, mockLogger);

      expect(mockedAxios.create).toHaveBeenCalledWith(
        expect.objectContaining({
          baseURL: 'http://gateway:8092',
        })
      );
    });

    it('should return DENY decision on 403 Forbidden response', async () => {
      const axiosError: any = {
        response: {
          status: 403,
          statusText: 'Forbidden',
        },
      };
      const mockPost = jest.fn<() => Promise<never>>().mockRejectedValue(axiosError);
      (client as any).client = { post: mockPost };
      mockedIsAxiosError.mockReturnValue(true);

      const result = await client.checkAuthorization(authRequest, mcpServerToken, 'test-domain');

      expect(result).toEqual({
        decision: false,
      });
    });

    it('should throw AuthZenAuthenticationError on 401 Unauthorized response', async () => {
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
        client.checkAuthorization(authRequest, mcpServerToken, 'test-domain')
      ).rejects.toThrow(AuthZenAuthenticationError);
    });

    it('should throw AuthZenRequestError on other HTTP errors', async () => {
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
        client.checkAuthorization(authRequest, mcpServerToken, 'test-domain')
      ).rejects.toThrow(AuthZenRequestError);
    });

    it('should propagate non-axios errors unchanged', async () => {
      const error = new Error('Network error');
      const mockPost = jest.fn<() => Promise<never>>().mockRejectedValue(error);
      (client as any).client = { post: mockPost };
      mockedIsAxiosError.mockReturnValue(false);

      await expect(
        client.checkAuthorization(authRequest, mcpServerToken, 'test-domain')
      ).rejects.toThrow('Network error');
    });
  });
});
