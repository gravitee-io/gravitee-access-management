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
import {Http, Response} from "@angular/http";
import {Observable} from "rxjs/Observable";

@Injectable()
export class GroupService {
  private groupsURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: Http) { }

  findByDomain(domainId, page, size): Observable<Response>  {
    return this.http.get(this.groupsURL + domainId + "/groups?page=" + page + "&size=" + size);
  }

  get(domainId, id): Observable<Response>  {
    return this.http.get(this.groupsURL + domainId + "/groups/" + id);
  }

  create(domainId, user): Observable<Response>  {
    return this.http.post(this.groupsURL + domainId + "/groups", user);
  }

  update(domainId, id, group): Observable<Response>  {
    return this.http.put(this.groupsURL + domainId + "/groups/" + id, {
      'name' : group.name,
      'members' : group.members
    });
  }

  delete(domainId, id): Observable<Response>  {
    return this.http.delete(this.groupsURL + domainId + "/groups/" + id);
  }

  findMembers(domainId, groupId, page, size): Observable<Response>  {
    return this.http.get(this.groupsURL + domainId + "/groups/" + groupId + "/members?page=" + page + "&size=" + size);
  }

}
