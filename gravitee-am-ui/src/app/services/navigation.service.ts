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
import { Injectable, OnDestroy } from '@angular/core';
import { Observable, ReplaySubject, Subject, Subscription } from 'rxjs';
import { ActivatedRouteSnapshot, ActivationEnd, Data, NavigationEnd, Route, Router } from '@angular/router';
import { buffer, filter, map, pluck } from 'rxjs/operators';
import { LicenseOptions } from '@gravitee/ui-particles-angular';

import { AuthGuard } from '../guards/auth-guard.service';
import { LicenseGuard } from '../guards/license-guard.service';

export interface MenuItem {
  label: string;
  icon: string;
  path: string[];
  section: string;
  level: string;
  beta: boolean;
  display: boolean;
  routerLinkActiveOptions: object;
  licenseOptions?: LicenseOptions;
  isMissingFeature$?: Observable<boolean>;
  iconRight$?: Observable<string | undefined>;
}

@Injectable()
export class NavigationService implements OnDestroy {
  public topMenuItemsObs$: Subject<any[]> = new ReplaySubject<any[]>(1);
  public subMenuItemsObs$: Subject<any[]> = new ReplaySubject<any[]>(1);
  public breadcrumbItemsObs$: Subject<any[]> = new ReplaySubject<any[]>(1);
  public level2MenuItemsObs$: Subject<any[]> = new ReplaySubject<any[]>(1);
  public level3MenuItemsObs$: Subject<any[]> = new ReplaySubject<any[]>(1);

  subscription: Subscription;

  constructor(
    private router: Router,
    private authGuard: AuthGuard,
    private licenseGuard: LicenseGuard,
  ) {
    const navigationEnd$ = this.router.events.pipe(filter((ev) => ev instanceof NavigationEnd));

    this.subscription = this.router.events
      .pipe(
        filter((ev) => ev instanceof ActivationEnd),
        pluck('snapshot'),
        buffer(navigationEnd$),
        map((activatedRoutes: ActivatedRouteSnapshot[]) => activatedRoutes[0]),
      )
      .subscribe((activatedRoute) => this.notifyChanges(activatedRoute));
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
    this.topMenuItemsObs$.complete();
    this.subMenuItemsObs$.complete();
    this.breadcrumbItemsObs$.complete();
    this.level2MenuItemsObs$.complete();
    this.level3MenuItemsObs$.complete();
  }

  notifyChanges(activatedRoute: ActivatedRouteSnapshot): void {
    const menuItems = this.getMenuItems(activatedRoute);
    this.topMenuItemsObs$.next(menuItems.filter((item) => item.level === 'top'));
    this.subMenuItemsObs$.next(menuItems.filter((item) => item.level !== 'top'));
    this.level2MenuItemsObs$.next(menuItems.filter((item) => item.level === 'level2'));
    this.level3MenuItemsObs$.next(menuItems.filter((item) => item.level === 'level3'));
    this.breadcrumbItemsObs$.next(this.getBreadcrumbItems(activatedRoute));
  }

  getBreadcrumbItems(route: ActivatedRouteSnapshot): any[] {
    let breadcrumbItems: any[] = [];

    if (route != null) {
      breadcrumbItems = this.getBreadcrumbItems(route.parent);

      const routeConfig = route.routeConfig;
      const breadcrumbData = routeConfig?.data?.breadcrumb;

      if (routeConfig && !breadcrumbData?.disabled && routeConfig.path !== '') {
        breadcrumbItems.push({
          label: this.resolveLabel(breadcrumbData, routeConfig.path, route.data),
          path: this.resolvePath(route),
        });
      }
    }

    return breadcrumbItems;
  }

  getMenuItems(route: ActivatedRouteSnapshot): MenuItem[] {
    let menuItems: MenuItem[] = [];

    if (route?.parent) {
      if (route.parent.routeConfig?.children) {
        route.parent.routeConfig.children.forEach((siblingRoute) => {
          if (siblingRoute.data?.menu && this.authGuard.canDisplay(route.parent, siblingRoute)) {
            const isMissingFeature$ = this.licenseGuard.isMissingFeature$(siblingRoute);
            const licenseOptions = this.licenseGuard.getLicenseOptions(siblingRoute);
            const iconRight$ = isMissingFeature$.pipe(map((isMissingFeature) => (isMissingFeature ? 'gio:lock' : undefined)));

            menuItems.push({
              label: siblingRoute.data.menu.label,
              icon: siblingRoute.data.menu.icon,
              path: this.resolvePath(route.parent, siblingRoute.path),
              section: siblingRoute.data.menu.section,
              level: siblingRoute.data.menu.level,
              beta: siblingRoute.data.menu.beta,
              display: this.resolveDisplay(route, siblingRoute),
              routerLinkActiveOptions: siblingRoute.data.menu.routerLinkActiveOptions ? siblingRoute.data.menu.routerLinkActiveOptions : {},
              licenseOptions,
              isMissingFeature$,
              iconRight$,
            });
          }
        });
      }

      menuItems = menuItems.concat(this.getMenuItems(route.parent));
    }

    return menuItems;
  }

  resolveDisplay(activatedRoute: ActivatedRouteSnapshot, route: Route): boolean {
    if (route.data.menu.displayOptions && route.data.menu.displayOptions.exact === true) {
      return this.router.routerState.snapshot.url === this.resolvePath(activatedRoute.parent, route.path).join('/');
    }
    return true;
  }

  resolvePath(route: ActivatedRouteSnapshot, path?: string) {
    let paths: string[] = [];
    route.pathFromRoot.forEach((p) => p.url.forEach((url) => paths.push(url.path)));

    if (path) {
      paths = paths.concat(path.split('/'));
    }

    paths[0] = '/' + paths[0];

    return paths;
  }

  resolveLabel(data: any, alt: string, data1: Data): string {
    if (data?.label) {
      const resolved = this.getProperty(data.label, data1);
      const label = resolved || data.label;

      if (data.applyOnLabel && typeof data.applyOnLabel === 'function') {
        return data.applyOnLabel(label);
      } else {
        return label;
      }
    }

    return alt;
  }

  getProperty(propertyName: string, object: any) {
    const parts = propertyName.split('.');
    const length = parts.length;
    let property = object || this;

    for (let i = 0; i < length; i++) {
      property = property[parts[i]];
    }

    return property;
  }
}
