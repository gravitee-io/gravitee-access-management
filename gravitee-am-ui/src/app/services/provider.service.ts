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
import * as bcrypt from 'bcryptjs';

import { AppConfig } from '../../config/app.config';

import { OrganizationService } from './organization.service';

@Injectable()
export class ProviderService {
  private providersURL = AppConfig.settings.domainBaseURL;

  constructor(
    private http: HttpClient,
    private organizationService: OrganizationService,
  ) {}

  findByDomain(domainId): Observable<any> {
    return this.http.get<any>(this.providersURL + domainId + '/identities');
  }

  findUserProvidersByDomain(domainId): Observable<any> {
    return this.http.get<any>(this.providersURL + domainId + '/identities?userProvider=true');
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.providersURL + domainId + '/identities/' + id);
  }

  create(domainId, provider, organizationContext): Observable<any> {
    this.prepareProvider(provider);
    if (organizationContext) {
      return this.organizationService.createIdentityProvider(provider);
    }
    return this.http.post<any>(this.providersURL + domainId + '/identities', provider);
  }

  update(domainId, id, provider, organizationContext): Observable<any> {
    this.prepareProvider(provider);
    if (organizationContext) {
      return this.organizationService.updateIdentityProvider(id, provider);
    }
    return this.http.put<any>(this.providersURL + domainId + '/identities/' + id, {
      name: provider.name,
      configuration: provider.configuration,
      domainWhitelist: provider.domainWhitelist,
      mappers: provider.mappers,
      roleMapper: provider.roleMapper,
      groupMapper: provider.groupMapper,
    });
  }

  assignPasswordPolicy(domainId, id, passwordPolicyId): Observable<any> {
    return this.http.put<any>(this.providersURL + domainId + '/identities/' + id + '/password-policy', {
      passwordPolicy: passwordPolicyId,
    });
  }

  delete(domainId, id, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.deleteIdentityProvider(id);
    }
    return this.http.delete<any>(this.providersURL + domainId + '/identities/' + id);
  }

  private prepareProvider(provider) {
    this.enhanceConfiguration(provider);
    provider.configuration = JSON.stringify(provider.configuration);
  }

  private enhanceConfiguration(provider) {
    if (provider.type && provider.type === 'inline-am-idp') {
      if (provider.configuration.passwordEncoder && provider.configuration.passwordEncoder === 'BCrypt') {
        // hash password if not already hashed
        if (provider.configuration.users) {
          const hashedPassword = '********';
          provider.configuration.users.forEach((user) => {
            if (!user.password.startsWith('$2a$') && hashedPassword !== user.password) {
              user.password = bcrypt.hashSync(user.password, bcrypt.genSaltSync(10));
            }
          });
        }
      }
    }
  }
}
