package com.googlecode.android_scripting.custom_component;

import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.Version;
import com.googlecode.android_scripting.activity.MainActivity;
import com.googlecode.android_scripting.activity.Preferences;
import com.googlecode.android_scripting.fragment.InterpreterManager;
import com.googlecode.android_scripting.fragment.ScriptManager;

/**
 * Fragment that holds the navigation drawer.
 * 
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class NavigationDrawer extends Fragment implements NavigationDrawerAdapter.ViewHolder.ClickListener {

    public static final int HEADER = 0;
    static final int SCRIPTS_ENTRY = 1;
    static final int INTERPRETERS_ENTRY = 2;
    static final int TRIGGERS_ENTRY = 3;
    static final int LOGCAT_ENTRY = 4;
    public static final int DIVIDER = 5;
    static final int SETTINGS_ENTRY = 6;

    /*    ScrimInsetsFrameLayout scrimInsetsFrameLayout;*/

    ActionBarDrawerToggle mDrawerToggle;
    DrawerLayout mDrawerLayout;
    View containerView;

    RecyclerView mDrawerContent;
    RecyclerView.LayoutManager mDrawerLayoutManager;

    String[] mDrawerEntries;
    TypedArray mDrawerIcons;
    TypedArray mDrawerIconsHighlighted;
    String mDrawerHeaderTitle;
    String mDrawerHeaderSubtitle;

    NavigationDrawerAdapter mDrawerAdapter;

    Runnable onDrawerClosedRunnable;
    Handler mHandler = new Handler();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment.
        return inflater.inflate(R.layout.navigation_drawer, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

/*        // Set the layout that envelops the Navigation Drawer to be used later.
        scrimInsetsFrameLayout = (ScrimInsetsFrameLayout) findViewById(R.id.scrimInsetsFrameLayout);*/

        // Since Status Bar is transparent in styles.xml, set the appropriate color for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }

        // Navigation drawer content.
        mDrawerEntries = getResources().getStringArray(R.array.navigation_drawer_entries);
        mDrawerIcons = getResources().obtainTypedArray(R.array.navigation_drawer_icons);
        mDrawerIconsHighlighted = getResources().obtainTypedArray(R.array.navigation_drawer_icons_highlighted);

        mDrawerHeaderTitle = getString(R.string.navigation_drawer_header_title);
        mDrawerHeaderSubtitle = getString(R.string.navigation_drawer_header_subtitle);
        mDrawerHeaderSubtitle += Version.getVersion(getActivity());

        mDrawerContent = (RecyclerView) getActivity().findViewById(R.id.navigation_drawer_content);
        mDrawerContent.setHasFixedSize(true);

        mDrawerAdapter = new NavigationDrawerAdapter(mDrawerEntries, mDrawerIcons,
                mDrawerIconsHighlighted, mDrawerHeaderTitle, mDrawerHeaderSubtitle, this);
        mDrawerContent.setAdapter(mDrawerAdapter);

        mDrawerLayoutManager = new LinearLayoutManager(getActivity());
        mDrawerContent.setLayoutManager(mDrawerLayoutManager);

        // Highlight default entry.
        mDrawerAdapter.toggleSelection(SCRIPTS_ENTRY);
    }

    public void setUp(DrawerLayout drawerLayout, int fragmentId, Toolbar toolbar) {
        containerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close) {

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
/*                getActivity().invalidateOptionsMenu();*/
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
/*                getActivity().invalidateOptionsMenu();*/
                if (onDrawerClosedRunnable != null) {
                    mHandler.post(onDrawerClosedRunnable);
                    onDrawerClosedRunnable = null;
                }
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                // Set "hamburguer" icon on toolbar.
                mDrawerToggle.syncState();
            }
        });
    }

    @Override
    public void onDrawerItemClick(final int position) {
        // Highlight selected item.
        if (position < DIVIDER) {
            mDrawerAdapter.clearSelection();
            mDrawerAdapter.toggleSelection(position);
        }

        // Set a runnable that runs once the drawer closes after clicking on item.
        onDrawerClosedRunnable = new Runnable() {
            @Override
            public void run() {

                switch (position) {
                    case SCRIPTS_ENTRY:
                        getActivity().getFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new ScriptManager(),
                                        MainActivity.SCRIPTS_FRAGMENT)
                                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                .commit();
                        break;

                    case INTERPRETERS_ENTRY:
                        getActivity().getFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new InterpreterManager(),
                                        MainActivity.INTERPRETERS_FRAGMENT)
                                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                .commit();
                        break;

                    case TRIGGERS_ENTRY:
                        break;

                    case LOGCAT_ENTRY:
                        break;

                    case SETTINGS_ENTRY:
                        startActivity(new Intent(getActivity(), Preferences.class));
                        break;

                    default:
                        break;
                }
            }
        };

        // Update selected item and title, then close the drawer.
        mDrawerLayout.closeDrawers();
    }
}
