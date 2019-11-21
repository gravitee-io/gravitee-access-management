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
export class ApplicationService {
  private appsURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: HttpClient) { }

  findByDomain(domainId, page, size): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + "/applications?page=" + page + "&size=" + size);
  }

  search(domainId, searchTerm): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + "/applications?q=" + searchTerm);
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + "/applications/" + id);
  }

  create(domainId, application): Observable<any> {
    return this.http.post<any>(this.appsURL + domainId + "/applications", application);
  }

  patch(domainId, appId, patchApplication): Observable<any> {
    return this.http.patch<any>(this.appsURL + domainId + "/applications/" + appId, patchApplication);
  }

  delete(domainId, id): Observable<any> {
    return this.http.delete<any>(this.appsURL + domainId + "/applications/" + id);
  }

  renewClientSecret(domainId, id): Observable<any> {
    return this.http.post<any>(this.appsURL + domainId + "/applications/" + id + "/secret/_renew", {});
  }

  members(domainId, id): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + "/applications/" + id + "/members");
  }

  addMember(domainId, id, memberId, memberType, role) {
    return this.http.post<any>(this.appsURL + domainId + "/applications/" + id + "/members", {
      'memberId': memberId,
      'memberType': memberType,
      'role': role
    });
  }

  removeMember(domainId, id, membershipId) {
    return this.http.delete<any>(this.appsURL + domainId + "/applications/" + id + "/members/" + membershipId);
  }
}
