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
import {Component, OnInit, ViewChild} from '@angular/core';
import {MatStepper} from '@angular/material';
import {ActivatedRoute, Router} from '@angular/router';
import {ProviderService} from '../../../../services/provider.service';
import {SnackbarService} from '../../../../services/snackbar.service';

@Component({
  selector: 'app-idp-creation',
  templateUrl: './provider-creation.component.html',
  styleUrls: ['./provider-creation.component.scss']
})
export class ProviderCreationComponent implements OnInit {
  public provider: any = {};
  private domainId: string;
  private organizationContext: boolean;
  configurationIsValid = false;
  @ViewChild ('stepper') stepper: MatStepper;

  constructor(private providerService: ProviderService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
  }

  create() {
    this.provider.configuration = JSON.stringify(this.provider.configuration);
    this.providerService.create(this.domainId, this.provider, this.organizationContext).subscribe(data => {
      this.snackbarService.open('Provider ' + data.name + ' created');
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  stepperValid() {
    return this.provider && this.provider.name && this.configurationIsValid;
  }
}
