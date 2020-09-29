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
import {DomainService} from '../services/domain.service';
import {AuthService} from '../services/auth.service';
import {NavbarService} from "../components/navbar/navbar.service";
import {Subscription} from "rxjs";
import {EnvironmentService} from "../services/environment.service";

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  readonly: boolean = true;
  isLoading = true;
  hasEnv = false;
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
      this.initDomain(environment);
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  initDomain(environment: any) {
    if (this.hasCurrentEnvironment()) {
      this.hasEnv = true;

      // redirect user to the first domain, if any.
      this.domainService.list().subscribe(response => {
        if (response && response.length > 0) {
          this.router.navigate(['environments', environment.hrids[0], 'domains', response[0].id], { relativeTo: this.route});
        } else {
          this.isLoading = false;
          this.readonly = !this.authService.hasPermissions(['domain_create']);
          this.navbarService.notifyDomain({});
        }
      });
    } else {
      this.isLoading = false;
      this.hasEnv = false;
    }
  }

  private hasCurrentEnvironment(): boolean {
    return this.currentEnvironment && this.currentEnvironment !== EnvironmentService.NO_ENVIRONMENT;
  }
}
