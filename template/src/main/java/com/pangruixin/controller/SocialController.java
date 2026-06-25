package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pangruixin.common.R;
import com.pangruixin.domain.Dict;
import com.pangruixin.domain.SocialChatMessage;
import com.pangruixin.domain.SocialChatSession;
import com.pangruixin.domain.SocialFriend;
import com.pangruixin.domain.SocialPost;
import com.pangruixin.domain.SocialPostComment;
import com.pangruixin.domain.SocialPostLike;
import com.pangruixin.domain.SocialProfile;
import com.pangruixin.domain.User;
import com.pangruixin.mapper.SocialChatMessageMapper;
import com.pangruixin.mapper.SocialChatSessionMapper;
import com.pangruixin.mapper.SocialFriendMapper;
import com.pangruixin.mapper.SocialPostCommentMapper;
import com.pangruixin.mapper.SocialPostLikeMapper;
import com.pangruixin.mapper.SocialPostMapper;
import com.pangruixin.mapper.SocialProfileMapper;
import com.pangruixin.service.DictService;
import com.pangruixin.service.UserService;
import com.pangruixin.websocket.SocialSocketService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/social")
public class SocialController {

    private static final int FRIEND_PENDING = 0;
    private static final int FRIEND_ACCEPTED = 1;
    private static final int FRIEND_REJECTED = 2;
    private static final int DAILY_MATCH_LIMIT = 3;
    private volatile Map<String, Dict> regionDictCache = Map.of();
    private final Map<Long, Object> postLikeLocks = new ConcurrentHashMap<>();
    private final Map<String, Object> chatSessionLocks = new ConcurrentHashMap<>();

    @Autowired
    private SocialProfileMapper socialProfileMapper;

    @Autowired
    private SocialPostMapper socialPostMapper;

    @Autowired
    private SocialPostLikeMapper socialPostLikeMapper;

    @Autowired
    private SocialPostCommentMapper socialPostCommentMapper;

    @Autowired
    private SocialChatSessionMapper socialChatSessionMapper;

    @Autowired
    private SocialChatMessageMapper socialChatMessageMapper;

    @Autowired
    private SocialFriendMapper socialFriendMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private DictService dictService;

    @Autowired
    private SocialSocketService socialSocketService;

    @GetMapping("/home")
    @SaCheckPermission(value = {"portal:social:chat:list", "portal:social:match:list", "portal:social:plaza:list"}, mode = SaMode.OR)
    public R getHomeData() {
        // 社交首页走聚合接口，一次性返回当前用户进入社交首页所需的大部分数据。
        Long currentUserId = currentUserId();
        // prepareMatchQuotaUser 会顺带处理“跨天重置匹配次数”等配额状态。
        User currentUser = prepareMatchQuotaUser(userService.getById(currentUserId));
        Map<String, Object> result = new HashMap<>();
        // currentUser 用于顶部用户卡片显示。
        result.put("currentUser", buildUserCard(currentUser));
        // myProfile 对应个人资料编辑表单。
        result.put("myProfile", socialProfileMapper.selectOne(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getUserId, currentUserId)
                .last("limit 1")));
        // friends / sessions / matches / feed / friendRequests 分别对应社交首页不同面板。
        result.put("friends", buildFriendList(currentUserId));
        result.put("sessions", buildSessionList(currentUserId));
        result.put("matches", buildMatchList(currentUserId, null, null, null, 6));
        result.put("feed", buildFeed(currentUserId, 1, 20));
        result.put("friendRequests", buildFriendRequestList(currentUserId));
        result.put("stats", buildSocialStats(currentUserId));
        result.put("matchQuota", buildMatchQuota(currentUser));
        return R.success(result);
    }

    @GetMapping("/summary")
    @SaCheckPermission(value = {"portal:social:chat:list", "portal:social:match:list", "portal:social:plaza:list"}, mode = SaMode.OR)
    public R getSocialSummary() {
        // 轻量概览接口，给匹配页/聊天页复用，避免每次都拉完整首页数据。
        Long currentUserId = currentUserId();
        // 同样先整理当前用户匹配额度状态。
        User currentUser = prepareMatchQuotaUser(userService.getById(currentUserId));
        Map<String, Object> result = new HashMap<>();
        result.put("currentUser", buildUserCard(currentUser));
        result.put("myProfile", socialProfileMapper.selectOne(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getUserId, currentUserId)
                .last("limit 1")));
        result.put("stats", buildSocialStats(currentUserId));
        result.put("matchQuota", buildMatchQuota(currentUser));
        return R.success(result);
    }

    @GetMapping("/friends")
    @SaCheckPermission("portal:social:chat:list")
    public R getFriends() {
        // 单独拉好友列表，供聊天页或局部刷新使用。
        return R.success(buildFriendList(currentUserId()));
    }

    @GetMapping("/match/recommend")
    @SaCheckPermission("portal:social:match:list")
    public R getMatchRecommend(@RequestParam(required = false) String location,
                               @RequestParam(required = false) Integer sex,
                               @RequestParam(required = false) String goal) {
        // 推荐匹配是“带筛选条件的列表模式”，和随机匹配不是一条逻辑。
        return R.success(buildMatchList(currentUserId(), location, sex, goal, 30));
    }

    @GetMapping("/match/random")
    @SaCheckPermission("portal:social:match:start")
    public R getRandomMatch(@RequestParam(required = false) String location,
                            @RequestParam(required = false) Integer sex,
                            @RequestParam(required = false) String goal,
                            @RequestParam(required = false) Long excludeUserId) {
        // 随机匹配除了筛选候选人，还要同步维护“当日匹配次数”配额。
        Long currentUserId = currentUserId();
        User currentUser = prepareMatchQuotaUser(userService.getById(currentUserId));
        Map<String, Object> quota = buildMatchQuota(currentUser);
        if (safeInt(currentUser.getMatchTodayCount()) >= DAILY_MATCH_LIMIT) {
            return R.error("今日匹配次数已达上限（3次），请明日再来。");
        }
        //排查已添加过的好友
        List<Map<String, Object>> candidates = buildRandomMatchCandidates(currentUserId, location, sex, goal, excludeUserId);
        if (candidates.isEmpty()) {
            // 没有候选人时，给前端返回更具体的原因文案，而不是简单空列表。
            return R.error(resolveNoMatchReason(currentUserId, location, sex, goal));
        }
        // 从符合条件的候选池中随机抽一个。
        Map<String, Object> match = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        // 随机匹配成功后立即扣减今日剩余次数。
        currentUser.setMatchTodayCount(safeInt(currentUser.getMatchTodayCount()) + 1);
        currentUser.setMatchDate(LocalDate.now());
        userService.updateById(currentUser);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("match", match);
        result.put("matchQuota", buildMatchQuota(currentUser));
        result.put("previousMatchQuota", quota);
        return R.success(result);
    }

    @GetMapping("/match/remaining")
    @SaCheckPermission("portal:social:match:start")
    public R getMatchRemaining() {
        // 单独提供额度查询，供前端局部刷新“今日剩余次数”。
        User currentUser = prepareMatchQuotaUser(userService.getById(currentUserId()));
        return R.success(buildMatchQuota(currentUser));
    }

    @GetMapping("/match/profile/{userId}")
    @SaCheckPermission("portal:social:match:view")
    public R getMatchProfile(@PathVariable Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return R.error("用户不存在");
        }
        SocialProfile profile = socialProfileMapper.selectOne(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getUserId, userId)
                .last("limit 1"));
        Map<String, Object> result = buildUserCard(user);
        result.put("goal", profile == null ? "" : profile.getGoal());
        result.put("intro", profile == null ? "" : profile.getIntro());
        // 附带当前与目标用户的关系快照，前端据此决定显示“已是好友/待处理/可申请”。
        result.putAll(buildRelationSnapshot(currentUserId(), userId));
        return R.success(result);
    }

    @PostMapping("/friend/add/{targetUserId}")
    @SaCheckPermission("portal:social:friend:apply")
    public R addFriend(@PathVariable Long targetUserId,
                       @RequestBody(required = false) FriendApplyRequest request) {
        // 好友申请要同时防重：不能加自己、不能重复申请、不能在对方已申请你时再次发起。
        Long currentUserId = currentUserId();
        if (Objects.equals(currentUserId, targetUserId)) {
            return R.error("不能添加自己为好友");
        }
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            return R.error("用户不存在");
        }
        if (isFriend(currentUserId, targetUserId)) {
            return R.success("你们已经是好友了");
        }
        SocialFriend outgoing = findRelation(currentUserId, targetUserId);
        if (outgoing != null && Objects.equals(outgoing.getStatus(), FRIEND_PENDING)) {
            return R.error("已发送好友申请，等待对方回复");
        }
        SocialFriend incoming = findRelation(targetUserId, currentUserId);
        if (incoming != null && Objects.equals(incoming.getStatus(), FRIEND_PENDING)) {
            return R.error("对方已向你发送好友申请，请先在申请中心处理");
        }
        String remark = request == null ? null : request.getRemark();
        // remark 为好友申请附言，可为空。
        saveFriendRequest(currentUserId, targetUserId, remark);
        // 通过 WebSocket 通知双方刷新申请中心和好友关系状态。
        socialSocketService.notifyUsers(List.of(currentUserId, targetUserId), "social-refresh", "friend-request");
        return R.success("好友申请已发送");
    }

    @GetMapping("/friend/requests")
    @SaCheckPermission("portal:social:chat:list")
    public R getFriendRequests() {
        // 申请列表包含“谁申请我”以及申请状态，供聊天页右侧申请面板使用。
        return R.success(buildFriendRequestList(currentUserId()));
    }

    @PostMapping("/friend/request/{requestId}/accept")
    @SaCheckPermission("portal:social:friend:accept")
    public R acceptFriendRequest(@PathVariable Long requestId) {
        Long currentUserId = currentUserId();
        SocialFriend request = socialFriendMapper.selectById(requestId);
        if (request == null || !Objects.equals(request.getFriendUserId(), currentUserId)) {
            return R.error("好友申请不存在");
        }
        if (!Objects.equals(request.getStatus(), FRIEND_PENDING)) {
            return R.error("该好友申请已处理");
        }
        // 同意申请后写入双向好友关系，并通知双方刷新好友列表与会话入口。
        // 一条是申请方 -> 被申请方，一条是反向关系，方便后续按 userId 单向查询。
        saveFriendPair(request.getUserId(), currentUserId, request.getRemark());
        saveFriendPair(currentUserId, request.getUserId(), null);
        request.setStatus(FRIEND_ACCEPTED);
        request.setHandleTime(LocalDateTime.now());
        socialFriendMapper.updateById(request);
        socialSocketService.notifyUsers(List.of(currentUserId, request.getUserId()), "social-refresh", "friend-accepted");
        return R.success("已同意好友申请");
    }

    @PostMapping("/friend/request/{requestId}/reject")
    @SaCheckPermission("portal:social:friend:reject")
    public R rejectFriendRequest(@PathVariable Long requestId) {
        Long currentUserId = currentUserId();
        SocialFriend request = socialFriendMapper.selectById(requestId);
        if (request == null || !Objects.equals(request.getFriendUserId(), currentUserId)) {
            return R.error("好友申请不存在");
        }
        if (!Objects.equals(request.getStatus(), FRIEND_PENDING)) {
            return R.error("该好友申请已处理");
        }
        // 拒绝时不建立好友关系，只更新申请状态和处理时间。
        request.setStatus(FRIEND_REJECTED);
        request.setHandleTime(LocalDateTime.now());
        socialFriendMapper.updateById(request);
        socialSocketService.notifyUsers(List.of(currentUserId, request.getUserId()), "social-refresh", "friend-rejected");
        return R.success("已拒绝好友申请");
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/friend/{targetUserId}")
    @SaCheckPermission("portal:social:friend:delete")
    public R deleteFriend(@PathVariable Long targetUserId) {
        Long currentUserId = currentUserId();
        if (!isFriend(currentUserId, targetUserId)) {
            return R.error("当前用户不是你的好友");
        }
        // 删除好友时顺带清理双方聊天会话和消息，避免保留失效聊天入口。
        // 这里一次性删除双向好友记录。
        socialFriendMapper.delete(new LambdaQueryWrapper<SocialFriend>()
                .and(wrapper -> wrapper
                        .and(w -> w.eq(SocialFriend::getUserId, currentUserId)
                                .eq(SocialFriend::getFriendUserId, targetUserId))
                        .or()
                        .and(w -> w.eq(SocialFriend::getUserId, targetUserId)
                                .eq(SocialFriend::getFriendUserId, currentUserId))));
        List<SocialChatSession> relatedSessions = socialChatSessionMapper.selectList(new LambdaQueryWrapper<SocialChatSession>()
                .and(wrapper -> wrapper
                        .and(w -> w.eq(SocialChatSession::getUserOneId, currentUserId)
                                .eq(SocialChatSession::getUserTwoId, targetUserId))
                        .or()
                        .and(w -> w.eq(SocialChatSession::getUserOneId, targetUserId)
                                .eq(SocialChatSession::getUserTwoId, currentUserId))));
        List<Long> sessionIds = relatedSessions.stream().map(SocialChatSession::getId).toList();
        if (!sessionIds.isEmpty()) {
            // 先删消息，再删会话，避免残留孤儿消息数据。
            socialChatMessageMapper.delete(new LambdaQueryWrapper<SocialChatMessage>()
                    .in(SocialChatMessage::getSessionId, sessionIds));
            socialChatSessionMapper.deleteBatchIds(sessionIds);
        }
        socialSocketService.notifyUsers(List.of(currentUserId, targetUserId), "social-refresh", "friend-deleted");
        return R.success("已删除好友");
    }

    @GetMapping("/profile/me")
    @SaCheckPermission("portal:social:profile:save")
    public R getMyProfile() {
        Long currentUserId = currentUserId();
        SocialProfile profile = socialProfileMapper.selectOne(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getUserId, currentUserId)
                .last("limit 1"));
        Map<String, Object> result = new HashMap<>();
        // user 存公共用户资料，profile 存社交扩展资料，前端编辑时需要两者组合展示。
        result.put("user", buildUserCard(userService.getById(currentUserId)));
        result.put("profile", profile);
        return R.success(result);
    }

    @PostMapping("/profile/me")
    @SaCheckPermission("portal:social:profile:save")
    public R saveMyProfile(@RequestBody ProfileRequest request) {
        // 社交资料既包含 user.location，也包含 social_profile.goal/intro，需要双表协同更新。
        if (request == null) {
            return R.error("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getGoal())) {
            return R.error("我的目标不能为空");
        }
        if (!StringUtils.hasText(request.getLocation())) {
            return R.error("所属地区不能为空");
        }
        Long currentUserId = currentUserId();
        User user = userService.getById(currentUserId);
        if (user == null) {
            return R.error("当前用户不存在");
        }
        // location 落在 user 主表里，供匹配筛选和用户卡片复用。
        user.setLocation(request.getLocation().trim());
        userService.updateById(user);
        SocialProfile profile = socialProfileMapper.selectOne(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getUserId, currentUserId)
                .last("limit 1"));
        if (profile == null) {
            // 第一次填写社交资料时直接创建。
            profile = new SocialProfile();
            profile.setUserId(currentUserId);
            profile.setStatus(1);
            profile.setGoal(request.getGoal().trim());
            profile.setIntro(normalizeIntro(request.getIntro()));
            socialProfileMapper.insert(profile);
        } else {
            // 已存在则走更新。
            profile.setGoal(request.getGoal().trim());
            profile.setIntro(normalizeIntro(request.getIntro()));
            socialProfileMapper.updateById(profile);
        }
        return R.success("保存成功");
    }

    @GetMapping("/feed")
    @SaCheckPermission("portal:social:plaza:list")
    public R getFeed(@RequestParam(defaultValue = "1") Integer pageNum,
                     @RequestParam(defaultValue = "20") Integer pageSize) {
        // pageNum 表示前端请求第几页动态。
        // pageSize 表示这一页最多返回多少条动态。
        // 动态流支持分页，但当前前端默认只拉第一页。
        // currentUserId() 用来补充“我是否点赞过”这类个性化字段。
        // buildFeed 会把帖子、作者、点赞态、评论树拼成前端直接可用的数据结构。
        return R.success(buildFeed(currentUserId(), pageNum, pageSize));
    }

    @PostMapping("/feed/post")
    @SaCheckPermission("portal:social:post:add")
    public R publishPost(@RequestBody PostRequest request) {
        // 悦吧发帖的最低要求是正文非空。
        if (!StringUtils.hasText(request.getContent())) {
            return R.error("动态内容不能为空");
        }
        // 动态图片 URL 已在前端通过公共上传接口获取，这里只负责把文本和图片数组落库。
        // 创建帖子实体，准备写入 social_post。
        SocialPost post = new SocialPost();
        // 发帖人固定取当前登录用户。
        post.setUserId(currentUserId());
        // 没传类型时默认按日常动态处理。
        post.setPostType(StringUtils.hasText(request.getPostType()) ? request.getPostType() : "daily");
        // topic 是前端展示用的话题标签。
        post.setTopic(request.getTopic());
        // 正文原样保存，后续列表页直接读取展示。
        post.setContent(request.getContent());
        // 图片列表为空时写空数组，避免前端判空分支过多。
        post.setImages(request.getImages() == null ? List.of() : request.getImages());
        // 新帖子初始点赞数一定是 0。
        post.setLikeCount(0);
        // 新帖子初始评论数也一定是 0。
        post.setCommentCount(0);
        // status=1 表示正常可见。
        post.setStatus(1);
        // 插入数据库，完成发帖。
        socialPostMapper.insert(post);
        // 返回简单成功文案即可，前端通常会自行刷新动态流。
        return R.success("发布成功");
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/feed/post/{id}/like")
    @SaCheckPermission("portal:social:post:like")
    public R toggleLike(@PathVariable Long id) {
        // 当前用户是谁，决定这次点赞/取消点赞的操作者。
        Long currentUserId = currentUserId();
        // 同一动态的点赞切换做同步锁，避免高并发下计数出现脏写。
        synchronized (getPostLikeLock(id)) {
            // 先查帖子是否存在，且必须是正常状态。
            SocialPost post = socialPostMapper.selectById(id);
            if (post == null || !Objects.equals(post.getStatus(), 1)) {
                return R.error("动态不存在");
            }
            // 这段查询专门找“当前用户是否已经给当前帖子点过赞”。
            LambdaQueryWrapper<SocialPostLike> userLikeWrapper = new LambdaQueryWrapper<SocialPostLike>()
                    .eq(SocialPostLike::getPostId, id)
                    .eq(SocialPostLike::getUserId, currentUserId)
                    .last("limit 1");
            // existed 不为空表示用户之前已经点赞过。
            SocialPostLike existed = socialPostLikeMapper.selectOne(userLikeWrapper);
            // liked 是本次操作后的最终点赞状态，返回给前端更新按钮状态。
            boolean liked;
            if (existed == null) {
                // 没有旧记录时，说明这次是新增点赞。
                SocialPostLike like = new SocialPostLike();
                // 记录点赞属于哪条帖子。
                like.setPostId(id);
                // 记录点赞人。
                like.setUserId(currentUserId);
                // 点赞时间用于后续可能的排序或审计。
                like.setCreateTime(LocalDateTime.now());
                // 真正落库写入点赞记录。
                socialPostLikeMapper.insert(like);
                // 不存在点赞记录则执行“点赞”。
                liked = true;
            } else {
                // 已有点赞记录时，说明这次是取消点赞。
                socialPostLikeMapper.delete(new LambdaQueryWrapper<SocialPostLike>()
                        .eq(SocialPostLike::getPostId, id)
                        .eq(SocialPostLike::getUserId, currentUserId));
                // 已存在则执行“取消点赞”。
                liked = false;
            }
            // 点赞数不直接做 +1/-1，而是重新 count，避免并发下计数漂移。
            // 重新统计这条动态的真实点赞数。
            int latestLikeCount = socialPostLikeMapper.selectCount(new LambdaQueryWrapper<SocialPostLike>()
                    .eq(SocialPostLike::getPostId, id)).intValue();
            // 再次兜底，避免极端情况下出现负数。
            post.setLikeCount(Math.max(0, latestLikeCount));
            // 把最新点赞总数回写到帖子主表。
            socialPostMapper.updateById(post);
            // 组装前端最关心的两个字段：我当前是否已点赞、总点赞数是多少。
            Map<String, Object> result = new HashMap<>();
            result.put("liked", liked);
            result.put("likeCount", post.getLikeCount());
            // 返回最新点赞态，前端可直接更新界面。
            return R.success(result);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/feed/post/{id}/comment")
    @SaCheckPermission("portal:social:post:comment")
    public R commentPost(@PathVariable Long id, @RequestBody CommentRequest request) {
        // 评论既支持根评论，也支持回复某条评论，因此要校验 parentId 是否属于当前动态。
        // request 整体不能为空，否则后续取字段会空指针。
        if (request == null) {
            return R.error("请求参数不能为空");
        }
        // 评论正文不能为空。
        if (!StringUtils.hasText(request.getContent())) {
            return R.error("评论内容不能为空");
        }
        // 先确认被评论的动态仍然存在且可见。
        SocialPost post = socialPostMapper.selectById(id);
        if (post == null || !Objects.equals(post.getStatus(), 1)) {
            return R.error("动态不存在");
        }
        // 只有在回复评论时才校验 parentId。
        if (request.getParentId() != null) {
            // 取出被回复的父评论。
            SocialPostComment parentComment = socialPostCommentMapper.selectById(request.getParentId());
            // 父评论不存在，或父评论不属于当前动态，都视为非法回复目标。
            if (parentComment == null || !Objects.equals(parentComment.getPostId(), id)) {
                return R.error("回复目标不存在");
            }
        }
        // 创建评论实体。
        SocialPostComment comment = new SocialPostComment();
        // 记录评论属于哪条动态。
        comment.setPostId(id);
        // 评论作者就是当前登录用户。
        comment.setUserId(currentUserId());
        // parentId 为空表示根评论，不为空表示回复评论。
        comment.setParentId(request.getParentId());
        // 保存评论正文。
        comment.setContent(request.getContent());
        // 插入评论表。
        socialPostCommentMapper.insert(comment);
        // 评论数同样采用重新 count 的方式回写。
        // 重新统计当前动态的评论总数。
        int latestCommentCount = socialPostCommentMapper.selectCount(new LambdaQueryWrapper<SocialPostComment>()
                .eq(SocialPostComment::getPostId, id)).intValue();
        // 防御性兜底，避免出现负数。
        post.setCommentCount(Math.max(0, latestCommentCount));
        // 回写帖子主表中的 comment_count。
        socialPostMapper.updateById(post);
        // 返回成功，让前端刷新评论区。
        return R.success("评论成功");
    }

    @GetMapping("/chat/sessions")
    @SaCheckPermission("portal:social:chat:list")
    public R getChatSessions() {
        // 会话列表用于左侧聊天导航栏。
        // buildSessionList 会补齐目标用户信息、未读数、是否仍可聊天等字段。
        return R.success(buildSessionList(currentUserId()));
    }

    @PostMapping("/chat/session/{targetUserId}")
    @SaCheckPermission("portal:social:chat:send")
    public R createChatSession(@PathVariable Long targetUserId) {
        // 禁止给自己创建聊天会话。
        if (Objects.equals(targetUserId, currentUserId())) {
            return R.error("不能和自己发起聊天");
        }
        // 只有当前仍是好友时才允许建立或复用聊天会话。
        if (!isFriend(currentUserId(), targetUserId)) {
            return R.error("只有好友之间才能发起聊天");
        }
        // 有会话就复用，没有才创建，避免一对好友产生多个平行会话。
        // findOrCreateSession 内部还做了并发锁保护。
        SocialChatSession session = findOrCreateSession(currentUserId(), targetUserId);
        // 返回的不是数据库实体，而是前端聊天列表可直接使用的会话视图对象。
        return R.success(buildSessionItem(session, currentUserId()));
    }

    @GetMapping("/chat/messages/{sessionId}")
    @SaCheckPermission("portal:social:chat:list")
    public R getChatMessages(@PathVariable Long sessionId) {
        // 当前登录用户，用于权限校验和 isMine 判断。
        Long currentUserId = currentUserId();
        // 先查会话本身。
        SocialChatSession session = socialChatSessionMapper.selectById(sessionId);
        // 会话不存在，或当前用户不在该会话里，都不允许查看消息。
        if (session == null || !sessionContainsUser(session, currentUserId)) {
            return R.error("会话不存在");
        }
        // 按时间正序拉取整段消息历史，保证前端从旧到新渲染。
        List<SocialChatMessage> messages = socialChatMessageMapper.selectList(new LambdaQueryWrapper<SocialChatMessage>()
                .eq(SocialChatMessage::getSessionId, sessionId)
                .orderByAsc(SocialChatMessage::getCreateTime));

        // 只挑出“发给我且尚未已读”的消息。
        List<SocialChatMessage> unreadMessages = messages.stream()
                .filter(item -> Objects.equals(item.getReceiverId(), currentUserId) && !Objects.equals(item.getIsRead(), 1))
                .toList();
        // 拉取消息的同时把当前会话未读消息改成已读，保证会话角标及时清零。
        for (SocialChatMessage item : unreadMessages) {
            // 标记为已读。
            item.setIsRead(1);
            // 逐条更新数据库。
            socialChatMessageMapper.updateById(item);
        }

        // 把数据库消息对象转换成前端消息气泡直接可消费的结构。
        List<Map<String, Object>> result = messages.stream().map(item -> {
            // 这里把数据库消息对象转换成更适合前端直接渲染的消息结构。
            Map<String, Object> row = new LinkedHashMap<>();
            // 消息主键。
            row.put("id", item.getId());
            // 所属会话 id。
            row.put("sessionId", item.getSessionId());
            // 发送方 id，前端有时会用于比对身份。
            row.put("senderId", item.getSenderId());
            // 接收方 id。
            row.put("receiverId", item.getReceiverId());
            // 消息正文。
            row.put("content", item.getContent());
            // isMine 让前端直接决定消息靠左还是靠右显示。
            row.put("isMine", Objects.equals(item.getSenderId(), currentUserId));
            // 发送时间，供前端显示。
            row.put("createTime", item.getCreateTime());
            return row;
        }).toList();
        // 返回整个会话历史。
        return R.success(result);
    }

    @PostMapping("/chat/message")
    @SaCheckPermission("portal:social:chat:send")
    public R sendMessage(@RequestBody MessageRequest request) {
        // 发消息前必须再次校验好友关系，避免前端缓存过期后继续给非好友发消息。
        // 空消息不允许发送。
        if (!StringUtils.hasText(request.getContent())) {
            return R.error("消息内容不能为空");
        }
        // 接收方不能为空，否则消息无法定向发送。
        if (request.getTargetUserId() == null) {
            return R.error("接收方不能为空");
        }
        // 当前用户就是消息发送人。
        Long currentUserId = currentUserId();
        // 优先复用前端传来的 sessionId；如果前端没传，就按双方用户关系找或建会话。
        SocialChatSession session = request.getSessionId() == null
                ? findOrCreateSession(currentUserId, request.getTargetUserId())
                // 前端如果带了 sessionId，就优先复用现有会话。
                : socialChatSessionMapper.selectById(request.getSessionId());
        // 防止伪造 sessionId 或发送到不属于自己的会话。
        if (session == null || !sessionContainsUser(session, currentUserId)) {
            return R.error("会话不存在");
        }
        // 发送前再校验一次好友关系，避免删好友后还能继续通过旧会话发消息。
        if (!isFriend(currentUserId, request.getTargetUserId())) {
            return R.error("对方已不是你的好友，请重新申请后再聊天");
        }

        // 创建消息实体。
        SocialChatMessage message = new SocialChatMessage();
        message.setSessionId(session.getId());// 绑定到当前会话。
        message.setSenderId(currentUserId);// 发送方是当前用户。
        message.setReceiverId(request.getTargetUserId());// 接收方是请求里的目标用户。
        message.setContent(request.getContent());// 保存消息正文。
        message.setIsRead(0);// 新消息默认未读，等待接收方拉取或进入会话时再标记已读。
        message.setCreateTime(LocalDateTime.now());// 新消息默认未读，等待接收方拉取或进入会话时再标记已读。
        socialChatMessageMapper.insert(message);// 新消息默认未读，等待接收方拉取或进入会话时再标记已读。
        // 发送消息即代表发送方在活跃聊天，把对方发给当前用户的未读消息标记为已读。
        List<SocialChatMessage> incomingUnread = socialChatMessageMapper.selectList(new LambdaQueryWrapper<SocialChatMessage>()
                .eq(SocialChatMessage::getSessionId, session.getId())
                .eq(SocialChatMessage::getReceiverId, currentUserId)
                .eq(SocialChatMessage::getIsRead, 0));
        for (SocialChatMessage item : incomingUnread) {
            item.setIsRead(1);
            socialChatMessageMapper.updateById(item);
        }
        // 更新会话摘要，前端会据此在会话列表中展示最后一条消息。
        session.setLastMessage(request.getContent());// 最后一条消息内容同步写入会话表。
        session.setLastMessageTime(message.getCreateTime());// 会话时间同步到本条消息的发送时间。
        socialChatSessionMapper.updateById(session);// 回写会话表，保证最近聊天排序正确。
        // 通知双方刷新聊天相关面板，同时直接推送消息内容，避免事务提交延迟导致接收方拉不到新消息。
        socialSocketService.notifyUsers(
                List.of(currentUserId, request.getTargetUserId()),
                "social-refresh",
                "message",
                Map.of(
                        "sessionId", String.valueOf(session.getId()),
                        "messageId", String.valueOf(message.getId()),
                        "senderId", String.valueOf(currentUserId),
                        "content", request.getContent(),
                        "createTime", message.getCreateTime().toString()
                )
        );
        // 返回更新后的会话视图对象，前端可以立刻刷新当前会话摘要。
        return R.success(buildSessionItem(session, currentUserId));
    }

    private List<Map<String, Object>> buildFeed(Long currentUserId, Integer pageNum, Integer pageSize) {
        // 动态流返回的不是裸帖子，而是帖子 + 作者 + 点赞态 + 评论树的组合视图对象。
        // 先创建 MyBatis-Plus 分页对象。
        Page<SocialPost> page = new Page<>(pageNum, pageSize);
        // 这里只查状态正常的动态，并按发布时间倒序展示最新内容。
        Page<SocialPost> postPage = socialPostMapper.selectPage(page, new LambdaQueryWrapper<SocialPost>()
                .eq(SocialPost::getStatus, 1)
                .orderByDesc(SocialPost::getCreateTime));
        // 拿到当前页帖子记录。
        List<SocialPost> posts = postPage.getRecords();
        // 空列表直接返回，后续就不必再查评论和点赞。
        if (posts.isEmpty()) {
            return List.of();
        }

        // 收集当前页所有帖子 id，供批量查评论和点赞。
        Set<Long> postIds = posts.stream().map(SocialPost::getId).collect(Collectors.toSet());
        // 先把帖子作者 id 收集起来，后面统一批量查用户。
        Set<Long> userIds = posts.stream().map(SocialPost::getUserId).collect(Collectors.toSet());

        // 评论和作者信息分两批查出，再在内存中组装成前端需要的嵌套结构。
        List<SocialPostComment> comments = socialPostCommentMapper.selectList(new LambdaQueryWrapper<SocialPostComment>()
                .in(SocialPostComment::getPostId, postIds)
                .orderByAsc(SocialPostComment::getCreateTime));
        // 评论作者也要补进 userIds，方便后面评论区一起展示用户卡片。
        comments.forEach(item -> userIds.add(item.getUserId()));

        // 批量加载帖子作者和评论作者，避免 N+1 查询。
        Map<Long, User> userMap = loadUsersByIds(userIds);
        // 按 postId 对评论分组，方便一篇帖子一组评论去构建评论树。
        Map<Long, List<SocialPostComment>> commentMap = comments.stream()
                .collect(Collectors.groupingBy(SocialPostComment::getPostId));

        // 查出“当前用户点赞过哪些帖子”，用于给前端补 liked 布尔值。
        Set<Long> likedPostIds = socialPostLikeMapper.selectList(new LambdaQueryWrapper<SocialPostLike>()
                        .eq(SocialPostLike::getUserId, currentUserId)
                        .in(SocialPostLike::getPostId, postIds))
                .stream()
                .map(SocialPostLike::getPostId)
                .collect(Collectors.toSet());

        // 最终逐条帖子映射为前端直接消费的悦吧动态结构。
        return posts.stream().map(post -> {
            Map<String, Object> row = new LinkedHashMap<>();
            // 帖子主键。
            row.put("id", post.getId());
            // 动态类型，比如 daily。
            row.put("postType", post.getPostType());
            // 话题文本。
            row.put("topic", post.getTopic());
            // 正文内容。
            row.put("content", post.getContent());
            // 图片数组为空时返回空列表，不返回 null。
            row.put("images", post.getImages() == null ? List.of() : post.getImages());
            // 点赞数做 null 安全兜底。
            row.put("likeCount", safeInt(post.getLikeCount()));
            // 评论数做 null 安全兜底。
            row.put("commentCount", safeInt(post.getCommentCount()));
            // 当前用户是否点过赞。
            row.put("liked", likedPostIds.contains(post.getId()));
            // 发布时间。
            row.put("createTime", post.getCreateTime());
            // 作者用户卡片。
            row.put("author", buildUserCard(userMap.get(post.getUserId())));
            // 当前帖子对应的评论树。
            row.put("comments", buildCommentTree(commentMap.getOrDefault(post.getId(), List.of()), userMap));
            return row;
        }).toList();
    }

    private List<Map<String, Object>> buildCommentTree(List<SocialPostComment> comments, Map<Long, User> userMap) {
        // 没有评论时直接返回空树。
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        // commentEntityMap 用于后续通过 parentId 找到被回复的那条评论实体。
        Map<Long, SocialPostComment> commentEntityMap = comments.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(SocialPostComment::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        // commentNodeMap 存放已经转成前端节点结构的评论数据。
        Map<Long, Map<String, Object>> commentNodeMap = new LinkedHashMap<>();
        // 第一轮先把所有评论都转成节点，但先不处理父子挂载关系。
        comments.forEach(comment -> {
            Map<String, Object> commentRow = new LinkedHashMap<>();
            // 评论 id。
            commentRow.put("id", comment.getId());
            // 父评论 id，根评论时为空。
            commentRow.put("parentId", comment.getParentId());
            // 评论正文。
            commentRow.put("content", comment.getContent());
            // 评论时间。
            commentRow.put("createTime", comment.getCreateTime());
            // 评论作者卡片。
            commentRow.put("author", buildUserCard(userMap.get(comment.getUserId())));
            // 找出父评论实体，后续构建 replyToUser。
            SocialPostComment parent = comment.getParentId() == null ? null : commentEntityMap.get(comment.getParentId());
            // replyToUser 只保存被回复人的用户卡片，方便前端显示 `@某人`。
            commentRow.put("replyToUser", parent == null ? null : buildUserCard(userMap.get(parent.getUserId())));
            // replies 先放空数组，第二轮再挂孩子节点。
            commentRow.put("replies", new ArrayList<Map<String, Object>>());
            // 以评论 id 为键缓存节点。
            commentNodeMap.put(comment.getId(), commentRow);
        });

        // roots 存放最终的根评论列表。
        List<Map<String, Object>> roots = new ArrayList<>();
        // 第二轮根据 parentId 把每条评论挂到对应父节点下面。
        comments.forEach(comment -> {
            Map<String, Object> current = commentNodeMap.get(comment.getId());
            Long parentId = comment.getParentId();
            if (parentId != null && commentNodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> replies = (List<Map<String, Object>>) commentNodeMap.get(parentId).get("replies");
                // parentId 命中时挂到父评论 replies 下。
                replies.add(current);
                return;
            }
            // 找不到父节点或本身就是根评论时，直接放到 roots。
            roots.add(current);
        });
        return roots;
    }

    private List<Map<String, Object>> buildMatchList(Long currentUserId, String location, Integer sex, String goal, int limit) {
        // 匹配列表以 social_profile 为主表，因为 goal/intro 都存这里。
        List<SocialProfile> profiles = socialProfileMapper.selectList(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getStatus, 1)
                .ne(SocialProfile::getUserId, currentUserId)
                .eq(StringUtils.hasText(goal), SocialProfile::getGoal, goal)
                .orderByDesc(SocialProfile::getUpdateTime));
        if (profiles.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = profiles.stream().map(SocialProfile::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsersByIds(userIds);
        Map<Long, Map<String, Object>> relationMap = buildRelationSnapshots(currentUserId, userIds);

        return profiles.stream()
                .filter(profile -> {
                    User user = userMap.get(profile.getUserId());
                    if (user == null) return false;
                    // sex/location 属于 user 主表字段，因此在内存中过滤。
                    if (sex != null && !Objects.equals(user.getSex(), sex)) return false;
                    return !StringUtils.hasText(location) || Objects.equals(user.getLocation(), location);
                })
                .limit(limit)
                .map(profile -> {
                    User user = userMap.get(profile.getUserId());
                    Map<String, Object> row = buildUserCard(user);
                    row.put("goal", profile.getGoal());
                    row.put("intro", profile.getIntro());
                    row.putAll(relationMap.getOrDefault(profile.getUserId(), defaultRelationSnapshot()));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRandomMatchCandidates(Long currentUserId, String location, Integer sex, String goal, Long excludeUserId) {
        // 先拿到当前用户已建立好友关系的对象，随机匹配时要排除掉。
        Set<Long> friendIds = socialFriendMapper.selectList(new LambdaQueryWrapper<SocialFriend>()
                        .eq(SocialFriend::getUserId, currentUserId)
                        .eq(SocialFriend::getStatus, 1))
                .stream()
                .map(SocialFriend::getFriendUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return buildMatchList(currentUserId, location, sex, goal, Integer.MAX_VALUE)
                .stream()
                .filter(item -> {
                    Object idValue = item.get("id");
                    if (idValue == null) return false;
                    Long userId = Long.valueOf(String.valueOf(idValue));
                    if (friendIds.contains(userId)) return false;
                    // continue match 时还要排除当前卡片上已经展示过的人。
                    return excludeUserId == null || !Objects.equals(userId, excludeUserId);
                })
                .toList();
    }

    private String resolveNoMatchReason(Long currentUserId, String location, Integer sex, String goal) {
        User currentUser = userService.getById(currentUserId);

        // 这里不是简单返回“空”，而是逐层分析到底卡在地区、性别还是目标条件。
        List<SocialProfile> profiles = socialProfileMapper.selectList(new LambdaQueryWrapper<SocialProfile>()
                .eq(SocialProfile::getStatus, 1)
                .ne(SocialProfile::getUserId, currentUserId));
        if (profiles.isEmpty()) {
            return "暂时还没有其他用户完善社交资料";
        }

        Set<Long> userIds = profiles.stream().map(SocialProfile::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsersByIds(userIds);

        List<SocialProfile> locationMatched = profiles;
        if (StringUtils.hasText(location)) {
            locationMatched = profiles.stream()
                    .filter(profile -> {
                        User user = userMap.get(profile.getUserId());
                        return user != null && Objects.equals(user.getLocation(), location);
                    })
                    .toList();
            if (locationMatched.isEmpty()) {
                if (currentUser != null && Objects.equals(currentUser.getLocation(), location)) {
                    return "当前地区符合条件的只有你自己";
                }
                return "该地区暂时没有其他可匹配用户";
            }
        }

        List<SocialProfile> sexMatched = locationMatched;
        if (sex != null) {
            sexMatched = locationMatched.stream()
                    .filter(profile -> {
                        User user = userMap.get(profile.getUserId());
                        return user != null && Objects.equals(user.getSex(), sex);
                    })
                    .toList();
            if (sexMatched.isEmpty()) {
                return StringUtils.hasText(location) ? "该地区暂无符合性别条件的用户" : "暂无符合性别条件的用户";
            }
        }

        List<SocialProfile> goalMatched = sexMatched;
        if (StringUtils.hasText(goal)) {
            goalMatched = sexMatched.stream()
                    .filter(profile -> Objects.equals(profile.getGoal(), goal))
                    .toList();
            if (goalMatched.isEmpty()) {
                return StringUtils.hasText(location) ? "该地区暂无符合目标条件的用户" : "暂无符合目标条件的用户";
            }
        }

        return "暂无更多搭子";
    }

    private List<Map<String, Object>> buildSessionList(Long currentUserId) {
        // 会话按最近消息时间倒序，保证最近聊天排在最上面。
        // 只查当前用户参与过的会话。
        List<SocialChatSession> sessions = socialChatSessionMapper.selectList(new LambdaQueryWrapper<SocialChatSession>()
                .and(wrapper -> wrapper.eq(SocialChatSession::getUserOneId, currentUserId)
                        .or()
                        .eq(SocialChatSession::getUserTwoId, currentUserId))
                .orderByDesc(SocialChatSession::getLastMessageTime)
                .orderByDesc(SocialChatSession::getUpdateTime));
        // 每条会话再补齐目标用户、未读数、好友状态等展示字段。
        return sessions.stream()
                .map(session -> buildSessionItem(session, currentUserId))
                .toList();
    }

    private Map<String, Object> buildSessionItem(SocialChatSession session, Long currentUserId) {
        // targetUserId 取会话中“不是我”的那一方。
        Long targetUserId = Objects.equals(session.getUserOneId(), currentUserId) ? session.getUserTwoId() : session.getUserOneId();
        // 查询会话对端用户。
        User targetUser = userService.getById(targetUserId);
        // canChat 是否可用由当前是否仍然互为好友决定。
        boolean friend = isFriend(currentUserId, targetUserId);
        // 统计当前用户在该会话下尚未读取的消息数。
        Long unreadCount = socialChatMessageMapper.selectCount(new LambdaQueryWrapper<SocialChatMessage>()
                .eq(SocialChatMessage::getSessionId, session.getId())
                .eq(SocialChatMessage::getReceiverId, currentUserId)
                .eq(SocialChatMessage::getIsRead, 0));
        // 组装聊天列表项。
        Map<String, Object> row = new LinkedHashMap<>();
        // 会话 id。
        row.put("id", session.getId());
        // 对端用户卡片。
        row.put("targetUser", buildUserCard(targetUser));
        // 最近一条消息摘要。
        row.put("lastMessage", session.getLastMessage());
        // 最近消息时间。
        row.put("lastMessageTime", session.getLastMessageTime());
        // 未读数角标。
        row.put("unreadCount", unreadCount);
        // 当前是否仍是好友。
        row.put("isFriend", friend);
        // 是否允许继续发送消息。
        row.put("canChat", friend);
        return row;
    }

    private List<Map<String, Object>> buildFriendList(Long currentUserId) {
        // 好友列表只取已接受状态。
        List<SocialFriend> relations = socialFriendMapper.selectList(new LambdaQueryWrapper<SocialFriend>()
                .eq(SocialFriend::getUserId, currentUserId)
                .eq(SocialFriend::getStatus, FRIEND_ACCEPTED)
                .orderByDesc(SocialFriend::getHandleTime)
                .orderByDesc(SocialFriend::getCreateTime));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = relations.stream()
                .map(SocialFriend::getFriendUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsersByIds(userIds);
        return relations.stream().map(relation -> {
            User targetUser = userMap.get(relation.getFriendUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", relation.getFriendUserId());
            row.put("friendId", relation.getId());
            row.put("user", buildUserCard(targetUser));
            row.put("createTime", relation.getCreateTime());
            row.put("handleTime", relation.getHandleTime());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> buildFriendRequestList(Long currentUserId) {
        // 这里取的是“收到的、待处理的”申请，不包含我发出去的申请。
        List<SocialFriend> requests = socialFriendMapper.selectList(new LambdaQueryWrapper<SocialFriend>()
                .eq(SocialFriend::getFriendUserId, currentUserId)
                .eq(SocialFriend::getStatus, FRIEND_PENDING)
                .orderByDesc(SocialFriend::getCreateTime));
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = requests.stream()
                .map(SocialFriend::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsersByIds(userIds);
        return requests.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("status", item.getStatus());
            row.put("remark", StringUtils.hasText(item.getRemark()) ? item.getRemark() : "我想和你成为搭子");
            row.put("createTime", item.getCreateTime());
            row.put("fromUser", buildUserCard(userMap.get(item.getUserId())));
            return row;
        }).toList();
    }

    private Map<String, Object> buildSocialStats(Long currentUserId) {
        // 社交统计目前只做轻量概览：动态数、会话数、可匹配总人数。
        Map<String, Object> result = new HashMap<>();
        result.put("postCount", socialPostMapper.selectCount(new LambdaQueryWrapper<SocialPost>().eq(SocialPost::getUserId, currentUserId)));
        result.put("sessionCount", socialChatSessionMapper.selectCount(new LambdaQueryWrapper<SocialChatSession>()
                .and(wrapper -> wrapper.eq(SocialChatSession::getUserOneId, currentUserId).or().eq(SocialChatSession::getUserTwoId, currentUserId))));
        result.put("matchCount", socialProfileMapper.selectCount(new LambdaQueryWrapper<SocialProfile>().eq(SocialProfile::getStatus, 1)));
        return result;
    }

    private Map<String, Object> buildMatchQuota(User user) {
        // 额度返回统一包含上限、已用、剩余、是否耗尽和计数日期。
        User normalizedUser = prepareMatchQuotaUser(user);
        int usedCount = Math.min(DAILY_MATCH_LIMIT, safeInt(normalizedUser == null ? 0 : normalizedUser.getMatchTodayCount()));
        int remainingCount = Math.max(0, DAILY_MATCH_LIMIT - usedCount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dailyLimit", DAILY_MATCH_LIMIT);
        result.put("usedCount", usedCount);
        result.put("remainingCount", remainingCount);
        result.put("exhausted", remainingCount <= 0);
        result.put("matchDate", normalizedUser == null ? null : normalizedUser.getMatchDate());
        return result;
    }

    private Map<String, Object> buildUserCard(User user) {
        if (user == null) {
            return Map.of();
        }
        // 用户卡片是社交模块的通用轻量视图对象。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("phone", user.getPhone());
        result.put("nickName", user.getNickName());
        result.put("avatar", user.getAvatar());
        result.put("sex", user.getSex());
        result.put("location", user.getLocation());
        result.put("locationName", resolveLocationName(user.getLocation()));
        return result;
    }

    private Map<Long, User> loadUsersByIds(Set<Long> userIds) {
        // 统一批量查询用户，避免在列表装配时出现 N+1 查询。
        Set<Long> validIds = userIds == null
                ? Set.of()
                : userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(validIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a));
    }

    private User prepareMatchQuotaUser(User user) {
        if (user == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        boolean changed = false;
        if (user.getMatchTodayCount() == null) {
            // 历史老数据可能没有初始化计数字段。
            user.setMatchTodayCount(0);
            changed = true;
        }
        if (!Objects.equals(user.getMatchDate(), today)) {
            // 跨天后自动重置匹配次数。
            user.setMatchTodayCount(0);
            user.setMatchDate(today);
            changed = true;
        }
        if (changed) {
            userService.updateById(user);
        }
        return user;
    }

    private String resolveLocationName(String locationCode) {
        if (!StringUtils.hasText(locationCode)) {
            return "";
        }
        if (locationCode.contains("/")) {
            // 已经是“省/市/区”路径格式时直接返回。
            return locationCode;
        }
        Map<String, Dict> dictMap = getRegionDictMap();
        List<String> path = new ArrayList<>();
        Dict current = dictMap.get(locationCode);
        while (current != null) {
            path.add(current.getDictName());
            String parentCode = current.getParentCode();
            // 走到根节点或脏父节点时停止向上回溯。
            if (!StringUtils.hasText(parentCode) || "0".equals(parentCode) || "-1".equals(parentCode) || !dictMap.containsKey(parentCode)) {
                break;
            }
            current = dictMap.get(parentCode);
        }
        if (path.isEmpty()) {
            return locationCode;
        }
        List<String> reversed = new ArrayList<>(path);
        java.util.Collections.reverse(reversed);
        return String.join("/", reversed);
    }

    private Map<String, Dict> getRegionDictMap() {
        Map<String, Dict> cached = regionDictCache;
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        synchronized (this) {
            // 懒加载 + 简单缓存，避免每次构建用户卡片都去查地区字典表。
            if (regionDictCache != null && !regionDictCache.isEmpty()) {
                return regionDictCache;
            }
            regionDictCache = dictService.list(new LambdaQueryWrapper<Dict>()
                            .eq(Dict::getDictType, "region")
                            .eq(Dict::getDeleted, 0))
                    .stream()
                    .collect(Collectors.toMap(Dict::getDictCode, item -> item, (a, b) -> a));
            return regionDictCache;
        }
    }

    private SocialChatSession findOrCreateSession(Long currentUserId, Long targetUserId) {
        // 先根据双方用户生成一个与顺序无关的锁键。
        String sessionKey = buildChatSessionKey(currentUserId, targetUserId);
        // 以“较小 id:较大 id”为锁键，避免并发创建重复会话。
        synchronized (chatSessionLocks.computeIfAbsent(sessionKey, key -> new Object())) {
            // 进锁后先查一次已有会话，避免重复创建。
            SocialChatSession session = findExistingSession(currentUserId, targetUserId);
            if (session != null) {
                // 查到就直接复用。
                return session;
            }
            // 查不到才真正创建新会话。
            session = new SocialChatSession();
            // userOneId 固定放较小 id，保证同一对用户会落在统一顺序。
            session.setUserOneId(Math.min(currentUserId, targetUserId));
            // userTwoId 固定放较大 id。
            session.setUserTwoId(Math.max(currentUserId, targetUserId));
            // 新会话先给一个默认摘要，方便最近聊天列表有内容可显示。
            session.setLastMessage("已建立聊天");
            // 初始化最近消息时间为建会话时间。
            session.setLastMessageTime(LocalDateTime.now());
            // 插入会话表。
            socialChatSessionMapper.insert(session);
            // 返回新建好的会话实体。
            return session;
        }
    }

    private boolean sessionContainsUser(SocialChatSession session, Long userId) {
        // 只要用户是会话双方中的任意一方，就视为拥有该会话访问权限。
        return Objects.equals(session.getUserOneId(), userId) || Objects.equals(session.getUserTwoId(), userId);
    }

    private SocialChatSession findExistingSession(Long currentUserId, Long targetUserId) {
        // 理论上一对好友只应有一个会话；这里按更新时间倒序取最新一条兜底。
        // 查询时要兼容 A->B 和 B->A 两种存储顺序。
        List<SocialChatSession> sessions = socialChatSessionMapper.selectList(new LambdaQueryWrapper<SocialChatSession>()
                .and(wrapper -> wrapper
                        .and(w -> w.eq(SocialChatSession::getUserOneId, currentUserId).eq(SocialChatSession::getUserTwoId, targetUserId))
                        .or()
                        .and(w -> w.eq(SocialChatSession::getUserOneId, targetUserId).eq(SocialChatSession::getUserTwoId, currentUserId)))
                .orderByDesc(SocialChatSession::getUpdateTime)
                .orderByDesc(SocialChatSession::getId)
                .last("limit 5"));
        // 查到多条时取最新一条作为有效会话。
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    private String buildChatSessionKey(Long currentUserId, Long targetUserId) {
        // 统一把较小 id 放前面，保证 A-B 与 B-A 得到同一个 key。
        long userOneId = Math.min(currentUserId, targetUserId);
        // 较大 id 放后面。
        long userTwoId = Math.max(currentUserId, targetUserId);
        // 最终格式类似 12:35。
        return userOneId + ":" + userTwoId;
    }

    private boolean isFriend(Long currentUserId, Long targetUserId) {
        // 聊天权限完全依赖好友表中“我 -> 对方”的 accepted 记录是否存在。
        return socialFriendMapper.selectCount(new LambdaQueryWrapper<SocialFriend>()
                .eq(SocialFriend::getUserId, currentUserId)
                .eq(SocialFriend::getFriendUserId, targetUserId)
                .eq(SocialFriend::getStatus, FRIEND_ACCEPTED)) > 0;
    }

    private void saveFriendRequest(Long userId, Long friendUserId, String remark) {
        SocialFriend relation = findRelation(userId, friendUserId);
        if (relation == null) {
            relation = new SocialFriend();
            relation.setUserId(userId);
            relation.setFriendUserId(friendUserId);
            relation.setCreateTime(LocalDateTime.now());
            relation.setStatus(FRIEND_PENDING);
            relation.setRemark(normalizeRemark(remark));
            relation.setHandleTime(null);
            socialFriendMapper.insert(relation);
            return;
        }
        relation.setStatus(FRIEND_PENDING);
        relation.setRemark(normalizeRemark(remark));
        relation.setCreateTime(LocalDateTime.now());
        relation.setHandleTime(null);
        socialFriendMapper.updateById(relation);
    }

    private void saveFriendPair(Long userId, Long friendUserId, String remark) {
        SocialFriend relation = findRelation(userId, friendUserId);
        if (relation == null) {
            relation = new SocialFriend();
            relation.setUserId(userId);
            relation.setFriendUserId(friendUserId);
            relation.setCreateTime(LocalDateTime.now());
            relation.setRemark(normalizeRemark(remark));
            relation.setStatus(FRIEND_ACCEPTED);
            relation.setHandleTime(LocalDateTime.now());
            socialFriendMapper.insert(relation);
            return;
        }
        relation.setStatus(FRIEND_ACCEPTED);
        relation.setHandleTime(LocalDateTime.now());
        if (StringUtils.hasText(remark)) {
            relation.setRemark(normalizeRemark(remark));
        }
        socialFriendMapper.updateById(relation);
    }

    private Map<String, Object> buildRelationSnapshot(Long currentUserId, Long targetUserId) {
        // 单目标版本本质上复用批量版本，便于复用同一套关系判断逻辑。
        return buildRelationSnapshots(currentUserId, Set.of(targetUserId)).getOrDefault(targetUserId, defaultRelationSnapshot());
    }

    private Map<Long, Map<String, Object>> buildRelationSnapshots(Long currentUserId, Set<Long> targetUserIds) {
        Set<Long> validTargetIds = targetUserIds == null
                ? Set.of()
                : targetUserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validTargetIds.isEmpty()) {
            return Map.of();
        }
        // outgoing: 我 -> 对方；incoming: 对方 -> 我。
        List<SocialFriend> outgoingRelations = socialFriendMapper.selectList(new LambdaQueryWrapper<SocialFriend>()
                .eq(SocialFriend::getUserId, currentUserId)
                .in(SocialFriend::getFriendUserId, validTargetIds));
        List<SocialFriend> incomingRelations = socialFriendMapper.selectList(new LambdaQueryWrapper<SocialFriend>()
                .eq(SocialFriend::getFriendUserId, currentUserId)
                .in(SocialFriend::getUserId, validTargetIds));
        Map<Long, SocialFriend> outgoingMap = outgoingRelations.stream()
                .collect(Collectors.toMap(SocialFriend::getFriendUserId, item -> item, (left, right) -> left));
        Map<Long, SocialFriend> incomingMap = incomingRelations.stream()
                .collect(Collectors.toMap(SocialFriend::getUserId, item -> item, (left, right) -> left));
        Map<Long, Map<String, Object>> result = new HashMap<>();
        validTargetIds.forEach(targetUserId -> {
            SocialFriend outgoing = outgoingMap.get(targetUserId);
            SocialFriend incoming = incomingMap.get(targetUserId);
            // relationStatus 是给前端做逻辑判断的机器态；relationText 是直接展示的人类文案。
            String relationStatus = "none";
            String relationText = "可发送申请";
            Long pendingRequestId = null;
            if (outgoing != null && Objects.equals(outgoing.getStatus(), FRIEND_ACCEPTED)) {
                relationStatus = "friend";
                relationText = "已是好友";
            } else if (outgoing != null && Objects.equals(outgoing.getStatus(), FRIEND_PENDING)) {
                relationStatus = "pendingSent";
                relationText = "已申请待回复";
            } else if (incoming != null && Objects.equals(incoming.getStatus(), FRIEND_PENDING)) {
                relationStatus = "pendingReceived";
                relationText = "待处理对方申请";
                pendingRequestId = incoming.getId();
            }
            Map<String, Object> relation = new LinkedHashMap<>();
            relation.put("relationStatus", relationStatus);
            relation.put("relationText", relationText);
            relation.put("isFriend", "friend".equals(relationStatus));
            relation.put("pendingRequestId", pendingRequestId);
            result.put(targetUserId, relation);
        });
        return result;
    }

    private Map<String, Object> defaultRelationSnapshot() {
        // 默认关系态表示双方没有任何申请/好友关系。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relationStatus", "none");
        result.put("relationText", "可发送申请");
        result.put("isFriend", false);
        result.put("pendingRequestId", null);
        return result;
    }

    private SocialFriend findRelation(Long userId, Long friendUserId) {
        // 查单向关系记录，好友关系本身在表里是双向两条数据。
        return socialFriendMapper.selectOne(new LambdaQueryWrapper<SocialFriend>()
                .eq(SocialFriend::getUserId, userId)
                .eq(SocialFriend::getFriendUserId, friendUserId)
                .last("limit 1"));
    }

    private Object getPostLikeLock(Long postId) {
        // 每条动态一个独立锁对象，降低全局锁粒度。
        // 同一条动态的点赞串行化，不同动态之间互不影响。
        return postLikeLocks.computeIfAbsent(postId, key -> new Object());
    }

    private String normalizeRemark(String remark) {
        // 好友附言为空时给一条默认文案。
        return StringUtils.hasText(remark) ? remark.trim() : "我想和你成为搭子";
    }

    private String normalizeIntro(String intro) {
        // 简介允许为空，但会统一 trim。
        return StringUtils.hasText(intro) ? intro.trim() : "";
    }

    private int safeInt(Integer value) {
        // 统一把 null 整数兜底成 0。
        return value == null ? 0 : value;
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Data
    public static class ProfileRequest {
        private String goal;
        private String intro;
        private String location;
    }

    @Data
    public static class PostRequest {
        private String postType;
        private String topic;
        private String content;
        private List<String> images;
    }

    @Data
    public static class CommentRequest {
        private Long parentId;
        private String content;
    }

    @Data
    public static class MessageRequest {
        private Long sessionId;
        private Long targetUserId;
        private String content;
    }

    @Data
    public static class FriendApplyRequest {
        private String remark;
    }
}
