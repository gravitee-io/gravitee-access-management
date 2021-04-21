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
import { MatStepper } from '@angular/material/stepper';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../services/snackbar.service';
import {ResourceService} from '../../../../services/resource.service';

@Component({
  selector: 'app-resource-creation',
  templateUrl: './resource-creation.component.html',
  styleUrls: ['./resource-creation.component.scss']
})
export class ResourceCreationComponent implements OnInit {
  private domainId: string;
  resource: any = {};
  configurationIsValid = true;
  @ViewChild('stepper', { static: true }) stepper: MatStepper;

  constructor(private resourceService: ResourceService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create() {
    this.resource.configuration = JSON.stringify(this.resource.configuration);
    this.resourceService.create(this.domainId, this.resource).subscribe(data => {
      this.snackbarService.open('Resource ' + data.name + ' created');
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  stepperValid() {
    return this.resource &&
      this.resource.name &&
      this.configurationIsValid;
  }

}
