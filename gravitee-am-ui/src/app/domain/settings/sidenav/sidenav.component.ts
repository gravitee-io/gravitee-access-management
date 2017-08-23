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
import { Router } from "@angular/router";

@Component({
  selector: 'domain-settings-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss']
})
export class DomainSettingsSidenavComponent implements OnInit {
  paths: any = {};

  constructor(private router: Router) { }

  ngOnInit() {
    this.router.config.filter(r => r.path ===  'domains/:domainId').forEach(r => {
      r.children.filter(r => r.path === 'settings').forEach(r => {
        r.children.filter(r => r.data).forEach(r => {
          if (this.paths[r.data.menu.section]) {
            this.paths[r.data.menu.section].push(r)
          } else {
            this.paths[r.data.menu.section] = [r];
          }
        });
      });
    });
  }
}
