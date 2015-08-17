package com.googlecode.android_scripting.custom_component;

import android.content.res.TypedArray;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.android_scripting.R;

/**
 * @author Miguel Palacio (palaciodelgado [at] gmail [dot] com)
 */
public class NavigationDrawerAdapter extends SelectableAdapter<NavigationDrawerAdapter.ViewHolder> {

    static final int TYPE_HEADER = 0;
    static final int TYPE_ROW = 1;
    static final int TYPE_DIVIDER = 2;

    final String[] entries;
    final TypedArray icons;
    final TypedArray icons_highlighted;
    final String headerTitle;
    final String headerSubtitle;

    private ViewHolder.ClickListener clickListener;

    // Class constructor.
    public NavigationDrawerAdapter(String[] entries, TypedArray icons, TypedArray icons_highlighted,
                                   String headerTitle, String headerSubtitle,
                                   ViewHolder.ClickListener clickListener) {
        super();

        this.entries = entries;
        this.icons = icons;
        this.icons_highlighted = icons_highlighted;
        this.headerTitle = headerTitle;
        this.headerSubtitle = headerSubtitle;

        this.clickListener = clickListener;
    }

    // ViewHolder Class.
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        int holderId;

        TextView entryText;
        ImageView entryIcon;

        TextView title;
        TextView subtitle;

        View itemView;

        private ClickListener listener;

        public ViewHolder(View itemView, int viewType, ClickListener listener) {
            super(itemView);

            this.itemView = itemView;

            // Set the view according with its type.
            if (viewType == TYPE_ROW) {
                holderId = TYPE_ROW;

                entryText = (TextView) itemView.findViewById(R.id.drawer_entry_text);
                entryIcon = (ImageView) itemView.findViewById(R.id.drawer_entry_icon);

                // Set click listener for the row.
                this.listener = listener;
                itemView.setOnClickListener(this);
            } else if (viewType == TYPE_DIVIDER) {
                holderId = TYPE_DIVIDER;
            }
            else {
                holderId = TYPE_HEADER;

                title = (TextView) itemView.findViewById(R.id.navigation_drawer_header_title);
                subtitle = (TextView) itemView.findViewById(R.id.navigation_drawer_header_subtitle);
            }
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                listener.onDrawerItemClick(getAdapterPosition());
            }
        }

        // Interface to route back click events to Activity.
        public interface ClickListener {
            void onDrawerItemClick(int position);
        }
    }

    // Inflate navigation_drawer_header.xml_header.xml, navigation_drawer_row_drawer_row.xml or navigation_drawer_divider in accordance with viewType.
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        //final int layout = viewType == TYPE_ROW ? R.layout.navigation_drawer_row : R.layout.navigation_drawer_header;
        final int layout;

        if (viewType == TYPE_ROW) {
            layout = R.layout.navigation_drawer_row;
        } else if (viewType == TYPE_DIVIDER) {
            layout = R.layout.navigation_drawer_divider;
        } else {
            layout = R.layout.navigation_drawer_header;
        }

        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v, viewType, clickListener);
    }

    // This method is called when the item in a row needs to be displayed.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        if (holder.holderId == TYPE_ROW) {

            int pos;
            if (position < NavigationDrawer.DIVIDER) {
                // Entries before divider.
                pos = position - 1;
            } else {
                // Entries after divider.
                pos = position - 2;
            }

            holder.entryText.setText(entries[pos]);

            // For selected row, highlight background and icon.
            if (isSelected(position) && position < NavigationDrawer.DIVIDER) {
                holder.itemView.setBackgroundResource(R.drawable.custom_bg_selected);
                holder.entryIcon.setImageDrawable(icons_highlighted.getDrawable(pos));
            } else {
                holder.itemView.setBackgroundResource(R.drawable.custom_bg);
                holder.entryIcon.setImageDrawable(icons.getDrawable(pos));
            }
        } else if (holder.holderId == TYPE_HEADER) {
            holder.title.setText(headerTitle);
            holder.subtitle.setText(headerSubtitle);
        }
    }

    // Return the number of items present in the list (rows + header + divider).
    @Override
    public int getItemCount() {
        return entries.length + 2;
    }

    // Return the type of the view that is being passed.
    @Override
    public int getItemViewType(int position) {
        if (position == NavigationDrawer.HEADER) {
            return TYPE_HEADER;
        } else if (position == NavigationDrawer.DIVIDER) {
            return TYPE_DIVIDER;
        } else {
            return TYPE_ROW;
        }
    }
}
