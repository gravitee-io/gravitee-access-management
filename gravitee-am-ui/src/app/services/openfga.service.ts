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

import { AppConfig } from '../../config/app.config';

@Injectable()
export class OpenFGAService {
  private domainsURL: string = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  getAuthorizationModel(domainId: string, engineId: string): Observable<any> {
    return this.http.get<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/authorization-model`);
  }

  getStore(domainId: string, engineId: string): Observable<any> {
    return this.http.get<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/store`);
  }

  listTuples(domainId: string, engineId: string, pageSize: number, continuationToken?: string): Observable<any> {
    let params = `pageSize=${pageSize}`;
    if (continuationToken) {
      params += `&continuationToken=${continuationToken}`;
    }
    return this.http.get<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/tuples?${params}`);
  }

  addTuple(domainId: string, engineId: string, tuple: any): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/tuples`, tuple);
  }

  deleteTuple(domainId: string, engineId: string, tuple: any): Observable<any> {
    return this.http.request<any>('delete', `${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/tuples`, {
      body: tuple,
    });
  }

  checkPermission(domainId: string, engineId: string, permissionRequest: any): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/check`, permissionRequest);
  }
}
