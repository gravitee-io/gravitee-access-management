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
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AppConfig } from '../../config/app.config';
import { DomainPasswordPolicy } from '../domain/settings/password-policy/domain-password-policy.model';
import { PasswordPolicyStatus } from '../domain/settings/password-policy/password-policy-status.model';
import { SKIP_404_REDIRECT } from '../interceptors/http-request.interceptor';

@Injectable()
export class PasswordPolicyService {
  private domainsURL = AppConfig.settings.domainBaseURL;
  private passwordPolicyURL = '/password-policies';

  constructor(private http: HttpClient) {}

  list(domainId: string): Observable<any> {
    return this.http.get<any>(this.domainsURL + domainId + this.passwordPolicyURL);
  }

  get(domainId: string, policyId: string): Observable<any> {
    return this.http.get<any>(this.domainsURL + domainId + this.passwordPolicyURL + '/' + policyId);
  }

  create(domainId: string, policy: any): Observable<any> {
    return this.http.post<any>(this.domainsURL + domainId + this.passwordPolicyURL, policy);
  }

  update(domainId: string, policyId: string, policy: any): Observable<any> {
    return this.http.put<any>(this.domainsURL + domainId + this.passwordPolicyURL + '/' + policyId, policy);
  }

  delete(domainId: string, policyId: any): Observable<any> {
    return this.http.delete<any>(this.domainsURL + domainId + this.passwordPolicyURL + '/' + policyId);
  }

  setDefaultPolicy(domainId: string, policyId: string): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}${this.passwordPolicyURL}/${policyId}/default`, null);
  }

  getPolicyForIdp(domainId: string, idpId): Observable<DomainPasswordPolicy> {
    const params = idpId ? { identity: idpId } : {};
    const context = new HttpContext().set(SKIP_404_REDIRECT, true);
    return this.http.get(`${this.domainsURL}${domainId}${this.passwordPolicyURL}/activePolicy`, {
      params,
      context,
    });
  }

  evaluatePassword(domainId: string, policyId: string, userId: string, password: string): Observable<PasswordPolicyStatus> {
    return this.http.post(`${this.domainsURL}${domainId}${this.passwordPolicyURL}/${policyId}/evaluate`, {
      userId,
      password,
    });
  }
}
