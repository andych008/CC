package com.billy.cc.core.component;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.BundleCompat;

import com.billy.cc.core.ipc.CP_Caller;
import com.billy.cc.core.ipc.CP_Util;
import com.billy.cc.core.ipc.IPCRequest;
import com.billy.cc.core.ipc.IRemoteCallback;

import java.util.HashMap;

import static com.billy.cc.core.ipc.IPCProvider.ARG_EXTRAS_CALLBACK;
import static com.billy.cc.core.ipc.IPCProvider.ARG_EXTRAS_REQUEST;

/**
 * 跨进程调用组件(通过IPCProvider的方式)
 *
 * @author 喵叔catuncle    2020/5/15 0015
 */
public class IPCCaller {

    //-------------------------单例模式 start --------------
    private IPCCaller(){
    }
    //-------------------------单例模式 end --------------


    /**
     * 异步调用(回调线程：不做处理，默认在binder线程)
     */
    public static void callAsync(Context context, String pkg, IPCRequest request, final CP_Caller.ICallback callback) {
        Bundle extras = new Bundle();
        extras.putParcelable(ARG_EXTRAS_REQUEST, request);
        if (callback != null) {
            BundleCompat.putBinder(extras, ARG_EXTRAS_CALLBACK, new IRemoteCallback.Stub() {
                @Override
                public void callback(Bundle remoteResult) {
                    callback.onResult(remoteResult);
                }
            });
        }
        doIpc(context, pkg, extras);
    }

    /**
     * doIpc()本身是同步调用，而且是跨进程的。所以，如果在主线程执行callAsync()可以通过一个Dispatcher线程来执行，从而减少主线程的耗时。
     *
     * 约定component==null表示非组件方法调用，如获取pkg里所有component的列表
     */
    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    private static Bundle doIpc(Context context, String pkg, Bundle extras) {
        Bundle result = null;
        Uri uri = Uri.parse("content://" + pkg + ".com.billy.cc.core.remote");
        try {
            int tryMax = 5;
            do {
                String method = "";
                String arg = "{}";
                if (CP_Util.VERBOSE_LOG) {
                    IPCRequest request = extras.getParcelable(ARG_EXTRAS_REQUEST);
                    method = String.format("[%s # %s]", request.getComponentName(), request.getActionName());
                    HashMap<String, Object> params = request.getParams();
                    if (params != null) {
                        arg = params.toString();
                    }
                }
                result = context.getContentResolver().call(uri, method, arg, extras);//extras非空表示异步请求
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



    public static void cancel(Context context, String pkg, String callId) {
        IPCRequest request = IPCRequest.createCancelRequest(callId);
        callAsync(context, pkg, request, null);
    }

    public static void timeout(Context context, String pkg, String callId) {
        IPCRequest request = IPCRequest.createTimeoutRequest(callId);
        callAsync(context, pkg, request, null);
    }

    public static void getComponentListByProcessName(Context context, String pkg, CP_Caller.ICallback callback) {
        IPCRequest request = IPCRequest.createGetComponentListRequest();
        callAsync(context, pkg, request, callback);
    }
}
