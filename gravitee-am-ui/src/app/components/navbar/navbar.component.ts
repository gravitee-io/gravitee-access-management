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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { interval, of, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs/operators';

import { UserNotificationsService } from '../../services/user-notifications.service';
import { AuthService } from '../../services/auth.service';
import { DomainService } from '../../services/domain.service';
import { SidenavService } from '../sidenav/sidenav.service';
import { EnvironmentService } from '../../services/environment.service';
import { UserPreferencesService } from '../../services/user-preferences.service';
import { AppConfig } from '../../../config/app.config';

import { NavbarService } from './navbar.service';

@Component({
  selector: 'gv-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
  standalone: false,
})
export class NavbarComponent implements OnInit, OnDestroy {
  private unsubscribe$ = new Subject<void>();
  private readonly REFRESH_INTERVAL_MS = 10000;

  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  reducedMode = false;
  domains: any[];
  currentDomain: any = {};
  navLinks: any[];
  currentEnvironment: any;
  notifications: any[];
  domainSearchTerm = '';
  private domainSearchTerm$ = new Subject<string>();

  constructor(
    private authService: AuthService,
    private domainService: DomainService,
    private navbarService: NavbarService,
    private sidenavService: SidenavService,
    private environmentService: EnvironmentService,
    private userPreferencesService: UserPreferencesService,
    public router: Router,
    private userNotificationsService: UserNotificationsService,
  ) {}

  ngOnInit() {
    this.initNavLinks();

    this.environmentService.currentEnvironmentObs$
      .pipe(
        tap((environment) => {
          this.currentEnvironment = environment;
          this.initNavLinks();
        }),
        takeUntil(this.unsubscribe$),
      )
      .subscribe();

    this.navbarService.currentDomainObs$
      .pipe(
        tap((currentDomain) => {
          this.currentDomain = currentDomain;
          this.initNavLinks();
        }),
        takeUntil(this.unsubscribe$),
      )
      .subscribe();

    this.sidenavService.resizeSidenavObservable
      .pipe(
        tap((reducedMode) => {
          this.reducedMode = reducedMode;
        }),
        takeUntil(this.unsubscribe$),
      )
      .subscribe();

    // read notifications on component initialization and then trigger a refresh in regular period
    this.userNotificationsService
      .listNotifications()
      .pipe(
        tap((data) => (this.notifications = data)),
        switchMap(() => this.fetchListNotificationsInterval()),
        takeUntil(this.unsubscribe$),
      )
      .subscribe();

    this.domainSearchTerm$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => (term ? this.domainService.search('*' + term + '*', 0, 10) : of(null))),
        takeUntil(this.unsubscribe$),
      )
      .subscribe((response) => {
        if (response) {
          this.domains = response.data;
        } else {
          this.loadPinnedDomains();
        }
      });
  }

  private fetchListNotificationsInterval() {
    return interval(this.REFRESH_INTERVAL_MS).pipe(
      switchMap(() => this.userNotificationsService.listNotifications()),
      tap((data) => (this.notifications = data)),
    );
  }

  ngOnDestroy(): void {
    this.unsubscribe$.next();
    this.unsubscribe$.unsubscribe();
  }

  get user() {
    return this.authService.user() != null ? this.authService.user() : null;
  }

  listDomains() {
    this.domainSearchTerm = '';
    this.loadPinnedDomains();
  }

  onDomainSearch(term: string) {
    this.domainSearchTerm = term;
    this.domainSearchTerm$.next(term);
  }

  togglePin(domain: any, event: Event) {
    event.preventDefault();
    event.stopPropagation();
    this.userPreferencesService.togglePin(domain.id).subscribe(() => {
      if (!this.domainSearchTerm) {
        this.loadPinnedDomains();
      }
    });
  }

  toggleDefault(domain: any, event: Event) {
    event.preventDefault();
    event.stopPropagation();
    this.userPreferencesService.toggleDefaultDomain(domain.id, this.currentEnvironment.id).subscribe();
  }

  isPinned(domainId: string): boolean {
    return this.userPreferencesService.isPinned(domainId);
  }

  isDefault(domainId: string): boolean {
    return this.userPreferencesService.isDefault(domainId);
  }

  private loadPinnedDomains() {
    const currentId = this.currentDomain?.id;
    const defaultId = this.userPreferencesService.defaultDomainId();
    const pinnedIds = this.userPreferencesService.pinnedDomainIds();
    // always surface the current and default domains so they can be shown/pinned even when they aren't yet
    const ids = [...new Set([currentId, defaultId, ...pinnedIds].filter(Boolean))];
    if (!this.hasCurrentEnvironment() || ids.length === 0) {
      this.domains = [];
      return;
    }
    this.domainService.findByIds(ids).subscribe((response) => (this.domains = response.data));
  }

  // the current domain gets its own section so it never shows under "Pinned" when it isn't pinned
  get currentDomainRow(): any {
    if (this.domainSearchTerm || !this.currentDomain?.id || !this.domains) {
      return null;
    }
    return this.domains.find((domain) => domain.id === this.currentDomain.id) ?? null;
  }

  // the default domain is always surfaced, unless it is the current domain (then "Current" already shows it)
  get defaultDomainRow(): any {
    const defaultId = this.userPreferencesService.defaultDomainId();
    if (this.domainSearchTerm || !defaultId || defaultId === this.currentDomain?.id || !this.domains) {
      return null;
    }
    return this.domains.find((domain) => domain.id === defaultId) ?? null;
  }

  get pinnedDomainRows(): any[] {
    if (this.domainSearchTerm || !this.domains) {
      return [];
    }
    const defaultId = this.userPreferencesService.defaultDomainId();
    return this.domains.filter((domain) => domain.id !== this.currentDomain?.id && domain.id !== defaultId);
  }

  private initNavLinks() {
    this.navLinks = [];

    if (this.hasCurrentEnvironment() && this.canDisplay(['domain_list'])) {
      this.navLinks.push({
        href: '/environments/' + this.currentEnvironment.hrids[0] + '/domains',
        label: 'All domains',
        icon: 'developer_board',
      });
    }

    if (this.hasCurrentEnvironment() && this.canDisplay(['domain_create'])) {
      this.navLinks.push({
        href: '/environments/' + this.currentEnvironment.hrids[0] + '/domains/new',
        label: 'New',
        icon: 'add',
      });
    }
  }

  hasCurrentEnvironment(): boolean {
    return this.currentEnvironment && this.currentEnvironment !== EnvironmentService.NO_ENVIRONMENT;
  }

  private canDisplay(permissions): boolean {
    return this.authService.hasPermissions(permissions);
  }

  hasNotifications() {
    return this.notifications && this.notifications.length > 0;
  }

  markNotificationAsRead(notificationId, event) {
    if (this.notifications.filter((notif) => notif.id !== notificationId).length > 0) {
      // keep menu open only of there are more notifications
      event.stopPropagation();
    }
    this.userNotificationsService.markAsRead(notificationId).subscribe(() => {
      this.notifications = this.notifications.filter((notif) => notif.id !== notificationId);
    });
  }

  navigateToHome() {
    this.router.navigateByUrl('');
  }
}
