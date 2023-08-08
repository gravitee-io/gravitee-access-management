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

import { OrganizationService } from './organization.service';

import { AppConfig } from '../../config/app.config';

@Injectable()
export class GroupService {
  private groupsURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient, private organizationService: OrganizationService) {}

  findByDomain(domainId, page, size): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + '/groups?page=' + page + '&size=' + size);
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + '/groups/' + id);
  }

  create(domainId, user): Observable<any> {
    return this.http.post<any>(this.groupsURL + domainId + '/groups', user);
  }

  update(domainId, id, group, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.updateGroup(id, group);
    }
    return this.http.put<any>(this.groupsURL + domainId + '/groups/' + id, {
      name: group.name,
      description: group.description,
      members: group.members,
    });
  }

  delete(domainId, id, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.deleteGroup(id);
    }
    return this.http.delete<any>(this.groupsURL + domainId + '/groups/' + id);
  }

  findMembers(domainId, groupId, page, size): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + '/groups/' + groupId + '/members?page=' + page + '&size=' + size);
  }

  roles(domainId, groupId): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + '/groups/' + groupId + '/roles');
  }

  revokeRole(domainId, groupId, roleId, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.revokeGroupRole(groupId, roleId);
    }
    return this.http.delete<any>(this.groupsURL + domainId + '/groups/' + groupId + '/roles/' + roleId);
  }

  assignRoles(domainId, groupId, roles, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.assignGroupRoles(groupId, roles);
    }
    return this.http.post<any>(this.groupsURL + domainId + '/groups/' + groupId + '/roles', roles);
  }
}
