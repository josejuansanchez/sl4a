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
import com.googlecode.android_scripting.interpreter.Interpreter;

import java.util.List;

/**
 * Adapter for a vertically scrolling list of interpreters using RecyclerView.
 *
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class InterpreterListAdapter extends SelectableAdapter<InterpreterListAdapter.ViewHolder> {

    static final int TYPE_LIST_ITEM = 0;
    static final int TYPE_FOOTER = 1;

    private Activity activity;

    private List<Interpreter> mInterpreters;

    private ViewHolder.ClickListener clickListener;

    public InterpreterListAdapter(Activity activity, List<Interpreter> mInterpreters,
                                  ViewHolder.ClickListener clickListener) {
        super();
        this.activity = activity;
        this.mInterpreters = mInterpreters;
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
            } else {
                holderId = TYPE_FOOTER;
            }
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                listener.onListItemClick(getAdapterPosition());
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
        } else {
            layout = R.layout.list_item_empty;
        }

        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v, viewType, clickListener);
    }

    // This method is called when the item in a row needs to be displayed.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (holder.holderId == TYPE_LIST_ITEM) {

            Interpreter interpreter = mInterpreters.get(position);
            int iconId = FeaturedInterpreters.getInterpreterIcon(activity, interpreter.getExtension());
            if (iconId == 0) {
                iconId = R.drawable.sl4a_logo_32;
            }

            holder.interpreterImageView.setImageResource(iconId);
            holder.interpreterTextView.setText(interpreter.getNiceName());
        }
    }

    // Return the number of items present in the list (rows + [footer]).
    @Override
    public int getItemCount() {
        return mInterpreters.size() + 1;
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

    // mInterpreters has to be set in this way since mInterpreters changes reference
    // to object in InterpreterManager.
    public void setmInterpreters(List<Interpreter> mInterpreters) {
        this.mInterpreters = mInterpreters;
    }
}
