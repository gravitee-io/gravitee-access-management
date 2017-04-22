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
import {Component, OnInit, OnDestroy} from '@angular/core';
import {Router, NavigationEnd, ActivatedRoute} from "@angular/router";
import { SidenavService } from "./sidenav.service";
import {Subscription} from "rxjs";

@Component({
  selector: 'gs-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss']
})
export class SidenavComponent implements OnInit, OnDestroy {
  reducedMode: boolean = false;
  paths: any[] = [];
  subPaths: any = {};
  currentSubPaths: any[] = [];
  currentResource: any = {};
  subscription: Subscription;
  constructor(private router: Router, private route: ActivatedRoute, private sidenavService : SidenavService) {
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
        this.currentSubPaths = [];
        let _currentSubPaths = this.router.routerState.snapshot.url.split('/');
        if (_currentSubPaths.length > 3) {
          this.currentSubPaths = this.subPaths[_currentSubPaths[1]];
          this.currentSubPaths.forEach(cS => {
            cS.parentPath = _currentSubPaths[1];
            cS.currentResourceId = _currentSubPaths[2];
          });
        }
      })
  }
}
