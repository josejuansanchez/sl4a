/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.android_scripting.activity;

import android.support.v7.app.ActionBar;
import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;

import com.googlecode.android_scripting.R;

public class CustomizeWindow {
    private CustomizeWindow() {
        // Utility class.
    }

    // TODO (miguelpalacio): this class hasn't been correctly redefined.
    public static void setToolbarTitle(Activity activity, String title, int contentViewLayoutResId) {
        // Temporary if, all will be using Toolbar.
        if (activity instanceof AppCompatActivity) {

            ActionBar mActionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (mActionBar != null) {
                mActionBar.setTitle(title);
/*            mActionBar.setSubtitle("SL4A r" + Version.getVersion(activity));*/
            }
        } else {
            // This was commented out because of problems with Android Lollipop.
/*        activity.requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);*/
            activity.setContentView(contentViewLayoutResId);
            activity.setTitle("SL4A - " + title);
/*        activity.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title);
        ((TextView) activity.findViewById(R.id.left_text)).setText(title);
        ((TextView) activity.findViewById(R.id.right_text)).setText("SL4A r"
                + Version.getVersion(activity));*/

        }
    }

    public static void setToolbarTitle(Activity activity, String title) {
        if (activity instanceof AppCompatActivity) {
            ActionBar mActionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (mActionBar != null) {
                mActionBar.setTitle(title);
            }
        }
    }

    public static void toggleProgressBarVisibility(Activity activity, boolean on) {
        ((ProgressBar) activity.findViewById(R.id.progress_bar)).setVisibility(on ? View.VISIBLE
                : View.GONE);
    }
}
