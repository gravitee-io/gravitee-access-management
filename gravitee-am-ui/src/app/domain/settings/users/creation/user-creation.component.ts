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
import {Component, OnInit, ComponentFactoryResolver, ViewContainerRef, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {SnackbarService} from "../../../../services/snackbar.service";
import {UserService} from "../../../../services/user.service";
import {AppConfig} from "../../../../../config/app.config";
import {animate, style, transition, trigger} from "@angular/animations";
import {UserClaimComponent} from "./user-claim.component";
import * as _ from 'lodash';
import {ProviderService} from "../../../../services/provider.service";

@Component({
  selector: 'user-creation',
  animations: [
    trigger(
      'fadeInOut', [
        transition(':leave', [
          animate(500, style({opacity:0}))
        ])
      ]
    )
  ],
  templateUrl: './user-creation.component.html',
  styleUrls: ['./user-creation.component.scss']
})
export class UserCreationComponent implements OnInit {
  private domainId: string;
  private adminContext: boolean;
  preRegistration: boolean = false;
  hidePassword: boolean = true;
  useEmailAsUsername: boolean = false;
  user: any = {};
  userClaims: any = {};
  userProviders: any[];
  @ViewChild('dynamic', { read: ViewContainerRef }) viewContainerRef: ViewContainerRef;

  constructor(private userService: UserService,
              private router: Router,
              private route: ActivatedRoute,
              private snackbarService: SnackbarService,
              private factoryResolver: ComponentFactoryResolver,
              private providerService: ProviderService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
      this.adminContext = true;
    }

    this.providerService.findUserProvidersByDomain(this.domainId).subscribe(response => {
      this.userProviders = response;
    });
  }

  create() {
    // set additional information
    if (this.userClaims && Object.keys(this.userClaims).length > 0) {
      let additionalInformation = {};
      _.each(this.userClaims, function(item) {
        additionalInformation[item.claimName] = item.claimValue;
      });
      this.user.additionalInformation = additionalInformation;
    }
    // set pre-registration
    this.user.preRegistration = this.preRegistration;
    this.userService.create(this.domainId, this.user).subscribe(data => {
      this.snackbarService.open("User " + data.username + " created");
      if (this.adminContext) {
        this.router.navigate(['/settings', 'management', 'users', data.id]);
      } else {
        this.router.navigate(['/domains', this.domainId, 'settings', 'users', data.id]);
      }
    });
  }

  onEmailChange(email) {
    if (this.useEmailAsUsername) {
      if (!email || email === '') {
        this.useEmailAsUsername = false;
        this.user.username = '';
      } else {
        this.user.username = email;
      }
    }
  }

  toggleUseEmailAsUsername(event) {
    this.useEmailAsUsername = event.checked;
    this.user.username = this.useEmailAsUsername ? this.user.email : '';
  }

  addDynamicComponent() {
    const factory = this.factoryResolver.resolveComponentFactory(UserClaimComponent);
    const component = this.viewContainerRef.createComponent(factory);

    let that = this;
    component.instance.addClaimChange.subscribe(claim => {
      if (claim.name && claim.value) {
        that.userClaims[claim.id] = {'claimName': claim.name, 'claimValue': claim.value};
      }
    });

    component.instance.removeClaimChange.subscribe(claim => {
      delete that.userClaims[claim.id];
      that.viewContainerRef.remove(that.viewContainerRef.indexOf(component.hostView));
      if (claim.name && claim.value) {
        that.snackbarService.open('Claim ' + claim.name + ' deleted');
      }
    });
  }

  onAppSelectionChanged(event) {
    this.user.client = event.id;
  }

  onAppDeleted(event) {
    this.user.client = null;
  }
}
