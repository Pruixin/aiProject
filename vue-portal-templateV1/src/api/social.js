import request from '../utils/request'

// 社交模块 API 封装：
// 按“首页/匹配/好友/资料/聊天/动态”分组，供 `useSocialState` 统一调度。
export function fetchSocialHome() {
  // 聚合接口：一次返回资料、好友、会话、动态、匹配额度等主页面数据。
  return request.get('/social/home')
}

export function fetchSocialSummary() {
  // 轻量接口：只拿概览，适合聊天页/匹配页快速初始化。
  return request.get('/social/summary')
}

export function fetchSocialMatches(params) {
  // 推荐匹配列表，支持地区/性别/目标筛选。
  return request.get('/social/match/recommend', { params })
}

export function fetchRandomSocialMatch(params) {
  // 随机匹配会消耗当日次数，后端会返回最新 matchQuota。
  return request.get('/social/match/random', { params })
}

export function fetchSocialMatchProfile(userId) {
  // 查看匹配对象详情（含当前关系快照）。
  return request.get(`/social/match/profile/${userId}`)
}

export function addSocialFriend(userId) {
  // 简版接口，兼容无附言的加好友场景。
  return request.post(`/social/friend/add/${userId}`)
}

export function applySocialFriend(userId, data) {
  // 带附言的好友申请接口，是当前匹配页主要使用的版本。
  return request.post(`/social/friend/add/${userId}`, data)
}

export function fetchFriendRequests() {
  // 拉取“别人向我发起的”待处理申请列表。
  return request.get('/social/friend/requests')
}

export function fetchSocialFriends() {
  // 拉取好友列表，常用于聊天侧栏刷新。
  return request.get('/social/friends')
}

export function acceptFriendRequest(requestId) {
  return request.post(`/social/friend/request/${requestId}/accept`)
}

export function rejectFriendRequest(requestId) {
  return request.post(`/social/friend/request/${requestId}/reject`)
}

export function deleteSocialFriend(userId) {
  return request.delete(`/social/friend/${userId}`)
}

export function saveSocialProfile(data) {
  // 保存“我的社交资料”，后端会同步更新 user.location 和 social_profile。
  return request.post('/social/profile/me', data)
}

export function fetchChatMessages(sessionId) {
  // 拉取会话消息时，后端会顺带把未读消息标记为已读。
  return request.get(`/social/chat/messages/${sessionId}`)
}

export function fetchChatSessions() {
  // 会话列表包含 lastMessage、unreadCount、canChat 等前端展示字段。
  return request.get('/social/chat/sessions')
}

export function createChatSession(targetUserId) {
  // 若会话已存在，后端会返回已有会话；不存在则自动创建。
  return request.post(`/social/chat/session/${targetUserId}`)
}

export function sendChatMessage(data) {
  // data 通常包含 sessionId/targetUserId/content。
  return request.post('/social/chat/message', data)
}

export function publishSocialPost(data) {
  // 发布动态（文本 + 主题 + 可选图片 URL 数组）。
  return request.post('/social/feed/post', data)
}

export function toggleSocialLike(postId) {
  // 点赞接口本身是“切换态”，不是显式点赞/取消点赞两套接口。
  return request.post(`/social/feed/post/${postId}/like`)
}

export function commentSocialPost(postId, data) {
  // parentId 为空是根评论，不为空是回复评论。
  return request.post(`/social/feed/post/${postId}/comment`, data)
}
