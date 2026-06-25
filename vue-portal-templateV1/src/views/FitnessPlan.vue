<template>
  <div class="fitness-plan-view">
    <div v-if="!planGenerated" class="form-container">
      <div class="intro-section">
        <h1 class="main-title">定制你的 7 天健身计划</h1>
        <p class="subtitle">输入你的身体数据，让 AI 为你量身定制专属的运动与饮食方案</p>
      </div>

      <div class="history-bar">
        <el-button class="ff-btn-outline" @click="openHistory">
          <el-icon>
            <Clock />
          </el-icon> 计划查看
        </el-button>
      </div>

      <el-card class="form-card ff-style">
        <el-form :model="formData" label-position="top" class="ff-form">
          <div class="form-row">
            <el-form-item label="身高 (cm)" class="half-width">
              <el-input-number v-model="formData.height" :min="100" :max="250" placeholder="175"
                controls-position="right" />
            </el-form-item>
            <el-form-item label="体重 (kg)" class="half-width">
              <el-input-number v-model="formData.weight" :min="30" :max="200" placeholder="70"
                controls-position="right" />
            </el-form-item>
          </div>

          <el-form-item label="既往病史 / 身体状况">
            <el-input v-model="formData.heartDisease" type="textarea" :rows="3" placeholder="是否有心脏病、高血压或其他需要注意的身体状况？" />
          </el-form-item>

          <el-form-item label="健身目标">
            <el-input v-model="formData.goal" type="textarea" :rows="3" placeholder="例如：减脂瘦身、增肌塑形、提高体能等" />
          </el-form-item>

          <el-button type="primary" class="ff-btn-primary" @click="generatePlan" :loading="loading">
            {{ loading ? '计划生成中...' : '开启我的 7 天计划' }}
          </el-button>
        </el-form>
      </el-card>
    </div>

    <div v-else class="plan-container">
      <div class="plan-header">
        <div class="plan-header-left">
          <el-button class="back-btn" text @click="resetForm">
            <el-icon>
              <ArrowLeft />
            </el-icon> 返回填报
          </el-button>
          <h2 class="section-title">我的 7 天蜕变计划</h2>
        </div>
        <div class="status-badge" v-if="loading">
          <el-icon class="is-loading">
            <Loading />
          </el-icon> AI 正在规划中...
        </div>
      </div>

      <div class="generating-panel" v-if="loading">
        <div class="gen-title">正在为你生成专属计划</div>
        <div class="gen-subtitle">已接收 {{ streamingContent.length }} 字符</div>
        <el-progress :percentage="Math.min(95, Math.floor(streamingContent.length / 40))" :stroke-width="10"
          :show-text="false" />
      </div>

      <FlipBook v-if="displayPlan.length > 0" :pages="displayPlan" :max-enabled-pages="maxEnabledPages"
        class="ff-flipbook">
        <template v-for="(day, index) in displayPlan" :key="index" #[`page-${index}`]="{ page }">
          <div class="plan-page">
            <div class="hero">
              <el-image class="hero-img" :src="page.cover" fit="cover">
                <template #error>
                  <div class="hero-fallback"></div>
                </template>
              </el-image>
              <div class="hero-overlay">
                <div class="day-pill">DAY {{ index + 1 }}</div>
                <div class="hero-title">{{ page.title }}</div>
                <div class="hero-desc">{{ page.summary }}</div>
              </div>
            </div>

            <div class="scroll-content">
              <section class="plan-card diet">
                <div class="card-title">
                  <el-icon>
                    <Calendar />
                  </el-icon>
                  <span>元气饮食方案</span>
                </div>
                <div class="fields">
                  <div class="field">
                    <div class="field-label">早餐</div>
                    <div class="field-value">{{ page.diet.morning }}</div>
                  </div>
                  <div class="field">
                    <div class="field-label">午餐</div>
                    <div class="field-value">{{ page.diet.noon }}</div>
                  </div>
                  <div class="field">
                    <div class="field-label">晚餐</div>
                    <div class="field-value">{{ page.diet.late }}</div>
                  </div>
                </div>
              </section>

              <section class="plan-card fitness">
                <div class="card-title">
                  <el-icon>
                    <Bicycle />
                  </el-icon>
                  <span>训练安排</span>
                </div>
                <div class="fields">
                  <div class="field">
                    <div class="field-label">训练内容</div>
                    <div class="field-value">{{ page.motion.motionContent }}</div>
                  </div>
                  <div class="field field-inline">
                    <div class="field-label">建议时长</div>
                    <div class="field-value">{{ page.motion.duration }}</div>
                  </div>
                  <div class="field field-inline">
                    <div class="field-label">有氧</div>
                    <div class="field-value">{{ page.motion.aerobic || '—' }}</div>
                  </div>
                  <div class="field">
                    <div class="field-label">动作清单</div>
                    <div class="field-value pre">{{ page.motion.actionText }}</div>
                  </div>
                </div>
              </section>

              <section class="plan-card checkin">
                <div class="card-title">
                  <el-icon>
                    <Camera />
                  </el-icon>
                  <span>拍照打卡</span>
                </div>
                <div class="checkin-grid">
                  <div v-for="photo in getDayCheckins(index)" :key="photo.id" class="checkin-thumb"
                    @click="window.open(photo.imageUrl)">
                    <img :src="photo.imageUrl" />
                  </div>
                  <div class="checkin-add-btn" @click="openCheckin(index)" v-if="currentPlanId">
                    <el-icon>
                      <Plus />
                    </el-icon>
                  </div>
                </div>
                <div v-if="!currentPlanId" class="checkin-hint">方案生成完成后即可拍照打卡</div>
              </section>
            </div>
          </div>
        </template>
      </FlipBook>

      <input type="file" ref="fileInput" accept="image/*" capture="environment" style="display:none"
        @change="handleCheckinUpload" />

      <div class="actions-footer" v-if="!loading">
        <el-button class="ff-btn-outline" @click="resetForm">返回填报</el-button>
        <el-button class="ff-btn-outline" @click="resetForm">重新生成计划</el-button>
      </div>
    </div>
  </div>

  <el-dialog v-model="historyVisible" title="历史方案记录" width="680px" class="history-dialog">
    <div v-loading="historyLoading" class="history-dialog-body">
      <el-empty v-if="!historyLoading && historyList.length === 0" description="暂无历史方案记录" />
      <div v-show="historyList.length > 0" class="history-list">
        <div v-for="record in historyList" :key="record.id" class="history-item" @click="viewPlan(record.id)">
          <div class="history-main">
            <div class="history-goal">{{ record.goal }}</div>
            <div class="history-body">
              <span>身高 {{ record.height }}cm</span>
              <span>体重 {{ record.weight }}kg</span>
            </div>
          </div>
          <div class="history-time">{{ formatTime(record.createTime) }}
            <span class="history-arrow">→</span>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { Camera, Calendar, Bicycle, Loading, Check, Clock, Plus, ArrowLeft } from '@element-plus/icons-vue';
import FlipBook from '../components/FlipBook.vue';
import { ElMessage } from 'element-plus';
import request from '../utils/request';

const router = useRouter();

// 这四个字段会原样提交给后端，其中 sex 不在表单中填写，
// 而是由后端根据当前登录用户信息自动补齐。
const formData = reactive({
  height: 175,
  weight: 70,
  heartDisease: '',
  goal: ''
});

// 页面状态分成三层：
// 1. streamingContent 保存后端 SSE 已返回的原始文本；
// 2. livePlan 用于边生成边展示的“半成品计划”；
// 3. parsedPlan 用于最终成功解析后的完整 7 天计划。
const loading = ref(false);
// 是否已经进入“生成态”，用于控制右侧翻页区是否显示结果区域。
const planGenerated = ref(false);
// SSE 文本流的原始累积内容。
const streamingContent = ref('');
// 已被完整 JSON.parse 成功的最终计划。
const parsedPlan = ref([]);
// 根据半截文本猜测出来的临时计划。
const livePlan = ref([]);
// 当前流式请求的中断控制器，便于重新生成或离开页面时取消请求。
let activePlanAbortController = null;

const historyVisible = ref(false);
const historyList = ref([]);
const historyLoading = ref(false);

const currentPlanId = ref(null);
const checkinPhotos = ref({});
const activeCheckinDay = ref(0);
const fileInput = ref(null);

const MEDICAL_KEYWORDS = new Set([
  '心脏', '心血管', '血管', '动脉', '静脉', '血压', '高血压', '低血压',
  '肺', '呼吸', '哮喘', '气喘', '支气管',
  '肝', '肝功能', '脂肪肝', '肝炎',
  '肾', '肾功能', '肾炎', '尿酸', '痛风',
  '胃', '胃炎', '胃溃疡', '消化不良', '胃酸', '肠胃', '消化',
  '肠', '肠道', '便秘', '腹泻',
  '甲状腺', '甲亢', '甲减', '内分泌', '激素',
  '血糖', '糖尿病', '胰岛素', '高血糖', '低血糖',
  '血脂', '胆固醇', '甘油三酯',
  '贫血', '血液', '血小板',
  '骨骼', '关节', '膝盖', '膝', '髋', '肩', '肘', '腕', '踝',
  '腰椎', '颈椎', '脊柱', '腰间盘', '椎间盘', '腰', '颈', '背',
  '骨质疏松', '关节炎', '风湿', '类风湿', '肩周炎',
  '骨折', '扭伤', '拉伤', '韧带', '肌肉损伤', '半月板', '肌腱',
  '手术', '术后', '开刀', '住院', '石膏',
  '慢性病', '遗传病', '过敏', '过敏源', '过敏原',
  '肥胖', '超重', '偏瘦', '营养不良',
  '体脂', 'BMI', '体质', '体能', '免疫力', '免疫',
  '失眠', '睡眠', '疲劳', '乏力', '虚弱',
  '压力', '焦虑', '抑郁', '心理', '情绪',
  '亚健康', '康复', '恢复', '产后', '孕期', '怀孕', '备孕',
  '吸烟', '喝酒', '饮酒', '酗酒',
  '正常', '健康', '良好', '无', '没有', '没问题', '无病史',
  '体检', '指标', '检查', '报告',
  '头晕', '头痛', '胸闷', '心慌', '心悸', '气短',
  '抽筋', '痉挛', '酸痛', '疼痛', '不适', '僵硬',
  '代谢', '新陈代谢', '基础代谢',
  '静脉曲张', '痔疮', '疝气',
  '耐力', '柔韧', '力量', '核心',
]);

const FITNESS_GOAL_KEYWORDS = new Set([
  '减脂', '减肥', '瘦身', '燃脂', '刷脂', '降体脂',
  '增肌', '增重', '增肥', '长肌肉',
  '塑形', '塑型', '塑身', '雕刻', '打造',
  '线条', '体型', '身材', '体态', '姿态', '姿势',
  '苗条', '纤细', '匀称', '紧致', '结实', '强壮', '健美',
  '腹肌', '马甲线', '人鱼线', '腹', '腰', '肚子', '小腹',
  '胸肌', '胸', '背肌', '背', '阔背',
  '手臂', '胳膊', '二头肌', '三头肌', '麒麟臂', '拜拜肉',
  '腿部', '腿', '大腿', '小腿', '臀', '翘臀', '臀部',
  '核心', '腰腹', '腹直肌',
  '力量', '爆发力', '耐力', '体能', '心肺', '有氧',
  '柔韧', '拉伸', '灵活', '协调', '平衡',
  '速度', '敏捷', '弹跳', '跳跃',
  '跑步', '游泳', '骑行', '单车', '跳绳',
  '瑜伽', '普拉提', '拳击', '散打', '搏击',
  '深蹲', '卧推', '硬拉', '引体向上', '俯卧撑', '平板支撑',
  '举重', '哑铃', '杠铃', '壶铃', '器械',
  'HIIT', 'CrossFit', '有氧操', '健身操',
  '马拉松', '越野', '铁人三项', '比赛', '备赛',
  '球类', '篮球', '足球', '羽毛球', '网球', '排球', '乒乓球',
  '舞蹈', '街舞', '拉丁', '芭蕾',
  '体重', '体脂率', '肌肉量', '维度', '围度',
  '卡路里', '代谢', '热量', '大卡',
  'BMI', '体脂', '体测',
  '减', '瘦', '增', '练', '锻炼', '训练', '运动', '健身',
  '提高', '提升', '改善', '增强', '强化',
  '保持', '维持', '巩固',
  '恢复', '康复', '矫正', '纠正', '调整',
  '健康', '强壮', '健美', '健壮',
  '入门', '进阶', '突破', '冲刺',
  '减重', '掉秤',
  '练出', '练成', '更健康', '更好', '完美', '理想', '自信',
]);

const validateTopicRelevance = (text, keywords, fieldName) => {
  if (!text || !text.trim()) {
    return { valid: false, reason: `请填写${fieldName}` };
  }
  const trimmed = text.trim();
  for (const keyword of keywords) {
    if (trimmed.includes(keyword)) {
      return { valid: true, reason: '' };
    }
  }
  return {
    valid: false,
    reason: `您输入的"${fieldName}"内容似乎与${fieldName}无关，请填写与${fieldName}相关的内容`
  };
};

const supportsStreamPlan = () => (
  // 确保当前运行环境真的是浏览器。
  typeof window !== 'undefined'
  // fetch 是读取流式响应的基础能力。
  && typeof window.fetch === 'function'
  // TextDecoder 用来把 Uint8Array 解码成字符串。
  && typeof window.TextDecoder !== 'undefined'
  // AbortController 用来取消正在进行的生成请求。
  && typeof window.AbortController !== 'undefined'
);

const resetForm = () => {
  // 重新生成时先中断上一次流式请求，避免旧响应继续写入当前页面状态。
  if (activePlanAbortController) {
    activePlanAbortController.abort();
    activePlanAbortController = null;
  }
  planGenerated.value = false;
  streamingContent.value = '';
  parsedPlan.value = [];
  livePlan.value = emptyPlan.value;
  currentPlanId.value = null;
  checkinPhotos.value = {};
};

const generatePlan = async () => {
  // 先在前端做兜底校验，避免把明显错误的表单送到后端和 Python 服务。
  const height = Number(formData.height);
  const weight = Number(formData.weight);
  // 身高必须能转成正数。
  if (!Number.isFinite(height) || height <= 0) {
    ElMessage.warning('请填写正确的身高');
    return;
  }
  // 体重也必须能转成正数。
  if (!Number.isFinite(weight) || weight <= 0) {
    ElMessage.warning('请填写正确的体重');
    return;
  }
  // 目标不能为空，因为它会直接影响 AI 输出内容。
  if (!formData.goal) {
    ElMessage.warning('请填写健身目标');
    return;
  }

  // 校验健身目标是否与主题词库匹配
  const goalCheck = validateTopicRelevance(formData.goal, FITNESS_GOAL_KEYWORDS, '健身目标');
  if (!goalCheck.valid) {
    ElMessage.warning(goalCheck.reason);
    return;
  }
  // 校验既往病史是否与主题词库匹配（仅在有填写内容时校验）
  if (formData.heartDisease && formData.heartDisease.trim()) {
    const medicalCheck = validateTopicRelevance(formData.heartDisease, MEDICAL_KEYWORDS, '既往病史/身体状况');
    if (!medicalCheck.valid) {
      ElMessage.warning(medicalCheck.reason);
      return;
    }
  }

  // 门户端登录态保存在 localStorage。
  const token = localStorage.getItem('token');
  if (!token) {
    ElMessage.warning('请先登录');
    router.push('/login');
    return;
  }

  // 每次开始新一轮生成时都重置展示状态，避免残留上一份计划。
  loading.value = true;
  planGenerated.value = true;
  streamingContent.value = '';
  parsedPlan.value = [];
  // 先把翻页区域重置成 7 天空白骨架。
  livePlan.value = emptyPlan.value;
  // 浏览器不支持流式读取时，自动降级到普通同步接口。
  if (!supportsStreamPlan()) {
    try {
      await generatePlanFallback();
    } finally {
      loading.value = false;
    }
    return;
  }
  if (activePlanAbortController) {
    // 如果用户连续点击“生成”，先取消上一轮请求。
    activePlanAbortController.abort();
  }
  activePlanAbortController = new AbortController();

  try {
    // 这里直接使用 fetch + ReadableStream，是为了逐段消费后端 SSE 返回的数据块。
    const response = await fetch('/api/chart/plan/doPlanStream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Sa-Token 默认从 satoken 这个 header 中取 token。
        'satoken': token
      },
      // 直接把表单对象转成 JSON 发送给后端。
      body: JSON.stringify(formData),
      // 允许外部通过 AbortController 取消本次请求。
      signal: activePlanAbortController.signal,
    });

    if (!response.ok) {
      // 401 说明登录态过期，清理本地 token 后跳回登录页。
      if (response.status === 401) {
        localStorage.removeItem('token');
        router.push('/login');
        throw new Error('会话已过期，请重新登录');
      }
      throw new Error('连接服务失败');
    }

    if (!response.body || typeof response.body.getReader !== 'function') {
      // 某些环境虽然 fetch 可用，但 response.body 不支持 reader，
      // 此时仍然回退到普通接口。
      await generatePlanFallback();
      loading.value = false;
      return;
    }

    // getReader 让我们能够一块一块地消费后端返回内容。
    const reader = response.body.getReader();
    // 文本解码器负责把二进制块转成字符串。
    const decoder = new TextDecoder();
    // SSE 事件可能被网络拆成半行，这里缓存未结束的残片，下一轮继续拼接。
    let sseBuffer = '';

    while (true) {
      // 每次读取一块数据；done=true 说明流结束。
      const { value, done } = await reader.read();
      if (done) break;

      // 用流式模式解码，避免跨 chunk 字符截断。
      sseBuffer += decoder.decode(value, { stream: true });

      // 只消费完整行，最后一段不完整内容留到下一次 chunk 再处理。
      const lines = sseBuffer.split(/\r?\n/);
      sseBuffer = lines.pop() || '';
      consumeSseLines(lines);

      // 原始文本先尝试做“局部可视化”，让用户在 AI 还没完全返回时看到日计划轮廓；
      // 同时持续尝试把累积文本解析成完整 JSON。
      updateLiveFromStream(streamingContent.value);
      tryParsePlan(streamingContent.value);
    }

    // 把解码器里可能残留的尾部字符与最后未结束的 SSE 行一并处理掉。
    sseBuffer += decoder.decode();
    if (sseBuffer.trim()) {
      consumeSseLines([sseBuffer]);
      updateLiveFromStream(streamingContent.value);
      tryParsePlan(streamingContent.value);
    }

    // 流结束后再做一次最终解析，尽可能把最后几个字段补全。
    tryParsePlan(streamingContent.value, true);
    loading.value = false;
  } catch (error) {
    if (error?.name === 'AbortError') {
      // 用户主动取消不算错误，不弹失败提示。
      loading.value = false;
      return;
    }
    console.error('Error generating plan:', error);
    ElMessage.error('生成计划失败: ' + error.message);
    loading.value = false;
    if (!parsedPlan.value.length) planGenerated.value = false;
  } finally {
    activePlanAbortController = null;
  }
};

const generatePlanFallback = async () => {
  // 降级链路走普通 HTTP 接口，等后端完整返回后一次性渲染。
  const res = await request.post('/chart/plan/doPlan', formData);
  // 无论同步接口返回结构如何，先统一规整成 7 天数组。
  const normalized = normalizePlan(res.data);
  if (!normalized.length) {
    throw new Error('计划数据解析失败');
  }
  parsedPlan.value = normalized.map((d) => ({
    ...d,
    ready: true,
  }));
  streamingContent.value = JSON.stringify(res.data || {});
  ElMessage.info('当前环境不支持流式生成，已自动切换为普通生成模式');
};

const consumeStreamPayload = (payload) => {
  // 流式接口约定了三种事件：
  // chunk: 正文片段；error: 业务错误；done: 生成完成。
  let parsed;
  try {
    // 优先尝试把 payload 解释成 JSON 事件。
    parsed = JSON.parse(payload);
  } catch {
    // 解析失败说明它更像是普通文本，交给外层继续拼接。
    return false;
  }
  if (parsed?.type === 'chunk') {
    // chunk 事件直接把内容附加到原始文本流。
    streamingContent.value += parsed.content || '';
    return true;
  }
  if (parsed?.type === 'error') {
    // error 事件转成前端异常，交给 generatePlan 的 catch 统一处理。
    throw new Error(parsed.message || '生成计划失败');
  }
  if (parsed?.type === 'done') {
    if (parsed.planId != null) {
      currentPlanId.value = parsed.planId;
      fetchCheckinPhotos();
    }
    return true;
  }
  return false;
};

const consumeSseLines = (lines) => {
  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    if (line.startsWith('data:')) {
      // 去掉 SSE 协议前缀，保留真正业务数据。
      const actualData = line.substring(5).trim();
      if (!actualData) continue;
      // 优先按我们约定的 { type, content } 结构消费。
      const handled = consumeStreamPayload(actualData);
      if (!handled) {
        // 如果不是结构化事件，就按普通文本直接拼接。
        streamingContent.value += actualData;
      }
      continue;
    }
    if (line.trim() && !line.startsWith(':')) {
      // 兼容非标准 SSE 行或 AI 返回的裸文本内容。
      streamingContent.value += line;
    }
  }
};

const stripMarkdownWrapper = (value) => {
  let text = String(value || '').trim();
  if (text.includes('```json')) {
    text = text.substring(text.indexOf('```json') + 7);
    if (text.includes('```')) {
      text = text.substring(0, text.lastIndexOf('```'));
    }
  } else if (text.includes('```')) {
    text = text.substring(text.indexOf('```') + 3);
    if (text.includes('```')) {
      text = text.substring(0, text.lastIndexOf('```'));
    }
  }
  return text.trim();
};

const unwrapPlanJsonText = (value, { final = false } = {}) => {
  let text = stripMarkdownWrapper(value);
  for (let i = 0; i < 4 && text; i++) {
    const trimmed = text.trim();
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      return trimmed;
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (typeof parsed === 'string') {
        text = stripMarkdownWrapper(parsed);
        continue;
      }
      return JSON.stringify(parsed);
    } catch {
      if (final) {
        let repaired = trimmed;
        if (repaired.startsWith('"')) repaired = repaired.slice(1);
        if (repaired.endsWith('"')) repaired = repaired.slice(0, -1);
        repaired = repaired
          .replace(/\\"/g, '"')
          .replace(/\\n/g, '\n')
          .replace(/\\r/g, '')
          .replace(/\\t/g, '\t')
          .replace(/\\\\/g, '\\');
        if (repaired !== trimmed) {
          text = repaired;
          continue;
        }
      }
      break;
    }
  }
  return text;
};

const decodePlanPreview = (value) => {
  let text = stripMarkdownWrapper(value);
  if (text.startsWith('"')) text = text.slice(1);
  if (text.endsWith('"')) text = text.slice(0, -1);
  return text
    .replace(/\\"/g, '"')
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '')
    .replace(/\\t/g, '\t')
    .replace(/\\\\/g, '\\');
};

const extractPlanErrorMessage = (data) => {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return '';
  const message = data.error || data.message || data.msg;
  return typeof message === 'string' ? message.trim() : '';
};

const pick = (obj, keys) => {
  if (!obj) return undefined;
  for (const key of keys) {
    // 找到第一个“非空字符串/非空值”就返回，用来兼容多种字段别名。
    const val = obj[key];
    if (val !== undefined && val !== null && String(val).trim() !== '') return val;
  }
  return undefined;
};

const normalizeDay = (dayObj, index) => {
  // Python 侧字段命名并不完全稳定，这里统一兼容中英文和多种别名。
  const diet = dayObj?.diet || dayObj?.Diet || dayObj?.饮食 || {};
  const motionRaw = dayObj?.Motion || dayObj?.motion || dayObj?.运动 || dayObj?.fitness || {};
  const actionArr = Array.isArray(motionRaw?.action) ? motionRaw.action : [];
  const actionText = actionArr
    .filter(Boolean)
    // action 数组里理论上是字符串，但这里仍兼容对象，防止解析失败。
    .map(x => (typeof x === 'string' ? x : JSON.stringify(x)))
    .join('\n');
  return {
    // 当前项目里后端不返回封面图，所以这里临时用 picsum 做 7 天占位图。
    cover: `https://picsum.photos/seed/fitflow-day-${index + 1}/1200/700`,
    title: `第 ${index + 1} 天 · 计划`,
    summary: pick(dayObj, ['summary', 'content']) || '饮食与训练建议已为你匹配到今日节奏',
    // ready 表示该页是否已经有足够内容可供用户翻阅。
    ready: false,
    diet: {
      morning: pick(diet, ['morning', '早餐']) || '—',
      noon: pick(diet, ['noon', 'lunch', '午餐']) || '—',
      late: pick(diet, ['late', 'dinner', '晚餐']) || '—',
    },
    motion: {
      motionContent: pick(motionRaw, ['motionContent', 'title', 'name', '训练内容']) || '—',
      aerobic: pick(motionRaw, ['aerobic', '有氧']) || '',
      duration: pick(motionRaw, ['duration', '时长']) || '—',
      actionText: actionText || '—',
    },
  };
};

const normalizePlan = (data) => {
  // 后端/AI 可能返回数组，也可能返回 oneDay/twoDay/day1 这类对象结构，
  // 这里先把所有可能形态统一规整成前端固定的 7 天数组。
  const root = data?.dietMotionPlan || data?.dietMotionPlanVO || data?.planData || data;
  if (Array.isArray(root)) {
    return root.map((d, i) => normalizeDay(d || {}, i));
  }
  const obj = typeof root === 'object' && root ? root : {};
  const orderedKeys = [
    // 这里兼容了不同历史版本里出现过的 day key 命名。
    'oneDay',
    'towDay',
    'twoDay',
    'threeDay',
    'fourDay',
    'fiveDay',
    'sixDay',
    'seven',
    'sevenDay',
    'day1',
    'day2',
    'day3',
    'day4',
    'day5',
    'day6',
    'day7',
  ];
  const days = [];
  for (const k of orderedKeys) {
    // 先按预定义顺序拿出有值的天数据。
    if (obj[k]) days.push(obj[k]);
  }
  if (days.length === 0) {
    // 如果没有命中预定义 key，就按对象 key 中的数字排序兜底。
    const keys = Object.keys(obj).sort((a, b) => {
      const numA = parseInt(a.replace(/\D/g, '')) || 0;
      const numB = parseInt(b.replace(/\D/g, '')) || 0;
      return numA - numB;
    });
    for (const k of keys) days.push(obj[k]);
  }
  return days.slice(0, 7).map((d, i) => normalizeDay(d || {}, i));
};

const emptyPlan = computed(() =>
  // 默认构造 7 天空白计划，用作初始占位和重置状态。
  new Array(7).fill(null).map((_, i) => normalizeDay({}, i))
);

const displayPlan = computed(() => {
  // 展示优先级：最终解析结果 > 流式半成品 > 空白占位。
  if (parsedPlan.value && parsedPlan.value.length > 0) return parsedPlan.value;
  if (livePlan.value && livePlan.value.length > 0) return livePlan.value;
  return emptyPlan.value;
});

const maxEnabledPages = computed(() => {
  // 仅允许翻到已经“有内容”的页，模拟边生成边解锁阅读的体验。
  if (parsedPlan.value && parsedPlan.value.length > 0) return 7;
  const list = livePlan.value && livePlan.value.length > 0 ? livePlan.value : emptyPlan.value;
  let max = 1;
  for (let i = 0; i < list.length; i++) {
    if (list[i]?.ready) max = i + 1;
  }
  return max;
});

const extractJsonStringValue = (text, key) => {
  // 这个函数不要求整段文本是合法 JSON，
  // 只做“字符串切片级别”的弱解析，专门服务于流式半成品展示。
  const idx = text.indexOf(`"${key}"`);
  if (idx < 0) return undefined;
  const colon = text.indexOf(':', idx);
  if (colon < 0) return undefined;
  const firstQuote = text.indexOf('"', colon + 1);
  if (firstQuote < 0) return undefined;
  let i = firstQuote + 1;
  let raw = '';
  while (i < text.length) {
    const ch = text[i];
    if (ch === '\\') {
      // 保留转义序列，稍后交给 JSON.parse 统一还原。
      raw += ch;
      if (i + 1 < text.length) raw += text[i + 1];
      i += 2;
      continue;
    }
    if (ch === '"') break;
    raw += ch;
    i += 1;
  }
  try {
    // 借助 JSON.parse 正确还原转义后的字符串。
    return JSON.parse(`"${raw}"`);
  } catch (e) {
    // 如果还原失败，至少返回原始文本，保证页面有内容可显示。
    return raw;
  }
};

const extractActionArray = (text) => {
  // 从不完整文本里粗略提取 action 数组，用于提前显示训练动作列表。
  const idx = text.indexOf('"action"');
  if (idx < 0) return [];
  const lbr = text.indexOf('[', idx);
  if (lbr < 0) return [];
  const rbr = text.indexOf(']', lbr + 1);
  const slice = rbr >= 0 ? text.slice(lbr + 1, rbr) : text.slice(lbr + 1);
  const re = /"((?:\\.|[^"\\])*)"/g;
  const out = [];
  let m;
  while ((m = re.exec(slice))) {
    try {
      out.push({ text: JSON.parse(`"${m[1]}"`) });
    } catch (e) {
      out.push({ text: m[1] });
    }
  }
  return out;
};

const updateIfLonger = (prev, next) => {
  // 流式生成时同一字段会被多次覆盖，优先保留内容更长、信息更完整的版本。
  if (!next) return prev;
  const p = prev || '';
  const n = String(next);
  if (p === '—') return n;
  if (n.length >= p.length) return n;
  return p;
};

const updateLiveFromStream = (content) => {
  if (!content) return;
  // 这里不是严格 JSON 解析，而是“边收边猜”：
  // 通过 day key 和常见字段名从半截文本里提取可展示信息。
  const previewContent = decodePlanPreview(content);
  const dayIndexMap = {
    oneDay: 0,
    day1: 0,
    towDay: 1,
    twoDay: 1,
    day2: 1,
    threeDay: 2,
    day3: 2,
    fourDay: 3,
    day4: 3,
    fiveDay: 4,
    day5: 4,
    sixDay: 5,
    day6: 5,
    seven: 6,
    sevenDay: 6,
    day7: 6,
  };
  const dayKeys = Object.keys(dayIndexMap);
  const starts = dayKeys
    .map(k => ({ k, i: previewContent.indexOf(`"${k}"`) }))
    .filter(x => x.i >= 0)
    .sort((a, b) => a.i - b.i);
  if (starts.length === 0) return;

  const nextPlan = (livePlan.value && livePlan.value.length === 7)
    // 已经有半成品时，先深拷贝一份，避免直接改旧引用导致响应式混乱。
    ? livePlan.value.map((x, i) => (x ? { ...x, diet: { ...x.diet }, motion: { ...x.motion }, } : normalizeDay({}, i)))
    // 第一次进入时，从空白骨架开始填充。
    : new Array(7).fill(null).map((_, i) => normalizeDay({}, i));

  for (let s = 0; s < starts.length; s++) {
    const dayKey = starts[s].k;
    const start = starts[s].i;
    const end = s + 1 < starts.length ? starts[s + 1].i : previewContent.length;
    const slice = previewContent.slice(start, end);
    const idx = dayIndexMap[dayKey];
    // idx 被限制在 0~6，防止异常 key 导致数组越界。
    const d = nextPlan[idx];

    // 先尝试提取饮食字段。
    const morning = extractJsonStringValue(slice, 'morning') || extractJsonStringValue(slice, '早餐');
    const noon = extractJsonStringValue(slice, 'noon') || extractJsonStringValue(slice, 'lunch') || extractJsonStringValue(slice, '午餐');
    const late = extractJsonStringValue(slice, 'late') || extractJsonStringValue(slice, 'dinner') || extractJsonStringValue(slice, '晚餐');

    d.diet.morning = updateIfLonger(d.diet.morning, morning);
    d.diet.noon = updateIfLonger(d.diet.noon, noon);
    d.diet.late = updateIfLonger(d.diet.late, late);

    // 再提取运动字段。
    const motionContent = extractJsonStringValue(slice, 'motionContent') || extractJsonStringValue(slice, '训练内容');
    const aerobic = extractJsonStringValue(slice, 'aerobic') || extractJsonStringValue(slice, '有氧');
    const duration = extractJsonStringValue(slice, 'duration') || extractJsonStringValue(slice, '时长');

    d.motion.motionContent = updateIfLonger(d.motion.motionContent, motionContent);
    d.motion.aerobic = updateIfLonger(d.motion.aerobic, aerobic);
    d.motion.duration = updateIfLonger(d.motion.duration, duration);

    const actions = extractActionArray(slice);
    if (actions.length > 0) {
      // action 数组最终被拼成多行字符串展示。
      const joined = actions.map(x => x.text).filter(Boolean).join('\n');
      d.motion.actionText = updateIfLonger(d.motion.actionText, joined);
    }

    d.title = `第 ${idx + 1} 天 · 计划`;
    d.summary = d.summary || '饮食与训练建议已为你匹配到今日节奏';
    d.ready =
      (d.diet.morning && d.diet.morning !== '—') ||
      (d.diet.noon && d.diet.noon !== '—') ||
      (d.diet.late && d.diet.late !== '—') ||
      (d.motion.motionContent && d.motion.motionContent !== '—') ||
      (d.motion.actionText && d.motion.actionText !== '—');
  }

  livePlan.value = nextPlan;
};

const tryParsePlan = (content, isFinal = false) => {
  // 大模型/AI 服务有时会返回 ```json 包裹的代码块，这里先剥掉 markdown 外壳再解析。
  let jsonStr = unwrapPlanJsonText(content, { final: isFinal }).trim();

  try {
    // 一旦能完整 parse，就说明可以切换到最终计划展示了。
    const data = JSON.parse(jsonStr);
    const errorMessage = extractPlanErrorMessage(data);
    if (errorMessage) {
      const error = new Error(errorMessage);
      error.name = 'PlanStreamBusinessError';
      throw error;
    }
    const normalized = normalizePlan(data);
    if (normalized.length > 0) {
      parsedPlan.value = normalized.map((d) => ({
        ...d,
        ready: true,
      }));
    }
  } catch (e) {
    if (e?.name === 'PlanStreamBusinessError') {
      throw e;
    }
    if (isFinal) {
      // 只有最终解析失败才打印日志，避免流式过程中控制台刷屏。
      console.error('Final parse failed:', e);
    }
  }
};

const fetchCheckinPhotos = async () => {
  if (!currentPlanId.value) return;
  try {
    const res = await request.get(`/chart/plan/checkin/${currentPlanId.value}`);
    const list = res.data || [];
    const map = {};
    list.forEach((item) => {
      const d = item.dayIndex;
      if (!map[d]) map[d] = [];
      map[d].push(item);
    });
    checkinPhotos.value = map;
  } catch (e) {
    // empty catch
  }
};

const getDayCheckins = (dayIndex) => {
  return checkinPhotos.value[dayIndex] || [];
};

const openCheckin = (dayIndex) => {
  activeCheckinDay.value = dayIndex;
  fileInput.value?.click();
};

const handleCheckinUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file || !currentPlanId.value) {
    e.target.value = '';
    return;
  }
  const formData = new FormData();
  formData.append('file', file);
  formData.append('planId', currentPlanId.value);
  formData.append('dayIndex', activeCheckinDay.value);
  try {
    await request.post('/chart/plan/checkin', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    await fetchCheckinPhotos();
    ElMessage.success('打卡成功');
  } catch (err) {
    ElMessage.error('打卡失败');
  }
  e.target.value = '';
};

const formatTime = (time) => {
  if (!time) return '';
  const d = new Date(time);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const openHistory = async () => {
  historyVisible.value = true;
  historyLoading.value = true;
  try {
    const res = await request.get('/chart/plan/history');
    console.log('计划查看响应:', res);
    console.log('计划查看data:', res.data);
    historyList.value = res.data || [];
    console.log('historyList赋值后:', historyList.value);
  } catch (e) {
    ElMessage.error('加载历史记录失败');
  } finally {
    historyLoading.value = false;
  }
};

const viewPlan = async (planId) => {
  historyVisible.value = false;
  historyLoading.value = true;
  try {
    const res = await request.get(`/chart/plan/history/${planId}`);
    const planData = res.data?.planData;
    if (!planData) {
      ElMessage.error('方案数据为空');
      return;
    }
    const normalized = normalizePlan(planData);
    if (!normalized.length) {
      ElMessage.error('方案数据解析失败');
      return;
    }
    parsedPlan.value = normalized.map((d) => ({ ...d, ready: true }));
    streamingContent.value = JSON.stringify(planData);
    planGenerated.value = true;
    loading.value = false;
    currentPlanId.value = planId;
    fetchCheckinPhotos();
  } catch (e) {
    ElMessage.error('加载方案详情失败');
  } finally {
    historyLoading.value = false;
  }
};

onBeforeUnmount(() => {
  // 离开页面时主动取消流式请求，避免组件销毁后仍然更新状态。
  if (activePlanAbortController) {
    activePlanAbortController.abort();
    activePlanAbortController = null;
  }
});
</script>

<style scoped lang="scss">
.fitness-plan-view {
  min-height: 100vh;
  background-color: #0a0a0a;
  color: #fff;
  padding: 0 0 40px 0;
}

.view-header {
  height: 60px;
  padding: 0 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 100;

  .logo {
    font-size: 20px;
    font-weight: 800;
    color: #24cf5f;
    letter-spacing: 2px;
  }
}

.top-entry-wrap {
  display: flex;
  justify-content: center;
  padding: 26px 20px 0;
}

.top-entry-grid {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.module-card {
  text-align: left;
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background:
    radial-gradient(600px 260px at 0% 0%, rgba(36, 207, 95, 0.16), rgba(255, 255, 255, 0.02)),
    rgba(255, 255, 255, 0.03);
  color: #fff;
  cursor: pointer;
  transition: transform 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease;
}

.module-card:hover {
  transform: translateY(-4px);
  border-color: rgba(36, 207, 95, 0.36);
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.28);
}

.module-card.active {
  border-color: rgba(36, 207, 95, 0.42);
  box-shadow: inset 0 0 0 1px rgba(36, 207, 95, 0.18);
}

.module-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(36, 207, 95, 0.12);
  color: #6dff9a;
  font-size: 12px;
  font-weight: 800;
}

.module-title {
  margin-top: 14px;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: 0.5px;
}

.module-desc {
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.68);
  line-height: 1.8;
  font-size: 14px;
}

.form-container {
  max-width: 800px;
  margin: 34px auto 60px;
  padding: 0 20px;
}

.intro-section {
  text-align: center;
  margin-bottom: 40px;

  .main-title {
    font-size: 36px;
    font-weight: 700;
    margin-bottom: 15px;
  }

  .subtitle {
    font-size: 16px;
    color: #999;
  }
}

.ff-style {
  background: #1a1a1a;
  border: none;
  border-radius: 24px;
  padding: 30px;

  :deep(.el-form-item__label) {
    color: #ccc;
    font-weight: 600;
  }

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner),
  :deep(.el-input-number) {
    background: #2a2a2a;
    box-shadow: none;
    border: none;
    color: #fff;
    border-radius: 12px;
  }

  :deep(.el-input__inner) {
    color: #fff;
  }
}

.form-row {
  display: flex;
  gap: 20px;

  .half-width {
    flex: 1;
  }
}

.ff-btn-primary {
  width: 100%;
  height: 54px;
  border-radius: 27px;
  background-color: #24cf5f;
  border: none;
  font-size: 18px;
  font-weight: 700;
  margin-top: 20px;

  &:hover {
    background-color: #1fb954;
    transform: translateY(-2px);
    transition: all 0.3s;
  }
}

.plan-container {
  max-width: none;
  margin: 22px 0 0;
  padding: 0 22px;
}

.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;

  .section-title {
    font-size: 28px;
    font-weight: 700;
  }

  .status-badge {
    color: #24cf5f;
    font-weight: 600;
  }
}

.back-btn {
  color: rgba(255, 255, 255, 0.55);
  font-size: 14px;
  font-weight: 600;
  padding: 6px 14px;
  border-radius: 8px;
  transition: all 0.25s;

  &:hover {
    color: #24cf5f;
    background: rgba(36, 207, 95, 0.08);
  }
}

.plan-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.social-entry {
  border-radius: 999px;
  border: 1px solid rgba(36, 207, 95, 0.28);
  background: rgba(36, 207, 95, 0.12);
  color: #6dff9a;
  font-weight: 700;
}

.generating-panel {
  background: radial-gradient(1200px 600px at 20% 0%, rgba(36, 207, 95, 0.18), rgba(26, 26, 26, 1));
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 18px;
  padding: 22px 22px 18px;
  margin-bottom: 26px;

  .gen-title {
    font-size: 18px;
    font-weight: 800;
    letter-spacing: 0.5px;
  }

  .gen-subtitle {
    margin-top: 6px;
    margin-bottom: 14px;
    color: rgba(255, 255, 255, 0.7);
    font-size: 13px;
  }

  :deep(.el-progress-bar__outer) {
    background: rgba(255, 255, 255, 0.08);
  }

  :deep(.el-progress-bar__inner) {
    background: linear-gradient(90deg, #24cf5f, #6dff9a);
  }
}

.ff-flipbook {
  height: calc(100vh - 60px - 170px);
  min-height: 78vh;

  :deep(.page-content) {
    background: #1a1a1a;
    color: #fff;
    border: 1px solid #333;
    padding: 40px;
  }

  :deep(.page-content.back) {
    background: #151515;
  }
}

.plan-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.hero {
  position: relative;
  height: 190px;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 18px;
}

.hero-img {
  width: 100%;
  height: 100%;
}

.hero-fallback {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(36, 207, 95, 0.35), rgba(255, 255, 255, 0.06));
}

.hero-overlay {
  position: absolute;
  inset: 0;
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.15), rgba(0, 0, 0, 0.85));
}

.day-pill {
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(36, 207, 95, 0.18);
  border: 1px solid rgba(36, 207, 95, 0.35);
  color: #c9ffe0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.hero-title {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: 0.3px;
}

.hero-desc {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
  line-height: 1.5;
}

.scroll-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
}

.plan-card {
  background: #262626;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 20px;

  .card-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 16px;
    font-weight: 700;
    margin-bottom: 12px;
    color: #24cf5f;
  }

  .card-body {
    font-size: 14px;
    line-height: 1.6;
    color: #ccc;
    white-space: pre-line;
  }
}

.fields {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.field {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px 14px 12px;
  background: rgba(255, 255, 255, 0.03);
}

.field-inline {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 12px;
  align-items: center;
}

.field-label {
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.65);
}

.field-value {
  font-size: 15px;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.9);
  white-space: pre-line;
}

.field-value.pre {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.plan-card.checkin {
  border-left: 3px solid #f5a623;
}

.checkin-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.checkin-thumb {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.12);
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;

  &:hover {
    transform: scale(1.08);
    border-color: #f5a623;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.checkin-add-btn {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  border: 2px dashed rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.45);
  transition: all 0.2s;

  &:hover {
    border-color: #f5a623;
    color: #f5a623;
    background: rgba(245, 166, 35, 0.08);
  }
}

.checkin-hint {
  color: rgba(255, 255, 255, 0.25);
  font-size: 12px;
  padding: 8px 0;
}

.ff-btn-outline {
  background: transparent;
  border: 2px solid #333;
  color: #999;
  height: 44px;
  border-radius: 22px;
  padding: 0 30px;

  &:hover {
    border-color: #24cf5f;
    color: #24cf5f;
  }
}

.history-bar {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;

  .el-button {
    background: transparent;
    border: 1.5px solid rgba(36, 207, 95, 0.3);
    color: #24cf5f;
    height: 44px;
    border-radius: 22px;
    padding: 0 30px;
    font-weight: 600;
    transition: all 0.3s;

    &:hover {
      background: rgba(36, 207, 95, 0.08);
      border-color: #24cf5f;
      box-shadow: 0 0 20px rgba(36, 207, 95, 0.15);
    }
  }
}

.history-dialog {
  :deep(.el-dialog) {
    background: #0d0d0d;
    border-radius: 20px;
    border: 1px solid rgba(36, 207, 95, 0.12);
  }

  :deep(.el-dialog__title) {
    color: #24cf5f;
    font-weight: 800;
  }

  :deep(.el-dialog__headerbtn .el-dialog__close) {
    color: rgba(255, 255, 255, 0.35);

    &:hover {
      color: #24cf5f;
    }
  }
}

.actions-footer {
  margin-top: 40px;
  text-align: center;
  display: flex;
  justify-content: center;
  gap: 16px;
}

@media (max-width: 768px) {
  .view-header {
    padding: 0 18px;
  }

  .top-entry-grid {
    grid-template-columns: 1fr;
  }

  .module-title {
    font-size: 20px;
  }
}
</style>

<style lang="scss">
.el-overlay-dialog .el-dialog {
  background: #0d0d0d;
  border-radius: 20px;
  border: 1px solid rgba(36, 207, 95, 0.12);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.7), 0 0 0 1px rgba(36, 207, 95, 0.06);
  overflow: hidden;

  .el-dialog__header {
    padding: 24px 28px 16px;
    margin: 0;
    border-bottom: 1px solid rgba(36, 207, 95, 0.15);
    background: linear-gradient(180deg, rgba(36, 207, 95, 0.04) 0%, transparent 100%);
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 800;
    color: #24cf5f;
    letter-spacing: 0.5px;
  }

  .el-dialog__headerbtn {
    top: 24px;
    right: 24px;

    .el-dialog__close {
      color: rgba(255, 255, 255, 0.35);
      font-size: 20px;
      transition: color 0.2s;

      &:hover {
        color: #24cf5f;
      }
    }
  }

  .el-dialog__body {
    padding: 16px 28px 28px;
  }

  .history-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .history-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px 24px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid rgba(255, 255, 255, 0.06);
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 3px;
      background: #24cf5f;
      border-radius: 0 4px 4px 0;
      opacity: 0;
      transition: opacity 0.25s;
    }

    &:hover {
      border-color: rgba(36, 207, 95, 0.4);
      background: rgba(36, 207, 95, 0.05);
      box-shadow: 0 0 24px rgba(36, 207, 95, 0.1), inset 0 0 24px rgba(36, 207, 95, 0.02);
      transform: translateX(6px);

      &::before {
        opacity: 1;
      }

      .history-arrow {
        opacity: 1;
        transform: translateX(0);
        color: #24cf5f;
      }
    }
  }

  .history-main {
    flex: 1;
    min-width: 0;
  }

  .history-goal {
    font-size: 17px;
    font-weight: 700;
    color: #fff;
    margin-bottom: 8px;
    letter-spacing: 0.3px;
  }

  .history-body {
    display: flex;
    gap: 20px;
    color: rgba(255, 255, 255, 0.4);
    font-size: 13px;

    span {
      display: flex;
      align-items: center;
      gap: 5px;
    }
  }

  .history-time {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 6px;
    color: rgba(255, 255, 255, 0.25);
    font-size: 12px;
    white-space: nowrap;
    flex-shrink: 0;
    margin-left: 16px;
  }

  .history-arrow {
    font-size: 16px;
    color: rgba(36, 207, 95, 0.4);
    opacity: 0;
    transform: translateX(-8px);
    transition: all 0.3s;
  }

  .el-empty {
    padding: 40px 0;

    .el-empty__description {
      color: rgba(255, 255, 255, 0.3);
      margin-top: 12px;
    }
  }
}
</style>
