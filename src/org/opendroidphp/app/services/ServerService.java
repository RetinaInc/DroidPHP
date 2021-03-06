package org.opendroidphp.app.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.internal.widget.IcsToast;

import org.opendroidphp.R;
import org.opendroidphp.app.common.tasks.ConnectServer;

import eu.chainfire.libsuperuser.Shell;

public class ServerService extends Service {

    public static final String EXTRA_PORT = "EXTRA_PORT";
    private final IBinder mBinder = new ServerBinder();
    private PowerManager.WakeLock wakeLock = null;
    // private WifiManager.WifiLock wifiLock = null;

    private SharedPreferences preferences;

    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        initialize();

        return (START_NOT_STICKY);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyService();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    protected void initialize() {

        NotificationManager noti = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Notification notification = new Notification(R.drawable.ic_launcher, "DroidPHP service started", System.currentTimeMillis());

        Context context = getApplicationContext();

        CharSequence contentTitle = "DroidPHP";
        CharSequence contentText = "Web Service started";

        Intent notificationIntent = new Intent();
        PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText,
                contentIntent);

        noti.notify(143, notification);

        if (preferences.getBoolean("enable_screen_on", false)) {

            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "DPScreenLock");
            wakeLock.acquire();

        }
        if (preferences.getBoolean("enable_lock_wifi", false)) {
            /*  not implemented */
        }
        String baseShell = (!preferences.getBoolean("run_as_root", false)) ? "sh" : "su";
        String daemon = preferences.getString("use_server_httpd", "lighttpd");
        String port = preferences.getString("server_port", "8080");


        Runnable connect = new ConnectServer()
                .setShell(baseShell)
                .setServer(daemon)
                .setServerPort(port);

        Thread connectThread = new Thread(connect);
        connectThread.start();
        //(new ServerListener()).start();


    }

    protected void destroyService() {

        NotificationManager notify = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notify.cancel(143);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public class ServerBinder extends Binder {

        ServerService getService() {
            return ServerService.this;
        }
    }

    class ServerListener extends Thread {

        @Override
        public void run() {

            while (true) {

                String res = Shell.SH.run("ps").get(0);
                if (!res.contains("php") && !res.contains("lighttpd") && !res.contains("mysqld")) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            IcsToast.makeText(
                                    getApplicationContext(),
                                    getString(R.string.core_apps_not_installed), Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                    destroyService();
                    break;
                }
            }
        }
    }
}