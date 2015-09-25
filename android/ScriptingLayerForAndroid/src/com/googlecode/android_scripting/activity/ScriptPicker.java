/*
 * Copyright (C) 2010 Google Inc.
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
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.FeaturedInterpreters;
import com.googlecode.android_scripting.IntentBuilders;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.ScriptStorageAdapter;
import com.googlecode.android_scripting.custom_component.item_lists.ScriptListAdapter;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration;
import com.googlecode.android_scripting.interpreter.InterpreterConstants;

import java.io.File;
import java.util.List;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;

/**
 * Presents available scripts and returns the selected one.
 *
 * @author Damon Kohler (damonkohler@gmail.com)
 */
public class ScriptPicker extends AppCompatActivity implements ScriptListAdapter.ViewHolder.ClickListener {

    Toolbar toolbar;
    ActionBar mActionBar;

    RecyclerView scriptListView;
    RecyclerView.LayoutManager scriptListLayoutManager;
    ScriptListAdapter scriptListAdapter;

    TextView noScriptsMessage;

    private List<File> mScripts;
    private InterpreterConfiguration mConfiguration;
    private File mCurrentDir;
    private final File mBaseDir = new File(InterpreterConstants.SCRIPTS_ROOT);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.script_picker);

        // Toolbar.
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mActionBar = getSupportActionBar();
        if (mActionBar != null) {
            mActionBar.setHomeButtonEnabled(true);
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Since Status Bar is transparent in styles.xml, set its color.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }

        CustomizeWindow.setToolbarTitle(this, "Scripts", R.layout.main_activity);
        mCurrentDir = mBaseDir;
        mConfiguration = ((BaseApplication) getApplication()).getInterpreterConfiguration();
        mScripts = ScriptStorageAdapter.listExecutableScripts(null, mConfiguration);

        noScriptsMessage = (TextView) findViewById(R.id.script_list_empty);

        scriptListView = (RecyclerView) findViewById(R.id.script_list);
        scriptListAdapter = new ScriptListAdapter(this, mScripts, this);
        scriptListView.setAdapter(scriptListAdapter);
        scriptListLayoutManager = new LinearLayoutManager(this);
        scriptListView.setLayoutManager(scriptListLayoutManager);

        if (mScripts.size() == 0) {
            scriptListView.setVisibility(View.GONE);
            noScriptsMessage.setVisibility(View.VISIBLE);
        }
        // Analytics.trackActivity(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // Make Home button behave as hardware Back button.
        if (id == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(int position) {

        final File script = mScripts.get(position);

        if (script.isDirectory()) {
            mCurrentDir = script;

            mScripts = ScriptStorageAdapter.listExecutableScripts(mCurrentDir, mConfiguration);
            // TODO(damonkohler): Extending the File class here seems odd.
            if (!mCurrentDir.equals(mBaseDir)) {
                mScripts.add(0, new File(mCurrentDir.getParent()) {
                    @Override
                    public boolean isDirectory() {
                        return true;
                    }

                    @Override
                    public String getName() {
                        return "..";
                    }
                });
            }
            scriptListAdapter.setmScripts(mScripts);
            scriptListAdapter.notifyDataSetChanged();
            return;
        }

        // "position + 1" because LayoutManager also takes into consideration the header.
        final QuickAction actionMenu =
                new QuickAction(scriptListLayoutManager.findViewByPosition(position + 1));

        ActionItem terminal = new ActionItem();
        terminal.setIcon(getResources().getDrawable(R.drawable.terminal));
        ActionItem background = new ActionItem();
        background.setIcon(getResources().getDrawable(R.drawable.background));

        actionMenu.addActionItems(terminal, background);

        if (Intent.ACTION_PICK.equals(getIntent().getAction())) {
            terminal.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = IntentBuilders.buildStartInTerminalIntent(script);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });

            background.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = IntentBuilders.buildStartInBackgroundIntent(script);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            int icon = FeaturedInterpreters.getInterpreterIcon(ScriptPicker.this, script.getName());
            if (icon == 0) {
                icon = R.drawable.sl4a_logo_48;
            }

            final Parcelable iconResource =
                    Intent.ShortcutIconResource.fromContext(ScriptPicker.this, icon);

            terminal.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = IntentBuilders.buildTerminalShortcutIntent(script, iconResource);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });

            background.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = IntentBuilders.buildBackgroundShortcutIntent(script, iconResource);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
        } else if (com.twofortyfouram.locale.platform.Intent.ACTION_EDIT_SETTING.equals(getIntent()
                .getAction())) {
            final Intent intent = new Intent();
            final Bundle storeAndForwardExtras = new Bundle();
            storeAndForwardExtras.putString(Constants.EXTRA_SCRIPT_PATH, script.getPath());

            intent.putExtra(com.twofortyfouram.locale.platform.Intent.EXTRA_STRING_BLURB,
                    script.getName());

            terminal.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    storeAndForwardExtras.putBoolean(Constants.EXTRA_LAUNCH_IN_BACKGROUND, false);
                    intent.putExtra(com.twofortyfouram.locale.platform.Intent.EXTRA_BUNDLE,
                            storeAndForwardExtras);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });

            background.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    storeAndForwardExtras.putBoolean(Constants.EXTRA_LAUNCH_IN_BACKGROUND, true);
                    intent.putExtra(com.twofortyfouram.locale.platform.Intent.EXTRA_BUNDLE,
                            storeAndForwardExtras);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
        }

        actionMenu.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);
        actionMenu.show();
    }

    @Override
    public boolean onItemLongClick(int position) {
        return false;
    }
}
