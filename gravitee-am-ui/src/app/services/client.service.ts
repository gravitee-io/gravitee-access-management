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
import { Http, Response } from "@angular/http";
import { Observable } from "rxjs";
import { AppConfig } from "../../config/app.config";

@Injectable()
export class ClientService {
  private clientsURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: Http) { }

  findByDomain(domainId): Observable<Response> {
    return this.http.get(this.clientsURL + domainId + "/clients");
  }

  search(domainId, searchTerm) {
    return this.http.get(this.clientsURL + domainId + "/clients?q=" + searchTerm);
  }

  get(domainId, id): Observable<Response> {
    return this.http.get(this.clientsURL + domainId + "/clients/" + id);
  }

  create(domainId, client): Observable<Response> {
    return this.http.post(this.clientsURL + domainId + "/clients", client);
  }

  update(domainId, id, client): Observable<Response> {
    return this.http.put(this.clientsURL + domainId + "/clients/" + id, {
      'clientName': client.clientName,
      'redirectUris': client.redirectUris,
      'authorizedGrantTypes': client.authorizedGrantTypes,
      'scopes': client.scopes,
      'autoApproveScopes': client.autoApproveScopes,
      'accessTokenValiditySeconds': client.accessTokenValiditySeconds,
      'refreshTokenValiditySeconds': client.refreshTokenValiditySeconds,
      'idTokenValiditySeconds': client.idTokenValiditySeconds,
      'idTokenCustomClaims': client.idTokenCustomClaims,
      'enabled': client.enabled,
      'identities': client.identities,
      'oauth2Identities': client.oauth2Identities,
      'certificate': client.certificate,
      'enhanceScopesWithUserPermissions' : client.enhanceScopesWithUserPermissions,
      'responseTypes' : client.responseTypes
    });
  }

  delete(domainId, id): Observable<Response> {
    return this.http.delete(this.clientsURL + domainId + "/clients/" + id);
  }
}
