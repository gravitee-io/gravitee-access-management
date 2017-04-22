import { Component, Input, OnInit } from '@angular/core';
import { AbstractControl } from '@angular/forms';

import { JsonSchemaFormService } from '../library/json-schema-form.service';
import { getControl, inArray, isDefined } from '../library/utilities/index';

@Component({
  selector: 'number-widget',
  template: `
    <div
      [class]="options?.htmlClass">
      <label *ngIf="options?.title"
        [attr.for]="'control' + layoutNode?._id"
        [class]="options?.labelHtmlClass"
        [class.sr-only]="options?.notitle"
        [innerHTML]="options?.title"></label>
      <input
        [attr.aria-describedby]="'control' + layoutNode?._id + 'Status'"
        [attr.max]="options?.maximum"
        [attr.min]="options?.minimum"
        [attr.placeholder]="options?.placeholder"
        [attr.required]="options?.required"
        [attr.readonly]="options?.readonly ? 'readonly' : null"
        [attr.step]="options?.multipleOf || options?.step || 'any'"
        [class]="options?.fieldHtmlClass"
        [disabled]="controlDisabled"
        [id]="'control' + layoutNode?._id"
        [name]="controlName"
        [readonly]="options?.readonly ? 'readonly' : null"
        [title]="lastValidNumber"
        [type]="layoutNode?.type === 'range' ? 'range' : 'number'"
        [value]="controlValue"
        (input)="updateValue($event)"
        (keydown)="validateInput($event)"
        (keyup)="validateNumber($event)">
        {{layoutNode?.type === 'range' ? controlValue : ''}}
    </div>`,
})
export class NumberComponent implements OnInit {
  private formControl: AbstractControl;
  controlName: string;
  controlValue: any;
  controlDisabled: boolean = false;
  private boundControl: boolean = false;
  options: any;
  private allowNegative: boolean = true;
  private allowDecimal: boolean = true;
  private allowExponents: boolean = false;
  lastValidNumber: string = '';
  @Input() formID: number;
  @Input() layoutNode: any;
  @Input() layoutIndex: number[];
  @Input() dataIndex: number[];

  constructor(
    private jsf: JsonSchemaFormService
  ) { }

  ngOnInit() {
    this.options = this.layoutNode.options;
    this.jsf.initializeControl(this);
    if (this.layoutNode.dataType === 'integer') { this.allowDecimal = false; }
  }

  updateValue(event) {
    this.jsf.updateValue(this, event.target.value);
  }

  validateInput(event) {
    const val = event.target.value;
    if (/^Digit\d$/.test(event.code)) { return true; }
    if (/^Numpad\d$/.test(event.code)) { return true; }
    if (/^Arrow/.test(event.code)) { return true; }
    if (inArray(event.code, ['Backspace', 'Delete', 'Enter', 'Escape',
      'NumpadEnter', 'PrintScreen', 'Tab'])) { return true; }
    if (event.ctrlKey || event.altKey || event.metaKey) { return true; }
    if (this.allowDecimal && event.key === '.' && val.indexOf('.') === -1) { return true; }
    if (this.allowExponents) {
      const hasExponent = /e/i.test(val);
      if (/^e$/i.test(event.key) && !hasExponent && val) { return true; }
      if (event.key === '-') {
        const minusCount = (val.match(/\-/g) || []).length;
        if ((this.allowNegative || hasExponent) && !minusCount) { return true; }
        if (this.allowNegative && hasExponent && minusCount === 1) { return true; }
      }
    } else if (this.allowNegative && event.key === '-' && val.indexOf('-') === -1) {
      return true;
    }
    // TODO: Display feedback for rejected keystroke,
    // and clear feedback on next valid keystroke
    return false;
  }

  validateNumber(event) {
    // TODO: This only works for input type=text - make it work for type=number
    const val = event.target.value;
    if (!isNaN(val) || val === '' || val === '.' || val === '-' || val === '-.' ||
      (val.length > 1 && val.slice(-1).toLowerCase() === 'e') ||
      (val.length > 2 && val.slice(-2).toLowerCase() === 'e-')
    ) {
      this.lastValidNumber = val;
    } else {
      // TODO: Display feedback for rejected key
      this.jsf.getControl(this).setValue(this.lastValidNumber);
    }
  }
}
