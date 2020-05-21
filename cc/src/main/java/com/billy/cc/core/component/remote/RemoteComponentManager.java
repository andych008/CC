package com.billy.cc.core.component.remote;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.billy.cc.core.component.CC;
import com.billy.cc.core.component.ComponentManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程组件管理(远程组件的查找)
 *
 * @author 喵叔catuncle    2020/5/21 0021
 */
public class RemoteComponentManager {

    private static final String INTENT_FILTER_SCHEME = "package";
    private static final ConcurrentHashMap<String, List<String>> REMOTE_COMPONENTS = new ConcurrentHashMap<>();

    private volatile static TaskDispatcher taskDispatcher;

    public void setTaskDispatcher(TaskDispatcher taskDispatcher) {
        RemoteComponentManager.taskDispatcher = taskDispatcher;
    }

    /**
     * 远程调用开关
     */
    public void enableRemote() {

        listenRemoteApps();

        scanRemoteApps();
    }

    /**
     * 获取远程组件所在app的包名
     */
    public String getPkgName(String componentName) {
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

    /**
     * 查找远程组件
     */
    private void scanRemoteApps() {
        //查找远程组件app的包名
        // FIXME: 2020/5/21 0021 参数指定action
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
            packageNames.add(packageName);
        }
        return packageNames;
    }

    /**
     * 监听设备上远程组件的安装、卸载等
     */
    private void listenRemoteApps() {
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
                if (packageName != null && packageName.startsWith(INTENT_FILTER_SCHEME)) {
                    //package:com.billy.cc.demo.component.a
                    packageName = packageName.substring(INTENT_FILTER_SCHEME.length() + 1);
                    String action = intent.getAction();
                    CC.log("onReceived.....pkg=%s, action=%s", packageName, action);
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        REMOTE_COMPONENTS.remove(packageName);
                    } else {
                        CC.log("find a remote app:%s", packageName);
                        taskDispatcher.threadPool(new ScanComponentTask(packageName));
                    }
                }
            }
        }, intentFilter);
    }


    public static RemoteComponentManager getInstance() {
        return SingletonHolder.instance;
    }

    private static class SingletonHolder {
        private static RemoteComponentManager instance = new RemoteComponentManager();
    }

    private RemoteComponentManager() {
    }


    /**
     * 扫描组件的任务
     */
    private static class ScanComponentTask implements Runnable {
        String packageName;

        ScanComponentTask(String packageName) {
            this.packageName = packageName;
        }

        @Override
        public void run() {
            ArrayList<String> componentList = taskDispatcher.getComponentList(packageName);
            CC.log("ScanComponentTask -> getComponentList : %s", componentList);
            if (componentList != null) {
                REMOTE_COMPONENTS.put(packageName, componentList);
            }
        }
    }

    /**
     * 任务分发器，输入包名，输出组件List
     */
    public interface TaskDispatcher {

        /**
         * 任务放入指定线程
         */
        void threadPool(Runnable runnable);

        /**
         * 获取组件List
         */
        ArrayList<String> getComponentList(String packageName);
    }
}
