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
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { AppConfig } from '../../config/app.config';

import { Page, Sort, transformToQueryParam } from './api.model';

@Injectable()
export class ProtectedResourceService {
  private baseURL = AppConfig.settings.domainBaseURL;
  constructor(private http: HttpClient) {}

  findByDomain(
    domainId: string,
    type: ProtectedResourceType,
    page: number,
    size: number,
    sort: Sort,
    searchTerm?: string,
  ): Observable<Page<ProtectedResourcePrimaryData>> {
    let url = this.baseURL + `${domainId}/protected-resources?type=${type}&page=${page}&size=${size}&sort=${transformToQueryParam(sort)}`;
    if (searchTerm) {
      url += `&q=${searchTerm}`;
    }
    return this.http.get<Page<ProtectedResourcePrimaryData>>(url);
  }

  create(domainId: string, protectedResource: NewProtectedResourceRequest): Observable<NewProtectedResourceResponse> {
    return this.http.post<NewProtectedResourceResponse>(this.baseURL + `${domainId}/protected-resources`, protectedResource);
  }

  update(domainId: string, resourceId: string, protectedResource: UpdateProtectedResourceRequest): Observable<ProtectedResource> {
    return this.http.put<ProtectedResource>(this.baseURL + `${domainId}/protected-resources/${resourceId}`, protectedResource);
  }

  patch(domainId: string, resourceId: string, patchProtectedResource: PatchProtectedResourceRequest): Observable<ProtectedResource> {
    return this.http.patch<ProtectedResource>(this.baseURL + `${domainId}/protected-resources/${resourceId}`, patchProtectedResource);
  }

  findById(domainId: string, id: string, type: ProtectedResourceType): Observable<ProtectedResource> {
    return this.http.get<ProtectedResource>(this.baseURL + `${domainId}/protected-resources/${id}?type=${type}`);
  }

  delete(domainId: string, id: string, type: ProtectedResourceType): Observable<any> {
    return this.http.delete<any>(this.baseURL + `${domainId}/protected-resources/${id}?type=${type}`);
  }

  getSecrets(domainId: string, id: string): Observable<ClientSecret[]> {
    return this.http.get<any>(this.baseURL + `${domainId}/protected-resources/${id}/secrets`).pipe(
      map((response) => {
        if (Array.isArray(response)) {
          return response;
        }
        return response.clientSecrets || [];
      }),
    );
  }

  createSecret(domainId: string, id: string, name: string): Observable<ClientSecret> {
    return this.http.post<ClientSecret>(this.baseURL + `${domainId}/protected-resources/${id}/secrets`, { name });
  }

  renewSecret(domainId: string, id: string, secretId: string): Observable<ClientSecret> {
    return this.http.post<ClientSecret>(this.baseURL + `${domainId}/protected-resources/${id}/secrets/${secretId}/_renew`, {});
  }

  deleteSecret(domainId: string, id: string, secretId: string): Observable<any> {
    return this.http.delete<any>(this.baseURL + `${domainId}/protected-resources/${id}/secrets/${secretId}`);
  }
}

export enum ProtectedResourceType {
  MCP_SERVER = 'MCP_SERVER',
}

export enum ProtectedResourceFeatureType {
  MCP_TOOL = 'MCP_TOOL',
}

export interface ProtectedResourcePrimaryData {
  id: string;
  name: string;
  type: ProtectedResourceType;
  resourceIdentifiers: string[];
  updatedAt: string;
  features: ProtectedResourceFeature[];
  settings?: any;
}

export interface ProtectedResource extends ProtectedResourcePrimaryData {
  description?: string;
  domainId: string;
  clientId: string;
  certificate?: string;
  clientSecrets?: ClientSecret[];
  secretSettings?: any;
}

export interface ProtectedResourceFeature {
  key: string;
  description: string;
  type: ProtectedResourceFeatureType;
  createdAt?: string;
}

export interface NewProtectedResourceRequest {
  name: string;
  type: ProtectedResourceType;
  resourceIdentifiers: string[];
  description?: string;
  clientId?: string;
  clientSecret?: string;
  features: ProtectedResourceFeature[];
  settings?: any;
}

export interface NewProtectedResourceResponse extends ProtectedResource {
  clientSecret: string;
}

export interface UpdateProtectedResourceRequest {
  name: string;
  resourceIdentifiers: string[];
  description?: string;
  features: ProtectedResourceFeature[];
  settings?: any;
}

export interface PatchProtectedResourceRequest {
  name?: string;
  description?: string;
  resourceIdentifiers?: string[];
  features?: ProtectedResourceFeature[];
  secretSettings?: any;
  settings?: any;
  certificate?: string;
}

export interface ClientSecret {
  id: string;
  name: string;
  value?: string;
}
