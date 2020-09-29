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
import {SnackbarService} from '../../../../services/snackbar.service';
import {FactorService} from '../../../../services/factor.service';

@Component({
  selector: 'app-factor-creation',
  templateUrl: './factor-creation.component.html',
  styleUrls: ['./factor-creation.component.scss']
})
export class FactorCreationComponent implements OnInit {
  private domainId: string;
  factor: any = {};
  configurationIsValid = true;
  @ViewChild ('stepper') stepper: MatStepper;

  constructor(private factorService: FactorService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
  }

  create() {
    this.factor.configuration = JSON.stringify(this.factor.configuration);
    this.factorService.create(this.domainId, this.factor).subscribe(data => {
      this.snackbarService.open('Factor ' + data.name + ' created');
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  stepperValid() {
    return this.factor &&
      this.factor.name &&
      this.configurationIsValid;
  }

}
