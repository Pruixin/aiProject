<template>
  <div class="comment-node">
    <div class="comment-body">
      <div class="comment-avatar">
        <img v-if="comment.author?.avatar" :src="assetUrl(comment.author.avatar)" alt="comment-avatar" />
        <span v-else>{{ (comment.author?.nickName || '评').slice(0, 1) }}</span>
      </div>
      <div class="comment-main">
        <div class="comment-header">
          <button class="comment-name" type="button" @click="$emit('reply', comment)">
            {{ comment.author?.nickName || '用户' }}
          </button>
          <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
        </div>
        <div class="comment-content">
          <span v-if="comment.replyToUser?.nickName" class="reply-tag">@{{ comment.replyToUser.nickName }}</span>
          <span>{{ comment.content }}</span>
        </div>
        <div class="comment-actions">
          <button class="reply-link" type="button" @click="$emit('reply', comment)">回复</button>
        </div>
        <div v-if="isReplying" class="reply-editor">
          <el-input
            :model-value="replyDraft"
            maxlength="200"
            show-word-limit
            :placeholder="`回复 @${comment.author?.nickName || '用户'}：`"
            @update:model-value="$emit('update-reply', comment.id, $event)"
            @keyup.enter="$emit('submit-reply', comment)"
          />
          <div class="reply-actions">
            <el-button class="reply-btn" :loading="replyPending" type="primary" @click="$emit('submit-reply', comment)">发表</el-button>
            <el-button class="reply-btn" plain @click="$emit('cancel-reply', comment.id)">取消</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="comment.replies?.length" class="reply-children">
      <CommentThread
        v-for="child in comment.replies"
        :key="child.id"
        :comment="child"
        :asset-url="assetUrl"
        :format-time="formatTime"
        :replying-id="replyingId"
        :reply-drafts="replyDrafts"
        :comment-pending-map="commentPendingMap"
        @reply="forwardReply"
        @cancel-reply="forwardCancelReply"
        @update-reply="forwardUpdateReply"
        @submit-reply="forwardSubmitReply"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'CommentThread' })

const props = defineProps({
  comment: {
    type: Object,
    required: true,
  },
  // assetUrl 与 formatTime 从父层透传进来，保证递归子节点也能复用同一套格式化逻辑。
  assetUrl: {
    type: Function,
    required: true,
  },
  formatTime: {
    type: Function,
    required: true,
  },
  replyingId: {
    type: [Number, String, null],
    default: null,
  },
  replyDrafts: {
    type: Object,
    required: true,
  },
  commentPendingMap: {
    type: Object,
    required: true,
  },
})

// 组件本身不处理评论提交，只把事件继续抛给最外层的 Plaza/useSocialState。
const emit = defineEmits(['reply', 'cancel-reply', 'update-reply', 'submit-reply'])

// 当前这条评论是否正处于“打开回复输入框”的状态。
const isReplying = computed(() => props.replyingId === props.comment.id)
// 回复草稿按 comment.id 从外部映射对象里读取。
const replyDraft = computed(() => props.replyDrafts?.[props.comment.id] || '')
// 回复提交中的 loading 同样通过 comment.id 定位。
const replyPending = computed(() => !!props.commentPendingMap?.[`comment-${props.comment.id}`])

// 以下四个 forward 方法的意义是：
// 当子评论继续触发 reply/cancel/update/submit 时，把事件原样往上层转发，
// 这样整棵评论树只在最外层维护一份状态。
const forwardReply = (comment) => emit('reply', comment)
const forwardCancelReply = (commentId) => emit('cancel-reply', commentId)
const forwardUpdateReply = (commentId, value) => emit('update-reply', commentId, value)
const forwardSubmitReply = (comment) => emit('submit-reply', comment)
</script>

<style scoped lang="scss">
.comment-node {
  display: grid;
  gap: 10px;
}

.comment-body {
  display: flex;
  gap: 10px;
}

.comment-avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  overflow: hidden;
  flex: 0 0 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(46, 204, 113, 0.12);
  border: 1px solid rgba(82, 255, 147, 0.22);
  color: #bfffcf;
  font-size: 13px;
  font-weight: 800;
}

.comment-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.comment-main {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
}

.comment-header,
.comment-actions,
.reply-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-name,
.reply-link {
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.comment-name {
  color: #7effa8;
  font-weight: 800;
}

.comment-time {
  color: rgba(220, 228, 223, 0.62);
  font-size: 12px;
}

.comment-content {
  margin-top: 8px;
  color: #dfe7e0;
  line-height: 1.75;
  word-break: break-word;
}

.reply-tag {
  margin-right: 6px;
  color: #7effa8;
  font-weight: 700;
}

.comment-actions {
  margin-top: 8px;
}

.reply-link {
  color: rgba(220, 228, 223, 0.72);
}

.reply-link:hover,
.comment-name:hover {
  color: #9cffbe;
}

.reply-editor {
  margin-top: 10px;
  display: grid;
  gap: 10px;
}

.reply-actions {
  justify-content: flex-end;
}

.reply-btn {
  border-radius: 999px;
}

.reply-children {
  margin-left: 44px;
  padding-left: 12px;
  border-left: 1px solid rgba(82, 255, 147, 0.12);
  display: grid;
  gap: 10px;
}
</style>
