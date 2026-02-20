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
import { Request, Response } from 'express';
import { createDeleteTestHandler } from '../deleteTest';
import { TestMetadataManager } from '../../auth/test-metadata-manager';

describe('deleteTest tool', () => {
  let handler: (req: Request, res: Response) => void;
  let testMetadataManager: TestMetadataManager;
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;

  beforeEach(() => {
    testMetadataManager = new TestMetadataManager();
    handler = createDeleteTestHandler(testMetadataManager);

    mockResponse = {
      json: jest.fn(),
      status: jest.fn().mockReturnThis(),
    } as unknown as Response;
  });

  it('should return success response with testId and testId when metadata and testId are valid', () => {
    testMetadataManager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
    mockRequest = {
      body: {
        testId: 'test-456',
      },
    };

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.json).toHaveBeenCalledTimes(1);
    expect(mockResponse.json).toHaveBeenCalledWith({
      testId: 'test-123',
      message: 'deleteTest tool responded',
      deletedTestId: 'test-456',
    });
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  it('should return 400 error when testId is missing from request body', () => {
    testMetadataManager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
    mockRequest = {
      body: {},
    };

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.status).toHaveBeenCalledWith(400);
    expect(mockResponse.json).toHaveBeenCalledWith({
      error: 'testId is required and must be a non-empty string',
    });
  });

  it('should return 400 error when testId is not a string', () => {
    testMetadataManager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
    mockRequest = {
      body: {
        testId: 123,
      },
    };

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.status).toHaveBeenCalledWith(400);
    expect(mockResponse.json).toHaveBeenCalledWith({
      error: 'testId is required and must be a non-empty string',
    });
  });

  it('should return 400 error when testId is empty string', () => {
    testMetadataManager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');
    mockRequest = {
      body: {
        testId: '',
      },
    };

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.status).toHaveBeenCalledWith(400);
    expect(mockResponse.json).toHaveBeenCalledWith({
      error: 'testId is required and must be a non-empty string',
    });
  });

  it('should return 503 error when metadata is not configured', () => {
    mockRequest = {
      body: {
        testId: 'test-456',
      },
    };

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.status).toHaveBeenCalledWith(503);
    expect(mockResponse.json).toHaveBeenCalledWith({
      error: 'Test metadata not configured',
    });
  });
});
