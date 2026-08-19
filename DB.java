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

    private static final String DB_NAME = "skmedkart.db";
    private static final int DB_VERSION = 3;

    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;

        public Medicine(long id, String name, double price, int stock, String expiry) {
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
    }

    public DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS customers(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL,phone TEXT,notes TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS medicines(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL,price REAL NOT NULL," +
                "stock INTEGER NOT NULL,expiry TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS bills(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "customer TEXT,phone TEXT,total REAL NOT NULL," +
                "created TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS bill_items(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bill_id INTEGER NOT NULL," +
                "medicine_id INTEGER NOT NULL," +
                "medicine_name TEXT NOT NULL," +
                "price REAL NOT NULL," +
                "qty INTEGER NOT NULL," +
                "amount REAL NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep existing customer, medicine and sales data.
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS bill_items(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "bill_id INTEGER NOT NULL," +
                    "medicine_id INTEGER NOT NULL," +
                    "medicine_name TEXT NOT NULL," +
                    "price REAL NOT NULL," +
                    "qty INTEGER NOT NULL," +
                    "amount REAL NOT NULL)");
        }
    }

    public long addCustomer(String name, String phone, String notes) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("phone", phone);
        v.put("notes", notes);
        return getWritableDatabase().insertOrThrow("customers", null, v);
    }

    public long addMedicine(String name, double price, int stock, String expiry) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("price", price);
        v.put("stock", stock);
        v.put("expiry", expiry);
        return getWritableDatabase().insertOrThrow("medicines", null, v);
    }

    public ArrayList<Medicine> medicineList() {
        ArrayList<Medicine> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry " +
                "FROM medicines ORDER BY name COLLATE NOCASE", null);
        try {
            while (c.moveToNext()) {
                list.add(new Medicine(
                        c.getLong(0),
                        c.getString(1),
                        c.getDouble(2),
                        c.getInt(3),
                        c.getString(4)
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public Cursor customers() {
        return getReadableDatabase().rawQuery(
                "SELECT id,name,phone,notes FROM customers ORDER BY id DESC", null);
    }

    public Cursor medicines() {
        return getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry FROM medicines ORDER BY id DESC", null);
    }

    public Cursor bills() {
        return getReadableDatabase().rawQuery(
                "SELECT id,customer,phone,total,created FROM bills ORDER BY id DESC", null);
    }

    /**
     * Saves the complete bill atomically.
     *
     * IMPORTANT:
     * Stock is read using the same medicine ID that MainActivity stores
     * in CartItem. The UPDATE checks both ID and available stock and the
     * number of changed rows is verified. If anything fails, the entire
     * bill is rolled back.
     */
    public long addBillWithItems(String customer,
                                 String phone,
                                 double total,
                                 String created,
                                 ArrayList<BillItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("No medicines in bill");
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            // Combine quantities by medicine ID first.
            Map<Long, Integer> required = new HashMap<>();

            for (BillItem item : items) {
                if (item == null || item.medicineId <= 0 || item.qty <= 0) {
                    throw new IllegalStateException("Invalid bill item");
                }

                int old = required.containsKey(item.medicineId)
                        ? required.get(item.medicineId) : 0;
                required.put(item.medicineId, old + item.qty);
            }

            // Check actual database stock.
            for (Map.Entry<Long, Integer> entry : required.entrySet()) {
                Cursor c = db.rawQuery(
                        "SELECT name,stock FROM medicines WHERE id=?",
                        new String[]{String.valueOf(entry.getKey())}
                );

                try {
                    if (!c.moveToFirst()) {
                        throw new IllegalStateException(
                                "Medicine not found (ID " + entry.getKey() + ")"
                        );
                    }

                    String name = c.getString(0);
                    int stock = c.getInt(1);
                    int need = entry.getValue();

                    if (need > stock) {
                        throw new IllegalStateException(
                                "Only " + stock + " in stock for " + name
                        );
                    }
                } finally {
                    c.close();
                }
            }

            // Save bill using the existing schema: "created".
            ContentValues bill = new ContentValues();
            bill.put("customer",
                    customer == null || customer.trim().isEmpty()
                            ? "Walk-in Customer" : customer);
            bill.put("phone", phone == null ? "" : phone);
            bill.put("total", total);
            bill.put("created", created);

            long billId = db.insertOrThrow("bills", null, bill);

            // Save every line and deduct the stock.
            for (BillItem item : items) {
                double amount = item.price * item.qty;

                ContentValues line = new ContentValues();
                line.put("bill_id", billId);
                line.put("medicine_id", item.medicineId);
                line.put("medicine_name", item.name);
                line.put("price", item.price);
                line.put("qty", item.qty);
                line.put("amount", amount);

                db.insertOrThrow("bill_items", null, line);

                // ACTUAL STOCK DEDUCTION.
                // Read current stock immediately before deduction.
                Cursor stockCursor = db.rawQuery(
                        "SELECT stock FROM medicines WHERE id=?",
                        new String[]{String.valueOf(item.medicineId)}
                );

                int currentStock;
                try {
                    if (!stockCursor.moveToFirst()) {
                        throw new IllegalStateException(
                                "Medicine not found for stock deduction: " + item.name
                        );
                    }
                    currentStock = stockCursor.getInt(0);
                } finally {
                    stockCursor.close();
                }

                int newStock = currentStock - item.qty;
                if (newStock < 0) {
                    throw new IllegalStateException(
                            "Insufficient stock for " + item.name
                    );
                }

                ContentValues stockValues = new ContentValues();
                stockValues.put("stock", newStock);

                int changed = db.update(
                        "medicines",
                        stockValues,
                        "id=?",
                        new String[]{String.valueOf(item.medicineId)}
                );

                if (changed != 1) {
                    throw new IllegalStateException(
                            "Stock update failed for " + item.name
                    );
                }
            }

            db.setTransactionSuccessful();
            return billId;

        } finally {
            db.endTransaction();
        }
    }
}
