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
export class AuthorizationSchemaService {
  private baseURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  findByDomain(domainId: string): Observable<any> {
    return this.http.get<any>(this.baseURL + domainId + '/authorization/schemas');
  }

  get(domainId: string, id: string): Observable<any> {
    return this.http.get<any>(this.baseURL + domainId + '/authorization/schemas/' + id);
  }

  create(domainId: string, schema: any): Observable<any> {
    return this.http.post<any>(this.baseURL + domainId + '/authorization/schemas', schema);
  }

  update(domainId: string, id: string, schema: any): Observable<any> {
    return this.http.put<any>(this.baseURL + domainId + '/authorization/schemas/' + id, schema);
  }

  delete(domainId: string, id: string): Observable<any> {
    return this.http.delete<any>(this.baseURL + domainId + '/authorization/schemas/' + id);
  }

  getVersions(domainId: string, id: string): Observable<any> {
    return this.http.get<any>(this.baseURL + domainId + '/authorization/schemas/' + id + '/versions');
  }

  getVersion(domainId: string, id: string, version: number): Observable<any> {
    return this.http.get<any>(this.baseURL + domainId + '/authorization/schemas/' + id + '/versions/' + version);
  }

  restoreVersion(domainId: string, id: string, version: number): Observable<any> {
    return this.http.post<any>(this.baseURL + domainId + '/authorization/schemas/' + id + '/versions/' + version + '/restore', {});
  }
}
