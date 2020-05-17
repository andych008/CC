package com.billy.cc.core.ipc;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.app.BundleCompat;

import static com.billy.cc.core.ipc.IPCProvider.ARG_EXTRAS_CALLBACK;

/**
 * 通过ContentProvider实现的跨进程方法调用(适用于组件化框架)
 *
 * @author 喵叔catuncle    2020/5/15 0015
 */
public class CP_Caller {

    public interface ICallback {
        void onResult(Bundle result);
    }

    /**
     * 同步调用
     */
    public static Bundle call(Context context, String pkg, String component, String action) {
        return doIpc(context, pkg, component, action, null);
    }

    /**
     * 异步调用(回调线程：不做处理，默认在binder线程)
     */
    public static void callAsync(Context context, String pkg, String component, String action, final ICallback callback) {
        Bundle extras = new Bundle();
        BundleCompat.putBinder(extras, ARG_EXTRAS_CALLBACK, new IRemoteCallback.Stub() {
            @Override
            public void callback(Bundle remoteResult) {
                callback.onResult(remoteResult);
            }
        });
        doIpc(context, pkg, component, action, extras);
    }

    /**
     * 异步调用(回调线程：通过handler指定)
     */
    public static void callAsync(Context context, String pkg, String component, String action, final Handler callbackHandler) {
        Bundle extras = new Bundle();
        BundleCompat.putBinder(extras, ARG_EXTRAS_CALLBACK, new IRemoteCallback.Stub() {
            @Override
            public void callback(Bundle remoteResult) {
                Message msg = callbackHandler.obtainMessage();
                msg.setData(remoteResult);
                msg.sendToTarget();
            }
        });
        doIpc(context, pkg, component, action, extras);
    }

    /**
     * doIpc()本身是同步调用，而且是跨进程的。所以，如果在主线程执行callAsync()可以通过一个Dispatcher线程来执行，从而减少主线程的耗时。
     */
    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    private static Bundle doIpc(Context context, String pkg, String component, String action, Bundle extras) {
        Bundle result = null;
        Uri uri = Uri.parse("content://" + pkg + ".provider");
        try {
            int tryMax = 5;
            do {
                result = context.getContentResolver().call(uri, component + "#" + action, null, extras);//extras非空表示异步请求
                if (result != null) {
                    break;
                } else {
                    CP_Util.verboseLog("tryMax = %s", tryMax);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } while (tryMax-- > 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 开关debug模式（打印日志），默认为关闭状态
     */
    public static void enableDebug(boolean enable) {
        CP_Util.DEBUG = enable;
    }

    /**
     * 开关调用过程详细日志，默认为关闭状态
     */
    public static void enableVerboseLog(boolean enable) {
        CP_Util.VERBOSE_LOG = enable;
    }

}
