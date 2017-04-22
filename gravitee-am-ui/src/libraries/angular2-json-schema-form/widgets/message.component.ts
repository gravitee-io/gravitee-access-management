import {
  ChangeDetectorRef, Component, Input, OnChanges, OnInit
} from '@angular/core';
import { FormGroup } from '@angular/forms';

import { JsonSchemaFormService } from '../library/json-schema-form.service';

@Component({
  selector: 'message-widget',
  template: `
    <span *ngIf="message"
      [class]="options?.labelHtmlClass"
      [innerHTML]="message"></span>`,
})
export class MessageComponent implements OnInit {
  private options: any;
  message: string = null;
  @Input() formID: number;
  @Input() layoutNode: any;
  @Input() layoutIndex: number[];
  @Input() dataIndex: number[];

  constructor(
    private jsf: JsonSchemaFormService
  ) { }

  ngOnInit() {
    this.options = this.layoutNode.options;
    this.message = this.options.help || this.options.helpvalue ||
      this.options.msg || this.options.message;
  }
}
