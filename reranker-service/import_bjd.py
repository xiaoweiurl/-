"""
内衣货号工序工价查询导入脚本
表名: order_gongxu_price
用法: python import_gongxu_price.py
数据文件: data_price.json（与脚本同目录）
"""

import json
import psycopg2
from psycopg2.extras import execute_values


# ==================== 数据库配置（按需修改）====================
DB_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'user': 'postgres',
    'password': '13369516',
    'database': 'image_management'
}

JSON_FILE = 'data.json'

# 字段列表
FIELDS = ['hhname', 'wtname', 'jsprice', 'price', 'tempworker_price', 'state']

# 建表 SQL（无主键，直接插入）
CREATE_TABLE_SQL = """
                   CREATE TABLE IF NOT EXISTS order_gongxu_price (
                                                                     hhname VARCHAR(100),                -- 货号
                       wtname VARCHAR(100),                 -- 工序
                       jsprice DECIMAL(10,4),               -- 技术工价
                       price DECIMAL(10,4),                 -- 工价
                       tempworker_price DECIMAL(10,4),      -- 临时工价
                       remarkgz TEXT,                       -- 工价备注
                       state VARCHAR(20)                    -- 审核状态 未审核 / 已审核
                       )
                   """

# 字段注释
COLUMN_COMMENTS_SQL = """
COMMENT ON COLUMN order_gongxu_price.hhname IS '货号';
COMMENT ON COLUMN order_gongxu_price.wtname IS '工序';
COMMENT ON COLUMN order_gongxu_price.jsprice IS '技术工价';
COMMENT ON COLUMN order_gongxu_price.price IS '工价';
COMMENT ON COLUMN order_gongxu_price.tempworker_price IS '临时工价';
COMMENT ON COLUMN order_gongxu_price.remarkgz IS '工价备注';
COMMENT ON COLUMN order_gongxu_price.state IS '审核状态 未审核 / 已审核';
"""

# 插入 SQL（无主键，直接插入）
INSERT_SQL = f"""
INSERT INTO order_gongxu_price ({', '.join(FIELDS)})
VALUES %s
"""


def safe_value(val, field):
    """空字符串转 None，数值类型转换"""
    if val == '' or val is None:
        return None
    # 小数类型
    if field in ('jsprice', 'price', 'tempworker_price'):
        try:
            return float(val)
        except (ValueError, TypeError):
            return None
    return val


def load_json(filepath):
    """读取 JSON，兼容纯数组和接口返回格式"""
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    if isinstance(data, dict):
        if 'Result' in data:
            return data['Result']
        elif 'result' in data:
            return data['result']
    if isinstance(data, list):
        return data
    raise ValueError("无法识别的 JSON 格式")


def main():
    print(f"正在读取文件：{JSON_FILE}")
    records = load_json(JSON_FILE)
    print(f"共读取到 {len(records)} 条数据")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        print("正在创建表和字段注释...")
        cursor.execute(CREATE_TABLE_SQL)
        cursor.execute(COLUMN_COMMENTS_SQL)
        conn.commit()

        rows = []
        for record in records:
            row = tuple(safe_value(record.get(field), field) for field in FIELDS)
            rows.append(row)

        print("正在导入数据...")
        execute_values(cursor, INSERT_SQL, rows, page_size=5000)
        conn.commit()
        print(f"✅ 导入完成，共处理 {len(rows)} 条")

    except Exception as e:
        conn.rollback()
        print(f"导入失败：{e}")
        raise
    finally:
        cursor.close()
        conn.close()
        print("数据库连接已关闭")


if __name__ == '__main__':
    main()
