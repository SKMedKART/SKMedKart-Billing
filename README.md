# SKMedKART V2.0

Sri Krishna Medicals pharmacy billing app.

## V2.0 features
- Invoice number
- Discount and GST %
- Multiple medicines per bill
- Automatic stock deduction on save
- WhatsApp bill sharing
- Android Print / Save as PDF
- Low-stock warning (<=10)
- Expiry list
- Notification alerts
- Customer + phone + refill reminder
- Today and monthly sales reports
- SQLite database migration from V2 to V3 without deleting existing rows

## Build on GitHub
Open **Actions** → **Build SKMedKART V2.0** → **Run workflow**. Download the APK from the workflow artifact.

## Important update/signing note
The package name remains `com.skmedkart.app` and versionCode is 2. To install as an update without uninstalling the old APK, the new APK must be signed with the **same signing key** as the installed APK. A different GitHub runner debug key can cause Android's “package conflicts with an existing package” error. Keep the same release keystore/signing configuration for future updates.
