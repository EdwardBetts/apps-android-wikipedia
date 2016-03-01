package org.wikipedia.useroption.dataclient;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import org.wikipedia.Site;
import org.wikipedia.WikipediaApp;
import org.wikipedia.dataclient.RestAdapterFactory;
import org.wikipedia.dataclient.mwapi.MwQueryResponse;
import org.wikipedia.editing.FetchEditTokenTask;
import org.wikipedia.useroption.UserOption;

import java.util.concurrent.Executor;

import retrofit.RetrofitError;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;

public class DefaultUserOptionDataClient implements UserOptionDataClient {
    @NonNull private final Site site;
    @NonNull private final Client client;

    public DefaultUserOptionDataClient(@NonNull Site site) {
        this.site = site;
        client = RestAdapterFactory.newInstance(site).create(Client.class);
    }

    @NonNull
    @Override
    public UserInfo get() {
        return client.get().query().userInfo();
    }

    @Override
    public void post(@NonNull UserOption option) {
        client.post(getToken(), option.key(), option.val()).check();
    }

    @Override
    public void delete(@NonNull UserOption option) {
        client.delete(getToken(), option.key()).check();
    }

    @NonNull private String getToken() {
        if (app().getEditTokenStorage().token(site) == null) {
            requestToken();
        }

        String token = app().getEditTokenStorage().token(site);
        if (token == null) {
            throw RetrofitError.unexpectedError(site.authority(), new RuntimeException("No token"));
        }
        return token;
    }

    private void requestToken() {
        new FetchEditTokenTask(app(), site) {
            @Override
            public void onFinish(String result) {
                app().getEditTokenStorage().token(site, result);
            }

            @Override
            public void execute() {
                super.executeOnExecutor(new SynchronousExecutor());
            }
        }.execute();
    }

    private WikipediaApp app() {
        return WikipediaApp.getInstance();
    }

    private static class SynchronousExecutor implements Executor {
        @Override
        public void execute(@NonNull Runnable runnable) {
            runnable.run();
        }
    }

    private interface Client {
        String ACTION = "/w/api.php?format=json&formatversion=2&action=";

        @GET(ACTION + "query&meta=userinfo&uiprop=options")
        @NonNull MwQueryResponse<QueryUserInfo> get();

        @FormUrlEncoded
        @POST(ACTION + "options")
        @NonNull PostResponse post(@Field("token") @NonNull String token,
                                   @Query("optionname") @NonNull String key,
                                   @Query("optionvalue") @Nullable String value);

        @FormUrlEncoded
        @POST(ACTION + "options")
        @NonNull PostResponse delete(@Field("token") @NonNull String token,
                                     @Query("change") @NonNull String key);
    }

    private static class PostResponse {
        private String options;

        public boolean success() {
            return "success".equals(options);
        }

        public String result() {
            return options;
        }

        public void check() {
            if (!success()) {
                // TODO: pass actual URL (here and elsewhere). This class is populated by Retrofit and
                //       doesn't seem to be able to hold references to the outter class' instance members.
                throw RetrofitError.unexpectedError("", new RuntimeException("Bad response=" + result()));
            }
        }
    }

    private static class QueryUserInfo {
        @SerializedName("userinfo")
        private UserInfo userInfo;

        public UserInfo userInfo() {
            return userInfo;
        }
    }
}
