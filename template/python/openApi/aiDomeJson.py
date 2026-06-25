import json
import csv
import math
import os
import re
import traceback
from collections import Counter, defaultdict
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from  openai import OpenAI
from starlette.responses import StreamingResponse

try:
    import redis as _redis_module
except ImportError:
    _redis_module = None

# 创建 FastAPI 应用实例。
# 这个文件同时承担两类职责：
# 1. `/getPlan` 提供 AI 健身计划生成接口；
# 2. `/data/*` 提供训练/饮食/推荐大屏所需的数据分析接口。
app = FastAPI()
# 允许前端跨域访问当前 Python 服务。
# 这里直接放开所有来源，方便本地开发时从不同端口的前端页面调用。
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------- 多AI供应商配置与故障切换 ----------

PROVIDERS_CONFIG = [
    {
        "name": "dashscope",
        "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "api_key_env": "DASHSCOPE_API_KEY",
        "model": "qwen3-max",
        "priority": 1,
    },
    {
        "name": "deepseek",
        "base_url": "https://api.deepseek.com/v1",
        "api_key_env": "DEEPSEEK_API_KEY",
        "model": "deepseek-chat",
        "priority": 2,
    },
    {
        "name": "openai",
        "base_url": "https://api.openai.com/v1",
        "api_key_env": "OPENAI_API_KEY",
        "model": "gpt-4o",
        "priority": 3,
    },
    {
        "name": "zhipu",
        "base_url": "https://open.bigmodel.cn/api/paas/v4",
        "api_key_env": "ZHIPU_API_KEY",
        "model": "glm-4",
        "priority": 4,
    },
]


def _build_active_providers():
    active = []
    for cfg in PROVIDERS_CONFIG:
        api_key = os.getenv(cfg["api_key_env"], "").strip()
        if not api_key:
            continue
        active.append({
            "name": cfg["name"],
            "base_url": cfg["base_url"],
            "api_key": api_key,
            "model": cfg["model"],
            "priority": cfg["priority"],
        })
    active.sort(key=lambda p: p["priority"])
    if not active:
        api_key = os.getenv("DASHSCOPE_API_KEY", "").strip()
        if api_key:
            active.append({
                "name": "dashscope",
                "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "api_key": api_key,
                "model": "qwen3-max",
                "priority": 1,
            })
    return active


def _try_create_stream(provider, messages, question):
    client = OpenAI(
        base_url=provider["base_url"],
        api_key=provider["api_key"],
        timeout=60.0,
    )
    return client.chat.completions.create(
        model=provider["model"],
        messages=messages + [{"role": "user", "content": f"按照上述的示例，回答问题：{question}"}],
        stream=True,
    )


@app.on_event("startup")
def _startup_warm_cache():
    try:
        result = compute_gym_plan_metrics()
        if _redis_conn is not None:
            try:
                _redis_conn.setex(_REDIS_GYM_KEY, _REDIS_CACHE_TTL, json.dumps(result, ensure_ascii=False))
            except Exception:
                pass
        try:
            with open(_GYM_DISK_CACHE_FILE, "w", encoding="utf-8") as f:
                json.dump(result, f, ensure_ascii=False)
        except Exception:
            pass
    except Exception:
        pass

# 定义 `/getPlan` 接口接收的请求体结构。
# 这里全部使用字符串，是为了和 Java 后端转发过来的 JSON 字段保持一致，
# 避免因为数字/空值类型差异导致解析失败。
class FitnessRequest(BaseModel):
    height: str
    weight: str
    sex: str
    die_of_illness: str
    goal: str

# 生成 AI 健身计划的接口。
# 支持多AI供应商自动降级：按优先级依次尝试，任一成功即返回流式结果。
@app.post("/getPlan")
def get_plan(data: FitnessRequest):

    answer_template = {
        "content": "身高：177cm，体重：83kg,目标是减脂",
        "dietMotionPlan": {
            "oneDay": {
                "diet": {
                    "morning": "燕麦50g + 蛋白3个 + 全蛋1个 + 蓝莓50g + 无糖杏仁奶200ml",
                    "noon": "鸡胸肉150g + 糙米饭100g（熟重）+ 西兰花200g + 橄榄油5g",
                    "late": "三文鱼120g + 菠菜沙拉（生菠菜150g + 小番茄5颗 + 黄瓜半根 + 油醋汁5ml）"
                },
                "Motion": {
                    "motionContent": "力量训练（上肢推：胸、肩、三头）",
                    "action": [
                        "平板杠铃卧推 4组×8-10次",
                        "哑铃肩推 4组×10次",
                        "上斜哑铃卧推 3组×10-12次",
                        "绳索下压 3组×12-15次",
                        "侧平举 3组×15次"
                    ],
                    "aerobic": "空腹低强度快走30分钟（可选）",
                    "duration": "60-70分钟"
                }
            },
            "towDay": {
                "diet": {
                    "morning": "全麦吐司2片 + 牛油果1/4个 + 水煮蛋2个 + 黑咖啡",
                    "noon": "瘦牛肉150g + 红薯150g（熟重）+ 混合蔬菜（生菜、彩椒、洋葱）200g + 橄榄油5g",
                    "late": "虾仁120g + 芦笋200g + 藜麦50g（干重）"
                },
                "Motion": {
                    "motionContent": "力量训练（下肢：股四头肌、腘绳肌、臀）+ 有氧",
                    "action": [
                        "杠铃深蹲 4组×8-10次",
                        "罗马尼亚硬拉 4组×10次",
                        "保加利亚分腿蹲 3组×10次/腿",
                        "腿弯举 3组×12-15次",
                        "提踵 4组×15-20次"
                    ],
                    "aerobic": "高强度间歇训练（HIIT）20分钟（如：30秒冲刺+60秒慢走，重复8轮）",
                    "duration": "70-80分钟"
                }
            },
            "threeDay": {
                "diet": {
                    "morning": "希腊酸奶150g（无糖）+ 奇亚籽10g + 草莓50g + 蛋白粉1勺（加水）",
                    "noon": "鸡腿肉（去皮）150g + 荞麦面60g（干重）+ 青菜200g + 蒜蓉橄榄油5g",
                    "late": "豆腐150g + 海带汤 + 紫甘蓝沙拉100g + 杏仁10颗"
                },
                "Motion": {
                    "motionContent": "主动恢复日",
                    "action": ["快走或骑行40分钟 + 泡沫轴放松 + 拉伸"],
                    "aerobic": "",
                    "duration": "45-50分钟"
                }
            },
            "fourDay": {
                "diet": {
                    "morning": "燕麦40g + 蛋白3个 + 核桃10g + 苹果半个",
                    "noon": "金枪鱼罐头（水浸）120g + 全麦意面60g（干重）+ 西葫芦炒蘑菇150g + 橄榄油5g",
                    "late": "鸡胸肉120g + 烤彩椒+西兰花200g + 南瓜100g（熟重）"
                },
                "Motion": {
                    "motionContent": "力量训练（上肢拉：背、二头）",
                    "action": [
                        "引体向上（辅助）4组×力竭",
                        "杠铃划船 4组×8-10次",
                        "高位下拉 3组×10-12次",
                        "坐姿划船 3组×12次",
                        "哑铃弯举 3组×12-15次"
                    ],
                    "aerobic": "空腹低强度快走30分钟（可选）",
                    "duration": "60-70分钟"
                }
            },
            "fiveDay": {
                "diet": {
                    "morning": "全麦贝果1个 + 花生酱10g + 水煮蛋2个 + 黑咖啡",
                    "noon": "瘦猪肉150g + 糙米饭100g（熟重）+ 芥蓝200g + 蒜蓉橄榄油5g",
                    "late": "鳕鱼120g + 芽菜沙拉150g + 小番茄+黄瓜 + 柠檬橄榄油汁"
                },
                "Motion": {
                    "motionContent": "全身功能性训练 + 有氧",
                    "action": [
                        "壶铃摇摆 4组×20次",
                        "战绳 3组×30秒",
                        "跳箱 3组×10次",
                        "农夫行走 3组×30米",
                        "平板支撑 3组×45秒"
                    ],
                    "aerobic": "中等强度稳态有氧（如跑步机快走/慢跑）30分钟",
                    "duration": "60分钟"
                }
            },
            "sixDay": {
                "diet": {
                    "morning": "蛋白煎饼（蛋白3个+燕麦粉30g+香蕉半根）+ 无糖豆浆200ml",
                    "noon": "烤鸡胸150g + 藜麦60g（干重）+ 烤茄子+洋葱200g + 橄榄油5g",
                    "late": "虾仁100g + 冬瓜海带汤 + 凉拌黄瓜150g"
                },
                "Motion": {
                    "motionContent": "核心 + 有氧耐力",
                    "action": [
                        "悬垂举腿 4组×12次",
                        "健腹轮 3组×10次",
                        "侧平板支撑 每侧3组×30秒",
                        "登山跑 3组×30秒"
                    ],
                    "aerobic": "户外跑步或椭圆机40分钟（心率维持在最大心率60-70%）",
                    "duration": "60分钟"
                }
            },
            "seven": {
                "diet": {
                    "morning": "水煮蛋2个 + 全麦吐司1片 + 牛油果1/4个 + 黑咖啡",
                    "noon": "三文鱼150g + 红薯150g（熟重）+ 西兰花+胡萝卜200g + 橄榄油5g",
                    "late": "豆腐120g + 菠菜150g + 紫菜蛋花汤（少油）"
                },
                "Motion": {
                    "motionContent": "完全休息或轻度活动",
                    "action": ["散步、瑜伽、拉伸"],
                    "aerobic": "",
                    "duration": "0-30分钟（自愿）"
                }
            }
        },
    }

    messages = [
        {"role": "system", "content": "你是一个经验丰富的健身教练，精通各种健身与饮食搭配方案，会根据用户提供的信息制定个性化方案。只输出一个合法的 JSON 对象，不要输出解释文字，不要输出 markdown 代码块，不要把 JSON 再包成字符串。"},
        {"role": "assistant", "content": "告诉我你的身体指标，为你量身定制方案"},
    ]
    question = "身高：" + data.height + "cm，体重：" + data.weight + "kg,性别：" + data.sex + "既往病史" + data.die_of_illness + "，目标是" + data.goal

    messages.append({"role": "user", "content": answer_template["content"]})
    messages.append({"role": "assistant", "content": json.dumps(answer_template["dietMotionPlan"], ensure_ascii=False)})

    providers = _build_active_providers()
    errors = []

    for provider in providers:
        try:
            response = _try_create_stream(provider, messages, question)

            def generate():
                for chunk in response:
                    if chunk.choices and chunk.choices[0].delta.content:
                        yield chunk.choices[0].delta.content

            return StreamingResponse(generate(), media_type="text/event-stream")
        except Exception as e:
            errors.append(f"{provider['name']}: {e}")
            traceback.print_exc()
            continue

    raise HTTPException(
        status_code=503,
        detail=f"所有AI服务暂不可用: {'; '.join(errors[-3:])}"
    )


# 数据目录：训练、饮食、推荐分析所需的 CSV 都放在 `template/python/data` 下。
DATA_DIR = Path(__file__).resolve().parent.parent / "data"

# 以下映射表负责把英文原始字段值翻译成中文，
# 便于前端图表和页面直接展示。
GENDER_MAP = {
    "Female": "女",
    "Male": "男",
    "Unknown": "未知",
}

WORKOUT_MAP = {
    "Strength": "力量训练",
    "Cardio": "有氧训练",
    "HIIT": "高强度间歇训练",
    "Yoga": "瑜伽",
    "Pilates": "普拉提",
    "Unknown": "未知",
}

FOOD_TYPE_MAP = {
    "breakfast": "早餐",
    "lunch": "午餐",
    "dinner": "晚餐",
    "snacks": "加餐",
  "side dish": "配菜",
  "soup": "汤品",
  "dessert": "甜点",
    "unknown": "未知",
}

REGION_MAP = {
    "North India": "北印度",
    "South India": "南印度",
    "East India": "东印度",
    "West India": "西印度",
    "Central India": "中印度",
    "Pan India": "全印度",
  "Pan-India": "全印度",
  "Gujarat": "古吉拉特邦",
  "West Bengal": "西孟加拉邦",
  "Maharashtra": "马哈拉施特拉邦",
  "Rajasthan": "拉贾斯坦邦",
    "unknown": "未知",
}

ALLERGY_MAP = {
    "Milk": "牛奶",
    "Gluten": "麸质",
  "Legumes": "豆类",
  "Meat": "肉类",
  "Dairy": "乳制品",
  "Shrimp": "虾",
  "Beef": "牛肉",
  "Seafood": "海鲜",
  "Chicken": "鸡肉",
  "Mutton": "羊肉",
  "Lobster": "龙虾",
    "Egg": "鸡蛋",
    "Eggs": "鸡蛋",
    "Peanut": "花生",
    "Peanuts": "花生",
    "Nuts": "坚果",
    "Soy": "大豆",
    "Fish": "鱼类",
    "Shellfish": "贝类",
    "Sesame": "芝麻",
}

GOAL_MAP = {
    "muscle_gain": "增肌",
    "fat_burn": "减脂",
    "weight_loss": "减重",
    "endurance": "提升耐力",
    "strength": "提升力量",
    "Unknown": "未知",
}

BMI_CAT_MAP = {
    "Underweight": "偏瘦",
    "Normal weight": "正常",
    "Overweight": "超重",
    "Obesity": "肥胖",
    "Unknown": "未知",
}

ENERGY_CAT_MAP = {
    "Low": "低能量",
    "Medium": "中能量",
    "Moderate": "中能量",
    "High": "高能量",
    "unknown": "未知",
}

EX_KW_MAP = {
    "strength": "力量",
    "cardio": "有氧",
    "hiit": "高强度间歇训练",
    "yoga": "瑜伽",
    "swim": "游泳",
    "running": "跑步",
    "walk": "步行",
    "cycling": "骑行",
    "pilates": "普拉提",
}

MEAL_KW_MAP = {
    "high protein": "高蛋白",
    "low carb": "低碳",
    "balanced": "均衡",
    "low calorie": "低热量",
    "keto": "生酮",
    "vegetarian": "素食",
    "vegan": "纯素",
}


def _clean_str(v):
    # 把任意输入值规整成“干净字符串”：
    # 1. None -> 空字符串；
    # 2. 去掉 tab / 转义换行；
    # 3. 压缩连续空白。
    if v is None:
        return ""
    s = str(v)
    s = s.replace("\t", " ")
    s = s.replace("\\t", " ")
    s = s.replace("\\n", " ")
    s = s.replace("\\r", " ")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def _to_float(v):
    # 在清洗字符串后尽量转成浮点数；
    # 无法转换时统一返回 None，而不是抛异常。
    s = _clean_str(v)
    if s == "":
        return None
    try:
        return float(s)
    except Exception:
        return None


def _read_rows(path):
    # 逐行读取 CSV，并把每一列的 key/value 都做字符串清洗。
    # `utf-8-sig` 用于兼容带 BOM 头的 CSV 文件。
    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            yield {(_clean_str(k)): _clean_str(v) for k, v in row.items()}


def _mean(values):
    # 计算均值前先过滤 None 和 NaN。
    xs = [x for x in values if x is not None and not math.isnan(x)]
    if not xs:
        return None
    return sum(xs) / len(xs)


def _topn(counter, n=10):
    # 把 Counter 转成前端更容易消费的 [{name, value}] 结构。
    return [{"name": k, "value": v} for k, v in counter.most_common(n)]


def _hist(values, bins=10):
    # 计算简单直方图：
    # bins 返回每个桶的起始标签；
    # counts 返回每个桶中的样本数量。
    xs = [x for x in values if x is not None and not math.isnan(x)]
    if not xs:
        return {"bins": [], "counts": []}
    mn = min(xs)
    mx = max(xs)
    if mn == mx:
        return {"bins": [mn, mx], "counts": [len(xs)]}
    step = (mx - mn) / bins
    edges = [mn + i * step for i in range(bins + 1)]
    counts = [0] * bins
    for x in xs:
        idx = int((x - mn) / step)
        if idx == bins:
            idx = bins - 1
        counts[idx] += 1
    labels = [round(edges[i], 2) for i in range(bins)]
    return {"bins": labels, "counts": counts}

def _safe_div(a, b):
    # 安全除法，避免除以 0 或空值。
    if a is None or b in (None, 0):
        return None
    return a / b

def _percentile(values, q):
    # 计算分位数。
    # q 通常取 0.25 / 0.5 / 0.75，用于箱线图。
    xs = sorted([x for x in values if x is not None and not math.isnan(x)])
    if not xs:
        return None
    if len(xs) == 1:
        return xs[0]
    pos = (len(xs) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return xs[int(pos)]
    return xs[lo] * (hi - pos) + xs[hi] * (pos - lo)

def _box_stats(values):
    # 输出箱线图所需的五数概括：
    # [最小值, Q1, 中位数, Q3, 最大值]
    xs = sorted([x for x in values if x is not None and not math.isnan(x)])
    if len(xs) < 5:
        return None
    return [
        round(xs[0], 2),
        round(_percentile(xs, 0.25), 2),
        round(_percentile(xs, 0.5), 2),
        round(_percentile(xs, 0.75), 2),
        round(xs[-1], 2),
    ]

def _pearson(xs, ys):
    # 计算皮尔逊相关系数，用于连续变量线性相关分析。
    pairs = [(x, y) for x, y in zip(xs, ys) if x is not None and y is not None]
    if len(pairs) < 3:
        return None
    x_vals = [p[0] for p in pairs]
    y_vals = [p[1] for p in pairs]
    mx = _mean(x_vals)
    my = _mean(y_vals)
    if mx is None or my is None:
        return None
    num = sum((x - mx) * (y - my) for x, y in pairs)
    den_x = math.sqrt(sum((x - mx) ** 2 for x in x_vals))
    den_y = math.sqrt(sum((y - my) ** 2 for y in y_vals))
    if den_x == 0 or den_y == 0:
        return None
    return num / (den_x * den_y)

def _rank(values):
    # 把一组数值转成秩次。
    # 相同值使用平均秩，供 Spearman 相关计算复用。
    indexed = sorted([(v, i) for i, v in enumerate(values)], key=lambda x: x[0])
    ranks = [0] * len(values)
    i = 0
    while i < len(indexed):
        j = i
        while j + 1 < len(indexed) and indexed[j + 1][0] == indexed[i][0]:
            j += 1
        avg_rank = (i + j + 2) / 2
        for k in range(i, j + 1):
            ranks[indexed[k][1]] = avg_rank
        i = j + 1
    return ranks

def _spearman(xs, ys):
    # Spearman 相关本质上是“先转秩，再算 Pearson”。
    pairs = [(x, y) for x, y in zip(xs, ys) if x is not None and y is not None]
    if len(pairs) < 3:
        return None
    xr = _rank([p[0] for p in pairs])
    yr = _rank([p[1] for p in pairs])
    return _pearson(xr, yr)

def _regression_line(points):
    # 基于散点计算一条简单线性回归趋势线，
    # 供前端在散点图上叠加显示。
    pairs = [(p["duration"], p["calories"]) for p in points if p.get("duration") is not None and p.get("calories") is not None]
    if len(pairs) < 3:
        return []
    xs = [p[0] for p in pairs]
    ys = [p[1] for p in pairs]
    mx = _mean(xs)
    my = _mean(ys)
    den = sum((x - mx) ** 2 for x in xs)
    if den == 0:
        return []
    slope = sum((x - mx) * (y - my) for x, y in pairs) / den
    intercept = my - slope * mx
    x0 = min(xs)
    x1 = max(xs)
    return [
        [round(x0, 2), round(slope * x0 + intercept, 2)],
        [round(x1, 2), round(slope * x1 + intercept, 2)],
    ]

def _bmi_category_from_value(bmi):
    # 根据 BMI 数值换算中文分类。
    if bmi is None:
        return "未知"
    if bmi < 18.5:
        return "偏瘦"
    if bmi < 25:
        return "正常"
    if bmi < 30:
        return "超重"
    return "肥胖"

def _age_group(age):
    # 把年龄划分到前端图表使用的年龄区间中。
    if age is None:
        return "未知"
    if age <= 30:
        return "18-30岁"
    if age <= 50:
        return "31-50岁"
    if age <= 60:
        return "51-60岁"
    return "60岁以上"

def _zh(v, mapping, default="未知"):
    # 通用翻译函数：
    # 1. 先清洗字符串；
    # 2. 先按原值查；
    # 3. 再按小写查；
    # 4. 实在没有就返回原文。
    s = _clean_str(v)
    if s == "":
        return default
    if s in mapping:
        return mapping[s]
    low = s.lower()
    if low in mapping:
        return mapping[low]
    return s

def _zh_allergy(v):
    # 过敏原字段比较脏，先按关键词粗匹配，再回退到通用映射表。
    s = _clean_str(v)
    if s == "":
        return ""
    low = s.lower()
    if "gluten" in low:
        return "麸质"
    if "milk" in low:
        return "牛奶"
    if "dairy" in low:
        return "乳制品"
    if "shellfish" in low:
        return "贝类"
    if "shrimp" in low:
        return "虾"
    if "lobster" in low:
        return "龙虾"
    if "seafood" in low:
        return "海鲜"
    if "beef" in low:
        return "牛肉"
    if "chicken" in low:
        return "鸡肉"
    if "mutton" in low:
        return "羊肉"
    if "fish" in low:
        return "鱼类"
    return _zh(s, ALLERGY_MAP, s)

def _load_fitness_rows():
    # 加载训练数据 CSV，并清洗成后续分析统一使用的行对象。
    path = DATA_DIR / "gym_members_exercise_tracking_synthetic_data.csv"
    rows = []
    # seen 用于去重，避免重复样本影响统计结果。
    seen = set()
    for raw in _read_rows(path):
        key = tuple(sorted(raw.items()))
        if key in seen:
            continue
        seen.add(key)
        # 先提取核心数值字段。
        weight = _to_float(raw.get("Weight (kg)"))
        height = _to_float(raw.get("Height (m)"))
        bmi = _to_float(raw.get("BMI"))
        # 如果原始 BMI 缺失，就根据体重和身高临时计算。
        if bmi is None and weight is not None and height not in (None, 0):
            bmi = round(weight / (height * height), 2)
        fat = _to_float(raw.get("Fat_Percentage"))
        age = _to_float(raw.get("Age"))
        # 缺少体重/身高等关键字段的样本直接丢弃。
        if weight is None or weight <= 0 or height is None or height <= 0:
            continue
        # 过滤明显异常的 BMI 和体脂率。
        if bmi is None or bmi < 10 or bmi > 60:
            continue
        if fat is not None and (fat < 0 or fat > 60):
            continue
        duration = _to_float(raw.get("Session_Duration (hours)"))
        calories = _to_float(raw.get("Calories_Burned"))
        if calories is None:
            continue
        max_bpm = _to_float(raw.get("Max_BPM"))
        resting_bpm = _to_float(raw.get("Resting_BPM"))
        avg_bpm = _to_float(raw.get("Avg_BPM"))
        workout_type = _zh(raw.get("Workout_Type", "") or "Unknown", WORKOUT_MAP, "未知")
        # 一个简单的异常样本定义：极短时长却极高热量消耗。
        anomaly = bool(calories is not None and calories > 2500 and duration is not None and duration < 0.5)
        # 把原始字段转成后续分析统一结构。
        rows.append({
            "age": age,
            "age_group": _age_group(age),
            "gender": _zh(raw.get("Gender", "") or "Unknown", GENDER_MAP, "未知"),
            "weight": weight,
            "height": height,
            "max_bpm": max_bpm,
            "avg_bpm": avg_bpm,
            "resting_bpm": resting_bpm,
            "duration": duration,
            "calories": calories,
            "workout_type": workout_type,
            "fat_percentage": fat,
            "water": _to_float(raw.get("Water_Intake (liters)")),
            "frequency": _to_float(raw.get("Workout_Frequency (days/week)")),
            "experience": f"Lv{int(float(raw.get('Experience_Level')))}" if _to_float(raw.get("Experience_Level")) is not None else "未知",
            "bmi": bmi,
            "bmi_category": _bmi_category_from_value(bmi),
            "heart_rate_recovery": (max_bpm - resting_bpm) if max_bpm is not None and resting_bpm is not None else None,
            "calories_per_hour": _safe_div(calories, duration),
            "anomaly": anomaly,
            "weight_group": "轻体重" if weight < 60 else ("中体重" if weight < 80 else "大体重"),
            # “瘦胖子”定义：BMI 偏低但体脂偏高。
            "skinny_fat": bool(bmi < 18.5 and fat is not None and fat > 30),
        })
    return rows

def _load_diet_rows():
    # 加载饮食数据 CSV，并清洗成统一结构。
    path = DATA_DIR / "InDiet_Dataset.csv"
    rows = []
    seen = set()
    for raw in _read_rows(path):
        key = tuple(sorted(raw.items()))
        if key in seen:
            continue
        seen.add(key)
        # 宏量营养三要素。
        carb = _to_float(raw.get("carb_g"))
        protein = _to_float(raw.get("protein_g"))
        fat = _to_float(raw.get("fat_g"))
        # 宏量营养完全没有意义的数据直接跳过。
        if all((v is None or v == 0) for v in [carb, protein, fat]):
            continue
        energy = _to_float(raw.get("energy_kcal"))
        if energy is None or energy <= 0:
            continue
        rows.append({
            "food_name": _zh_food(raw.get("food_name", "") or raw.get("food_code", "")),
            # 食物分组先翻译成中文，方便前端直接显示。
            "food_group": {
                "Vegetarian": "素食",
                "Non-Vegetarian": "荤食",
                "Vegan": "纯素",
            }.get(raw.get("food_group_nin", ""), raw.get("food_group_nin", "") or "未知"),
            "energy": energy,
            "carb": carb or 0,
            "protein": protein or 0,
            "fat": fat or 0,
            "free_sugar": _to_float(raw.get("freesugar_g")),
            "fibre": _to_float(raw.get("fibre_g")),
            "cholesterol": _to_float(raw.get("cholesterol_mg")),
            "allergies": raw.get("allergies", ""),
            "region": _zh(raw.get("region", "") or "unknown", REGION_MAP, "未知"),
            "food_type": _zh(raw.get("food_type", "") or "unknown", FOOD_TYPE_MAP, "未知"),
            "protein_calorie_ratio": _to_float(raw.get("protein_calorie_ratio")),
            "nutrient_score": _to_float(raw.get("nutrient_score")),
            "health_score": _to_float(raw.get("health_score")),
            "diversity_score": _to_float(raw.get("diversity_score")),
            "energy_category": _zh(raw.get("energy_category", "") or "unknown", ENERGY_CAT_MAP, "未知"),
        })
    for row in rows:
        # 根据三大营养素换算供能占比。
        total_macro_kcal = row["carb"] * 4 + row["protein"] * 4 + row["fat"] * 9
        row["carb_ratio"] = round((_safe_div(row["carb"] * 4, total_macro_kcal) or 0) * 100, 2)
        row["protein_ratio"] = round((_safe_div(row["protein"] * 4, total_macro_kcal) or 0) * 100, 2)
        row["fat_ratio"] = round((_safe_div(row["fat"] * 9, total_macro_kcal) or 0) * 100, 2)
        hs = row["health_score"]
        # 顺手把健康评分离散成高/中/低三档。
        if hs is None:
            row["health_level"] = "未知"
        elif hs >= 60:
            row["health_level"] = "高健康"
        elif hs >= 40:
            row["health_level"] = "中健康"
        else:
            row["health_level"] = "低健康"
    return rows

def _meal_plan_profile(text):
    # 从推荐饮食描述文本里粗略推断出饮食策略和目标供能比例。
    low = (text or "").lower()
    profile = {
        "strategy": "均衡",
        "protein_target": "18%-28%",
        "carb_target": "40%-50%",
        "fat_target": "20%-30%",
    }
    if "high protein" in low:
        profile.update({"strategy": "高蛋白", "protein_target": "28%-40%", "carb_target": "30%-40%", "fat_target": "20%-30%"})
    if "low carb" in low:
        profile.update({"strategy": "低碳", "protein_target": "25%-35%", "carb_target": "20%-30%", "fat_target": "30%-40%"})
    if "low calorie" in low:
        profile.update({"strategy": "低热量", "protein_target": "25%-35%", "carb_target": "30%-40%", "fat_target": "20%-30%"})
    if "keto" in low:
        profile.update({"strategy": "生酮", "protein_target": "20%-30%", "carb_target": "5%-10%", "fat_target": "60%-70%"})
    if "vegetarian" in low:
        profile["strategy"] = "素食"
    if "vegan" in low:
        profile["strategy"] = "纯素"
    return profile

def _parse_schedule(text):
    # 从训练计划文本里粗略识别力量/有氧/灵活性三类训练是否出现。
    low = (text or "").lower()
    strength = 1 if "strength" in low else 0
    cardio = 1 if any(k in low for k in ["cardio", "hiit", "running", "cycling", "swim"]) else 0
    mobility = 1 if any(k in low for k in ["yoga", "pilates", "walk", "walking", "steps"]) else 0
    return {
        "strength": strength,
        "cardio": cardio,
        "mobility": mobility,
        "total_units": strength + cardio + mobility,
    }

def compute_fitness_metrics():
    # 训练大屏的主计算函数。
    # 返回值会被 `/data/fitness` 直接输出给前端。
    rows = _load_fitness_rows()
    # 下面这些容器按不同分析维度逐步累积数据。
    workout_counts = Counter()
    workout_metrics = defaultdict(lambda: {"fat": [], "bmi": [], "calories": [], "avg_bpm": []})
    frequency_impact = defaultdict(lambda: {"fat": [], "bmi": [], "calories": []})
    scatter_points = []
    bmi_duration_points = []
    corr_fields = {
        "体脂率": [],
        "BMI": [],
        "平均心率": [],
        "静息心率": [],
        "训练时长": [],
        "热量消耗": [],
        "饮水量": [],
    }
    bmi_workout = defaultdict(lambda: defaultdict(list))
    experience_metrics = defaultdict(lambda: {"duration": [], "calories": [], "avg_bpm": [], "frequency": [], "water": []})
    bmi_performance = defaultdict(lambda: {"duration": [], "avg_bpm": []})
    bmi_duration_box = defaultdict(list)
    age_recovery = defaultdict(list)
    recovery_by_resting_group = defaultdict(list)
    water_by_workout = defaultdict(list)
    water_corr_points = []
    water_bpm_points = []
    pattern_groups = {"高频低强": [], "低频高强": []}
    heart_calories_groups = defaultdict(lambda: {"bpm": [], "calories": []})
    anomaly_count = 0
    skinny_fat_points = []
    calories_values = []
    bmi_values = []
    fat_values = []
    water_values = []
    duration_values = []
    recovery_values = []

    for row in rows:
        # 先累积最基础的异常样本与训练类型统计。
        if row["anomaly"]:
            anomaly_count += 1
        wt = row["workout_type"]
        workout_counts[wt] += 1
        workout_metrics[wt]["fat"].append(row["fat_percentage"])
        workout_metrics[wt]["bmi"].append(row["bmi"])
        workout_metrics[wt]["calories"].append(row["calories"])
        workout_metrics[wt]["avg_bpm"].append(row["avg_bpm"])

        freq_key = str(int(row["frequency"])) if row["frequency"] is not None else "未知"
        frequency_impact[freq_key]["fat"].append(row["fat_percentage"])
        frequency_impact[freq_key]["bmi"].append(row["bmi"])
        frequency_impact[freq_key]["calories"].append(row["calories"])

        # 散点图样本做上限限制，避免点数过多影响前端渲染性能。
        if row["duration"] is not None and row["calories"] is not None and len(scatter_points) < 420:
            scatter_points.append({"workout": wt, "duration": row["duration"], "calories": row["calories"]})
        if row["bmi"] is not None and row["duration"] is not None and len(bmi_duration_points) < 420:
            bmi_duration_points.append({"bmi": row["bmi"], "duration": row["duration"], "gender": row["gender"]})

        corr_fields["体脂率"].append(row["fat_percentage"])
        corr_fields["BMI"].append(row["bmi"])
        corr_fields["平均心率"].append(row["avg_bpm"])
        corr_fields["静息心率"].append(row["resting_bpm"])
        corr_fields["训练时长"].append(row["duration"])
        corr_fields["热量消耗"].append(row["calories"])
        corr_fields["饮水量"].append(row["water"])

        # 以下这些字典/数组分别服务后续不同图表：
        # BMI × 训练类型、经验等级雷达图、心率恢复、饮水量相关等。
        bmi_workout[row["bmi_category"]][wt].append(row["calories"])
        bmi_performance[row["bmi_category"]]["duration"].append(row["duration"])
        bmi_performance[row["bmi_category"]]["avg_bpm"].append(row["avg_bpm"])
        bmi_duration_box[row["bmi_category"]].append(row["duration"])

        exp = row["experience"]
        experience_metrics[exp]["duration"].append(row["duration"])
        experience_metrics[exp]["calories"].append(row["calories"])
        experience_metrics[exp]["avg_bpm"].append(row["avg_bpm"])
        experience_metrics[exp]["frequency"].append(row["frequency"])
        experience_metrics[exp]["water"].append(row["water"])

        if row["heart_rate_recovery"] is not None:
            age_recovery[row["age_group"]].append(row["heart_rate_recovery"])
            recovery_values.append(row["heart_rate_recovery"])
            if row["resting_bpm"] is not None:
                if row["resting_bpm"] < 60:
                    recovery_by_resting_group["低静息心率"].append(row["heart_rate_recovery"])
                elif row["resting_bpm"] < 75:
                    recovery_by_resting_group["中静息心率"].append(row["heart_rate_recovery"])
                else:
                    recovery_by_resting_group["高静息心率"].append(row["heart_rate_recovery"])

        if row["water"] is not None:
            water_by_workout[wt].append(row["water"])
            if row["calories"] is not None and len(water_corr_points) < 420:
                water_corr_points.append([row["water"], row["calories"]])
            if row["max_bpm"] is not None and len(water_bpm_points) < 420:
                water_bpm_points.append([row["water"], row["max_bpm"]])

        if row["avg_bpm"] is not None and row["calories"] is not None:
            heart_calories_groups[row["age_group"]]["bpm"].append(row["avg_bpm"])
            heart_calories_groups[row["age_group"]]["calories"].append(row["calories"])

        if row["frequency"] is not None and row["duration"] is not None and row["calories"] is not None:
            weekly_burn = row["frequency"] * row["calories"]
            if row["frequency"] >= 5 and row["duration"] < 1:
                pattern_groups["高频低强"].append(weekly_burn)
            if row["frequency"] <= 2 and row["duration"] > 1.5:
                pattern_groups["低频高强"].append(weekly_burn)

        if row["skinny_fat"] and len(skinny_fat_points) < 250:
            skinny_fat_points.append({
                "bmi": row["bmi"],
                "fat": row["fat_percentage"],
                "workout": row["workout_type"],
                "water": row["water"],
            })

        calories_values.append(row["calories"])
        bmi_values.append(row["bmi"])
        fat_values.append(row["fat_percentage"])
        water_values.append(row["water"])
        duration_values.append(row["duration"])

    # workout_body_metrics：不同训练类型下 BMI / 体脂率 / 热量 / 心率的平均值。
    workout_body_metrics = []
    for wt in sorted(workout_metrics.keys()):
        workout_body_metrics.append({
            "name": wt,
            "avg_fat": round(_mean(workout_metrics[wt]["fat"]) or 0, 2),
            "avg_bmi": round(_mean(workout_metrics[wt]["bmi"]) or 0, 2),
            "avg_calories": round(_mean(workout_metrics[wt]["calories"]) or 0, 2),
            "avg_bpm": round(_mean(workout_metrics[wt]["avg_bpm"]) or 0, 2),
        })

    # frequency_effect：每周训练频率对 BMI / 体脂率 / 热量的影响。
    frequency_effect = []
    for fk in sorted(frequency_impact.keys(), key=lambda x: (999 if x == "未知" else int(x))):
        frequency_effect.append({
            "name": fk,
            "avg_fat": round(_mean(frequency_impact[fk]["fat"]) or 0, 2),
            "avg_bmi": round(_mean(frequency_impact[fk]["bmi"]) or 0, 2),
            "avg_calories": round(_mean(frequency_impact[fk]["calories"]) or 0, 2),
        })

    # correlation_matrix：关键指标之间的皮尔逊相关矩阵。
    corr_names = list(corr_fields.keys())
    corr_data = []
    for xi, x_name in enumerate(corr_names):
        for yi, y_name in enumerate(corr_names):
            corr = _pearson(corr_fields[x_name], corr_fields[y_name])
            corr_data.append([xi, yi, round(corr or 0, 2)])

    # BMI 分类 × 训练类型 热量矩阵。
    bmi_workout_series = []
    workout_order = [x["name"] for x in workout_body_metrics]
    for bmi_cat in ["偏瘦", "正常", "超重", "肥胖", "未知"]:
        vals = []
        for wt in workout_order:
            vals.append(round(_mean(bmi_workout[bmi_cat][wt]) or 0, 2))
        if any(v > 0 for v in vals):
            bmi_workout_series.append({"name": bmi_cat, "data": vals})

    # 经验等级雷达图数据。
    experience_radar = []
    for exp in sorted(experience_metrics.keys()):
        experience_radar.append({
            "name": exp,
            "duration": round(_mean(experience_metrics[exp]["duration"]) or 0, 2),
            "calories": round(_mean(experience_metrics[exp]["calories"]) or 0, 2),
            "avg_bpm": round(_mean(experience_metrics[exp]["avg_bpm"]) or 0, 2),
            "frequency": round(_mean(experience_metrics[exp]["frequency"]) or 0, 2),
            "water": round(_mean(experience_metrics[exp]["water"]) or 0, 2),
        })

    # BMI 分组下的训练表现。
    bmi_performance_list = []
    for bmi_cat in ["偏瘦", "正常", "超重", "肥胖"]:
        bmi_performance_list.append({
            "name": bmi_cat,
            "avg_duration": round(_mean(bmi_performance[bmi_cat]["duration"]) or 0, 2),
            "avg_bpm": round(_mean(bmi_performance[bmi_cat]["avg_bpm"]) or 0, 2),
        })

    # BMI 分组下训练时长箱线图。
    bmi_duration_boxplot = []
    for bmi_cat in ["偏瘦", "正常", "超重", "肥胖"]:
        stats = _box_stats(bmi_duration_box[bmi_cat])
        if stats:
            bmi_duration_boxplot.append({"name": bmi_cat, "value": stats})

    # 年龄分组下心率恢复能力。
    age_recovery_list = []
    for age_group in ["18-30岁", "31-50岁", "51-60岁", "60岁以上"]:
        vals = age_recovery[age_group]
        if vals:
            age_recovery_list.append({"name": age_group, "value": round(_mean(vals) or 0, 2)})

    # 静息心率分组下的心率恢复箱线图。
    recovery_resting_box = []
    for key in ["低静息心率", "中静息心率", "高静息心率"]:
        stats = _box_stats(recovery_by_resting_group[key])
        if stats:
            recovery_resting_box.append({"name": key, "value": stats})

    # 年龄分组下平均心率与热量消耗的相关性。
    heart_calorie_corr = []
    for group in ["18-30岁", "31-50岁", "51-60岁", "60岁以上"]:
        corr = _pearson(heart_calories_groups[group]["bpm"], heart_calories_groups[group]["calories"])
        if corr is not None:
            heart_calorie_corr.append({"name": group, "value": round(corr, 3)})

    # 不同训练类型的平均饮水量及标准差。
    water_by_workout_list = []
    for wt in sorted(water_by_workout.keys()):
        vals = [v for v in water_by_workout[wt] if v is not None]
        mean_v = _mean(vals) or 0
        std_v = math.sqrt(sum((v - mean_v) ** 2 for v in vals) / len(vals)) if vals else 0
        water_by_workout_list.append({"name": wt, "value": round(mean_v, 2), "error": round(std_v, 2)})

    # 两类训练模式（高频低强、低频高强）的周热量消耗分布。
    pattern_weekly_burn = []
    for key in ["高频低强", "低频高强"]:
        stats = _box_stats(pattern_groups[key])
        if stats:
            pattern_weekly_burn.append({"name": key, "value": stats, "avg": round(_mean(pattern_groups[key]) or 0, 2)})

    # 顶部 KPI 摘要。
    kpis = {
        "sessions": len(rows),
        "avg_calories": round(_mean(calories_values) or 0, 2),
        "avg_bmi": round(_mean(bmi_values) or 0, 2),
        "avg_body_fat": round(_mean(fat_values) or 0, 2),
        "avg_water_intake": round(_mean(water_values) or 0, 2),
        "top_workout": workout_counts.most_common(1)[0][0] if workout_counts else "未知",
        "anomaly_count": anomaly_count,
        "skinny_fat_count": len(skinny_fat_points),
    }

    # 统一返回前端图表和 KPI 所需的所有结构。
    return {
        "kpis": kpis,
        "workout_counts": _topn(workout_counts, 12),
        "workout_body_metrics": workout_body_metrics,
        "bmi_performance": bmi_performance_list,
        "bmi_duration_box": bmi_duration_boxplot,
        "bmi_duration_scatter": bmi_duration_points,
        "frequency_effect": frequency_effect,
        "scatter_duration_calories": scatter_points,
        "duration_calories_trend": _regression_line(scatter_points),
        "heart_calorie_corr_by_age": heart_calorie_corr,
        "age_recovery": age_recovery_list,
        "recovery_resting_box": recovery_resting_box,
        "correlation_matrix": {"x": corr_names, "y": corr_names, "data": corr_data},
        "bmi_workout_calories": {"x": workout_order, "series": bmi_workout_series},
        "experience_radar": experience_radar,
        "water_by_workout": water_by_workout_list,
        "water_calorie_points": water_corr_points,
        "water_bpm_points": water_bpm_points,
        "water_calorie_corr": round(_pearson([p[0] for p in water_corr_points], [p[1] for p in water_corr_points]) or 0, 3),
        "water_bpm_corr": round(_pearson([p[0] for p in water_bpm_points], [p[1] for p in water_bpm_points]) or 0, 3),
        "pattern_weekly_burn": pattern_weekly_burn,
        "skinny_fat_scatter": skinny_fat_points,
        "bmi_hist": _hist(bmi_values, 12),
        "fat_hist": _hist(fat_values, 12),
        "duration_hist": _hist(duration_values, 12),
        "workout_frequency_hist": _hist([r["frequency"] for r in rows], 7),
        "recovery_hist": _hist(recovery_values, 12),
    }


def compute_diet_metrics():
    # 饮食大屏主计算函数。
    rows = _load_diet_rows()
    # 每个容器分别服务不同图表和卡片。
    by_food_group = defaultdict(list)
    avg_by_energy_level = defaultdict(lambda: {"carb_ratio": [], "protein_ratio": [], "fat_ratio": []})
    food_type_macros = defaultdict(lambda: {"carb": [], "protein": [], "fat": []})
    veg_nonveg_metrics = defaultdict(lambda: {"protein_ratio": [], "fat": [], "fibre": [], "carb_ratio": [], "protein": [], "health": []})
    allergy_counts = Counter()
    sugar_fibre_points = []
    energy_health_points = []
    top_health = []
    healthy_dense = []
    unhealthy_dense = []
    high_protein_low_fat = []
    energy_values = []
    health_scores = []
    protein_ratio_values = []

    for row in rows:
        # 先按不同维度把数据塞进分组容器里，后面再统一做均值/箱线图/TopN 聚合。
        by_food_group[row["food_group"]].append(row)
        avg_by_energy_level[row["energy_category"]]["carb_ratio"].append(row["carb_ratio"])
        avg_by_energy_level[row["energy_category"]]["protein_ratio"].append(row["protein_ratio"])
        avg_by_energy_level[row["energy_category"]]["fat_ratio"].append(row["fat_ratio"])
        food_type_macros[row["food_type"]]["carb"].append(row["carb"])
        food_type_macros[row["food_type"]]["protein"].append(row["protein"])
        food_type_macros[row["food_type"]]["fat"].append(row["fat"])
        veg_nonveg_metrics[row["food_group"]]["protein_ratio"].append(row["protein_calorie_ratio"])
        veg_nonveg_metrics[row["food_group"]]["fat"].append(row["fat"])
        veg_nonveg_metrics[row["food_group"]]["fibre"].append(row["fibre"])
        veg_nonveg_metrics[row["food_group"]]["carb_ratio"].append(row["carb_ratio"])
        veg_nonveg_metrics[row["food_group"]]["protein"].append(row["protein_ratio"])
        veg_nonveg_metrics[row["food_group"]]["health"].append(row["health_score"])
        # 散点图同样限制样本数上限，防止前端渲染过重。
        if row["free_sugar"] is not None and row["fibre"] is not None and row["health_score"] is not None and len(sugar_fibre_points) < 500:
            sugar_fibre_points.append({
                "group": row["food_group"],
                "sugar": row["free_sugar"],
                "fibre": row["fibre"],
                "health": row["health_score"],
            })
        if row["energy"] is not None and row["health_score"] is not None and len(energy_health_points) < 500:
            energy_health_points.append({
                "name": row["food_name"],
                "energy": row["energy"],
                "health": row["health_score"],
                "flag": "高纤高蛋白" if (row["fibre"] or 0) > 5 and (row["protein"] or 0) > 10 else "普通",
            })
        # 挑出高能量密度但健康度高/低的代表食物。
        if row["energy"] is not None and row["health_score"] is not None and row["energy"] >= 250:
            payload = {
                "name": row["food_name"],
                "energy": row["energy"],
                "health": row["health_score"],
                "food_group": row["food_group"],
            }
            if row["health_score"] >= 60:
                healthy_dense.append(payload)
            elif row["health_score"] <= 35:
                unhealthy_dense.append(payload)
        # 高蛋白低脂候选列表，后面直接给前端卡片区展示。
        if (row["protein_calorie_ratio"] or 0) > 0.07 and (row["fat"] or 0) < 10:
            high_protein_low_fat.append({
                "name": row["food_name"],
                "protein_calorie_ratio": row["protein_calorie_ratio"],
                "fat_g": row["fat"],
                "health_score": row["health_score"],
                "food_group": row["food_group"],
            })
        if row["health_score"] is not None:
            top_health.append((row["health_score"], row["food_name"], row["food_type"], row["region"], row["energy"]))
            health_scores.append(row["health_score"])
        if row["protein_calorie_ratio"] is not None:
            protein_ratio_values.append(row["protein_calorie_ratio"])
        energy_values.append(row["energy"])

        if row["allergies"]:
            for part in re.split(r"[;,/|]+", row["allergies"]):
                aa = _zh_allergy(part)
                if aa:
                    allergy_counts[aa] += 1

    # 食物大类健康评分箱线图和平均分对比。
    food_group_box = []
    food_group_avg = []
    for group, items in sorted(by_food_group.items(), key=lambda x: x[0]):
        stats = _box_stats([i["health_score"] for i in items])
        if stats:
            food_group_box.append({"name": group, "value": stats})
        food_group_avg.append({
            "name": group,
            "avg_health_score": round(_mean([i["health_score"] for i in items]) or 0, 2),
            "avg_nutrient_score": round(_mean([i["nutrient_score"] for i in items]) or 0, 2),
        })

    # 能量等级下三大营养素供能比例。
    macro_ratio_by_energy = []
    for level in ["低能量", "中能量", "高能量", "未知"]:
        if level not in avg_by_energy_level:
            continue
        macro_ratio_by_energy.append({
            "name": level,
            "carb_ratio": round(_mean(avg_by_energy_level[level]["carb_ratio"]) or 0, 2),
            "protein_ratio": round(_mean(avg_by_energy_level[level]["protein_ratio"]) or 0, 2),
            "fat_ratio": round(_mean(avg_by_energy_level[level]["fat_ratio"]) or 0, 2),
        })

    # 不同餐别的平均宏量营养。
    macro_avg_by_food_type = []
    for ft, vals in sorted(food_type_macros.items(), key=lambda x: x[0]):
        macro_avg_by_food_type.append({
            "name": ft,
            "carb_g": round(_mean(vals["carb"]) or 0, 2),
            "protein_g": round(_mean(vals["protein"]) or 0, 2),
            "fat_g": round(_mean(vals["fat"]) or 0, 2),
        })

    # 素食/荤食/纯素对比。
    veg_nonveg_compare = []
    for key in ["素食", "荤食", "纯素"]:
        if key not in veg_nonveg_metrics:
            continue
        veg_nonveg_compare.append({
            "name": key,
            "protein_ratio": round((_mean(veg_nonveg_metrics[key]["protein_ratio"]) or 0) * 100, 2),
            "fat_g": round(_mean(veg_nonveg_metrics[key]["fat"]) or 0, 2),
            "fibre_g": round(_mean(veg_nonveg_metrics[key]["fibre"]) or 0, 2),
            "carb_ratio": round(_mean(veg_nonveg_metrics[key]["carb_ratio"]) or 0, 2),
            "protein_energy_ratio": round(_mean(veg_nonveg_metrics[key]["protein"]) or 0, 2),
        })

    # 选出健康评分最高的一批食物卡片。
    top_health_cards = [
        {"name": n, "health_score": h, "food_type": t, "region": r, "energy_kcal": e}
        for h, n, t, r, e in sorted(top_health, key=lambda x: x[0], reverse=True)
        if r != "\u5168\u5370\u5ea6"
    ][:12]

    # 饮食大屏顶部 KPI。
    kpis = {
        "foods": len(rows),
        "avg_energy_kcal": round(_mean(energy_values) or 0, 2),
        "avg_health_score": round(_mean(health_scores) or 0, 2),
        "avg_protein_calorie_ratio": round(_mean(protein_ratio_values) or 0, 4),
        "top_allergy": allergy_counts.most_common(1)[0][0] if allergy_counts else "无明显过敏原",
    }

    # 返回给饮食大屏前端的完整数据结构。
    return {
        "kpis": kpis,
        "food_group_health_box": food_group_box,
        "food_group_health_avg": food_group_avg,
        "macro_ratio_by_energy": macro_ratio_by_energy,
        "macro_avg_by_food_type": macro_avg_by_food_type,
        "veg_nonveg_compare": veg_nonveg_compare,
        "sugar_fibre_health_scatter": sugar_fibre_points,
        "energy_health_scatter": energy_health_points,
        "energy_health_spearman": round(_spearman([p["energy"] for p in energy_health_points], [p["health"] for p in energy_health_points]) or 0, 3),
        "healthy_dense_top": sorted(healthy_dense, key=lambda x: x["health"], reverse=True)[:10],
        "unhealthy_dense_top": sorted(unhealthy_dense, key=lambda x: x["health"])[:10],
        "high_protein_low_fat_foods": sorted(high_protein_low_fat, key=lambda x: ((x["health_score"] or 0), (x["protein_calorie_ratio"] or 0)), reverse=True)[:15],
        "allergy_top": _topn(allergy_counts, 10),
        "top_health_foods": top_health_cards,
        "health_hist": _hist(health_scores, 12),
    }


_gym_cache = None
_gym_cache_time = 0

# Redis 配置（与 Java application.yml 保持一致）
_REDIS_HOST = "10.252.254.178"
_REDIS_PORT = 6379
_REDIS_PASSWORD = "Xin3209273024"
_REDIS_DB = 0
_REDIS_GYM_KEY = "gym:dashboard:data"
_REDIS_CACHE_TTL = 86400

# 磁盘缓存目录（Redis 不可用时的降级方案）
_DISK_CACHE_DIR = Path(__file__).resolve().parent.parent / "cache"
_DISK_CACHE_DIR.mkdir(parents=True, exist_ok=True)
_GYM_DISK_CACHE_FILE = _DISK_CACHE_DIR / "gym_dashboard_cache.json"

_redis_conn = None
if _redis_module is not None:
    try:
        _redis_conn = _redis_module.Redis(
            host=_REDIS_HOST,
            port=_REDIS_PORT,
            password=_REDIS_PASSWORD,
            db=_REDIS_DB,
            socket_connect_timeout=3,
            socket_timeout=3,
            decode_responses=True,
        )
        _redis_conn.ping()
    except Exception:
        _redis_conn = None

WORKOUT_TYPE_CN = {
    "Strength": "力量训练",
    "HIIT": "高强度间歇训练",
    "Cardio": "有氧训练",
    "Yoga": "瑜伽",
}

DIET_TYPE_CN = {
    "Vegan": "纯素",
    "Vegetarian": "素食",
    "Keto": "生酮",
    "Low-Carb": "低碳",
    "Paleo": "原始人饮食",
    "Balanced": "均衡饮食",
}

DIFFICULTY_CN = {
    "Beginner": "初级",
    "Intermediate": "中级",
    "Advanced": "高级",
}

EXERCISE_NAME_CN = {
    "Dips": "双杠臂屈伸", "Plank": "平板支撑", "Squats": "深蹲",
    "Lunges": "弓步蹲", "Step-ups": "台阶训练", "Pull-ups": "引体向上",
    "Leg Press": "腿举", "Deadlifts": "硬拉", "Thrusters": "火箭推",
    "Frog Jumps": "蛙跳", "Bear Crawls": "熊爬", "Bicep Curls": "肱二头肌弯举",
    "Prone Cobras": "俯卧眼镜蛇式", "Glute Bridges": "臀桥",
    "Lat Pulldowns": "高位下拉", "Shoulder Press": "肩推",
    "Russian Twists": "俄罗斯转体", "Decline Push-ups": "下斜俯卧撑",
    "Turkish Get-ups": "土耳其起立", "Mountain Climbers": "登山者",
    "Dumbbell flyes": "哑铃飞鸟", "Standing calf raises": "站姿提踵",
    "Lateral raises": "侧平举", "Incline dumbbell flyes": "上斜哑铃飞鸟",
    "Push-ups": "俯卧撑", "Bench Press": "卧推", "Rows": "划船",
    "Crunches": "卷腹", "Burpees": "波比跳", "Jumping Jacks": "开合跳",
    "Box Jumps": "跳箱", "Kettlebell Swings": "壶铃摆荡",
    "Tricep Extensions": "肱三头肌伸展", "Hammer Curls": "锤式弯举",
    "Leg Extensions": "腿屈伸", "Leg Curls": "腿弯举",
    "Calf Raises": "提踵", "Face Pulls": "面拉", "Hip Thrusts": "臀推",
    "Front Raises": "前平举", "Reverse Flyes": "反向飞鸟",
    "Overhead Press": "过头推举", "Arnold Press": "阿诺德推举",
    "Skull Crushers": "碎颅者", "Preacher Curls": "牧师凳弯举",
    "Concentration Curls": "集中弯举", "Wrist Curls": "腕弯举",
    "Shrugs": "耸肩", "Good Mornings": "早安式",
    "Romanian Deadlifts": "罗马尼亚硬拉", "Sumo Deadlifts": "相扑硬拉",
    "Front Squats": "前蹲", "Goblet Squats": "高脚杯深蹲",
    "Bulgarian Split Squats": "保加利亚分腿蹲", "Pistol Squats": "手枪深蹲",
    "Wall Sit": "靠墙静蹲", "Flutter Kicks": "交替打水",
    "Bicycle Crunches": "自行车卷腹", "Leg Raises": "抬腿",
    "Hanging Leg Raises": "悬垂举腿", "Side Plank": "侧平板支撑",
    "Superman": "超人式", "Bird Dog": "鸟狗式", "Dead Bug": "死虫式",
    "Pallof Press": "帕洛夫推", "Cable Woodchop": "绳索伐木",
    "Medicine Ball Slams": "药球砸地", "Battle Ropes": "战绳",
    "Sled Push": "雪橇推", "Farmers Walk": "农夫行走",
    "Tire Flips": "翻轮胎", "Boxing": "拳击", "Jump Rope": "跳绳",
    "Rowing": "划船机", "Cycling": "骑行", "Running": "跑步",
    "Swimming": "游泳", "Elliptical": "椭圆机", "Stair Climber": "爬楼机",
    "Treadmill": "跑步机", "Tricep Dips": "肱三头肌臂屈伸",
    "Chest Flyes": "胸肌飞鸟", "Pec Deck": "蝴蝶机夹胸",
    "Cable Crossovers": "绳索交叉", "Incline Bench Press": "上斜卧推",
    "Decline Bench Press": "下斜卧推", "Close-Grip Bench Press": "窄距卧推",
    "Wide-Grip Pull-ups": "宽距引体向上", "Chin-ups": "反握引体向上",
    "T-Bar Rows": "T杠划船", "Pendlay Rows": "潘德莱划船",
    "Seated Cable Rows": "坐姿绳索划船",
    "Single-Arm Dumbbell Rows": "单臂哑铃划船",
    "Inverted Rows": "反向划船", "Straight-Arm Pulldowns": "直臂下拉",
    "Upright Rows": "直立划船", "Barbell Shrugs": "杠铃耸肩",
    "Dumbbell Shrugs": "哑铃耸肩", "Plate Pinch": "杠铃片捏握",
    "Reverse Lunges": "反向弓步蹲", "Push Ups": "俯卧撑",
    "Seated Rows": "坐姿划船", "Scissors Kicks": "剪刀踢",
    "Lateral Raises": "侧平举", "Front Raises": "前平举",
    "Tricep Dips": "肱三头肌臂屈伸", "Bench Dips": "凳上臂屈伸",
    "Jumping Jacks": "开合跳", "Bicycle Crunches": "自行车卷腹",
    "Leg Raises": "抬腿", "Hanging Leg Raises": "悬垂举腿",
    "Decline Pushups": "下斜俯卧撑", "Incline Pushups": "上斜俯卧撑",
    "Single Arm Dumbbell Rows": "单臂哑铃划船",
    "Straight Arm Pulldowns": "直臂下拉",
    "Incline Dumbbell Flyes": "上斜哑铃飞鸟",
    "Dumbbell Flyes": "哑铃飞鸟", "Reverse Flyes": "反向飞鸟",
    "Chest Flyes": "胸肌飞鸟", "Standing Calf Raises": "站姿提踵",
    "Calf Raises": "提踵", "Hip Thrusts": "臀推",
    "Glute Bridges": "臀桥", "Box Jumps": "跳箱",
    "Step Ups": "台阶训练", "Pull Ups": "引体向上",
    "Chin Ups": "反握引体向上", "Muscle Ups": "双力臂",
    "Bear Crawls": "熊爬", "Frog Jumps": "蛙跳",
}

FOOD_NAME_CN = {
    "Palak Paneer": "菠菜奶酪", "Chole Bhature": "鹰嘴豆配炸饼", "Butter Chicken": "黄油鸡",
    "Dal Tadka": "调味豆糊", "Masala Dosa": "马萨拉薄饼", "Chicken Biryani": "鸡肉炒饭",
    "Samosa": "咖喱角", "Paneer Tikka": "烤奶酪块", "Aloo Gobi": "土豆菜花",
    "Mutton Rogan Josh": "羊肉咖喱", "Idli": "米糕", "Upma": "粗麦粥",
    "Chicken Korma": "鸡肉酸奶咖喱", "Chana Masala": "鹰嘴豆咖喱",
    "Vegetable Biryani": "蔬菜炒饭", "Methi Thepla": "葫芦巴薄饼",
    "Chicken Tikka": "烤鸡块", "Aloo Paratha": "土豆馅饼", "Keema Matar": "肉末豌豆",
    "Palak Chaat": "菠菜小吃", "Malai Kofta": "奶油蔬菜丸",
    "Hyderabadi Biryani": "海得拉巴炒饭", "Saag Aloo": "菠菜土豆",
    "Chicken Shawarma": "鸡肉卷", "Methi Paratha": "葫芦巴饼",
    "Kadhai Paneer": "铁锅奶酪", "Masoor Dal": "红扁豆糊",
    "Chicken Pakora": "炸鸡块", "Chana Pulao": "鹰嘴豆饭",
    "Prawn Curry": "咖喱虾", "Dal Makhani": "黄油豆糊", "Rajma": "红豆咖喱",
    "Biryani": "印度炒饭", "Roti": "全麦饼", "Naan": "馕饼",
    "Tandoori Chicken": "烤鸡", "Fish Curry": "咖喱鱼", "Dhokla": "蒸糕",
    "Pav Bhaji": "蔬菜泥配面包", "Vada Pav": "土豆炸饼汉堡",
    "Aloo Paratha": "土豆馅饼", "Chicken Curry": "咖喱鸡",
    "Palak Chaat": "菠菜沙拉", "Lamb Curry": "咖喱羊肉",
    "Pani Puri": "脆球小吃", "Sev Puri": "脆面小吃",
    "Bhel Puri": "膨化米小吃", "Aloo Tikki": "土豆饼",
    "Chicken 65": "65号辣鸡", "Mutton Biryani": "羊肉炒饭",
    "Egg Curry": "咖喱蛋", "Chicken Chettinad": "胡椒鸡",
    "Dal Fry": "炒豆糊", "Chana Dal": "鹰嘴豆瓣",
    "Moong Dal": "绿豆糊", "Toor Dal": "木豆糊",
    "Urad Dal": "黑豆糊", "Rajma Chawal": "红豆饭",
    "Kadhi Pakora": "酸奶咖喱饺", "Baingan Bharta": "烤茄子泥",
    "Bhindi Masala": "秋葵咖喱", "Matar Paneer": "豌豆奶酪",
    "Shahi Paneer": "皇家奶酪", "Palak Dal": "菠菜豆糊",
    "Aloo Methi": "土豆葫芦巴", "Gobi Manchurian": "菜花满洲",
    "Chicken Manchurian": "鸡肉满洲", "Veg Manchurian": "蔬菜满洲",
    "Hakka Noodles": "客家炒面", "Fried Rice": "炒饭",
    "Chicken Lollipop": "鸡腿棒", "Chilli Chicken": "辣子鸡",
    "Chicken Momos": "鸡肉饺子", "Veg Momos": "蔬菜饺子",
    "Masala Chai": "香料奶茶", "Lassi": "酸奶饮",
    "Mango Lassi": "芒果酸奶饮", "Gulab Jamun": "玫瑰糖球",
    "Rasgulla": "奶酪糖球", "Jalebi": "炸糖圈",
    "Kheer": "米布丁", "Halwa": "甜糕",
    "Chicken Salad": "鸡肉沙拉", "Vegetable Pulao": "蔬菜香饭",
    "Jeera Rice": "孜然米饭", "Lemon Rice": "柠檬饭",
    "Curd Rice": "酸奶饭", "Tomato Rice": "番茄饭",
    "Coconut Rice": "椰子饭", "Tamarind Rice": "罗望子饭",
    "Chicken Do Pyaza": "双葱鸡", "Mutton Do Pyaza": "双葱羊肉",
    "Chicken Vindaloo": "酸辣鸡", "Goan Fish Curry": "果阿咖喱鱼",
    "Prawn Masala": "咖喱虾仁", "Crab Curry": "咖喱蟹",
    "Chicken Jalfrezi": "爆炒蔬菜鸡", "Mushroom Curry": "咖喱蘑菇",
    "Mix Veg Curry": "什锦蔬菜咖喱", "Sambar": "酸豆汤",
    "Rasam": "胡椒汤", "Dahi Vada": "酸奶炸饼",
    "Poha": "压扁米", "Khichdi": "米豆粥",
    "Rice": "米饭", "Plain Rice": "白米饭", "Brown Rice": "糙米饭",
    "Boiled Rice": "煮米饭", "Basmati Rice": "巴斯马蒂米",
    "Chapati": "薄饼", "Phulka": "膨饼", "Paratha": "层饼",
    "Puri": "油炸饼", "Bhatura": "大炸饼", "Kulcha": "烤饼",
    "Appam": "米饼", "Dosa": "米薄饼", "Uttapam": "厚米饼",
    "Kachori": "油炸豆饼", "Pakora": "炸蔬菜",
    "Paneer Paratha": "奶酪馅饼", "Gobi Paratha": "菜花馅饼",
    "Muli Paratha": "萝卜馅饼", "Onion Paratha": "洋葱馅饼",
    "Paneer Bhurji": "碎奶酪", "Egg Bhurji": "碎蛋炒",
    "Chicken Frankie": "鸡肉卷", "Paneer Frankie": "奶酪卷",
    "Chicken Seekh Kebab": "鸡肉烤肉串", "Mutton Seekh Kebab": "羊肉烤肉串",
    "Chicken Reshmi Kebab": "嫩鸡串", "Hara Bhara Kebab": "蔬菜饼",
    "Chicken Tikka Masala": "烤鸡咖喱", "Paneer Tikka Masala": "烤奶酪咖喱",
    "Chicken Saagwala": "菠菜鸡", "Lamb Saagwala": "菠菜羊肉",
    "Palak Corn": "菠菜玉米", "Corn Palak": "玉米菠菜",
    "Methi Malai Paneer": "奶油葫芦巴奶酪", "Methi Chicken": "葫芦巴鸡",
    "Methi Matar Malai": "奶油葫芦巴豌豆", "Dum Aloo": "慢炖土豆",
    "Aloo Dum": "炖土豆", "Aloo Posto": "罂粟籽土豆",
    "Chicken Kali Mirch": "黑椒鸡", "Chicken Achari": "腌菜鸡",
    "Chicken Handi": "瓦罐鸡", "Mutton Handi": "瓦罐羊肉",
    "Chicken Rara": "肉末鸡", "Mutton Rara": "肉末羊肉",
    "Chicken Lababdar": "稠汁鸡", "Paneer Lababdar": "稠汁奶酪",
    "Butter Naan": "黄油馕", "Garlic Naan": "蒜香馕",
    "Cheese Naan": "奶酪馕", "Tandoori Roti": "烤全麦饼",
    "Missi Roti": "香料饼", "Makki Di Roti": "玉米饼",
    "Rumali Roti": "手帕薄饼", "Laccha Paratha": "千层饼",
    "Green Salad": "蔬菜沙拉", "Cucumber Salad": "黄瓜沙拉",
    "Onion Salad": "洋葱沙拉", "Kachumber Salad": "印式沙拉",
    "Raita": "酸奶酱", "Boondi Raita": "炸豆酸奶",
    "Cucumber Raita": "黄瓜酸奶", "Pineapple Raita": "菠萝酸奶",
    "Mint Chutney": "薄荷酱", "Tamarind Chutney": "罗望子酱",
    "Coconut Chutney": "椰子酱", "Tomato Chutney": "番茄酱",
    "Mango Pickle": "芒果泡菜", "Lemon Pickle": "柠檬泡菜",
    "Chilli Pickle": "辣椒泡菜", "Mix Pickle": "什锦泡菜",
    "Papad": "薄脆饼", "Papadum": "豆粉薄脆",
    "Sev": "脆面条", "Chivda": "香料炒米",
    "Peanut Chikki": "花生糖", "Til Chikki": "芝麻糖",
    "Coconut Barfi": "椰子糖糕", "Kaju Katli": "腰果糖片",
    "Besan Ladoo": "鹰嘴豆粉球", "Motichoor Ladoo": "细丝糖球",
    "Peda": "奶糖", "Mysore Pak": "迈索尔酥",
    "Soan Papdi": "丝糖", "Sandesh": "奶酪甜点",
    "Rasmalai": "奶酪奶球", "Kulfi": "印度冰淇淋",
    "Falooda": "玫瑰面条甜饮", "Badam Milk": "杏仁奶",
    "Chicken Soup": "鸡汤", "Tomato Soup": "番茄汤",
    "Sweet Corn Soup": "玉米汤", "Hot and Sour Soup": "酸辣汤",
    "Manchow Soup": "满洲汤", "Lemon Coriander Soup": "柠檬香菜汤",
    "Chicken Nuggets": "鸡块", "French Fries": "炸薯条",
    "Veg Burger": "蔬菜汉堡", "Chicken Burger": "鸡肉汉堡",
    "Veg Pizza": "蔬菜披萨", "Chicken Pizza": "鸡肉披萨",
    "Pasta": "意面", "White Sauce Pasta": "白酱意面",
    "Red Sauce Pasta": "红酱意面", "Chicken Pasta": "鸡肉意面",
    "Veg Sandwich": "蔬菜三明治", "Chicken Sandwich": "鸡肉三明治",
    "Grilled Sandwich": "烤三明治", "Cheese Sandwich": "奶酪三明治",
    "Veg Maggi": "蔬菜方便面", "Masala Maggi": "香料方便面",
    "Omelette": "蛋饼", "Masala Omelette": "香料蛋饼",
    "Bread Omelette": "面包蛋饼", "Boiled Egg": "煮鸡蛋",
    "Scrambled Egg": "炒鸡蛋", "Egg Fried Rice": "蛋炒饭",
    "Chicken Fried Rice": "鸡肉炒饭", "Veg Fried Rice": "蔬菜炒饭",
    "Schezwan Fried Rice": "四川炒饭", "Triple Schezwan Rice": "三味四川饭",
    "Chicken Noodles": "鸡肉面", "Veg Noodles": "蔬菜面",
    "Schezwan Noodles": "四川面", "Chilli Garlic Noodles": "蒜辣面",
    "Chicken Lollipop Dry": "干鸡腿棒", "Chicken Chilli": "辣椒鸡",
    "Paneer Chilli": "辣椒奶酪", "Mushroom Chilli": "辣椒蘑菇",
    "Gobi 65": "65号菜花", "Paneer 65": "65号奶酪",
    "Veg Crispy": "脆炸蔬菜", "Chicken Crispy": "脆炸鸡",
    "Spring Roll": "春卷", "Veg Spring Roll": "蔬菜春卷",
    "Chicken Spring Roll": "鸡肉春卷", "Veg Manchurian Dry": "干蔬菜丸",
    "Chicken Manchurian Dry": "干鸡肉丸", "Veg Manchurian Gravy": "汁蔬菜丸",
    "Chicken Manchurian Gravy": "汁鸡肉丸",
    "Laal Maas": "辣羊肉咖喱", "Kosha Mangsho": "慢炖山羊肉",
    "Kozhi Varuval": "香料炒鸡", "Chettinad Chicken": "胡椒鸡",
    "Lahori Chicken": "拉合尔鸡", "Chettinad Fish Curry": "胡椒咖喱鱼",
    "Chettinad Mutton Curry": "胡椒羊肉咖喱",
    "Bisi Bele Bath": "香料豆饭", "Curd Rice": "酸奶饭",
    "Veg Biryani": "蔬菜炒饭", "Egg Biryani": "鸡蛋炒饭",
    "Mushroom Biryani": "蘑菇炒饭", "Paneer Biryani": "奶酪炒饭",
    "Dal Khichdi": "豆糊粥", "Veg Pulao": "蔬菜香饭",
    "Peas Pulao": "豌豆饭", "Kashmiri Pulao": "克什米尔饭",
    "Chicken Keema": "鸡肉末", "Mutton Keema": "羊肉末",
    "Egg Curry with Rice": "咖喱蛋配饭", "Dal Chawal": "豆糊饭",
    "Rajma Rice": "红豆饭", "Chole Rice": "鹰嘴豆饭",
    "Kadhi Chawal": "酸奶咖喱饭", "Sambar Rice": "酸汤饭",
    "Pongal": "米豆粥", "Ven Pongal": "咸米豆粥",
    "Sakkarai Pongal": "甜米豆粥", "Bisibelebath": "香料豆饭",
    "Vangi Bath": "茄子饭", "Puliyogare": "罗望子饭",
    "Chitranna": "柠檬饭", "Tomato Bath": "番茄饭",
    "Bonda Soup": "土豆球汤", "Vada": "炸豆饼",
    "Medu Vada": "米豆炸饼", "Dal Vada": "豆炸饼",
    "Sabudana Vada": "西米炸饼", "Batata Vada": "土豆炸饼",
    "Dahi Bhalla": "酸奶炸饼", "Dahi Puri": "酸奶脆球",
    "Papdi Chaat": "脆饼小吃", "Samosa Chaat": "咖喱角碎",
    "Aloo Chaat": "土豆小吃", "Fruit Chaat": "水果沙拉",
    "Chana Chaat": "鹰嘴豆沙拉", "Corn Chaat": "玉米沙拉",
    "Sukha Puri": "脆球", "Dabeli": "香料土豆堡",
    "Misal Pav": "辣豆配面包", "Usal Pav": "豆芽配面包",
    "Kanda Bhaji": "洋葱炸饼", "Sabudana Khichdi": "西米粥",
    "Thalipeeth": "杂粮饼", "Kothimbir Vadi": "香菜蒸糕",
    "Pithla Bhakri": "豆糊配饼", "Zunka Bhakar": "豆粉饼",
    "Puran Poli": "甜豆馅饼", "Modak": "甜饺子",
    "Ukadiche Modak": "蒸甜饺子", "Shrikhand": "酸奶甜点",
    "Amrakhand": "芒果酸奶甜点", "Basundi": "浓缩奶甜点",
    "Gajar Halwa": "胡萝卜甜糕", "Moong Dal Halwa": "绿豆甜糕",
    "Suji Halwa": "粗麦甜糕", "Atta Halwa": "全麦甜糕",
    "Lauki Halwa": "葫芦甜糕", "Dudhi Halwa": "葫芦奶糕",
    "Malpua": "甜煎饼", "Rabri": "炼乳甜点",
}

FOOD_INGREDIENT_CN = {
    "Chicken": "鸡肉", "Mutton": "羊肉", "Lamb": "羊肉",
    "Prawn": "虾", "Fish": "鱼", "Egg": "鸡蛋",
    "Paneer": "奶酪", "Vegetable": "蔬菜", "Veg": "蔬菜",
    "Mushroom": "蘑菇", "Corn": "玉米", "Peas": "豌豆",
    "Potato": "土豆", "Tomato": "番茄", "Onion": "洋葱",
    "Cabbage": "卷心菜", "Cauliflower": "菜花", "Gobi": "菜花",
    "Aloo": "土豆", "Alu": "土豆", "Chana": "鹰嘴豆",
    "Dal": "豆糊", "Daal": "豆糊", "Rajma": "红豆",
    "Palak": "菠菜", "Saag": "菠菜", "Methi": "葫芦巴",
    "Matar": "豌豆", "Baingan": "茄子", "Brinjal": "茄子",
    "Bhindi": "秋葵", "Lauki": "葫芦", "Karela": "苦瓜",
    "Rice": "米饭", "Biryani": "炒饭", "Pulao": "香饭",
    "Khichdi": "米豆粥", "Pongal": "米豆粥", "Upma": "粗麦粥",
    "Roti": "饼", "Naan": "馕", "Paratha": "馅饼",
    "Puri": "炸饼", "Dosa": "薄饼", "Idli": "米糕",
    "Pakora": "炸", "Pakoda": "炸", "Kebab": "烤肉串",
    "Tikka": "烤块", "Tandoori": "烤", "Masala": "咖喱",
    "Curry": "咖喱", "Korma": "酸奶咖喱", "Vindaloo": "酸辣",
    "Biryani": "炒饭", "Salad": "沙拉", "Soup": "汤",
    "Chaat": "小吃", "Chutney": "酱", "Raita": "酸奶酱",
    "Pickle": "泡菜", "Lassi": "酸奶饮", "Kheer": "米布丁",
    "Halwa": "甜糕", "Barfi": "糖糕", "Ladoo": "糖球",
    "Sandwich": "三明治", "Burger": "汉堡", "Pizza": "披萨",
    "Pasta": "意面", "Noodles": "面", "Maggi": "方便面",
    "Omelette": "蛋饼", "Fried": "炒", "Steam": "蒸",
    "Boiled": "煮", "Grilled": "烤", "Roasted": "烤",
    "Butter": "黄油", "Garlic": "蒜香", "Cheese": "奶酪",
    "Coconut": "椰子", "Lemon": "柠檬", "Mango": "芒果",
    "Tempeh": "天贝", "Tofu": "豆腐", "Stir-Fry": "炒",
    "Stir Fry": "炒", "Protein-Packed": "高蛋白", "Protein": "蛋白",
    "Lentil": "小扁豆", "Breast": "胸肉", "Steamed": "蒸",
    "Hummus": "鹰嘴豆泥", "Chickpea": "鹰嘴豆",
    "Broccoli": "西兰花", "Spinach": "菠菜", "Carrot": "胡萝卜",
    "Cucumber": "黄瓜", "Pepper": "辣椒", "Beans": "豆",
    "Lentils": "小扁豆", "Quinoa": "藜麦", "Oats": "燕麦",
    "Brown Rice": "糙米", "Whole Wheat": "全麦", "Multigrain": "杂粮",
    "Baked": "烤", "Poached": "水煮", "Raw": "生",
    "Fresh": "新鲜", "Organic": "有机", "Sprouted": "发芽",
    "Herb": "香草", "Spice": "香料", "Seed": "籽",
    "Nut": "坚果", "Almond": "杏仁", "Cashew": "腰果",
    "Walnut": "核桃", "Peanut": "花生", "Sesame": "芝麻",
    "Flaxseed": "亚麻籽", "Chia": "奇亚籽", "Pumpkin": "南瓜",
    "Sweet Potato": "红薯", "Bell Pepper": "甜椒", "Zucchini": "西葫芦",
    "Eggplant": "茄子", "Radish": "萝卜", "Beetroot": "甜菜根",
    "Yogurt": "酸奶", "Curd": "酸奶", "Milk": "牛奶",
    "Paneer": "印度奶酪", "Ghee": "酥油", "Cottage Cheese": "乡村奶酪",
    "Soy": "大豆", "Soya": "大豆", "Soya Bean": "大豆",
    "Spicy": "辣", "Turkey": "火鸡", "Cod": "鳕鱼",
    "Tilapia": "罗非鱼", "Shrimp": "虾", "Whites": "蛋白",
    "Fry": "炒",
    "vegetarian": "素", "Vegetarian": "素",
    "vegetables": "蔬菜", "Vegetables": "蔬菜",
    "vegetable": "蔬菜", "Vegetable": "蔬菜",
    "veggies": "蔬菜", "Veggies": "蔬菜",
    "Vegan": "纯素",
    "Keema": "肉末",
    "Roast": "烤", "Tuna": "金枪鱼",
    "with": "配", "and": "和",
    " Sauce": "酱", " Gravy": "汁", " Dry": "干",
    " Wrap": "卷", " Roll": "卷", " Ball": "丸",
}

def _zh_food(name):
    if not name:
        return name
    cn = FOOD_NAME_CN.get(name)
    if cn:
        return cn
    result = name
    for en, zh in sorted(FOOD_INGREDIENT_CN.items(), key=lambda x: -len(x[0])):
        result = result.replace(en, zh)
    return result if result != name else name

def _zh_muscle(v):
    m = {
        "Core": "核心", "Shoulders": "肩部", "Glutes": "臀肌", "Triceps": "肱三头肌",
        "Hamstrings": "腘绳肌", "Quadriceps": "股四头肌", "Legs": "腿部",
        "Upper Back": "上背", "Chest": "胸部", "Back": "背部", "Obliques": "腹斜肌",
        "Biceps": "肱二头肌", "Lower Back": "下背", "Rear Deltoids": "后三角肌",
        "Full Body": "全身", "Forearms": "前臂", "Lats": "背阔肌",
        "Abs": "腹肌", "Calves": "小腿", "Hip Flexors": "髋屈肌",
        "Trapezius": "斜方肌", "Adductors": "内收肌", "Abductors": "外展肌",
        "Neck": "颈部", "Grip Strength": "握力",
    }
    vv = v.strip()
    return m.get(vv, vv)

def _load_archive_rows():
    path = DATA_DIR / "archive" / "Final_data.csv"
    rows = []
    seen = set()
    for raw in _read_rows(path):
        key = tuple(sorted(raw.items()))
        if key in seen:
            continue
        seen.add(key)
        weight = _to_float(raw.get("Weight (kg)"))
        height = _to_float(raw.get("Height (m)"))
        if weight is None or weight <= 0 or height is None or height <= 0:
            continue
        gender_raw = raw.get("Gender", "").strip()
        gender = "男" if gender_raw == "Male" else ("女" if gender_raw == "Female" else "未知")
        workout_type = WORKOUT_TYPE_CN.get(raw.get("Workout_Type", ""), raw.get("Workout_Type", "未知"))
        diet_type = DIET_TYPE_CN.get(raw.get("diet_type", ""), raw.get("diet_type", "未知"))
        raw_name = raw.get("Name of Exercise", "").strip()
        exercise_name = EXERCISE_NAME_CN.get(raw_name, raw_name)
        if exercise_name == raw_name:
            lower = raw_name.lower()
            for k, v in EXERCISE_NAME_CN.items():
                if k.lower() == lower:
                    exercise_name = v
                    break
        muscle_raw = raw.get("Target Muscle Group", "").strip().replace('"', '')
        muscles = [_zh_muscle(m) for m in muscle_raw.split(",") if m.strip()]

        bmi_val = _to_float(raw.get("BMI"))
        if bmi_val is None and weight is not None and height not in (None, 0):
            bmi_val = round(weight / (height * height), 2)

        rows.append({
            "gender": gender,
            "workout_type": workout_type,
            "diet_type": diet_type,
            "exercise_name": exercise_name,
            "muscles": muscles,
            "bmi": bmi_val,
            "calories_burned": _to_float(raw.get("Calories_Burned")),
            "session_duration": _to_float(raw.get("Session_Duration (hours)")),
            "workout_frequency": _to_float(raw.get("Workout_Frequency (days/week)")),
            "fat_percentage": _to_float(raw.get("Fat_Percentage")),
            "water_intake": _to_float(raw.get("Water_Intake (liters)")),
            "meals_frequency": _to_float(raw.get("Daily meals frequency")),
            "carbs": _to_float(raw.get("Carbs")),
            "proteins": _to_float(raw.get("Proteins")),
            "fats": _to_float(raw.get("Fats")),
            "ingested_calories": _to_float(raw.get("Calories")),
            "burn_30min": _to_float(raw.get("Burns Calories (per 30 min)")),
            "pct_hrr": _to_float(raw.get("pct_HRR")),
            "cal_balance": _to_float(raw.get("cal_balance")),
            "difficulty": DIFFICULTY_CN.get(raw.get("Difficulty Level", ""), raw.get("Difficulty Level", "未知")),
        })
    return rows

def compute_gym_plan_metrics():
    global _gym_cache, _gym_cache_time
    import time
    now = time.time()
    if _gym_cache is not None and now - _gym_cache_time < 60:
        return _gym_cache

    rows = _load_archive_rows()

    # ===== 图1：性别×运动类型偏好热力图 =====
    gender_workout = defaultdict(lambda: defaultdict(int))
    gender_totals = defaultdict(int)
    for r in rows:
        gender_workout[r["gender"]][r["workout_type"]] += 1
        gender_totals[r["gender"]] += 1
    workout_types_cn = list(WORKOUT_TYPE_CN.values())
    genders_cn = ["男", "女"]
    heat_gender_workout = []
    for yi, g in enumerate(genders_cn):
        for xi, w in enumerate(workout_types_cn):
            pct = round(gender_workout[g][w] / gender_totals[g] * 100, 1) if gender_totals[g] else 0
            heat_gender_workout.append([xi, yi, pct])
    max_val = max((d[2] for d in heat_gender_workout), default=30)
    chart1_gender_workout = {
        "x": workout_types_cn,
        "y": genders_cn,
        "data": heat_gender_workout,
        "max": max_val,
    }

    # ===== 图2：BMI分类×运动类型推荐热力图 =====
    # 不同BMI人群做不同运动的平均热量消耗，体现“哪类人群最适合哪种运动”
    bmi_workout_cal = defaultdict(lambda: defaultdict(list))
    for r in rows:
        bmi_cat = _bmi_category_from_value(r.get("bmi"))
        if r["calories_burned"]:
            bmi_workout_cal[bmi_cat][r["workout_type"]].append(r["calories_burned"])
    bmi_cats = ["偏瘦", "正常", "超重", "肥胖"]
    heat_bmi_workout = []
    for yi, bmi in enumerate(bmi_cats):
        for xi, wt in enumerate(workout_types_cn):
            avg = round(_mean(bmi_workout_cal[bmi].get(wt, [])) or 0, 1)
            heat_bmi_workout.append([xi, yi, avg])
    max_bmi_cal = max((d[2] for d in heat_bmi_workout), default=400)
    chart2_bmi_workout = {
        "x": workout_types_cn,
        "y": bmi_cats,
        "data": heat_bmi_workout,
        "max": max_bmi_cal,
    }

    # ===== 图3：推荐训练单元 vs 实际训练频次 =====
    # 从 GYM.csv 中提取推荐训练单元数
    gym_path = DATA_DIR / "GYM.csv"
    recommended_units_by_bmi = defaultdict(list)
    gym_seen = set()
    for raw in _read_rows(gym_path):
        key = tuple(sorted(raw.items()))
        if key in gym_seen:
            continue
        gym_seen.add(key)
        bmi = _zh(raw.get("BMI Category", "") or "Unknown", BMI_CAT_MAP, "未知")
        schedule = _parse_schedule(raw.get("Exercise Schedule", ""))
        recommended_units_by_bmi[bmi].append(schedule["total_units"])
    # 从 Final_data.csv 中提取实际训练频次
    actual_frequency_by_bmi = defaultdict(list)
    for r in rows:
        if r["workout_frequency"] is not None:
            bmi_cat = _bmi_category_from_value(r.get("bmi", None))
            actual_frequency_by_bmi[bmi_cat].append(r["workout_frequency"])
    chart3_recommend_vs_actual = []
    for bmi in ["偏瘦", "正常", "超重", "肥胖"]:
        chart3_recommend_vs_actual.append({
            "name": bmi,
            "recommended_units": round(_mean(recommended_units_by_bmi[bmi]) or 0, 2),
            "actual_frequency": round(_mean(actual_frequency_by_bmi[bmi]) or 0, 2),
        })

    # ===== 图4：训练频率分组×饮食策略偏好堆叠柱状图 =====
    # 不同训练频率的人群偏好什么饮食，体现“高频训练的人该吃什么，低频训练的人该吃什么”
    freq_diet = defaultdict(lambda: defaultdict(int))
    for r in rows:
        freq = r["workout_frequency"] or 0
        if freq <= 2:
            freq_cat = "低频(1-2天)"
        elif freq <= 4:
            freq_cat = "中频(3-4天)"
        else:
            freq_cat = "高频(5-7天)"
        freq_diet[freq_cat][r["diet_type"]] += 1
    diet_types_cn = list(DIET_TYPE_CN.values())
    freq_cats = ["低频(1-2天)", "中频(3-4天)", "高频(5-7天)"]
    chart4_freq_diet = {
        "x": freq_cats,
        "series": [],
    }
    for dt in diet_types_cn:
        series_data = [freq_diet[fc].get(dt, 0) for fc in freq_cats]
        chart4_freq_diet["series"].append({"name": dt, "data": series_data})

    # ===== 图6：性别×每日饮水/餐频分组柱状图 =====
    gender_water = defaultdict(list)
    gender_meals = defaultdict(list)
    for r in rows:
        if r["water_intake"]:
            gender_water[r["gender"]].append(r["water_intake"])
        if r["meals_frequency"]:
            gender_meals[r["gender"]].append(r["meals_frequency"])
    chart6_gender_lifestyle = []
    for g in ["男", "女"]:
        chart6_gender_lifestyle.append({
            "name": g,
            "water_avg": round(_mean(gender_water[g]) or 0, 2),
            "meals_avg": round(_mean(gender_meals[g]) or 0, 2),
        })

    # ===== 图7：运动类型×目标肌肉群桑基图 =====
    workout_muscle = defaultdict(Counter)
    for r in rows:
        for m in r["muscles"]:
            workout_muscle[r["workout_type"]][m] += 1
    sankey_node_set = {}
    sankey_link_list = []
    for wt in workout_types_cn:
        if wt not in workout_muscle:
            continue
        sankey_node_set[wt] = True
        for m, cnt in workout_muscle[wt].most_common(5):
            if cnt < 50:
                continue
            sankey_node_set[m] = True
            sankey_link_list.append({"source": wt, "target": m, "value": cnt})
    chart7_sankey = {
        "nodes": [{"name": n} for n in sankey_node_set],
        "links": sankey_link_list,
    }

    # ===== 图8：热量平衡×体脂率散点（按饮食策略着色）=====
    # 相同热量平衡下不同饮食策略的体脂分布，体现“哪种饮食对控制体脂最有效”
    diet_scatter_map = defaultdict(list)
    seen_cf2 = set()
    for r in rows:
        cb = r["cal_balance"]; fp = r["fat_percentage"]; dt = r["diet_type"]
        if cb is not None and fp is not None and dt:
            if -2000 < cb < 2000 and len(seen_cf2) < 600:
                key = (round(cb), round(fp, 1), dt)
                if key not in seen_cf2:
                    seen_cf2.add(key)
                    diet_scatter_map[dt].append([cb, round(fp, 1)])
    chart8_diet_scatter = {dt: pts for dt, pts in diet_scatter_map.items() if len(pts) >= 5}

    # ===== 图9：BMI分类×运动类型消耗效果分组箱线图 =====
    # 不同BMI人群做不同运动的消耗分布，体现“偏瘦/超重人群分别适合什么运动”
    bmi_workout_box = defaultdict(lambda: defaultdict(list))
    for r in rows:
        bmi_cat = _bmi_category_from_value(r.get("bmi"))
        if r["calories_burned"]:
            bmi_workout_box[bmi_cat][r["workout_type"]].append(r["calories_burned"])
    chart9_bmi_workout_box = {"categories": workout_types_cn, "series": []}
    for bmi in bmi_cats:
        boxes = []
        for wt in workout_types_cn:
            stats = _box_stats(bmi_workout_box[bmi].get(wt, []))
            boxes.append(stats if stats else [0, 0, 0, 0, 0])
        chart9_bmi_workout_box["series"].append({"name": bmi, "box": boxes})

    # ===== 图10：30分钟高效燃脂运动横向柱状图 =====
    exercise_burn = defaultdict(list)
    for r in rows:
        if r["burn_30min"] is not None and r["exercise_name"]:
            exercise_burn[r["exercise_name"]].append(r["burn_30min"])
    exercise_avg_burn = []
    for name, vals in exercise_burn.items():
        exercise_avg_burn.append({"name": name, "value": round(_mean(vals) or 0, 1)})
    exercise_avg_burn.sort(key=lambda x: x["value"], reverse=True)
    chart10_top_exercise = exercise_avg_burn[:20]

    result = {
        "chart1_gender_workout": chart1_gender_workout,
        "chart2_bmi_workout": chart2_bmi_workout,
        "chart3_recommend_vs_actual": chart3_recommend_vs_actual,
        "chart4_freq_diet": chart4_freq_diet,
        "chart6_gender_lifestyle": chart6_gender_lifestyle,
        "chart7_sankey": chart7_sankey,
        "chart8_diet_scatter": chart8_diet_scatter,
        "chart9_bmi_workout_box": chart9_bmi_workout_box,
        "chart10_top_exercise": chart10_top_exercise,
    }
    _gym_cache = result
    _gym_cache_time = now
    return result


@app.get("/data/overview")
def data_overview():
    # 总览接口：一次性返回三套分析结果。
    fitness = compute_fitness_metrics()
    diet = compute_diet_metrics()
    gym = compute_gym_plan_metrics()
    return {
        "kpis": {
            "sessions": fitness["kpis"]["sessions"],
            "avg_calories": fitness["kpis"]["avg_calories"],
            "avg_bmi": fitness["kpis"]["avg_bmi"],
            "foods": diet["kpis"]["foods"],
            "avg_energy_kcal": diet["kpis"]["avg_energy_kcal"],
            "avg_health_score": diet["kpis"]["avg_health_score"],
        },
        "fitness": fitness,
        "diet": diet,
        "gym": gym,
    }


@app.get("/data/fitness")
def data_fitness():
    # 训练数据大屏接口。
    return compute_fitness_metrics()


@app.get("/data/diet")
def data_diet():
    # 饮食数据大屏接口。
    return compute_diet_metrics()


@app.get("/data/gym")
def data_gym():
    import time
    now = time.time()
    global _gym_cache, _gym_cache_time

    if _gym_cache is not None and now - _gym_cache_time < 60:
        return _gym_cache

    if _redis_conn is not None:
        try:
            raw = _redis_conn.get(_REDIS_GYM_KEY)
            if raw:
                result = json.loads(raw)
                _gym_cache = result
                _gym_cache_time = now
                return result
        except Exception:
            pass

    if _GYM_DISK_CACHE_FILE.exists():
        try:
            with open(_GYM_DISK_CACHE_FILE, "r", encoding="utf-8") as f:
                result = json.load(f)
            _gym_cache = result
            _gym_cache_time = now
            if _redis_conn is not None:
                try:
                    _redis_conn.setex(_REDIS_GYM_KEY, _REDIS_CACHE_TTL, json.dumps(result, ensure_ascii=False))
                except Exception:
                    pass
            return result
        except Exception:
            pass

    result = compute_gym_plan_metrics()
    _gym_cache = result
    _gym_cache_time = now

    if _redis_conn is not None:
        try:
            _redis_conn.setex(_REDIS_GYM_KEY, _REDIS_CACHE_TTL, json.dumps(result, ensure_ascii=False))
        except Exception:
            pass

    try:
        with open(_GYM_DISK_CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False)
    except Exception:
        pass

    return result
