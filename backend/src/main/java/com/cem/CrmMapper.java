package com.cem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CrmMapper {
    String COLUMNS = "SELECT r.*, parent.name AS related_name FROM cem_record r LEFT JOIN cem_record parent ON parent.id = r.related_id ";
    String FILTER = """
        WHERE r.kind = #{kind}
        <if test="status != null and status != ''">AND r.status = #{status}</if>
        <if test="owner != null and owner != ''">AND r.owner = #{owner}</if>
        <if test="related != null">AND r.related_id = #{related}</if>
        <if test="keyword != null and keyword != ''">
            AND (LOCATE(#{keyword}, r.name) &gt; 0 OR LOCATE(#{keyword}, r.owner) &gt; 0
            OR LOCATE(#{keyword}, CAST(r.details AS CHAR)) &gt; 0)
        </if>
        """;

    @Select("<script>" + COLUMNS + FILTER + " ORDER BY r.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<CrmRecord> list(String kind, String status, String owner, Long related, String keyword, int size, long offset);

    @Select("<script>SELECT COUNT(*) FROM cem_record r " + FILTER + "</script>")
    long count(String kind, String status, String owner, Long related, String keyword);

    @Select(COLUMNS + " WHERE r.id = #{id}")
    CrmRecord get(long id);

    @Select("SELECT * FROM cem_record WHERE id = #{id} FOR UPDATE")
    CrmRecord lock(long id);

    @Insert("""
        INSERT INTO cem_record(kind, name, status, owner, related_id, amount, due_date, details)
        VALUES(#{kind}, #{name}, #{status}, #{owner}, #{relatedId}, #{amount}, #{dueDate}, #{details})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CrmRecord row);

    @Update("""
        UPDATE cem_record SET kind=#{kind}, name=#{name}, status=#{status}, owner=#{owner}, related_id=#{relatedId},
        amount=#{amount}, due_date=#{dueDate}, details=#{details} WHERE id=#{id}
        """)
    void update(CrmRecord row);

    @Delete("DELETE FROM cem_record WHERE id=#{id}")
    void delete(long id);

    @Select("SELECT COUNT(*) FROM cem_record WHERE related_id=#{id}")
    long dependents(long id);

    @Select("""
        SELECT COALESCE(SUM(amount), 0) FROM cem_record
        WHERE kind=#{kind} AND related_id=#{relatedId} AND status IN ('审核中','已通过')
        AND (#{exceptId} IS NULL OR id <> #{exceptId})
        """)
    BigDecimal committedAmount(String kind, long relatedId, Long exceptId);

    @Select("""
        SELECT kind, status, COUNT(*) AS total, COALESCE(SUM(amount),0) AS amount
        FROM cem_record GROUP BY kind,status
        """)
    List<Map<String,Object>> totals();

    @Select("""
        SELECT kind, DATE_FORMAT(COALESCE(due_date, DATE(created_at)), '%Y-%m') AS month,
               COALESCE(SUM(amount),0) AS amount
        FROM cem_record
        WHERE kind IN ('contracts','payments') AND status='已通过'
          AND COALESCE(due_date, DATE(created_at)) >= #{start}
          AND COALESCE(due_date, DATE(created_at)) < #{end}
        GROUP BY kind, month
        """)
    List<Map<String,Object>> trend(String start, String end);

    @Select("""
        SELECT COUNT(*) AS total, AVG(CAST(JSON_UNQUOTE(JSON_EXTRACT(details,'$.score')) AS DECIMAL(5,2))) AS score
        FROM cem_record WHERE kind='feedback'
        """)
    Map<String,Object> experience();

    @Select(COLUMNS + " WHERE r.kind='tasks' AND r.status='待处理' ORDER BY r.due_date, r.id DESC LIMIT 5")
    List<CrmRecord> tasks();

    @Select("<script>" + COLUMNS + """
        WHERE r.kind NOT IN ('tasks','approvals','settings') AND r.kind IN
        <foreach collection="kinds" item="kind" open="(" close=")" separator=",">#{kind}</foreach>
        ORDER BY r.updated_at DESC,r.id DESC LIMIT 5
        </script>
        """)
    List<CrmRecord> recent(@Param("kinds") Set<String> kinds);

    @Select("SELECT username, password_hash AS passwordHash, display_name AS displayName FROM cem_user WHERE username=#{username}")
    Map<String,String> user(String username);

    @Insert("INSERT IGNORE INTO cem_user(username,password_hash,display_name) VALUES(#{username},#{passwordHash},#{displayName})")
    void insertUser(String username, String passwordHash, String displayName);

    @Update("UPDATE cem_user SET password_hash=#{passwordHash} WHERE username=#{username}")
    void changePassword(String username, String passwordHash);

    @Insert("INSERT IGNORE INTO cem_initialization(name) VALUES(#{name})")
    int initialize(String name);
}
