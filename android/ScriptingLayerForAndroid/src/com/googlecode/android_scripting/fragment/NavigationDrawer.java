package com.googlecode.android_scripting.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.PreferenceUtils;
import com.googlecode.android_scripting.R;

/**
 * Fragment that holds the navigation drawer.
 * 
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class NavigationDrawer extends Fragment {

/*    static final String PREF_FILE_DRAWER = "pref_file_drawer";
    static final String KEY_USER_LEARNED_DRAWER = "user_learned_drawer";*/


    ActionBarDrawerToggle mDrawerToggle;
    DrawerLayout mDrawerLayout;
    View containerView;

/*    boolean mUserLearnedDrawer;
    boolean mFromSavedInstanceState;*/

/*    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUserLearnedDrawer = Boolean.parseBoolean(PreferenceUtils.readFromPreferences(getActivity(),
                PREF_FILE_DRAWER, KEY_USER_LEARNED_DRAWER, "false"));

*//*        if (!Boolean.parseBoolean(PreferenceUtils.readFromPreferences(getActivity(), PREF_FILE_DRAWER,
                "testing_pref", "false"))) {
            PreferenceUtils.saveToPreferences(getActivity(), PREF_FILE_DRAWER, "testing_pref", "true");
            Log.d("Preference was created.");
        } else {
            Log.d("Preference already existed. Value: " + PreferenceUtils.readFromPreferences(getActivity(),
                    PREF_FILE_DRAWER, "testing_pref", "false"));
        }*//*

        if (savedInstanceState != null) {
            mFromSavedInstanceState = true;
        }

    }*/

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment.
        return inflater.inflate(R.layout.navigation_drawer, container, false);
    }

    public void setUp(DrawerLayout drawerLayout, int fragmentId, Toolbar toolbar) {
        containerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close) {

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
/*                if (!mUserLearnedDrawer) {
                    mUserLearnedDrawer = true;
                    PreferenceUtils.saveToPreferences(getActivity(), PREF_FILE_DRAWER,
                            KEY_USER_LEARNED_DRAWER, "true");
                }*/
                getActivity().invalidateOptionsMenu();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                getActivity().invalidateOptionsMenu();
            }
        };
/*        if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
            mDrawerLayout.openDrawer(containerView);
        }*/
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                // Set "hamburguer" icon on toolbar.
                mDrawerToggle.syncState();
            }
        });
    }

}
