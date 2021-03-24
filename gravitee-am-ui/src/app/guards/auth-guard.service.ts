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
import {ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot} from '@angular/router';
import {combineLatest, Observable, of} from 'rxjs';
import {catchError, map, mergeMap} from 'rxjs/operators';
import {AuthService} from './../services/auth.service';
import {DomainService} from '../services/domain.service';
import {ApplicationService} from '../services/application.service';
import {EnvironmentService} from "../services/environment.service";

@Injectable()
export class AuthGuard implements CanActivate {

  constructor(private authService: AuthService,
              private environmentService: EnvironmentService,
              private domainService: DomainService,
              private applicationService: ApplicationService) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> | Promise<boolean> | boolean {

    // if no permission required, continue
    if (!route.data || !route.data.perms || !route.data.perms.only) {
      return true;
    }
    // check if we need to load some data
    const combineSources: any[] = [];

    const requiredPerms = route.data.perms.only;
    combineSources.push(!this.authService.isAuthenticated() ? this.authService.userInfo() : of(this.authService.user()));

    if ((requiredPerms[0].startsWith('domain') || requiredPerms[0].startsWith('application')) && requiredPerms[0] !== 'domain_create') {
      // check if the authenticated user can navigate to the next route (domain settings or application settings)
      const environmentId = route.paramMap.get('envHrid');
      const domainHrid = route.paramMap.get('domainId');
      const appId = route.paramMap.get('appId');

      if(environmentId && !this.authService.environmentPermissionsLoaded()) {
        combineSources.push(this.environmentService.permissions(environmentId));
      }

      if(domainHrid && !this.authService.domainPermissionsLoaded()) {
        combineSources.push(this.domainService.get(domainHrid)
          .pipe(mergeMap(domain => this.domainService.permissions(domain.id)
            .pipe(mergeMap(__ => {
              if (appId && !this.authService.applicationPermissionsLoaded()) {
                return this.applicationService.permissions(domainHrid, appId)
              } else {
                return of([]);
              }
            })))));
      }
    }
    // check permissions
    return combineLatest(combineSources).pipe(
      map(() =>  this.isAuthorized(requiredPerms)),
      catchError((err) => {
        return of(false);
      }));
  }

  canDisplay(route: ActivatedRouteSnapshot, path): boolean {
    // if no data configuration, continue
    if (!path.data) {
      return true;
    }
    // if resource (application) should not display a settings page, continue
    if (path.data.types && path.data.types.only && path.data.types.only.length > 0) {
      const app = route.parent.data['application'];
      if (app && path.data.types.only.indexOf(app.type.toUpperCase()) === -1) {
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
