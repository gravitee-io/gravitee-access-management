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
import {ActivatedRoute, Router} from '@angular/router';
import {DomainService} from '../services/domain.service';
import {AuthService} from '../services/auth.service';
import {NavbarService} from "../components/navbar/navbar.service";
import {Subscription} from "rxjs";
import {EnvironmentService} from "../services/environment.service";

@Component({
  selector: 'app-home',
  templateUrl: './environment.component.html',
  styleUrls: ['./environment.component.scss']
})
export class EnvironmentComponent implements OnInit, OnDestroy {
  readonly: boolean = true;
  isLoading: boolean = true;
  hasDomain: boolean = false;
  currentEnvironment: any;
  subscription: Subscription;

  constructor(private router: Router,
              private route: ActivatedRoute,
              private domainService: DomainService,
              private authService: AuthService,
              private navbarService: NavbarService,
              private environmentService: EnvironmentService) {
  }

  ngOnInit() {
    this.subscription = this.environmentService.currentEnvironmentObs$.subscribe(environment => {
      this.currentEnvironment = environment;
      this.initDomains();
    });
  }

  ngOnDestroy() {
  }

  initDomains() {
    // redirect user to the first domain, if any.
    this.domainService.list().subscribe(response => {
      if (response.data && response.data.length > 0) {
        this.router.navigate(['domains', response.data[0].hrid], {relativeTo: this.route});
      } else {
        this.isLoading = false;
        this.readonly = !this.authService.hasPermissions(['domain_create']);
        this.navbarService.notifyDomain({});
      }
    });
  }
}
