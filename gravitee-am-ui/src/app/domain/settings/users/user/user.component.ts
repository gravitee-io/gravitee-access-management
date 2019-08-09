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
import { ActivatedRoute } from "@angular/router";
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  private domainId: string;
  user: any;
  displayName: string;
  avatarUrl: string;
  navLinks: any = [
    {'href': 'profile' , 'label': 'Profile'},
    {'href': 'applications' , 'label': 'Authorized Apps'},
    {'href': 'roles' , 'label': 'Roles'},
  ];

  constructor(private route: ActivatedRoute, private breadcrumbService: BreadcrumbService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.user = this.route.snapshot.data['user'];
    this.initDisplayName();
    this.initAvatar();
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/users/'+this.user.id+'$', this.user.username);
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/users/'+this.user.id+'/applications$', 'Authorized Apps');
  }

  initAvatar() {
    if (this.user.additionalInformation && this.user.additionalInformation['picture']) {
      this.avatarUrl = this.user.additionalInformation['picture'];
      return;
    }
    this.avatarUrl = 'assets/material-letter-icons/' + this.user.username.charAt(0).toUpperCase() + '.svg';
  }

  initDisplayName() {
    // check display name attribute first
    if (this.user.displayName) {
      this.displayName = this.user.displayName;
      return;
    }

    // fall back to standard claim 'name'
    if (this.user.additionalInformation && this.user.additionalInformation['name']) {
      this.displayName = this.user.additionalInformation['name'];
      return;
    }

    // fall back to combination of first name and last name
    if (this.user.firstName) {
      this.displayName = this.user.firstName;
      if (this.user.lastName) {
        this.displayName += ' ' + this.user.lastName;
      } else if (this.user.additionalInformation && this.user.additionalInformation['family_name']) {
        this.displayName += ' ' + this.user.additionalInformation['family_name']
      }
      return;
    }

    if (this.user.additionalInformation && this.user.additionalInformation['given_name']) {
      this.displayName = this.user.additionalInformation['given_name'];
      if (this.user.additionalInformation && this.user.additionalInformation['family_name']) {
        this.displayName += ' ' + this.user.additionalInformation['family_name']
      }
      return;
    }

    // default display the username
    this.displayName = this.user.username;
  }
}
