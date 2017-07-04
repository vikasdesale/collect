package org.odk.collect.android.receivers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.NotificationActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.exception.MultipleFoldersFoundException;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.GoogleSheetsAbstractUploader;
import org.odk.collect.android.tasks.InstanceUploaderTask;
import org.odk.collect.android.utilities.WebUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import timber.log.Timber;

public class NetworkReceiver extends BroadcastReceiver implements InstanceUploaderListener {

    // turning on wifi often gets two CONNECTED events. we only want to run one thread at a time
    public static boolean running = false;
    InstanceUploaderTask instanceUploaderTask;

    GoogleSheetsAutoUploadTask googleSheetsUploadTask;

    @Override
    public void onReceive(Context context, Intent intent) {
        // make sure sd card is ready, if not don't try to send
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return;
        }

        String action = intent.getAction();
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetworkInfo = manager.getActiveNetworkInfo();

        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (currentNetworkInfo != null
                    && currentNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                if (interfaceIsEnabled(context, currentNetworkInfo)) {
                    uploadForms(context);
                }
            }
        } else if (action.equals("org.odk.collect.android.FormSaved")) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

            if (ni == null || !ni.isConnected()) {
                // not connected, do nothing
            } else {
                if (interfaceIsEnabled(context, ni)) {
                    uploadForms(context);
                }
            }
        }
    }

    private boolean interfaceIsEnabled(Context context,
                                       NetworkInfo currentNetworkInfo) {
        // make sure autosend is enabled on the given connected interface
        String autosend = (String) GeneralSharedPreferences.getInstance().get(PreferenceKeys.KEY_AUTOSEND);
        boolean sendwifi = autosend.equals("wifi_only");
        boolean sendnetwork = autosend.equals("cellular_only");
        if (autosend.equals("wifi_and_cellular")) {
            sendwifi = true;
            sendnetwork = true;
        }

        return (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
                && sendwifi || currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE
                && sendnetwork);
    }


    private void uploadForms(Context context) {
        if (!running) {
            running = true;

            ArrayList<Long> toUpload = new ArrayList<Long>();
            Cursor c = new InstancesDao().getFinalizedInstancesCursor();

            try {
                if (c != null && c.getCount() > 0) {
                    c.move(-1);
                    while (c.moveToNext()) {
                        Long l = c.getLong(c.getColumnIndex(InstanceColumns._ID));
                        toUpload.add(l);
                    }
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            if (toUpload.size() < 1) {
                running = false;
                return;
            }

            Long[] toSendArray = new Long[toUpload.size()];
            toUpload.toArray(toSendArray);


            GoogleAccountCredential accountCredential;
            // Initialize credentials and service object.
            accountCredential = GoogleAccountCredential.usingOAuth2(
                    Collect.getInstance(), Collections.singleton(DriveScopes.DRIVE))
                    .setBackOff(new ExponentialBackOff());

            GeneralSharedPreferences settings = GeneralSharedPreferences.getInstance();

            String protocol = (String) settings.get(PreferenceKeys.KEY_PROTOCOL);

            if (protocol.equals(context.getString(R.string.protocol_google_sheets))) {
                googleSheetsUploadTask = new GoogleSheetsAutoUploadTask(context, accountCredential);
                String googleUsername = (String) settings.get(
                        PreferenceKeys.KEY_SELECTED_GOOGLE_ACCOUNT);
                if (googleUsername == null || googleUsername.equalsIgnoreCase("")) {
                    // just quit if there's no username
                    running = false;
                    return;
                }
                accountCredential.setSelectedAccountName(googleUsername);
                googleSheetsUploadTask.setUploaderListener(this);
                googleSheetsUploadTask.execute(toSendArray);

            } else {
                // get the username, password, and server from preferences

                String storedUsername = (String) settings.get(PreferenceKeys.KEY_USERNAME);
                String storedPassword = (String) settings.get(PreferenceKeys.KEY_PASSWORD);
                String server = (String) settings.get(PreferenceKeys.KEY_SERVER_URL);
                String url = server + settings.get(PreferenceKeys.KEY_FORMLIST_URL);

                Uri u = Uri.parse(url);
                WebUtils.addCredentials(storedUsername, storedPassword, u.getHost());

                instanceUploaderTask = new InstanceUploaderTask();
                instanceUploaderTask.setUploaderListener(this);

                instanceUploaderTask.execute(toSendArray);
            }
        }
    }

    @Override
    public void uploadingComplete(HashMap<String, String> result) {
        // task is done
        if (instanceUploaderTask != null) {
            instanceUploaderTask.setUploaderListener(null);
        }
        if (googleSheetsUploadTask != null) {
            googleSheetsUploadTask.setUploaderListener(null);
        }
        running = false;

        StringBuilder message = new StringBuilder();
        message.append(Collect.getInstance().getString(R.string.odk_auto_note) + " :: \n\n");

        if (result == null) {
            message.append(Collect.getInstance().getString(R.string.odk_auth_auth_fail));
        } else {

            StringBuilder selection = new StringBuilder();
            Set<String> keys = result.keySet();
            Iterator<String> it = keys.iterator();

            String[] selectionArgs = new String[keys.size()];
            int i = 0;
            while (it.hasNext()) {
                String id = it.next();
                selection.append(InstanceColumns._ID + "=?");
                selectionArgs[i++] = id;
                if (i != keys.size()) {
                    selection.append(" or ");
                }
            }

            {
                Cursor results = null;
                try {
                    results = new InstancesDao().getInstancesCursor(selection.toString(), selectionArgs);
                    if (results.getCount() > 0) {
                        results.moveToPosition(-1);
                        while (results.moveToNext()) {
                            String name = results.getString(results
                                    .getColumnIndex(InstanceColumns.DISPLAY_NAME));
                            String id = results.getString(results
                                    .getColumnIndex(InstanceColumns._ID));
                            message.append(name + " - " + result.get(id) + "\n\n");
                        }
                    }
                } finally {
                    if (results != null) {
                        results.close();
                    }
                }
            }
        }

        Intent notifyIntent = new Intent(Collect.getInstance(), NotificationActivity.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifyIntent.putExtra(NotificationActivity.NOTIFICATION_KEY, message.toString().trim());

        PendingIntent pendingNotify = PendingIntent.getActivity(Collect.getInstance(), 0,
                notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(Collect.getInstance())
                .setSmallIcon(R.drawable.notes)
                .setContentTitle(Collect.getInstance().getString(R.string.odk_auto_note))
                .setContentIntent(pendingNotify)
                .setContentText(message.toString().trim())
                .setAutoCancel(true)
                .setLargeIcon(
                        BitmapFactory.decodeResource(Collect.getInstance().getResources(),
                                android.R.drawable.ic_dialog_info));

        NotificationManager notificationManager = (NotificationManager) Collect.getInstance()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1328974928, builder.build());
    }


    @Override
    public void progressUpdate(int progress, int total) {
        // do nothing
    }


    @Override
    public void authRequest(Uri url, HashMap<String, String> doneSoFar) {
        // if we get an auth request, just fail
        if (instanceUploaderTask != null) {
            instanceUploaderTask.setUploaderListener(null);
        }
        if (googleSheetsUploadTask != null) {
            googleSheetsUploadTask.setUploaderListener(null);
        }
        running = false;
    }

    private class GoogleSheetsAutoUploadTask extends
            GoogleSheetsAbstractUploader {

        private final GoogleAccountCredential credential;
        private Context context;

        public GoogleSheetsAutoUploadTask(Context c, GoogleAccountCredential credential) {
            context = c;
            this.credential = credential;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            sheetsService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("ODK-Collect")
                    .build();
            driveService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("ODK-Collect")
                    .build();
        }

        @Override
        protected HashMap<String, String> doInBackground(Long... values) {

            results = new HashMap<String, String>();

            String selection = InstanceColumns._ID + "=?";
            String[] selectionArgs = new String[(values == null) ? 0 : values.length];
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    if (i != values.length - 1) {
                        selection += " or " + InstanceColumns._ID + "=?";
                    }
                    selectionArgs[i] = values[i].toString();
                }
            }

            String token;
            try {
                token = credential.getToken();
                GoogleAuthUtil.invalidateToken(context, token);

                getIDOfFolderWithName(GOOGLE_DRIVE_ROOT_FOLDER, null);


            } catch (IOException | GoogleAuthException | MultipleFoldersFoundException e) {
                Timber.e(e);
                return null;
            }
            context = null;

            if (token == null) {
                // failed, so just return
                return null;
            }

            uploadInstances(selection, selectionArgs, token);
            return results;
        }
    }

}
