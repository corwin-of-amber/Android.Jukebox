package com.example.corwin.jukebox.widgets;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

/**
 * Created by corwin on 1/9/15.
 */

public class ListAdapterProxy implements ListAdapter {

    private ListAdapter a;

    public ListAdapterProxy(ListAdapter deferTo) {
        a = deferTo;
    }

    public<A extends ListAdapter> A get() {
        return (A) a;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return a.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return a.isEnabled(position);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        a.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        a.unregisterDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        return a.getCount();
    }

    @Override
    public Object getItem(int position) {
        return a.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return a.getItemId(position);
    }

    @Override
    public boolean hasStableIds() {
        return a.hasStableIds();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return a.getView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        return a.getItemViewType(position);
    }

    @Override
    public int getViewTypeCount() {
        return a.getViewTypeCount();
    }

    @Override
    public boolean isEmpty() {
        return a.isEmpty();
    }
}