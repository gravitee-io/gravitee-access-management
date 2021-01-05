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
import {HttpClient} from '@angular/common/http';
import {AppConfig} from '../../config/app.config';
import {toHttpParams} from "../utils/http-utils";
import {Observable} from "rxjs";

interface AnalyticsQuery {
  readonly type: string;
  readonly field: string;
  readonly from?: number;
  readonly to?: number;
  readonly interval?: number;
  readonly size?: number;
}

@Injectable()
export class AnalyticsService {
  private analyticsURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient) { }

  search(domainId: string, analyticsQuery: AnalyticsQuery): Observable<any> {
    const url = `${this.analyticsURL + domainId}/analytics`;
    return this._search(url, analyticsQuery);
  }

  searchApplicationAnalytics(domainId: string, applicationId: string, analyticsQuery: AnalyticsQuery): Observable<any> {
    const url = `${this.analyticsURL + domainId}/applications/${applicationId}/analytics`;
    return this._search(url, analyticsQuery);
  }

  private _search(url, analyticsQuery: AnalyticsQuery ) {
    const params = toHttpParams({...analyticsQuery});

    return this.http.get(url, {params});
  }
}
