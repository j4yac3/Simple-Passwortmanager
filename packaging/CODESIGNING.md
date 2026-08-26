# Code Signing / SmartScreen - Options & How-To

## Why does SmartScreen warn?

Windows SmartScreen warns about downloaded programs whose publisher has
no established reputation. A warning for unsigned software cannot be
technically switched off by the developer - it is a Windows feature.

## Options

### Option 1: Leave it unsigned (current state, free)
Users see: "Windows protected your PC" -> More info -> Run anyway.
The one-click installer and this documentation guide users through it.
SmartScreen reputation for a specific binary also improves over time
once enough users install it.

### Option 2: Code signing certificate (removes warnings, costs money)
Prices (approx., per year):
  - OV (Organization Validation):   ~100 - 200 USD
    Providers: Sectigo, DigiCert, GlobalSign, Certum (cheapest)
  - EV (Extended Validation):       ~250 - 500 USD
    Instant SmartScreen reputation.
  Since June 2023 all certificates require a hardware token or HSM
  (USB stick) - the key cannot be a plain file anymore.

Steps once you own a certificate:
1. Install the vendor's token software and plug in the token.
2. Uncomment the SignTool section in `setup.iss` and configure:
     [Setup]
     SignTool=signtool
   and in Inno Setup's .iss preferences file (Tools -> Configure
   Sign Tools...) add a tool named "signtool":
     signtool.exe sign /fd SHA256 /tr http://timestamp.digicert.com
       /td SHA256 /a $p
3. For the MSI (jpackage): rebuild with
     jpackage ... --win-sign-tool? (not needed) - sign AFTER build:
     signtool sign /fd SHA256 /tr <timestamp-url> /td SHA256 /a
       "JM Passwortmanager-1.1.0.msi"
   and also sign "JM Passwortmanager.exe" inside the app image before
   packaging for the best result.
4. Rebuild and publish.

### Option 3: Azure Trusted Signing (Microsoft, ~10 USD/month)
Signs via cloud API, gives instant SmartScreen acceptance.
Requires an Azure account and identity validation.

## Summary
Without a purchased certificate the SmartScreen warning stays - this is
true for EVERY unsigned program, not a bug in this installer.
