package unice.com.smsanalysis;
import android.content.ContentResolver;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.JsonReader;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.microsoft.windowsazure.mobileservices.*;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.TableOperationCallback;

public class MainActivity extends Activity {
    TextView textView;
    public String postContent;
    public int numberSmsToRead = 100;
    public Hashtable<Integer, ArrayList<String>> matrice = new Hashtable<Integer, ArrayList<String>>();
    private MobileServiceClient mClient;
    public class SmsTable {
        public String id;
        public String Text;
    }

    /*
    *   Private class HttpAsyncTask to do network things in background
    *   and set the content of the view.
    */
    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            return testRestHttp(urls);
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getBaseContext(), "Data received!", Toast.LENGTH_LONG).show();
            textView.setText(textView.getText() + "\n\nRestHttp tests :\n" + result);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.SMS);
        // call AsynTask to perform network operation on separate thread
        new HttpAsyncTask().execute("https://www.e-meta.fr/testjson.php", "https://www.e-meta.fr/test.json");
        getSMSDetails();
        // attempting to connect to azure mobile service

        try {
            mClient = new MobileServiceClient("https://smsanalysisapp.azurewebsites.net", this);
            SmsTable item = new SmsTable();
            item.id = "3ea8aebb-27ab-44e4-88c7-0af7dd3dbfa5";
            item.Text = postContent;
            mClient.getTable(SmsTable.class).update(item, new TableOperationCallback<SmsTable>() {
                public void onCompleted(SmsTable entity, Exception exception, ServiceFilterResponse response) {
                    if (exception == null) {
                        // Insert succeeded
                        Log.d("update", "success");
                    } else {
                        // Insert failed
                        Log.d("update", "fail");
                    }
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    // Get details from SMS
    private void getSMSDetails() {
        // Sms HashTable
        Sms sms = new Sms();
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("Sms Analysis and creating matrix :");
        Uri uri = Uri.parse("content://sms");
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor.moveToFirst()) {
            // cursor.getCount();
            for (int i = 0; i < numberSmsToRead; i++) {
                String body = cursor.getString(cursor.getColumnIndexOrThrow("body")).toString();
                int nbrCaracters = body.length();
                String number = cursor.getString(cursor.getColumnIndexOrThrow("address")).toString();
                String name = UserManagement.getContactName(this,number).toString();
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date")).toString();
                Date smsDayTime = new Date(Long.valueOf(date));
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type")).toString();
                int dayWeek = DateControl.dayOfWeek(DateControl.getDayOfWeek(smsDayTime));
                int dayWeekend = DateControl.dayOfWeekend(DateControl.getDayOfWeek(smsDayTime));
                String typeOfSMS = null;
                // Calculate hour of day using calendar instance
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(date));
                int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
                // Switch for type of SMS
                switch (Integer.parseInt(type)) {
                    case 1:
                        typeOfSMS = "INBOX";
                        break;
                    case 2:
                        typeOfSMS = "SENT";
                        break;
                    case 3:
                        typeOfSMS = "DRAFT";
                        break;
                }
                //stringBuffer.append("\nPhone Number:--- " + number +" \nMessage Type:--- " + typeOfSMS +" \nMessage Date:--- " + smsDayTime);
                // stringBuffer.append("\n----------------------------------");
                cursor.moveToNext();
                // Add to matrix
                ArrayList<String> contenu = new ArrayList<String>();
                contenu.add(name);
                contenu.add(number);
                contenu.add(date);
                contenu.add(type);
                // if day of week
                contenu.add(Integer.toString(dayWeek));
                // if day of weekend
                contenu.add(Integer.toString(dayWeekend));
                // nbr caracters
                contenu.add(Integer.toString(nbrCaracters));
                // hour 0h - 5h
                contenu.add(Integer.toString(DateControl.isNight(hourOfDay)));
                // hour 6h - 10h
                contenu.add(Integer.toString(DateControl.isMorning(hourOfDay)));
                // hour 11h - 14h
                contenu.add(Integer.toString(DateControl.isMidday(hourOfDay)));
                // hour 15h - 18h
                contenu.add(Integer.toString(DateControl.isAfternoon(hourOfDay)));
                // hour 19h - 23h
                contenu.add(Integer.toString(DateControl.isEvening(hourOfDay)));
                matrice.put(i,contenu);

                sms.addSmsToUser(name, Long.parseLong(date), nbrCaracters);

            }
            stringBuffer.append("\n Affichage de la matrice :\n" + matrice.toString());
            textView.setText(stringBuffer);
            postContent = matrice.toString();
            Log.i("Matrice", matrice.toString());
            Log.i("sms michael", sms.getSmsfromUser("Michael").toString());
        }
        cursor.close();
    }


    // Test our class Rest
    public String testRestHttp(String... urls) {
        // url[0] = url to test send
        // url[1] = url to test receive
        String urlSend = urls[0];
        String urlReceive = urls[1];

        // REST HTTP SENDER TO TEST SENDING DATA
        RestHttp sender = new RestHttp(urlSend);
        int result = sender.sendPostData("ok");

        // REST HTTP RECEIVER TO TEST RECEIVED DATA
        RestHttp receiver = new RestHttp(urlReceive);
        JsonReader jsonReader = receiver.getData();
        // If the jsonReader is not null
        if(jsonReader != null) {
            try {
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals("test")) {
                        String text = jsonReader.nextString();
                        return text;
                    } else {
                        jsonReader.skipValue();
                    }
                }
                jsonReader.endObject();

            } catch (java.io.IOException e) {
                Log.e("error", "io error");
            }
        } else {
            Log.e("ResultReceive", "error");
        }
        return "false";
    }
}