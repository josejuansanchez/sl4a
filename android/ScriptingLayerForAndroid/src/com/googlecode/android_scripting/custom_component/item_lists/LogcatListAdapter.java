package com.googlecode.android_scripting.custom_component.item_lists;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.googlecode.android_scripting.R;

import java.util.List;

/**
 * Adapter for a vertically scrolling list of logs using RecyclerView.
 *
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class LogcatListAdapter extends SelectableAdapter<LogcatListAdapter.ViewHolder> {

    final static int TYPE_LIST_ITEM = 0;
    final static int TYPE_FOOTER = 1;

    List<String> logs;

    public LogcatListAdapter(List<String> logs) {
        super();
        this.logs = logs;
    }

    // ViewHolder Class.
    public static class ViewHolder extends RecyclerView.ViewHolder {

        int holderId;

        TextView logTextView;
        View itemView;

        public ViewHolder(View itemView, int viewType) {
            super(itemView);
            this.itemView = itemView;

            // Set the view according with its type.
            if (viewType == TYPE_LIST_ITEM) {
                holderId = TYPE_LIST_ITEM;
                logTextView = (TextView) itemView.findViewById(R.id.list_item_log);
            } else {
                holderId = TYPE_FOOTER;
            }
        }
    }

    // Inflate list_item_summaryr item_list_row_last in accordance with viewType.
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final int layout;
        if (viewType == TYPE_LIST_ITEM) {
            layout = R.layout.list_item_logcat;
        } else {
            layout = R.layout.list_item_empty_24dp;
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v, viewType);
    }

    // This method is called when the item in a row needs to be displayed.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (holder.holderId == TYPE_LIST_ITEM) {
            holder.logTextView.setText(logs.get(position));
        }
    }

    // Return the number of items present in the list (rows + [footer]).
    @Override
    public int getItemCount() {
        return logs.size() + 1;
    }

    // Return the type of the view that is being passed.
    @Override
    public int getItemViewType(int position) {
        if (position == getItemCount() - 1) {
            return TYPE_FOOTER;
        } else {
            return TYPE_LIST_ITEM;
        }
    }
}
