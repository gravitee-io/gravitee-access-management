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
import { Observable } from "rxjs";
import { Http, Response } from "@angular/http";
import { AppConfig } from "../../config/app.config";
import { Subject } from "rxjs/Subject";

@Injectable()
export class DomainService {
  private domainsURL: string = AppConfig.settings.baseURL + '/domains/';
  private domainUpdatedSource = new Subject<any>();
  domainUpdated$ = this.domainUpdatedSource.asObservable();

  constructor(private http: Http) {}

  list(): Observable<Response> {
    return this.http.get(this.domainsURL);
  }

  get(id: string): Observable<Response>  {
    return this.http.get(this.domainsURL + id);
  }

  create(domain): Observable<Response>  {
    return this.http.post(this.domainsURL, domain);
  }

  update(id, domain): Observable<Response> {
    return this.http.put(this.domainsURL + id, {
      'name': domain.name,
      'description': domain.description,
      'path': domain.path,
      'enabled': domain.enabled,
      'identities' : domain.identities,
      'oauth2Identities': domain.oauth2Identities
    });
  }

  delete(id): Observable<Response> {
    return this.http.delete(this.domainsURL + id);
  }

  getLoginForm(id): Observable<Response> {
    return this.http.get(this.domainsURL + id + '/login');
  }

  createLoginForm(id, loginForm): Observable<Response> {
    return this.http.put(this.domainsURL + id + '/login', loginForm);
  }

  deleteLoginForm(domainId): Observable<Response> {
    return this.http.delete(this.domainsURL + domainId + '/login');
  }

  notify(domain): void {
    this.domainUpdatedSource.next(domain);
  }
}
