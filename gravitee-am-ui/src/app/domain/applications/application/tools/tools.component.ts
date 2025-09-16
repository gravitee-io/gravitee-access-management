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
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { SnackbarService } from '../../../../services/snackbar.service';
import { EntrypointService } from '../../../../services/entrypoint.service';
import { DomainStoreService } from '../../../../stores/domain.store';

@Component({
  selector: 'application-overview',
  templateUrl: './tools.component.html',
  styleUrls: ['./tools.component.scss'],
  standalone: false,
})
export class ApplicationToolsComponent implements OnInit {
  application: any;
  entrypoint: any;
  private baseUrl: string;
  private domain: any;
  @ViewChild('copyText', { read: ElementRef }) copyText: ElementRef;
  
  // View mode switcher
  viewMode: 'cards' | 'table' = 'table';

  constructor(
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private entrypointService: EntrypointService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.application = this.route.snapshot.data['application'];
    this.baseUrl = this.entrypointService.resolveBaseUrl(this.entrypoint, this.domain);
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }


  isMCPApplication(): boolean {
    return true;
  }

  getMCPTools(): any[] {
    // Return mock data for demonstration purposes
    return [
      {
        name: 'filesystem',
        description: 'Tool for reading and writing files on the local filesystem. Supports common file operations like create, read, update, and delete.',
        requiredScopes: ['filesystem:read', 'filesystem:write', 'filesystem:delete'],
        inputSchema: {
          type: 'object',
          properties: {
            operation: {
              type: 'string',
              enum: ['read', 'write', 'delete', 'list'],
              description: 'The file operation to perform'
            },
            path: {
              type: 'string',
              description: 'The file or directory path'
            },
            content: {
              type: 'string',
              description: 'Content to write (required for write operations)'
            }
          },
          required: ['operation', 'path']
        }
      },
      {
        name: 'web_search',
        description: 'Tool for performing web searches and retrieving search results from various search engines.',
        requiredScopes: ['web:search', 'web:results'],
        inputSchema: {
          type: 'object',
          properties: {
            query: {
              type: 'string',
              description: 'The search query string'
            },
            engine: {
              type: 'string',
              enum: ['google', 'bing', 'duckduckgo'],
              default: 'google',
              description: 'The search engine to use'
            },
            maxResults: {
              type: 'integer',
              minimum: 1,
              maximum: 50,
              default: 10,
              description: 'Maximum number of results to return'
            }
          },
          required: ['query']
        }
      },
      {
        name: 'database_query',
        description: 'Tool for executing database queries against configured database connections.',
        requiredScopes: ['database:read', 'database:write'],
        inputSchema: {
          type: 'object',
          properties: {
            connection: {
              type: 'string',
              description: 'Database connection identifier'
            },
            query: {
              type: 'string',
              description: 'SQL query to execute'
            },
            parameters: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  name: { type: 'string' },
                  value: { type: 'string' },
                  type: { type: 'string' }
                }
              },
              description: 'Query parameters'
            }
          },
          required: ['connection', 'query']
        }
      },
      {
        name: 'email_sender',
        description: 'Tool for sending emails through configured SMTP servers with support for HTML and plain text content.',
        requiredScopes: ['email:send'],
        inputSchema: {
          type: 'object',
          properties: {
            to: {
              type: 'array',
              items: { type: 'string' },
              description: 'Recipient email addresses'
            },
            subject: {
              type: 'string',
              description: 'Email subject line'
            },
            body: {
              type: 'string',
              description: 'Email body content'
            },
            isHtml: {
              type: 'boolean',
              default: false,
              description: 'Whether the body contains HTML content'
            },
            attachments: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  filename: { type: 'string' },
                  content: { type: 'string' },
                  contentType: { type: 'string' }
                }
              },
              description: 'Email attachments'
            }
          },
          required: ['to', 'subject', 'body']
        }
      },
      {
        name: 'api_client',
        description: 'Tool for making HTTP requests to external APIs with support for various authentication methods.',
        requiredScopes: ['api:request'],
        inputSchema: {
          type: 'object',
          properties: {
            url: {
              type: 'string',
              format: 'uri',
              description: 'The API endpoint URL'
            },
            method: {
              type: 'string',
              enum: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
              default: 'GET',
              description: 'HTTP method'
            },
            headers: {
              type: 'object',
              additionalProperties: { type: 'string' },
              description: 'HTTP headers'
            },
            body: {
              type: 'string',
              description: 'Request body (for POST, PUT, PATCH)'
            },
            timeout: {
              type: 'integer',
              minimum: 1000,
              maximum: 30000,
              default: 5000,
              description: 'Request timeout in milliseconds'
            }
          },
          required: ['url']
        }
      },
      {
        name: 'image_processor',
        description: 'Tool for processing and manipulating images including resizing, cropping, format conversion, and applying filters.',
        requiredScopes: ['image:process', 'image:read', 'image:write'],
        inputSchema: {
          type: 'object',
          properties: {
            operation: {
              type: 'string',
              enum: ['resize', 'crop', 'convert', 'filter', 'rotate'],
              description: 'The image processing operation'
            },
            inputImage: {
              type: 'string',
              description: 'Base64 encoded input image data'
            },
            outputFormat: {
              type: 'string',
              enum: ['jpeg', 'png', 'gif', 'webp'],
              default: 'jpeg',
              description: 'Output image format'
            },
            parameters: {
              type: 'object',
              description: 'Operation-specific parameters (dimensions, quality, etc.)'
            }
          },
          required: ['operation', 'inputImage']
        }
      }
    ];
  }

  getMCPUrl(): string {
    // Return mock URL for demonstration purposes
    return this.application?.settings?.mcp?.url || 'https://mcp.example.com/api/v1';
  }

  copyToClipboard(text: string, message: string): void {
    this.snackbarService.open(message);
    // The actual copying is handled by the ngxClipboard directive
  }

  setViewMode(mode: 'cards' | 'table'): void {
    this.viewMode = mode;
  }

  isCardMode(): boolean {
    return this.viewMode === 'cards';
  }

  isTableMode(): boolean {
    return this.viewMode === 'table';
  }

  viewSchema(tool: any): void {
    if (tool.inputSchema) {
      const schemaJson = JSON.stringify(tool.inputSchema, null, 2);
      this.copyToClipboard(schemaJson, 'Schema copied to clipboard');
    }
  }
}
