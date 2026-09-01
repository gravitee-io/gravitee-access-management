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
import { normalizeTrustDomain } from '../domain/settings/trust-domains/trust-domain.types';

@Injectable()
export class TrustDomainService {
  private domainBaseURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  list(domainId: string): Observable<any> {
    return this.http
      .get<any>(this.domainBaseURL + domainId + '/trust-domains')
      .pipe(map((trustDomains) => (trustDomains ?? []).map(normalizeTrustDomain)));
  }

  get(domainId: string, id: string): Observable<any> {
    return this.http.get<any>(this.domainBaseURL + domainId + '/trust-domains/' + id).pipe(map(normalizeTrustDomain));
  }

  create(domainId: string, trustDomain: any): Observable<any> {
    return this.http.post<any>(this.domainBaseURL + domainId + '/trust-domains', trustDomain).pipe(map(normalizeTrustDomain));
  }

  update(domainId: string, id: string, trustDomain: any): Observable<any> {
    return this.http
      .put<any>(this.domainBaseURL + domainId + '/trust-domains/' + id, {
        name: trustDomain.name,
        description: trustDomain.description,
        spiffeTrustDomain: trustDomain.spiffeTrustDomain,
        issuer: trustDomain.issuer,
        keyMaterial: trustDomain.keyMaterial,
        refreshIntervalSeconds: trustDomain.refreshIntervalSeconds,
        allowedAlgorithms: trustDomain.allowedAlgorithms,
        scopeMappings: trustDomain.scopeMappings,
        userBindingEnabled: trustDomain.userBindingEnabled,
        userBindingCriteria: trustDomain.userBindingCriteria,
      })
      .pipe(map(normalizeTrustDomain));
  }

  delete(domainId: string, id: string): Observable<any> {
    return this.http.delete<any>(this.domainBaseURL + domainId + '/trust-domains/' + id);
  }
}
