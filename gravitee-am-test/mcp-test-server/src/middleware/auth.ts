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
 * Authentication and authorization middleware
 */

import type { Request, Response, NextFunction } from 'express';
import { IntrospectionService } from '../auth/introspection';
import { AuthZenClient } from '../clients/authzen';
import { TestMetadataManager } from '../auth/test-metadata-manager';
import { UnauthorizedError, ForbiddenError } from '../utils/errors';
import type { AppConfig } from '../types/config';
import type { Logger } from '../utils/logger';
import type { IntrospectionResult, AuthZenRequest } from '../types/auth';

export interface AuthRequest extends Request {
  subject?: string;
  introspectionResult?: IntrospectionResult;
}

/**
 * Tools that require read-only access (can_access)
 */
const READ_ONLY_TOOLS = new Set(['getTests']);

/**
 * Build AuthZen evaluation request from tool name and subject
 */
function buildAuthZenRequest(subjectId: string, toolName: string): AuthZenRequest {
  const actionName = READ_ONLY_TOOLS.has(toolName) ? 'can_access' : 'can_manage';

  return {
    subject: {
      type: 'user',
      id: subjectId,
    },
    resource: {
      type: 'tool',
      // Use underscore instead of colon for OpenFGA compatibility (colons are not allowed in object IDs)
      id: `mcp_tool_${toolName}`,
    },
    action: {
      name: actionName,
    },
  };
}

export function createAuthMiddleware(
  config: AppConfig,
  logger: Logger,
  testMetadataManager: TestMetadataManager
) {
  const introspectionService = new IntrospectionService(config.am, logger);
  const authzenClient = new AuthZenClient(config.am, config.authzen, logger);

  return async (req: AuthRequest, res: Response, next: NextFunction): Promise<void> => {
    try {
      // Extract Bearer token
      const authHeader = req.headers.authorization;
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        throw new UnauthorizedError('Missing or invalid Authorization header');
      }

      const clientToken = authHeader.substring(7);

      // Get Protected Resource credentials and domain for introspection/AuthZen
      const credentials = testMetadataManager.getClientCredentials();
      const domainHrid = testMetadataManager.getDomainHrid();
      if (!credentials || !domainHrid) {
        logger.error('Missing metadata', {
          hasCredentials: !!credentials,
          hasDomainHrid: !!domainHrid,
        });
        throw new UnauthorizedError(
          'Protected Resource credentials or domain HRID not configured. Test metadata must be injected first.'
        );
      }
      
      logger.debug('Using dynamic domain for introspection/AuthZen', {
        domainHrid,
        clientId: credentials.clientId,
        toolName: req.path.split('/').pop(),
      });

      // Introspect client token
      const introspectionResult = await introspectionService.introspect(
        clientToken,
        credentials.clientId,
        credentials.clientSecret,
        domainHrid
      );

      // Extract subject
      const subject = introspectionService.extractSubject(introspectionResult);
      if (!subject) {
        throw new UnauthorizedError('Unable to extract subject from token');
      }

      // Get MCP server's own token for AuthZen
      const mcpServerToken = testMetadataManager.getToken();
      if (!mcpServerToken) {
        throw new UnauthorizedError('MCP server token not configured. Test metadata must be injected first.');
      }

      // Determine tool name from route
      const toolName = req.path.split('/').pop() || 'unknown';

      // Build AuthZen evaluation request
      const authzenRequest = buildAuthZenRequest(subject, toolName);

      // Check authorization via AuthZen
      const authzenResponse = await authzenClient.checkAuthorization(
        authzenRequest,
        mcpServerToken,
        domainHrid
      );

      if (!authzenResponse.decision) {
        throw new ForbiddenError('Access denied by authorization policy');
      }

      // Attach subject and introspection result to request
      req.subject = subject;
      req.introspectionResult = introspectionResult;

      next();
    } catch (error) {
      if (error instanceof UnauthorizedError || error instanceof ForbiddenError) {
        res.status(error.statusCode).json({
          error: error.message,
          code: error.code,
        });
      } else {
        const safeError =
          error instanceof Error
            ? { name: error.name, message: error.message, stack: error.stack }
            : { message: String(error) };

        logger.error('Auth middleware error', safeError);
        res.status(500).json({ error: 'Internal server error' });
      }
    }
  };
}
