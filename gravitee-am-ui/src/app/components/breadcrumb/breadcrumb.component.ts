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
import {Component, Input, OnChanges, OnDestroy, OnInit} from '@angular/core';
import {NavigationEnd, Router} from '@angular/router';
import {BreadcrumbService} from '../../services/breadcrumb.service';

@Component({
  selector: 'gv-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.scss']
})
export class BreadcrumbComponent implements OnInit, OnChanges, OnDestroy {
  @Input() prefix: string = '';

  public urls: any[];
  public _routerSubscription: any;
  private breadcrumbEventSubscription: any;
  private navigateUrl;

  constructor(
    private router: Router,
    private breadcrumbService: BreadcrumbService,
  ) {
    this.urls = [];

    // Subscribe to breadcrumb service events to be notified of route regex changes and trigger a breadcrumb regeneration.
    this.breadcrumbEventSubscription = breadcrumbService.events.subscribe(e => {
      if (this.navigateUrl) {
        this.regenerateBreadcrumb(this.navigateUrl);
      }
    });

    if (this.prefix.length > 0) {
      this.urls.unshift(this.buildUrl(this.prefix));
    }

    this._routerSubscription = this.router.events.subscribe((navigationEnd: NavigationEnd) => {
      if (navigationEnd instanceof NavigationEnd) {
        this.navigateUrl = navigationEnd.urlAfterRedirects ? navigationEnd.urlAfterRedirects : navigationEnd.url;
        this.regenerateBreadcrumb(this.navigateUrl);
      }
    });
  }

  ngOnInit(): void {
  }

  private regenerateBreadcrumb(url) {
    this.urls.length = 0;
    this.generateBreadcrumbTrail(url);
  }

  buildUrl(url): any {
    return {displayUrl: this.friendlyName(url), navigateTo: url};
  }

  ngOnChanges(changes: any): void {
    if (!this.urls) {
      return;
    }

    this.urls.length = 0;
    this.generateBreadcrumbTrail(this.router.url);
  }

  generateBreadcrumbTrail(url: string): void {
    if (!this.breadcrumbService.isRouteHidden(url)) {
      //Add url to beginning of array (since the url is being recursively broken down from full url to its parent)
      this.urls.unshift(this.buildUrl(url));
    }

    if (url.lastIndexOf('/') > 0) {
      this.generateBreadcrumbTrail(url.substr(0, url.lastIndexOf('/'))); //Find last '/' and add everything before it as a parent route
    } else if (this.prefix.length > 0) {
      this.urls.unshift(this.buildUrl(this.prefix));
    }
  }

  navigateTo(url: string): void {
    this.router.navigateByUrl(url);
  }

  friendlyName(url: string): string {
    // dummy url is never displayed in the breadcrumb to avoid blink effect.
    return !url || url === '/dummy' ? '' : this.breadcrumbService.getFriendlyNameForRoute(url);
  }

  ngOnDestroy(): void {
    this._routerSubscription.unsubscribe();
    this.breadcrumbEventSubscription.unsubscribe();
  }
}
