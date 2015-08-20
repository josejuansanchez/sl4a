package com.googlecode.android_scripting.custom_component.item_lists;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Adapter for vertically scrolling lists of items using RecyclerView.
 *
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class ItemListAdapter extends SelectableAdapter<ItemListAdapter.ViewHolder> {

    static final int TYPE_ROW = 0;
    static final int TYPE_HEADER = 1;
    static final int TYPE_FOOTER = 2;

    List<String> titles;
    List<String> summaries;

    int rowLayoutId = 0;
    int headerLayoutId = 0;
    int footerLayoutId = 0;

    ViewHolder.ClickListener clickListener;

    // Constructors.

    public ItemListAdapter(List<String> titles, List<String> summaries,
                           ViewHolder.ClickListener clickListener) {
        super();
        this.titles = titles;
        this.summaries = summaries;
        this.clickListener = clickListener;
    }

    public ItemListAdapter(List<String> titles, ViewHolder.ClickListener clickListener) {
        super();
        this.titles = titles;
        this.clickListener = clickListener;
    }

    // List of items with icon.
    // --

    // Layout setters.

    public void setRowLayout(int rowLayoutId) {
        this.rowLayoutId = rowLayoutId;
    }

    public void setHeaderLayout(int headerLayoutId) {
        this.headerLayoutId = headerLayoutId;
    }

    public void setFooterLayout(int footerLayoutId) {
        this.footerLayoutId = footerLayoutId;
    }

    // ViewHolder Inner Class.
    public static class ViewHolder extends RecyclerView.ViewHolder implements
            View.OnClickListener, View.OnLongClickListener {

        int holderId;

        TextView titleTextView;
        TextView summaryTextView;

        View itemView;

        private ClickListener listener;

        public ViewHolder(View itemView, int viewType, ClickListener listener) {
            super(itemView);

            this.itemView = itemView;

            // Set the view according with its type.
            if (viewType == TYPE_HEADER) {
                holderId = TYPE_HEADER;
            } else if (viewType == TYPE_ROW) {
                holderId = TYPE_ROW;

/*                titleTextView = (TextView) itemView.findViewById(R.id.list_item_title);
                summaryTextView = (TextView) itemView.findViewById(R.id.list_item_summary);*/

                // Set click listeners for the row.
                this.listener = listener;
                itemView.setOnClickListener(this);
                itemView.setOnLongClickListener(this);
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

        @Override
        public boolean onLongClick(View v) {
            return listener!= null && listener.onItemLongClicked(getAdapterPosition());
        }

        // Interface to route back click events to Activity.
        public interface ClickListener {
            void onListItemClick(int position);
            boolean onItemLongClicked(int position);
        }
    }

    // Inflate list_item_title_summary.xml or item_list_row_last in accordance with viewType.
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //layout = oneLineRow ? R.layout.item_list_one_line_row : R.layout.item_list_row;
        final int layout;
        if (viewType == TYPE_HEADER) {
            layout = headerLayoutId;
        } else if (viewType == TYPE_ROW) {
            layout = rowLayoutId;
        } else {
            layout = footerLayoutId;
        }

        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v, viewType, clickListener);
    }

    // This method is called when the item in a row needs to be displayed.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int pos = headerLayoutId == 0 ? position : position - 1;
        // TODO: differentiate type of list (with header, with footer, etc).
        if (holder.holderId == 1) {
            holder.titleTextView.setText(titles.get(pos));
            holder.summaryTextView.setText(summaries.get(pos));
        }
    }

    // Return the number of items present in the list ([header] + rows + [footer]).
    @Override
    public int getItemCount() {
        if (headerLayoutId == 0 && footerLayoutId == 0) {
            return titles.size();
        } else if (headerLayoutId != 0 && footerLayoutId != 0) {
            return titles.size() + 2;
        } else {
            return titles.size() + 1;
        }
    }

    // Return the type of the view that is being passed.
    @Override
    public int getItemViewType(int position) {
        if (position == 0 && headerLayoutId != 0) {
            return TYPE_HEADER;
        } else if (position == getItemCount() - 1 && footerLayoutId != 0) {
            return TYPE_FOOTER;
        } else {
            return TYPE_ROW;
        }
    }
}
