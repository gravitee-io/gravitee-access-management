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
import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {PlatformService} from '../../../../../../services/platform.service';

@Component({
  selector: 'factor-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class FactorCreationStep2Component implements OnInit {
  @Input('factor') factor: any;
  @Input('configurationIsValid') configurationIsValid: boolean;
  @Output('configurationIsValidChange') configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  factorSchema: any = {};

  constructor(private platformService: PlatformService) { }

  ngOnInit() {
    this.platformService.factorSchema(this.factor.type).subscribe(data => {
      this.factorSchema = data;
      // set the grant_type value
      if (this.factorSchema.properties.factorType) {
        this.factor.factorType = this.factorSchema.properties.factorType.default;
      }
    });
  }

  enableFactorCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.factor.configuration = configurationWrapper.configuration;
  }
}
