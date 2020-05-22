package com.billy.cc.core.remote.inner;

import android.app.Activity;
import android.os.Bundle;

/**
 * 用于跨app探索组件及唤醒app的activity
 *
 * @author billy.qi
 * @since 18/7/2 23:38
 */
public class RemoteConnectionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
