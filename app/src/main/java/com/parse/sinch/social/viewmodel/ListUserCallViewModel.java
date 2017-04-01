package com.parse.sinch.social.viewmodel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.databinding.BindingAdapter;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.backendless.BackendlessCollection;
import com.backendless.BackendlessUser;
import com.parse.sinch.social.LoginActivity;
import com.parse.sinch.social.adapter.UserChatAdapter;
import com.social.backendless.model.UserInfo;
import com.parse.sinch.social.utils.Constants;
import com.social.backendless.utils.LoggedUser;
import com.social.backendless.bus.RxIncomingEventBus;
import com.social.backendless.data.DataManager;
import com.social.backendless.model.EventMessage;
import com.social.backendless.model.EventStatus;
import com.social.backendless.utils.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;

/**
 * Class to encapsulates the logic to display the calls received/made by the user in the list
 */

public class ListUserCallViewModel {
    private Context mContext;
    private UserChatAdapter mUserCallsAdapter;
    private List<String> mUserContactIds;
    private boolean mShowPanel;

    private static final String TAG = "ListUserCallViewModel";

    public ListUserCallViewModel(Context context) {
        this.mContext = context;
        this.mUserCallsAdapter = new UserChatAdapter(context);
        this.mUserContactIds = new ArrayList<>();
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
                                                ListUserCallViewModel viewModel) {
        viewModel.getUserCalls(recyclerView);
        recyclerView.setAdapter(viewModel.getAdapter());
        recyclerView.setLayoutManager(viewModel.createLayoutManager());
    }
    private void getUserCalls(final RecyclerView callsRecyclerView) {
        DataManager.getFetchAllUsersObservable(LoggedUser.getInstance().getUserIdLogged())
                .doOnSubscribe(new Action0() {
            @Override
            public void call() {
                  setShowPanel(true);
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Object>() {
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
                    public void onNext(Object data) {
                        processResponse(callsRecyclerView, data);
                        setShowPanel(false);
                    }
                });
    }

    private void processResponse(final RecyclerView callsRecyclerView, Object data) {
        if (data instanceof BackendlessCollection) {
            BackendlessCollection<BackendlessUser> users =
                    (BackendlessCollection<BackendlessUser>) data;
            mUserCallsAdapter.setUserCalls(convertToUserInfo(users));
        } else {
            //reset the previous list of calls
            mUserCallsAdapter = new UserChatAdapter(mContext);
            redirectToLogin();
        }
        callsRecyclerView.setAdapter(mUserCallsAdapter);
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

    //TODO refactor this to remove backendless user dependency
    private List<UserInfo> convertToUserInfo(BackendlessCollection<BackendlessUser> calls) {
        List<BackendlessUser> userList = calls.getData();
        UserInfo user;
        List<UserInfo> lstUsers = new ArrayList<>();
        for (BackendlessUser backendlessUser : userList) {
            user = new UserInfo();
            user.setObjectId(backendlessUser.getObjectId());
            user.setFullName((String) backendlessUser.getProperty("full_name"));
            user.setPhoneNumber((String) backendlessUser.getProperty("phone"));
            user.setProfilePicture((String) backendlessUser.getProperty("avatar"));
            Date lastTimeSeen = (Date) backendlessUser.getProperty("last_seen");
            Date lastLogin = (Date) backendlessUser.getProperty("lastLogin");
            if (lastTimeSeen == null) {
                user.setLastSeen(DateUtils.convertDateToString(lastLogin));
            } else {
                user.setLastSeen(DateUtils.convertDateToString(lastTimeSeen));
            }
            lstUsers.add(user);
            mUserContactIds.add(user.getObjectId());
            if (DateUtils.isSameDay(user.getLastSeen())) {
                EventMessage eventMessage = new EventMessage(LoggedUser.getInstance().getUserIdLogged(),
                        user.getObjectId(),
                        EventStatus.ONLINE.toString(),
                        EventStatus.ONLINE);
                RxIncomingEventBus.getInstance().sendEvent(eventMessage);
            }
        }
        return lstUsers;
    }

    public void notifyConnectionStatus(String message) {
        EventMessage eventMessage;
        for (String contactId : mUserContactIds) {
            eventMessage = new EventMessage(LoggedUser.getInstance().getUserIdLogged(),
                    contactId,
                    message,
                    EventStatus.OFFLINE);
            RxIncomingEventBus.getInstance().sendEvent(eventMessage);
        }
    }
}
