package com.billy.cc.core.component;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;


import com.billy.cc.core.component.remote.RemoteConnection;
import com.billy.cc.core.ipc.IPCCaller;
import com.billy.cc.core.ipc.IPCRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.billy.cc.core.ipc.IPCProvider.ARG_EXTRAS_RESULT;

/**
 * 跨App调用组件
 * 继承自 {@link SubProcessCCInterceptor}, 额外处理了跨App的进程连接
 * @author billy.qi
 * @since 18/6/24 00:25
 */
class RemoteCCInterceptor extends SubProcessCCInterceptor {

    private static final ConcurrentHashMap<String, List<String>> REMOTE_COMPONENTS = new ConcurrentHashMap<>();

    //-------------------------单例模式 start --------------
    /** 单例模式Holder */
    private static class RemoteCCInterceptorHolder {
        private static final RemoteCCInterceptor INSTANCE = new RemoteCCInterceptor();
    }
    private RemoteCCInterceptor(){}
    /** 获取{@link RemoteCCInterceptor}的单例对象 */
    static RemoteCCInterceptor getInstance() {
        return RemoteCCInterceptorHolder.INSTANCE;
    }
    //-------------------------单例模式 end --------------

    @Override
    public CCResult intercept(Chain chain) {
        String processName = getProcessName(chain.getCC().getComponentName());
        if (!TextUtils.isEmpty(processName)) {
            return multiProcessCall(chain, processName);
        }
        return CCResult.error(CCResult.CODE_ERROR_NO_COMPONENT_FOUND);
    }

    private String getProcessName(String componentName) {
        String processName = null;
        for (Map.Entry<String, List<String>> entry : REMOTE_COMPONENTS.entrySet()) {
            for (String s : entry.getValue()) {
                if (s.equals(componentName)) {
                    processName = entry.getKey();
                    break;
                }
            }
        }
        return processName;
    }

    void enableRemoteCC() {
        //监听设备上其它包含CC组件的app
        listenComponentApps();
        connect(RemoteConnection.scanComponentApps());
    }

    private static final String INTENT_FILTER_SCHEME = "package";
    private void listenComponentApps() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        intentFilter.addDataScheme(INTENT_FILTER_SCHEME);
        CC.getApplication().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String packageName = intent.getDataString();
                if (TextUtils.isEmpty(packageName)) {
                    return;
                }
                if (packageName.startsWith(INTENT_FILTER_SCHEME)) {
                    packageName = packageName.replace(INTENT_FILTER_SCHEME + ":", "");
                }
                String action = intent.getAction();
                CC.log("onReceived.....pkg=" + packageName + ", action=" + action);
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    REMOTE_COMPONENTS.remove(packageName);
                } else {
                    CC.log("start to wakeup remote app:%s", packageName);
                    if (RemoteConnection.tryWakeup(packageName)) {
                        ComponentManager.threadPool(new ConnectTask(packageName));
                    }
                }
            }
        }, intentFilter);
    }

    private void connect(List<String> packageNames) {
        if (packageNames == null || packageNames.isEmpty()) {
            return;
        }
        for (String pkg : packageNames) {
            ComponentManager.threadPool(new ConnectTask(pkg));
        }
    }

    class ConnectTask implements Runnable {
        String packageName;

        ConnectTask(String packageName) {
            this.packageName = packageName;
        }

        @Override
        public void run() {
            IPCRequest request = CCIPCRequest.createGetComponentListCmd();

            Bundle resultBundle = IPCCaller.call(CC.getApplication(), packageName, request);
            ArrayList<String> componentList = resultBundle.getStringArrayList(ARG_EXTRAS_RESULT);
            if (componentList != null) {
                CC.log("getComponentListByProcessName#onResult : %s", componentList);
                REMOTE_COMPONENTS.put(packageName, componentList);
            } else {
                CC.logError("componentList == null");
            }
        }
    }
}
