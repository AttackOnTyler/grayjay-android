package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.views.behavior.NonScrollingTextView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.segments.CommentsList
import userpackage.Protocol

class RepliesOverlay : LinearLayout {
    val onClose = Event0();

    private val _topbar: OverlayTopbar;
    private val _commentsList: CommentsList;
    private val _addCommentView: AddCommentView;
    private val _textBody: NonScrollingTextView;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _creatorThumbnail: CreatorThumbnail;
    private val _layoutParentComment: ConstraintLayout;
    private var _readonly = false;
    private var _onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_replies, this)
        _topbar = findViewById(R.id.topbar);
        _commentsList = findViewById(R.id.comments_list);
        _addCommentView = findViewById(R.id.add_comment_view);
        _textBody = findViewById(R.id.text_body)
        _textMetadata = findViewById(R.id.text_metadata)
        _textAuthor = findViewById(R.id.text_author)
        _creatorThumbnail = findViewById(R.id.image_thumbnail)
        _layoutParentComment = findViewById(R.id.layout_parent_comment)

        _addCommentView.onCommentAdded.subscribe {
            _commentsList.addComment(it);
            _onCommentAdded?.invoke(it);
        }

        _commentsList.onCommentsLoaded.subscribe { count ->
            if (_readonly && count == 0) {
                UIDialogs.toast(context, context.getString(R.string.expected_at_least_one_reply_but_no_replies_were_returned_by_the_server));
            }
        }

        _commentsList.onRepliesClick.subscribe { c ->
            val replyCount = c.replyCount;
            var metadata = "";
            if (replyCount != null && replyCount > 0) {
                metadata += "$replyCount " + context.getString(R.string.replies);
            }

            if (c is PolycentricPlatformComment) {
                load(false, metadata, c.contextUrl, c.reference, c, { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) });
            } else {
                load(true, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) });
            }
        };

        _topbar.onClose.subscribe(this, onClose::emit);
        _topbar.setInfo(context.getString(R.string.Replies), "");
    }

    fun load(readonly: Boolean, metadata: String, contextUrl: String?, ref: Protocol.Reference?, parentComment: IPlatformComment? = null, loader: suspend () -> IPager<IPlatformComment>, onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null) {
        _readonly = readonly;
        if (readonly) {
            _addCommentView.visibility = View.GONE;
        } else {
            _addCommentView.visibility = View.VISIBLE;
            _addCommentView.setContext(contextUrl, ref);
        }

        if (parentComment == null) {
            _layoutParentComment.visibility = View.GONE
        } else {
            _layoutParentComment.visibility = View.VISIBLE

            _textBody.text = parentComment.message.fixHtmlLinks()
            _textAuthor.text = parentComment.author.name

            val date = parentComment.date
            if (date != null) {
                _textMetadata.visibility = View.VISIBLE
                _textMetadata.text = " • ${date.toHumanNowDiffString()} ago"
            } else {
                _textMetadata.visibility = View.GONE
            }

            _creatorThumbnail.setThumbnail(parentComment.author.thumbnail, false);
            _creatorThumbnail.setHarborAvailable(parentComment is PolycentricPlatformComment,false);
        }

        _topbar.setInfo(context.getString(R.string.Replies), metadata);
        _commentsList.load(readonly, loader);
        _onCommentAdded = onCommentAdded;
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
        _onCommentAdded = null;
        _commentsList.cancel();
    }
}