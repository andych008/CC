package com.billy.cc.core.ipc;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.BundleCompat;

import com.billy.cc.core.ipc.inner.InnerProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static com.billy.cc.core.ipc.IPCRequest.CMD_ACTION_CANCEL;
import static com.billy.cc.core.ipc.IPCRequest.CMD_ACTION_GET_COMPONENT_LIST;
import static com.billy.cc.core.ipc.IPCRequest.CMD_ACTION_TIMEOUT;


abstract public class IPCProvider extends InnerProvider {

    public static final String ARG_EXTRAS_REQUEST = "request";
    public static final String ARG_EXTRAS_CALLBACK = "callback";
    public static final String ARG_EXTRAS_COMPONENT_LIST = "component_list";
    public static final String ARG_EXTRAS_RESULT = "result";


    private volatile static TaskDispatcher taskDispatcher;
    private Handler mainHandler;
    private Handler workerHandler;

    /**
     * 请求分发器
     * <br/>
     * 我们将请求分为两类：常规请求、命令请求
     */
    public interface TaskDispatcher {
        /**
         * 组件间常规请求 同步调用
         */
        void runAction(IPCRequest request, Bundle remoteResult);

        /**
         * 命令请求：获取当前app中包含的组件列表
         */
        ArrayList<String> cmdGetComponentList();

        /**
         * 命令请求：取消请求
         */
        void cmdCancel(String callId);

        /**
         * 命令请求：请求超时
         */
        void cmdTimeout(String callId);
    }

    public static void setTaskDispatcher(TaskDispatcher taskDispatcher) {
        IPCProvider.taskDispatcher = taskDispatcher;
    }

    @Override
    public boolean onCreate() {
        mainHandler = new Handler(Looper.getMainLooper())  {
            @Override
            public void handleMessage(Message msg) {
                doHandleMessage(msg);
            }
        };
        HandlerThread worker = new HandlerThread("CP_WORKER");
        worker.start();
        workerHandler = new Handler(worker.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                doHandleMessage(msg);
            }
        };

        return super.onCreate();
    }

    private void doHandleMessage(Message msg) {
        if (CP_Util.VERBOSE_LOG) {
            CP_Util.verboseLog("handleMessage with: msg = %s", msg);
        }

        Bundle extras = msg.getData();
        extras.setClassLoader(IPCRequest.class.getClassLoader());
        IPCRequest request = extras.getParcelable(ARG_EXTRAS_REQUEST);



        Bundle remoteResult = new Bundle();

        if (request.isCmd()) {
            // FIXME: 2020/5/17 0017 命令也改成异步
            //命令都是在子线程 同步处理
            handleCmd(request, remoteResult);
        } else {
            IPCProvider.taskDispatcher.runAction(request, remoteResult);
        }

        //有回调用方法就是异步调用
        IBinder iBinder = (BundleCompat.getBinder(extras, ARG_EXTRAS_CALLBACK));
        IRemoteCallback callback = IRemoteCallback.Stub.asInterface(iBinder);

        if (callback != null) {
            try {
                callback.callback(remoteResult);
            } catch (RemoteException e) {
                CP_Util.printStackTrace(e);
                CP_Util.log("remote doCallback failed!");
            }
        }
    }


    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable final Bundle extras) {
        CP_Util.log("receive call from other process. method = %s, arg = %s, extras = %s", method, arg, extras);
        if (extras != null) {
            extras.setClassLoader(getClass().getClassLoader());
            CP_Util.log("receive call from other process. extras.keySet() = %s", Arrays.asList(extras.keySet().toArray()));

            Message msg = Message.obtain();
            msg.setData(extras);
            IPCRequest request = extras.getParcelable(ARG_EXTRAS_REQUEST);
            if (request != null) {
                if (request.isMainThreadSyncCall()) {
                    mainHandler.sendMessage(msg);
                } else {
                    workerHandler.sendMessage(msg);
                }
            } else {
                CP_Util.logError("receive call from other process. request = null null null");
            }

            CP_Util.log("dispatch call ...");
            return Bundle.EMPTY;
        } else {
            CP_Util.logError("receive call from other process. extras = null null null");
            return Bundle.EMPTY;
        }
    }


    private void handleCmd(IPCRequest request, Bundle remoteResult) {
        String actionName = request.getActionName();
        if (CP_Util.VERBOSE_LOG) {
            CP_Util.verboseLog("ipc cmd = %s", actionName);
        }

        if (CMD_ACTION_GET_COMPONENT_LIST.equals(actionName)) {
            // 获取组件列表
            ArrayList<String> componentList = IPCProvider.taskDispatcher.cmdGetComponentList();
            remoteResult.putStringArrayList(ARG_EXTRAS_COMPONENT_LIST, componentList);

        } else if (CMD_ACTION_CANCEL.equals(actionName)) {
            // 取消请求
            IPCProvider.taskDispatcher.cmdCancel(request.getCallId());

        } else if (CMD_ACTION_TIMEOUT.equals(actionName)) {
            //请求超时
            IPCProvider.taskDispatcher.cmdTimeout(request.getCallId());
        }
    }
}
