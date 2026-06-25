<template>
  <div class="page">
    <div class="hero">
      <div class="hero-copy">
        <div class="h1">悦动社交</div>
        <div class="h2">聊天、找搭子、晒打卡，一站式连接同城同目标运动伙伴</div>
      </div>
      <div class="hero-stats">
        <div class="stat-card">
          <div class="stat-label">我的动态</div>
          <div class="stat-value">{{ stats.postCount || 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">聊天会话</div>
          <div class="stat-value">{{ stats.sessionCount || 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">可匹配搭子</div>
          <div class="stat-value">{{ stats.matchCount || 0 }}</div>
        </div>
      </div>
    </div>

    <div v-if="isFallbackMode" class="fallback-banner">
      当前运行的是旧版后端，社交接口尚未生效，页面正在展示演示数据。重启 Java 服务后可切换为真实数据。
    </div>

    <div class="grid">
      <div class="card span-4">
        <div class="card-title">我的社交名片</div>
        <div class="profile-card">
          <div class="avatar-wrap">
            <img v-if="currentUser.avatar" :src="assetUrl(currentUser.avatar)" class="avatar" alt="avatar" />
            <div v-else class="avatar empty">{{ (currentUser.nickName || 'U').slice(0, 1) }}</div>
          </div>
          <div class="profile-name">{{ currentUser.nickName || '未命名用户' }}</div>
          <div class="profile-meta">
            <span class="tag">{{ Number(currentUser.sex) === 1 ? '女生' : '男生' }}</span>
            <span class="tag">{{ currentUser.locationName || '未设置地区' }}</span>
          </div>
          <el-form label-position="top" class="mini-form">
            <el-form-item label="我的目标">
              <el-select v-model="profileForm.goal" placeholder="请选择目标">
                <el-option v-for="item in goalOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="个人介绍">
              <el-input v-model="profileForm.intro" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="介绍一下你的训练习惯、作息和期待搭子类型" />
            </el-form-item>
            <el-button type="primary" class="full-btn" :loading="savingProfile" @click="handleSaveProfile">保存资料</el-button>
          </el-form>
        </div>
      </div>

      <div class="card span-8">
        <div class="card-title">搭子匹配</div>
        <div class="match-toolbar">
          <el-cascader
            v-model="matchForm.locationCodes"
            :options="regionOptions"
            :props="cascaderProps"
            clearable
            filterable
            placeholder="按地区筛选"
            @change="handleMatchLocationChange"
          />
          <el-select v-model="matchForm.sex" clearable placeholder="按性别筛选">
            <el-option label="男" :value="0" />
            <el-option label="女" :value="1" />
          </el-select>
          <el-select v-model="matchForm.goal" clearable placeholder="按目标筛选">
            <el-option v-for="item in goalOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-button type="primary" @click="loadMatches">开始匹配</el-button>
        </div>
        <div class="match-grid">
          <div v-for="item in matches" :key="item.id" class="mate-card">
            <div class="mate-top">
              <div>
                <div class="mate-name">{{ item.nickName || '运动搭子' }}</div>
                <div class="mate-sub">{{ item.locationName || '未设置地区' }}</div>
              </div>
              <span class="tag strong">{{ item.goal || '综合陪练' }}</span>
            </div>
            <div class="mate-tags">
              <span class="tag">{{ Number(item.sex) === 1 ? '女生' : '男生' }}</span>
              <span class="tag">目标匹配</span>
            </div>
            <div class="mate-intro">{{ item.intro || '这个用户还没有填写社交介绍，但已经准备好一起训练。' }}</div>
            <div class="mate-actions">
              <el-button type="primary" plain @click="startChat(item.id)">发起聊天</el-button>
            </div>
          </div>
          <el-empty v-if="!matches.length" description="暂时没有匹配到搭子，换个条件试试" />
        </div>
      </div>

      <div class="card span-4">
        <div class="card-title">聊天会话</div>
        <div class="session-list">
          <button
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: activeSession?.id === session.id }"
            @click="selectSession(session)"
          >
            <div class="session-name">{{ session.targetUser?.nickName || '未命名用户' }}</div>
            <div class="session-meta">{{ session.lastMessage || '点击开始聊天' }}</div>
            <div class="session-row">
              <span>{{ session.targetUser?.locationName || '未知地区' }}</span>
              <span v-if="Number(session.unreadCount) > 0" class="badge">{{ session.unreadCount }}</span>
            </div>
          </button>
          <el-empty v-if="!sessions.length" description="还没有聊天会话" />
        </div>
      </div>

      <div class="card span-8">
        <div class="card-title">聊天窗口</div>
        <div v-if="activeSession" class="chat-panel">
          <div class="chat-header">
            <div class="chat-name">{{ activeSession.targetUser?.nickName || '聊天对象' }}</div>
            <div class="chat-sub">{{ activeSession.targetUser?.locationName || '未知地区' }}</div>
          </div>
          <div class="chat-messages">
            <div v-for="message in messages" :key="message.id" class="message-row" :class="{ mine: message.isMine }">
              <div class="message-bubble">{{ message.content }}</div>
            </div>
            <el-empty v-if="!messages.length" description="发送第一条消息，开启今天的运动约练吧" />
          </div>
          <div class="chat-send">
            <el-input v-model="chatDraft" placeholder="输入想说的话，比如一起约周末晨跑" @keyup.enter="handleSendMessage" />
            <el-button type="primary" @click="handleSendMessage">发送</el-button>
          </div>
        </div>
        <el-empty v-else description="先从右侧选择一个会话，或在搭子卡片中发起聊天" />
      </div>

      <div class="card span-12">
        <div class="card-title">悦吧动态</div>
        <div class="publish-box">
          <div class="publish-head">
            <el-select v-model="postForm.topic" class="topic-select">
              <el-option label="训练打卡" value="训练打卡" />
              <el-option label="饮食分享" value="饮食分享" />
              <el-option label="搭子招募" value="搭子招募" />
              <el-option label="日常碎片" value="日常碎片" />
            </el-select>
            <span class="publish-tip">把今天的训练、饮食、心得发到社区里</span>
          </div>
          <el-input v-model="postForm.content" type="textarea" :rows="4" maxlength="600" show-word-limit placeholder="分享今天的运动成果、饮食打卡，或者寻找同城搭子..." />
          <div class="publish-actions">
            <el-upload
              class="upload-list"
              :show-file-list="false"
              accept="image/*"
              :http-request="handlePostImageUpload"
            >
              <el-button>上传图片</el-button>
            </el-upload>
            <div class="image-chips">
              <span v-for="(img, index) in postForm.images" :key="`${img}-${index}`" class="chip">
                图片{{ index + 1 }}
              </span>
            </div>
            <el-button type="primary" :loading="publishing" @click="handlePublishPost">发布动态</el-button>
          </div>
        </div>

        <div class="feed-list">
          <div v-for="post in feed" :key="post.id" class="feed-card">
            <div class="feed-head">
              <div>
                <div class="feed-name">{{ post.author?.nickName || '匿名用户' }}</div>
                <div class="feed-meta">{{ post.author?.locationName || '未知地区' }} · {{ post.topic || post.postType || '动态分享' }}</div>
              </div>
              <div class="feed-time">{{ formatTime(post.createTime) }}</div>
            </div>

            <div class="feed-content">{{ post.content }}</div>

            <div v-if="post.images?.length" class="feed-images">
              <img v-for="(img, index) in post.images" :key="`${post.id}-${index}`" :src="assetUrl(img)" alt="post-image" />
            </div>

            <div class="feed-actions">
              <el-button link type="primary" @click="handleLike(post)">{{ post.liked ? '已点赞' : '点赞' }} {{ post.likeCount || 0 }}</el-button>
              <span class="action-text">评论 {{ post.commentCount || 0 }}</span>
            </div>

            <div v-if="post.comments?.length" class="comment-list">
              <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
                <span class="comment-name">{{ comment.author?.nickName || '用户' }}：</span>
                <span>{{ comment.content }}</span>
              </div>
            </div>

            <div class="comment-editor">
              <el-input v-model="commentDrafts[post.id]" placeholder="写下你的评论" @keyup.enter="handleComment(post)" />
              <el-button @click="handleComment(post)">发送</el-button>
            </div>
          </div>
          <el-empty v-if="!feed.length" description="还没有社区内容，发布第一条动态吧" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import request from '../../utils/request'
import {
  commentSocialPost,
  createChatSession,
  fetchChatMessages,
  fetchSocialHome,
  fetchSocialMatches,
  publishSocialPost,
  saveSocialProfile,
  sendChatMessage,
  toggleSocialLike,
} from '../../api/social'

const loading = ref(false)
const savingProfile = ref(false)
const publishing = ref(false)
const sessions = ref([])
const matches = ref([])
const feed = ref([])
const messages = ref([])
const regionOptions = ref([])
const activeSession = ref(null)
const chatDraft = ref('')
const commentDrafts = reactive({})
const isFallbackMode = ref(false)

const currentUser = ref({})
const stats = ref({})

const profileForm = reactive({
  goal: '',
  intro: '',
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

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
}

const goalOptions = [
  '增肌塑形',
  '减脂燃卡',
  '跑步进阶',
  '力量提升',
  '规律打卡',
  '饮食管理',
]

const assetUrl = (value) => {
  if (!value) return ''
  return value.startsWith('http') ? value : value
}

const formatTime = (value) => {
  if (!value) return '刚刚'
  return String(value).replace('T', ' ').slice(0, 16)
}

const fallbackState = () => ({
  currentUser: {
    id: -1,
    nickName: '演示用户',
    avatar: '',
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
    },
  ],
  matches: [
    { id: 2001, nickName: '晨跑搭子小林', sex: 0, goal: '跑步进阶', intro: '每周 4 跑，想找一起刷配速的伙伴。', location: '110105', locationName: '北京市/朝阳区' },
    { id: 2002, nickName: '塑形同伴阿宁', sex: 1, goal: '增肌塑形', intro: '下班后固定去健身房，能一起练更有动力。', location: '310101', locationName: '上海市/黄浦区' },
    { id: 2003, nickName: '减脂打卡KK', sex: 1, goal: '减脂燃卡', intro: '希望互相监督饮食和每周打卡，稳定掉秤。', location: '440103', locationName: '广州市/荔湾区' },
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
})

const applyHomeData = (data) => {
  currentUser.value = data?.currentUser || {}
  sessions.value = data?.sessions || []
  matches.value = data?.matches || []
  feed.value = data?.feed || []
  stats.value = data?.stats || {}
  profileForm.goal = data?.myProfile?.goal || ''
  profileForm.intro = data?.myProfile?.intro || ''
}

const loadFallbackData = () => {
  isFallbackMode.value = true
  applyHomeData(fallbackState())
}

const buildAuthHeaders = () => {
  const token = localStorage.getItem('token')
  return token ? { satoken: token } : {}
}

const fetchRegions = async () => {
  const res = await request.get('/dict/region/options')
  regionOptions.value = Array.isArray(res.data) ? res.data : []
}

const loadHome = async () => {
  loading.value = true
  try {
    const response = await fetch('/api/social/home', {
      headers: buildAuthHeaders(),
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const res = await response.json()
    if (res.code !== 200) {
      throw new Error(res.msg || '社交接口未就绪')
    }
    isFallbackMode.value = false
    applyHomeData(res.data || {})
  } catch (error) {
    loadFallbackData()
    ElMessage.warning('社交后端未升级，已切换为演示模式')
  } finally {
    loading.value = false
  }
}

const handleMatchLocationChange = (values) => {
  matchForm.location = Array.isArray(values) && values.length ? values[values.length - 1] : ''
}

const loadMatches = async () => {
  if (isFallbackMode.value) {
    const source = fallbackState().matches
    matches.value = source.filter((item) => {
      const sexMatched = matchForm.sex === '' || Number(item.sex) === Number(matchForm.sex)
      const goalMatched = !matchForm.goal || item.goal === matchForm.goal
      const locationMatched = !matchForm.location || item.location === matchForm.location
      return sexMatched && goalMatched && locationMatched
    })
    return
  }
  const res = await fetchSocialMatches({
    location: matchForm.location || undefined,
    sex: matchForm.sex === '' ? undefined : matchForm.sex,
    goal: matchForm.goal || undefined,
  })
  matches.value = Array.isArray(res.data) ? res.data : []
}

const handleSaveProfile = async () => {
  if (!profileForm.goal) {
    ElMessage.warning('请先选择你的目标')
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
    })
    ElMessage.success('社交资料已保存')
    await loadMatches()
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
  const res = await fetchChatMessages(session.id)
  messages.value = Array.isArray(res.data) ? res.data : []
  await loadHome()
}

const startChat = async (targetUserId) => {
  if (isFallbackMode.value) {
    const session = fallbackState().sessions[0]
    activeSession.value = session
    await selectSession(session)
    ElMessage.info('当前是演示模式，聊天功能待后端重启后生效')
    return
  }
  const res = await createChatSession(targetUserId)
  await loadHome()
  const created = res.data
  const session = sessions.value.find((item) => item.id === created.id) || created
  await selectSession(session)
}

const handleSendMessage = async () => {
  if (!activeSession.value || !chatDraft.value.trim()) return
  if (isFallbackMode.value) {
    messages.value.push({
      id: Date.now(),
      content: chatDraft.value.trim(),
      isMine: true,
    })
    chatDraft.value = ''
    ElMessage.info('演示模式下消息仅本地展示')
    return
  }
  await sendChatMessage({
    sessionId: activeSession.value.id,
    targetUserId: activeSession.value.targetUser?.id,
    content: chatDraft.value.trim(),
  })
  chatDraft.value = ''
  await selectSession(activeSession.value)
}

const handlePostImageUpload = async ({ file }) => {
  if (isFallbackMode.value) {
    postForm.images.push(URL.createObjectURL(file))
    ElMessage.success('演示模式下图片已本地加入')
    return
  }
  const formData = new FormData()
  formData.append('file', file)
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
    await publishSocialPost({
      topic: postForm.topic,
      postType: 'daily',
      content: postForm.content.trim(),
      images: postForm.images,
    })
    postForm.content = ''
    postForm.images = []
    ElMessage.success('动态已发布')
    await loadHome()
  } finally {
    publishing.value = false
  }
}

const handleLike = async (post) => {
  if (isFallbackMode.value) {
    post.liked = !post.liked
    post.likeCount = Math.max(0, Number(post.likeCount || 0) + (post.liked ? 1 : -1))
    return
  }
  const res = await toggleSocialLike(post.id)
  post.liked = !!res.data?.liked
  post.likeCount = Number(res.data?.likeCount || 0)
}

const handleComment = async (post) => {
  const content = (commentDrafts[post.id] || '').trim()
  if (!content) return
  if (isFallbackMode.value) {
    post.comments = post.comments || []
    post.comments.unshift({
      id: Date.now(),
      content,
      author: { nickName: currentUser.value.nickName || '演示用户' },
    })
    post.commentCount = Number(post.commentCount || 0) + 1
    commentDrafts[post.id] = ''
    return
  }
  await commentSocialPost(post.id, { content })
  commentDrafts[post.id] = ''
  await loadHome()
}

onMounted(async () => {
  await Promise.allSettled([fetchRegions(), loadHome()])
})
</script>

<style scoped lang="scss">
.page {
  width: 100%;
}

.fallback-banner {
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 196, 61, 0.12);
  border: 1px solid rgba(255, 196, 61, 0.26);
  color: rgba(255, 238, 184, 0.96);
  font-size: 13px;
  line-height: 1.7;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.hero-copy,
.hero-stats {
  border-radius: 22px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.hero-copy {
  flex: 1;
}

.hero-stats {
  width: 420px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.h1 {
  font-size: 30px;
  font-weight: 900;
}

.h2 {
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.68);
}

.stat-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
}

.stat-label {
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;
  font-weight: 800;
}

.stat-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 950;
}

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

.span-4 { grid-column: span 4; }
.span-8 { grid-column: span 8; }
.span-12 { grid-column: span 12; }

.profile-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.avatar-wrap {
  display: flex;
  justify-content: center;
}

.avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #24cf5f, #6dff9a);
  color: #0a0a0a;
  font-size: 32px;
  font-weight: 900;
}

.profile-name {
  text-align: center;
  font-size: 20px;
  font-weight: 900;
}

.profile-meta,
.mate-tags,
.image-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag,
.chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(36, 207, 95, 0.12);
  border: 1px solid rgba(36, 207, 95, 0.24);
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 800;
}

.tag.strong {
  color: #6dff9a;
}

.mini-form :deep(.el-select),
.mini-form :deep(.el-textarea),
.match-toolbar :deep(.el-select),
.match-toolbar :deep(.el-cascader) {
  width: 100%;
}

.full-btn {
  width: 100%;
}

.match-toolbar {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: 12px;
  margin-bottom: 14px;
}

.match-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mate-card,
.feed-card,
.publish-box {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.mate-top,
.feed-head,
.publish-head,
.feed-actions,
.session-row,
.chat-header,
.publish-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mate-name,
.feed-name,
.chat-name {
  font-size: 16px;
  font-weight: 900;
}

.mate-sub,
.feed-meta,
.feed-time,
.publish-tip,
.chat-sub,
.session-meta,
.session-row {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

.mate-intro,
.feed-content {
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.86);
  line-height: 1.8;
}

.mate-actions {
  margin-top: 14px;
}

.session-list {
  display: grid;
  gap: 10px;
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

.session-name {
  font-weight: 900;
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

.feed-list {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.feed-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.feed-images img {
  width: 120px;
  height: 120px;
  border-radius: 14px;
  object-fit: cover;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.action-text,
.comment-item {
  color: rgba(255, 255, 255, 0.76);
  font-size: 13px;
}

.comment-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.comment-name {
  color: #6dff9a;
  font-weight: 800;
}

.comment-editor {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.topic-select {
  width: 180px;
}

.upload-list {
  display: inline-flex;
}

@media (max-width: 1200px) {
  .hero,
  .match-toolbar {
    grid-template-columns: repeat(1, minmax(0, 1fr));
    display: grid;
  }

  .hero-stats {
    width: 100%;
  }

  .span-4,
  .span-8,
  .span-12 {
    grid-column: span 12;
  }

  .match-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}
</style>
