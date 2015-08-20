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
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.Process;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.activity.CustomizeWindow;
import com.googlecode.android_scripting.custom_component.item_lists.LogcatListAdapter;
import com.googlecode.android_scripting.dialog.Help;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class LogcatViewer extends Fragment {

    private List<String> mLogcatMessages;
    private int mOldLastPosition;
    private Process mLogcatProcess;

    Activity activity;

    RecyclerView logcatListView;
    RecyclerView.LayoutManager logcatListLayoutManager;
    LogcatListAdapter logcatListAdapter;

    TextView emptyLogcatMessage;

    private enum MenuId {
        HELP, JUMP_TO_BOTTOM, SHARE, COPY;
        public int getId() {
            return ordinal() + Menu.FIRST;
        }
    }

    private class LogcatWatcher implements Runnable {
        @Override
        public void run() {
            mLogcatProcess = new Process();
            mLogcatProcess.setBinary(new File("/system/bin/logcat"));
            mLogcatProcess.start(null);
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(mLogcatProcess.getIn()));
                while (true) {
                    final String line = br.readLine();
                    if (line == null) {
                        break;
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLogcatMessages.add(line);
                            emptyLogcatMessage.setVisibility(View.GONE);
                            logcatListAdapter.notifyDataSetChanged();
                            // This logic performs what transcriptMode="normal" should do. Since that doesn't seem
                            // to work, we do it this way.
                            LinearLayoutManager llm = ((LinearLayoutManager) logcatListView.getLayoutManager());
                            int lastVisiblePosition = llm.findLastVisibleItemPosition();
                            int lastPosition = mLogcatMessages.size() - 1;
                            if (lastVisiblePosition == mOldLastPosition || lastVisiblePosition == -1) {
                                llm.scrollToPosition(lastPosition);
                            }
                            mOldLastPosition = lastPosition;
                        }
                    });
                }
            } catch (IOException e) {
                Log.e("Failed to read from logcat process.", e);
            } finally {
                mLogcatProcess.kill();
            }
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
        return inflater.inflate(R.layout.logcat_viewer, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = getActivity();
        CustomizeWindow.setToolbarTitle(activity, "Logcat", R.layout.logcat_viewer);
        mLogcatMessages = new LinkedList<>();
        mOldLastPosition = 0;

        if (getView() != null) {
            emptyLogcatMessage = (TextView) getView().findViewById(R.id.logcat_list_empty);
        }

        logcatListView = (RecyclerView) getActivity().findViewById(R.id.logcat_list);
        logcatListAdapter = new LogcatListAdapter(mLogcatMessages);
        logcatListView.setAdapter(logcatListAdapter);
        logcatListLayoutManager = new LinearLayoutManager(getActivity());
        logcatListView.setLayoutManager(logcatListLayoutManager);

        //ActivityFlinger.attachView(getListView(), this);
        //ActivityFlinger.attachView(activity.getWindow().getDecorView(), this);
        // Analytics.trackActivity(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        menu.add(Menu.NONE, MenuId.JUMP_TO_BOTTOM.getId(), Menu.NONE, "Jump to Bottom").setIcon(
                android.R.drawable.ic_menu_revert);
        menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(Menu.NONE, MenuId.SHARE.getId(), Menu.NONE, "Share").setIcon(
                android.R.drawable.ic_menu_share);
        menu.add(Menu.NONE, MenuId.COPY.getId(), Menu.NONE, "Copy").setIcon(
                android.R.drawable.ic_menu_edit);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private String getAsString() {
        StringBuilder builder = new StringBuilder();
        for (String message : mLogcatMessages) {
            builder.append(message + "\n");
        }
        return builder.toString();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == MenuId.HELP.getId()) {
            Help.show(activity);
        } else if (itemId == MenuId.JUMP_TO_BOTTOM.getId()) {
            logcatListView.scrollToPosition(mLogcatMessages.size() - 1);
            //getListView().setSelection(mLogcatMessages.size() - 1);
        } else if (itemId == MenuId.SHARE.getId()) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, getAsString().toString());
            intent.putExtra(Intent.EXTRA_SUBJECT, "Logcat Dump");
            intent.setType("text/plain");
            startActivity(Intent.createChooser(intent, "Send Logcat to:"));
        } else if (itemId == MenuId.COPY.getId()) {
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(getAsString());
            Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        mLogcatMessages.clear();
        Thread logcatWatcher = new Thread(new LogcatWatcher());
        logcatWatcher.setPriority(Thread.NORM_PRIORITY - 1);
        logcatWatcher.start();
        logcatListAdapter.notifyDataSetChanged();
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        mLogcatProcess.kill();
    }
}