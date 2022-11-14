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
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { SidenavService } from "./components/sidenav/sidenav.service";
import { AuthService } from "./services/auth.service";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  reducedMode: boolean = false;

  constructor(public router : Router,
              private authService: AuthService,
              private sidenavService: SidenavService) {}

  ngOnInit() {
    this.authService.notifyObservable$.subscribe(response => {
      if (response && response === 'Unauthorized') {
        this.router.navigate(['/login']);
      }
    })

    this.sidenavService.resizeSidenavObservable.subscribe(reducedMode => this.reducedMode = reducedMode);
  }

  displaySidenav(): boolean {
    return this.router.url !== '/login' && this.router.url !== '/newsletter';
  }

  displayNavbar(): boolean {
    return this.router.url !== '/login' && this.router.url !== '/newsletter';
  }

  displayBreadcrumb(): boolean {
    return !this.router.url.startsWith('/domains/new') &&
      !this.router.url.startsWith('/login') &&
      !this.router.url.startsWith('/logout') &&
      !this.router.url.startsWith('/404');
  }
}
