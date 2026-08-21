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
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';

import { AppConfig } from '../../config/app.config';

/**
 * The storage rules the platform applies to a mongo identity provider that reuses the system cluster.
 * They default to the installation type and an operator overrides either one in `gravitee.yml`.
 */
export interface IdentityProviderStorageRules {
  pinDatabase: boolean;
  prefixUsersCollection: boolean;
}

@Injectable()
export class CloudModeService {
  private platformURL = AppConfig.settings.baseURL + '/platform';
  private installation$: Observable<any>;

  constructor(private http: HttpClient) {}

  isCloudModeEnabled(): Observable<boolean> {
    return this.installation().pipe(map((response) => response.type === 'managed'));
  }

  identityProviderStorageRules(): Observable<IdentityProviderStorageRules> {
    return this.installation().pipe(
      map((response) => ({
        pinDatabase: response.pinIdentityProviderDatabase === true,
        prefixUsersCollection: response.prefixIdentityProviderUsersCollection === true,
      })),
    );
  }

  private installation(): Observable<any> {
    if (!this.installation$) {
      this.installation$ = this.http.get<any>(this.platformURL + '/configuration/installation').pipe(
        catchError(() => of({})),
        shareReplay({ bufferSize: 1, refCount: true }),
      );
    }
    return this.installation$;
  }
}
