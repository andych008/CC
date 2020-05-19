package com.billy.cc.core.component;

import android.os.Bundle;

import com.billy.cc.core.ipc.IPCRequest;

/**
 * 业务方请求对象扩展相关
 *
 * @author 喵叔catuncle    2020/5/20 0020
 */
public class CCIPCRequest {
    public static final String CMD_ACTION_GET_COMPONENT_LIST = "cmd_action_get_component_list";
    public static final String CMD_ACTION_CANCEL = "cmd_action_cancel";
    public static final String CMD_ACTION_TIMEOUT = "cmd_action_timeout";
    public static final String EXTRAS_IS_CMD = "isCmd";


    public static IPCRequest createGetComponentListCmd() {
        return cmd(CMD_ACTION_GET_COMPONENT_LIST, "nil");
    }

    public static IPCRequest createCancelCmd(String callId) {
        return cmd(CMD_ACTION_CANCEL, callId);
    }

    public static IPCRequest createTimeoutRequest(String callId) {
        return cmd(CMD_ACTION_TIMEOUT, callId);
    }

    private static IPCRequest cmd(String actionName, String callId) {
        Bundle extra = new Bundle();
        extra.putBoolean(EXTRAS_IS_CMD, true);
        return new IPCRequest.Builder()
                .initTask(null, actionName, null, callId)
                .extra(extra)
                .build();
    }


}
