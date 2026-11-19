package com.cem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class Workspace {
    public record Field(String key, String label, String type, List<String> options) {}
    public record Module(String key, String group, String icon, String label, String name,
                         List<String> statuses, String related, String date, String amount, List<Field> fields) {}
    public record Input(String name, String status, String owner, Long relatedId, BigDecimal amount,
                        LocalDate dueDate, Map<String,JsonNode> details) {}
    public record Row(Long id, String kind, String name, String status, String owner, Long relatedId,
                      String relatedName, BigDecimal amount, LocalDate dueDate, JsonNode details,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Page(List<Row> rows, long total) {}

    static final Set<String> APPROVABLE = Set.of("contracts", "payments", "invoices", "expenses");
    private final CrmMapper db;
    private final ObjectMapper json;
    private final Map<String,Module> modules = new LinkedHashMap<>();

    public Workspace(CrmMapper db, ObjectMapper json) throws IOException {
        this.db = db;
        this.json = json;
        try (var stream = new ClassPathResource("modules.json").getInputStream()) {
            List<Module> definitions = json.readValue(stream, new TypeReference<>() {});
            definitions.forEach(module -> modules.put(module.key(), module));
        }
    }

    public Collection<Module> catalog() { return modules.values(); }

    Module module(String kind) {
        Module module = modules.get(kind);
        if (module == null) throw error(HttpStatus.NOT_FOUND, "业务模块不存在");
        return module;
    }

    public Page page(String kind, String status, String owner, Long related, String keyword, int page, int size) {
        module(kind);
        if (page < 1 || size < 1 || size > 500) throw invalid("分页参数无效");
        return new Page(db.list(kind, status, owner, related, keyword.strip(), size, (long)(page - 1) * size)
                .stream().map(this::view).toList(), db.count(kind, status, owner, related, keyword.strip()));
    }

    public Row get(String kind, long id) {
        return view(required(db.get(id), kind));
    }

    @Transactional
    public Row save(String kind, Long id, Input input, String actor) {
        Module module = module(kind);
        if (kind.equals("approvals")) throw invalid("审批单由业务单据提交后生成");
        CrmRecord row = id == null ? new CrmRecord() : required(db.lock(id), kind);
        if (id != null) ensureEditable(row);
        row.kind = kind;
        row.name = field(input.name(), module.name(), 160, true);
        row.owner = field(input.owner() == null || input.owner().isBlank() ? actor : input.owner(), "负责人", 80, true);
        if (kind.endsWith("-pool")) row.owner = "未分配";
        String status = input.status();
        if (module.statuses().isEmpty()) {
            row.status = "";
        } else if (APPROVABLE.contains(kind)) {
            row.status = id == null ? "草稿" : row.status;
        } else if (kind.equals("leads")) {
            if ("已转化".equals(status) && !"已转化".equals(row.status)) {
                throw invalid("请使用转为客户操作");
            }
            row.status = "已转化".equals(row.status) ? row.status : field(status, "状态", 24, true);
            if (!module.statuses().contains(row.status)) throw invalid("状态无效");
        } else {
            row.status = field(status, "状态", 24, true);
            if (!module.statuses().contains(row.status)) throw invalid("状态无效");
        }
        if (module.related() != null) {
            if (input.relatedId() == null) throw invalid("请选择关联" + module(module.related()).name().replace("名称", ""));
            CrmRecord parent = db.get(input.relatedId());
            if (parent == null || (!module.related().equals(parent.kind) && !Objects.equals(input.relatedId(), row.relatedId))) {
                throw invalid("关联记录无效");
            }
            row.relatedId = input.relatedId();
        }
        row.amount = module.amount() == null ? null : input.amount();
        if (module.amount() != null && (row.amount == null || row.amount.signum() < 0
                || row.amount.compareTo(new BigDecimal("99999999999999.99")) > 0
                || row.amount.stripTrailingZeros().scale() > 2
                || (APPROVABLE.contains(kind) && row.amount.signum() == 0))) {
            throw invalid(module.amount() + "无效，请填写最多两位小数的合法金额");
        }
        row.dueDate = module.date() == null ? null : input.dueDate();
        if (module.date() != null && row.dueDate == null) throw invalid("请选择" + module.date());
        var details = json.createObjectNode();
        for (Field descriptor : module.fields()) {
            JsonNode value = input.details() == null ? null : input.details().get(descriptor.key());
            if (value == null || value.isNull() || value.asText().isBlank()) continue;
            if (descriptor.type().equals("rating")) {
                if (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > 5) throw invalid("满意度须为 1 至 5 分");
                details.set(descriptor.key(), value);
                continue;
            }
            if (!value.isTextual()) throw invalid(descriptor.label() + "格式无效");
            String text = field(value.asText(), descriptor.label(), descriptor.type().equals("textarea") ? 4000 : 200, false);
            if (descriptor.options() != null && !descriptor.options().contains(text)) throw invalid(descriptor.label() + "选项无效");
            try {
                if (descriptor.type().equals("date")) LocalDate.parse(text);
                if (descriptor.type().equals("time")) LocalTime.parse(text);
            } catch (DateTimeParseException ex) {
                throw invalid(descriptor.label() + "格式无效");
            }
            details.put(descriptor.key(), text);
        }
        row.details = details.toString();
        if (id == null) db.insert(row); else db.update(row);
        return get(kind, row.id);
    }

    @Transactional
    public void delete(String kind, long id) {
        CrmRecord row = required(db.lock(id), kind);
        ensureEditable(row);
        if (kind.equals("approvals")) throw invalid("审批记录不可删除");
        if (db.dependents(id) > 0) throw error(HttpStatus.CONFLICT, "存在关联记录，无法删除");
        db.delete(id);
    }

    @Transactional
    public Row action(String kind, long id, String action, String comment, String actor) {
        CrmRecord row = required(db.lock(id), kind);
        switch (action) {
            case "claim" -> {
                if (!Set.of("lead-pool", "customer-pool").contains(kind)) throw invalid("该记录不可领取");
                row.kind = kind.equals("lead-pool") ? "leads" : "customers";
                row.status = module(row.kind).statuses().get(0);
                row.owner = actor;
            }
            case "release" -> {
                if (!Set.of("leads", "customers").contains(kind) || "已转化".equals(row.status)) throw invalid("该记录不可移入公海");
                row.kind = kind.equals("leads") ? "lead-pool" : "customer-pool";
                row.status = "";
                row.owner = "未分配";
            }
            case "convert" -> {
                if (!kind.equals("leads") || "已转化".equals(row.status)) throw invalid("该线索不可转化");
                CrmRecord customer = new CrmRecord();
                customer.kind = "customers";
                customer.name = row.name;
                customer.owner = row.owner;
                customer.status = "潜在客户";
                customer.relatedId = row.id;
                var details = view(row).details();
                var converted = json.createObjectNode();
                for (String key : List.of("contact", "phone", "note")) {
                    if (details.has(key)) converted.set(key, details.get(key));
                }
                customer.details = converted.toString();
                db.insert(customer);
                row.status = "已转化";
            }
            case "submit" -> {
                if (!APPROVABLE.contains(kind) || !Set.of("草稿", "已驳回").contains(row.status)) throw invalid("当前状态不可提交");
                if (Set.of("payments", "invoices").contains(kind)) {
                    CrmRecord contract = required(db.lock(row.relatedId), "contracts");
                    if (!contract.status.equals("已通过")) throw invalid("请先完成关联合同的审批");
                    BigDecimal used = db.committedAmount(kind, contract.id, row.id);
                    if (used.add(row.amount).compareTo(contract.amount) > 0) throw invalid("累计金额超过关联合同金额");
                }
                CrmRecord approval = new CrmRecord();
                approval.kind = "approvals";
                approval.name = module(kind).label().replace("管理", "") + "审批 · " + row.name;
                if (approval.name.length() > 160) approval.name = approval.name.substring(0, 160);
                approval.owner = actor;
                approval.relatedId = row.id;
                approval.status = "待审批";
                approval.details = "{}";
                db.insert(approval);
                row.status = "审核中";
            }
            case "approve", "reject" -> {
                if (!kind.equals("approvals") || !row.status.equals("待审批")) throw invalid("审批已处理或状态无效");
                String opinion = field(comment, "审批意见", 4000, action.equals("reject"));
                CrmRecord document = db.lock(row.relatedId);
                if (document == null || !document.status.equals("审核中")) throw invalid("关联单据状态无效");
                row.status = action.equals("approve") ? "已通过" : "已驳回";
                row.details = json.createObjectNode().put("comment", opinion).put("reviewer", actor).toString();
                document.status = row.status;
                db.update(document);
            }
            case "complete" -> {
                if (!kind.equals("tasks") && !kind.equals("calendar")) throw invalid("该记录不可标记完成");
                row.status = "已完成";
            }
            default -> throw invalid("操作无效");
        }
        db.update(row);
        return get(row.kind, id);
    }

    public Map<String,Object> dashboard(int year, Set<String> kinds) {
        if (year < 2000 || year > 2100) throw invalid("年份无效");
        return Map.of("totals", db.totals().stream().filter(row -> kinds.contains(row.get("kind"))).toList(),
                "trend", db.trend(year + "-01-01", (year + 1) + "-01-01").stream()
                        .filter(row -> kinds.contains(row.get("kind"))).toList(),
                "experience", kinds.contains("feedback") ? db.experience() : Map.of(),
                "tasks", kinds.contains("tasks") ? db.tasks().stream().map(this::view).toList() : List.of(),
                "recent", kinds.isEmpty() ? List.of() : db.recent(kinds).stream().map(this::view).toList());
    }

    private void ensureEditable(CrmRecord row) {
        if (APPROVABLE.contains(row.kind) && Set.of("审核中", "已通过").contains(row.status)) {
            throw invalid("审核中或已通过的单据不可修改");
        }
    }

    static String field(String value, String label, int max, boolean required) {
        String text = value == null ? "" : value.strip();
        if (required && text.isBlank()) throw invalid("请填写" + label);
        if (text.length() > max) throw invalid(label + "不能超过 " + max + " 个字符");
        return text;
    }

    private CrmRecord required(CrmRecord row, String kind) {
        if (row == null || !row.kind.equals(kind)) throw error(HttpStatus.NOT_FOUND, "记录不存在");
        return row;
    }

    private Row view(CrmRecord row) {
        try {
            return new Row(row.id, row.kind, row.name, row.status, row.owner, row.relatedId, row.relatedName,
                    row.amount, row.dueDate, json.readTree(row.details), row.createdAt, row.updatedAt);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取业务记录", ex);
        }
    }

    static ResponseStatusException invalid(String message) { return error(HttpStatus.BAD_REQUEST, message); }
    static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
