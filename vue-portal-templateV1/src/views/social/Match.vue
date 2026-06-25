<template>
  <div class="match-page">
    <div class="soft-grid">
      <section class="soft-card profile-panel">
        <div class="section-title">我的匹配信息</div>
        <div class="profile-head">
          <img v-if="currentUser.avatar" :src="assetUrl(currentUser.avatar)" class="self-avatar" alt="avatar" />
          <div v-else class="self-avatar empty">{{ (currentUser.nickName || 'U').slice(0, 1) }}</div>
          <div>
            <div class="self-name">{{ currentUser.nickName || '未命名用户' }}</div>
            <div class="self-meta">{{ currentUser.locationName || '未设置地区' }}</div>
          </div>
        </div>

        <el-form label-position="top" class="soft-form">
          <el-form-item label="所在地区">
            <el-cascader
              v-model="profileForm.locationCodes"
              :options="regionOptions"
              :props="cascaderProps"
              clearable
              filterable
              placeholder="请选择所在地区"
              @change="handleProfileLocationChange"
            />
          </el-form-item>
          <el-form-item label="我的目标">
            <el-select v-model="profileForm.goal" placeholder="请选择目标">
              <el-option v-for="item in goalOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="个人介绍">
            <el-input v-model="profileForm.intro" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="介绍一下你的训练习惯、作息和期待搭子类型" />
          </el-form-item>
          <el-button type="primary" class="save-btn" :loading="savingProfile" @click="handleSaveProfile">保存资料</el-button>
        </el-form>

        <div class="filter-box">
          <div class="filter-title">匹配条件</div>
          <div class="filter-grid">
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
          </div>
        </div>
      </section>

      <section class="soft-card match-panel">
        <div class="section-title">搭子匹配</div>
        <div class="match-stage">
          <div
            class="match-orb"
            :class="{ matched: !!currentMatch, disabled: matchActionDisabled }"
            @click="!currentMatch && !matchActionDisabled && performRandomMatch()"
          >
            <template v-if="currentMatch">
              <img v-if="currentMatch.avatar" :src="assetUrl(currentMatch.avatar)" class="match-avatar" alt="match-avatar" />
              <div v-else class="match-avatar empty">{{ (currentMatch.nickName || '搭').slice(0, 1) }}</div>

              <transition name="fade">
                <div class="hover-actions">
                  <button
                    class="hover-chip"
                    :class="{ disabled: currentMatchActionLabel !== '添加' }"
                    @click.stop="addCurrentFriend"
                  >
                    {{ currentMatchActionLabel }}
                  </button>
                  <button class="hover-chip" @click.stop="viewCurrentMatch">查看</button>
                  <button class="hover-chip" :disabled="matchActionDisabled" @click.stop="continueCurrentMatch">继续匹配</button>
                  <button class="hover-chip" @click.stop="cancelCurrentMatch">取消匹配</button>
                </div>
              </transition>
            </template>

            <template v-else>
              <div class="orb-inner">
                <div class="orb-icon">{{ matching ? '...' : '搭' }}</div>
                <div class="orb-text">{{ matchActionDisabled ? '已达上限' : (noMoreMatch ? '暂无更多搭子' : '匹配') }}</div>
              </div>
            </template>
          </div>

          <div class="match-tip">
            <div class="match-quota">{{ matchRemainingText }}</div>
            <template v-if="currentMatch">
              <div class="match-name">{{ currentMatch.nickName || '新搭子' }}</div>
              <div class="match-sub">{{ currentMatch.locationName || '未知地区' }} · {{ currentMatch.goal || '综合陪练' }}</div>
              <div class="match-status">{{ currentMatch.relationText || '可发送申请' }}</div>
            </template>
            <template v-else-if="noMoreMatch">
              <div class="match-name">暂时没有更多搭子啦</div>
              <div class="match-sub">你可以调整筛选条件，或者稍后再来试试</div>
            </template>
            <template v-else>
              <div class="match-name">点击圆形按钮开始匹配</div>
              <div class="match-sub">系统会随机为你选择一位尚未添加过的合适搭子</div>
            </template>
          </div>
        </div>

        <div class="friend-rule">
          仅从符合条件且尚未与你建立好友关系的用户中随机匹配；点击“继续匹配”会消耗今日次数，点击“取消匹配”只会清空当前结果。
        </div>
      </section>
    </div>

    <el-dialog v-model="profileDialogVisible" class="match-dialog" modal-class="match-dialog-mask" title="搭子资料" width="480px">
      <div class="detail-card" v-if="matchDetail">
        <div class="detail-name">{{ matchDetail.nickName || '运动搭子' }}</div>
        <div class="detail-meta">{{ matchDetail.locationName || '未知地区' }} · {{ Number(matchDetail.sex) === 1 ? '女生' : '男生' }}</div>
        <div class="detail-tag">{{ matchDetail.goal || '综合陪练' }}</div>
        <div class="detail-tag sub">{{ matchDetail.relationText || '可发送申请' }}</div>
        <div class="detail-intro">{{ matchDetail.intro || '这个用户暂未填写更多介绍。' }}</div>
      </div>
    </el-dialog>

    <el-dialog v-model="applyDialogVisible" class="match-dialog" modal-class="match-dialog-mask" title="发送好友申请" width="420px">
      <el-form label-position="top">
        <el-form-item label="附言">
          <el-input
            v-model="friendApplyRemark"
            type="textarea"
            :rows="4"
            maxlength="100"
            show-word-limit
            placeholder="默认附言：我想和你成为搭子"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCurrentFriendApply">发送申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useSocialState } from './useSocialState'

// 匹配页完全复用社交状态中心：
// 当前用户资料、匹配条件、随机匹配结果、申请弹窗状态都由 `useSocialState` 管理。
const {
  savingProfile,
  currentMatch,
  matchDetail,
  profileDialogVisible,
  applyDialogVisible,
  noMoreMatch,
  matching,
  regionOptions,
  currentUser,
  profileForm,
  matchForm,
  goalOptions,
  cascaderProps,
  assetUrl,
  friendApplyRemark,
  currentMatchActionLabel,
  matchRemainingText,
  matchActionDisabled,
  handleProfileLocationChange,
  handleMatchLocationChange,
  handleSaveProfile,
  performRandomMatch,
  cancelCurrentMatch,
  continueCurrentMatch,
  viewCurrentMatch,
  addCurrentFriend,
  submitCurrentFriendApply,
} = useSocialState()

// 模板可以分成四块理解：
// 1. currentUser + profileForm：左侧“我的匹配信息”；
// 2. matchForm：匹配筛选条件；
// 3. currentMatch + matching + noMoreMatch：中间匹配球和结果展示；
// 4. profileDialogVisible / applyDialogVisible：查看资料与发送申请弹窗。
</script>

<style scoped lang="scss">
.match-page {
  width: 100%;
}

.soft-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 20px;
}

.soft-card {
  border-radius: 30px;
  padding: 24px;
  background:
    linear-gradient(180deg, rgba(18, 18, 18, 0.94), rgba(10, 10, 10, 0.92)),
    rgba(12, 12, 12, 0.9);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(46, 204, 113, 0.22);
  color: rgba(238, 255, 245, 0.92);
}

.section-title {
  font-size: 18px;
  font-weight: 900;
  color: #2ecc71;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}

.self-avatar,
.match-avatar {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  object-fit: cover;
}

.self-avatar.empty,
.match-avatar.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(16, 16, 16, 0.92), rgba(22, 22, 22, 0.92));
  border: 1px solid rgba(46, 204, 113, 0.36);
  color: #2ecc71;
  font-size: 28px;
  font-weight: 900;
}

.self-name,
.match-name,
.detail-name {
  font-size: 22px;
  font-weight: 900;
  color: #f2fff7;
}

.match-status {
  margin-top: 10px;
  color: rgba(137, 227, 178, 0.9);
  font-size: 13px;
  font-weight: 700;
}

.match-quota {
  margin-bottom: 14px;
  color: rgba(123, 214, 168, 0.88);
  font-size: 13px;
  font-weight: 700;
}

.self-meta,
.match-sub,
.detail-meta {
  margin-top: 6px;
  color: rgba(184, 214, 199, 0.78);
  font-size: 14px;
}

.soft-form {
  margin-top: 18px;
}

.soft-form :deep(.el-input__wrapper),
.soft-form :deep(.el-textarea__inner),
.soft-form :deep(.el-select__wrapper),
.filter-grid :deep(.el-input__wrapper),
.filter-grid :deep(.el-select__wrapper),
.filter-grid :deep(.el-cascader .el-input__wrapper) {
  border-radius: 18px;
  box-shadow: inset 0 0 0 1px rgba(46, 204, 113, 0.28);
  background: rgba(8, 8, 8, 0.78);
  color: rgba(235, 255, 244, 0.92);
}

.soft-form :deep(.el-textarea__inner),
.soft-form :deep(.el-select__selected-item),
.soft-form :deep(.el-input__inner),
.filter-grid :deep(.el-input__inner),
.filter-grid :deep(.el-select__selected-item) {
  color: rgba(235, 255, 244, 0.92);
}

.soft-form :deep(.el-form-item__label),
.filter-box :deep(.el-form-item__label) {
  color: rgba(163, 230, 193, 0.88);
}

.save-btn {
  width: 100%;
  height: 46px;
  border-radius: 999px;
  background: linear-gradient(135deg, #00ff66, #2ecc71);
  border: 1px solid rgba(64, 255, 146, 0.86);
  color: #04160d;
  font-weight: 800;
  box-shadow: 0 10px 26px rgba(0, 255, 102, 0.28);
}

.hover-chip.disabled {
  opacity: 0.72;
}

.hover-chip:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.filter-box {
  margin-top: 18px;
  padding: 16px;
  border-radius: 22px;
  background: rgba(5, 5, 5, 0.75);
  border: 1px solid rgba(46, 204, 113, 0.2);
}

.filter-title {
  margin-bottom: 12px;
  font-weight: 800;
  color: #2ecc71;
}

.filter-grid {
  display: grid;
  gap: 12px;
}

.detail-tag.sub {
  margin-top: 10px;
  background: rgba(18, 62, 40, 0.78);
}

.match-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 620px;
  text-align: center;
}

.match-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.match-orb {
  position: relative;
  width: 250px;
  height: 250px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background:
    radial-gradient(circle at 30% 30%, rgba(36, 36, 36, 0.94), rgba(8, 8, 8, 0.96)),
    #000;
  box-shadow:
    0 22px 46px rgba(0, 0, 0, 0.52),
    0 0 28px rgba(0, 255, 102, 0.22),
    inset 0 0 0 1px rgba(46, 204, 113, 0.42);
  transition: transform 0.28s ease, box-shadow 0.28s ease;
}

.match-orb:hover {
  transform: translateY(-4px) scale(1.01);
  box-shadow:
    0 28px 52px rgba(0, 0, 0, 0.58),
    0 0 34px rgba(0, 255, 102, 0.34),
    inset 0 0 0 1px rgba(64, 255, 146, 0.58);
}

.match-orb.disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.match-orb.matched {
  background:
    radial-gradient(circle at 30% 30%, rgba(28, 58, 42, 0.84), rgba(7, 12, 9, 0.94)),
    #050a07;
}

.orb-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.orb-icon {
  width: 78px;
  height: 78px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(46, 204, 113, 0.14);
  border: 1px solid rgba(46, 204, 113, 0.4);
  color: #2eff91;
  font-size: 30px;
  font-weight: 900;
}

.orb-text {
  font-size: 20px;
  font-weight: 900;
  color: #86f7bf;
}

.hover-actions {
  position: absolute;
  right: -126px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hover-chip {
  min-width: 76px;
  padding: 10px 12px;
  border-radius: 999px;
  border: 1px solid rgba(46, 204, 113, 0.35);
  background: rgba(8, 15, 11, 0.88);
  color: rgba(192, 255, 221, 0.95);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  backdrop-filter: blur(8px);
  transition: all 0.22s ease;
}

.hover-chip:hover {
  background: rgba(15, 58, 34, 0.92);
  color: #c6ffde;
  transform: translateX(2px);
}

.friend-rule {
  margin-top: 28px;
  max-width: 500px;
  color: rgba(181, 209, 194, 0.8);
  line-height: 1.8;
}

.detail-card {
  border-radius: 20px;
  padding: 18px;
  background: rgba(10, 10, 10, 0.94);
  border: 1px solid rgba(46, 204, 113, 0.26);
}

.detail-tag {
  width: fit-content;
  margin-top: 12px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(22, 72, 46, 0.72);
  color: #b4ffd2;
  font-size: 13px;
  font-weight: 800;
}

.detail-intro {
  margin-top: 14px;
  color: rgba(206, 232, 219, 0.86);
  line-height: 1.9;
}

.match-page :deep(.el-dialog) {
  background: linear-gradient(180deg, rgba(18, 18, 18, 0.98), rgba(10, 10, 10, 0.96));
  border: 1px solid rgba(46, 204, 113, 0.26);
  border-radius: 20px;
}

.match-page :deep(.el-dialog__title),
.match-page :deep(.el-form-item__label) {
  color: rgba(210, 255, 230, 0.92);
}

.match-page :deep(.el-dialog__body) {
  color: rgba(220, 245, 232, 0.88);
}

.match-page :deep(.el-overlay-dialog) {
  backdrop-filter: blur(2px);
}

.match-page :deep(.el-button--default) {
  background: rgba(24, 24, 24, 0.9);
  border-color: rgba(46, 204, 113, 0.22);
  color: rgba(210, 238, 224, 0.9);
}

.match-page :deep(.el-button--primary) {
  background: linear-gradient(135deg, #00ff66, #2ecc71);
  border-color: rgba(64, 255, 146, 0.92);
  color: #031109;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(-8px);
}

@media (max-width: 1080px) {
  .soft-grid {
    grid-template-columns: 1fr;
  }

  .match-panel {
    min-height: auto;
  }

  .hover-actions {
    right: 50%;
    top: calc(100% + 14px);
    transform: translateX(50%);
    flex-direction: row;
  }

  .fade-enter-from,
  .fade-leave-to {
    opacity: 0;
    transform: translateX(50%) translateY(-8px);
  }
}

@media (max-width: 640px) {
  .soft-card {
    border-radius: 24px;
    padding: 18px;
  }

  .match-orb {
    width: 220px;
    height: 220px;
  }

  .hover-actions {
    gap: 8px;
    flex-wrap: wrap;
    justify-content: center;
    width: 220px;
  }

  .hover-chip {
    min-width: 64px;
    padding: 9px 10px;
  }
}
</style>

<style lang="scss">
.match-dialog {
  background: linear-gradient(180deg, rgba(18, 18, 18, 0.98), rgba(10, 10, 10, 0.96));
  border: 1px solid rgba(46, 204, 113, 0.26);
  border-radius: 20px;
  box-shadow: 0 24px 56px rgba(0, 0, 0, 0.5);
}

.match-dialog .el-dialog__title,
.match-dialog .el-form-item__label {
  color: rgba(210, 255, 230, 0.92);
}

.match-dialog .el-dialog__body {
  color: rgba(220, 245, 232, 0.88);
}

.match-dialog .el-input__wrapper,
.match-dialog .el-textarea__inner {
  background: rgba(8, 8, 8, 0.8);
  box-shadow: inset 0 0 0 1px rgba(46, 204, 113, 0.28);
  color: rgba(235, 255, 244, 0.92);
}

.match-dialog .el-input__inner,
.match-dialog .el-textarea__inner {
  color: rgba(235, 255, 244, 0.92);
}

.match-dialog .el-button--default {
  background: rgba(24, 24, 24, 0.9);
  border-color: rgba(46, 204, 113, 0.22);
  color: rgba(210, 238, 224, 0.9);
}

.match-dialog .el-button--primary {
  background: linear-gradient(135deg, #00ff66, #2ecc71);
  border-color: rgba(64, 255, 146, 0.92);
  color: #031109;
}

.match-dialog-mask {
  backdrop-filter: blur(2px);
}
</style>
