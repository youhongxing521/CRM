package com.cem;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
interface OrganizationMapper {
    record Department(Long id, String name, String description, long employeeCount) {}
    record Role(Long id, String code, String name, String description) {}
    record Account(String username, String displayName, Long departmentId, boolean enabled, long sessionVersion) {}
    record Credentials(String username, String passwordHash, long sessionVersion) {}
    record Membership(String username, Long roleId) {}
    record Grant(Long roleId, String permission) {}

    String ACCOUNTS = """
        SELECT e.username, u.display_name, e.department_id, e.enabled, e.session_version
        FROM cem_employee e JOIN cem_user u ON u.username=e.username
        """;

    @Select(ACCOUNTS + " WHERE e.username=#{username}")
    Account account(String username);

    @Select("""
        SELECT e.username, u.password_hash, e.session_version FROM cem_employee e
        JOIN cem_user u ON u.username=e.username WHERE e.username=#{username} AND e.enabled=TRUE
        """)
    Credentials credentials(String username);

    @Select(ACCOUNTS + " ORDER BY e.username <> 'admin', e.username")
    List<Account> accounts();

    @Select("SELECT username FROM cem_employee WHERE username=#{username} FOR UPDATE")
    String lockAccount(String username);

    @Select("""
        SELECT COUNT(*) FROM cem_employee_role m JOIN cem_role r ON r.id=m.role_id
        WHERE m.username=#{username} AND r.code='system-admin'
        """)
    int isAdministrator(String username);

    @Select("""
        SELECT DISTINCT p.permission FROM cem_employee_role m
        JOIN cem_role_permission p ON p.role_id=m.role_id WHERE m.username=#{username}
        """)
    List<String> permissions(String username);

    @Insert("INSERT IGNORE INTO cem_employee(username) SELECT username FROM cem_user")
    void registerExistingAccounts();

    @Insert("INSERT IGNORE INTO cem_employee(username) VALUES(#{username})")
    void registerAccount(String username);

    @Insert("INSERT INTO cem_user(username,password_hash,display_name) VALUES(#{username},#{hash},#{name})")
    void insertUser(String username, String hash, String name);

    @Update("UPDATE cem_user SET display_name=#{name} WHERE username=#{username}")
    void rename(String username, String name);

    @Update("""
        UPDATE cem_employee
        SET session_version=session_version + (enabled <> #{enabled}), department_id=#{departmentId}, enabled=#{enabled}
        WHERE username=#{username}
        """)
    void updateEmployee(String username, Long departmentId, boolean enabled);

    @Update("UPDATE cem_employee SET session_version=session_version+1 WHERE username=#{username}")
    void revokeSessions(String username);

    @Select("""
        SELECT d.id,d.name,d.description,(SELECT COUNT(*) FROM cem_employee e WHERE e.department_id=d.id) AS employee_count
        FROM cem_department d ORDER BY d.id
        """)
    List<Department> departments();

    @Select("SELECT id FROM cem_department WHERE id=#{id} FOR UPDATE")
    Long lockDepartment(long id);

    @Select("SELECT id FROM cem_department WHERE name=#{name}")
    Long departmentId(String name);

    @Insert("INSERT INTO cem_department(name,description) VALUES(#{name},#{description})")
    void insertDepartment(String name, String description);

    @Update("UPDATE cem_department SET name=#{name},description=#{description} WHERE id=#{id}")
    void updateDepartment(long id, String name, String description);

    @Select("SELECT COUNT(*) FROM cem_employee WHERE department_id=#{id}")
    long departmentMembers(long id);

    @Delete("DELETE FROM cem_department WHERE id=#{id}")
    void deleteDepartment(long id);

    @Select("SELECT id,code,name,description FROM cem_role ORDER BY code <> 'system-admin',id")
    List<Role> roles();

    @Select("SELECT id,code,name,description FROM cem_role WHERE id=#{id} FOR UPDATE")
    Role lockRole(long id);

    @Select("SELECT id FROM cem_role WHERE code=#{code}")
    Long roleId(String code);

    @Insert("INSERT INTO cem_role(code,name,description) VALUES(#{code},#{name},#{description})")
    void insertRole(String code, String name, String description);

    @Update("UPDATE cem_role SET name=#{name},description=#{description} WHERE id=#{id}")
    void updateRole(long id, String name, String description);

    @Select("SELECT COUNT(*) FROM cem_employee_role WHERE role_id=#{id}")
    long roleMembers(long id);

    @Delete("DELETE FROM cem_role WHERE id=#{id}")
    void deleteRole(long id);

    @Select("SELECT role_id,permission FROM cem_role_permission ORDER BY role_id,permission")
    List<Grant> grants();

    @Delete("DELETE FROM cem_role_permission WHERE role_id=#{id}")
    void clearPermissions(long id);

    @Insert("INSERT INTO cem_role_permission(role_id,permission) VALUES(#{id},#{permission})")
    void grant(long id, String permission);

    @Select("SELECT username,role_id FROM cem_employee_role ORDER BY username,role_id")
    List<Membership> memberships();

    @Delete("DELETE FROM cem_employee_role WHERE username=#{username}")
    void clearRoles(String username);

    @Insert("INSERT IGNORE INTO cem_employee_role(username,role_id) VALUES(#{username},#{roleId})")
    void assign(String username, long roleId);
}
