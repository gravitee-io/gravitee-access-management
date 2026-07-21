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
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { AppConfig } from '../../config/app.config';

export interface ConsoleUserPreferences {
  defaultDomainId?: string | null;
  defaultEnvironmentId?: string | null;
  pinnedDomainIds?: string[];
}

@Injectable()
export class UserPreferencesService {
  private preferencesUrl: string = AppConfig.settings.baseURL + '/user/preferences';
  private preferencesSubject = new BehaviorSubject<ConsoleUserPreferences>({});
  preferences$ = this.preferencesSubject.asObservable();
  private pendingDefaultDomainRedirect = false;

  constructor(private http: HttpClient) {}

  load(): Observable<ConsoleUserPreferences> {
    return this.http.get<ConsoleUserPreferences>(this.preferencesUrl).pipe(
      tap((preferences) => {
        this.preferencesSubject.next(preferences || {});
        this.pendingDefaultDomainRedirect = !!preferences?.defaultDomainId;
      }),
    );
  }

  preferences(): ConsoleUserPreferences {
    return this.preferencesSubject.value;
  }

  pinnedDomainIds(): string[] {
    return this.preferences().pinnedDomainIds || [];
  }

  isPinned(domainId: string): boolean {
    return this.pinnedDomainIds().includes(domainId);
  }

  isDefault(domainId: string): boolean {
    return this.preferences().defaultDomainId === domainId;
  }

  defaultDomainId(): string | null {
    return this.preferences().defaultDomainId ?? null;
  }

  togglePin(domainId: string): Observable<ConsoleUserPreferences> {
    const pinned = this.pinnedDomainIds();
    const pinnedDomainIds = pinned.includes(domainId) ? pinned.filter((id) => id !== domainId) : [...pinned, domainId];
    return this.update({ ...this.preferences(), pinnedDomainIds });
  }

  toggleDefaultDomain(domainId: string, environmentId: string): Observable<ConsoleUserPreferences> {
    const unset = this.isDefault(domainId);
    return this.update({
      ...this.preferences(),
      defaultDomainId: unset ? null : domainId,
      defaultEnvironmentId: unset ? null : environmentId,
    });
  }

  /**
   * Returns the default domain id once per application bootstrap, so the
   * post-login redirect fires on initial entry only (not when navigating back home).
   */
  consumeDefaultDomainRedirect(): string | null {
    if (this.pendingDefaultDomainRedirect) {
      this.pendingDefaultDomainRedirect = false;
      return this.preferences().defaultDomainId ?? null;
    }
    return null;
  }

  private update(preferences: ConsoleUserPreferences): Observable<ConsoleUserPreferences> {
    return this.http
      .put<ConsoleUserPreferences>(this.preferencesUrl, preferences)
      .pipe(tap((updated) => this.preferencesSubject.next(updated || {})));
  }
}
