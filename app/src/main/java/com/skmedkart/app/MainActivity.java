package com.skmedkart.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.app.Notification;
import android.print.PageRange;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private DB db; private LinearLayout box; private final int PAD=24; private final ArrayList<CartItem> cart=new ArrayList<>();
    private static class CartItem { long id; String name; double price; int qty; CartItem(long i,String n,double p,int q){id=i;name=n;price=p;qty=q;} double amount(){return price*qty;} }

    @Override public void onCreate(Bundle s){super.onCreate(s); db=new DB(this); requestNotificationPermission(); home(); notifyStockAlerts();}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(PAD,10,PAD,10);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setPadding(PAD,10,PAD,10);box.addView(e);return e;}
    private void page(String title){box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(10,10,10,10);android.widget.ScrollView sc=new android.widget.ScrollView(this);sc.addView(box);setContentView(sc);TextView h=text(title,25);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(h);}

    private void home(){cart.clear();page("🏪 Sri Krishna Medicals");box.addView(text("SKMedKART • Pharmacy Billing & Reminder",17));
        Button b=button("🧾 NEW BILL");box.addView(b);b.setOnClickListener(v->bill());
        b=button("👤 CUSTOMERS & REMINDERS");box.addView(b);b.setOnClickListener(v->customers());
        b=button("💊 MEDICINE STOCK");box.addView(b);b.setOnClickListener(v->medicines());
        b=button("📊 SALES HISTORY & REPORTS");box.addView(b);b.setOnClickListener(v->sales());
    }

    private void bill(){page("🧾 New Bill");EditText customer=input("Customer name");EditText phone=input("Mobile number");phone.setInputType(InputType.TYPE_CLASS_PHONE);
        ArrayList<DB.Medicine> meds=db.medicineList();box.addView(text("Select medicine and quantity",17));
        ArrayList<String> labels=new ArrayList<>();for(DB.Medicine m:meds)labels.add(m.name+"  ₹"+money(m.price)+"  (Stock: "+m.stock+")");
        Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));box.addView(sp);
        EditText qty=input("Quantity");qty.setInputType(InputType.TYPE_CLASS_NUMBER);Button add=button("➕ ADD ITEM");box.addView(add);
        LinearLayout cartBox=new LinearLayout(this);cartBox.setOrientation(LinearLayout.VERTICAL);box.addView(cartBox);TextView subText=text("Subtotal: ₹0.00",19);box.addView(subText);
        EditText discount=input("Discount ₹ (optional)");discount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText gst=input("GST % (optional)");gst.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView totalText=text("Grand Total: ₹0.00",21);totalText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(totalText);
        add.setOnClickListener(v->{if(meds.isEmpty()){toast("Add medicines to Stock first");return;}int p=sp.getSelectedItemPosition();if(p<0||p>=meds.size())return;int q=parseInt(qty.getText().toString());if(q<=0){toast("Enter a valid quantity");return;}DB.Medicine m=meds.get(p);int already=0;for(CartItem i:cart)if(i.id==m.id)already+=i.qty;if(already+q>m.stock){toast("Only "+(m.stock-already)+" in stock");return;}cart.add(new CartItem(m.id,m.name,m.price,q));qty.setText("");renderCart(cartBox,subText,totalText,discount,gst);});
        discount.setOnFocusChangeListener((v,has)->{if(!has)renderCart(cartBox,subText,totalText,discount,gst);});gst.setOnFocusChangeListener((v,has)->{if(!has)renderCart(cartBox,subText,totalText,discount,gst);});
        Button save=button("💾 SAVE BILL");box.addView(save);Button clear=button("CLEAR ITEMS");box.addView(clear);Button back=button("← HOME");box.addView(back);clear.setOnClickListener(v->{cart.clear();renderCart(cartBox,subText,totalText,discount,gst);});back.setOnClickListener(v->home());
        save.setOnClickListener(v->{if(cart.isEmpty()){toast("Add at least one medicine");return;}double subtotal=0;for(CartItem i:cart)subtotal+=i.amount();double dis=parseDouble(discount.getText().toString());double gp=parseDouble(gst.getText().toString());if(dis<0)dis=0;if(dis>subtotal)dis=subtotal;double taxable=subtotal-dis;double g=taxable*gp/100.0;double total=taxable+g;String name=customer.getText().toString().trim();if(name.isEmpty())name="Walk-in Customer";ArrayList<DB.BillItem> items=new ArrayList<>();for(CartItem i:cart)items.add(new DB.BillItem(i.id,i.name,i.price,i.qty));String d=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date());try{long id=db.addBillWithItems(name,phone.getText().toString().trim(),subtotal,dis,g,total,d,items);cart.clear();showBillActions(id); }catch(Exception e){toast(e.getMessage()==null?"Could not save bill":e.getMessage());}});
    }

    private void renderCart(LinearLayout c,TextView sub,TextView total,EditText dis,EditText gst){c.removeAllViews();double s=0;for(int i=0;i<cart.size();i++){CartItem x=cart.get(i);s+=x.amount();LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);TextView t=text(x.name+" × "+x.qty+"  ₹"+money(x.amount()),16);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));Button rm=button("X");row.addView(rm);final int at=i;rm.setOnClickListener(v->{cart.remove(at);renderCart(c,sub,total,dis,gst);});c.addView(row);}double d=Math.min(Math.max(parseDouble(dis.getText().toString()),0),s);double gp=Math.max(parseDouble(gst.getText().toString()),0);double g=(s-d)*gp/100.0;sub.setText("Subtotal: ₹"+money(s));total.setText("Grand Total: ₹"+money(s-d+g));}

    private void showBillActions(long id){DB.Bill b=db.getBill(id);page("✅ Bill Saved");box.addView(text("Bill No: "+b.billNo+"\nCustomer: "+b.customer+"\nTotal: ₹"+money(b.total),19));Button w=button("📱 SHARE ON WHATSAPP");box.addView(w);w.setOnClickListener(v->shareWhatsApp(id));Button p=button("🖨️ PRINT / SAVE PDF");box.addView(p);p.setOnClickListener(v->printBill(id));Button n=button("🧾 NEW BILL");box.addView(n);n.setOnClickListener(v->bill());Button h=button("← HOME");box.addView(h);h.setOnClickListener(v->home());}

    private void customers(){page("👤 Customers & Reminders");EditText n=input("Customer name");EditText p=input("Mobile number");p.setInputType(InputType.TYPE_CLASS_PHONE);EditText notes=input("Reminder note");Button add=button("ADD CUSTOMER");box.addView(add);add.setOnClickListener(v->{if(n.getText().toString().trim().isEmpty()){toast("Enter customer name");return;}db.addCustomer(n.getText().toString().trim(),p.getText().toString().trim(),notes.getText().toString().trim());customers();});Cursor c=db.customers();while(c.moveToNext()){String name=c.getString(1),phone=c.getString(2),note=c.getString(3);box.addView(text("• "+name+"  "+phone+"\n  "+note,16));Button r=button("🔔 REMIND TOMORROW — "+name);box.addView(r);r.setOnClickListener(v->setReminder(name));}c.close();Button back=button("← HOME");box.addView(back);back.setOnClickListener(v->home());}

    private void medicines(){page("💊 Medicine Stock");EditText n=input("Medicine name");EditText price=input("Selling price ₹");price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText stock=input("Stock quantity");stock.setInputType(InputType.TYPE_CLASS_NUMBER);EditText exp=input("Expiry (MM/YYYY)");Button add=button("ADD MEDICINE");box.addView(add);add.setOnClickListener(v->{try{String name=n.getText().toString().trim();double pr=Double.parseDouble(price.getText().toString().trim());int st=Integer.parseInt(stock.getText().toString().trim());if(name.isEmpty()||pr<0||st<0)throw new Exception();db.addMedicine(name,pr,st,exp.getText().toString().trim());medicines();}catch(Exception e){toast("Check medicine details");}});
        box.addView(text("⚠️ LOW STOCK LIMIT: 10",15));Cursor c=db.medicines();int low=0,expiry=0;while(c.moveToNext()){String name=c.getString(1),ex=c.getString(4);int st=c.getInt(3);if(st<=10)low++;if(isNearExpiry(ex))expiry++;String flag=st<=10?"  ⚠️ LOW STOCK":"";String exFlag=isExpiredOrNear(ex)?"  ⏰ EXPIRY":"";box.addView(text("💊 "+name+"  ₹"+money(c.getDouble(2))+"\nStock: "+st+"   Expiry: "+ex+flag+exFlag,16));}c.close();box.addView(text("Low stock items: "+low+"    Near/expired: "+expiry,17));Button back=button("← HOME");box.addView(back);back.setOnClickListener(v->home());}

    private void sales(){page("📊 Sales History & Reports");Button daily=button("📅 TODAY REPORT");box.addView(daily);daily.setOnClickListener(v->report(false));Button monthly=button("🗓️ THIS MONTH REPORT");box.addView(monthly);monthly.setOnClickListener(v->report(true));Cursor c=db.bills();double sum=0;while(c.moveToNext()){long id=c.getLong(0);String no=c.getString(1),name=c.getString(2);double total=c.getDouble(7);sum+=total;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.addView(text("🧾 "+no+"  "+name+"  ₹"+money(total)+"\n"+c.getString(8),16));LinearLayout actions=new LinearLayout(this);Button share=button("SHARE");Button print=button("PRINT/PDF");actions.addView(share,new LinearLayout.LayoutParams(0,-2,1));actions.addView(print,new LinearLayout.LayoutParams(0,-2,1));share.setOnClickListener(v->shareWhatsApp(id));print.setOnClickListener(v->printBill(id));row.addView(actions);box.addView(row);}c.close();box.addView(text("TOTAL SALES: ₹"+money(sum),21));Button back=button("← HOME");box.addView(back);back.setOnClickListener(v->home());}

    private void report(boolean month){Cursor c=db.bills();double total=0;int count=0;String prefix;if(month){prefix=new SimpleDateFormat("yyyy-MM",Locale.getDefault()).format(new Date());}else{prefix=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());}while(c.moveToNext()){String d=c.getString(8);if(d!=null&&d.startsWith(prefix)){total+=c.getDouble(7);count++;}}c.close();final double reportTotal=total;final int reportCount=count;page(month?"🗓️ This Month Report":"📅 Today Report");box.addView(text("Bills: "+reportCount+"\nSales: ₹"+money(reportTotal),23));Button share=button("📱 SHARE REPORT");box.addView(share);share.setOnClickListener(v->{String msg=(month?"SKMedKART MONTHLY REPORT\n":"SKMedKART TODAY REPORT\n")+"Bills: "+reportCount+"\nSales: ₹"+money(reportTotal);shareText(msg,"SKMedKART Report");});Button back=button("← SALES HISTORY");box.addView(back);back.setOnClickListener(v->sales());}

    private String billText(long id){DB.Bill b=db.getBill(id);StringBuilder s=new StringBuilder();s.append("Sri Krishna Medicals\nSKMedKART Pharmacy Billing\n");s.append("Bill No: ").append(b.billNo).append("\nDate: ").append(b.created).append("\nCustomer: ").append(b.customer).append("\nPhone: ").append(b.phone==null?"":b.phone).append("\n------------------------------\n");for(DB.BillItem i:db.billItems(id))s.append(i.name).append(" x ").append(i.qty).append(" @ ₹").append(money(i.price)).append(" = ₹").append(money(i.amount())).append("\n");s.append("------------------------------\nSubtotal: ₹").append(money(b.subtotal)).append("\nDiscount: ₹").append(money(b.discount)).append("\nGST: ₹").append(money(b.gst)).append("\nTOTAL: ₹").append(money(b.total)).append("\n\nThank you!");return s.toString();}
    private void shareWhatsApp(long id){String msg=billText(id);Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg);i.setPackage("com.whatsapp");try{startActivity(i);}catch(Exception e){shareText(msg,"SKMedKART Bill");}}
    private void shareText(String msg,String title){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg);startActivity(Intent.createChooser(i,title));}

    private void printBill(long id){String content=billText(id);PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);pm.print("SKMedKART-Bill",new TextPrintAdapter(this,content),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());}
    private static class TextPrintAdapter extends PrintDocumentAdapter {
        private final String text;
        TextPrintAdapter(Context c,String t){text=t;}
        public void onLayout(PrintAttributes oldA,PrintAttributes newA,CancellationSignal cs,LayoutResultCallback cb,Bundle extras){
            if(cs.isCanceled()){cb.onLayoutCancelled();return;}
            cb.onLayoutFinished(new PrintDocumentInfo.Builder("SKMedKART-Bill.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);
        }
        public void onWrite(PageRange[] pages,ParcelFileDescriptor dest,CancellationSignal cs,WriteResultCallback cb){
            PdfDocument pdf=new PdfDocument();
            try{
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
                Paint paint=new Paint();paint.setTextSize(14);
                float y=40;
                for(String line:text.split("\\n",-1)){
                    if(cs.isCanceled()){pdf.close();cb.onWriteCancelled();return;}
                    page.getCanvas().drawText(line,30,y,paint);y+=20;
                    if(y>810)break;
                }
                pdf.finishPage(page);pdf.writeTo(new FileOutputStream(dest.getFileDescriptor()));cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            }catch(Exception e){cb.onWriteFailed(e.getMessage());}finally{pdf.close();}
        }
    }

    private void notifyStockAlerts(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        int low=0,near=0;Cursor c=db.medicines();while(c.moveToNext()){if(c.getInt(3)<=10)low++;if(isNearExpiry(c.getString(4)))near++;}c.close();
        if(low==0&&near==0)return;
        NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("skmedkart_alerts","SKMedKART Alerts",NotificationManager.IMPORTANCE_DEFAULT));
        String msg="Low stock: "+low+"   Near/expired: "+near;
        Notification.Builder b=new Notification.Builder(this).setContentTitle("SKMedKART Stock Alert").setContentText(msg).setSmallIcon(android.R.drawable.ic_dialog_alert).setAutoCancel(true);
        if(Build.VERSION.SDK_INT>=26)b.setChannelId("skmedkart_alerts");
        nm.notify(1001,b.build());
    }

    private void setReminder(String customer){AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms()){startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));return;}Intent i=new Intent(this,ReminderReceiver.class);i.putExtra("customer",customer);i.putExtra("message","Medicine refill reminder");PendingIntent pi=PendingIntent.getBroadcast(this,(int)(System.currentTimeMillis()%100000),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+86400000L,pi);toast("Reminder set for tomorrow");}

    private boolean isNearExpiry(String ex){if(ex==null||ex.trim().isEmpty())return false;try{String[] p=ex.trim().split("/");if(p.length!=2)return false;int m=Integer.parseInt(p[0]),y=Integer.parseInt(p[1]);Calendar now=Calendar.getInstance();int ym=now.get(Calendar.YEAR)*12+now.get(Calendar.MONTH);int em=y*12+(m-1);return em-ym<=2;}catch(Exception e){return false;}}
    private boolean isExpiredOrNear(String ex){return isNearExpiry(ex);}
    private static int parseInt(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 0;}}
    private static double parseDouble(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private static String money(double v){return String.format(Locale.getDefault(),"%.2f",v);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==10&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)notifyStockAlerts();}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);}
}
