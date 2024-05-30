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
import { Component, OnDestroy, OnInit, Pipe, PipeTransform } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { filter, take, tap } from 'rxjs/operators';
import { GioLicenseService } from '@gravitee/ui-particles-angular';

import { AppConfig } from '../../../config/app.config';
import { MenuItem, NavigationService } from '../../services/navigation.service';
import { EnvironmentService } from '../../services/environment.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'gv-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss'],
})
export class SidenavComponent implements OnInit, OnDestroy {
  private environmentSubscription: Subscription;

  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  isGlobalSettings = false;
  topMenuItems: MenuItem[] = [];
  footerMenuItems: any[] = [
    {
      label: 'Organization',
      path: '/settings',
      icon: 'gio:building',
      tooltip: 'Organization settings',
    },
  ];
  navSubscription: Subscription;
  itemsSubscription: Subscription;
  currentEnvironment: any;
  environments: any[] = [];
  private rawEnvironments: any[] = [];
  public licenseExpirationDate$: Observable<Date>;

  constructor(
    private router: Router,
    private navigationService: NavigationService,
    private environmentService: EnvironmentService,
    private authService: AuthService,
    private licenseService: GioLicenseService,
  ) {}

  ngOnInit() {
    this.initEnvironments();

    this.environmentSubscription = this.environmentService.currentEnvironmentObs$.subscribe((environment) => {
      this.currentEnvironment = environment;
    });

    this.navSubscription = this.router.events.pipe(filter((evt) => evt instanceof NavigationEnd)).subscribe((evt: NavigationEnd) => {
      this.isGlobalSettings = evt.urlAfterRedirects.startsWith('/settings');
    });

    this.itemsSubscription = this.navigationService.topMenuItemsObs$.subscribe((items) => (this.topMenuItems = items));

    this.licenseExpirationDate$ = this.licenseService.getExpiresAt$().pipe(take(1));
  }

  ngOnDestroy() {
    this.environmentSubscription.unsubscribe();
    this.navSubscription.unsubscribe();
    this.itemsSubscription.unsubscribe();
  }

  switchEnvironment($event: any): void {
    const currentEnvironment = this.rawEnvironments.find((element) => element.id === $event);
    this.environmentService.setCurrentEnvironment(currentEnvironment);
    this.router.navigate(['/', 'environments', currentEnvironment.hrids[0]]);
  }

  canDisplayEnvironments(): boolean {
    return this.environments && this.environments.length > 0 && !this.router.url.startsWith('/settings');
  }

  canDisplayLicence(): boolean {
    return this.authService.hasPermissions(['license_notification_read']);
  }

  canDisplayOrganizationSettings(): boolean {
    return !this.router.url.startsWith('/settings') && this.authService.hasPermissions(['organization_settings_read']);
  }

  private initEnvironments() {
    this.environmentService.getAllEnvironments().subscribe((environments) => {
      this.rawEnvironments = environments;
      this.environments = environments.map((env) => ({ value: env.id, displayValue: env.name }));
    });
  }

  onTopMenuItemClick(topMenuItem: MenuItem) {
    if (topMenuItem.isMissingFeature$) {
      topMenuItem.isMissingFeature$
        .pipe(
          filter((isMissingFeature: boolean) => isMissingFeature),
          tap(() => {
            this.licenseService.openDialog(topMenuItem.licenseOptions);
          }),
        )
        .subscribe();
    }
  }
}

@Pipe({
  name: 'displayableItemFilter',
  pure: false,
})
export class DisplayableItemPipe implements PipeTransform {
  transform(items: any[]): any {
    if (!items || !filter) {
      return items;
    }
    return items.filter((item) => item.display);
  }
}
