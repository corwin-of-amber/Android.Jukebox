package com.example.corwin.jukebox.widgets;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.example.corwin.jukebox.R;

/**
 * Created by corwin on 1/2/15.
 */
public class ListAdapterWithResize extends ListAdapterProxy {

    private SizingMixin sz;

    public ListAdapterWithResize(ListAdapter deferTo, SizingMixin sizing) {
        super(deferTo);
        sz = sizing;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return sz.resizeTextInView(super.getView(position, convertView, parent));
    }

    public static class SizingMixin {

        private float textSize = 16;

        public float getTextSize() { return textSize; }
        public void setTextSize(float newSize) {
            textSize = newSize;
        }

        public View resizeTextInView(View view) {
            TextView item_text = (TextView) view.findViewById(R.id.item_text);
            item_text.setTextSize(textSize);
            return view;
        }
    }

}
