package com.cem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class Api {
    private final Workspace workspace;
    private final Organization organization;
    private final boolean demoEnabled;
    static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();

    public record Login(String username, String password) {}
    public record DemoLogin(String username) {}
    public record PasswordChange(String currentPassword, String newPassword) {}
    public record Action(String action, String comment) {}
    public record Identity(String username, String displayName, boolean administrator, Set<String> permissions) {}
    private record Session(String username, long version) implements java.io.Serializable {}

    public Api(Workspace workspace, Organization organization, @Value("${cem.demo-data}") boolean demoEnabled) {
        this.workspace = workspace;
        this.organization = organization;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status", "up"); }

    @GetMapping("/demo-accounts")
    public List<DemoData.DemoAccount> demoAccounts() {
        if (!demoEnabled) return List.of();
        return DemoData.ACCOUNTS.stream().filter(account -> organization.access(account.username()) != null).toList();
    }

    @PostMapping("/demo-accounts/login")
    public Identity demoLogin(@RequestBody DemoLogin input, HttpServletRequest request) {
        if (!demoEnabled || !DemoData.isDemoAccount(input.username())) {
            throw Workspace.error(HttpStatus.FORBIDDEN, "该演示账号不可用");
        }
        return login(new Login(input.username(), DemoData.DEMO_PASSWORD), request);
    }

    @PostMapping("/login")
    public Identity login(@RequestBody Login input, HttpServletRequest request) {
        var access = organization.authenticate(input.username(), input.password(), demoEnabled);
        if (access == null) throw Workspace.error(HttpStatus.UNAUTHORIZED, "账号或密码错误，或账号已停用");
        var old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession().setAttribute("identity", new Session(access.account().username(), access.account().sessionVersion()));
        return identity(access);
    }

    @GetMapping("/me")
    public Identity me(HttpServletRequest request) { return identity(access(request)); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) { request.getSession().invalidate(); }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestBody PasswordChange input, HttpServletRequest request) {
        String username = access(request).account().username();
        long version = organization.changeOwnPassword(username, input.currentPassword(), input.newPassword());
        request.getSession().setAttribute("identity", new Session(username, version));
    }

    @GetMapping("/modules")
    public Object catalog(HttpServletRequest request) {
        var access = access(request);
        return workspace.catalog().stream().filter(module -> access.can(module.key(), "read")).toList();
    }

    @GetMapping("/dashboard")
    public Object dashboard(@RequestParam(required = false) Integer year, HttpServletRequest request) {
        return dashboardData(year, access(request));
    }

    @GetMapping("/reports")
    public Object reports(@RequestParam(required = false) Integer year, HttpServletRequest request) {
        return dashboardData(year, require(request, "reports", "read"));
    }

    private Object dashboardData(Integer year, Organization.UserAccess access) {
        var kinds = workspace.catalog().stream().map(Workspace.Module::key).filter(kind -> access.can(kind, "read"))
                .collect(Collectors.toSet());
        return workspace.dashboard(year == null ? Year.now().getValue() : year, kinds);
    }

    @GetMapping("/records/{kind}")
    public Workspace.Page page(@PathVariable String kind, @RequestParam(required = false) String status,
            @RequestParam(required = false) String owner, @RequestParam(required = false) Long related,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size, HttpServletRequest request) {
        require(request, kind, "read");
        return workspace.page(kind, status, owner, related, keyword, page, size);
    }

    @GetMapping("/records/{kind}/{id}")
    public Workspace.Row get(@PathVariable String kind, @PathVariable long id, HttpServletRequest request) {
        require(request, kind, "read");
        return workspace.get(kind, id);
    }

    @PostMapping("/records/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    public Workspace.Row create(@PathVariable String kind, @RequestBody Workspace.Input data, HttpServletRequest request) {
        var access = require(request, kind, "write");
        return workspace.save(kind, null, data, access.account().displayName());
    }

    @PutMapping("/records/{kind}/{id}")
    public Workspace.Row update(@PathVariable String kind, @PathVariable long id, @RequestBody Workspace.Input data,
                                HttpServletRequest request) {
        var access = require(request, kind, "write");
        return workspace.save(kind, id, data, access.account().displayName());
    }

    @DeleteMapping("/records/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String kind, @PathVariable long id, HttpServletRequest request) {
        require(request, kind, "delete");
        workspace.delete(kind, id);
    }

    @PostMapping("/records/{kind}/{id}/actions")
    public Workspace.Row action(@PathVariable String kind, @PathVariable long id, @RequestBody Action input,
                                HttpServletRequest request) {
        var access = require(request, kind, "operate");
        if (input.action() == null) throw Workspace.invalid("操作无效");
        return workspace.action(kind, id, input.action(), input.comment(), access.account().displayName());
    }

    private static Identity identity(Organization.UserAccess access) {
        return new Identity(access.account().username(), access.account().displayName(),
                access.administrator(), access.permissions());
    }

    static Organization.UserAccess access(HttpServletRequest request) {
        return (Organization.UserAccess) request.getAttribute("cem-access");
    }

    private static Organization.UserAccess require(HttpServletRequest request, String kind, String operation) {
        var access = access(request);
        if (!access.can(kind, operation)) throw Workspace.error(HttpStatus.FORBIDDEN, "没有该操作权限");
        return access;
    }

    static boolean validPassword(String value) {
        return value != null && !value.isEmpty() && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Configuration
    static class ApiAuthentication implements WebMvcConfigurer {
        private static final List<String> PUBLIC_PATHS = List.of(
                "/api/login", "/api/health", "/api/demo-accounts", "/api/demo-accounts/login");
        private final Organization organization;

        ApiAuthentication(Organization organization) { this.organization = organization; }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                    String path = request.getServletPath();
                    if (!request.getMethod().equals("GET") && !"1".equals(request.getHeader("X-Cem-Request"))) {
                        throw Workspace.error(HttpStatus.FORBIDDEN, "请求校验失败");
                    }
                    if (PUBLIC_PATHS.contains(path)) return true;
                    var session = request.getSession(false);
                    if (session == null || !(session.getAttribute("identity") instanceof Session identity)) {
                        throw Workspace.error(HttpStatus.UNAUTHORIZED, "请先登录");
                    }
                    var access = organization.access(identity.username());
                    if (access == null || access.account().sessionVersion() != identity.version()) {
                        session.invalidate();
                        throw Workspace.error(HttpStatus.UNAUTHORIZED, "账号已停用或会话已失效，请重新登录");
                    }
                    request.setAttribute("cem-access", access);
                    return true;
                }
            }).addPathPatterns("/api/**");
        }
    }
}
