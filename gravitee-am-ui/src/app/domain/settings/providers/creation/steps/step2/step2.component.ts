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
import {Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {PlatformService} from '../../../../../../services/platform.service';
import * as _ from 'lodash';

@Component({
  selector: 'provider-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class ProviderCreationStep2Component implements OnInit, OnChanges {
  @Input('provider') provider: any;
  @Input('configurationIsValid') configurationIsValid: boolean;
  @Output('configurationIsValidChange') configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  configuration: any;
  providerSchema: any = {};
  private certificates: any[];

  constructor(private platformService: PlatformService,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.certificates = this.route.snapshot.data['certificates'];
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.provider) {
      this.platformService.identitySchema(changes.provider.currentValue.type).subscribe(data => {
        this.providerSchema = data;
        // enhance schema information
        if (this.providerSchema.properties.graviteeCertificate && this.certificates && this.certificates.length > 0) {
          this.providerSchema.properties.graviteeCertificate.enum = _.flatMap(this.certificates, 'id');
          this.providerSchema.properties.graviteeCertificate['x-schema-form'] = { 'type' : 'select' };
          this.providerSchema.properties.graviteeCertificate['x-schema-form'].titleMap = this.certificates.reduce(function(map, obj) {
            map[obj.id] = obj.name;
            return map;
          }, {});
        }
      });
    }
  }

  enableProviderCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.provider.configuration = configurationWrapper.configuration;
  }
}
