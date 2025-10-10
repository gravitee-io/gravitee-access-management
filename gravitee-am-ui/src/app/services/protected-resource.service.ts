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
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { AppConfig } from '../../config/app.config';

@Injectable()
export class ProtectedResourceService {
  private baseURL = AppConfig.settings.domainBaseURL;
  constructor(private http: HttpClient) {}

  findByDomain(domainId: string, page: number, size: number): Observable<ProtectedResource[]> {
    console.log(`${domainId}, ${page}, ${size}`);
    return of([
      {
        id: '4a4eb41e-8432-473e-8eb4-1e8432073e80',
        name: 'Banking MCP Server',
        resourceIdentifiers: ['https://something.org'],
        tools: ['makePayment', 'createAccount'],
        updatedAt: '2025-10-09 14:30:00',
        type: 'MCP_SERVER',
      } as ProtectedResource, // TODO AM-5758
    ]);
  }

  create(domainId: string, protectedResource: NewProtectedResourceRequest): Observable<NewProtectedResourceResponse> {
    return this.http.post<NewProtectedResourceResponse>(this.baseURL + domainId + '/protected-resources', protectedResource);
  }
}

export interface ProtectedResource {
  id: string;
  name: string;
  type: 'MCP_SERVER';
  resourceIdentifiers: string[];
  description?: string;

  tools: string[];

  domainId: string;
  clientId: string;

  updatedAt: string;
  createdAt: string;
}

export interface NewProtectedResourceRequest {
  name: string;
  type: 'MCP_SERVER';
  resourceIdentifiers: string[];
  description?: string;
  clientId?: string;
  clientSecret?: string;
}

export interface NewProtectedResourceResponse extends ProtectedResource {
  clientSecret: string;
}
