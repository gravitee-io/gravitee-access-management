import {filter, tap} from 'rxjs/operators';
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
import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, ActivationEnd, NavigationEnd, Router} from '@angular/router';
import {SidenavService} from './sidenav.service';
import {AppConfig} from '../../../config/app.config';
import {Subscription} from 'rxjs';
import {NavigationService} from "../../services/navigation.service";
import {MatSelectChange} from "@angular/material/select";
import {EnvironmentService} from "../../services/environment.service";
import {AuthService} from "../../services/auth.service";

@Component({
  selector: 'gv-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss']
})
export class SidenavComponent implements OnInit, OnDestroy {
  private environmentSubscription: Subscription;

  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  reducedMode = false;
  isGlobalSettings = false;
  topMenuItems: any[] = [];
  navSubscription: Subscription;
  itemsSubscription: Subscription;
  currentEnvironment: any;
  environments: any[] = [];

  constructor(private router: Router,
              private route: ActivatedRoute,
              private navigationService: NavigationService,
              private sidenavService: SidenavService,
              private environmentService: EnvironmentService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.initEnvironments();

    this.environmentSubscription = this.environmentService.currentEnvironmentObs$.subscribe(environment => {
      this.currentEnvironment = environment;
    });

    this.navSubscription = this.router.events.pipe(filter(evt => evt instanceof NavigationEnd))
      .subscribe((evt: NavigationEnd) => {
        this.isGlobalSettings = evt.urlAfterRedirects.startsWith('/settings');
      });

    this.itemsSubscription = this.navigationService.topMenuItemsObs$
      .subscribe(items => this.topMenuItems = items);
  }

  ngOnDestroy() {
    this.environmentSubscription.unsubscribe();
    this.navSubscription.unsubscribe();
    this.itemsSubscription.unsubscribe();
  }

  resize() {
    this.reducedMode = !this.reducedMode;
    this.sidenavService.resize(this.reducedMode);
  }

  private initEnvironments() {
    this.environmentService.getAllEnvironments().subscribe(environments => {
      this.environments = environments;
    });
  }

  switchEnvironment($event: MatSelectChange) {
    this.environmentService.setCurrentEnvironment($event.value);
    this.router.navigate(['/', 'environments', $event.value.hrids[0]]);
  }

  canDisplayEnvironments(): boolean {
    return this.environments && this.environments.length > 0 && !this.router.url.startsWith('/settings');
  }

  canDisplayOrganizationSettings(): boolean {
    return !this.router.url.startsWith('/settings') && this.authService.hasPermissions(['organization_settings_read']);
  }
}
