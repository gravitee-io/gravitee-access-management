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
import { EMPTY, Observable } from 'rxjs';
import { expand, map, reduce } from 'rxjs/operators';

import { AppConfig } from '../../config/app.config';
import { fromTrustedDomain, toTrustedDomainRequest, TrustDomain } from '../domain/settings/trust-domains/trust-domain.types';

import { Sort } from './api.model';

export interface TrustDomainPage {
  data: TrustDomain[];
  page: number;
  totalCount: number;
  nextCursor?: string;
}

export type TrustedDomainUsage = 'SPIFFE' | 'TOKEN_EXCHANGE' | 'CROSS_APP_ACCESS';

export interface TrustDomainListOptions {
  usage?: TrustedDomainUsage;
  sort?: Sort;
}

const MAX_PAGE_SIZE = 100;

@Injectable()
export class TrustDomainService {
  private domainBaseURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) {}

  list(domainId: string, page: number, size: number, options: TrustDomainListOptions = {}): Observable<TrustDomainPage> {
    const usageParam = options.usage ? '&usage=' + options.usage : '';
    const sortParam = options.sort ? `&sort=${options.sort.prop}&dir=${options.sort.dir}` : '';
    return this.toPage(
      this.http.get<any>(this.domainBaseURL + domainId + '/trusted-domains?page=' + page + '&limit=' + size + sortParam + usageParam),
    );
  }

  next(nextCursor: string): Observable<TrustDomainPage> {
    return this.toPage(this.http.get<any>(AppConfig.settings.baseURL + nextCursor));
  }

  listAll(domainId: string, usage?: TrustedDomainUsage): Observable<TrustDomain[]> {
    return this.list(domainId, 0, MAX_PAGE_SIZE, { usage }).pipe(
      expand((result) => (result.nextCursor ? this.next(result.nextCursor) : EMPTY)),
      reduce((trustDomains, result) => trustDomains.concat(result.data), [] as TrustDomain[]),
    );
  }

  private toPage(request: Observable<any>): Observable<TrustDomainPage> {
    return request.pipe(map((result) => ({ ...result, data: (result?.data ?? []).map(fromTrustedDomain) })));
  }

  get(domainId: string, id: string): Observable<any> {
    return this.http.get<any>(this.domainBaseURL + domainId + '/trusted-domains/' + id).pipe(map(fromTrustedDomain));
  }

  create(domainId: string, trustDomain: any): Observable<any> {
    return this.http
      .post<any>(this.domainBaseURL + domainId + '/trusted-domains', toTrustedDomainRequest(trustDomain))
      .pipe(map(fromTrustedDomain));
  }

  update(domainId: string, id: string, trustDomain: any): Observable<any> {
    return this.http
      .put<any>(this.domainBaseURL + domainId + '/trusted-domains/' + id, toTrustedDomainRequest(trustDomain))
      .pipe(map(fromTrustedDomain));
  }

  delete(domainId: string, id: string): Observable<any> {
    return this.http.delete<any>(this.domainBaseURL + domainId + '/trusted-domains/' + id);
  }
}
