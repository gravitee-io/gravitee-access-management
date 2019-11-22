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
import { Component, Input } from '@angular/core';
import { Router } from "@angular/router";

@Component({
  selector: 'gs-sidenav-settings',
  templateUrl: './sidenav-settings.component.html',
  styleUrls: ['./sidenav-settings.component.scss']
})
export class SidenavSettingsComponent {
  @Input('filterLvl1') filterLvl1: string;
  @Input('filterLvl2') filterLvl2: string;
  @Input('filterLvl3') filterLvl3: string;
  paths: any = {};

  constructor(private router: Router) { }

  ngOnInit() {
    this.router.config.filter(r => r.path ===  this.filterLvl1).forEach(r => {
      r.children.filter(r => r.path === this.filterLvl2).forEach(r => {
        if (this.filterLvl3) {
          r.children.filter(r => r.path === this.filterLvl3).forEach(r => {
            this.setPaths(r);
          });
        } else {
          this.setPaths(r);
        }
      });
    });
  }

  private setPaths(currentRoute) {
    currentRoute.children.filter(r => r.data).forEach(r => {
      if (this.paths[r.data.menu.section]) {
        this.paths[r.data.menu.section].push(r)
      } else {
        this.paths[r.data.menu.section] = [r];
      }
    });
  }
}
