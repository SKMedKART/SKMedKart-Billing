# SKMedKART Ready Project

This project is a clean Java Android version of the SKMedKART app based on the
MainActivity.java and DB.java supplied in the conversation.

Included:
- New Bill
- Medicine Stock
- Customer records
- Tomorrow reminders
- Sales History
- Atomic bill saving
- Automatic medicine-stock deduction

IMPORTANT STOCK FIX:
When a bill is saved, the app:
1. Checks the real database stock.
2. Saves the bill.
3. Saves each bill item.
4. Reads the current medicine stock.
5. Subtracts the sold quantity.
6. Verifies the update result.
7. Commits the whole transaction.

Database schema keeps the existing column names used by the supplied project:
bills.created and bill_items.medicine_name / amount.

TEST:
Medicine Stock -> add Dolo 650 with stock 10.
New Bill -> Dolo 650 -> quantity 1 -> SAVE BILL.
Open Medicine Stock.
Expected: Stock 9.

For an existing installation, install the new build over the old app so the
existing database can be retained. If the Android IDE refuses an update because
the signing key differs, uninstalling will erase local database data.
