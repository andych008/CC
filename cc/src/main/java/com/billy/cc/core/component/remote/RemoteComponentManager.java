package com.billy.cc.core.component.remote;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;

import com.billy.cc.core.component.CC;
import com.billy.cc.core.component.CCIPCRequest;
import com.billy.cc.core.component.CCUtil;
import com.billy.cc.core.component.ComponentManager;
import com.billy.cc.core.ipc.IPCCaller;
import com.billy.cc.core.ipc.IPCRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.billy.cc.core.ipc.IPCProvider.ARG_EXTRAS_RESULT;

/**
 * 远程组件管理(远程组件的查找)
 *
 * @author 喵叔catuncle    2020/5/21 0021
 */
public class RemoteComponentManager {

    private static final ConcurrentHashMap<String, List<String>> REMOTE_COMPONENTS = new ConcurrentHashMap<>();

    /**
     * 远程调用开关
     */
    public void enableRemote() {
        // FIXME: 2020/5/21 0021 有开有关
        //监听设备上远程组件的安装、卸载等
        listenRemoteApps();

        //查找远程组件
        scanRemoteApps();
    }

    private void scanRemoteApps() {

        //查找远程组件app的包名
        ArrayList<String> packageNames = scanPkgWithAction("action.com.billy.cc.connection");

        //查找每个包里的组件
        for (String pkg : packageNames) {
            ComponentManager.threadPool(new ScanComponentTask(pkg));
        }
    }

    /**
     * 获取当前设备上安装的可供跨app调用组件的App列表
     *
     * @return 包名集合
     */
    private ArrayList<String> scanPkgWithAction(String action) {
        Application application = CC.getApplication();
        String curPkg = application.getPackageName();
        PackageManager pm = application.getPackageManager();
        // 查询所有已经安装的应用程序
        Intent intent = new Intent(action);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        ArrayList<String> packageNames = new ArrayList<>();
        for (ResolveInfo info : list) {
            ActivityInfo activityInfo = info.activityInfo;
            String packageName = activityInfo.packageName;
            if (curPkg.equals(packageName)) {
                continue;
            }
            if (tryWakeup(packageName)) {
                packageNames.add(packageName);
            }
        }
        return packageNames;
    }

    /**
     * 检测组件App是否存在，并顺便唤醒App
     *
     * @param packageName app的包名
     * @return 成功与否（true:app存在，false: 不存在）
     */
    public boolean tryWakeup(String packageName) {
        long time = SystemClock.elapsedRealtime();
        Intent intent = new Intent();
        intent.setClassName(packageName, RemoteConnectionActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            CC.getApplication().startActivity(intent);
            CC.log("wakeup remote app '%s' success. time=%d", packageName, (SystemClock.elapsedRealtime() - time));
            return true;
        } catch (Exception e) {
            CCUtil.printStackTrace(e);
            CC.log("wakeup remote app '%s' failed. time=%d", packageName, (SystemClock.elapsedRealtime() - time));
            return false;
        }
    }

    public String getProcessName(String componentName) {
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

    private static final String INTENT_FILTER_SCHEME = "package";

    public void listenRemoteApps() {
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
                    if (RemoteComponentManager.getInstance().tryWakeup(packageName)) {
                        ComponentManager.threadPool(new ScanComponentTask(packageName));
                    }
                }
            }
        }, intentFilter);
    }


    private static class ScanComponentTask implements Runnable {
        String packageName;

        ScanComponentTask(String packageName) {
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

    public static RemoteComponentManager getInstance() {
        return SingletonHolder.instance;
    }

    private static class SingletonHolder {
        private static RemoteComponentManager instance = new RemoteComponentManager();
    }

    private RemoteComponentManager() {
    }
}
