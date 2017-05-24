package com.vanniktech.emoji.custom;

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiVariantPopup;
import com.vanniktech.emoji.EmojiView;
import com.vanniktech.emoji.RecentEmoji;
import com.vanniktech.emoji.RecentEmojiManager;
import com.vanniktech.emoji.R;
import com.vanniktech.emoji.listeners.OnEmojiClickedListener;
import com.vanniktech.emoji.listeners.OnEmojiLongClickedListener;

/**
 * Gif View that is shown inside the bottom pop up
 */

public class EmojiGIFView extends FrameLayout {
    private RecentEmoji mRecentEmoji;
    private EmojiVariantPopup mVariantPopup;

    private static final String TAG = "EmojiGIFView";

    public EmojiGIFView(View rootView, EmojiEditText emojiEditText) {
        super(rootView.getContext());
        init(rootView, emojiEditText);
    }

    private void init(View rootView, EmojiEditText emojiEditText) {
        LayoutInflater.from(getContext()).inflate(R.layout.general_layout_view, this, true);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.emoji_background));

        //index 0 add the emoji view visible
        addView(getEmojiView(rootView, emojiEditText), 0);
        Toolbar bottomToolbar = (Toolbar) findViewById(R.id.toolbarBottomMenu);
        //index 1 add the gif grid view invisible
        CustomEmoticonView gifGridView = new CustomEmoticonView(getContext(), bottomToolbar);
        gifGridView.hideView();
        addView(gifGridView, 1);

        initEmojiClickListener();
        initGifClickListener();
    }

    private void initEmojiClickListener() {
        findViewById(R.id.emoji_button).setOnClickListener(view -> {
            toggle();
        });
    }

    private void initGifClickListener() {
        findViewById(R.id.gif_button).setOnClickListener(view -> {
            toggle();
        });
    }

    public void toggle() {
        //based on the dynamically addition done in init, the position indexes must always match
        EmojiView emojiView = (EmojiView) getChildAt(0);
        CustomEmoticonView gifGridView = (CustomEmoticonView) getChildAt(1);
        if (gifGridView.isShown()) {
            gifGridView.hideView();
            emojiView.showView();
        } else {
            emojiView.hideView();
            gifGridView.showView();
        }
    }

    private EmojiView getEmojiView(View rootView, EmojiEditText emojiEditText) {
        mRecentEmoji = new RecentEmojiManager(getContext());

        final OnEmojiLongClickedListener longClickListener = (view, emoji) -> mVariantPopup.show(view, emoji);

        final OnEmojiClickedListener clickListener = emoji -> {
            emojiEditText.input(emoji);
            mRecentEmoji.addEmoji(emoji);
            mVariantPopup.dismiss();
        };

        mVariantPopup = new EmojiVariantPopup(rootView, clickListener);

        final EmojiView emojiView = new EmojiView(getContext(), clickListener, longClickListener, mRecentEmoji);
        emojiView.setOnEmojiBackspaceClickListener(v -> emojiEditText.backspace());
        return emojiView;
    }


//    @Override
//    public void onLoadingStarted() {
//        findViewById(R.id.progressBar).setVisibility(VISIBLE);
//    }
//
//    @Override
//    public void onLoadFinished() {
//        findViewById(R.id.progressBar).setVisibility(INVISIBLE);
//    }

    @Override
    protected void onDetachedFromWindow() {
        mVariantPopup.dismiss();
        mRecentEmoji.persist();
        super.onDetachedFromWindow();
    }
}