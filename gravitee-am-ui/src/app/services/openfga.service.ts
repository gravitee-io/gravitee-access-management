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
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, timer } from 'rxjs';
import { retry, catchError, delayWhen, mergeMap } from 'rxjs/operators';
import { SKIP_404_REDIRECT } from 'app/interceptors/http-request.interceptor';

import type { AuthorizationModel } from '@openfga/sdk';

import { AppConfig } from '../../config/app.config';

@Injectable()
export class OpenFGAService {
  private domainsURL: string = AppConfig.settings.domainBaseURL;
  private readonly RETRY_DELAY_MS = 1000;
  private readonly MAX_RETRIES = 3;

  constructor(private http: HttpClient) {}

  listAuthorizationModels(domainId: string, engineId: string, pageSize: number, continuationToken?: string): Observable<any> {
    let params = `pageSize=${pageSize}`;
    if (continuationToken) {
      params += `&continuationToken=${continuationToken}`;
    }
    const request = this.http.get<any>(
      `${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/authorization-models?${params}`,
    );
    return continuationToken ? request : this.retryOnServerError(request);
  }

  addAuthorizationModel(
    domainId: string,
    engineId: string,
    authorizationModel: Omit<AuthorizationModel, 'id'>,
  ): Observable<{ authorizationModelId: string }> {
    return this.http.post<any>(
      `${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/authorization-models`,
      authorizationModel,
    );
  }

  getStore(domainId: string, engineId: string): Observable<any> {
    const context = new HttpContext().set(SKIP_404_REDIRECT, true);
    const request = this.http.get<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/store`, {
      context,
    });
    return this.retryOnServerError(request);
  }

  listTuples(domainId: string, engineId: string, pageSize: number, continuationToken?: string): Observable<any> {
    let params = `pageSize=${pageSize}`;
    if (continuationToken) {
      params += `&continuationToken=${continuationToken}`;
    }
    const request = this.http.get<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/tuples?${params}`);
    return continuationToken ? request : this.retryOnServerError(request);
  }

  addTuple(domainId: string, engineId: string, tuple: any): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/tuples`, tuple);
  }

  deleteTuple(domainId: string, engineId: string, tuple: any): Observable<any> {
    return this.http.request<any>('delete', `${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/tuples`, {
      body: tuple,
    });
  }

  checkPermission(domainId: string, engineId: string, permissionRequest: any): Observable<any> {
    return this.http.post<any>(`${this.domainsURL}${domainId}/authorization-engines/${engineId}/settings/check`, permissionRequest);
  }

  private retryOnServerError<T>(source: Observable<T>): Observable<T> {
    let retryCount = 0;
    return source.pipe(
      retry({
        count: this.MAX_RETRIES,
        delay: (error: any) => {
          const isServerError = error instanceof HttpErrorResponse && error.status >= 500 && error.status < 600;
          if (isServerError) {
            retryCount++;
            return timer(this.RETRY_DELAY_MS);
          }
          return throwError(() => error);
        },
      }),
      catchError((error: any) => {
        return throwError(() => error);
      }),
    );
  }
}
