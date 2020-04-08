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
import {Subject} from 'rxjs';

@Injectable()
export class BreadcrumbService {

  private routesFriendlyNames: Map<string, string> = new Map<string, string>();
  private routesFriendlyNamesRegex: Map<string, string> = new Map<string, string>();
  private routesWithCallback: Map<string, (string: string) => string> = new Map<string, (string: string) => string>();
  private routesWithCallbackRegex: Map<string, (string: string) => string> = new Map<string, (string: string) => string>();
  private hideRoutes: any = new Array<string>();
  private hideRoutesRegex: any = new Array<string>();

  public events: Subject<any> = new Subject<any>();

  /**
   * Specify a friendly name for the corresponding route.
   *
   * @param route
   * @param name
   */
  addFriendlyNameForRoute(route: string, name: string): void {
    this.routesFriendlyNames.set(route, name);
    this.notify();
  }

  /**
   * Specify a friendly name for the corresponding route matching a regular expression.
   *
   * @param route
   * @param name
   */
  addFriendlyNameForRouteRegex(routeRegex: string, name: string): void {
    this.routesFriendlyNamesRegex.set(routeRegex, name);
    this.notify();
  }

  /**
   * Specify a callback for the corresponding route.
   * When a mathing url is navigatedd to, the callback function is invoked to get the name to be displayed in the breadcrumb.
   */
  addCallbackForRoute(route: string, callback: (id: string) => string): void {
    this.routesWithCallback.set(route, callback);
    this.notify();
  }

  /**
   * Specify a callback for the corresponding route matching a regular expression.
   * When a mathing url is navigatedd to, the callback function is invoked to get the name to be displayed in the breadcrumb.
   */
  addCallbackForRouteRegex(routeRegex: string, callback: (id: string) => string): void {
    this.routesWithCallbackRegex.set(routeRegex, callback);
    this.notify();
  }

  /**
   * Show the friendly name for a given route (url). If no match is found the url (without the leading '/') is shown.
   *
   * @param route
   * @returns {*}
   */
  getFriendlyNameForRoute(route: string): string {
    let name: string;
    const routeEnd = route.substr(route.lastIndexOf('/') + 1, route.length);

    this.routesFriendlyNames.forEach((value, key, map) => {
      if (key === route) {
        name = value;
      }
    });

    this.routesFriendlyNamesRegex.forEach((value, key, map) => {
      if (new RegExp(key).exec(route)) {
        name = value;
      }
    });

    this.routesWithCallback.forEach((value, key, map) => {
      if (key === route) {
        name = value(routeEnd);
      }
    });

    this.routesWithCallbackRegex.forEach((value, key, map) => {
      if (new RegExp(key).exec(route)) {
        name = value(routeEnd);
      }
    });

    return name ? name : routeEnd;
  }

  /**
   * Specify a route (url) that should not be shown in the breadcrumb.
   */
  hideRoute(route: string): void {
    if (this.hideRoutes.indexOf(route) === -1) {
      this.hideRoutes.push(route);
      this.notify();
    }
  }

  /**
   * Specify a route (url) regular expression that should not be shown in the breadcrumb.
   */
  hideRouteRegex(routeRegex: string): void {
    if (this.hideRoutesRegex.indexOf(routeRegex) === -1) {
      this.hideRoutesRegex.push(routeRegex);
      this.notify();
    }
  }

  /**
   * Returns true if a route should be hidden.
   */
  isRouteHidden(route: string): boolean {
    let hide = this.hideRoutes.indexOf(route) > -1;

    this.hideRoutesRegex.forEach((value: any) => {
      if (new RegExp(value).exec(route)) {
        hide = true;
      }
    });

    return hide;
  }

  notify() {
    this.events.next('refresh');
  }
}
