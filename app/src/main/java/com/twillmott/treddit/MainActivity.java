package com.twillmott.treddit;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.common.collect.Lists;

import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.UserContributionPaginator;

import java.net.URL;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private UserAgent myUserAgent = UserAgent.of("andriod", "com.twillmott.treddit", "v0.1", "spud_nuts");
    private final RedditClient redditClient = new RedditClient(myUserAgent);
    private Credentials credentials;
    private OAuthHelper oAuthHelper;
    private OAuthData oAuthData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        //Reddit authenticaiton
        credentials = Credentials.installedApp("vFNAEFGu0TUy3Q", "http://twillmott.com");
        oAuthHelper = redditClient.getOAuthHelper();
        URL authURL = oAuthHelper.getAuthorizationUrl(credentials, true, true, "edit", "history", "identity", "report");

        // Open the browser for the user to log in
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(authURL.toString()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        startActivity(intent);

        // Unhide button stuff
        Button clickButton = (Button) findViewById(R.id.button_hidden);
        clickButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    new GetHiddenPosts(redditClient).execute().get();
                } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * This activity should be opened by the redirect URL from the reddit login (other than when
     * the user opens it themselves).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri intentUri = intent.getData();

        // Check we opened the intent from the redirect url
        if (intentUri != null && intentUri.toString().contains("twillmott") && intentUri.getQueryParameter("code") != null) {
            // Continue the authorisation process
            try {
                new RunSecondAuthentication(intentUri.toString()).execute();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
    }

    /**
     * Continue the second part of the OAuth process.
     */
    private class RunSecondAuthentication extends AsyncTask<Void, Void, Void> {

        String url;

        RunSecondAuthentication(String url) {
            this.url = url;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                oAuthData = oAuthHelper.onUserChallenge(url, credentials);
                redditClient.authenticate(oAuthData);
            } catch ( Exception e ) {
                Log.e(LOG_TAG, e.getMessage());
            }
            return null;
        }
    }

    /**
     * An AsyncTask that is used to hide all reddit posts for the logged in user.
     * It loads the posts in to a pagenator, and then loops through them all, unhiding them 25 at a
     * time.
     */
    private class GetHiddenPosts extends AsyncTask<Void, Void, Void> {

        RedditClient redditClient;

        GetHiddenPosts(RedditClient redditClient) {
            this.redditClient = redditClient;
        }


        @Override
        protected Void doInBackground(Void... params) {

            AccountManager accountManager = new AccountManager(redditClient);
            UserContributionPaginator userContributionPaginator = new UserContributionPaginator(redditClient, "hidden", redditClient.me().getFullName());
            int unhiddenPosts = 0;

            while (userContributionPaginator.hasNext()) {

                userContributionPaginator.next(true);

                // Get a list of submissions from the pagenator. JAVA8
                List<Submission> submissions = Lists.newArrayList();
                for (Contribution contribution : userContributionPaginator.getCurrentListing()) {
                    submissions.add((Submission) contribution);
                }

                // Unhide the posts.
                try {
                    if (submissions.size() > 1) {
                        accountManager.hide(true, submissions.get(0));
                    } else {
                        accountManager.hide(false, submissions.get(0), submissions.subList(1, submissions.size() - 1).toArray(new Submission[(submissions.size() - 2)]));
                    }
                } catch (ApiException e) {
                    Log.e(LOG_TAG, e.getMessage());
                }

                // At the moment this shows that progress is being made (in the console). We can change
                // this to give an on screen indication.
                unhiddenPosts += submissions.size();
                Log.i(LOG_TAG, "Unhiden "+ unhiddenPosts +" posts");

                // For some reason the paginator only loads 984 posts. So if we have reached the end of the posts,
                // reload some more!
                if (!userContributionPaginator.hasNext()) {
                    userContributionPaginator = new UserContributionPaginator(redditClient, "hidden", redditClient.me().getFullName());
                }
            }
            return null;
        }
    }
}
