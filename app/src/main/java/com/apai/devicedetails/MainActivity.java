package com.apai.devicedetails;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;

import com.google.api.services.sheets.v4.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String BUTTON_TEXT = "Save Device Details to Drive";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {SheetsScopes.SPREADSHEETS};

    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT + "\' button to send device details to drive.");
        activityLayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Sheets API ...");

        setContentView(activityLayout);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }


    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            String setText = "No network connection available.";
            mOutputText.setText(setText);
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    String setText = "This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.";
                    mOutputText.setText(setText);
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Sheets API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.sheets.v4.Sheets mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Sheets API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                //Set sheet name
                //Can be any string, I chose to set it to the account name
                Integer sheetID;
                String sheetName = mCredential.getSelectedAccountName();
                String spreadsheetId = null;
                String range = String.format("%s!A1:U", "Sheet1");//"Sheet1!A1:B";
                if (spreadsheetId == null) {
                    spreadsheetId = createNewSpreadSheet(mCredential);
                }
                //Check if the sheet exists and clear all the values, if not, create a new one with the user's name
                sheetID = getSheetId(spreadsheetId, sheetName);
                setFormatting(spreadsheetId, sheetID);
                writeDataToSheet(spreadsheetId, range);
                return getDataFromApi(spreadsheetId, range);
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of names and majors of students in a sample spreadsheet:
         * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
         *
         * @return List of names and majors
         * @throws IOException
         */
        private List<String> getDataFromApi(String spreadsheetId, String range) throws IOException {
            List<String> results = new ArrayList<>();
            ValueRange response = this.mService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            if (values != null) {
                Log.d("log", mCredential.getSelectedAccountName());
                for (List row : values) {
                    results.add(row.get(0) + " " + row.get(1));// + " " + row.get(2));
                }
            }
            return results;
        }

        private Integer getSheetId(String spreadsheetId, String sheetName) throws IOException {
            Integer sheetID = 0;
            Spreadsheet response1 = this.mService.spreadsheets().get(spreadsheetId).setIncludeGridData(false).execute();
            List<Sheet> workSheetList = response1.getSheets();

            for (Sheet sheet : workSheetList) {
                if (sheet.getProperties().getTitle().equalsIgnoreCase(sheetName)) {
                    sheetID = sheet.getProperties().getSheetId();
                    break;
                }
            }
            return sheetID;
        }

        private void writeDataToSheet(String spreadsheetId, String range) throws IOException {
            //for the values that you want to input, create a list of object lists
            List<List<Object>> values = new ArrayList<>();

            //Where each value represents the list of objects that is to be written to a range
            //I simply want to edit a single row, so I use a single list of objects
            List<Object> data1 = new ArrayList<>();
            List<Object> data2 = new ArrayList<>();
            data1.add("Device Name");
            data1.add("Brand");
            data1.add("Display");
            data1.add("Produce");
            data1.add("VERSION.RELEASE");
            data1.add("VERSION.INCREMENTAL");
            data1.add("VERSION.SDK.NUMBER");
            data1.add("BOARD");
            data1.add("BOOTLOADER");
            data1.add("FINGERPRINT");
            data1.add("HARDWARE");
            data1.add("HOST");
            data1.add("ID");
            data1.add("MANUFACTURER");
            data1.add("MODEL");
            data1.add("SERIAL");
            data1.add("TAGS");
            data1.add("TIME");
            data1.add("TYPE");
            data1.add("USER");
            data2.add(getDeviceName());
            data2.add(Build.BRAND);
            data2.add(Build.DISPLAY);
            data2.add(Build.PRODUCT);
            data2.add(Build.VERSION.RELEASE);
            data2.add(Build.VERSION.INCREMENTAL);
            data2.add(Build.VERSION.SDK_INT);
            data2.add(Build.BOARD);
            data2.add(Build.BOOTLOADER);
            data2.add(Build.FINGERPRINT);
            data2.add(Build.HARDWARE);
            data2.add(Build.HOST);
            data2.add(Build.ID);
            data2.add(Build.MANUFACTURER);
            data2.add(Build.MODEL);
            data2.add(Build.SERIAL);
            data2.add(Build.TAGS);
            data2.add(Build.TIME);
            data2.add(Build.TYPE);
            data2.add(Build.USER);
            //There are obviously more dynamic ways to do these, but you get the picture
            values.add(data1);
            values.add(data2);

            //Create the valuerange object and set its fields
            ValueRange valueRange = new ValueRange();
            valueRange.setMajorDimension("ROWS");
            valueRange.setRange(range);
            valueRange.setValues(values);

            //then gloriously execute this copy-pasted code ;)
            this.mService.spreadsheets().values()
                    .update(spreadsheetId, range, valueRange)
                    .setValueInputOption("RAW")
                    .execute();

            //Try calling this method before executing the readDataFromApi method,
            //and you'll see the immediate change
        }

        private void createNewSheet(String spreadsheetId, String sheetName) throws IOException {

            //Create a new AddSheetRequest
            AddSheetRequest addSheetRequest = new AddSheetRequest();
            SheetProperties sheetProperties = new SheetProperties();

            //Add the sheetName to the sheetProperties
            addSheetRequest.setProperties(sheetProperties);
            addSheetRequest.setProperties(sheetProperties.setTitle(sheetName));

            //Create batchUpdateSpreadsheetRequest
            BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();

            //Create requestList and set it on the batchUpdateSpreadsheetRequest
            List<Request> requestsList = new ArrayList<>();
            batchUpdateSpreadsheetRequest.setRequests(requestsList);

            //Call the sheets API to execute the batchUpdate
            this.mService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
        }

        private void setFormatting(String spreadsheetId, Integer sheetID) throws IOException {

            //Column 0
            //Create a new updateDimensionPropertiesRequest
            UpdateDimensionPropertiesRequest updateDimensionPropertiesRequest1 = new UpdateDimensionPropertiesRequest();

            //Create a dimensionRange with the desired attributes
            DimensionRange dimensionRange1 = new DimensionRange();
            dimensionRange1.setSheetId(sheetID);
            dimensionRange1.setDimension("COLUMNS");
            dimensionRange1.setStartIndex(0);
            dimensionRange1.setEndIndex(1);

            //Create new dimensionProperties specifying the width of the cells
            DimensionProperties dimensionProperties1 = new DimensionProperties();
            dimensionProperties1.setPixelSize(70);

            //Added the range and the properties to the updateDimensionPropertiesRequest
            updateDimensionPropertiesRequest1.setRange(dimensionRange1);
            updateDimensionPropertiesRequest1.setProperties(dimensionProperties1);
            updateDimensionPropertiesRequest1.setFields("pixelSize");

            //Columns 1 to 5
            //Create a new updateDimensionPropertiesRequest
            UpdateDimensionPropertiesRequest updateDimensionPropertiesRequest2 = new UpdateDimensionPropertiesRequest();

            //Create a dimensionRange with the desired attributes
            DimensionRange dimensionRange2 = new DimensionRange();
            dimensionRange2.setSheetId(sheetID);
            dimensionRange2.setDimension("COLUMNS");
            dimensionRange2.setStartIndex(1);
            dimensionRange2.setEndIndex(21);

            //Create new dimensionProperties specifying the width of the cells
            DimensionProperties dimensionProperties2 = new DimensionProperties();
            dimensionProperties2.setPixelSize(200);

            //Added the range and the properties to the updateDimensionPropertiesRequest
            updateDimensionPropertiesRequest2.setRange(dimensionRange2);
            updateDimensionPropertiesRequest2.setProperties(dimensionProperties2);
            updateDimensionPropertiesRequest2.setFields("pixelSize");

            //Column 6
            //Create a new updateDimensionPropertiesRequest
            UpdateDimensionPropertiesRequest updateDimensionPropertiesRequest3 = new UpdateDimensionPropertiesRequest();

            //Create a dimensionRange with the desired attributes
            DimensionRange dimensionRange3 = new DimensionRange();
            dimensionRange3.setSheetId(sheetID);
            dimensionRange3.setDimension("COLUMNS");
            dimensionRange3.setStartIndex(5);
            dimensionRange3.setEndIndex(6);

            //Create new dimensionProperties specifying the width of the cells
            DimensionProperties dimensionProperties3 = new DimensionProperties();
            dimensionProperties3.setPixelSize(250);

            //Added the range and the properties to the updateDimensionPropertiesRequest
            updateDimensionPropertiesRequest3.setRange(dimensionRange3);
            updateDimensionPropertiesRequest3.setProperties(dimensionProperties3);
            updateDimensionPropertiesRequest3.setFields("pixelSize");

            //Create batchUpdateSpreadsheetRequest
            BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();

            //Create requestList and set it on the batchUpdateSpreadsheetRequest
            List<Request> requestsList = new ArrayList<>();
            batchUpdateSpreadsheetRequest.setRequests(requestsList);

            //Create a new request list containing all the updateDimensionPropertiesRequests
            Request request1 = new Request();
            request1.setUpdateDimensionProperties(updateDimensionPropertiesRequest1);
            Request request2 = new Request();
            request2.setUpdateDimensionProperties(updateDimensionPropertiesRequest2);
            Request request3 = new Request();
            request3.setUpdateDimensionProperties(updateDimensionPropertiesRequest3);
            requestsList.add(request1);
            requestsList.add(request2);
            requestsList.add(request3);

            //Add the requestList to the batchUpdateSpreadsheetRequest
            batchUpdateSpreadsheetRequest.setRequests(requestsList);

            //Call the sheets API to execute the batchUpdate
            this.mService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            mOutputText.setText("Please check your google drive for sheet named DeviceDetails");
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    String setText = "The following error occurred:\n"
                            + mLastError.getMessage();
                    mOutputText.setText(setText);
                }
            } else {
                String setText = "Request cancelled.";
                mOutputText.setText(setText);
            }
        }
    }

    private String createNewSpreadSheet(GoogleAccountCredential mCredential) throws IOException, GeneralSecurityException {
        String sheetId = null;
        Spreadsheet requestBody = new Spreadsheet();
        SpreadsheetProperties sheetProperties = new SpreadsheetProperties();
        sheetProperties.setTitle("Device Details");
        requestBody.setProperties(sheetProperties);
        Sheets sheetsService = createSheetsService(mCredential);
        Sheets.Spreadsheets.Create request = sheetsService.spreadsheets().create(requestBody);

        Spreadsheet response = request.execute();
        sheetId = response.getSpreadsheetId();
        // TODO: Change code below to process the `response` object:
        return sheetId;
    }

    public static Sheets createSheetsService(GoogleAccountCredential mCredential) throws IOException, GeneralSecurityException {
        //HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        // TODO: Change placeholder below to generate authentication credentials. See
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        return new com.google.api.services.sheets.v4.Sheets.Builder(
                transport, jsonFactory, mCredential)
                .setApplicationName("Google Sheets API Android Quickstart")
                .build();
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;
        String phrase = "";
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase += Character.toUpperCase(c);
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase += c;
        }
        return phrase;
    }

}
