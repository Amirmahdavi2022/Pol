package org.torproject.android.ui.connect;

// adapter from https://gist.github.com/laminr/246d3b859cb25c784894c231c9d26a11
// see https://stackoverflow.com/questions/4869392/diacritics-international-characters-in-autocompletetextview

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A ListAdapter that manages a ListView backed by an array of arbitrary
 * objects. By default, this class expects that the provided resource id references
 * a single TextView.  If you want to use a more complex layout, use the constructors that
 * also takes a field id.  That field id should reference a TextView in the larger layout
 * resource.
 * <p/>
 * However the TextView is referenced, it will be filled with the toString() of each object in
 * the array. You can add lists or arrays of custom objects. Override the toString() method
 * of your objects to determine what text will be displayed for the item in the list.
 * <p/>
 * To use something other than TextViews for the array display, for instance, ImageViews,
 * or to have some data besides toString() results fill the views,
 * override {@link #getView(int, View, ViewGroup)} to return the type of view you want.
 */
public class IgnoreAccentsArrayAdapter<T> extends BaseAdapter implements Filterable {
    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     */
    List<T> mObjects;

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock. This lock is also
     * used by the filter (see {@link #getFilter()}) to make a synchronized copy of
     * the original array of data.
     */
    final Object mLock = new Object();

    /**
     * The resource indicating what views to inflate to display the content of this
     * array adapter.
     */
    private final int mResource;

    ArrayList<T> mOriginalValues;
    private HRArrayFilter mFilter;

    private final LayoutInflater mInflater;

    public IgnoreAccentsArrayAdapter(Context context, int textViewResourceId, List<T> objects) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResource = textViewResourceId;
        mObjects = objects;
    }

    @Override
    public int getCount() {
        return mObjects.size();
    }

    @Override
    public T getItem(int position) {
        return mObjects.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView, ViewGroup parent,
                                        int resource) {
        View view;
        if (convertView == null) {
            view = mInflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }

        try {
            var text = (TextView) view;
            text.setText(getItem(position).toString());
        } catch (ClassCastException e) {
            Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                    "ArrayAdapter requires the resource ID to be a TextView", e);
        }

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    @Override
    public Filter getFilter() {
        if (mFilter == null) {
            mFilter = new HRArrayFilter();
        }
        return mFilter;
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    class HRArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            var results = new FilterResults();

            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
            }

            //noinspection SizeReplaceableByIsEmpty
            if (prefix == null || prefix.length() == 0) {
                synchronized (mLock) {
                    var list = new ArrayList<>(mOriginalValues);
                    results.values = list;
                    results.count = list.size();
                }
            } else {
                var prefixString = prefix.toString().toLowerCase(Locale.getDefault());
                var values = mOriginalValues;
                final var newValues = new ArrayList<>(values.size());

                for (var value : values) {
                    final var valueText = value.toString().toLowerCase(Locale.getDefault()).substring(5);
                    var valueTextNoPalatals = Normalizer
                            .normalize(valueText, Normalizer.Form.NFD)
                            .replaceAll("[^\\p{ASCII}]", "")
                            .toLowerCase(Locale.getDefault());

                    var prefixStringNoPalatals = Normalizer
                            .normalize(prefixString, Normalizer.Form.NFD)
                            .replaceAll("[^\\p{ASCII}]", "")
                            .toLowerCase(Locale.getDefault());
                    // First match against the whole, non-splitted value
                    if (valueText.startsWith(prefixString) || valueTextNoPalatals.startsWith(prefixStringNoPalatals)) {
                        newValues.add(value);
                    } else {
                        final var words = valueText.split(" ");

                        for (var word : words) {
                            if (word.startsWith(prefixString)) {
                                newValues.add(value);
                                break;
                            }
                        }
                    }
                }
                results.values = newValues;
                results.count = newValues.size();
            }

            return results;
        }


        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mObjects = (List<T>) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    }
}