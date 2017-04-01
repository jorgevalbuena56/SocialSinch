package com.social.backendless.database;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import com.social.backendless.model.ChatMessage;
import com.social.backendless.model.ChatStatus;
import com.social.backendless.utils.DateUtils;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import rx.schedulers.Schedulers;

/**
 * Class to handle the interaction with the local SQLite
 */

public class ChatBriteDataSource {
    private BriteDatabase mChatBriteDB;

    private static final String TAG = "ChatBriteDataSource";
    private static final Object mObjectLock = new Object();
    private static ChatBriteDataSource sChatBriteDataSource;

    public static ChatBriteDataSource getInstance(Context context) {
        if (sChatBriteDataSource == null) {
            sChatBriteDataSource = new ChatBriteDataSource(context);
        }
        return sChatBriteDataSource;
    }

    private ChatBriteDataSource(Context context) {
        ChatSQLiteHelper mChatDbHelper = new ChatSQLiteHelper(context);
        //create and configure sqlbrite
        SqlBrite sqlBrite = new SqlBrite.Builder().build();
        mChatBriteDB = sqlBrite.wrapDatabaseHelper(mChatDbHelper, Schedulers.io());
        mChatBriteDB.setLoggingEnabled(true);
    }

    /**
     * Increase in 1 the total messages exchanged between senderId and recipientId
     * @param identifier (senderId:recipientId)
     * @return
     */
    public void increaseTotalMessage(final String identifier) throws SQLException {
        String query = "INSERT " +
                "    OR REPLACE " +
                "INTO " +
                "    " + ChatSQLiteHelper.TABLE_TOTAL_MESSAGES + " ( " +
                ChatSQLiteHelper.COLUMN_ID + ", " +
                ChatSQLiteHelper.COLUMN_TOTAL_MESSAGES + " ) " +
                "VALUES " +
                "    (?, " +
                "        (SELECT " +
                "           CASE  " +
                "              WHEN exists(SELECT 1  FROM " +
                ChatSQLiteHelper.TABLE_TOTAL_MESSAGES +
                " WHERE " + ChatSQLiteHelper.COLUMN_ID +
                " LIKE ?) " +
                "              THEN (SELECT " + ChatSQLiteHelper.COLUMN_TOTAL_MESSAGES +
                " + 1 FROM " + ChatSQLiteHelper.TABLE_TOTAL_MESSAGES +
                " WHERE " + ChatSQLiteHelper.COLUMN_ID + " LIKE ?) " +
                "              ELSE 1 " +
                "           END " +
                "         ) " +
                "    )";
        mChatBriteDB.execute(query, identifier, identifier, identifier);
    }

    /**
     * Obtains the total messages exchanged between senderId and recipientId
     * @param identifier(senderId:recipientId)
     * @return
     * @throws SQLException
     */
    public Long getTotalMessage(final String identifier) throws SQLException {
        Long result = -1L;
        String queryTotalMessages = String.valueOf("SELECT " +
                ChatSQLiteHelper.COLUMN_TOTAL_MESSAGES + " FROM " +
                ChatSQLiteHelper.TABLE_TOTAL_MESSAGES + " WHERE " +
                ChatSQLiteHelper.COLUMN_ID + " LIKE ?");
        Cursor cursor = mChatBriteDB.query(queryTotalMessages, identifier);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = cursor.getLong(0);
        }
        return result;
    }
    /**
     * Changes the status for a message
     * @param messageId
     * @param status
     * @throws SQLException
     */
    public void updateMessageStatus(Long messageId, ChatStatus status) throws SQLException {
        String queryUpdateMessage = String.valueOf("UPDATE " +
                ChatSQLiteHelper.TABLE_MESSAGES + " SET " +
                ChatSQLiteHelper.COLUMN_STATUS + " = ?  WHERE " +
                ChatSQLiteHelper.COLUMN_ID_MSG + " = ? ");
        mChatBriteDB.execute(queryUpdateMessage, status.name(), String.valueOf(messageId));
    }

    public Long addNewMessage(ChatMessage chatMessage) throws SQLException {
        synchronized (mObjectLock) {
            //first increase total message between the users
            String identifier;
            if (chatMessage.getStatus().equals(ChatStatus.RECEIVED)) {
                identifier = String.valueOf(chatMessage.getRecipientId() + ":" +
                        chatMessage.getSenderId());
            } else {
                identifier = String.valueOf(chatMessage.getSenderId() + ":" +
                        chatMessage.getRecipientId());
            }
            increaseTotalMessage(identifier);
            //get the new total message
            Long totalMessages = getTotalMessage(identifier);
            mChatBriteDB.execute(
                    "INSERT INTO " + ChatSQLiteHelper.TABLE_MESSAGES +
                            " ( " + ChatSQLiteHelper.COLUMN_ID_MSG + ", " +
                            ChatSQLiteHelper.COLUMN_PARTICIPANTS + ", " +
                            ChatSQLiteHelper.COLUMN_MESSAGE + ", " +
                            ChatSQLiteHelper.COLUMN_FROM + ", " +
                            ChatSQLiteHelper.COLUMN_DATE + ", " +
                            ChatSQLiteHelper.COLUMN_STATUS
                            + " ) VALUES ( ?, ?, ? , ? , ?, ? ) ",
                    totalMessages, identifier, chatMessage.getTextBody(),
                    chatMessage.getSenderId(),
                    DateUtils.convertDateToString(chatMessage.getTimestamp()), chatMessage.getStatus());
            return totalMessages;
        }
    }

    public List<ChatMessage> retrieveLastMessages(final String user1,
                                                  final String user2,
                                                  final int max) throws SQLException{
        synchronized (mObjectLock) {
            List<ChatMessage> listConversations = new ArrayList<>();
            String participants = String.valueOf(user1 + ":" + user2);
            Long totalMessages = getTotalMessage(participants);

            if (totalMessages != -1) {
                String query = "SELECT * FROM " +
                        "( SELECT * FROM " +
                        ChatSQLiteHelper.TABLE_MESSAGES + " WHERE " +
                        ChatSQLiteHelper.COLUMN_PARTICIPANTS + " = ? ORDER BY " +
                        ChatSQLiteHelper.COLUMN_ID_MSG + " ASC " +
                        " ) ORDER BY " + ChatSQLiteHelper.COLUMN_ID_MSG + " ASC LIMIT ?";
                Cursor cursor = mChatBriteDB.query(query, participants,
                        totalMessages.toString());
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        Long id = cursor.getLong(0);
                        String fromUser = cursor.getString(3);
                        String toUser;
                        if (fromUser.equals(user1)) {
                            toUser = user2;
                        } else {
                            toUser = user1;
                        }
                        ChatMessage chatMessage = new ChatMessage(toUser, cursor.getString(2));
                        chatMessage.setMessageId(id);
                        chatMessage.setSenderId(fromUser);
                        chatMessage.setTimestamp(DateUtils.convertStringToDate(cursor.getString(4)));
                        chatMessage.setStatus(ChatStatus.fromString(cursor.getString(5)));
                        listConversations.add(chatMessage);
                        cursor.moveToNext();
                    }

                }
                cursor.close();
            }
            return listConversations;
        }
    }

    /**
     * Obtains only the last message sent/received to/from a specific user
     * @param user1
     * @param user2
     * @return
     * @throws SQLException
     */
    public ChatMessage retrieveLastMessage(final String user1,
                                           final String user2) throws SQLException{
        synchronized (mObjectLock) {
            String participants = String.valueOf(user1 + ":" + user2);

            String query = "SELECT * FROM " +
                    "( SELECT * FROM " +
                    ChatSQLiteHelper.TABLE_MESSAGES + " WHERE " +
                    ChatSQLiteHelper.COLUMN_PARTICIPANTS + " = ? ORDER BY " +
                    ChatSQLiteHelper.COLUMN_ID_MSG + " ASC " +
                    " ) ORDER BY " + ChatSQLiteHelper.COLUMN_ID_MSG + " DESC LIMIT 1";
            Cursor cursor = mChatBriteDB.query(query, participants);
            ChatMessage chatMessage;
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                Long id = cursor.getLong(0);
                String fromUser = cursor.getString(3);
                String toUser;
                if (fromUser.equals(user1)) {
                    toUser = user2;
                } else {
                    toUser = user1;
                }
                chatMessage = new ChatMessage(toUser, cursor.getString(2));
                chatMessage.setMessageId(id);
                chatMessage.setSenderId(fromUser);
                chatMessage.setTimestamp(DateUtils.convertStringToDate(cursor.getString(4)));
                chatMessage.setStatus(ChatStatus.fromString(cursor.getString(5)));
            } else {
                chatMessage = new ChatMessage(user2, "");
            }
            cursor.close();
            return chatMessage;
        }
    }

    public void closeDatabase() {
        mChatBriteDB.close();
    }
}
