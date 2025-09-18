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

export interface OpenFGAConfiguration {
  apiUrl: string;
  storeId?: string;
}

export interface OpenFGAStore {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable()
export class OpenFGAService {
  private domainsURL: string = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  connect(domainId: string, serverUrl: string): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/openfga/connect`, { serverUrl });
  }

  listStores(domainId: string): Observable<OpenFGAStore[]> {
    return this.http.get<OpenFGAStore[]>(`${this.domainsURL}${domainId}/openfga/stores`);
  }

  createStore(domainId: string, storeName: string): Observable<OpenFGAStore> {
    return this.http.post<OpenFGAStore>(`${this.domainsURL}${domainId}/openfga/stores`, { name: storeName });
  }

  deleteStore(domainId: string, storeId: string): Observable<void> {
    return this.http.delete<void>(`${this.domainsURL}${domainId}/openfga/stores/${storeId}`);
  }

  updateAuthorizationModel(domainId: string, authorizationModel: string, storeId: string): Observable<any> {
    return this.http.put<any>(`${this.domainsURL}${domainId}/openfga/${storeId}/authorization-model`, { authorizationModel });
  }

  getAuthorizationModel(domainId: string, storeId: string): Observable<any> {
    return this.http.get<any>(`${this.domainsURL}${domainId}/openfga/${storeId}/authorization-model`);
  }

  listTuples(domainId: string, storeId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.domainsURL}${domainId}/openfga/${storeId}/tuples`);
  }

  addTuple(domainId: string, storeId: string, tuple: any): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/openfga/${storeId}/tuples`, tuple);
  }


  checkPermission(domainId: string, storeId: string, permissionRequest: any): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/openfga/${storeId}/check-permission`, permissionRequest);
  }
}
