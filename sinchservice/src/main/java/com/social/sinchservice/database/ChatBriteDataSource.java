package com.social.sinchservice.database;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;

import com.social.sinchservice.model.ChatMessage;
import com.social.sinchservice.model.ChatStatus;
import com.social.sinchservice.utils.DateUtils;
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

    public ChatBriteDataSource(Context context) {
        ChatSQLiteHelper mChatDbHelper = new ChatSQLiteHelper(context);
        //create and configure sqlbrite
        SqlBrite sqlBrite = new SqlBrite.Builder().build();
        mChatBriteDB = sqlBrite.wrapDatabaseHelper(mChatDbHelper, Schedulers.io());
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
                ChatSQLiteHelper.TABLE_TOTAL_MESSAGES) + " WHERE " +
                ChatSQLiteHelper.COLUMN_ID + " LIKE ?";
        Cursor cursor = mChatBriteDB.query(queryTotalMessages, identifier);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = cursor.getLong(0);
        }
        return result;
    }

    /**
     * Verify a sent message is already in DB using the sentID received
     * @param senderId
     * @param recipientId
     * @param sentId
     * @return
     * @throws SQLException
     */
    public Long verifyMessageBySentID(final String senderId,
                              final String recipientId,
                              final String sentId) throws SQLException {
        Long messageId = -1L;
        String identifier = String.valueOf(senderId + ":" + recipientId);
        String queryMessages = String.valueOf("SELECT " +
                ChatSQLiteHelper.COLUMN_ID_MSG + " FROM " +
                ChatSQLiteHelper.TABLE_MESSAGES) + " WHERE " +
                ChatSQLiteHelper.COLUMN_PARTICIPANTS + " = ? AND " +
                ChatSQLiteHelper.COLUMN_SENT_ID + " = ? ";
        Cursor cursor = mChatBriteDB.query(queryMessages, identifier, sentId);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            messageId = cursor.getLong(0);
        }
        return messageId;
    }

    /**
     * Verify if a message received already exits with the same timestamp
     * @param senderId
     * @param recipientId
     * @param timestamp
     * @return
     * @throws SQLException
     */
    public Long verifyMessageByDate(final String senderId,
                                    final String recipientId,
                                    final Date timestamp,
                                    final String textBody) throws SQLException {
        Long messageId = -1L;
        String identifier = String.valueOf(senderId + ":" + recipientId);
        String queryMessages = String.valueOf("SELECT " +
                ChatSQLiteHelper.COLUMN_ID_MSG + " FROM " +
                ChatSQLiteHelper.TABLE_MESSAGES) + " WHERE " +
                ChatSQLiteHelper.COLUMN_PARTICIPANTS + " = ? AND " +
                ChatSQLiteHelper.COLUMN_DATE + " = ? AND " +
                ChatSQLiteHelper.COLUMN_MESSAGE + " = ? ";
        Cursor cursor = mChatBriteDB.query(queryMessages, identifier,
                DateUtils.convertDateToString(timestamp), textBody);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            messageId = cursor.getLong(0);
        }
        return messageId;
    }
    /**
     * Changes the status for a message
     * @param messageId
     * @param status
     * @throws SQLException
     */
    public void updateMessageStatus(Long messageId, ChatStatus status) throws SQLException {
        String queryUpdateMessage = String.valueOf("UPDATE " +
                ChatSQLiteHelper.TABLE_MESSAGES) + " SET " +
                ChatSQLiteHelper.COLUMN_STATUS + " = ?  WHERE " +
                ChatSQLiteHelper.COLUMN_ID_MSG + " = ? ";
        mChatBriteDB.execute(queryUpdateMessage, status.name(), String.valueOf(messageId));
    }

    public Long addNewMessage(ChatMessage chatMessage) throws SQLException {


        //first increase total message between the users
        String identifier;
        if (chatMessage.getStatus().equals(ChatStatus.RECEIVED)) {
            identifier = String.valueOf(chatMessage.getRecipientIds().get(0) + ":" +
                                        chatMessage.getSenderId());
        } else {
            identifier = String.valueOf(chatMessage.getSenderId() + ":" +
                                        chatMessage.getRecipientIds().get(0));
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
                        ChatSQLiteHelper.COLUMN_SENT_ID + ", " +
                        ChatSQLiteHelper.COLUMN_DATE + ", " +
                        ChatSQLiteHelper.COLUMN_STATUS
                        + " ) VALUES ( ?, ?, ? , ? , ?, ?, ? ) ",
                totalMessages, identifier, chatMessage.getTextBody(),
                chatMessage.getSenderId(), chatMessage.getSentId(),
                DateUtils.convertDateToString(chatMessage.getTimestamp()), chatMessage.getStatus());
        return totalMessages;
    }

    public List<ChatMessage> retrieveLastMessages(final String user1,
                                                  final String user2,
                                                  final int max) throws SQLException{
        List<ChatMessage> listConversations = new ArrayList<>();
        String participants = String.valueOf(user1 + ":" + user2);
        Long totalMessages = getTotalMessage(participants);
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
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setMessageId(id);
                chatMessage.setSenderId(fromUser);
                List<String> recipientIds = new ArrayList<>();
                if (fromUser.equals(user1)) {
                    recipientIds.add(user2);
                } else {
                    recipientIds.add(user1);
                }
                chatMessage.setRecipientIds(recipientIds);
                chatMessage.setTextBody(cursor.getString(2));
                listConversations.add(chatMessage);
                cursor.moveToNext();
            }
            cursor.close();
        }
        return listConversations;
    }
}
