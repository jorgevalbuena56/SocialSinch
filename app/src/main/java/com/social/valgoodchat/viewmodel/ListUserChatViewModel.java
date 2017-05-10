package com.social.valgoodchat.viewmodel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.databinding.BindingAdapter;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.social.backendless.data.ContactInformationManager;
import com.social.backendless.data.DataManager;
import com.social.backendless.model.CurrentChatsEvent;
import com.social.backendless.utils.LoggedUser;
import com.social.valgoodchat.LoginActivity;
import com.social.valgoodchat.adapter.UserChatAdapter;
import com.social.valgoodchat.utils.Constants;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Class to encapsulates the logic to display the calls received/made by the user in the list
 */

public class ListUserChatViewModel {
    private Context mContext;
    private UserChatAdapter mUserCallsAdapter;
    private ContactInformationManager mContactInformationManager;
    private boolean mShowPanel;

    private static final String TAG = "ListUserCallViewModel";

    public ListUserChatViewModel(Context context) {
        this.mContext = context;
        this.mUserCallsAdapter = new UserChatAdapter(context);
        this.mContactInformationManager = new ContactInformationManager(context);
    }
    private UserChatAdapter getAdapter() {
        return mUserCallsAdapter;
    }

    private RecyclerView.LayoutManager createLayoutManager() {
        return new LinearLayoutManager(mContext);
    }
    public boolean getShowPanel() {
        return mShowPanel;
    }

    public void setShowPanel(boolean showPanel) {
        this.mShowPanel = showPanel;
    }
    @BindingAdapter("userCallViewModel")
    public static void setUserCallViewModel(RecyclerView recyclerView,
                                                ListUserChatViewModel viewModel) {
        viewModel.getUserChats(recyclerView);
        recyclerView.setAdapter(viewModel.getAdapter());
        recyclerView.setLayoutManager(viewModel.createLayoutManager());
    }
    /**
     * Get all the users information from the backend
     * @param callsRecyclerView
     */
    private void getUserChats(final RecyclerView callsRecyclerView) {
        DataManager.getFetchAllUsersObservable(LoggedUser.getInstance().getUserIdLogged())
                .doOnSubscribe(() -> setShowPanel(true)).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<CurrentChatsEvent>() {
                    @Override
                    public void onCompleted() {
                        //empty implementation doesn't apply here
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "Error trying to get the List of Calls: " + e.getMessage());
                        redirectToLogin();
                    }

                    @Override
                    public void onNext(CurrentChatsEvent data) {
                        processResponse(callsRecyclerView, data);
                        setShowPanel(false);
                    }
                });
    }
    /**
     * Fill in the adapter with the information obtained from the backend
     * @param callsRecyclerView
     * @param data
     */
    private void processResponse(final RecyclerView callsRecyclerView, CurrentChatsEvent data) {
        if (data.getCode().equals(com.social.backendless.utils.Constants.SUCCESS_CODE)) {
            mContactInformationManager.verifyContactInformationChanges(data.getCharUserInfo());
            mUserCallsAdapter.setUserChats(data.getCharUserInfo());
        } else {
            //reset the previous list of calls
            mUserCallsAdapter = new UserChatAdapter(mContext);
            redirectToLogin();
        }
        callsRecyclerView.setAdapter(mUserCallsAdapter);
    }
    /**
     * Method called by the fragment to force a last message update
     */
    public void refreshLastMessage() {
        mUserCallsAdapter.refreshLastMessage();
    }

    /**
     * After an error getting the users, redirect to login screen
     */
    private void redirectToLogin() {
        Intent intent = new Intent(mContext, LoginActivity.class);
        intent.putExtra(Constants.FAULT_REFRESH_TOKEN, true);
        mContext.startActivity(intent);
        if (mContext instanceof Activity) {
            ((Activity) mContext).finish();
        }
    }
}
