package com.billy.cc.core.remote;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 单元测试
 *
 * @author 喵叔catuncle    2020/5/22 0022
 */
@RunWith(AndroidJUnit4.class)
public class InstrumentedTest {

    //任务处理线程（建议用线程池，HandlerThread只是demo）
    private static Handler workerHandler;
    private static final String TARGET_APP = "com.example.target";

    @BeforeClass
    public static void startApp() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        try {
            appContext.getPackageManager().getPackageInfo(TARGET_APP, 0);
        } catch (PackageManager.NameNotFoundException e) {
            fail("请先安装app-target，以配合本单元测试的运行");
            e.printStackTrace();
            throw e;
        }

        HandlerThread worker = new HandlerThread("CP_WORKER");
        worker.start();
        workerHandler = new Handler(worker.getLooper());


        //任务怎么处理应该分发给业务方决定
        RemoteManager.getInstance().setSupport(new RemoteManager.RemoteSupport() {

            @Override
            public Context getContext() {
                return InstrumentationRegistry.getTargetContext();
            }

            @Override
            public void threadPool(Runnable runnable) {
                workerHandler.post(runnable);
            }

            @Override
            public ArrayList<String> getComponentList(String packageName) {
                //实际会通过远程调用获取指定包下的组件列表
                // mock
                try {
                    Thread.sleep(500);//模拟getPkgName()时还没有准备好数据的情况
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ArrayList<String> componentList = new ArrayList<String>();
                if (packageName.equals(TARGET_APP)) {
                    componentList.add("mock.ComponentA");
                    componentList.add("mock.ComponentA2");
                }
                return componentList;
            }

            @Override
            public void log(String format, Object... args) {
                InstrumentedTest.log(format, args);
            }

        });

        RemoteManager.getInstance().enableRemote();
    }


    @Test
    public void useAppContext() throws Exception {
        printLine();
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("com.billy.cc.core.remote.test", appContext.getPackageName());
    }


    @Test
    public void getPkgName() throws Exception {
        printLine();

        String pkgName = RemoteManager.getInstance().getPkgName("mock.ComponentA");
        assertEquals(TARGET_APP, pkgName);
    }


    private void printLine() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        log("\n-------------------------[ %s ]-----------------------------\n", stackTrace[3].getMethodName());
    }

    private static void log(String format, Object... args) {
        Log.i("Test", " >>>> "+String.format(format, args));
    }
}
