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
import { interval, Subject } from 'rxjs';
import { switchMap, takeUntil, tap } from 'rxjs/operators';

import { UserNotificationsService } from '../../services/user-notifications.service';
import { AuthService } from '../../services/auth.service';
import { DomainService } from '../../services/domain.service';
import { SidenavService } from '../sidenav/sidenav.service';
import { EnvironmentService } from '../../services/environment.service';
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

  constructor(
    private authService: AuthService,
    private domainService: DomainService,
    private navbarService: NavbarService,
    private sidenavService: SidenavService,
    private environmentService: EnvironmentService,
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
    // this.userNotificationsService
    //   .listNotifications()
    //   .pipe(
    //     tap((data) => (this.notifications = data)),
    //     switchMap(() => this.fetchListNotificationsInterval()),
    //     takeUntil(this.unsubscribe$),
    //   )
    //   .subscribe();
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
    if (this.hasCurrentEnvironment()) {
      this.domainService.findByEnvironment(0, 5).subscribe((response) => (this.domains = response.data));
    } else {
      this.domains = [];
    }
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
        label: 'Create domain',
        icon: 'add',
      });
    }

    this.navLinks.push({ href: '/logout', label: 'Sign out', icon: 'exit_to_app' });
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
