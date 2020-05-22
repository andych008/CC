package com.billy.cc.core.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 远程组件管理(查找、增、删、改)
 *
 * @author 喵叔catuncle    2020/5/21 0021
 */
public class RemoteManager {

    private static final String INTENT_FILTER_SCHEME = "package";

    // FIXME: 2020/5/21 0021 参数指定action
    private static final String INTENT_FILTER_ACTION = "action.com.billy.cc.connection";

    private static final ConcurrentHashMap<String, List<String>> REMOTE_COMPONENTS = new ConcurrentHashMap<String, List<String>>();

    private volatile static RemoteSupport support;

    private volatile boolean hasInit = false;//getPkgName()时，scanRemoteApps()可能还没有扫描完

    public void setSupport(RemoteSupport remoteSupport) {
        RemoteManager.support = remoteSupport;
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
        if (!hasInit) {
            synchronized (this) {
                try {
                    support.log("getPkgName wait ...");
                    wait();
                    support.log("getPkgName wait finished");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
        new Thread() {
            @Override
            public void run() {
                ArrayList<String> packageNames = scanPkgWithAction(INTENT_FILTER_ACTION);
                int size = packageNames.size();

                if (size > 0) {
                    CountDownLatch latch = new CountDownLatch(size);
                    //查找每个包里的组件
                    for (String pkg : packageNames) {
                        support.threadPool(new ScanComponentTask(pkg, latch));
                    }

                    try {
                        latch.await(5000L, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }

                hasInit = true;

                synchronized (RemoteManager.this) {
                    RemoteManager.this.notifyAll();
                }
            }
        }.start();
    }

    /**
     * 获取当前设备上安装的可供跨app调用组件的App列表
     *
     * @return 包名集合
     */
    private ArrayList<String> scanPkgWithAction(String action) {
        Context context = support.getContext();
        String curPkg = context.getPackageName();
        PackageManager pm = context.getPackageManager();
        // 查询所有已经安装的应用程序
        Intent intent = new Intent(action);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        ArrayList<String> packageNames = new ArrayList<String>();
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
        support.getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String packageName = intent.getDataString();
                if (packageName != null && packageName.startsWith(INTENT_FILTER_SCHEME)) {
                    //package:com.billy.cc.demo.component.a
                    packageName = packageName.substring(INTENT_FILTER_SCHEME.length() + 1);
                    String action = intent.getAction();
                    support.log("onReceived.....pkg=%s, action=%s", packageName, action);
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        REMOTE_COMPONENTS.remove(packageName);
                    } else {
                        support.log("find a remote app:%s", packageName);
                        support.threadPool(new ScanComponentTask(packageName, null));
                    }
                }
            }
        }, intentFilter);
    }

    public static RemoteManager getInstance() {
        return SingletonHolder.instance;
    }

    private static class SingletonHolder {
        private static RemoteManager instance = new RemoteManager();
    }

    private RemoteManager() {
    }


    /**
     * 扫描组件的任务
     */
    private static class ScanComponentTask implements Runnable {
        private String packageName;
        private CountDownLatch latch;

        ScanComponentTask(String packageName, CountDownLatch latch) {
            this.packageName = packageName;
            this.latch = latch;
        }

        @Override
        public void run() {
            support.log("ScanComponentTask -> %s : ...", packageName);
            ArrayList<String> componentList = support.getComponentList(packageName);
            support.log("ScanComponentTask -> %s : %s", packageName, componentList);
            if (componentList != null) {
                REMOTE_COMPONENTS.put(packageName, componentList);
            }
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    public interface RemoteSupport {

        Context getContext();

        /**
         * 任务放入指定线程
         */
        void threadPool(Runnable runnable);

        /**
         * 获取组件List
         */
        ArrayList<String> getComponentList(String packageName);

        void log(String format, Object... args);
    }
}
