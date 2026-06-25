<template>
  <div class="grid">
    <div class="card span-4">
      <div class="card-title">聊天中心</div>

      <div v-if="friendRequests.length" class="panel request-panel">
        <div class="panel-title">好友申请</div>
        <div class="request-list">
          <div v-for="request in friendRequests" :key="request.id" class="request-item">
            <div class="request-name">{{ request.fromUser?.nickName || '未命名用户' }}</div>
            <div class="request-meta">{{ request.fromUser?.locationName || '未知地区' }} · {{ formatTime(request.createTime)
              }}</div>
            <div class="request-remark">{{ request.remark || '我想和你成为搭子' }}</div>
            <div class="request-actions">
              <el-button size="small" type="primary" :loading="requestActionLoading"
                @click="acceptIncomingRequest(request)">同意</el-button>
              <el-button size="small" plain :loading="requestActionLoading"
                @click="rejectIncomingRequest(request)">拒绝</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="panel friend-panel">
        <div class="panel-title">我的好友</div>
        <div class="friend-list">
          <div v-for="friend in friends" :key="friend.id" class="friend-item" @dblclick="startChatWithFriend(friend)"
            @contextmenu.prevent="openContextMenu($event, 'friend', friend)">
            <img v-if="friend.user?.avatar" :src="assetUrl(friend.user.avatar)" class="friend-avatar"
              alt="friend-avatar" />
            <div v-else class="friend-avatar empty">{{ (friend.user?.nickName || '友').slice(0, 1) }}</div>
            <div class="friend-content">
              <div class="friend-name">{{ friend.user?.nickName || '未命名用户' }}</div>
              <div class="friend-meta">{{ friend.user?.locationName || '未知地区' }}</div>
            </div>
          </div>
          <el-empty v-if="!friends.length" description="还没有好友，先去匹配搭子吧" />
        </div>
      </div>

      <div class="panel session-panel">
        <div class="panel-title">最近聊天</div>
        <div class="session-list">
          <button v-for="session in sessions" :key="session.id" class="session-item"
            :class="{ active: activeSession?.id === session.id }" @click="selectSession(session)"
            @contextmenu.prevent="openContextMenu($event, 'session', session)">
            <div class="session-main">
              <img v-if="session.targetUser?.avatar" :src="assetUrl(session.targetUser.avatar)" class="friend-avatar"
                alt="session-avatar" />
              <div v-else class="friend-avatar empty">{{ (session.targetUser?.nickName || '聊').slice(0, 1) }}</div>
              <div class="session-content">
                <div class="session-top">
                  <div class="session-name">{{ session.targetUser?.nickName || '未命名用户' }}</div>
                  <span class="session-time">{{ formatTime(session.lastMessageTime) }}</span>
                </div>
                <div class="session-meta">{{ session.lastMessage || '点击开始聊天' }}</div>
                <div class="session-row">
                  <span>{{ session.targetUser?.locationName || '未知地区' }}</span>
                  <span v-if="Number(session.unreadCount) > 0" class="badge">{{ session.unreadCount }}</span>
                </div>
              </div>
            </div>
          </button>
          <el-empty v-if="!sessions.length" description="还没有聊天会话" />
        </div>
      </div>
    </div>

    <div class="card span-8">
      <div class="card-title">聊天窗口</div>
      <div v-if="!isFallbackMode && socketStatus !== 'connected' && socketStatusMessage" class="socket-banner"
        :class="`socket-${socketStatus}`">
        {{ socketStatusMessage }}
      </div>
      <div v-if="activeSession" class="chat-panel">
        <div class="chat-header">
          <div class="chat-name">{{ activeSession.targetUser?.nickName || '聊天对象' }}</div>
          <div class="chat-sub">
            {{ activeSession.targetUser?.locationName || '未知地区' }}
            <span v-if="!activeSession.canChat"> · 当前已不是好友</span>
          </div>
        </div>
        <div ref="messageListRef" class="chat-messages">
          <div v-for="message in messages" :key="message.id" class="message-row" :class="{ mine: message.isMine }">
            <div class="message-bubble">{{ message.content }}</div>
          </div>
          <el-empty v-if="!messages.length" description="发送第一条消息，开启今天的运动约练吧" />
        </div>
        <div class="chat-send">
          <el-input v-model="chatDraft" :disabled="!activeSession.canChat"
            :placeholder="activeSession.canChat ? '输入想说的话，比如一起约周末晨跑' : '对方已不是你的好友，请重新申请后再聊天'"
            @keyup.enter="handleSendMessage" />
          <el-button type="primary" :disabled="!activeSession.canChat" @click="handleSendMessage">发送</el-button>
        </div>
      </div>
      <el-empty v-else description="请先去搭子匹配页发起聊天，或选择已有会话" />
    </div>

    <div v-if="menuState.visible" class="context-menu" :style="{ left: `${menuState.x}px`, top: `${menuState.y}px` }">
      <button class="context-item" @click="handleDeleteFriend">删除好友</button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useSocialState } from './useSocialState'

// 聊天页本身几乎不持有业务状态，核心数据都来自 `useSocialState`：
// 好友列表、申请列表、会话列表、消息列表以及 WebSocket 状态全部由状态中心统一维护。
const {
  sessions,
  friends,
  activeSession,
  messages,
  friendRequests,
  chatDraft,
  requestActionLoading,
  isFallbackMode,
  socketStatus,
  socketStatusMessage,
  assetUrl,
  selectSession,
  handleSendMessage,
  formatTime,
  acceptIncomingRequest,
  rejectIncomingRequest,
  deleteFriendByUser,
  startChatWithFriend,
} = useSocialState()

// 这里解构出的状态大致对应模板三块区域：
// 1. friendRequests -> 顶部好友申请面板；
// 2. friends / sessions -> 左侧好友与会话列表；
// 3. activeSession / messages / chatDraft -> 右侧聊天窗口。

// 右键菜单只在当前页面本地维护坐标和目标项，用于“删除好友”这类上下文操作。
const menuState = reactive({
  visible: false,
  x: 0,
  y: 0,
  type: null,
  session: null,
})
const messageListRef = ref(null)

const closeSessionMenu = () => {
  // 点击空白处或执行操作后关闭右键菜单。
  menuState.visible = false
  menuState.type = null
  menuState.session = null
}

const openContextMenu = (event, type, item) => {
  // 记录当前右键点击的对象，后续统一走删除逻辑。
  // type 用来区分来源是好友列表还是会话列表。
  menuState.visible = true
  menuState.x = event.clientX
  menuState.y = event.clientY
  menuState.type = type
  menuState.session = item
}

const handleDeleteFriend = async () => {
  // 好友列表和会话列表都可能触发“删除好友”，
  // 这里根据来源提取不同的数据结构再委托给状态中心处理。
  const item = menuState.session
  const type = menuState.type
  closeSessionMenu()
  if (type === 'friend') {
    // 好友列表项里目标用户挂在 `item.user` 上。
    await deleteFriendByUser(item.user || item)
    return
  }
  // 会话项里目标用户挂在 `item.targetUser` 上。
  await deleteFriendByUser(item.targetUser)
}

// 监听全局点击关闭右键菜单，避免菜单悬停残留。
window.addEventListener('click', closeSessionMenu)
watch(
  [() => activeSession.value?.id, () => messages.value.length],
  async ([sessionId, msgLen]) => {
    if (msgLen !== undefined) {
      console.log('[CHAT-WATCH] messages.length changed to', msgLen, 'sessionId', sessionId, 'messages:', messages.value.map(m => ({ id: m.id, content: m.content?.slice(0, 20), isMine: m.isMine })))
    }
    await nextTick()
    const messageList = messageListRef.value
    if (!messageList) return
    messageList.scrollTop = messageList.scrollHeight
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  window.removeEventListener('click', closeSessionMenu)
})
</script>

<style scoped lang="scss">
.grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
}

.card {
  border-radius: 22px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.card-title {
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.92);
}

.span-4 {
  grid-column: span 4;
}

.span-8 {
  grid-column: span 8;
}

.session-list {
  display: grid;
  gap: 10px;
  overflow: auto;
}

.session-item {
  width: 100%;
  text-align: left;
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  color: #fff;
  cursor: pointer;
}

.session-item.active {
  border-color: rgba(36, 207, 95, 0.45);
  box-shadow: inset 0 0 0 1px rgba(36, 207, 95, 0.28);
}

.session-main {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.session-content,
.friend-content {
  min-width: 0;
  flex: 1;
}

.session-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.session-name {
  font-weight: 900;
}

.session-status {
  font-size: 12px;
  color: #f8b36c;
}

.session-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.session-meta,
.session-row,
.chat-sub {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

.session-row,
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #24cf5f;
  color: #0a0a0a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
}

.socket-banner {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.socket-connecting,
.socket-reconnecting {
  background: rgba(255, 196, 61, 0.12);
  color: rgba(255, 238, 184, 0.96);
  border-color: rgba(255, 196, 61, 0.28);
}

.socket-offline,
.socket-unauthorized,
.socket-unsupported {
  background: rgba(255, 116, 116, 0.12);
  color: rgba(255, 205, 205, 0.96);
  border-color: rgba(255, 116, 116, 0.28);
}

.chat-name {
  font-size: 16px;
  font-weight: 900;
}

.chat-messages {
  flex: 1;
  min-height: 280px;
  max-height: 420px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.message-row {
  display: flex;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
}

.message-row.mine .message-bubble {
  background: linear-gradient(135deg, rgba(36, 207, 95, 0.85), rgba(109, 255, 154, 0.72));
  color: #0a0a0a;
  font-weight: 800;
}

.chat-send {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel+.panel {
  margin-top: 14px;
}

.panel-title {
  font-size: 14px;
  font-weight: 900;
  color: rgba(255, 255, 255, 0.88);
}

.friend-panel {
  min-height: 220px;
}

.session-panel {
  min-height: 280px;
}

.friend-list {
  display: grid;
  gap: 10px;
  overflow: auto;
}

.friend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
}

.friend-item:hover {
  border-color: rgba(36, 207, 95, 0.28);
}

.friend-avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
}

.friend-avatar.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-weight: 900;
}

.friend-name {
  font-weight: 900;
}

.friend-meta {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.58);
}

.request-list {
  display: grid;
  gap: 10px;
  overflow: auto;
}

.request-item {
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.request-name {
  font-weight: 900;
}

.request-meta,
.request-remark {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
}

.request-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

.context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 120px;
  padding: 8px;
  border-radius: 14px;
  background: rgba(18, 18, 18, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.35);
}

.context-item {
  width: 100%;
  border: 0;
  border-radius: 10px;
  padding: 10px 12px;
  background: transparent;
  color: #ffb0b0;
  text-align: left;
  cursor: pointer;
}

.context-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

@media (max-width: 1200px) {

  .span-4,
  .span-8 {
    grid-column: span 12;
  }
}
</style>
