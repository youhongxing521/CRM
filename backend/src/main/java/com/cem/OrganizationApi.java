package com.cem;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization")
class OrganizationApi {
    private final Organization organization;

    OrganizationApi(Organization organization) { this.organization = organization; }

    record PasswordReset(String password) {}

    @GetMapping
    public Organization.Snapshot snapshot(HttpServletRequest request) {
        requireAdministrator(request);
        return organization.snapshot();
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createEmployee(@RequestBody Organization.EmployeeInput input, HttpServletRequest request) {
        organization.saveEmployee(null, input, requireAdministrator(request));
    }

    @PutMapping("/employees/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateEmployee(@PathVariable String username, @RequestBody Organization.EmployeeInput input,
                               HttpServletRequest request) {
        organization.saveEmployee(username, input, requireAdministrator(request));
    }

    @PutMapping("/employees/{username}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable String username, @RequestBody PasswordReset input, HttpServletRequest request) {
        organization.resetPassword(username, input.password(), requireAdministrator(request));
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createDepartment(@RequestBody Organization.DepartmentInput input, HttpServletRequest request) {
        requireAdministrator(request);
        organization.saveDepartment(null, input);
    }

    @PutMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDepartment(@PathVariable long id, @RequestBody Organization.DepartmentInput input,
                                 HttpServletRequest request) {
        requireAdministrator(request);
        organization.saveDepartment(id, input);
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDepartment(@PathVariable long id, HttpServletRequest request) {
        requireAdministrator(request);
        organization.deleteDepartment(id);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createRole(@RequestBody Organization.RoleInput input, HttpServletRequest request) {
        requireAdministrator(request);
        organization.saveRole(null, input);
    }

    @PutMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRole(@PathVariable long id, @RequestBody Organization.RoleInput input, HttpServletRequest request) {
        requireAdministrator(request);
        organization.saveRole(id, input);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable long id, HttpServletRequest request) {
        requireAdministrator(request);
        organization.deleteRole(id);
    }

    private String requireAdministrator(HttpServletRequest request) {
        var access = Api.access(request);
        if (!access.administrator()) throw Workspace.error(HttpStatus.FORBIDDEN, "只有系统管理员可以配置员工、部门和角色");
        return access.account().username();
    }
}
