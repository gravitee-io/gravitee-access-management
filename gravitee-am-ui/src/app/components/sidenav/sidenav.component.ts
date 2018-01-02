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
import { Router, NavigationEnd, ActivatedRoute } from "@angular/router";
import { SidenavService } from "./sidenav.service";
import { Subscription} from "rxjs";
import { AuthService } from "../../services/auth.service";
import { AppConfig } from "../../../config/app.config";

@Component({
  selector: 'gs-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss']
})
export class SidenavComponent implements OnInit, OnDestroy {
  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  reducedMode: boolean = false;
  paths: any[] = [];
  subPaths: any = {};
  currentSubPaths: any[] = [];
  currentResource: any = {};
  subscription: Subscription;
  displayFirstLevel: boolean = false;
  displaySettingsLevel: boolean = false;

  constructor(private router: Router, private currentRoute:ActivatedRoute, private sidenavService : SidenavService, private authService: AuthService) {
  }

  ngOnInit() {
    for (let route of this.router.config) {
      if (route.data && route.data.menu.firstLevel) {
        this.paths.push(route);
      }
      if (route.children) {
        this.subPaths[route.path.split('/')[0]] = route.children.filter(c => c.data);
      }
    }
    this.watchRoute();
    this.subscription = this.sidenavService.notifyObservable$.subscribe(data => {
      this.currentResource = data;
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  watchRoute() {
    this.router.events
      .filter(event => event instanceof NavigationEnd)
      .subscribe(event => {
        this.displayFirstLevel = false;
        this.displaySettingsLevel = false;
        if (this.currentRoute.root.firstChild.snapshot.data && this.currentRoute.root.firstChild.snapshot.data.menu) {
          // check if we display first level menu items
          let displayFirstLevel = this.currentRoute.root.firstChild.snapshot.data.menu.displayFirstLevel;
          this.displayFirstLevel = (typeof displayFirstLevel != 'undefined') ? displayFirstLevel : true;
          // check if we display settings level menu items
          if (this.currentRoute.root.firstChild.snapshot.data.menu.displaySettingsLevel) {
            let displaySettingsLevel = this.currentRoute.root.firstChild.snapshot.data.menu.displaySettingsLevel;
            this.displaySettingsLevel = (typeof displaySettingsLevel != 'undefined') ? displaySettingsLevel : false;
          }
        }
        this.currentSubPaths = [];
        let _currentSubPaths = this.router.routerState.snapshot.url.split('/');
        if (_currentSubPaths.length > 2) {
          this.currentSubPaths = this.subPaths[_currentSubPaths[1]];
          if (this.currentSubPaths) {
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

  get user() {
    return this.authService.user();
  }

  isAuthenticated() {
    return this.authService.isAuthenticated();
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  resize() {
    this.reducedMode = !this.reducedMode;
    this.sidenavService.resize(this.reducedMode);
  }
}
