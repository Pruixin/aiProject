<template>
  <div class="plaza-page">
    <div class="page-card">
      <div class="card-title">悦吧动态</div>
      <div class="publish-box">
        <div class="publish-head">
          <div>
            <div class="publish-title">分享今天的训练、饮食或搭子招募</div>
            <div class="publish-tip">用更轻松的方式记录运动日常，让同城同目标的人更快看见你</div>
          </div>
          <el-select v-model="postForm.topic" class="topic-select">
            <el-option label="训练打卡" value="训练打卡" />
            <el-option label="饮食分享" value="饮食分享" />
            <el-option label="搭子招募" value="搭子招募" />
            <el-option label="日常碎片" value="日常碎片" />
          </el-select>
        </div>
        <el-input
          v-model="postForm.content"
          class="publish-input"
          type="textarea"
          :rows="4"
          maxlength="600"
          show-word-limit
          placeholder="分享今天的运动成果、饮食打卡，或者寻找同城搭子..."
        />
        <div class="publish-actions">
          <div class="publish-actions-left">
            <el-upload
              class="upload-list"
              :show-file-list="false"
              accept="image/*"
              :http-request="handlePostImageUpload"
            >
              <el-button class="soft-button" plain>上传图片</el-button>
            </el-upload>
            <div class="image-chips">
              <span v-for="(img, index) in postForm.images" :key="`${img}-${index}`" class="chip">
                图片{{ index + 1 }}
              </span>
            </div>
          </div>
          <el-button class="publish-btn" type="primary" :loading="publishing" @click="handlePublishPost">发布动态</el-button>
        </div>
      </div>

      <div class="feed-list">
        <article v-for="post in feed" :key="post.id" class="feed-card">
          <div class="feed-head">
            <div class="feed-author">
              <div class="author-avatar">{{ (post.author?.nickName || '匿').slice(0, 1) }}</div>
              <div>
                <div class="feed-name">{{ post.author?.nickName || '匿名用户' }}</div>
                <div class="feed-meta">{{ post.author?.locationName || '未知地区' }} · {{ post.topic || post.postType || '动态分享' }}</div>
              </div>
            </div>
            <div class="feed-time">{{ formatTime(post.createTime) }}</div>
          </div>

          <div class="feed-content">{{ post.content }}</div>

          <div v-if="post.images?.length" class="feed-images">
            <img v-for="(img, index) in post.images" :key="`${post.id}-${index}`" :src="assetUrl(img)" alt="post-image" />
          </div>

          <div class="feed-actions">
            <button class="action-pill" :class="{ active: post.liked, loading: likePendingMap[post.id] }" :disabled="!!likePendingMap[post.id]" @click="handleLike(post)">
              <span class="action-icon">{{ post.liked ? '♥' : '♡' }}</span>
              <span>{{ post.liked ? '已点赞' : '点赞' }}</span>
              <span>{{ post.likeCount || 0 }}</span>
            </button>
            <div class="action-pill passive">
              <span class="action-icon">💬</span>
              <span>评论</span>
              <span>{{ post.commentCount || 0 }}</span>
            </div>
          </div>

          <div v-if="post.comments?.length" class="comment-list">
            <CommentThread
              v-for="comment in post.comments"
              :key="comment.id"
              :comment="comment"
              :asset-url="assetUrl"
              :format-time="formatTime"
              :replying-id="activeReplyTarget?.commentId"
              :reply-drafts="replyDrafts"
              :comment-pending-map="commentPendingMap"
              @reply="(commentItem) => openCommentReply(post, commentItem)"
              @cancel-reply="cancelCommentReply"
              @update-reply="updateReplyDraft"
              @submit-reply="(commentItem) => handleComment(post, commentItem)"
            />
          </div>

          <div class="comment-editor">
            <el-input v-model="commentDrafts[post.id]" maxlength="200" show-word-limit placeholder="写下你的评论" @keyup.enter="handleComment(post)" />
            <el-button class="soft-button" :loading="!!commentPendingMap[`post-${post.id}`]" @click="handleComment(post)">发送</el-button>
          </div>
        </article>
        <el-empty v-if="!feed.length" description="还没有社区内容，发布第一条动态吧" />
      </div>
    </div>
  </div>
</template>

<script setup>
import CommentThread from './CommentThread.vue'
import { useSocialState } from './useSocialState'

// 悦吧页同样不重复实现业务逻辑：
// 发布动态、上传图片、点赞、评论、回复等动作全部转交给 `useSocialState`。
const {
  publishing,
  feed,
  commentDrafts,
  replyDrafts,
  activeReplyTarget,
  commentPendingMap,
  postForm,
  assetUrl,
  formatTime,
  likePendingMap,
  handlePostImageUpload,
  handlePublishPost,
  handleLike,
  handleComment,
  openCommentReply,
  cancelCommentReply,
} = useSocialState()

const updateReplyDraft = (commentId, value) => {
  // 回复输入框由 commentId 定位，便于同一动态下同时维护多条草稿。
  // replyDrafts 是一个以 commentId 为 key 的对象映射。
  replyDrafts[commentId] = value
}
</script>

<style scoped lang="scss">
.plaza-page {
  width: 100%;
}

.page-card {
  border-radius: 28px;
  padding: 20px;
  background:
    radial-gradient(circle at top right, rgba(46, 204, 113, 0.12), transparent 26%),
    linear-gradient(180deg, rgba(18, 18, 18, 0.96), rgba(10, 10, 10, 0.96));
  border: 1px solid rgba(82, 255, 147, 0.14);
  box-shadow: 0 22px 60px rgba(0, 0, 0, 0.26);
}

.card-title {
  margin-bottom: 16px;
  font-size: 18px;
  font-weight: 900;
  color: rgba(248, 255, 250, 0.96);
}

.publish-box,
.feed-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(25, 25, 25, 0.94), rgba(15, 15, 15, 0.94));
  border: 1px solid rgba(82, 255, 147, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.feed-card:hover,
.publish-box:hover {
  transform: translateY(-2px);
  border-color: rgba(82, 255, 147, 0.18);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.26);
}

.publish-head,
.feed-head,
.feed-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.publish-actions,
.publish-actions-left,
.feed-author {
  display: flex;
  align-items: center;
  gap: 12px;
}

.publish-title {
  font-size: 16px;
  font-weight: 800;
  color: rgba(250, 255, 251, 0.94);
}

.publish-tip,
.feed-meta,
.feed-time,
.comment-item {
  color: rgba(220, 228, 223, 0.74);
  font-size: 13px;
}

.feed-list {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.feed-name {
  font-size: 16px;
  font-weight: 900;
  color: rgba(248, 255, 250, 0.96);
}

.feed-content {
  margin-top: 14px;
  color: #dde6de;
  line-height: 1.85;
}

.author-avatar {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(46, 204, 113, 0.16);
  border: 1px solid rgba(82, 255, 147, 0.4);
  color: #bfffcf;
  font-size: 18px;
  font-weight: 900;
}

.feed-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.feed-images img {
  width: 136px;
  height: 136px;
  border-radius: 16px;
  object-fit: cover;
  border: 1px solid rgba(82, 255, 147, 0.12);
}

.comment-list {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.comment-editor {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.topic-select {
  width: 180px;
}

.publish-input :deep(.el-textarea__inner),
.comment-editor :deep(.el-input__wrapper) {
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.04);
  box-shadow: none;
}

.soft-button,
.publish-btn {
  border-radius: 999px;
}

.upload-list {
  display: inline-flex;
}

.image-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(36, 207, 95, 0.12);
  border: 1px solid rgba(36, 207, 95, 0.24);
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 800;
}

.feed-actions {
  margin-top: 16px;
  align-items: center;
}

.action-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid rgba(82, 255, 147, 0.14);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(238, 246, 240, 0.86);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-pill:hover:not(:disabled) {
  transform: translateY(-1px);
  background: rgba(46, 204, 113, 0.12);
  border-color: rgba(82, 255, 147, 0.28);
}

.action-pill:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.action-pill.active {
  color: #7effa8;
  background: rgba(46, 204, 113, 0.14);
  border-color: rgba(82, 255, 147, 0.32);
}

.action-pill.passive {
  cursor: default;
}

.action-pill.passive:hover {
  transform: none;
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(82, 255, 147, 0.14);
}

.action-pill.loading {
  opacity: 0.78;
}

.action-icon {
  font-size: 16px;
  line-height: 1;
}

@media (max-width: 900px) {
  .publish-head,
  .publish-actions,
  .publish-actions-left,
  .feed-head,
  .feed-actions,
  .comment-editor {
    display: grid;
    grid-template-columns: 1fr;
  }

  .topic-select {
    width: 100%;
  }
}
</style>
