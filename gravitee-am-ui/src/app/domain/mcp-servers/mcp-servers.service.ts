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
import { Injectable } from '@angular/core';
import { Observable, shareReplay, map } from 'rxjs';

import {
  NewProtectedResourceRequest,
  NewProtectedResourceResponse,
  ProtectedResourceService,
} from '../../services/protected-resource.service';

@Injectable({
  providedIn: 'root',
})
export class McpServersService {
  constructor(private service: ProtectedResourceService) {}

  findByDomain(domainId: string, page: number, size: number): Observable<McpServer[]> {
    return this.service.findByDomain(domainId, page, size).pipe(
      map(
        (page) =>
          page.map(
            (elem) =>
              ({
                id: elem.id,
                name: elem.name,
                resourceIdentifier: elem?.resourceIdentifiers[0],
                tools: elem.tools,
                updatedAt: elem.updatedAt,
              }) as McpServer,
          ),
        shareReplay({ bufferSize: 1, refCount: true }),
      ),
    );
  }
  create(domainId: string, newMcpServer: NewMcpServer): Observable<NewProtectedResourceResponse> {
    const request = {
      name: newMcpServer.name,
      resourceIdentifiers: [newMcpServer.resourceIdentifier],
      description: newMcpServer.description,
      clientId: newMcpServer.clientId,
      clientSecret: newMcpServer.clientSecret,
      type: 'MCP_SERVER',
    } as NewProtectedResourceRequest;
    return this.service.create(domainId, request);
  }
}

export interface McpServer {
  id: string;
  name: string;
  resourceIdentifier: string;
  tools: string[];
  updatedAt: string;
}

export interface NewMcpServer {
  name: string;
  resourceIdentifier: string;
  description?: string;
  clientId?: string;
  clientSecret?: string;
}
