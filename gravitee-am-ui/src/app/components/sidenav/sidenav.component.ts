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
import { Subscription } from 'rxjs';
import { filter, take, tap } from 'rxjs/operators';
import { GioLicenseService, License } from '@gravitee/ui-particles-angular';

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
  licenseExpirationMessage: string;
  private rawEnvironments: any[] = [];
  private expirationDays: number;

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

    this.licenseService
      .getLicense$()
      .pipe(take(1))
      .subscribe((license: GraviteeLicense) => this.setLicenseExpirationMessage(license));
  }

  private setLicenseExpirationMessage(licenseObj: GraviteeLicense) {
    if (licenseObj.expirationDate) {
      const now = new Date();
      const expirationDate = new Date(licenseObj.expirationDate);
      this.expirationDays = this.dateDiffDays(expirationDate, now);
      if (this.expirationDays > 0) {
        this.licenseExpirationMessage = `Your license will expire in ${this.expirationDays} days`;
      } else {
        this.licenseExpirationMessage = `Your license has expired`;
      }
    }
  }

  showExpirationMessage() {
    return this.expirationDays < 31;
  }

  getLicenseColor(): string {
    if (this.expirationDays === undefined) {
      return '';
    } else if (this.expirationDays > 15) {
      return 'color: #0482c7; background-color: #E7F8FF;';
    } else if (this.expirationDays > 0) {
      return 'color: #9C4626; background-color: #FDEDE5;';
    } else {
      return 'color: #9E2C64; background-color: #FDE9F4;';
    }
  }

  ngOnDestroy() {
    this.environmentSubscription.unsubscribe();
    this.navSubscription.unsubscribe();
    this.itemsSubscription.unsubscribe();
  }

  dateDiffDays(date1: Date, date2: Date): number {
    return parseInt(String((date1.getTime() - date2.getTime()) / (1000 * 60 * 60 * 24)), 10);
  }

  switchEnvironment($event: any) {
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

interface GraviteeLicense extends License {
  expirationDate: number;
}
