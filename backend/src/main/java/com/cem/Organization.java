package com.cem;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Organization {
    private static final String ADMIN = "system-admin";
    private static final String DEMO = "demo-workspace";
    private static final Set<String> WORKFLOW_MODULES = Set.of(
            "tasks", "calendar", "leads", "lead-pool", "customers", "customer-pool", "approvals");

    record Permission(String code, String label) {}
    record PermissionModule(String key, String group, String label, String related, List<Permission> actions) {}
    record RoleView(Long id, String name, String description, boolean builtIn, List<String> permissions) {}
    record EmployeeView(String username, String displayName, Long departmentId, boolean enabled,
                        boolean protectedAccount, boolean demo, List<Long> roleIds) {}
    record Snapshot(List<OrganizationMapper.Department> departments, List<RoleView> roles,
                    List<EmployeeView> employees, List<PermissionModule> permissionModules) {}
    record DepartmentInput(String name, String description) {}
    record RoleInput(String name, String description, List<String> permissions) {}
    record EmployeeInput(String username, String displayName, Long departmentId, Boolean enabled,
                         List<Long> roleIds, String password) {}
    record UserAccess(OrganizationMapper.Account account, boolean administrator, Set<String> permissions) {
        boolean can(String kind, String operation) {
            return administrator || permissions.contains(kind + ":" + operation);
        }
    }

    private final OrganizationMapper org;
    private final CrmMapper db;
    private final List<PermissionModule> modules;
    private final Map<String, PermissionModule> permissionIndex = new LinkedHashMap<>();

    Organization(OrganizationMapper org, CrmMapper db, Workspace workspace) {
        this.org = org;
        this.db = db;
        var catalog = new ArrayList<PermissionModule>();
        for (Workspace.Module module : workspace.catalog()) {
            String kind = module.key();
            var actions = new ArrayList<Permission>();
            actions.add(new Permission(kind + ":read", "查看"));
            if (!kind.equals("approvals")) {
                actions.add(new Permission(kind + ":write", "新建与编辑"));
                actions.add(new Permission(kind + ":delete", "删除"));
            }
            if (WORKFLOW_MODULES.contains(kind) || Workspace.APPROVABLE.contains(kind)) {
                actions.add(new Permission(kind + ":operate", kind.equals("approvals") ? "审批处理" : "业务操作"));
            }
            catalog.add(new PermissionModule(kind, module.group(), module.label(), module.related(), List.copyOf(actions)));
        }
        catalog.add(new PermissionModule("reports", "数据", "数据报表", null,
                List.of(new Permission("reports:read", "查看"))));
        modules = List.copyOf(catalog);
        for (PermissionModule module : modules) {
            for (Permission permission : module.actions()) permissionIndex.put(permission.code(), module);
        }
    }

    void installDefaults(boolean demoEnabled) {
        if (db.initialize("organization-v1") == 1) {
            org.registerExistingAccounts();
            seedDepartment("销售部", "客户开发、报价、跟单与售后受理");
            seedDepartment("供应链部", "采购、仓储、发货与交期协调");
            seedDepartment("生产部", "自产件加工、组装与包装");
            seedDepartment("质量技术部", "样品确认、质量检验与异常处理");
            seedDepartment("财务行政部", "应收对账、收款、开票及行政");
            seedRole(ADMIN, "系统管理员", "拥有全部业务及系统管理权限", List.of());
            org.assign("admin", org.roleId(ADMIN));
            seedRole("sales", "销售", "维护线索、客户、商机及销售单据", merge(
                    operations("read", "products", "payments", "invoices", "announcements"),
                    operations("write", "tasks", "calendar", "leads", "lead-pool", "customers", "customer-pool",
                            "contacts", "followups", "opportunities", "contracts", "expenses", "feedback", "logs"),
                    operations("operate", "tasks", "calendar", "leads", "lead-pool", "customers",
                            "customer-pool", "contracts", "expenses")));
            seedRole("supply-chain", "供应链", "查看供货单据，维护商品资料", merge(
                    operations("read", "contracts", "customers", "announcements"),
                    operations("write", "products", "tasks", "logs"), operations("operate", "tasks")));
            seedRole("production", "生产", "查看商品，维护任务与工作日志", merge(
                    operations("read", "products", "announcements"), operations("write", "tasks", "logs"),
                    operations("operate", "tasks")));
            seedRole("quality", "质量技术", "维护客户反馈和质量相关跟进", merge(
                    operations("read", "products", "announcements"), operations("write", "feedback", "followups", "tasks", "logs"),
                    operations("operate", "tasks")));
            seedRole("finance", "财务", "查看合同及报表，管理财务单据和业务审批", merge(
                    operations("read", "contracts", "customers", "reports", "announcements"),
                    operations("write", "payments", "invoices", "expenses", "targets", "logs"),
                    operations("operate", "payments", "invoices", "expenses", "approvals")));
        }
        if (demoEnabled && db.initialize("organization-demo-v1") == 1) {
            seedRole(DEMO, "业务演示", "演示账号的业务权限", permissionIndex.keySet());
            List<String> departments = List.of("销售部", "质量技术部", "供应链部");
            for (int i = 0; i < DemoData.ACCOUNTS.size(); i++) {
                String username = DemoData.ACCOUNTS.get(i).username();
                org.registerAccount(username);
                org.updateEmployee(username, org.departmentId(departments.get(i)), true);
                org.assign(username, org.roleId(DEMO));
            }
        }
    }

    @Transactional(readOnly = true)
    public UserAccess access(String username) {
        var account = org.account(username);
        if (account == null || !account.enabled()) return null;
        boolean admin = org.isAdministrator(account.username()) > 0;
        var permissions = admin ? permissionIndex.keySet() : org.permissions(account.username());
        return new UserAccess(account, admin, Set.copyOf(permissions));
    }

    @Transactional(readOnly = true)
    public UserAccess authenticate(String username, String password, boolean demoEnabled) {
        if (username == null || username.length() > 64 || !Api.validPassword(password)) return null;
        var credentials = org.credentials(username.trim());
        if (credentials == null || (!demoEnabled && DemoData.isDemoAccount(credentials.username()))
                || !Api.PASSWORDS.matches(password, credentials.passwordHash())) return null;
        var access = access(credentials.username());
        return access != null && access.account().sessionVersion() == credentials.sessionVersion() ? access : null;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        var memberships = org.memberships().stream().collect(Collectors.groupingBy(OrganizationMapper.Membership::username,
                Collectors.mapping(OrganizationMapper.Membership::roleId, Collectors.toList())));
        var grants = org.grants().stream().collect(Collectors.groupingBy(OrganizationMapper.Grant::roleId,
                Collectors.mapping(OrganizationMapper.Grant::permission, Collectors.toList())));
        var roles = org.roles().stream().map(role -> new RoleView(role.id(), role.name(), role.description(),
                ADMIN.equals(role.code()), ADMIN.equals(role.code()) ? List.copyOf(permissionIndex.keySet())
                        : grants.getOrDefault(role.id(), List.of()))).toList();
        var employees = org.accounts().stream().map(account -> new EmployeeView(account.username(), account.displayName(),
                account.departmentId(), account.enabled(), "admin".equals(account.username()),
                DemoData.isDemoAccount(account.username()), memberships.getOrDefault(account.username(), List.of()))).toList();
        return new Snapshot(org.departments(), roles, employees, modules);
    }

    @Transactional
    public void saveDepartment(Long id, DepartmentInput input) {
        String name = Workspace.field(input.name(), "部门名称", 80, true);
        String description = Workspace.field(input.description(), "部门说明", 400, false);
        if (id == null) org.insertDepartment(name, description);
        else {
            requireDepartment(id);
            org.updateDepartment(id, name, description);
        }
    }

    @Transactional
    public void deleteDepartment(long id) {
        requireDepartment(id);
        if (org.departmentMembers(id) > 0) throw conflict("部门还有员工，请先调整员工所属部门");
        org.deleteDepartment(id);
    }

    @Transactional
    public void saveRole(Long id, RoleInput input) {
        String name = Workspace.field(input.name(), "角色名称", 80, true);
        String description = Workspace.field(input.description(), "角色说明", 400, false);
        Set<String> permissions = normalize(input.permissions());
        if (id == null) {
            String code = UUID.randomUUID().toString();
            org.insertRole(code, name, description);
            id = org.roleId(code);
        } else {
            requireEditableRole(id);
            org.updateRole(id, name, description);
            org.clearPermissions(id);
        }
        for (String permission : permissions) org.grant(id, permission);
    }

    @Transactional
    public void deleteRole(long id) {
        requireEditableRole(id);
        if (org.roleMembers(id) > 0) throw conflict("角色已分配给员工，请先取消分配");
        org.deleteRole(id);
    }

    @Transactional
    public void saveEmployee(String target, EmployeeInput input, String actor) {
        String username = target == null ? Workspace.field(input.username(), "登录账号", 64, true).toLowerCase(Locale.ROOT)
                : target.toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9_.-]{2,63}")) {
            throw Workspace.invalid("登录账号须为 3 至 64 位字母、数字或 . _ -，并以字母或数字开头");
        }
        if (target == null && (username.equals("admin") || DemoData.isDemoAccount(username))) throw conflict("登录账号已保留");
        if (target != null) requireEditableEmployee(username, actor);
        String name = Workspace.field(input.displayName(), "员工姓名", 80, true);
        if (input.enabled() == null) throw Workspace.invalid("请选择账号状态");
        if (input.departmentId() != null) requireDepartment(input.departmentId());
        Set<Long> roles = new TreeSet<>();
        if (input.roleIds() != null) {
            for (Long roleId : input.roleIds()) {
                if (roleId == null || roleId <= 0) throw Workspace.invalid("角色无效");
                roles.add(roleId);
            }
        }
        for (long roleId : roles) {
            if (org.lockRole(roleId) == null) throw Workspace.invalid("所选角色不存在");
        }
        if (DemoData.isDemoAccount(username) && roles.contains(org.roleId(ADMIN))) {
            throw Workspace.invalid("快捷登录账号不可分配系统管理员角色");
        }
        if (target == null) {
            org.insertUser(username, hashPassword(input.password()), name);
            org.registerAccount(username);
        } else {
            org.rename(username, name);
            org.clearRoles(username);
        }
        org.updateEmployee(username, input.departmentId(), input.enabled());
        for (long roleId : roles) org.assign(username, roleId);
    }

    @Transactional
    public void resetPassword(String username, String newPassword, String actor) {
        username = username.toLowerCase(Locale.ROOT);
        requireEditableEmployee(username, actor);
        rejectDemoPasswordChange(username);
        db.changePassword(username, hashPassword(newPassword));
        org.revokeSessions(username);
    }

    @Transactional
    public long changeOwnPassword(String username, String current, String next) {
        if (org.lockAccount(username) == null) throw missing("账号不存在");
        rejectDemoPasswordChange(username);
        var user = db.user(username);
        if (!Api.validPassword(current) || !Api.PASSWORDS.matches(current, user.get("passwordHash"))) {
            throw Workspace.invalid("原密码错误");
        }
        db.changePassword(username, hashPassword(next));
        org.revokeSessions(username);
        return org.account(username).sessionVersion();
    }

    private void seedDepartment(String name, String description) {
        if (org.departmentId(name) == null) org.insertDepartment(name, description);
    }

    private void seedRole(String code, String name, String description, Collection<String> permissions) {
        org.insertRole(code, name, description);
        long id = org.roleId(code);
        for (String permission : normalize(permissions)) org.grant(id, permission);
    }

    private Set<String> normalize(Collection<String> permissions) {
        Set<String> result = new LinkedHashSet<>();
        if (permissions == null) return result;
        for (String code : permissions) {
            PermissionModule module = permissionIndex.get(code);
            if (module == null) throw Workspace.invalid("请选择有效权限");
            result.add(module.key() + ":read");
            result.add(code);
            if (code.endsWith(":write") && module.related() != null) result.add(module.related() + ":read");
        }
        return result;
    }

    private void requireDepartment(long id) {
        if (org.lockDepartment(id) == null) throw missing("部门不存在");
    }

    private void requireEditableRole(long id) {
        var role = org.lockRole(id);
        if (role == null) throw missing("角色不存在");
        if (ADMIN.equals(role.code())) throw Workspace.invalid("系统管理员角色不可修改或删除");
    }

    private void requireEditableEmployee(String username, String actor) {
        if ("admin".equals(username) || username.equals(actor)) throw Workspace.invalid("当前管理员或内置管理员账号不可在这里修改");
        if (org.lockAccount(username) == null) throw missing("员工不存在");
    }

    private void rejectDemoPasswordChange(String username) {
        if (DemoData.isDemoAccount(username)) throw Workspace.error(HttpStatus.FORBIDDEN, "演示账号使用固定密码");
    }

    private String hashPassword(String password) {
        if (!Api.validPassword(password) || password.length() < 8) {
            throw Workspace.invalid("密码至少 8 个字符且不超过 72 字节");
        }
        return Api.PASSWORDS.encode(password);
    }

    private static List<String> operations(String operation, String... kinds) {
        return Arrays.stream(kinds).map(kind -> kind + ":" + operation).toList();
    }

    @SafeVarargs
    private static List<String> merge(List<String>... permissions) {
        return Arrays.stream(permissions).flatMap(Collection::stream).toList();
    }

    private static RuntimeException missing(String detail) { return Workspace.error(HttpStatus.NOT_FOUND, detail); }
    private static RuntimeException conflict(String detail) { return Workspace.error(HttpStatus.CONFLICT, detail); }
}
