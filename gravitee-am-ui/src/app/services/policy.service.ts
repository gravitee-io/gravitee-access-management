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
import { Observable } from "rxjs";
import { AppConfig } from "../../config/app.config";

@Injectable()
export class PolicyService {
  private providersURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: HttpClient) { }

  findByDomain(domainId): Observable<any>  {
    return this.http.get<any>(this.providersURL + domainId + "/policies");
  }

  get(domainId, id): Observable<any>  {
    return this.http.get<any>(this.providersURL + domainId + "/policies/" + id);
  }

  create(domainId, policy): Observable<any>  {
    return this.http.post<any>(this.providersURL + domainId + "/policies", policy);
  }

  update(domainId, id, policy): Observable<any>  {
    return this.http.put<any>(this.providersURL + domainId + "/policies/" + id, {
      'name' : policy.name,
      'configuration' : policy.configuration,
      'order' : policy.order,
      'enabled' : policy.enabled
    });
  }

  updateAll(domainId, policies): Observable<any> {
    return this.http.put<any>(this.providersURL + domainId + "/policies", policies);
  }

  delete(domainId, id): Observable<any>  {
    return this.http.delete<any>(this.providersURL + domainId + "/policies/" + id);
  }

}
