package com.nononsenseapps.feeder.ui;


import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ShareActionProvider;
import android.widget.TextView;

import com.nononsenseapps.feeder.R;
import com.nononsenseapps.feeder.views.ObservableScrollView;
import com.shirwa.simplistic_rss.RssItem;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ReaderFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ReaderFragment extends Fragment {
    public static final String ARG_TITLE = "title";
    public static final String ARG_DESCRIPTION = "body";
    public static final String ARG_LINK = "link";
    public static final String ARG_IMAGEURL = "imageurl";
    public static final String ARG_ID = "dbid";

    // TODO database id
    private long _id = -1;
    // All content contained in RssItem
    private RssItem mRssItem;
    private TextView mTitleTextView;
    private TextView mBodyTextView;
    private ObservableScrollView mScrollView;


    public ReaderFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param rssItem the Item to open in the reader
     * @return A new instance of fragment ReaderFragment.
     */
    public static ReaderFragment newInstance(long id, RssItem rssItem) {
        ReaderFragment fragment = new ReaderFragment();
        // Save some time on load
        fragment.mRssItem = rssItem;
        fragment._id = id;

        fragment.setArguments(RssItemToBundle(id, rssItem, null));
        return fragment;
    }

    /**
     * Convert an RssItem into a Bundle for use with Fragment Arguments
     *
     * @param id      potential database id
     * @param rssItem to convert
     * @param bundle  may be null
     * @return bundle of rssItem plus id
     */
    public static Bundle RssItemToBundle(long id, RssItem rssItem,
                                         Bundle bundle) {
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putLong(ARG_ID, id);
        bundle.putString(ARG_TITLE, rssItem.getTitle());
        bundle.putString(ARG_DESCRIPTION, rssItem.getDescription());
        bundle.putString(ARG_LINK, rssItem.getLink());
        bundle.putString(ARG_IMAGEURL, rssItem.getImageUrl());
        return bundle;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            _id = savedInstanceState.getLong(ARG_ID);
            mRssItem = RssItemFromBundle(savedInstanceState);

        } else if (mRssItem == null) {
            // Construct from arguments
            _id = getArguments().getLong(ARG_ID, -1);
            mRssItem = RssItemFromBundle(getArguments());
        }

        setHasOptionsMenu(true);
    }

    public static RssItem RssItemFromBundle(Bundle bundle) {
        RssItem rssItem = new RssItem();
        rssItem.setTitle(bundle.getString(ARG_TITLE));
        rssItem.setDescription(bundle.getString(ARG_DESCRIPTION));
        rssItem.setLink(bundle.getString(ARG_LINK));
        rssItem.setImageUrl(bundle.getString(ARG_IMAGEURL));
        return rssItem;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_reader, container,
                false);

        mScrollView = (ObservableScrollView) rootView.findViewById(R.id.scroll_view);
        mTitleTextView = (TextView) rootView.findViewById(R.id.story_title);
        mBodyTextView = (TextView) rootView.findViewById(R.id.story_body);

        if (mRssItem.getTitle() == null) {

        } else {
            mTitleTextView
                    .setText(android.text.Html.fromHtml(mRssItem.getTitle()));
        }
        if (mRssItem.getDescription() == null) {

        } else {
            // TODO maybe do formatting in background first...
            mBodyTextView.setText(
                    android.text.Html.fromHtml(mRssItem.getDescription()));
        }

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);
        // TODO set for scrollview
        ((BaseActivity) getActivity()).enableActionBarAutoHide(mScrollView);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        RssItemToBundle(_id, mRssItem, outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.reader, menu);

        // Locate MenuItem with ShareActionProvider
        MenuItem shareItem = menu.findItem(R.id.action_share);

        // Fetch and store ShareActionProvider
        ShareActionProvider shareActionProvider =
                (ShareActionProvider) shareItem.getActionProvider();

        // Set intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, mRssItem.getLink());
        shareActionProvider.setShareIntent(shareIntent);

        // Don't forget super call here
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        final long id = menuItem.getItemId();
        if (id == R.id.action_open_in_browser) {
            // Open in browser
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mRssItem.getLink())));
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }
}