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
import { ActivatedRouteSnapshot } from '@angular/router';
import { combineLatest, Observable, of } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';

import { AuthService } from '../services/auth.service';
import { DomainService } from '../services/domain.service';
import { ApplicationService } from '../services/application.service';
import { EnvironmentService } from '../services/environment.service';
import { ProtectedResourceService } from '../services/protected-resource.service';
import { DomainStoreService } from '../stores/domain.store';

@Injectable()
export class AuthGuard {
  constructor(
    private authService: AuthService,
    private environmentService: EnvironmentService,
    private domainService: DomainService,
    private applicationService: ApplicationService,
    private protectedResourceService: ProtectedResourceService,
    private domainStore: DomainStoreService,
  ) {}

  canActivate(route: ActivatedRouteSnapshot): Observable<boolean> | Promise<boolean> | boolean {
    // if no permission required, continue
    if (!route.data || !route.data.perms || !route.data.perms.only) {
      return true;
    }
    // check if we need to load some data
    const combineSources: any[] = [];

    const requiredPerms = route.data.perms.only;
    combineSources.push(!this.authService.isAuthenticated() ? this.authService.userInfo() : of(this.authService.user()));

    const environmentId = route.paramMap.get('envHrid');
    const domainId = route.paramMap.get('domainId');
    const appId = route.paramMap.get('appId');
    const mcpServerId = route.paramMap.get('mcpServerId');
    const perm = requiredPerms[0];

    const needsEnv = !!environmentId && !this.authService.environmentPermissionsLoaded() && perm !== 'domain_create';
    const needsDomain = !!domainId && !this.authService.domainPermissionsLoaded() && perm !== 'domain_create';
    const needsApp = !!appId && !this.authService.applicationPermissionsLoaded() && perm.startsWith('application');
    const needsProtectedResource =
      !!mcpServerId && !this.authService.protectedResourcePermissionsLoaded() && perm.startsWith('protected_resource');

    if (needsEnv) {
      combineSources.push(this.environmentService.permissions(environmentId));
    }

    if (needsDomain) {
      combineSources.push(this.domainService.getById(domainId).pipe(mergeMap((domain) => this.domainService.permissions(domain.id))));
    }
    if (needsApp) {
      combineSources.push(
        this.domainService.getById(domainId).pipe(mergeMap((domain) => this.applicationService.permissions(domain.id, appId))),
      );
    }
    if (needsProtectedResource) {
      combineSources.push(
        this.domainService.getById(domainId).pipe(mergeMap((domain) => this.protectedResourceService.permissions(domain.id, mcpServerId))),
      );
    }
    // check permissions
    return combineLatest(combineSources).pipe(
      map(() => this.isAuthorized(requiredPerms)),
      catchError(() => {
        return of(false);
      }),
    );
  }

  canDisplay(route: ActivatedRouteSnapshot, path): boolean {
    // if no data configuration, continue
    if (!path.data) {
      return true;
    }
    // display SAML menu on the app settings only if the domain has enabled the SAML Protocol
    if (path.data?.protocol === 'SAML') {
      const domain = this.domainStore.current || route.data['domain'];
      if (!domain?.saml?.enabled) {
        return false;
      }
    }
    // if resource (application) should not display a settings page, continue
    if (path.data?.types?.only?.length > 0) {
      const app = route.data['application'];
      if (app && path.data.types.only.indexOf(app.type?.toUpperCase()) === -1) {
        return false;
      }
    }
    // hide menu items that require an agent application
    if (path.data?.requiresAgentIdentity === true) {
      const app = route.data['application'];
      if (app?.type?.toUpperCase() !== 'AGENT') {
        return false;
      }
    }
    // if no permission required, continue
    if (!path.data.perms || !path.data.perms.only) {
      return true;
    }
    // check if the authenticated user can display UI items
    const requiredPerms = path.data.perms.only;
    return this.isAuthorized(requiredPerms);
  }

  private isAuthorized(requiredPerms): boolean {
    return this.authService.hasAnyPermissions(requiredPerms);
  }
}
