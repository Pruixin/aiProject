import csv
import random
import os
from datetime import date, datetime, timedelta

import pymysql

script_dir = os.path.dirname(os.path.abspath(__file__))

conn = pymysql.connect(
    host="localhost", port=3306, user="root", password="mysql",
    database="yunfan_jicang", charset="utf8mb4"
)
cursor = conn.cursor()
cursor.execute("SELECT dict_code, dict_name, parent_code FROM dict WHERE dict_type = 'region' AND deleted = 0 ORDER BY id")
region_rows = cursor.fetchall()
cursor.close()
conn.close()

print(f"从数据库dict表查询到地区数据: {len(region_rows)} 条")

parent_codes = {row[2] for row in region_rows if row[2]}
leaf_codes = [row[0] for row in region_rows if row[0] not in parent_codes]
if not leaf_codes:
    leaf_codes = [row[0] for row in region_rows]

print(f"叶子节点(市/区级): {len(leaf_codes)}")

social_goals = ["增肌塑形", "减脂燃卡", "跑步进阶", "力量提升", "规律打卡", "饮食管理"]

avatars = [
    "https://api.dicebear.com/7.x/avataaars/svg?seed=",
    "https://api.dicebear.com/7.x/bottts/svg?seed=",
    "https://api.dicebear.com/7.x/adventurer/svg?seed=",
]

phone_prefixes = ["13", "14", "15", "16", "17", "18", "19"]
prefix_map = {
    "13": "0123456789",
    "14": "01456879",
    "15": "0123456789",
    "16": "2567",
    "17": "012345678",
    "18": "0123456789",
    "19": "012356789",
}


def generate_phone():
    prefix = random.choice(phone_prefixes)
    second = random.choice(list(prefix_map[prefix]))
    suffix = "".join([str(random.randint(0, 9)) for _ in range(8)])
    return prefix + second + suffix


def generate_password():
    letter = random.choice("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
    length = random.randint(5, 17)
    chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"
    suffix = "".join(random.choices(chars, k=length))
    return letter + suffix


data = []
used_phones = set()

for i in range(1, 101):
    phone = generate_phone()
    while phone in used_phones:
        phone = generate_phone()
    used_phones.add(phone)

    data.append([
        i,
        phone,
        generate_password(),
        random.randint(0, 1),
        random.choice(avatars) + phone,
        random.choice(leaf_codes),
        random.choice(social_goals),
        1,
    ])

csv_path = os.path.join(script_dir, "test_register_data.csv")
with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
    writer = csv.writer(f)
    writer.writerow(["序号", "phone", "password", "sex", "avatar", "location(dict_code)", "socialGoal", "enable"])
    writer.writerows(data)

print(f"注册接口测试数据: {len(data)} 条  -> {csv_path}")
print(f"location样例: {data[0][5]}, {data[1][5]}, {data[2][5]} ...")
print(f"socialGoal样例: {data[0][6]}, {data[1][6]}, {data[2][6]} ...")