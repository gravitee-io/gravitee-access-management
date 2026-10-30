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
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AppConfig } from '../../config/app.config';
import { CursorResponse } from '../utils/cursor';
import { SKIP_ERROR_SNACKBAR } from '../interceptors/http-request.interceptor';

import { Sort } from './api.model';

@Injectable()
export class AuthorizationDetailTypeService {
  private domainBaseURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  list(domainId: string, size: number, page: number, sort: Sort, query?: string): Observable<CursorResponse> {
    const queryParam = query ? '&q=' + encodeURIComponent(query) : '';
    return this.http.get<CursorResponse>(
      this.domainBaseURL +
        domainId +
        `/authorization-detail-types?page=${page}&limit=${size}&sort=${sort.prop}&dir=${sort.dir.toUpperCase()}` +
        queryParam,
    );
  }

  next(nextCursor: string): Observable<CursorResponse> {
    return this.http.get<CursorResponse>(AppConfig.settings.baseURL + nextCursor);
  }

  get(domainId: string, id: string): Observable<any> {
    return this.http.get<any>(this.domainBaseURL + domainId + '/authorization-detail-types/' + id);
  }

  create(domainId: string, authorizationDetailType: any): Observable<any> {
    return this.http.post<any>(this.domainBaseURL + domainId + '/authorization-detail-types', authorizationDetailType, {
      context: new HttpContext().set(SKIP_ERROR_SNACKBAR, true),
    });
  }

  patch(domainId: string, id: string, patch: any): Observable<any> {
    return this.http.patch<any>(this.domainBaseURL + domainId + '/authorization-detail-types/' + id, patch, {
      context: new HttpContext().set(SKIP_ERROR_SNACKBAR, true),
    });
  }

  delete(domainId: string, id: string): Observable<any> {
    return this.http.delete<any>(this.domainBaseURL + domainId + '/authorization-detail-types/' + id);
  }
}
