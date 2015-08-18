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

package com.googlecode.android_scripting.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.activity.CustomizeWindow;
import com.googlecode.android_scripting.activity.Preferences;
import com.googlecode.android_scripting.activity.ScriptPicker;
import com.googlecode.android_scripting.dialog.Help;
import com.googlecode.android_scripting.facade.FacadeConfiguration;
import com.googlecode.android_scripting.rpc.MethodDescriptor;
import com.googlecode.android_scripting.trigger.ScriptTrigger;
import com.googlecode.android_scripting.trigger.Trigger;
import com.googlecode.android_scripting.trigger.TriggerRepository;
import com.googlecode.android_scripting.trigger.TriggerRepository.TriggerRepositoryObserver;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TriggerManager extends ListFragment {
    private final List<ScriptTrigger> mTriggers = Lists.newArrayList();

    private ScriptTriggerAdapter mAdapter;
    private TriggerRepository mTriggerRepository;

    Activity activity;

    private enum ContextMenuId {
        REMOVE;
        public int getId() {
            return ordinal() + Menu.FIRST;
        }
    }

    private enum MenuId {
        ADD, PREFERENCES, HELP;
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
        return inflater.inflate(R.layout.trigger_manager, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = getActivity();
        CustomizeWindow.setToolbarTitle(activity, "Triggers", R.layout.trigger_manager);
        ScriptTriggerListObserver observer = new ScriptTriggerListObserver();
        mAdapter = new ScriptTriggerAdapter();
        setListAdapter(mAdapter);
        registerForContextMenu(getListView());
        mTriggerRepository = ((BaseApplication) activity.getApplication()).getTriggerRepository();
        mTriggerRepository.bootstrapObserver(observer);
        //ActivityFlinger.attachView(getListView(), activity);
        //ActivityFlinger.attachView(activity.getWindow().getDecorView(), activity);
        // Analytics.trackActivity(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(Menu.NONE, MenuId.ADD.getId(), Menu.NONE, "Add").setIcon(
                android.R.drawable.ic_menu_add);
        menu.add(Menu.NONE, MenuId.PREFERENCES.getId(), Menu.NONE, "Preferences").setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
                android.R.drawable.ic_menu_help);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == MenuId.HELP.getId()) {
            Help.show(activity);
        } else if (itemId == MenuId.PREFERENCES.getId()) {
            startActivity(new Intent(activity, Preferences.class));
        } else if (itemId != Menu.NONE) {
            Intent intent = new Intent(activity, ScriptPicker.class);
            intent.setAction(Intent.ACTION_PICK);
            startActivityForResult(intent, itemId);
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, ContextMenuId.REMOVE.getId(), Menu.NONE, "Remove");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e("Bad menuInfo", e);
            return false;
        }

        Trigger trigger = mAdapter.getItem(info.position);
        if (trigger == null) {
            Log.v("No trigger selected.");
            return false;
        }

        if (item.getItemId() == ContextMenuId.REMOVE.getId()) {
            mTriggerRepository.remove(trigger);
        }
        return true;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mAdapter.notifyDataSetInvalidated();
    }

    private class ScriptTriggerListObserver implements TriggerRepositoryObserver {

        @Override
        public void onPut(Trigger trigger) {
            mTriggers.add((ScriptTrigger) trigger);
            mAdapter.notifyDataSetInvalidated();
        }

        @Override
        public void onRemove(Trigger trigger) {
            mTriggers.remove(trigger);
            mAdapter.notifyDataSetInvalidated();
        }
    }

    private class ScriptTriggerAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mTriggers.size();
        }

        @Override
        public Trigger getItem(int position) {
            return mTriggers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ScriptTrigger trigger = mTriggers.get(position);
            TextView textView = new TextView(activity);
            textView.setText(trigger.getEventName() + " " + trigger.getScript().getName());
            return textView;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            final File script = new File(data.getStringExtra(Constants.EXTRA_SCRIPT_PATH));
            if (requestCode == MenuId.ADD.getId()) {
                Map<String, MethodDescriptor> eventMethodDescriptors =
                        FacadeConfiguration.collectStartEventMethodDescriptors();
                final List<String> eventNames = Lists.newArrayList(eventMethodDescriptors.keySet());
                Collections.sort(eventNames);
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setItems(eventNames.toArray(new CharSequence[eventNames.size()]),
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int position) {
                                mTriggerRepository.put(new ScriptTrigger(eventNames.get(position), script));
                            }
                        });
                builder.show();
            }
        }
    }

    public void clickCancel() {
        for (Trigger t : mTriggerRepository.getAllTriggers().values()) {
            mTriggerRepository.remove(t);
        }
    }
}
