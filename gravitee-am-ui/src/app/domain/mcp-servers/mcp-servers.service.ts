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
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { Page, Sort } from '../../services/api.model';
import {
  NewProtectedResourceRequest,
  NewProtectedResourceResponse,
  PatchProtectedResourceRequest,
  ProtectedResource,
  ProtectedResourceFeatureType,
  ProtectedResourceService,
  ProtectedResourceType,
  ClientSecret,
} from '../../services/protected-resource.service';

@Injectable({
  providedIn: 'root',
})
export class McpServersService {
  constructor(private readonly service: ProtectedResourceService) {}

  findByDomain(domainId: string, page: number, size: number, sort: Sort, searchTerm?: string): Observable<Page<McpServer>> {
    return this.service.findByDomain(domainId, ProtectedResourceType.MCP_SERVER, page, size, sort, searchTerm).pipe(
      map(
        (page) =>
          ({
            ...page,
            data: page.data.map(
              (elem) =>
                ({
                  id: elem.id,
                  name: elem.name,
                  resourceIdentifier: elem?.resourceIdentifiers?.[0],
                  tools: elem.features
                    ? elem.features.map((feat) => ({
                        key: feat.key,
                        description: feat.description,
                        scopes: feat['scopes'],
                      }))
                    : [],
                  updatedAt: elem.updatedAt,
                }) as McpServer,
            ),
          }) as Page<McpServer>,
      ),
    );
  }
  create(domainId: string, newMcpServer: NewMcpServer): Observable<NewProtectedResourceResponse> {
    const request = {
      name: newMcpServer.name?.trim(),
      resourceIdentifiers: [newMcpServer.resourceIdentifier.toLowerCase().trim()],
      description: newMcpServer.description?.trim(),
      clientId: newMcpServer.clientId?.trim(),
      clientSecret: newMcpServer.clientSecret,
      type: ProtectedResourceType.MCP_SERVER,
      features: newMcpServer.tools.map((tool) => ({
        ...tool,
        type: ProtectedResourceFeatureType.MCP_TOOL,
      })),
    } as NewProtectedResourceRequest;
    return this.service.create(domainId, request);
  }

  patch(domainId: string, id: string, patchProtectedResource: PatchProtectedResourceRequest): Observable<ProtectedResource> {
    return this.service.patch(domainId, id, patchProtectedResource);
  }

  delete(domainId: string, id: string): Observable<any> {
    return this.service.delete(domainId, id, ProtectedResourceType.MCP_SERVER);
  }

  getSecrets(domainId: string, id: string): Observable<ClientSecret[]> {
    return this.service.getSecrets(domainId, id);
  }

  createSecret(domainId: string, id: string, name: string): Observable<ClientSecret> {
    return this.service.createSecret(domainId, id, name);
  }

  renewSecret(domainId: string, id: string, secretId: string): Observable<ClientSecret> {
    return this.service.renewSecret(domainId, id, secretId);
  }

  deleteSecret(domainId: string, id: string, secretId: string): Observable<any> {
    return this.service.deleteSecret(domainId, id, secretId);
  }
}

export interface McpServer {
  id: string;
  name: string;
  resourceIdentifier: string;
  updatedAt: string;
  tools: McpServerTool[];
}

export interface NewMcpServer {
  name: string;
  resourceIdentifier: string;
  description?: string;
  clientId?: string;
  clientSecret?: string;
  tools: McpServerTool[];
}

export interface McpServerTool {
  key: string;
  description: string;
  scopes: string[];
}
