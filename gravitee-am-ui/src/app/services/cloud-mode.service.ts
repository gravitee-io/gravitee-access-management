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

interface InstallationConfiguration {
  type?: string;
  systemClusterRestricted?: boolean;
}

@Injectable()
export class CloudModeService {
  private platformURL = AppConfig.settings.baseURL + '/platform';
  private installation$: Observable<InstallationConfiguration>;

  constructor(private http: HttpClient) {}

  isCloudModeEnabled(): Observable<boolean> {
    return this.getInstallation().pipe(map((installation) => installation.type === 'managed'));
  }

  /** True when the platform owns the database and users collection of a system cluster mongo provider. */
  isSystemClusterRestricted(): Observable<boolean> {
    return this.getInstallation().pipe(map((installation) => installation.systemClusterRestricted === true));
  }

  private getInstallation(): Observable<InstallationConfiguration> {
    if (!this.installation$) {
      this.installation$ = this.http.get<InstallationConfiguration>(this.platformURL + '/configuration/installation').pipe(
        catchError(() => of({} as InstallationConfiguration)),
        shareReplay({ bufferSize: 1, refCount: true }),
      );
    }
    return this.installation$;
  }
}
