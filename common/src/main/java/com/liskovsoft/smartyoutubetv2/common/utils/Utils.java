package com.liskovsoft.smartyoutubetv2.common.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.WebBrowserPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlService;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlWorker;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final String TASK_ID = RemoteControlWorker.class.getSimpleName();
    private static final String TAG = Utils.class.getSimpleName();
    private static final String QR_CODE_URL_TEMPLATE = "https://api.qrserver.com/v1/create-qr-code/?data=%s";
    private static final int GLOBAL_VOLUME_TYPE = AudioManager.STREAM_MUSIC;
    private static final String GLOBAL_VOLUME_SERVICE = Context.AUDIO_SERVICE;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    /**
     * Limit the maximum size of a Map by removing oldest entries when limit reached
     */
    public static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries*10/7, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * Trim playlist if one exceeds max size
     */
    public static <T> List<T> createLRUList(final int maxEntries) {
        return new LinkedList<T>() {
            @Override
            public boolean add(T t) {
                if (size() > maxEntries) {
                    removeFirst();
                }

                return super.add(t);
            }
        };
    }

    @TargetApi(17)
    public static void displayShareVideoDialog(Context context, String videoId) {
        displayShareVideoDialog(context, videoId, 0);
    }

    @TargetApi(17)
    public static void displayShareVideoDialog(Context context, String videoId, int posSec) {
        Uri videoUrl = convertToFullVideoUrl(videoId, posSec);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    public static void displayShareEmbedVideoDialog(Context context, String videoId) {
        displayShareEmbedVideoDialog(context, videoId, 0);
    }

    @TargetApi(17)
    public static void displayShareEmbedVideoDialog(Context context, String videoId, int posSec) {
        Uri videoUrl = convertToEmbedVideoUrl(videoId, posSec);
        showMultiChooser(context, videoUrl);
    }

    @TargetApi(17)
    public static void displayShareChannelDialog(Context context, String channelId) {
        Uri channelUrl = convertToFullChannelUrl(channelId);
        showMultiChooser(context, channelUrl);
    }

    @TargetApi(17)
    public static void showMultiChooser(Context context, Uri url) {
        Intent primaryIntent = new Intent(Intent.ACTION_VIEW);
        Intent secondaryIntent = new Intent(Intent.ACTION_SEND);
        primaryIntent.setData(url);
        secondaryIntent.putExtra(Intent.EXTRA_TEXT, url.toString());
        secondaryIntent.setType("text/plain");
        Intent chooserIntent = Intent.createChooser(primaryIntent, context.getResources().getText(R.string.share_link));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { secondaryIntent });
        chooserIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.startActivity(chooserIntent);
    }

    /**
     * https://youtu.be/nragduYePsQ?t=193<br/>
     * https://www.youtube.com/watch?v=nragduYePsQ&t=193
     */
    public static Uri convertToFullVideoUrl(String videoId, int posSec) {
        String url = String.format("https://youtu.be/%s?t=%s", videoId, posSec);
        return Uri.parse(url);
    }

    /**
     * https://www.youtube.com/embed/nragduYePsQ?start=193
     */
    public static Uri convertToEmbedVideoUrl(String videoId, int posSec) {
        String url = String.format("https://www.youtube.com/embed/%s?start=%s", videoId, posSec);
        return Uri.parse(url);
    }

    public static Uri convertToFullChannelUrl(String channelId) {
        String url = String.format("https://www.youtube.com/channel/%s", channelId);
        return Uri.parse(url);
    }

    public static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
    }

    public static boolean isPlayerInForeground(Context context) {
        if (context == null) {
            return false;
        }

        return isAppInForeground() && ViewManager.instance(context).getTopView() == PlaybackView.class;
    }

    public static void moveAppToForeground(Context context) {
        if (!Utils.isAppInForeground()) {
            ViewManager.instance(context).startView(SplashView.class);
        }
    }

    public static void movePlayerToForeground(Context context) {
        turnScreenOn(context);

        if (!Utils.isPlayerInForeground(context)) {
            ViewManager.instance(context).startView(PlaybackView.class);
        }
    }

    /**
     * NOTE: Below won't help with "Can not perform this action after onSaveInstanceState"
     */
    public static boolean checkActivity(Activity activity) {
        return activity != null && !activity.isDestroyed() && !activity.isFinishing();
    }

    public static void updateRemoteControlService(Context context) {
        if (context == null || VERSION.SDK_INT <= 19) { // Eltex NPE fix
            return;
        }

        if (RemoteControlData.instance(context).isDeviceLinkEnabled()) {
            // Service that prevents the app from destroying
            startService(context, RemoteControlService.class);
        } else {
            stopService(context, RemoteControlService.class);
        }
    }

    private static void bindService(Context context, Intent serviceIntent) {
        // https://medium.com/@debuggingisfun/android-auto-stop-background-service-336e8b3ff03c
        // https://medium.com/@debuggingisfun/android-o-work-around-background-service-limitation-e697b2192bc3
        context.getApplicationContext().bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                 // NOP
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                 // NOP
            }
        }, Context.BIND_AUTO_CREATE);
    }

    public static void startRemoteControlWorkRequest(Context context) {
        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(
                        RemoteControlWorker.class, 30, TimeUnit.MINUTES
                ).build();

        WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                        TASK_ID,
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                );
    }

    /**
     * Volume: 0 - 100
     */
    public static void setGlobalVolume(Context context, int volume) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(GLOBAL_VOLUME_SERVICE);
            if (audioManager != null) {
                int streamMaxVolume = audioManager.getStreamMaxVolume(GLOBAL_VOLUME_TYPE);
                audioManager.setStreamVolume(GLOBAL_VOLUME_TYPE, (int) Math.ceil(streamMaxVolume / 100f * volume), 0);
            }
        }
    }

    /**
     * Volume: 0 - 100
     */
    public static int getGlobalVolume(Context context) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(GLOBAL_VOLUME_SERVICE);
            if (audioManager != null) {
                int streamMaxVolume = audioManager.getStreamMaxVolume(GLOBAL_VOLUME_TYPE);
                int streamVolume = audioManager.getStreamVolume(GLOBAL_VOLUME_TYPE);

                return (int) Math.ceil(streamVolume / (streamMaxVolume / 100f));
            }
        }

        return 100;
    }

    public static boolean isGlobalVolumeEnabled(Context context) {
        if (context != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(GLOBAL_VOLUME_SERVICE);
            if (audioManager != null) {
                return audioManager.isVolumeFixed();
            }
        }

        return false;
    }

    /**
     * <a href="https://stackoverflow.com/questions/2891337/turning-on-screen-programmatically">More info</a>
     */
    @SuppressWarnings("deprecation")
    private static void turnScreenOn(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(true);
                activity.setTurnScreenOn(true);
                KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (keyguardManager != null) {
                    keyguardManager.requestDismissKeyguard(activity, null);
                }
            } else {
                Window window = activity.getWindow();
                window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                );
            }
        }
    }

    /**
     * Need to be the first line and executed on earliest stage once.<br/>
     * Inits media service language and context.<br/>
     * NOTE: this command should run before using any of the media service api.
     */
    public static void initGlobalData(Context context) {
        Log.d(TAG, "initGlobalData called...");

        // 1) Auth token storage init
        // 2) Media service language setup (I assume that context has proper language)
        GlobalPreferences.instance(context);

        // 1) Remove downloaded apks
        // 2) Setup language
        ViewManager.instance(context).clearCaches();
    }

    public static String toQrCodeLink(String data) {
        return String.format(QR_CODE_URL_TEMPLATE, data);
    }

    public static void openLink(Context context, String url) {
        try {
            WebBrowserPresenter.instance(context).loadUrl(url);
        } catch (Exception e) {
            // WebView not found. Use alt method.
            openLinkExt(context, url);
        }
    }

    public static void openLinkExt(Context context, String url) {
        try {
            openLinkInTabs(context, url);
        } catch (Exception e) {
            // Permission Denial on Android 9 (SecurityException)
            // Chrome Tabs not found (ActivityNotFoundException)
            Helpers.openLink(context, url); // revert to simple in-browser page
        }
    }

    /**
     * <a href="https://developer.chrome.com/docs/android/custom-tabs/integration-guide/">Chrome custom tabs</a>
     */
    private static void openLinkInTabs(Context context, String url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(context, Uri.parse(url));
    }

    public static void postDelayed(Runnable callback, int delayMs) {
        sHandler.removeCallbacks(callback);
        sHandler.postDelayed(callback, delayMs);
    }

    public static void post(Runnable callback) {
        sHandler.removeCallbacks(callback);
        sHandler.post(callback);
    }

    public static void removeCallbacks(Runnable... callbacks) {
        if (callbacks == null) {
            return;
        }

        for (Runnable callback : callbacks) {
             sHandler.removeCallbacks(callback);
        }
    }

    public static CharSequence color(CharSequence string, int color) {
        SpannableString spannable = new SpannableString(string);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(color);
        spannable.setSpan(foregroundColorSpan, 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

    public static CharSequence italic(CharSequence string) {
        SpannableString spannable = new SpannableString(string);
        spannable.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public static CharSequence bold(CharSequence string) {
        SpannableString spannable = new SpannableString(string);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public static CharSequence icon(Context context, int resId, int lineHeight) {
        SpannableString spannable = new SpannableString(" ");
        Drawable drawable = ContextCompat.getDrawable(context, resId);
        drawable.setBounds(0, 0, lineHeight, lineHeight);
        ImageSpan imageSpan = new ImageSpan(drawable);
        spannable.setSpan(imageSpan, 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }

    @SuppressWarnings("deprecation")
    public static boolean isServiceRunning(Context context, Class<? extends Service> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    public static Notification createNotification(Context context, int iconResId, String title, Class<? extends Activity> activityCls) {
        return createNotification(context, iconResId, title, null, activityCls);
    }

    @SuppressWarnings("deprecation")
    public static Notification createNotification(Context context, int iconResId, String title, String content, Class<? extends Activity> activityCls) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(iconResId)
                        .setContentTitle(title);

        if (content != null) {
            builder.setContentText(content);
        }

        Intent targetIntent = new Intent(context, activityCls);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        if (VERSION.SDK_INT >= 26) {
            String channelId = context.getPackageName();
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    title,
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);
        }

        return builder.build();
    }

    public static void showNotification(Context context, int notificationId, Notification notification) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }

    public static void startService(Context context, Class<? extends Service> serviceCls) {
        if (isServiceRunning(context, serviceCls)) {
            return;
        }

        Intent serviceIntent = new Intent(context, serviceCls);

        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        if (VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public static void stopService(Context context, Class<? extends Service> serviceCls) {
        if (!isServiceRunning(context, serviceCls)) {
            return;
        }

        Intent serviceIntent = new Intent(context, serviceCls);

        context.stopService(serviceIntent);
    }

    public static void showRepeatInfo(Context context, int modeIndex) {
        switch (modeIndex) {
            case PlaybackUIController.REPEAT_MODE_ALL:
                MessageHelpers.showMessage(context, R.string.repeat_mode_all);
                break;
            case PlaybackUIController.REPEAT_MODE_ONE:
                MessageHelpers.showMessage(context, R.string.repeat_mode_one);
                break;
            case PlaybackUIController.REPEAT_MODE_PAUSE:
                MessageHelpers.showMessage(context, R.string.repeat_mode_pause);
                break;
            case PlaybackUIController.REPEAT_MODE_LIST:
                MessageHelpers.showMessage(context, R.string.repeat_mode_pause_alt);
                break;
            case PlaybackUIController.REPEAT_MODE_CLOSE:
                MessageHelpers.showMessage(context, R.string.repeat_mode_none);
                break;
        }
    }

    /**
     * Selecting right presenter for the channel.<br/>
     * Channels could be of two types: regular (usr channel) and playlist channel (contains single row, try search: 'Mon mix')
     */
    public static void chooseChannelPresenter(Context context, Video item) {
        if (item.hasVideo()) { // regular channel
            ChannelPresenter.instance(context).openChannel(item);
            return;
        }

        LoadingManager.showLoading(context, true);

        MediaServiceManager.instance().loadChannelRows(item, group -> {
            LoadingManager.showLoading(context, false);

            if (group == null || group.size() == 0) {
                return;
            }

            if (group.size() == 1) {
                // Start first video or open full list?
                //if (group.get(0).getMediaItems() != null) {
                //    PlaybackPresenter.instance(context).openVideo(Video.from(group.get(0).getMediaItems().get(0)));
                //}

                // TODO: clear only once, on start
                ChannelUploadsPresenter.instance(context).clear();
                ChannelUploadsPresenter.instance(context).updateGrid(group.get(0));
            } else {
                // TODO: clear only once, on start
                ChannelPresenter.instance(context).clear();
                ChannelPresenter.instance(context).updateRows(group);
            }
        });
    }

    public static void sendKey(int key) {
        try {
            Instrumentation instrumentation = new Instrumentation();
            instrumentation.sendKeyDownUpSync(key);
        } catch (SecurityException e) {
            // Injecting to another application requires INJECT_EVENTS permission
            e.printStackTrace();
        }
    }

    public static void sendKey(KeyEvent keyEvent) {
        try {
            Instrumentation instrumentation = new Instrumentation();
            instrumentation.sendKeySync(keyEvent);
        } catch (SecurityException e) {
            // Injecting to another application requires INJECT_EVENTS permission
            e.printStackTrace();
        }
    }

    public static void showNotCompatibleMessage(Context context, int msgResId) {
        MessageHelpers.showMessage(context, String.format("%s '%s'",
                context.getString(R.string.not_compatible_with),
                context.getString(msgResId)));
    }

    public static String getCountryFlagUrl(String countryCode) {
        return "https://countryflagsapi.com/png/" + countryCode;
    }

    public static void showPlayerControls(Context context, boolean show) {
        PlaybackView view = PlaybackPresenter.instance(context).getView();
        if (view != null) {
            view.getController().showOverlay(show);
        }
    }

    public static int toSec(long ms) {
        return (int) (ms / 1_000);
    }
}
