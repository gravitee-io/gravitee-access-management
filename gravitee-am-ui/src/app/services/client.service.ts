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
import { HttpClient } from "@angular/common/http";
import { AppConfig } from "../../config/app.config";
import { Observable } from "rxjs";

@Injectable()
export class ClientService {
  private clientsURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: HttpClient) { }

  findByDomain(domainId): Observable<any> {
    return this.http.get<any>(this.clientsURL + domainId + "/clients");
  }

  search(domainId, searchTerm): Observable<any> {
    return this.http.get<any>(this.clientsURL + domainId + "/clients?q=" + searchTerm);
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.clientsURL + domainId + "/clients/" + id);
  }

  create(domainId, client): Observable<any> {
    return this.http.post<any>(this.clientsURL + domainId + "/clients", client);
  }

  update(domainId, id, client): Observable<any> {
    return this.http.put<any>(this.clientsURL + domainId + "/clients/" + id, {
      'clientName': client.clientName,
      'redirectUris': client.redirectUris,
      'authorizedGrantTypes': client.authorizedGrantTypes,
      'scopes': client.scopes,
      'autoApproveScopes': client.autoApproveScopes,
      'accessTokenValiditySeconds': client.accessTokenValiditySeconds,
      'refreshTokenValiditySeconds': client.refreshTokenValiditySeconds,
      'idTokenValiditySeconds': client.idTokenValiditySeconds,
      'tokenCustomClaims': client.tokenCustomClaims,
      'enabled': client.enabled,
      'identities': client.identities,
      'certificate': client.certificate,
      'enhanceScopesWithUserPermissions' : client.enhanceScopesWithUserPermissions,
      'responseTypes' : client.responseTypes,
      'scopeApprovals' : client.scopeApprovals,
      'template' : client.template
    });
  }

  patchAccountSettings(domainId, id, accountSettings): Observable<any> {
    return this.http.patch<any>(this.clientsURL + domainId + "/clients/" + id, {
      "accountSettings" : accountSettings
    });
  }

  patchTemplate(domainId, id, template): Observable<any> {
    return this.http.patch<any>(this.clientsURL + domainId + "/clients/" + id, {
      "template" : template
    });
  }

  delete(domainId, id): Observable<any> {
    return this.http.delete<any>(this.clientsURL + domainId + "/clients/" + id);
  }

  renewClientSecret(domainId, id): Observable<any> {
    return this.http.post<any>(this.clientsURL + domainId + "/clients/" + id + "/secret/_renew", {});
  }
}
