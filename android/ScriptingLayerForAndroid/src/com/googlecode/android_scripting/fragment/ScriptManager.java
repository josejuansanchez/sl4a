package com.googlecode.android_scripting.fragment;

import android.app.Activity;
import android.app.Fragment;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
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
import com.googlecode.android_scripting.ScriptStorageAdapter;
import com.googlecode.android_scripting.activity.CustomizeWindow;
import com.googlecode.android_scripting.activity.ScriptingLayerService;
import com.googlecode.android_scripting.custom_component.item_lists.ScriptListAdapter;
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

public class ScriptManager extends Fragment implements ScriptListAdapter.ViewHolder.ClickListener {

    private final static String EMPTY = "";

    Activity activity;

    RecyclerView scriptListView;
    RecyclerView.LayoutManager scriptListLayoutManager;
    ScriptListAdapter scriptListAdapter;

    TextView noScriptsMessage;

    MenuItem searchEntry;
    SearchView searchView;
    ImageView searchButton;

    private List<File> mScripts;
    //private ScriptManagerAdapterOld mAdapter;
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
        DELETE, HELP, FOLDER_ADD, QRCODE_ADD, REFRESH, SEARCH, RENAME, EXTERNAL;
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
        CustomizeWindow.setToolbarTitle(activity, getString(R.string.toolbar_title_scripts), R.layout.script_manager);
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
        mObserver = new ScriptListObserver();
        mConfiguration = ((BaseApplication) activity.getApplication()).getInterpreterConfiguration();
        mManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);

        if (getView() != null) {
            noScriptsMessage = (TextView) getView().findViewById(R.id.script_list_empty);
        }

        scriptListView = (RecyclerView) getActivity().findViewById(R.id.script_list);
        scriptListAdapter = new ScriptListAdapter(getActivity(), mScripts, this);
        scriptListView.setAdapter(scriptListAdapter);
        scriptListLayoutManager = new LinearLayoutManager(getActivity());
        scriptListView.setLayoutManager(scriptListLayoutManager);

        registerForContextMenu(scriptListView);
        updateAndFilterScriptList(mQuery);
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
                if (query == null || query.equals(EMPTY)) {
                    CustomizeWindow.setToolbarTitle(activity, getString(R.string.toolbar_title_scripts));
                } else {
                    CustomizeWindow.setToolbarTitle(activity, query);
                }
                mQuery = query;
            }
        }

        if (mScripts.size() == 0 && mCurrentDir.equals(mBaseDir)) {
            scriptListView.setVisibility(View.GONE);
            noScriptsMessage.setVisibility(View.VISIBLE);
            if (mInSearchResultMode) {
                noScriptsMessage.setText(getText(R.string.no_scripts_found));
            } else {
                noScriptsMessage.setText(getText(R.string.no_scripts_message));
            }
        } else {
            scriptListView.setVisibility(View.VISIBLE);
            noScriptsMessage.setVisibility(View.GONE);
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

        scriptListAdapter.setmScripts(mScripts);
        scriptListAdapter.notifyDataSetChanged();
    }

    public void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mInSearchResultMode = true;
            String query = intent.getStringExtra(SearchManager.QUERY);
            updateAndFilterScriptList(query);
            scriptListAdapter.setmScripts(mScripts);
            scriptListAdapter.notifyDataSetChanged();
        }
    }

/*    public void onKeyDown(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mInSearchResultMode) {
            mInSearchResultMode = false;

            scriptListAdapter.setmScripts(mScripts);
            scriptListAdapter.notifyDataSetChanged();
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK) {
            searchView.setIconified(true);
            searchEntry.collapseActionView();
        }
    }*/

/*    public void onToolbarBackPressed() {
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }*/

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
            // TODO (miguelpalacio): display of "no scripts" message has to be solved.
/*            scriptListView.setVisibility(View.GONE);
            noScriptsMessage.setVisibility(View.VISIBLE);*/
            // (TO REMOVE)
            //((TextView) getView().findViewById(android.R.id.empty)).setText(R.string.no_scripts_message);
        } else {
/*            scriptListView.setVisibility(View.VISIBLE);
            noScriptsMessage.setVisibility(View.GONE);*/
        }
        updateAndFilterScriptList(mQuery);
        scriptListAdapter.setmScripts(mScripts);
        scriptListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        buildMenuIdMaps();
        searchEntry = menu.add(Menu.NONE, MenuId.SEARCH.getId(), Menu.NONE, "Search").setIcon(
                R.drawable.ic_search_white_24dp);
        buildAddMenu(menu);
        menu.add(Menu.NONE, MenuId.REFRESH.getId(), Menu.NONE, "Refresh").setIcon(
                R.drawable.ic_menu_refresh);
        menu.add(Menu.NONE, MenuId.HELP.getId(), Menu.NONE, "Help").setIcon(
                android.R.drawable.ic_menu_help);
        super.onCreateOptionsMenu(menu, inflater);

        // Search widget.

        searchEntry.setActionView(new SearchView(getActivity()));
        searchEntry.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menu.findItem(MenuId.SEARCH.getId()).getActionView();

        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        searchView.setIconifiedByDefault(false);

        // Customize search widget.

        int searchPlateId = searchView.getContext().getResources().
                getIdentifier("android:id/search_plate", null, null);
        View mSearchPlate = searchView.findViewById(searchPlateId);
        mSearchPlate.setBackgroundColor(getResources().getColor(R.color.primary));

        int searchCloseId = searchView.getContext().getResources().
                getIdentifier("android:id/search_close_btn", null, null);
        ImageView searchCloseButton = (ImageView) searchView.findViewById(searchCloseId);
        searchCloseButton.setImageResource(R.drawable.ic_clear_white_24dp);

        int searchButtonId = searchView.getContext().getResources().
                getIdentifier("android:id/search_mag_icon", null, null);
        searchButton = (ImageView) searchView.findViewById(searchButtonId);
        searchButton.setAdjustViewBounds(true);
        searchButton.setMaxWidth(0);
        searchButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        searchButton.setImageDrawable(null);

        MenuItemCompat.setOnActionExpandListener(searchEntry,new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                // Hide soft keyboard upon collapse of search widget.
                View view = activity.getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)
                            activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                // If a query was performed, refresh list.
                if (mInSearchResultMode) {
                    mInSearchResultMode = false;

                    updateAndFilterScriptList(EMPTY);
                    scriptListAdapter.setmScripts(mScripts);
                    scriptListAdapter.notifyDataSetChanged();

                    CustomizeWindow.setToolbarTitle(activity, getString(R.string.toolbar_title_scripts));
                }
                return true;
            }
        });
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
        } else if (itemId == MenuId.REFRESH.getId()) {
            updateAndFilterScriptList(mQuery);
            scriptListAdapter.setmScripts(mScripts);
            scriptListAdapter.notifyDataSetChanged();
        } else if (itemId == MenuId.SEARCH.getId()) {
            //activity.onSearchRequested();
            searchView.setFocusable(true);
            searchView.setIconified(false);
            searchView.requestFocusFromTouch();
        }
        return true;
    }

    @Override
    public void onListItemClick(int position) {

        final File file = mScripts.get(position);
        mCurrent = file;
        if (file.isDirectory()) {
            mCurrentDir = file;
            updateAndFilterScriptList(EMPTY);
            return;
        }
        if (FacadeConfiguration.getSdkLevel() <= 3 || !mPreferences.getBoolean("use_quick_menu", true)) {
            doDialogMenu();
            return;
        }

        // "position + 1" because LayoutManager also takes into consideration the header.
        final QuickAction actionMenu =
                new QuickAction(scriptListLayoutManager.findViewByPosition(position + 1));

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

    @Override
    public boolean onItemLongClick(final int position) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setItems(R.array.context_menu_scripts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                File file = mScripts.get(position);

                if (which == 0) {
                    rename(file);
                } else if (which == 1) {
                    delete(file);
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();

        return false;
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
                scriptListAdapter.setmScripts(mScripts);
                scriptListAdapter.notifyDataSetChanged();
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
                updateAndFilterScriptList(EMPTY);
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
                updateAndFilterScriptList(EMPTY);
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
        updateAndFilterScriptList(EMPTY);
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

/*    private class ScriptListObserver extends DataSetObserver implements ConfigurationObserver {
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
    }*/

    private class ScriptListObserver extends RecyclerView.AdapterDataObserver
            implements ConfigurationObserver {

        @Override
        public void onConfigurationChanged() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateAndFilterScriptList(mQuery);
                    scriptListAdapter.setmScripts(mScripts);
                    scriptListAdapter.notifyDataSetChanged();
                }
            });
        }
    }

}
