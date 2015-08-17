package com.googlecode.android_scripting.fragment;

import android.app.Activity;
import android.app.ListFragment;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.FileUtils;
import com.googlecode.android_scripting.IntentBuilders;
import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.ScriptListAdapter;
import com.googlecode.android_scripting.ScriptStorageAdapter;
import com.googlecode.android_scripting.activity.CustomizeWindow;
import com.googlecode.android_scripting.activity.LogcatViewer;
import com.googlecode.android_scripting.activity.ScriptingLayerService;
import com.googlecode.android_scripting.activity.TriggerManager;
import com.googlecode.android_scripting.dialog.Help;
import com.googlecode.android_scripting.dialog.UsageTrackingConfirmation;
import com.googlecode.android_scripting.facade.FacadeConfiguration;
import com.googlecode.android_scripting.interpreter.Interpreter;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration.ConfigurationObserver;
import com.googlecode.android_scripting.interpreter.InterpreterConstants;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;

/**
 * Manages creation, deletion, and execution of stored scripts.
 *
 * @author Damon Kohler (damonkohler@gmail.com)
 */

public class ScriptManager extends ListFragment {

    private final static String EMPTY = "";

    Activity activity;

    private List<File> mScripts;
    private ScriptManagerAdapter mAdapter;
    private SharedPreferences mPreferences;
    private HashMap<Integer, Interpreter> mAddMenuIds;
    private ScriptListObserver mObserver;
    private InterpreterConfiguration mConfiguration;
    private SearchManager mManager;
    private boolean mInSearchResultMode = false;
    private String mQuery = EMPTY;
    private File mCurrentDir;
    private final File mBaseDir = new File(InterpreterConstants.SCRIPTS_ROOT);
    private final Handler mHandler = new Handler();
    private File mCurrent;

    private enum RequestCode {
        INSTALL_INTERPRETER, QRCODE_ADD
    }

    private enum MenuId {
        DELETE, HELP, FOLDER_ADD, QRCODE_ADD, INTERPRETER_MANAGER, LOGCAT_VIEWER,
        TRIGGER_MANAGER, REFRESH, SEARCH, RENAME, EXTERNAL;
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
        return inflater.inflate(R.layout.script_manager, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        CustomizeWindow.setToolbarTitle(activity, "Scripts", R.layout.main_activity);
        if (FileUtils.externalStorageMounted()) {
            File sl4a = mBaseDir.getParentFile();
            if (!sl4a.exists()) {
                sl4a.mkdir();
                try {
                    FileUtils.chmod(sl4a, 0755); // Handle the sl4a parent folder first.
                } catch (Exception e) {
                    // Not much we can do here if it doesn't work.
                }
            }
            if (!FileUtils.makeDirectories(mBaseDir, 0755)) {
                new AlertDialog.Builder(activity)
                        .setTitle("Error")
                        .setMessage(
                                "Failed to create scripts directory.\n" + mBaseDir + "\n"
                                        + "Please check the permissions of your external storage media.")
                        .setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton("Ok", null).show();
            }
        } else {
            new AlertDialog.Builder(activity).setTitle("External Storage Unavailable")
                    .setMessage("Scripts will be unavailable as long as external storage is unavailable.")
                    .setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton("Ok", null).show();
        }

        mCurrentDir = mBaseDir;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        mAdapter = new ScriptManagerAdapter(activity);
        mObserver = new ScriptListObserver();
        mAdapter.registerDataSetObserver(mObserver);
        mConfiguration = ((BaseApplication) activity.getApplication()).getInterpreterConfiguration();
        mManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);

        registerForContextMenu(getListView());
        updateAndFilterScriptList(mQuery);
        setListAdapter(mAdapter);
        //ActivityFlinger.attachView(getListView(), activity);
        //ActivityFlinger.attachView(activity.getWindow().getDecorView(), activity);
        activity.startService(IntentBuilders.buildTriggerServiceIntent());
        handleIntent(activity.getIntent());
        UsageTrackingConfirmation.show(activity);
        // Analytics.trackActivity(this);
    }

    @SuppressWarnings("serial")
    private void updateAndFilterScriptList(final String query) {
        List<File> scripts;
        if (mPreferences.getBoolean("show_all_files", false)) {
            scripts = ScriptStorageAdapter.listAllScripts(mCurrentDir);
        } else {
            scripts = ScriptStorageAdapter.listExecutableScripts(mCurrentDir, mConfiguration);
        }
        mScripts = Lists.newArrayList(Collections2.filter(scripts, new Predicate<File>() {
            @Override
            public boolean apply(File file) {
                return file.getName().toLowerCase().contains(query.toLowerCase());
            }
        }));

        synchronized (mQuery) {
            if (!mQuery.equals(query)) {
                // TODO (miguelpalacio): this calls views in the title layout, yields null.
                if (query == null || query.equals(EMPTY)) {
                    ((TextView) getView().findViewById(R.id.left_text)).setText("Scripts");
                } else {
                    ((TextView) getView().findViewById(R.id.left_text)).setText(query);
                }
                mQuery = query;
            }
        }

        if (mScripts.size() == 0) {
            ((TextView) activity.findViewById(android.R.id.empty)).setText("No matches found.");
        }

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
    }

    public void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mInSearchResultMode = true;
            String query = intent.getStringExtra(SearchManager.QUERY);
            updateAndFilterScriptList(query);
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, MenuId.RENAME.getId(), Menu.NONE, "Rename");
        menu.add(Menu.NONE, MenuId.DELETE.getId(), Menu.NONE, "Delete");
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
        File file = (File) mAdapter.getItem(info.position);
        int itemId = item.getItemId();
        if (itemId == MenuId.DELETE.getId()) {
            delete(file);
            return true;
        } else if (itemId == MenuId.RENAME.getId()) {
            rename(file);
            return true;
        }
        return false;
    }

    public boolean onKeyDown(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mInSearchResultMode) {
            mInSearchResultMode = false;
            mAdapter.notifyDataSetInvalidated();
            return true;
        }
        return false;
    }

    @Override
    public void onStop() {
        super.onStop();
        mConfiguration.unregisterObserver(mObserver);
    }

    @Override
    public void onStart() {
        super.onStart();
        mConfiguration.registerObserver(mObserver);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mInSearchResultMode) {
            ((TextView) getView().findViewById(android.R.id.empty)).setText(R.string.no_scripts_message);
        }
        updateAndFilterScriptList(mQuery);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // TODO (miguelpalacio): I'm calling a super method that does not correspond...
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        buildMenuIdMaps();
        buildAddMenu(menu);
        buildSwitchActivityMenu(menu);
        menu.add(Menu.NONE, MenuId.SEARCH.getId(), Menu.NONE, "Search").setIcon(
                R.drawable.ic_menu_search);
        menu.add(Menu.NONE, MenuId.REFRESH.getId(), Menu.NONE, "Refresh").setIcon(
                R.drawable.ic_menu_refresh);
        menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
                android.R.drawable.ic_menu_help);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void buildSwitchActivityMenu(Menu menu) {
        Menu subMenu =
                menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, "View").setIcon(
                        android.R.drawable.ic_menu_more);
        subMenu.add(Menu.NONE, MenuId.INTERPRETER_MANAGER.getId(), Menu.NONE, "Interpreters");
        subMenu.add(Menu.NONE, MenuId.TRIGGER_MANAGER.getId(), Menu.NONE, "Triggers");
        subMenu.add(Menu.NONE, MenuId.LOGCAT_VIEWER.getId(), Menu.NONE, "Logcat");
    }

    private void buildMenuIdMaps() {
        mAddMenuIds = new LinkedHashMap<>();
        int i = MenuId.values().length + Menu.FIRST;
        List<Interpreter> installed = mConfiguration.getInstalledInterpreters();
        Collections.sort(installed, new Comparator<Interpreter>() {
            @Override
            public int compare(Interpreter interpreterA, Interpreter interpreterB) {
                return interpreterA.getNiceName().compareTo(interpreterB.getNiceName());
            }
        });
        for (Interpreter interpreter : installed) {
            mAddMenuIds.put(i, interpreter);
            ++i;
        }
    }

    private void buildAddMenu(Menu menu) {
        Menu addMenu =
                menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, "Add").setIcon(
                        android.R.drawable.ic_menu_add);
        addMenu.add(Menu.NONE, MenuId.FOLDER_ADD.getId(), Menu.NONE, "Folder");
        for (Entry<Integer, Interpreter> entry : mAddMenuIds.entrySet()) {
            addMenu.add(Menu.NONE, entry.getKey(), Menu.NONE, entry.getValue().getNiceName());
        }
        addMenu.add(Menu.NONE, MenuId.QRCODE_ADD.getId(), Menu.NONE, "Scan Barcode");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == MenuId.HELP.getId()) {
            Help.show(activity);
        } else if (itemId == MenuId.INTERPRETER_MANAGER.getId()) {
            // Show interpreter manger.
            Intent i = new Intent(activity, InterpreterManager.class);
            startActivity(i);
        } else if (mAddMenuIds.containsKey(itemId)) {
            // Add a new script.
            Intent intent = new Intent(Constants.ACTION_EDIT_SCRIPT);
            Interpreter interpreter = mAddMenuIds.get(itemId);
            intent.putExtra(Constants.EXTRA_SCRIPT_PATH,
                    new File(mCurrentDir.getPath(), interpreter.getExtension()).getPath());
            intent.putExtra(Constants.EXTRA_SCRIPT_CONTENT, interpreter.getContentTemplate());
            intent.putExtra(Constants.EXTRA_IS_NEW_SCRIPT, true);
            startActivity(intent);
            synchronized (mQuery) {
                mQuery = EMPTY;
            }
        } else if (itemId == MenuId.QRCODE_ADD.getId()) {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            startActivityForResult(intent, RequestCode.QRCODE_ADD.ordinal());
        } else if (itemId == MenuId.FOLDER_ADD.getId()) {
            addFolder();
        } else if (itemId == MenuId.TRIGGER_MANAGER.getId()) {
            startActivity(new Intent(activity, TriggerManager.class));
        } else if (itemId == MenuId.LOGCAT_VIEWER.getId()) {
            startActivity(new Intent(activity, LogcatViewer.class));
        } else if (itemId == MenuId.REFRESH.getId()) {
            updateAndFilterScriptList(mQuery);
            mAdapter.notifyDataSetChanged();
        } else if (itemId == MenuId.SEARCH.getId()) {
            activity.onSearchRequested();
        }
        return true;
    }

    @Override
    public void onListItemClick(ListView list, View view, int position, long id) {
        final File file = (File) list.getItemAtPosition(position);
        mCurrent = file;
        if (file.isDirectory()) {
            mCurrentDir = file;
            mAdapter.notifyDataSetInvalidated();
            return;
        }
        if (FacadeConfiguration.getSdkLevel() <= 3 || !mPreferences.getBoolean("use_quick_menu", true)) {
            doDialogMenu();
            return;
        }

        final QuickAction actionMenu = new QuickAction(view);

        ActionItem terminal = new ActionItem();
        terminal.setIcon(getResources().getDrawable(R.drawable.terminal));
        terminal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, ScriptingLayerService.class);
                intent.setAction(Constants.ACTION_LAUNCH_FOREGROUND_SCRIPT);
                intent.putExtra(Constants.EXTRA_SCRIPT_PATH, file.getPath());
                activity.startService(intent);
                dismissQuickActions(actionMenu);
            }
        });

        final ActionItem background = new ActionItem();
        background.setIcon(getResources().getDrawable(R.drawable.background));
        background.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, ScriptingLayerService.class);
                intent.setAction(Constants.ACTION_LAUNCH_BACKGROUND_SCRIPT);
                intent.putExtra(Constants.EXTRA_SCRIPT_PATH, file.getPath());
                activity.startService(intent);
                dismissQuickActions(actionMenu);
            }
        });

        final ActionItem edit = new ActionItem();
        edit.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_edit));
        edit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                editScript(file);
                dismissQuickActions(actionMenu);
            }
        });

        final ActionItem delete = new ActionItem();
        delete.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_delete));
        delete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                delete(file);
                dismissQuickActions(actionMenu);
            }
        });

        final ActionItem rename = new ActionItem();
        rename.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_save));
        rename.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                rename(file);
                dismissQuickActions(actionMenu);
            }
        });

        final ActionItem external = new ActionItem();
        external.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_directions));
        external.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                externalEditor(file);
                dismissQuickActions(actionMenu);
            }
        });

        actionMenu.addActionItems(terminal, background, edit, rename, delete, external);
        actionMenu.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);
        actionMenu.show();
    }

    // Quickedit chokes on sdk 3 or below, and some Android builds. Provides alternative menu.
    private void doDialogMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final CharSequence[] menuList =
                { "Run Foreground", "Run Background", "Edit", "Delete", "Rename", "External Editor" };
        builder.setTitle(mCurrent.getName());
        builder.setItems(menuList, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent;
                switch (which) {
                    case 0:
                        intent = new Intent(activity, ScriptingLayerService.class);
                        intent.setAction(Constants.ACTION_LAUNCH_FOREGROUND_SCRIPT);
                        intent.putExtra(Constants.EXTRA_SCRIPT_PATH, mCurrent.getPath());
                        activity.startService(intent);
                        break;
                    case 1:
                        intent = new Intent(activity, ScriptingLayerService.class);
                        intent.setAction(Constants.ACTION_LAUNCH_BACKGROUND_SCRIPT);
                        intent.putExtra(Constants.EXTRA_SCRIPT_PATH, mCurrent.getPath());
                        activity.startService(intent);
                        break;
                    case 2:
                        editScript(mCurrent);
                        break;
                    case 3:
                        delete(mCurrent);
                        break;
                    case 4:
                        rename(mCurrent);
                        break;
                    case 5:
                        externalEditor(mCurrent);
                        break;
                }
            }
        });
        builder.show();
    }

    protected void externalEditor(File file) {
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setDataAndType(Uri.fromFile(file), "text/plain");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to open external editor\n" + e.toString(), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void dismissQuickActions(final QuickAction action) {
        // HACK(damonkohler): Delay the dismissal to avoid an otherwise noticeable flicker.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                action.dismiss();
            }
        }, 1);
    }

    /**
     * Opens the script for editing.
     *
     * @param script
     *          the name of the script to edit
     */
    private void editScript(File script) {
        Intent i = new Intent(Constants.ACTION_EDIT_SCRIPT);
        i.putExtra(Constants.EXTRA_SCRIPT_PATH, script.getAbsolutePath());
        startActivity(i);
    }

    private void delete(final File file) {
        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setTitle("Delete");
        alert.setMessage("Would you like to delete " + file.getName() + "?");
        alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                FileUtils.delete(file);
                mScripts.remove(file);
                mAdapter.notifyDataSetChanged();
            }
        });
        alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Ignore.
            }
        });
        alert.show();
    }

    private void addFolder() {
        final EditText folderName = new EditText(activity);
        folderName.setHint("Folder Name");
        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setTitle("Add Folder");
        alert.setView(folderName);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String name = folderName.getText().toString();
                if (name.length() == 0) {
                    Log.e(activity, "Folder name is empty.");
                    return;
                } else {
                    for (File f : mScripts) {
                        if (f.getName().equals(name)) {
                            Log.e(activity, String.format("Folder \"%s\" already exists.", name));
                            return;
                        }
                    }
                }
                File dir = new File(mCurrentDir, name);
                if (!FileUtils.makeDirectories(dir, 0755)) {
                    Log.e(activity, String.format("Cannot create folder \"%s\".", name));
                }
                mAdapter.notifyDataSetInvalidated();
            }
        });
        alert.show();
    }

    private void rename(final File file) {
        final EditText newName = new EditText(activity);
        newName.setText(file.getName());
        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setTitle("Rename");
        alert.setView(newName);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String name = newName.getText().toString();
                if (name.length() == 0) {
                    Log.e(activity, "Name is empty.");
                    return;
                } else {
                    for (File f : mScripts) {
                        if (f.getName().equals(name)) {
                            Log.e(activity, String.format("\"%s\" already exists.", name));
                            return;
                        }
                    }
                }
                if (!FileUtils.rename(file, name)) {
                    throw new RuntimeException(String.format("Cannot rename \"%s\".", file.getPath()));
                }
                mAdapter.notifyDataSetInvalidated();
            }
        });
        alert.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        RequestCode request = RequestCode.values()[requestCode];
        if (resultCode == activity.RESULT_OK) {
            switch (request) {
                case QRCODE_ADD:
                    writeScriptFromBarcode(data);
                    break;
                default:
                    break;
            }
        } else {
            switch (request) {
                case QRCODE_ADD:
                    break;
                default:
                    break;
            }
        }
        mAdapter.notifyDataSetInvalidated();
    }

    private void writeScriptFromBarcode(Intent data) {
        String result = data.getStringExtra("SCAN_RESULT");
        if (result == null) {
            Log.e(activity, "Invalid QR code content.");
            return;
        }
        String contents[] = result.split("\n", 2);
        if (contents.length != 2) {
            Log.e(activity, "Invalid QR code content.");
            return;
        }
        String title = contents[0];
        String body = contents[1];
        File script = new File(mCurrentDir, title);
        ScriptStorageAdapter.writeScript(script, body);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mConfiguration.unregisterObserver(mObserver);
        mManager.setOnCancelListener(null);
    }

    private class ScriptListObserver extends DataSetObserver implements ConfigurationObserver {
        @Override
        public void onInvalidated() {
            updateAndFilterScriptList(EMPTY);
        }

        @Override
        public void onConfigurationChanged() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateAndFilterScriptList(mQuery);
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private class ScriptManagerAdapter extends ScriptListAdapter {
        public ScriptManagerAdapter(Context context) {
            super(context);
        }

        @Override
        protected List<File> getScriptList() {
            return mScripts;
        }
    }

}
