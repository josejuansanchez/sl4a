package com.googlecode.android_scripting.fragment;

import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.custom_component.NavigationDrawerAdapter;

/**
 * Fragment that holds the navigation drawer.
 * 
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class NavigationDrawer extends Fragment implements NavigationDrawerAdapter.ViewHolder.ClickListener {

    /*    ScrimInsetsFrameLayout scrimInsetsFrameLayout;*/

    ActionBarDrawerToggle mDrawerToggle;
    DrawerLayout mDrawerLayout;
    View containerView;

    RecyclerView mDrawerContent;
    RecyclerView.LayoutManager mDrawerLayoutManager;
    String[] mDrawerEntries;
    TypedArray mDrawerIcons;
    TypedArray mDrawerIconsHighlighted;

    NavigationDrawerAdapter mDrawerAdapter;

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

        mDrawerContent = (RecyclerView) getActivity().findViewById(R.id.navigation_drawer_content);
        mDrawerContent.setHasFixedSize(true);

        mDrawerAdapter = new NavigationDrawerAdapter(mDrawerEntries, mDrawerIcons,
                mDrawerIconsHighlighted, this);
        mDrawerContent.setAdapter(mDrawerAdapter);

        mDrawerLayoutManager = new LinearLayoutManager(getActivity());
        mDrawerContent.setLayoutManager(mDrawerLayoutManager);
    }

    public void setUp(DrawerLayout drawerLayout, int fragmentId, Toolbar toolbar) {
        containerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close) {

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getActivity().invalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                getActivity().invalidateOptionsMenu();
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
    public void onDrawerItemClick(int position) {

    }
}
