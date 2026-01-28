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
import { createGetTestsHandler } from '../getTests';
import { TestMetadataManager } from '../../auth/test-metadata-manager';

describe('getTests tool', () => {
  let handler: (req: Request, res: Response) => void;
  let testMetadataManager: TestMetadataManager;
  let mockRequest: Partial<Request>;
  let mockResponse: Partial<Response>;

  beforeEach(() => {
    testMetadataManager = new TestMetadataManager();
    handler = createGetTestsHandler(testMetadataManager);

    mockRequest = {};
    mockResponse = {
      json: jest.fn(),
      status: jest.fn().mockReturnThis(),
    } as unknown as Response;
  });

  it('should return tests with testId when metadata is configured', () => {
    testMetadataManager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', undefined, 'test-domain');

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.json).toHaveBeenCalledTimes(1);
    expect(mockResponse.json).toHaveBeenCalledWith({
      testId: 'test-123',
      message: 'getTests tool responded',
      tests: [
        { id: '1', text: 'Sample test 1' },
        { id: '2', text: 'Sample test 2' },
      ],
    });
    expect(mockResponse.status).not.toHaveBeenCalled();
  });

  it('should return 503 error when metadata is not configured', () => {
    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.status).toHaveBeenCalledWith(503);
    expect(mockResponse.json).toHaveBeenCalledWith({
      error: 'Test metadata not configured',
    });
  });

  it('should return 503 error when metadata is expired', () => {
    testMetadataManager.setMetadata('test-123', 'token-abc', 'client-id', 'client-secret', -1, 'test-domain');

    handler(mockRequest as Request, mockResponse as Response);

    expect(mockResponse.status).toHaveBeenCalledWith(503);
    expect(mockResponse.json).toHaveBeenCalledWith({
      error: 'Test metadata not configured',
    });
  });
});
