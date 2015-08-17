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

package com.googlecode.android_scripting.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.FeaturedInterpreters;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.activity.CustomizeWindow;
import com.googlecode.android_scripting.activity.Preferences;
import com.googlecode.android_scripting.activity.ScriptingLayerService;
import com.googlecode.android_scripting.dialog.Help;
import com.googlecode.android_scripting.interpreter.Interpreter;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration.ConfigurationObserver;
import com.googlecode.android_scripting.interpreter.html.HtmlInterpreter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class InterpreterManager extends ListFragment {

    private InterpreterManagerAdapter mAdapter;
    private InterpreterListObserver mObserver;
    private List<Interpreter> mInterpreters;
    private List<String> mFeaturedInterpreters;
    private InterpreterConfiguration mConfiguration;
    private SharedPreferences mPreferences;

    Activity activity;

    private enum MenuId {
        HELP, ADD, NETWORK;
        public int getId() {
            return ordinal() + Menu.FIRST;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable menu entries to receive calls.
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate layout for this fragment.
        return inflater.inflate(R.layout.interpreter_manager, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = getActivity();
        CustomizeWindow.setToolbarTitle(activity, "Interpreters", R.layout.interpreter_manager);
        mConfiguration = ((BaseApplication) activity.getApplication()).getInterpreterConfiguration();
        mInterpreters = new ArrayList<>();
        mAdapter = new InterpreterManagerAdapter();
        mObserver = new InterpreterListObserver();
        mAdapter.registerDataSetObserver(mObserver);
        setListAdapter(mAdapter);
        //ActivityFlinger.attachView(getListView(), this);
        //ActivityFlinger.attachView(getWindow().getDecorView(), this);
        mFeaturedInterpreters = FeaturedInterpreters.getList();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        // Analytics.trackActivity(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        mConfiguration.registerObserver(mObserver);
        mAdapter.notifyDataSetInvalidated();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.notifyDataSetInvalidated();
    }

    @Override
    public void onStop() {
        super.onStop();
        mConfiguration.unregisterObserver(mObserver);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        buildInstallLanguagesMenu(menu);
        menu.add(Menu.NONE, MenuId.NETWORK.getId(), Menu.NONE, "Start Server").setIcon(
                android.R.drawable.ic_menu_share);
        menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
                android.R.drawable.ic_menu_help);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void buildInstallLanguagesMenu(Menu menu) {
        SubMenu installMenu =
                menu.addSubMenu(Menu.NONE, MenuId.ADD.getId(), Menu.NONE, "Add").setIcon(
                        android.R.drawable.ic_menu_add);
        int i = MenuId.values().length + Menu.FIRST;
        for (String interpreterName : mFeaturedInterpreters) {
            installMenu.add(Menu.NONE, i++, Menu.NONE, interpreterName);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == MenuId.HELP.getId()) {
            Help.show(activity);
        } else if (itemId == MenuId.NETWORK.getId()) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
            dialog.setItems(new CharSequence[] { "Public", "Private" }, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    launchService(which == 0 /* usePublicIp */);
                }
            });
            dialog.show();
        } else if (itemId >= MenuId.values().length + Menu.FIRST) {
            int i = itemId - MenuId.values().length - Menu.FIRST;
            if (i < mFeaturedInterpreters.size()) {
                URL url = FeaturedInterpreters.getUrlForName(mFeaturedInterpreters.get(i));
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()));
                startActivity(viewIntent);
            }
        }
        return true;
    }

    private int getPrefInt(String key, int defaultValue) {
        int result = defaultValue;
        String value = mPreferences.getString(key, null);
        if (value != null) {
            try {
                result = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                result = defaultValue;
            }
        }
        return result;
    }

    private void launchService(boolean usePublicIp) {
        Intent intent = new Intent(activity, ScriptingLayerService.class);
        intent.setAction(Constants.ACTION_LAUNCH_SERVER);
        intent.putExtra(Constants.EXTRA_USE_EXTERNAL_IP, usePublicIp);
        intent.putExtra(Constants.EXTRA_USE_SERVICE_PORT, getPrefInt("use_service_port", 0));
        activity.startService(intent);
    }

    private void launchTerminal(Interpreter interpreter) {
        if (interpreter instanceof HtmlInterpreter) {
            return;
        }
        Intent intent = new Intent(activity, ScriptingLayerService.class);
        intent.setAction(Constants.ACTION_LAUNCH_INTERPRETER);
        intent.putExtra(Constants.EXTRA_INTERPRETER_NAME, interpreter.getName());
        activity.startService(intent);
    }

    @Override
    public void onListItemClick(ListView list, View view, int position, long id) {
        Interpreter interpreter = (Interpreter) list.getItemAtPosition(position);
        launchTerminal(interpreter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mConfiguration.unregisterObserver(mObserver);
    }

    private class InterpreterListObserver extends DataSetObserver implements ConfigurationObserver {
        @Override
        public void onInvalidated() {
            mInterpreters = mConfiguration.getInteractiveInterpreters();
        }

        @Override
        public void onChanged() {
            mInterpreters = mConfiguration.getInteractiveInterpreters();
        }

        @Override
        public void onConfigurationChanged() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private class InterpreterManagerAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mInterpreters.size();
        }

        @Override
        public Object getItem(int position) {
            return mInterpreters.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout container;

            Interpreter interpreter = mInterpreters.get(position);

            if (convertView == null) {
                LayoutInflater inflater =
                        (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                container = (LinearLayout) inflater.inflate(R.layout.list_item, null);
            } else {
                container = (LinearLayout) convertView;
            }
            ImageView img = (ImageView) container.findViewById(R.id.list_item_icon);

            int imgId =
                    FeaturedInterpreters.getInterpreterIcon(activity, interpreter.getExtension());
            if (imgId == 0) {
                imgId = R.drawable.sl4a_logo_32;
            }

            img.setImageResource(imgId);

            TextView text = (TextView) container.findViewById(R.id.list_item_title);

            text.setText(interpreter.getNiceName());
            return container;
        }
    }
}
