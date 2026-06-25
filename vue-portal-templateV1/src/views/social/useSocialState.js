import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import request from '../../utils/request'
import {
  acceptFriendRequest,
  applySocialFriend,
  commentSocialPost,
  createChatSession,
  deleteSocialFriend,
  fetchChatMessages,
  fetchChatSessions,
  fetchFriendRequests,
  fetchSocialHome,
  fetchSocialFriends,
  fetchSocialSummary,
  fetchRandomSocialMatch,
  fetchSocialMatchProfile,
  fetchSocialMatches,
  publishSocialPost,
  rejectFriendRequest,
  saveSocialProfile,
  sendChatMessage,
  toggleSocialLike,
} from '../../api/social'

const loading = ref(false)
const savingProfile = ref(false)
const publishing = ref(false)
const sessions = ref([])
const friends = ref([])
const matches = ref([])
const feed = ref([])
const messages = ref([])
const friendRequests = ref([])
const regionOptions = ref([])
const activeSession = ref(null)
const chatDrafts = reactive({})
const chatDraft = computed({
  get: () => {
    const sessionId = activeSession.value?.id
    if (!sessionId) return ''
    return chatDrafts[sessionId] || ''
  },
  set: (value) => {
    const sessionId = activeSession.value?.id
    if (!sessionId) return
    if (value) {
      chatDrafts[sessionId] = value
      return
    }
    delete chatDrafts[sessionId]
  },
})
const commentDrafts = reactive({})
const replyDrafts = reactive({})
const activeReplyTarget = ref(null)
const isFallbackMode = ref(false)
const socialReady = ref(false)
const fallbackNoticeShown = ref(false)
const matching = ref(false)
const currentMatch = ref(null)
const matchDetail = ref(null)
const profileDialogVisible = ref(false)
const applyDialogVisible = ref(false)
const noMoreMatch = ref(false)
const fallbackMessage = ref('社交服务暂时不可用，页面正在展示演示数据。')
const friendApplyRemark = ref('')
const requestActionLoading = ref(false)
const likePendingMap = reactive({})
const commentPendingMap = reactive({})
const socketStatus = ref('disconnected')
const socketStatusMessage = ref('')
const matchQuota = ref({
  dailyLimit: 3,
  usedCount: 0,
  remainingCount: 3,
  exhausted: false,
})

let socialSocket = null
let socketRefreshing = false
let socketReconnectTimer = null
let socketReconnectAttempt = 0
let socketConnectedOnce = false
let socketManuallyClosed = false
let socketLifecycleBound = false

const currentUser = ref({})
const stats = ref({})

const profileForm = reactive({
  goal: '',
  intro: '',
  location: '',
  locationCodes: [],
})

const matchForm = reactive({
  location: '',
  locationCodes: [],
  sex: '',
  goal: '',
})

const postForm = reactive({
  topic: '训练打卡',
  content: '',
  images: [],
})

const goalOptions = [
  '增肌塑形',
  '减脂燃卡',
  '跑步进阶',
  '力量提升',
  '规律打卡',
  '饮食管理',
]

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
}

const assetUrl = (value) => {
  if (!value) return ''
  const url = value.startsWith('http') || value.startsWith('blob:') ? value : value
  if (value === currentUser.value?.avatar) {
    const avatarVersion = localStorage.getItem('avatarUpdatedAt')
    if (avatarVersion) {
      return `${url}${url.includes('?') ? '&' : '?'}v=${avatarVersion}`
    }
  }
  return url
}

const formatTime = (value) => {
  if (!value) return '刚刚'
  return String(value).replace('T', ' ').slice(0, 16)
}

const findRegionPath = (options, targetValue, trail = []) => {
  if (!Array.isArray(options) || !targetValue) return []
  for (const item of options) {
    const nextTrail = [...trail, item.value]
    if (item.value === targetValue) {
      return nextTrail
    }
    const childPath = findRegionPath(item.children || [], targetValue, nextTrail)
    if (childPath.length) {
      return childPath
    }
  }
  return []
}

const syncProfileLocationFields = (locationCode = profileForm.location || currentUser.value?.location) => {
  profileForm.location = locationCode || ''
  profileForm.locationCodes = locationCode ? findRegionPath(regionOptions.value, locationCode) : []
}

const fallbackState = () => ({
  currentUser: {
    id: -1,
    nickName: localStorage.getItem('nickName') || '演示用户',
    avatar: localStorage.getItem('avatar') || '',
    sex: 0,
    location: '110105',
    locationName: '北京市/朝阳区',
  },
  myProfile: {
    goal: '规律打卡',
    intro: '工作日夜跑，周末力量训练，希望找到能长期互相督促的运动搭子。',
  },
  sessions: [
    {
      id: 1001,
      targetUser: {
        id: 2001,
        nickName: '晨跑搭子小林',
        locationName: '北京市/朝阳区',
      },
      lastMessage: '周六早上 7 点奥森见？',
      lastMessageTime: '2026-05-02 08:30',
      unreadCount: 2,
      isFriend: true,
      canChat: true,
    },
  ],
  friends: [
    {
      id: 2001,
      user: {
        id: 2001,
        nickName: '晨跑搭子小林',
        locationName: '北京市/朝阳区',
      },
      createTime: '2026-05-01 10:00',
      handleTime: '2026-05-01 10:05',
    },
    {
      id: 2003,
      user: {
        id: 2003,
        nickName: '减脂打卡KK',
        locationName: '广州市/荔湾区',
      },
      createTime: '2026-05-02 09:00',
      handleTime: '2026-05-02 09:03',
    },
  ],
  friendRequests: [
    {
      id: 501,
      remark: '我想和你成为搭子',
      createTime: '2026-05-02 08:10',
      fromUser: {
        id: 2002,
        nickName: '塑形同伴阿宁',
        locationName: '上海市/黄浦区',
      },
    },
  ],
  matches: [
    { id: 2001, nickName: '晨跑搭子小林', sex: 0, goal: '跑步进阶', intro: '每周 4 跑，想找一起刷配速的伙伴。', location: '110105', locationName: '北京市/朝阳区', relationStatus: 'none', relationText: '可发送申请' },
    { id: 2002, nickName: '塑形同伴阿宁', sex: 1, goal: '增肌塑形', intro: '下班后固定去健身房，能一起练更有动力。', location: '310101', locationName: '上海市/黄浦区', relationStatus: 'pendingReceived', relationText: '待处理对方申请', pendingRequestId: 501 },
    { id: 2003, nickName: '减脂打卡KK', sex: 1, goal: '减脂燃卡', intro: '希望互相监督饮食和每周打卡，稳定掉秤。', location: '440103', locationName: '广州市/荔湾区', relationStatus: 'pendingSent', relationText: '已申请待回复' },
  ],
  feed: [
    {
      id: 3001,
      topic: '训练打卡',
      postType: 'daily',
      content: '今天完成了 5 公里轻松跑和 20 分钟核心训练，配速终于稳定住了。',
      images: [],
      likeCount: 18,
      commentCount: 2,
      liked: false,
      createTime: '2026-05-02 07:42',
      author: { nickName: '晨跑搭子小林', locationName: '北京市/朝阳区' },
      comments: [
        { id: 1, content: '状态真好，周末一起刷圈。', author: { nickName: '演示用户' } },
        { id: 2, content: '配速越来越稳了！', author: { nickName: '塑形同伴阿宁' } },
      ],
    },
    {
      id: 3002,
      topic: '饮食分享',
      postType: 'daily',
      content: '午餐做了鸡胸肉藜麦碗，热量控制住了，饱腹感也不错。',
      images: [],
      likeCount: 12,
      commentCount: 1,
      liked: true,
      createTime: '2026-05-01 12:16',
      author: { nickName: '减脂打卡KK', locationName: '广州市/荔湾区' },
      comments: [
        { id: 3, content: '这个搭配很适合减脂期。', author: { nickName: '演示用户' } },
      ],
    },
  ],
  stats: {
    postCount: 2,
    sessionCount: 1,
    matchCount: 3,
  },
  matchQuota: {
    dailyLimit: 3,
    usedCount: 0,
    remainingCount: 3,
    exhausted: false,
  },
})

const applyHomeData = (data) => {
  const previousAvatar = currentUser.value?.avatar || ''
  currentUser.value = data?.currentUser || {}
  sessions.value = data?.sessions || []
  friends.value = data?.friends || []
  matches.value = data?.matches || []
  feed.value = data?.feed || []
  friendRequests.value = data?.friendRequests || []
  stats.value = data?.stats || {}
  matchQuota.value = {
    dailyLimit: Number(data?.matchQuota?.dailyLimit || 3),
    usedCount: Number(data?.matchQuota?.usedCount || 0),
    remainingCount: Number(data?.matchQuota?.remainingCount ?? 3),
    exhausted: !!data?.matchQuota?.exhausted,
  }
  profileForm.goal = data?.myProfile?.goal || ''
  profileForm.intro = data?.myProfile?.intro || ''
  syncProfileLocationFields(currentUser.value?.location || '')
  if (currentUser.value?.nickName) {
    localStorage.setItem('nickName', currentUser.value.nickName)
  }
  if (currentUser.value?.avatar) {
    localStorage.setItem('avatar', currentUser.value.avatar)
  } else {
    localStorage.removeItem('avatar')
  }
  if (currentUser.value?.avatar !== previousAvatar) {
    localStorage.setItem('avatarUpdatedAt', String(Date.now()))
  }
  syncActiveSession()
}

const applySummaryData = (data) => {
  const previousAvatar = currentUser.value?.avatar || ''
  currentUser.value = data?.currentUser || currentUser.value || {}
  stats.value = data?.stats || {}
  matchQuota.value = {
    dailyLimit: Number(data?.matchQuota?.dailyLimit || 3),
    usedCount: Number(data?.matchQuota?.usedCount || 0),
    remainingCount: Number(data?.matchQuota?.remainingCount ?? 3),
    exhausted: !!data?.matchQuota?.exhausted,
  }
  profileForm.goal = data?.myProfile?.goal || ''
  profileForm.intro = data?.myProfile?.intro || ''
  syncProfileLocationFields(currentUser.value?.location || '')
  if (currentUser.value?.nickName) {
    localStorage.setItem('nickName', currentUser.value.nickName)
  }
  if (currentUser.value?.avatar) {
    localStorage.setItem('avatar', currentUser.value.avatar)
  } else {
    localStorage.removeItem('avatar')
  }
  if (currentUser.value?.avatar !== previousAvatar) {
    localStorage.setItem('avatarUpdatedAt', String(Date.now()))
  }
}

const loadFallbackData = () => {
  isFallbackMode.value = true
  closeSocialSocket({ manual: true })
  updateSocketStatus('disconnected')
  applyHomeData(fallbackState())
}

const syncActiveSession = () => {
  if (!activeSession.value?.id) return
  const latest = sessions.value.find((item) => String(item.id) === String(activeSession.value.id))
  if (latest) {
    activeSession.value = latest
  }
}

const isSameIdentity = (left, right) => {
  if (left === null || left === undefined || right === null || right === undefined) return false
  return String(left) === String(right)
}

const findSessionByTargetUserId = (targetUserId) =>
  sessions.value.find((item) => isSameIdentity(item?.targetUser?.id, targetUserId))

const upsertSession = (session) => {
  if (!session?.id) return
  sessions.value = [session, ...sessions.value.filter((item) => String(item.id) !== String(session.id))]
  syncActiveSession()
}

const clearSocketReconnectTimer = () => {
  if (!socketReconnectTimer) return
  window.clearTimeout(socketReconnectTimer)
  socketReconnectTimer = null
}

const updateSocketStatus = (status, message = '') => {
  socketStatus.value = status
  socketStatusMessage.value = message
}

const closeSocialSocket = ({ manual = false } = {}) => {
  socketManuallyClosed = manual
  clearSocketReconnectTimer()
  const currentSocket = socialSocket
  socialSocket = null
  if (!currentSocket) return
  currentSocket.onopen = null
  currentSocket.onmessage = null
  currentSocket.onerror = null
  currentSocket.onclose = null
  if (currentSocket.readyState === WebSocket.OPEN || currentSocket.readyState === WebSocket.CONNECTING) {
    try {
      currentSocket.close()
    } catch {
      // Ignore close failures from a stale socket instance.
    }
  }
}

const scheduleSocketReconnect = (message = '实时聊天连接已断开，正在尝试恢复。') => {
  if (typeof window === 'undefined' || isFallbackMode.value || socketManuallyClosed) return
  const token = localStorage.getItem('token')
  if (!token) {
    updateSocketStatus('unauthorized', '登录状态已失效，请重新登录后使用实时聊天')
    return
  }
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    updateSocketStatus('offline', '网络已断开，聊天消息将暂停同步')
    return
  }
  clearSocketReconnectTimer()
  socketReconnectAttempt += 1
  const delay = Math.min(10000, 1000 * 2 ** Math.max(0, socketReconnectAttempt - 1))
  updateSocketStatus('reconnecting', `${message} 第 ${socketReconnectAttempt} 次重连将在 ${Math.ceil(delay / 1000)} 秒后进行。`)
  socketReconnectTimer = window.setTimeout(() => {
    socketReconnectTimer = null
    connectSocialSocket({ force: true })
  }, delay)
}

const bindSocketLifecycle = () => {
  if (typeof window === 'undefined' || socketLifecycleBound) return
  socketLifecycleBound = true
  window.addEventListener('online', () => {
    if (isFallbackMode.value) return
    updateSocketStatus('reconnecting', '网络已恢复，正在重新连接实时聊天')
    connectSocialSocket({ force: true })
  })
  window.addEventListener('offline', () => {
    closeSocialSocket()
    updateSocketStatus('offline', '网络已断开，聊天消息将暂停同步')
  })
}

const markSessionReadLocally = (sessionId) => {
  if (!sessionId) return
  sessions.value = sessions.value.map((item) => {
    if (String(item.id) !== String(sessionId)) return item
    return {
      ...item,
      unreadCount: 0,
    }
  })
}

const fetchSessionMessagesOnly = async (session) => {
  if (!session?.id) return
  const res = await fetchChatMessages(session.id)
  messages.value = Array.isArray(res.data) ? res.data : []
}

const refreshRealtimePanels = async ({ includeFriends = false, includeRequests = false, notifySessionId = null } = {}) => {
  const activeSessionId = activeSession.value?.id
  const tasks = [
    fetchChatSessions().then((res) => {
      sessions.value = Array.isArray(res.data) ? res.data : []
      syncActiveSession()
    }),
  ]
  if (includeFriends) {
    tasks.push(
      fetchSocialFriends().then((res) => {
        friends.value = Array.isArray(res.data) ? res.data : []
      }),
    )
  }
  if (includeRequests) {
    tasks.push(
      fetchFriendRequests().then((res) => {
        friendRequests.value = Array.isArray(res.data) ? res.data : []
      }),
    )
  }
  await Promise.all(tasks)
  if (notifySessionId) {
    if (!activeSessionId || String(activeSessionId) === String(notifySessionId)) {
      const latestSession = sessions.value.find(
        (item) => String(item.id) === String(notifySessionId)
      )
      if (latestSession) {
        activeSession.value = latestSession
        await fetchSessionMessagesOnly(latestSession)
      }
    }
    return
  }
  if (!activeSessionId) return
  const latestSession = sessions.value.find((item) => String(item.id) === String(activeSessionId))
  if (latestSession) {
    activeSession.value = latestSession
    await fetchSessionMessagesOnly(latestSession)
    return
  }
  activeSession.value = null
  messages.value = []
}

const applyPushedMessage = (msg) => {
  const isMine = Number(msg.senderId) === Number(currentUser.value?.id)
  const exists = messages.value.some((m) => String(m.id) === String(msg.id))
  if (exists) return
  if (isMine) {
    const localIdx = messages.value.findIndex(
      (m) => typeof m.id === 'string' && String(m.id).startsWith('local-') && m.content === msg.content
    )
    if (localIdx >= 0) {
      const updated = [...messages.value]
      updated[localIdx] = { ...msg, isMine: true }
      messages.value = updated
      return
    }
  }
  messages.value = [...messages.value, { ...msg, isMine }]
}

const connectSocialSocket = ({ force = false } = {}) => {
  if (typeof window === 'undefined') return
  bindSocketLifecycle()
  const token = localStorage.getItem('token')
  if (!token) {
    closeSocialSocket({ manual: true })
    updateSocketStatus('unauthorized', '登录状态已失效，请重新登录后使用实时聊天')
    return
  }
  if (typeof window.WebSocket === 'undefined') {
    updateSocketStatus('unsupported', '当前浏览器不支持实时聊天连接，请更换浏览器')
    return
  }
  if (!force && socialSocket && (socialSocket.readyState === WebSocket.OPEN || socialSocket.readyState === WebSocket.CONNECTING)) {
    return
  }
  socketManuallyClosed = false
  clearSocketReconnectTimer()
  if (force && socialSocket) {
    closeSocialSocket()
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const nextSocket = new WebSocket(`${protocol}//${window.location.host}/ws/social?token=${encodeURIComponent(token)}`)
  socialSocket = nextSocket
  updateSocketStatus('connecting', '正在建立实时聊天连接')
  nextSocket.onopen = () => {
    if (socialSocket !== nextSocket) return
    socketReconnectAttempt = 0
    socketConnectedOnce = true
    updateSocketStatus('connected')
  }
  nextSocket.onmessage = async (event) => {
    if (socialSocket !== nextSocket) return
    try {
      let reason = ''
      let notifySessionId = null
      let pushMessage = null
      try {
        const payload = JSON.parse(event?.data || '{}')
        reason = payload?.reason || ''
        notifySessionId = payload?.sessionId || null
        if (reason === 'message' && payload.messageId && payload.content) {
          pushMessage = {
            id: Number(payload.messageId) || payload.messageId,
            sessionId: Number(notifySessionId) || notifySessionId,
            senderId: Number(payload.senderId) || payload.senderId,
            content: payload.content,
            createTime: payload.createTime || new Date().toISOString(),
          }
        }
      } catch {
        reason = ''
      }

      const activeSessionId = activeSession.value?.id

      if (reason === 'message') {
        console.log('[MSG-IN]', {
          notifySessionId,
          activeSessionId,
          hasPushMessage: !!pushMessage,
          senderId: pushMessage?.senderId,
          contentPreview: pushMessage?.content?.slice(0, 30),
          currentUserId: currentUser.value?.id,
        })

        const activeNotifiedSession = notifySessionId &&
          (!activeSessionId || String(activeSessionId) === String(notifySessionId))
        const shouldPush = !!(pushMessage && activeNotifiedSession)

        console.log('[MSG-IN] shouldPush=', shouldPush, 'activeNotifiedSession=', activeNotifiedSession)

        if (shouldPush) {
          console.log('[MSG-IN] pushing to messages, count before:', messages.value.length)
          applyPushedMessage(pushMessage)
          console.log('[MSG-IN] messages count after:', messages.value.length)
        }

        if (notifySessionId) {
          const isOwnMessage = pushMessage && Number(pushMessage.senderId) === Number(currentUser.value?.id)
          sessions.value = sessions.value.map((item) => {
            if (String(item.id) !== String(notifySessionId)) return item
            const unreadDelta = isOwnMessage || activeNotifiedSession ? 0 : 1
            return {
              ...item,
              lastMessage: pushMessage ? pushMessage.content : item.lastMessage,
              lastMessageTime: pushMessage ? pushMessage.createTime : item.lastMessageTime,
              unreadCount: Math.max(0, (Number(item.unreadCount) || 0) + unreadDelta),
            }
          })
        }

        if (activeNotifiedSession && !activeSession.value && notifySessionId) {
          const match = sessions.value.find((s) => String(s.id) === String(notifySessionId))
          if (match) {
            activeSession.value = match
          }
        }
        return
      }

      const includeFriends = reason.startsWith('friend')
      const includeRequests = ['friend-request', 'friend-accepted', 'friend-rejected'].includes(reason)

      try {
        await refreshRealtimePanels({
          includeFriends,
          includeRequests,
          notifySessionId,
        })
      } catch {
        // 面板刷新失败
      }
    } finally {
      // 无需额外清理
    }
  }
  nextSocket.onerror = () => {
    if (socialSocket !== nextSocket || socketStatus.value !== 'connecting') return
    updateSocketStatus('reconnecting', '实时聊天连接失败，正在准备重连')
  }
  nextSocket.onclose = (event) => {
    if (socialSocket === nextSocket) {
      socialSocket = null
    }
    if (socketManuallyClosed || isFallbackMode.value) {
      return
    }
    const reason = String(event?.reason || '')
    if (event?.code === 1003 || reason.includes('未登录')) {
      updateSocketStatus('unauthorized', '登录状态已失效，请重新登录后使用实时聊天')
      return
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      updateSocketStatus('offline', '网络已断开，聊天消息将暂停同步')
      return
    }
    const reconnectMessage = socketConnectedOnce
      ? '实时聊天连接已断开，正在尝试恢复。'
      : '实时聊天连接建立失败，正在持续重试。'
    scheduleSocketReconnect(reconnectMessage)
  }
}

const fetchRegions = async () => {
  try {
    const res = await request.get('/dict/region/options')
    regionOptions.value = Array.isArray(res.data) ? res.data : []
    syncProfileLocationFields()
  } catch {
    regionOptions.value = []
    syncProfileLocationFields()
  }
}

const handleSocialLoadError = (error) => {
  socialReady.value = false
  loadFallbackData()
  const msg = error?.message || ''
  if (msg.includes('Network Error') || msg.includes('timeout')) {
    fallbackMessage.value = '社交服务暂时不可用，页面正在展示演示数据。'
  } else if (msg.includes('401') || msg.includes('未登录') || msg.includes('token')) {
    fallbackMessage.value = '当前登录状态已失效，页面正在展示演示数据，请重新登录后查看真实社交数据。'
  } else {
    fallbackMessage.value = `社交数据加载失败，页面正在展示演示数据。原因：${msg || '未知原因'}`
  }
  if (!fallbackNoticeShown.value) {
    fallbackNoticeShown.value = true
    if (msg.includes('Network Error') || msg.includes('timeout')) {
      ElMessage.warning('社交服务暂时不可用，已切换为演示模式')
    } else if (msg.includes('401') || msg.includes('未登录') || msg.includes('token')) {
      ElMessage.warning('当前登录状态已失效，社交模块已切换为演示模式')
    } else {
      ElMessage.warning(`社交数据加载失败，已切换为演示模式：${msg || '未知原因'}`)
    }
  }
}

const loadSummary = async () => {
  // `summary` 只负责轻量概览数据，适合聊天页/匹配页快速初始化。
  const res = await fetchSocialSummary()
  // 一旦请求成功，说明真实后端可用，不再展示演示模式。
  socialReady.value = true
  isFallbackMode.value = false
  // 把返回的数据拆解写入 currentUser/profileForm/stats/matchQuota 等响应式状态。
  applySummaryData(res.data || {})
  // 初始化成功后尝试建立 WebSocket 实时连接。
  connectSocialSocket()
}

const loadHome = async () => {
  // `home` 是后端聚合接口，会一次性返回好友、会话、推荐匹配、动态和统计数据。
  loading.value = true
  try {
    const res = await fetchSocialHome()
    // 真实接口成功后，切回真实数据模式。
    socialReady.value = true
    isFallbackMode.value = false
    // 聚合接口会一次填充多个面板的数据。
    applyHomeData(res.data || {})
    // 社交首页加载成功后连接实时通道。
    connectSocialSocket()
  } catch (error) {
    // 如果真实接口失败，则进入演示模式或提示用户当前服务不可用。
    handleSocialLoadError(error)
  } finally {
    loading.value = false
  }
}

const initChatView = async () => {
  loading.value = true
  try {
    // 聊天页除了用户概览，还必须同步好友和申请列表，保证左侧导航始终最新。
    // 这里并发执行是因为 summary 与实时面板没有先后依赖关系。
    await Promise.all([loadSummary(), refreshRealtimePanels({ includeFriends: true, includeRequests: true })])
  } catch (error) {
    handleSocialLoadError(error)
  } finally {
    loading.value = false
  }
}

const initMatchView = async () => {
  loading.value = true
  try {
    // 匹配页依赖地区数据和用户资料两部分信息，因此并发拉取。
    // 地区数据用于 cascader，summary 用于个人资料和匹配额度展示。
    await Promise.all([fetchRegions(), loadSummary()])
  } catch (error) {
    handleSocialLoadError(error)
  } finally {
    loading.value = false
  }
}

const initPlazaView = async () => {
  loading.value = true
  try {
    // 悦吧页重点是“发布入口 + 动态流”，因此额外补一次 feed 装载。
    // summary 用来展示当前用户和基础状态，feed 用来展示社区内容。
    await Promise.all([loadSummary(), loadFeed()])
  } catch (error) {
    handleSocialLoadError(error)
  } finally {
    loading.value = false
  }
}

const initSocial = async () => {
  // 进入社交默认页时，使用地区和聚合首页并发初始化。
  await Promise.allSettled([fetchRegions(), loadHome()])
}

const initSocialView = async (path = '') => {
  // 单一状态中心根据当前子路由分派初始化逻辑，避免各页面重复维护请求流程。
  const routePath = String(path)
  if (routePath.includes('/social/chat')) {
    await initChatView()
    return
  }
  if (routePath.includes('/social/match')) {
    await initMatchView()
    return
  }
  if (routePath.includes('/social/plaza')) {
    await initPlazaView()
    return
  }
  await initSocial()
}

const handleMatchLocationChange = (values) => {
  // cascader 返回的是路径数组，这里只保留最后一级地区 code。
  matchForm.location = Array.isArray(values) && values.length ? values[values.length - 1] : ''
}

const handleProfileLocationChange = (values) => {
  // 个人资料里的地区也只保存叶子节点 code。
  profileForm.location = Array.isArray(values) && values.length ? values[values.length - 1] : ''
}

const loadMatches = async () => {
  if (isFallbackMode.value) {
    // 演示模式下只对本地样例数据做筛选，不访问真实接口。
    const source = fallbackState().matches
    matches.value = source.filter((item) => {
      // 空条件表示“不限制”，有条件时才执行对应过滤。
      const sexMatched = matchForm.sex === '' || Number(item.sex) === Number(matchForm.sex)
      const goalMatched = !matchForm.goal || item.goal === matchForm.goal
      const locationMatched = !matchForm.location || item.location === matchForm.location
      return sexMatched && goalMatched && locationMatched
    })
    return
  }
  const res = await fetchSocialMatches({
    // 空值转 undefined，避免拼进 query string 干扰后端筛选逻辑。
    location: matchForm.location || undefined,
    sex: matchForm.sex === '' ? undefined : matchForm.sex,
    goal: matchForm.goal || undefined,
  })
  matches.value = Array.isArray(res.data) ? res.data : []
}

const loadFeed = async () => {
  if (isFallbackMode.value) {
    feed.value = fallbackState().feed
    return
  }
  // 动态流固定拉第一页 20 条，当前页面采用“下拉不分页”的轻量展示方式。
  const res = await request.get('/social/feed', {
    params: {
      pageNum: 1,
      pageSize: 20,
    },
  })
  feed.value = Array.isArray(res.data) ? res.data : []
}

const buildMatchParams = () => ({
  // 把前端筛选表单统一收敛成后端匹配接口所需参数。
  location: matchForm.location || undefined,
  sex: matchForm.sex === '' ? undefined : matchForm.sex,
  goal: matchForm.goal || undefined,
})

const updateMatchQuota = (quota) => {
  // 后端额度结构在不同接口里会重复返回，这里统一归一化到前端固定字段。
  if (!quota) return
  matchQuota.value = {
    dailyLimit: Number(quota.dailyLimit || 3),
    usedCount: Number(quota.usedCount || 0),
    remainingCount: Number(quota.remainingCount ?? 3),
    exhausted: !!quota.exhausted,
  }
}

const performRandomMatch = async ({ continueMatch = false } = {}) => {
  // 首次匹配和“换一个看看”共用同一套逻辑，仅通过 excludeUserId 排除当前对象。
  if (matchQuota.value.exhausted || Number(matchQuota.value.remainingCount) <= 0) {
    ElMessage.warning('今日匹配次数已达上限（3次），请明日再来。')
    return
  }
  // 用于控制匹配按钮 loading/禁用状态。
  matching.value = true
  try {
    if (isFallbackMode.value) {
      let source = fallbackState().matches.filter((item) => {
        const sexMatched = matchForm.sex === '' || Number(item.sex) === Number(matchForm.sex)
        const goalMatched = !matchForm.goal || item.goal === matchForm.goal
        const locationMatched = !matchForm.location || item.location === matchForm.location
        return sexMatched && goalMatched && locationMatched
      })
      if (continueMatch && currentMatch.value?.id) {
        // “继续匹配”时排除当前已经展示过的对象。
        source = source.filter((item) => item.id !== currentMatch.value.id)
      }
      if (!source.length) {
        if (!continueMatch) {
          currentMatch.value = null
          noMoreMatch.value = true
        }
        ElMessage.info('暂无更多搭子')
        return
      }
      // 演示模式下直接从本地候选中随机抽一个。
      currentMatch.value = source[Math.floor(Math.random() * source.length)]
      noMoreMatch.value = false
      updateMatchQuota({
        ...matchQuota.value,
        usedCount: Number(matchQuota.value.usedCount || 0) + 1,
        remainingCount: Math.max(0, Number(matchQuota.value.remainingCount || 0) - 1),
        exhausted: Number(matchQuota.value.remainingCount || 0) - 1 <= 0,
      })
      return
    }
    // 真实模式下调用后端随机匹配接口。
    const res = await fetchRandomSocialMatch({
      ...buildMatchParams(),
      excludeUserId: continueMatch ? currentMatch.value?.id : undefined,
    })
    const payload = res.data || {}
    const nextMatch = payload.match || null
    if (!nextMatch) {
      if (!continueMatch) {
        currentMatch.value = null
        noMoreMatch.value = true
      }
      return
    }
    currentMatch.value = nextMatch
    noMoreMatch.value = false
    // 后端会把最新剩余额度一并返回。
    updateMatchQuota(payload.matchQuota)
  } catch (error) {
    if (!continueMatch) {
      currentMatch.value = null
      noMoreMatch.value = true
    }
    ElMessage.info(error?.message || '暂无更多搭子')
  } finally {
    matching.value = false
  }
}

const cancelCurrentMatch = () => {
  // 取消匹配不会返还次数，只是清空当前展示结果。
  currentMatch.value = null
  matchDetail.value = null
  noMoreMatch.value = false
}

const continueCurrentMatch = async () => {
  if (!currentMatch.value) return
  await performRandomMatch({ continueMatch: true })
}

const viewCurrentMatch = async () => {
  if (!currentMatch.value?.id) return
  if (isFallbackMode.value) {
    // 演示模式下当前卡片本身就包含详情，可直接复用。
    matchDetail.value = currentMatch.value
    profileDialogVisible.value = true
    return
  }
  // 真实模式下再拉一次详情，补齐 intro/关系态等信息。
  const res = await fetchSocialMatchProfile(currentMatch.value.id)
  matchDetail.value = res.data || null
  currentMatch.value = { ...currentMatch.value, ...(res.data || {}) }
  profileDialogVisible.value = true
}

const addCurrentFriend = async () => {
  if (!currentMatch.value?.id) return
  // 根据当前关系状态决定是直接提示、进入申请弹窗，还是禁止重复申请。
  const relationStatus = currentMatch.value.relationStatus
  if (relationStatus === 'friend') {
    ElMessage.info('你们已经是好友了')
    return
  }
  if (relationStatus === 'pendingSent') {
    ElMessage.info('已发送好友申请，等待对方回复')
    return
  }
  if (relationStatus === 'pendingReceived') {
    ElMessage.info('对方已向你发来好友申请，请先在聊天页处理')
    return
  }
  if (isFallbackMode.value) {
    currentMatch.value = {
      ...currentMatch.value,
      relationStatus: 'pendingSent',
      relationText: '已申请待回复',
    }
    ElMessage.success('演示模式下已模拟发送好友申请')
    return
  }
  // 默认附言先给一个常用文案，用户仍可在弹窗里修改。
  friendApplyRemark.value = '我想和你成为搭子'
  applyDialogVisible.value = true
}

const submitCurrentFriendApply = async () => {
  if (!currentMatch.value?.id) return
  if (isFallbackMode.value) {
    currentMatch.value = {
      ...currentMatch.value,
      relationStatus: 'pendingSent',
      relationText: '已申请待回复',
    }
    applyDialogVisible.value = false
    ElMessage.success('演示模式下已模拟发送好友申请')
    return
  }
  await applySocialFriend(currentMatch.value.id, {
    remark: friendApplyRemark.value.trim(),
  })
  // 提交成功后先更新当前名片状态，页面不用等待下一次全量刷新。
  currentMatch.value = {
    ...currentMatch.value,
    relationStatus: 'pendingSent',
    relationText: '已申请待回复',
  }
  applyDialogVisible.value = false
  profileDialogVisible.value = false
  ElMessage.success('好友申请已发送')
}

const acceptIncomingRequest = async (requestItem) => {
  if (!requestItem?.id) return
  if (isFallbackMode.value) {
    friendRequests.value = friendRequests.value.filter((item) => item.id !== requestItem.id)
    ElMessage.success('演示模式下已模拟同意好友申请')
    return
  }
  friendRequests.value = friendRequests.value.filter((item) => item.id !== requestItem.id)
  requestActionLoading.value = true
  try {
    await acceptFriendRequest(requestItem.id)
    ElMessage.success('已同意好友申请')
    await refreshRealtimePanels({ includeFriends: true, includeRequests: true })
  } finally {
    requestActionLoading.value = false
  }
}

const rejectIncomingRequest = async (requestItem) => {
  if (!requestItem?.id) return
  if (isFallbackMode.value) {
    friendRequests.value = friendRequests.value.filter((item) => item.id !== requestItem.id)
    ElMessage.success('演示模式下已模拟拒绝好友申请')
    return
  }
  friendRequests.value = friendRequests.value.filter((item) => item.id !== requestItem.id)
  requestActionLoading.value = true
  try {
    await rejectFriendRequest(requestItem.id)
    ElMessage.success('已拒绝好友申请')
    await refreshRealtimePanels({ includeRequests: true })
  } finally {
    requestActionLoading.value = false
  }
}

const startChatWithFriend = async (friendItem) => {
  const userId = friendItem?.user?.id || friendItem?.id
  if (!userId) return
  await startChat(userId)
}

const deleteFriendByUser = async (targetUser) => {
  if (!targetUser?.id) return
  await ElMessageBox.confirm(
    `确定要删除好友【${targetUser.nickName || '该用户'}】吗？删除后将同时从双方好友列表中移除，对应会话也会被关闭。`,
    '删除好友确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
  if (isFallbackMode.value) {
    friends.value = friends.value.filter((item) => (item.user?.id || item.id) !== targetUser.id)
    sessions.value = sessions.value.filter((item) => item.targetUser?.id !== targetUser.id)
    if (String(activeSession.value?.targetUser?.id) === String(targetUser.id)) {
      activeSession.value = null
      messages.value = []
    }
    ElMessage.success('演示模式下已模拟删除好友')
    return
  }
  const deletedUserId = targetUser.id
  friends.value = friends.value.filter((item) => String(item.user?.id || item.id) !== String(deletedUserId))
  sessions.value = sessions.value.filter(
    (item) => String(item.targetUser?.id || item.user?.id) !== String(deletedUserId),
  )
  if (String(activeSession.value?.targetUser?.id) === String(deletedUserId)) {
    activeSession.value = null
    messages.value = []
  }
  try {
    await deleteSocialFriend(deletedUserId)
    ElMessage.success('好友已删除')
    try {
      await refreshRealtimePanels({ includeFriends: true })
    } catch {
    }
    friends.value = friends.value.filter((item) => String(item.user?.id || item.id) !== String(deletedUserId))
    sessions.value = sessions.value.filter(
      (item) => String(item.targetUser?.id || item.user?.id) !== String(deletedUserId),
    )
    if (String(activeSession.value?.targetUser?.id) === String(deletedUserId)) {
      activeSession.value = null
      messages.value = []
    }
  } catch {
    ElMessage.error('删除好友失败，请稍后重试')
  }
}

const deleteFriendFromSession = async (session) => {
  if (!session?.targetUser?.id) return
  await deleteFriendByUser(session.targetUser)
}

const handleSaveProfile = async () => {
  if (!profileForm.goal) {
    ElMessage.warning('请先选择你的目标')
    return
  }
  if (!profileForm.location) {
    ElMessage.warning('请先选择你的所在地区')
    return
  }
  if (isFallbackMode.value) {
    ElMessage.info('当前是演示模式，重启后端后可保存真实社交资料')
    return
  }
  savingProfile.value = true
  try {
    await saveSocialProfile({
      goal: profileForm.goal,
      intro: profileForm.intro,
      location: profileForm.location,
    })
    await loadSummary()
    ElMessage.success('社交资料已保存')
  } finally {
    savingProfile.value = false
  }
}

const selectSession = async (session) => {
  activeSession.value = session
  if (isFallbackMode.value) {
    messages.value = [
      { id: 1, content: '周六早上一起跑 5 公里吗？', isMine: false },
      { id: 2, content: '可以，我 6:50 到。', isMine: true },
    ]
    return
  }
  // 选中会话时同步做三件事：拉消息、标记已读、刷新当前会话摘要。
  try {
    await fetchSessionMessagesOnly(session)
  } catch {
    // 消息拉取失败不影响已读标记
  }
  markSessionReadLocally(session.id)
  syncActiveSession()
}

const startChat = async (targetUserId) => {
  if (isFallbackMode.value) {
    const session = fallbackState().sessions[0]
    activeSession.value = session
    await selectSession(session)
    ElMessage.info('当前是演示模式，聊天功能待后端重启后生效')
    return
  }
  const existingSession = findSessionByTargetUserId(targetUserId)
  if (existingSession) {
    upsertSession(existingSession)
    const session = sessions.value.find((item) => String(item.id) === String(existingSession.id)) || existingSession
    await selectSession(session)
    return
  }
  const res = await createChatSession(targetUserId)
  const created = res.data
  upsertSession(created)
  const session = sessions.value.find((item) => String(item.id) === String(created.id)) || created
  await selectSession(session)
}

const handleSendMessage = async () => {
  const draft = chatDraft.value.trim()
  if (!activeSession.value || !draft) return
  if (!activeSession.value?.canChat) {
    ElMessage.warning('对方已不是你的好友，请重新发送好友申请后再聊天')
    return
  }
  if (isFallbackMode.value) {
    messages.value.push({
      id: Date.now(),
      content: draft,
      isMine: true,
    })
    chatDraft.value = ''
    ElMessage.info('演示模式下消息仅本地展示')
    return
  }
  const content = draft
  const currentSessionId = activeSession.value.id
  const targetUserId = activeSession.value.targetUser?.id
  chatDraft.value = ''
  const localId = `local-${Date.now()}`
  messages.value = [
    ...messages.value,
    {
      id: localId,
      sessionId: currentSessionId,
      senderId: currentUser.value.id,
      receiverId: targetUserId,
      content,
      isMine: true,
      createTime: new Date().toISOString(),
    },
  ]
  markSessionReadLocally(currentSessionId)
  try {
    const res = await sendChatMessage({
      sessionId: activeSession.value.id,
      targetUserId,
      content,
    })
    upsertSession(res.data)
    markSessionReadLocally(currentSessionId)
  } catch {
    messages.value = messages.value.filter((m) => m.id !== localId)
    chatDraft.value = content
    ElMessage.error('消息发送失败，请稍后重试')
  }
}

const currentMatchActionLabel = computed(() => {
  const relationStatus = currentMatch.value?.relationStatus
  if (relationStatus === 'friend') return '已是好友'
  if (relationStatus === 'pendingSent') return '已申请'
  if (relationStatus === 'pendingReceived') return '待处理申请'
  return '添加'
})

const matchRemainingText = computed(() => `今日剩余匹配次数：${matchQuota.value.remainingCount}/${matchQuota.value.dailyLimit}`)
const matchActionDisabled = computed(() => matching.value || matchQuota.value.exhausted || Number(matchQuota.value.remainingCount) <= 0)

const handlePostImageUpload = async ({ file }) => {
  if (isFallbackMode.value) {
    postForm.images.push(URL.createObjectURL(file))
    ElMessage.success('演示模式下图片已本地加入')
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  // 动态图片先上传到公共文件服务，再把得到的 URL 写入发帖表单。
  const res = await request.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  const url = res.data?.url
  if (url) {
    postForm.images.push(url)
    ElMessage.success('图片上传成功')
  }
}

const handlePublishPost = async () => {
  if (!postForm.content.trim()) {
    ElMessage.warning('先写点内容再发布吧')
    return
  }
  if (isFallbackMode.value) {
    feed.value.unshift({
      id: Date.now(),
      topic: postForm.topic,
      postType: 'daily',
      content: postForm.content.trim(),
      images: [...postForm.images],
      likeCount: 0,
      commentCount: 0,
      liked: false,
      createTime: new Date().toISOString().slice(0, 16).replace('T', ' '),
      author: {
        nickName: currentUser.value.nickName || '演示用户',
        locationName: currentUser.value.locationName || '未知地区',
      },
      comments: [],
    })
    postForm.content = ''
    postForm.images = []
    ElMessage.success('演示模式下动态已本地发布')
    return
  }
  publishing.value = true
  try {
    // 发布成功后重新拉取一次 feed，保证评论树、点赞态与后端最新结果一致。
    await publishSocialPost({
      topic: postForm.topic,
      postType: 'daily',
      content: postForm.content.trim(),
      images: postForm.images,
    })
    postForm.content = ''
    postForm.images = []
    ElMessage.success('动态已发布')
    await loadFeed()
  } finally {
    publishing.value = false
  }
}

const handleLike = async (post) => {
  if (!post?.id || likePendingMap[post.id]) return
  if (isFallbackMode.value) {
    post.liked = !post.liked
    post.likeCount = Math.max(0, Number(post.likeCount || 0) + (post.liked ? 1 : -1))
    return
  }
  likePendingMap[post.id] = true
  // 点赞采用乐观更新，失败时再把 liked 和 likeCount 回滚。
  const previous = {
    liked: !!post.liked,
    likeCount: Number(post.likeCount || 0),
  }
  post.liked = !previous.liked
  post.likeCount = Math.max(0, previous.likeCount + (post.liked ? 1 : -1))
  try {
    const res = await toggleSocialLike(post.id)
    post.liked = !!res.data?.liked
    post.likeCount = Number(res.data?.likeCount || 0)
  } catch (error) {
    post.liked = previous.liked
    post.likeCount = previous.likeCount
    throw error
  } finally {
    likePendingMap[post.id] = false
  }
}

const openCommentReply = (post, comment) => {
  if (!post?.id || !comment?.id) return
  activeReplyTarget.value = {
    postId: post.id,
    commentId: comment.id,
    nickName: comment.author?.nickName || '用户',
  }
  if (replyDrafts[comment.id] === undefined) {
    replyDrafts[comment.id] = ''
  }
}

const cancelCommentReply = (commentId) => {
  if (activeReplyTarget.value?.commentId === commentId) {
    activeReplyTarget.value = null
  }
  if (commentId !== undefined) {
    replyDrafts[commentId] = ''
  }
}

const isReplyingComment = (commentId) => activeReplyTarget.value?.commentId === commentId

const buildLocalComment = (content, parentComment = null) => ({
  id: Date.now(),
  parentId: parentComment?.id || null,
  content,
  createTime: new Date().toISOString(),
  author: {
    id: currentUser.value.id,
    nickName: currentUser.value.nickName || '演示用户',
    avatar: currentUser.value.avatar || '',
  },
  replyToUser: parentComment?.author || null,
  replies: [],
})

const appendCommentToTree = (commentList, newComment, parentId) => {
  if (!Array.isArray(commentList)) return false
  if (!parentId) {
    commentList.push(newComment)
    return true
  }
  for (const item of commentList) {
    if (item.id === parentId) {
      item.replies = Array.isArray(item.replies) ? item.replies : []
      item.replies.push(newComment)
      return true
    }
    if (appendCommentToTree(item.replies, newComment, parentId)) {
      return true
    }
  }
  return false
}

const handleComment = async (post, parentComment = null) => {
  const draftKey = parentComment?.id ?? post.id
  const content = ((parentComment ? replyDrafts[draftKey] : commentDrafts[draftKey]) || '').trim()
  if (!content) return
  const pendingKey = parentComment ? `comment-${draftKey}` : `post-${post.id}`
  if (commentPendingMap[pendingKey]) return
  if (isFallbackMode.value) {
    post.comments = post.comments || []
    const newComment = buildLocalComment(content, parentComment)
    appendCommentToTree(post.comments, newComment, parentComment?.id || null)
    post.commentCount = Number(post.commentCount || 0) + 1
    if (parentComment) {
      replyDrafts[draftKey] = ''
      cancelCommentReply(draftKey)
    } else {
      commentDrafts[post.id] = ''
    }
    return
  }
  commentPendingMap[pendingKey] = true
  try {
    await commentSocialPost(post.id, {
      content,
      parentId: parentComment?.id || undefined,
    })
    if (parentComment) {
      replyDrafts[draftKey] = ''
      cancelCommentReply(draftKey)
    } else {
      commentDrafts[post.id] = ''
    }
    await loadFeed()
  } finally {
    commentPendingMap[pendingKey] = false
  }
}

export function useSocialState() {
  return {
    loading,
    savingProfile,
    publishing,
    sessions,
    friends,
    matches,
    feed,
    messages,
    friendRequests,
    regionOptions,
    activeSession,
    chatDraft,
    commentDrafts,
    isFallbackMode,
    socialReady,
    matching,
    currentMatch,
    matchDetail,
    profileDialogVisible,
    applyDialogVisible,
    noMoreMatch,
    fallbackMessage,
    socketStatus,
    socketStatusMessage,
    currentUser,
    stats,
    matchQuota,
    profileForm,
    matchForm,
    postForm,
    goalOptions,
    cascaderProps,
    assetUrl,
    formatTime,
    friendApplyRemark,
    requestActionLoading,
    replyDrafts,
    activeReplyTarget,
    commentPendingMap,
    likePendingMap,
    currentMatchActionLabel,
    matchRemainingText,
    matchActionDisabled,
    initSocial,
    initSocialView,
    loadHome,
    handleProfileLocationChange,
    handleMatchLocationChange,
    loadMatches,
    performRandomMatch,
    cancelCurrentMatch,
    continueCurrentMatch,
    viewCurrentMatch,
    addCurrentFriend,
    submitCurrentFriendApply,
    acceptIncomingRequest,
    rejectIncomingRequest,
    deleteFriendFromSession,
    deleteFriendByUser,
    handleSaveProfile,
    selectSession,
    startChat,
    startChatWithFriend,
    handleSendMessage,
    handlePostImageUpload,
    handlePublishPost,
    handleLike,
    handleComment,
    openCommentReply,
    cancelCommentReply,
    isReplyingComment,
  }
}
