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
import {merge, Observable, of} from "rxjs";
import {mergeMap, toArray} from "rxjs/operators";

@Injectable()
export class ScopeService {
  private scopes = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) { }

  findAllByDomain(domainId): Observable<any> {
    const PAGE_SIZE = 50; // probably enough to get all scopes at once, but just in case iterate on pages
    return this.findByDomain(domainId, 0, PAGE_SIZE).pipe(mergeMap((p) => {
      let iterations = Math.floor(p.totalCount / PAGE_SIZE) + (p.totalCount % PAGE_SIZE == 0 ? 0 : 1);
      let pageRequests = [];
      pageRequests[0] = of(... p.data);
      for (let i = 1; i < iterations; ++i) {
        pageRequests[i] = this.findByDomain(domainId, i, PAGE_SIZE)
          .pipe(mergeMap((p) => {
            return of(... p.data);
          }));
      }

      return merge (...pageRequests);
    })).pipe(toArray())
  }

  findByDomain(domainId, page, size): Observable<any> {
    return this.http.get<any>(this.scopes + domainId + "/scopes" + '?page=' + page + '&size=' + size);
  }

  search(searchTerm, domainId,page, size): Observable<any> {
    return this.http.get<any>(this.scopes + domainId + "/scopes" + '?q=' + searchTerm + '&page=' + page + '&size=' + size);
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.scopes + domainId + "/scopes/" + id);
  }

  create(domainId, scope): Observable<any> {
    return this.http.post<any>(this.scopes + domainId + "/scopes", scope);
  }

  update(domainId, id, scope): Observable<any> {
    return this.http.put<any>(this.scopes + domainId + "/scopes/" + id, {
      'name' : scope.name,
      'description' : scope.description,
      'expiresIn' : scope.expiresIn,
      'discovery' : scope.discovery,
      'parameterized' : scope.parameterized
    });
  }

  patchDiscovery(domainId, id, discovery): Observable<any> {
    return this.http.patch<any>(this.scopes + domainId + "/scopes/" + id, {
      'discovery' : discovery
    });
  }

  patchParameterized(domainId, id, parameterized): Observable<any> {
    return this.http.patch<any>(this.scopes + domainId + "/scopes/" + id, {
      'parameterized' : parameterized
    });
  }

  delete(domainId, id): Observable<any> {
    return this.http.delete<any>(this.scopes + domainId + "/scopes/" + id);
  }

}
