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
import {Injectable} from '@angular/core';
import {AppConfig} from "../../config/app.config";
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";

@Injectable()
export class GroupService {
  private groupsURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: HttpClient) { }

  findByDomain(domainId, page, size): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + "/groups?page=" + page + "&size=" + size);
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + "/groups/" + id);
  }

  create(domainId, user): Observable<any> {
    return this.http.post<any>(this.groupsURL + domainId + "/groups", user);
  }

  update(domainId, id, group): Observable<any> {
    return this.http.put<any>(this.groupsURL + domainId + "/groups/" + id, {
      'name' : group.name,
      'members' : group.members
    });
  }

  delete(domainId, id): Observable<any> {
    return this.http.delete<any>(this.groupsURL + domainId + "/groups/" + id);
  }

  findMembers(domainId, groupId, page, size): Observable<any> {
    return this.http.get<any>(this.groupsURL + domainId + "/groups/" + groupId + "/members?page=" + page + "&size=" + size);
  }

}
