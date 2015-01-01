package com.porosh.googleoauthapiaccess;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

import com.loopj.android.http.*;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends Activity {
    Button select,api_call;
    String[] avail_accounts;
    private AccountManager mAccountManager;
    ListView list;
    ArrayAdapter<String> adapter;
    SharedPreferences pref;

    private String ACCESS_TOKEN;


    private String SCOPE_URLSHORTENER = "oauth2:https://www.googleapis.com/auth/urlshortener";
    private String SCOPE_CONTACTS_V3 = "oauth2:https://www.google.com/m8/feeds";


    private ArrayList<GoogleContact> dataList = new ArrayList<>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        select = (Button)findViewById(R.id.select_button);
        api_call = (Button)findViewById(R.id.apicall_button);
        avail_accounts = getAccountNames();
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,avail_accounts );
        pref = getSharedPreferences("AppPref", MODE_PRIVATE);
        select.setOnClickListener(new View.OnClickListener() {
            Dialog accountDialog;

            @Override
            public void onClick(View arg0) {

                if (avail_accounts.length != 0){
                    accountDialog = new Dialog(MainActivity.this);
                    accountDialog.setContentView(R.layout.accounts_dialog);
                    accountDialog.setTitle("Select Google Account");
                    list = (ListView)accountDialog.findViewById(R.id.list);
                    list.setAdapter(adapter);
                    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view,
                                                int position, long id) {
                            SharedPreferences.Editor edit = pref.edit();
                            //Storing Data using SharedPreferences
                            edit.putString("Email", avail_accounts[position]);
                            edit.commit();

                            ArrayList<String> passing = new ArrayList<String>();
                            passing.add(avail_accounts[position]);

                            // For Google Contacts
                            passing.add(SCOPE_CONTACTS_V3);


                            // For Googlr URL Shortener
                            //passing.add(SCOPE_URLSHORTENER);

                            new AuthenticateAndRetrieveToken().execute(passing);
                            accountDialog.cancel();
                        }
                    });
                    accountDialog.show();
                }else{
                    Toast.makeText(getApplicationContext(), "No accounts found, Add a Account and Continue.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        api_call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                //new URLShort().execute();
                getContacts();
                //makeURLShort("http://poroshkhan.wordpress.com");
            }
        });
    }
    private String[] getAccountNames() {
        mAccountManager = AccountManager.get(this);
        Account[] accounts = mAccountManager.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        String[] names = new String[accounts.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = accounts[i].name;
        }
        return names;
    }


    private class processContactData extends AsyncTask<JSONObject, Void, Void> {

        ProgressDialog pDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage("Processing Google Contacts....");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();

        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            pDialog.dismiss();
        }

        @Override
        protected Void doInBackground(JSONObject... params) {

            JSONObject jsonObj = params[0];


            try {

                JSONArray contactArray = jsonObj.getJSONArray("entry");

                for (int i=0; i<contactArray.length(); i++) {
                    JSONObject contact = contactArray.getJSONObject(i);
                    String name = contact.getString("title");

                    JSONArray emailArray = contact.getJSONArray("gd$email");
                    String email = emailArray.getJSONObject(0).getString("address");

                    GoogleContact googleContact = new GoogleContact(name,email);
                    dataList.add(googleContact);
                }


            } catch (JSONException e) {
                e.printStackTrace();
            }





            return null;
        }
    }



    private class AuthenticateAndRetrieveToken extends AsyncTask<ArrayList<String>, String, String> {
        ProgressDialog pDialog;
        String mEmail;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage("Authenticating....");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            mEmail= pref.getString("Email", "");
            pDialog.show();
        }
        @Override
        protected void onPostExecute(String token) {
            pDialog.dismiss();
            if(token != null){
                //SharedPreferences.Editor edit = pref.edit();
                //Storing Access Token using SharedPreferences
                //edit.putString("Access Token", token);
                //edit.commit();

                ACCESS_TOKEN = token;

                Log.i("Token", "Access Token retrieved:" + token);
                Toast.makeText(getApplicationContext(),"Access Token is " +token, Toast.LENGTH_SHORT).show();
                select.setText(pref.getString("Email", "")+" is Authenticated");
            }
        }
        @Override
        protected String doInBackground(ArrayList<String>... passing) {

            String token = null;
            try {

                ArrayList<String> passed = passing[0];
                String accountName = passed.get(0);
                String scope = passed.get(1);

                token = GoogleAuthUtil.getToken( MainActivity.this, accountName, scope);

            } catch (IOException transientEx) {
                // Network or server error, try later
                Log.e("IOException", transientEx.toString());
            } catch (UserRecoverableAuthException e) {
                // Recover (with e.getIntent())
                startActivityForResult(e.getIntent(), 1001);
                Log.e("AuthException", e.toString());
            } catch (GoogleAuthException authEx) {
                // The call is not ever expected to succeed
                // assuming you have already verified that
                // Google Play services is installed.
                Log.e("GoogleAuthException", authEx.toString());
            }
            return token;
        }
    };


    private void getContacts(){

        //String Token = pref.getString("Access Token", "");

        String GOOGLE_CONTACTS_API_URL = "https://www.google.com/m8/feeds/contacts/default/full?alt=json&oauth_token=" + ACCESS_TOKEN;

        Log.e("GOOGLE_CONTACTS_API_URL",GOOGLE_CONTACTS_API_URL);


        AsyncHttpClient client = new AsyncHttpClient();

        client.get(GOOGLE_CONTACTS_API_URL, new JsonHttpResponseHandler() {

            private ProgressDialog pDialog;


            @Override
            public void onStart() {
                super.onStart();

                pDialog = new ProgressDialog(MainActivity.this);
                pDialog.setMessage("Contacting Google Servers ...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();

            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                super.onSuccess(statusCode, headers, response);

                pDialog.dismiss();

                Log.e("JSONObject",response.toString());


                try {

                    String contactTitle = response.getString("title");
                    String authorName = response.getJSONObject("author").getString("name");
                    String authorEmail = response.getJSONObject("author").getString("email");

                    new processContactData().execute(response);



                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }


            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);

                pDialog.dismiss();
            }
        });


    }


    private void makeURLShort(String longURL){

        //String Token = pref.getString("Access Token", "");

        String GOOGLE_URL_SHORTENER_API_URL = "https://www.googleapis.com/urlshortener/v1/url?access_token=" + ACCESS_TOKEN;

        Log.e("GOOGLE_URL_SHORTENER_API_URL",GOOGLE_URL_SHORTENER_API_URL);

        RequestParams params = new RequestParams();
        params.put("longUrl", longURL);
        params.put("shortUrl", longURL);


        AsyncHttpClient client = new AsyncHttpClient();



        client.post(GOOGLE_URL_SHORTENER_API_URL, params, new JsonHttpResponseHandler() {

            private ProgressDialog pDialog;


            @Override
            public void onStart() {
                super.onStart();

                pDialog = new ProgressDialog(MainActivity.this);
                pDialog.setMessage("Contacting Google Servers ...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();

            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                super.onSuccess(statusCode, headers, response);

                pDialog.dismiss();

                Log.e("JSONObject", response.toString());


                try {
                    Log.e("short URL",response.getString("id"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }


            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);

                pDialog.dismiss();

                Log.e("error " + statusCode,errorResponse.toString());

            }


            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
            }
        });


    }




}