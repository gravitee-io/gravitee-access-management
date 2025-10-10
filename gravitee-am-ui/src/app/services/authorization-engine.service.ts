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
export class AuthorizationEngineService {
  private authorizationEnginesURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  findByDomain(domainId: any): Observable<any> {
    return this.http.get<any>(this.authorizationEnginesURL + domainId + '/authorization-engines');
  }

  get(domainId: any, id: any): Observable<any> {
    return this.http.get<any>(this.authorizationEnginesURL + domainId + '/authorization-engines/' + id);
  }

  create(domainId: any, authorizationEngine: any): Observable<any> {
    const payload = {
      ...authorizationEngine,
      configuration: this.stringifyConfiguration(authorizationEngine.configuration),
    };
    return this.http.post<any>(this.authorizationEnginesURL + domainId + '/authorization-engines', payload);
  }

  update(domainId: any, id: any, authorizationEngine: any): Observable<any> {
    return this.http.put<any>(this.authorizationEnginesURL + domainId + '/authorization-engines/' + id, {
      name: authorizationEngine.name,
      configuration: this.stringifyConfiguration(authorizationEngine.configuration),
    });
  }

  delete(domainId: any, id: any): Observable<any> {
    return this.http.delete<any>(this.authorizationEnginesURL + domainId + '/authorization-engines/' + id);
  }

  private stringifyConfiguration(configuration: any): string {
    if (configuration == null) {
      return '{}';
    }

    if (typeof configuration === 'string') {
      return configuration;
    }

    return JSON.stringify(configuration);
  }
}
