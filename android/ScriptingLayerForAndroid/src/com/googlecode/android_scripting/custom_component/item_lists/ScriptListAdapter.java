package com.googlecode.android_scripting.custom_component.item_lists;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.android_scripting.FeaturedInterpreters;
import com.googlecode.android_scripting.R;

import java.io.File;
import java.util.List;

/**
 * Adapter for a vertically scrolling list of scripts using RecyclerView.
 *
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class ScriptListAdapter extends SelectableAdapter<ScriptListAdapter.ViewHolder> {

    static final int TYPE_LIST_ITEM = 0;
    static final int TYPE_HEADER = 1;
    static final int TYPE_FOOTER = 2;

    private Activity activity;

    private List<File> mScripts;

    private ViewHolder.ClickListener clickListener;

    public ScriptListAdapter(Activity activity, List<File> mScripts,
                             ViewHolder.ClickListener clickListener) {
        super();
        this.activity = activity;
        this.mScripts = mScripts;
        this.clickListener = clickListener;
    }

    // ViewHolder Inner Class.
    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        int holderId;

        TextView interpreterTextView;
        ImageView interpreterImageView;

        View itemView;

        private ClickListener listener;

        public ViewHolder(View itemView, int viewType, ClickListener listener) {
            super(itemView);

            this.itemView = itemView;

            // Set the view according with its type.
            if (viewType == TYPE_LIST_ITEM) {
                holderId = TYPE_LIST_ITEM;

                interpreterTextView = (TextView) itemView.findViewById(R.id.list_item_one_line_title);
                interpreterImageView = (ImageView) itemView.findViewById(R.id.list_item_one_line_icon);

                // Set click listeners for the row.
                this.listener = listener;
                itemView.setOnClickListener(this);
            } else if (viewType == TYPE_HEADER) {
                holderId = TYPE_HEADER;
            } else {
                holderId = TYPE_FOOTER;
            }
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                listener.onListItemClick(getAdapterPosition() - 1);
            }
        }

        // Interface to route back click events to Activity.
        public interface ClickListener {
            void onListItemClick(int position);
        }
    }

    // Inflate list_item_title_summary.xml or item_list_row_last in accordance with viewType.
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final int layout;
        if (viewType == TYPE_LIST_ITEM) {
            layout = R.layout.list_item_one_line_icon;
        } else if (viewType == TYPE_HEADER) {
            layout = R.layout.list_item_empty_8dp;
        } else {
            layout = R.layout.list_item_empty_24dp;
        }

        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v, viewType, clickListener);
    }

    // This method is called when the item in a row needs to be displayed.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (holder.holderId == TYPE_LIST_ITEM) {

            File script = mScripts.get(position - 1);
            int resourceId;
            if (script.isDirectory()) {
                resourceId = R.drawable.folder;
            } else {
                resourceId = FeaturedInterpreters.getInterpreterIcon(activity, script.getName());
                if (resourceId == 0) {
                    resourceId = R.drawable.sl4a_logo_32;
                }
            }

            holder.interpreterImageView.setImageResource(resourceId);
            holder.interpreterTextView.setText(script.getName());
        }
    }

    // Return the number of items present in the list ([header] + rows + [footer]).
    @Override
    public int getItemCount() {
        return mScripts.size() + 2;
    }

    // Return the type of the view that is being passed.
    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        } else if (position == getItemCount() - 1) {
            return TYPE_FOOTER;
        } else {
            return TYPE_LIST_ITEM;
        }
    }

    // mScripts has to be set in this way since mScripts changes reference
    // to object in ScriptManager.
    public void setmScripts(List<File> mScripts) {
        this.mScripts = mScripts;
    }
}
