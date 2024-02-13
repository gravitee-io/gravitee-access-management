export class MfaIconsResolver {
  factorTypes: any = {
    OTP: 'TOTP',
    SMS: 'SMS',
    EMAIL: 'EMAIL',
    CALL: 'CALL',
    HTTP: 'HTTP',
    RECOVERY_CODE: 'Recovery Code',
    FIDO2: 'FIDO2',
  };

  factorIcons: any = {
    OTP: 'mobile_friendly',
    SMS: 'sms',
    EMAIL: 'email',
    CALL: 'call',
    HTTP: 'http',
    RECOVERY_CODE: 'autorenew',
    FIDO2: 'fingerprint',
  };

  public getFactorTypeIcon(type: any): string {
    const factorType = type.toUpperCase();
    if (this.factorIcons[factorType]) {
      return this.factorIcons[factorType];
    }
    return 'donut_large';
  }

  public displayFactorType(type: any): string {
    const factorType = type.toUpperCase();
    if (this.factorTypes[factorType]) {
      return this.factorTypes[factorType];
    }
    return 'Custom';
  }
}
