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
import {AuthService} from "./auth.service";
import {map} from "rxjs/operators";

@Injectable()
export class ApplicationService {
  private appsURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient, private authService: AuthService) { }

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

  updateType(domainId, id, type): Observable<any> {
    return this.http.put<any>(this.appsURL + domainId + "/applications/" + id + "/type", {
      'type': type
    });
  }

  members(domainId, id): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + "/applications/" + id + "/members")
      .pipe(map(response => {
        const memberships = response.memberships;
        const metadata = response.metadata;
        const members = memberships.map(m => {
          m.roleName = (metadata['roles'][m.roleId]) ? metadata['roles'][m.roleId].name : 'Unknown role';
          if (m.memberType === 'user') {
            m.name = (metadata['users'][m.memberId]) ? metadata['users'][m.memberId].displayName : 'Unknown user';
          } else if (m.memberType === 'group') {
            m.name = (metadata['groups'][m.memberId]) ? metadata['groups'][m.memberId].displayName : 'Unknown group';
          }
          return m;
        });
        return members;
      }));
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

  permissions(domainId, id): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + '/applications/' + id + '/members/permissions')
      .pipe(map(perms => {
        this.authService.reloadApplicationPermissions(perms);
        return perms;
      }));
  }

  resources(domainId, id, page, size): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + '/applications/' + id + '/resources?page=' + page + '&size=' + size)
      .pipe(map(pagedResponse => {
        if (pagedResponse.data) {
          const resources = pagedResponse.data[0].resources;
          const metadata = pagedResponse.data[0].metadata;
          const resourceSet = resources.map(r => {
            r.userName = (metadata['users'][r.userId]) ? metadata['users'][r.userId].displayName : 'Unknown user';
            r.appName = (metadata['applications'][r.clientId]) ? metadata['applications'][r.clientId].name : 'Unknown app';
            return r;
          });
          return {data: resourceSet, totalCount: pagedResponse.totalCount};
        } else {
          return {data: [], totalCount: 0};
        }
      }));
  }

  resource(domainId, id, resourceId): Observable<any> {
    return this.http.get<any>(this.appsURL + domainId + '/applications/' + id + '/resources/' + resourceId);
  }
}
