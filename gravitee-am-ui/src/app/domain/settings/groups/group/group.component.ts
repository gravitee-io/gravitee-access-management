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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';

@Component({
  selector: 'app-group',
  templateUrl: './group.component.html',
  styleUrls: ['./group.component.scss']
})
export class GroupComponent implements OnInit {
  private domainId: string;
  private organizationContext = false;
  group: any;
  navLinks: any = [ ];

  constructor(private route: ActivatedRoute,
              private router: Router) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.group = this.route.snapshot.data['group'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    this.initNavLinks();
  }

  initNavLinks() {
    this.navLinks.push({'href': 'settings' , 'label': 'Settings'});
    this.navLinks.push({'href': 'members' , 'label': 'Members'});

    if (!this.organizationContext) {
      this.navLinks.push({'href': 'roles' , 'label': 'Roles'});
    }
  }
}
