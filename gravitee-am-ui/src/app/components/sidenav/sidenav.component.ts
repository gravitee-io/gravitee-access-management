
import {filter} from 'rxjs/operators';
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
import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { SidenavService } from './sidenav.service';
import { AppConfig } from '../../../config/app.config';
import { AuthGuard } from '../../guards/auth-guard.service';
import { Subscription} from 'rxjs';
import * as _ from 'lodash';

@Component({
  selector: 'gv-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss']
})
export class SidenavComponent implements OnInit, OnDestroy {
  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  reducedMode = false;
  paths: any[] = [];
  subPaths: any = {};
  currentSubPaths: any[] = [];
  subscription: Subscription;
  displayFirstLevel = false;
  displaySettingsLevel = false;

  constructor(private router: Router,
              private currentRoute: ActivatedRoute,
              private sidenavService: SidenavService,
              private authGuard: AuthGuard) {
  }

  ngOnInit() {
    for (const route of this.router.config) {
      if (route.data && route.data.menu && route.data.menu.firstLevel) {
        this.paths.push(route);
      }
      if (route.children) {
        this.subPaths[route.path.split('/')[0]] = route.children.filter(c => c.data && c.data.menu);
      }
    }
    this.watchRoute();
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  watchRoute() {
    const that = this;
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        for (const route of that.paths) {
          route.data.menu.active = false;
        }
        this.displayFirstLevel = true;
        this.displaySettingsLevel = false;
        const currentSnapshot = this.currentRoute.root.firstChild.snapshot;
        if (currentSnapshot.data && currentSnapshot.data.menu) {
          // check if we display first level menu items
          const displayFirstLevel = currentSnapshot.data.menu.displayFirstLevel;
          this.displayFirstLevel = (typeof displayFirstLevel !== 'undefined') ? displayFirstLevel : true;
          // check if we display settings level menu items
          if (currentSnapshot.data.menu.displaySettingsLevel) {
            const displaySettingsLevel = currentSnapshot.data.menu.displaySettingsLevel;
            this.displaySettingsLevel = (typeof displaySettingsLevel !== 'undefined') ? displaySettingsLevel : false;
          }
          // check if we active first level menu
          const activeParentPath = currentSnapshot.data.menu.activeParentPath;
          if (activeParentPath) {
            const path = _.find(that.paths, p => p.path === activeParentPath);
            if (path) {
              path.data.menu.active = true;
            }
          }
        }
        this.currentSubPaths = [];
        const _currentSubPaths = this.router.routerState.snapshot.url.split('/');
        if (_currentSubPaths.length > 2) {
          this.currentSubPaths = this.subPaths[_currentSubPaths[1]];
          if (this.currentSubPaths) {
            // filter paths to display
            this.currentSubPaths = this.currentSubPaths.filter(subPath => this.authGuard.canDisplay(currentSnapshot, subPath));
            this.currentSubPaths.forEach(cS => {
              cS.parentPath = _currentSubPaths[1];
              cS.currentResourceId = _currentSubPaths[2];
              cS.fullPath = [];
              cS.fullPath.push(cS.parentPath);
              if (!this.displaySettingsLevel) {
                cS.fullPath.push(cS.currentResourceId);
              }
              cS.fullPath.push(cS.path);
            });
          }
        }
      })
  }

  resize() {
    this.reducedMode = !this.reducedMode;
    this.sidenavService.resize(this.reducedMode);
  }
}
