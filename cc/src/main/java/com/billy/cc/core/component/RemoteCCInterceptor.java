package com.billy.cc.core.component;

import android.text.TextUtils;

import com.billy.cc.core.component.remote.RemoteComponentManager;

/**
 * 跨App调用组件
 * 继承自 {@link SubProcessCCInterceptor}, 额外处理了跨App的进程连接
 * @author billy.qi
 * @since 18/6/24 00:25
 */
class RemoteCCInterceptor extends SubProcessCCInterceptor {

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
        String processName = RemoteComponentManager.getInstance().getPkgName(chain.getCC().getComponentName());
        if (!TextUtils.isEmpty(processName)) {
            return multiProcessCall(chain, processName);
        }
        return CCResult.error(CCResult.CODE_ERROR_NO_COMPONENT_FOUND);
    }


    void enableRemoteCC() {
        //监听设备上其它包含CC组件的app
        RemoteComponentManager.getInstance().enableRemote();
    }
}
