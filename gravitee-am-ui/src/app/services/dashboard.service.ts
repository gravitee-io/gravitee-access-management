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
import { AppConfig } from '../../config/app.config';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class DashboardService {
  private dashboardURL = AppConfig.settings.baseURL + '/dashboard/';

  constructor(private http: HttpClient) {}

  findApplications(domainId: any): Observable<any> {
    return this.http.get<any>(this.dashboardURL + 'applications' + (domainId ? '?domainId=' + domainId : ''));
  }

  findTopApplications(domainId: any): Observable<any> {
    return this.http.get<any>(this.dashboardURL + 'applications/top' + (domainId ? '?domainId=' + domainId : ''));
  }

  findTotalApplications(domainId: any): Observable<any> {
    return this.http.get<any>(this.dashboardURL + 'applications/total' + (domainId ? '?domainId=' + domainId : ''));
  }

  findTotalTokens(domainId: any): Observable<any> {
    return this.http.get<any>(this.dashboardURL + 'tokens' + (domainId ? '?domainId=' + domainId : ''));
  }
}
