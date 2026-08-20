package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DB extends SQLiteOpenHelper {

    // Version 3 = Bill Number + Discount + GST
    private static final int DB_VERSION = 3;

    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;

        Medicine(long id, String name, double price, int stock, String expiry) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.expiry = expiry;
        }
    }

    public static class BillItem {
        public long medicineId;
        public String name;
        public double price;
        public int qty;

        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId = medicineId;
            this.name = name;
            this.price = price;
            this.qty = qty;
        }
    public double amount() {
    return price * qty;
    }

    public DB(Context context) {
        super(context, "skmedkart.db", null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE customers(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT)"
        );

        db.execSQL(
                "CREATE TABLE medicines(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL," +
                        "expiry TEXT)"
        );

        db.execSQL(
                "CREATE TABLE bills(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_no TEXT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "subtotal REAL NOT NULL DEFAULT 0," +
                        "discount REAL NOT NULL DEFAULT 0," +
                        "gst_percent REAL NOT NULL DEFAULT 0," +
                        "gst_amount REAL NOT NULL DEFAULT 0," +
                        "total REAL NOT NULL DEFAULT 0," +
                        "created TEXT NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE bill_items(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_id INTEGER NOT NULL," +
                        "medicine_id INTEGER NOT NULL," +
                        "medicine_name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "qty INTEGER NOT NULL," +
                        "amount REAL NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // Existing Version 1 -> Version 2
        if (oldVersion < 2) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bill_items(" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "bill_id INTEGER NOT NULL," +
                            "medicine_id INTEGER NOT NULL," +
                            "medicine_name TEXT NOT NULL," +
                            "price REAL NOT NULL," +
                            "qty INTEGER NOT NULL," +
                            "amount REAL NOT NULL)"
            );
        }

        // Version 2 -> Version 3
        // IMPORTANT: Existing data is NOT deleted.
        if (oldVersion < 3) {

            addColumnIfMissing(
                    db,
                    "bills",
                    "bill_no",
                    "TEXT"
            );

            addColumnIfMissing(
                    db,
                    "bills",
                    "subtotal",
                    "REAL NOT NULL DEFAULT 0"
            );

            addColumnIfMissing(
                    db,
                    "bills",
                    "discount",
                    "REAL NOT NULL DEFAULT 0"
            );

            addColumnIfMissing(
                    db,
                    "bills",
                    "gst_percent",
                    "REAL NOT NULL DEFAULT 0"
            );

            addColumnIfMissing(
                    db,
                    "bills",
                    "gst_amount",
                    "REAL NOT NULL DEFAULT 0"
            );

            // Old total becomes subtotal and total remains unchanged.
            db.execSQL(
                    "UPDATE bills " +
                            "SET subtotal = total " +
                            "WHERE subtotal = 0"
            );

            // Existing bills receive bill numbers.
            db.execSQL(
                    "UPDATE bills " +
                            "SET bill_no = 'SK-' || id " +
                            "WHERE bill_no IS NULL OR bill_no = ''"
            );
        }
    }

    private void addColumnIfMissing(
            SQLiteDatabase db,
            String table,
            String column,
            String definition) {

        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "PRAGMA table_info(" + table + ")",
                    null
            );

            boolean exists = false;

            while (cursor.moveToNext()) {
                String existingColumn = cursor.getString(1);

                if (column.equalsIgnoreCase(existingColumn)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                db.execSQL(
                        "ALTER TABLE " + table +
                                " ADD COLUMN " + column +
                                " " + definition
                );
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public long addCustomer(
            String name,
            String phone,
            String notes) {

        ContentValues v = new ContentValues();

        v.put("name", name);
        v.put("phone", phone);
        v.put("notes", notes);

        return getWritableDatabase().insert(
                "customers",
                null,
                v
        );
    }

    public long addMedicine(
            String name,
            double price,
            int stock,
            String expiry) {

        ContentValues v = new ContentValues();

        v.put("name", name);
        v.put("price", price);
        v.put("stock", stock);
        v.put("expiry", expiry);

        return getWritableDatabase().insert(
                "medicines",
                null,
                v
        );
    }

    /*
     * OLD METHOD
     *
     * Kept for compatibility with existing MainActivity.java.
     * No discount/GST = old billing behaviour.
     */
    public long addBillWithItems(
            String customer,
            String phone,
            double total,
            String created,
            ArrayList<BillItem> items) {

        return addBillWithItems(
                customer,
                phone,
                total,
                0.0,
                0.0,
                0.0,
                total,
                created,
                items
        );
    }

    /*
     * VERSION 3 BILL METHOD
     *
     * subtotal    = medicine total before discount
     * discount    = discount amount
     * gstPercent  = GST percentage
     * gstAmount   = calculated GST amount
     * total       = final payable amount
     */
    public long addBillWithItems(
            String customer,
            String phone,
            double subtotal,
            double discount,
            double gstPercent,
            double gstAmount,
            double total,
            String created,
            ArrayList<BillItem> items) {

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            if (items == null || items.isEmpty()) {
                throw new IllegalStateException(
                        "Bill must contain at least one medicine"
                );
            }

            if (discount < 0) {
                throw new IllegalStateException(
                        "Invalid discount"
                );
            }

            if (gstPercent < 0) {
                throw new IllegalStateException(
                        "Invalid GST percentage"
                );
            }

            if (gstAmount < 0) {
                throw new IllegalStateException(
                        "Invalid GST amount"
                );
            }

            /*
             * Calculate required stock for each medicine.
             */
            Map<Long, Integer> required = new HashMap<>();

            for (BillItem item : items) {

                if (item.qty <= 0) {
                    throw new IllegalStateException(
                            "Invalid quantity for " + item.name
                    );
                }

                Integer old = required.get(item.medicineId);

                required.put(
                        item.medicineId,
                        (old == null ? 0 : old) + item.qty
                );
            }

            /*
             * Check stock BEFORE saving anything.
             */
            for (Map.Entry<Long, Integer> entry :
                    required.entrySet()) {

                Cursor c = db.rawQuery(
                        "SELECT name,stock FROM medicines WHERE id=?",
                        new String[]{
                                String.valueOf(entry.getKey())
                        }
                );

                if (!c.moveToFirst()) {
                    c.close();

                    throw new IllegalStateException(
                            "Medicine no longer exists"
                    );
                }

                String name = c.getString(0);
                int stock = c.getInt(1);

                c.close();

                if (entry.getValue() > stock) {

                    throw new IllegalStateException(
                            "Insufficient stock: " +
                                    name +
                                    " (available " +
                                    stock +
                                    ")"
                    );
                }
            }

            /*
             * Save bill.
             *
             * bill_no is generated AFTER getting the ID.
             */
            ContentValues bill = new ContentValues();

            bill.put("customer", customer);
            bill.put("phone", phone);
            bill.put("subtotal", subtotal);
            bill.put("discount", discount);
            bill.put("gst_percent", gstPercent);
            bill.put("gst_amount", gstAmount);
            bill.put("total", total);
            bill.put("created", created);

            long billId = db.insertOrThrow(
                    "bills",
                    null,
                    bill
            );

            /*
             * Automatic Bill Number.
             *
             * Example:
             * SK-1
             * SK-2
             * SK-3
             */
            String billNumber = "SK-" + billId;

            ContentValues billNumberValues =
                    new ContentValues();

            billNumberValues.put(
                    "bill_no",
                    billNumber
            );

            db.update(
                    "bills",
                    billNumberValues,
                    "id=?",
                    new String[]{
                            String.valueOf(billId)
                    }
            );

            /*
             * Save bill items and deduct stock.
             */
            for (BillItem item : items) {

                double amount =
                        item.price * item.qty;

                ContentValues line =
                        new ContentValues();

                line.put(
                        "bill_id",
                        billId
                );

                line.put(
                        "medicine_id",
                        item.medicineId
                );

                line.put(
                        "medicine_name",
                        item.name
                );

                line.put(
                        "price",
                        item.price
                );

                line.put(
                        "qty",
                        item.qty
                );

                line.put(
                        "amount",
                        amount
                );

                db.insertOrThrow(
                        "bill_items",
                        null,
                        line
                );

                /*
                 * AUTOMATIC STOCK DEDUCTION
                 */
                db.execSQL(
                        "UPDATE medicines " +
                                "SET stock = stock - ? " +
                                "WHERE id = ?",
                        new Object[]{
                                item.qty,
                                item.medicineId
                        }
                );
            }

            db.setTransactionSuccessful();

            return billId;

        } finally {

            db.endTransaction();
        }
    }

    public ArrayList<Medicine> medicineList() {

        ArrayList<Medicine> list =
                new ArrayList<>();

        Cursor c =
                getReadableDatabase().rawQuery(
                        "SELECT id,name,price,stock,expiry " +
                                "FROM medicines " +
                                "ORDER BY name COLLATE NOCASE",
                        null
                );

        while (c.moveToNext()) {

            list.add(
                    new Medicine(
                            c.getLong(0),
                            c.getString(1),
                            c.getDouble(2),
                            c.getInt(3),
                            c.getString(4)
                    )
            );
        }

        c.close();

        return list;
    }

    public Cursor customers() {

        return getReadableDatabase().rawQuery(
                "SELECT id,name,phone,notes " +
                        "FROM customers " +
                        "ORDER BY id DESC",
                null
        );
    }

    public Cursor medicines() {

        return getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry " +
                        "FROM medicines " +
                        "ORDER BY id DESC",
                null
        );
    }

    /*
     * Bills list now includes:
     * Bill Number
     * Customer
     * Phone
     * Subtotal
     * Discount
     * GST %
     * GST Amount
     * Final Total
     * Date
     */
    public Cursor bills() {

        return getReadableDatabase().rawQuery(
                "SELECT id,bill_no,customer,phone," +
                        "subtotal,discount,gst_percent," +
                        "gst_amount,total,created " +
                        "FROM bills " +
                        "ORDER BY id DESC",
                null
        );
    }

    /*
     * Get a single bill.
     */
    public Cursor getBill(long billId) {

        return getReadableDatabase().rawQuery(
                "SELECT id,bill_no,customer,phone," +
                        "subtotal,discount,gst_percent," +
                        "gst_amount,total,created " +
                        "FROM bills WHERE id=?",
                new String[]{
                        String.valueOf(billId)
                }
        );
    }

    /*
     * Get items belonging to a bill.
     */
    public Cursor getBillItems(long billId) {

        return getReadableDatabase().rawQuery(
                "SELECT id,bill_id,medicine_id," +
                        "medicine_name,price,qty,amount " +
                        "FROM bill_items " +
                        "WHERE bill_id=? " +
                        "ORDER BY id ASC",
                new String[]{
                        String.valueOf(billId)
                }
        );
    }
        // Compatibility method for existing MainActivity.java
    public ArrayList<BillItem> billItems(long billId) {

        ArrayList<BillItem> list = new ArrayList<>();

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT medicine_id,medicine_name,price,qty " +
                        "FROM bill_items " +
                        "WHERE bill_id=? " +
                        "ORDER BY id ASC",
                new String[]{
                        String.valueOf(billId)
                }
        );

        while (c.moveToNext()) {

            list.add(
                    new BillItem(
                            c.getLong(0),
                            c.getString(1),
                            c.getDouble(2),
                            c.getInt(3)
                    )
            );
        }

        c.close();

        return list;
    }
}
