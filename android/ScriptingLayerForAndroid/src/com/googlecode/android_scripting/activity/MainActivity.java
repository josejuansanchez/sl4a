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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.fragment.NavigationDrawer;
import com.googlecode.android_scripting.fragment.ScriptManager;

/**
 * Activity that holds the ScriptManager, InterpreterManager, TriggerManager,
 * and LogcatViewer fragments.
 *
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */

public class MainActivity extends AppCompatActivity {

    static String SCRIPTS_FRAGMENT = "scriptsFragment";

    Toolbar toolbar;
    ActionBar mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        // Toolbar.
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mActionBar = getSupportActionBar();
        if (mActionBar != null) {
            mActionBar.setHomeButtonEnabled(true);
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Navigation drawer.
        NavigationDrawer mDrawer = (NavigationDrawer)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer_fragment);
        mDrawer.setUp((DrawerLayout) findViewById(R.id.drawer_layout),
                R.id.navigation_drawer_fragment, toolbar);


        // Display Settings fragment.
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new ScriptManager(), SCRIPTS_FRAGMENT)
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        ((ScriptManager) getFragmentManager().findFragmentByTag(SCRIPTS_FRAGMENT)).handleIntent(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return (((ScriptManager) getFragmentManager().findFragmentByTag(SCRIPTS_FRAGMENT))
                .onKeyDown(keyCode)) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.terminal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        //int id = item.getItemId();

        //noinspection SimplifiableIfStatement

/*        // Open the Settings activity.
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        // Open the FAQ activity.
        else if (id == R.id.action_faq) {
            return true;
        }*/

        return super.onOptionsItemSelected(item);
    }

}
