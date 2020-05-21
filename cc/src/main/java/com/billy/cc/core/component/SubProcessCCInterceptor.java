package com.billy.cc.core.component;

import android.os.Bundle;
import android.os.Looper;

import com.billy.cc.core.component.remote.RemoteCC;
import com.billy.cc.core.component.remote.RemoteCCResult;
import com.billy.cc.core.ipc.IPCCaller;
import com.billy.cc.core.ipc.IPCProvider;
import com.billy.cc.core.ipc.IPCRequest;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App内跨进程调用组件
 * @author billy.qi
 * @since 18/6/24 00:25
 */
class SubProcessCCInterceptor implements ICCInterceptor {

    private static final ConcurrentHashMap<String, List<String>> CONNECTIONS = new ConcurrentHashMap<>();

    //-------------------------单例模式 start --------------
    /** 单例模式Holder */
    private static class SubProcessCCInterceptorHolder {
        private static final SubProcessCCInterceptor INSTANCE = new SubProcessCCInterceptor();
    }
    SubProcessCCInterceptor(){}
    /** 获取SubProcessCCInterceptor的单例对象 */
    static SubProcessCCInterceptor getInstance() {
        return SubProcessCCInterceptorHolder.INSTANCE;
    }
    //-------------------------单例模式 end --------------

    @Override
    public CCResult intercept(Chain chain) {
        String componentName = chain.getCC().getComponentName();
        String processName = ComponentManager.getComponentProcessName(componentName);
        return multiProcessCall(chain, processName);
    }

    CCResult multiProcessCall(Chain chain, String processName) {

        CC cc = chain.getCC();
        //主线程同步调用时，跨进程也要在主线程同步调用
        boolean isMainThreadSyncCall = !cc.isAsync() && Looper.getMainLooper() == Looper.myLooper();
        ProcessCrossTask task = new ProcessCrossTask(cc, processName, isMainThreadSyncCall);
        ComponentManager.threadPool(task);
        if (!cc.isFinished()) {
            //执行 Wait4ResultInterceptor
            chain.proceed();
            //如果是提前结束的，跨进程通知被调用方
            if (cc.isCanceled()) {
                task.cancel();
            } else if (cc.isTimeout()) {
                task.timeout();
            }
        }
        return cc.getResult();
    }

    class ProcessCrossTask implements Runnable {

        private final CC cc;
        private final String processName;
        private final boolean isMainThreadSyncCall;

        ProcessCrossTask(CC cc, String processName, boolean isMainThreadSyncCall) {
            this.cc = cc;
            this.processName = processName;
            this.isMainThreadSyncCall = isMainThreadSyncCall;
        }

        @Override
        public void run() {
            RemoteCC processCrossCC = new RemoteCC(cc, isMainThreadSyncCall);
            call(processCrossCC);
        }

        private void call(RemoteCC remoteCC) {
            if (processName != null) {
                if (cc.isFinished()) {
                    CC.verboseLog(cc.getCallId(), "cc is finished before call %s process", processName);
                    return;
                }

                if (CC.VERBOSE_LOG) {
                    CC.verboseLog(cc.getCallId(), "start to call process:%s, RemoteCC: %s"
                            , processName, remoteCC.toString());
                }
                HashMap<String, Object> params = (HashMap<String, Object>)cc.getParams();
                IPCRequest request = new IPCRequest.Builder()
                        .initTask(cc.getComponentName(), cc.getActionName(), params, cc.getCallId())
                        .mainThreadSyncCall(true)
                        .build();

                IPCCaller.callAsync(cc.getContext(), processName, request, new IPCCaller.ICallback() {
                    @Override
                    public void onResult(Bundle resultBundle) {
                        setResult(resultBundle);
                    }
                });
            }

        }

        private void setResult(Bundle bundle) {
            bundle.setClassLoader(getClass().getClassLoader());
            RemoteCCResult remoteCCResult = bundle.getParcelable(IPCProvider.ARG_EXTRAS_RESULT);
            if (CC.VERBOSE_LOG) {
                CC.verboseLog(cc.getCallId(), "receive RemoteCCResult from process:%s, RemoteCCResult: %s"
                        , processName, remoteCCResult.toString());
            }
            cc.setResult4Waiting(remoteCCResult.toCCResult());
        }

        private void cancel() {
            if (processName != null) {
                IPCRequest request = CCIPCCmd.createCancelCmd(cc.getCallId());
                IPCCaller.callAsync(cc.getContext(), processName, request);
            }
        }

        private void timeout() {
            if (processName != null) {
                IPCRequest request = CCIPCCmd.createTimeoutRequest(cc.getCallId());
                IPCCaller.callAsync(cc.getContext(), processName, request);
            }
        }
    }

}
