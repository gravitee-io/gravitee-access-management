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

export interface AgentJwk {
  kid: string;
  kty?: string;
  alg?: string;
  use?: string;
  createdAt?: number | string;
  [key: string]: unknown;
}

@Injectable({
  providedIn: 'root',
})
export class ApplicationAgentKeysService {
  private readonly appsURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  list(domainId: string, applicationId: string): Observable<AgentJwk[]> {
    return this.http.get<AgentJwk[]>(this.baseUrl(domainId, applicationId));
  }

  add(domainId: string, applicationId: string, jwk: AgentJwk): Observable<AgentJwk> {
    return this.http.post<AgentJwk>(this.baseUrl(domainId, applicationId), jwk);
  }

  remove(domainId: string, applicationId: string, kid: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(domainId, applicationId)}/${encodeURIComponent(kid)}`);
  }

  private baseUrl(domainId: string, applicationId: string): string {
    return `${this.appsURL}${domainId}/applications/${applicationId}/agent/keys`;
  }
}
