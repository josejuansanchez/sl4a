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
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.activity.CustomizeWindow;
import com.googlecode.android_scripting.activity.ScriptPicker;
import com.googlecode.android_scripting.custom_component.item_lists.TriggerListAdapter;
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

public class TriggerManager extends Fragment implements TriggerListAdapter.ViewHolder.ClickListener {

    private final List<ScriptTrigger> mTriggers = Lists.newArrayList();
    private TriggerRepository mTriggerRepository;

    Activity activity;

    RecyclerView triggerListView;
    RecyclerView.LayoutManager triggerListLayoutManager;
    TriggerListAdapter triggerListAdapter;

    TextView noTriggersMessage;

    private enum MenuId {
        ADD, HELP;
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

        if (getView() != null) {
            noTriggersMessage = (TextView) getView().findViewById(R.id.script_list_empty);
        }

        triggerListView = (RecyclerView) getActivity().findViewById(R.id.script_list);
        triggerListAdapter = new TriggerListAdapter(mTriggers, this);
        triggerListView.setAdapter(triggerListAdapter);
        triggerListLayoutManager = new LinearLayoutManager(getActivity());
        triggerListView.setLayoutManager(triggerListLayoutManager);

        registerForContextMenu(triggerListView);
        mTriggerRepository = ((BaseApplication) activity.getApplication()).getTriggerRepository();
        mTriggerRepository.bootstrapObserver(observer);
        //ActivityFlinger.attachView(getListView(), activity);
        //ActivityFlinger.attachView(activity.getWindow().getDecorView(), activity);
        // Analytics.trackActivity(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        menu.add(Menu.NONE, MenuId.ADD.getId(), Menu.NONE, "Add").setIcon(
                android.R.drawable.ic_menu_add);
        menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
                android.R.drawable.ic_menu_help);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == MenuId.HELP.getId()) {
            Help.show(activity);
        } else if (itemId != Menu.NONE) {
            Intent intent = new Intent(activity, ScriptPicker.class);
            intent.setAction(Intent.ACTION_PICK);
            startActivityForResult(intent, itemId);
        }
        return true;
    }

    @Override
    public boolean onItemLongClick(final int position) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setItems(R.array.context_menu_triggers, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                if (which == 0) {
                    Trigger trigger = mTriggers.get(position);
                    mTriggerRepository.remove(trigger);
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();

        return true;
    }

    private class ScriptTriggerListObserver implements TriggerRepositoryObserver {

        @Override
        public void onPut(Trigger trigger) {
            mTriggers.add((ScriptTrigger) trigger);
            triggerListAdapter.notifyDataSetChanged();
            // Show list of triggers.
            if (triggerListView.getVisibility() == View.GONE) {
                noTriggersMessage.setVisibility(View.GONE);
                triggerListView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onRemove(Trigger trigger) {
            mTriggers.remove(trigger);
            triggerListAdapter.notifyDataSetChanged();
            // Show "no triggers" message.
            if (mTriggers.size() == 0) {
                noTriggersMessage.setVisibility(View.VISIBLE);
                triggerListView.setVisibility(View.GONE);
            }
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