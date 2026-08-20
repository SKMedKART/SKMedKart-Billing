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
    private static final int DB_VERSION = 3;

    public static class Medicine {
        public long id; public String name; public double price; public int stock; public String expiry;
        Medicine(long id, String name, double price, int stock, String expiry) {
            this.id=id; this.name=name; this.price=price; this.stock=stock; this.expiry=expiry;
        }
    }

    public static class BillItem {
        public long medicineId; public String name; public double price; public int qty;
        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId=medicineId; this.name=name; this.price=price; this.qty=qty;
        }
        public double amount() { return price * qty; }
    }

    public static class Bill {
        public long id; public String billNo; public String customer; public String phone;
        public double subtotal, discount, gst, total; public String created;
        Bill(long id, String billNo, String customer, String phone, double subtotal,
             double discount, double gst, double total, String created) {
            this.id=id; this.billNo=billNo; this.customer=customer; this.phone=phone;
            this.subtotal=subtotal; this.discount=discount; this.gst=gst; this.total=total; this.created=created;
        }
    }

    public DB(Context context) { super(context, "skmedkart.db", null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,notes TEXT)");
        db.execSQL("CREATE TABLE medicines(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price REAL NOT NULL,stock INTEGER NOT NULL,expiry TEXT)");
        db.execSQL("CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_no TEXT,customer TEXT,phone TEXT,subtotal REAL NOT NULL,discount REAL NOT NULL DEFAULT 0,gst REAL NOT NULL DEFAULT 0,total REAL NOT NULL,created TEXT NOT NULL)");
        db.execSQL("CREATE TABLE bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,medicine_id INTEGER NOT NULL,medicine_name TEXT NOT NULL,price REAL NOT NULL,qty INTEGER NOT NULL,amount REAL NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,medicine_id INTEGER NOT NULL,medicine_name TEXT NOT NULL,price REAL NOT NULL,qty INTEGER NOT NULL,amount REAL NOT NULL)");
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, "bills", "bill_no", "TEXT");
            addColumnIfMissing(db, "bills", "subtotal", "REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "bills", "discount", "REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "bills", "gst", "REAL NOT NULL DEFAULT 0");
            Cursor c = db.rawQuery("SELECT id,total FROM bills WHERE subtotal=0", null);
            while (c.moveToNext()) {
                ContentValues v = new ContentValues();
                v.put("subtotal", c.getDouble(1));
                v.put("bill_no", "OLD-" + c.getLong(0));
                db.update("bills", v, "id=?", new String[]{String.valueOf(c.getLong(0))});
            }
            c.close();
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        try { db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); }
        catch (Exception ignored) { }
    }

    public long addCustomer(String name, String phone, String notes) {
        ContentValues v=new ContentValues(); v.put("name",name); v.put("phone",phone); v.put("notes",notes);
        return getWritableDatabase().insert("customers",null,v);
    }

    public long addMedicine(String name,double price,int stock,String expiry) {
        ContentValues v=new ContentValues(); v.put("name",name); v.put("price",price); v.put("stock",stock); v.put("expiry",expiry);
        return getWritableDatabase().insert("medicines",null,v);
    }

    public long addBillWithItems(String customer,String phone,double subtotal,double discount,double gst,
                                 double total,String created,ArrayList<BillItem> items) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Map<Long,Integer> required=new HashMap<>();
            for(BillItem item:items){
                if(item.qty<=0) throw new IllegalStateException("Invalid quantity for " + item.name);
                Integer old=required.get(item.medicineId); required.put(item.medicineId,(old==null?0:old)+item.qty);
            }
            for(Map.Entry<Long,Integer> e:required.entrySet()){
                Cursor c=db.rawQuery("SELECT name,stock FROM medicines WHERE id=?",new String[]{String.valueOf(e.getKey())});
                if(!c.moveToFirst()){c.close();throw new IllegalStateException("Medicine no longer exists");}
                String name=c.getString(0); int stock=c.getInt(1); c.close();
                if(e.getValue()>stock) throw new IllegalStateException("Insufficient stock: " + name + " (available " + stock + ")");
            }
            ContentValues bill=new ContentValues();
            bill.put("bill_no", "PENDING"); bill.put("customer",customer); bill.put("phone",phone);
            bill.put("subtotal",subtotal); bill.put("discount",discount); bill.put("gst",gst); bill.put("total",total); bill.put("created",created);
            long billId=db.insertOrThrow("bills",null,bill);
            String datePart=created.length()>=10 ? created.substring(0,10).replace("-","") : "00000000";
            ContentValues no=new ContentValues(); no.put("bill_no",String.format(java.util.Locale.US,"SKM-%s-%04d",datePart,billId));
            db.update("bills",no,"id=?",new String[]{String.valueOf(billId)});
            for(BillItem item:items){
                ContentValues line=new ContentValues(); line.put("bill_id",billId); line.put("medicine_id",item.medicineId);
                line.put("medicine_name",item.name); line.put("price",item.price); line.put("qty",item.qty); line.put("amount",item.amount());
                db.insertOrThrow("bill_items",null,line);
                db.execSQL("UPDATE medicines SET stock=stock-? WHERE id=?",new Object[]{item.qty,item.medicineId});
            }
            db.setTransactionSuccessful(); return billId;
        } finally { db.endTransaction(); }
    }

    public ArrayList<Medicine> medicineList(){
        ArrayList<Medicine> list=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT id,name,price,stock,expiry FROM medicines ORDER BY name COLLATE NOCASE",null);
        while(c.moveToNext()) list.add(new Medicine(c.getLong(0),c.getString(1),c.getDouble(2),c.getInt(3),c.getString(4))); c.close(); return list;
    }
    public Cursor customers(){return getReadableDatabase().rawQuery("SELECT id,name,phone,notes FROM customers ORDER BY id DESC",null);}
    public Cursor medicines(){return getReadableDatabase().rawQuery("SELECT id,name,price,stock,expiry FROM medicines ORDER BY id DESC",null);}
    public Cursor bills(){return getReadableDatabase().rawQuery("SELECT id,bill_no,customer,phone,subtotal,discount,gst,total,created FROM bills ORDER BY id DESC",null);}

    public Bill getBill(long id){
        Cursor c=getReadableDatabase().rawQuery("SELECT id,bill_no,customer,phone,subtotal,discount,gst,total,created FROM bills WHERE id=?",new String[]{String.valueOf(id)});
        Bill b=null; if(c.moveToFirst()) b=new Bill(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getDouble(4),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getString(8)); c.close(); return b;
    }
    public ArrayList<BillItem> billItems(long billId){
        ArrayList<BillItem> list=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT medicine_id,medicine_name,price,qty FROM bill_items WHERE bill_id=? ORDER BY id",new String[]{String.valueOf(billId)});
        while(c.moveToNext()) list.add(new BillItem(c.getLong(0),c.getString(1),c.getDouble(2),c.getInt(3))); c.close(); return list;
    }
}
