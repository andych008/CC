package com.billy.cc.core.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

/**
 * IPC请求对象
 *
 * @author billy.qi
 * @since 18/6/24 11:29
 */
public class IPCRequest implements Parcelable {
    public static final String CMD_ACTION_GET_COMPONENT_LIST = "cmd_action_get_component_list";
    public static final String CMD_ACTION_CANCEL = "cmd_action_cancel";
    public static final String CMD_ACTION_TIMEOUT = "cmd_action_timeout";

    private String componentName;
    private String actionName;
    private HashMap<String, Object> params;
    private String callId;
    private boolean isMainThreadSyncCall;
    private boolean isCmd = false;

    public static IPCRequest createGetComponentListRequest() {
        return new IPCRequest(CMD_ACTION_GET_COMPONENT_LIST);
    }

    public static IPCRequest createCancelRequest(String callId) {
        return new IPCRequest(CMD_ACTION_CANCEL, callId);
    }

    public static IPCRequest createTimeoutRequest(String callId) {
        return new IPCRequest(CMD_ACTION_TIMEOUT, callId);
    }

    private IPCRequest(String actionName) {
        this.actionName = actionName;
        this.isCmd = true;
    }

    private IPCRequest(String actionName, String callId) {
        this.actionName = actionName;
        this.callId = callId;
        this.isCmd = true;
    }

    public IPCRequest(String componentName, String actionName, HashMap<String, Object> params, String callId) {
        this.componentName = componentName;
        this.actionName = actionName;
        this.params = params;
        this.callId = callId;
    }

    public IPCRequest(String componentName, String actionName, HashMap<String, Object> params, String callId, boolean isMainThreadSyncCall) {
        this(componentName, actionName, params, callId);
        this.isMainThreadSyncCall = isMainThreadSyncCall;
    }

    protected IPCRequest(Parcel in) {
        componentName = in.readString();
        actionName = in.readString();
        callId = in.readString();
        isMainThreadSyncCall = in.readByte() != 0;
        isCmd = in.readByte() != 0;
        params = (HashMap<String, Object>) in.readSerializable();
    }

    @Override
    public String toString() {
        return "IPCRequest{" +
                "componentName='" + componentName + '\'' +
                ", actionName='" + actionName + '\'' +
                ", params=" + params +
                ", callId='" + callId + '\'' +
                ", isMainThreadSyncCall=" + isMainThreadSyncCall +
                ", isCmd=" + isCmd +
                '}';
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(componentName);
        dest.writeString(actionName);
        dest.writeString(callId);
        dest.writeByte((byte) (isMainThreadSyncCall ? 1 : 0));
        dest.writeByte((byte) (isCmd ? 1 : 0));
        dest.writeSerializable(params);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<IPCRequest> CREATOR = new Creator<IPCRequest>() {
        @Override
        public IPCRequest createFromParcel(Parcel in) {
            return new IPCRequest(in);
        }

        @Override
        public IPCRequest[] newArray(int size) {
            return new IPCRequest[size];
        }
    };

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public HashMap<String, Object> getParams() {
        return params;
    }

    public void setParams(HashMap<String, Object> params) {
        this.params = params;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public boolean isMainThreadSyncCall() {
        return isMainThreadSyncCall;
    }

    public void setMainThreadSyncCall(boolean mainThreadSyncCall) {
        isMainThreadSyncCall = mainThreadSyncCall;
    }

    public boolean isCmd() {
        return isCmd;
    }

    public void setCmd(boolean cmd) {
        isCmd = cmd;
    }
}
