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
import {AppConfig} from "../../../../config/app.config";

@Injectable()
export class RoleService {
  private rolesURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: Http) { }

  findByDomain(domainId): Observable<Response>  {
    return this.http.get(this.rolesURL + domainId + "/roles");
  }

  get(domainId, id): Observable<Response>  {
    return this.http.get(this.rolesURL + domainId + "/roles/" + id);
  }

  create(domainId, role): Observable<Response>  {
    return this.http.post(this.rolesURL + domainId + "/roles", role);
  }

  update(domainId, id, role): Observable<Response>  {
    return this.http.put(this.rolesURL + domainId + "/roles/" + id, {
      'name' : role.name,
      'description' : role.description,
      'permissions' : role.permissions
    });
  }

  delete(domainId, id): Observable<Response>  {
    return this.http.delete(this.rolesURL + domainId + "/roles/" + id);
  }

}
