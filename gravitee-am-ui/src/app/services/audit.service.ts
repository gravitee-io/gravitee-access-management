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
import moment from 'moment';
import { Observable } from 'rxjs';

import { AppConfig } from '../../config/app.config';
import { toHttpParams } from '../utils/http-utils';

import { OrganizationService } from './organization.service';
import { SearchParams } from './search';

interface AuditsResponse {
  currentPage: number;
  data: any[];
  totalCount: number;
}

@Injectable()
export class AuditService {
  private auditsURL = AppConfig.settings.domainBaseURL;

  constructor(
    private http: HttpClient,
    private organizationService: OrganizationService,
  ) {}

  get(domainId: string, auditId: string): Observable<any> {
    return this.http.get(`${this.auditsURL + domainId}/audits/${auditId}`);
  }

  findByDomain(domainId: string, page: number, size: number): Observable<AuditsResponse> {
    const from = moment().subtract(1, 'days').valueOf();
    const to = moment().valueOf();
    return this.http.get<AuditsResponse>(`${this.auditsURL + domainId}/audits`, {
      params: toHttpParams({
        page,
        size,
        from,
        to,
      }),
    });
  }

  search(searchParams: SearchParams, organizationContext: boolean): Observable<AuditsResponse> {
    if (organizationContext) {
      return this.organizationService.audits(searchParams);
    }
    return this.http.get<AuditsResponse>(`${this.auditsURL + searchParams.domainId}/audits`, {
      params: toHttpParams({
        page: searchParams.page,
        size: searchParams.size,
        type: searchParams.type,
        status: searchParams.status,
        user: searchParams.userId,
        from: searchParams.from,
        to: searchParams.to,
      }),
    });
  }
}
