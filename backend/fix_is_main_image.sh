#!/bin/bash

# 从 PGDATABASE_URL 解析数据库连接信息
PG_URL="${PGDATABASE_URL}"
if [[ "$PG_URL" =~ ^postgresql://([^:]+):([^@]+)@([^:]+):([0-9]+)/([^?]+) ]]; then
    PG_USER="${BASH_REMATCH[1]}"
    PG_PASSWORD="${BASH_REMATCH[2]}"
    PG_HOST="${BASH_REMATCH[3]}"
    PG_PORT="${BASH_REMATCH[4]}"
    PG_DBNAME="${BASH_REMATCH[5]}"
fi

JDBC_URL="jdbc:postgresql://${PG_HOST}:${PG_PORT}/${PG_DBNAME}?sslmode=require"

echo "修复 is_main_image 字段..."
echo "数据库: ${PG_DBNAME}"
echo "主机: ${PG_HOST}"

# 使用 Java 程序来修复数据
cat > /tmp/FixIsMainImage.java << 'EOF'
import java.sql.*;

public class FixIsMainImage {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        String username = args[1];
        String password = args[2];

        Connection conn = DriverManager.getConnection(url, username, password);
        Statement stmt = conn.createStatement();

        // 检查字段类型
        ResultSet rs = stmt.executeQuery(
            "SELECT column_name, data_type FROM information_schema.columns " +
            "WHERE table_name = 'images' AND column_name = 'is_main_image'"
        );
        if (rs.next()) {
            System.out.println("字段类型: " + rs.getString("data_type"));
        }
        rs.close();

        // 统计值分布
        rs = stmt.executeQuery(
            "SELECT is_main_image, COUNT(*) FROM images WHERE album_id = 'album-underwear' GROUP BY is_main_image"
        );
        System.out.println("\nis_main_image 值分布:");
        while (rs.next()) {
            System.out.println("  " + rs.getString(1) + ": " + rs.getInt(2) + " 条");
        }
        rs.close();

        // 修复：将 't'/'f' 转换为 true/false
        int updated = stmt.executeUpdate(
            "UPDATE images SET is_main_image = CASE " +
            "WHEN is_main_image = 't' OR is_main_image = 'true' THEN true " +
            "WHEN is_main_image = 'f' OR is_main_image = 'false' THEN false " +
            "WHEN is_main_image IS NULL THEN false " +
            "ELSE is_main_image END " +
            "WHERE album_id = 'album-underwear' AND is_main_image IN ('t', 'f')"
        );
        System.out.println("\n修复了 " + updated + " 条记录");

        // 再次统计
        rs = stmt.executeQuery(
            "SELECT is_main_image, COUNT(*) FROM images WHERE album_id = 'album-underwear' GROUP BY is_main_image"
        );
        System.out.println("\n修复后的值分布:");
        while (rs.next()) {
            System.out.println("  " + rs.getString(1) + ": " + rs.getInt(2) + " 条");
        }
        rs.close();

        // 统计主图数量
        rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM images WHERE album_id = 'album-underwear' AND is_main_image = true"
        );
        if (rs.next()) {
            System.out.println("\n主图总数 (is_main_image = true): " + rs.getInt(1));
        }
        rs.close();

        stmt.close();
        conn.close();
    }
}
EOF

# 编译并运行
cd /tmp
javac FixIsMainImage.java
java -cp . FixIsMainImage "$JDBC_URL" "$PG_USER" "$PG_PASSWORD"

echo "\n修复完成！"
