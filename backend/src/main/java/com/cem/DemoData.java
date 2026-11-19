package com.cem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DemoData implements ApplicationRunner {
    static final String DEMO_PASSWORD = "demo12345";
    record DemoAccount(String username, String displayName) {}
    static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("demo_sales", "销售演示"),
            new DemoAccount("demo_service", "客服演示"),
            new DemoAccount("demo_ops", "运营演示"));
    private record Buyer(String name, String industry, String contact, String title, String demand) {}
    private record Product(String code, String name, String category, String pack, String price) {}

    private static final List<Buyer> BUYERS = List.of(
            new Buyer("青禾快修中心", "快修保养", "刘明", "店长", "护板卡扣月度补货"),
            new Buyer("路川轮胎服务店", "轮胎服务", "王芳", "采购负责人", "气门嘴和补胎胶条采购"),
            new Buyer("恒路汽配商行", "汽配经销", "陈涛", "采购经理", "紧固件礼盒渠道铺货"),
            new Buyer("新桥汽车维修厂", "综合汽修", "赵强", "车间主管", "放油螺丝垫片补货"),
            new Buyer("润驰快保连锁", "快修保养", "宋洁", "供应链主管", "一次性座套集中采购"),
            new Buyer("行远车队维保中心", "车队维保", "许伟", "维保主管", "保险丝及端子备货"),
            new Buyer("南湾汽车钣喷中心", "钣喷维修", "方凯", "采购负责人", "砂纸和遮蔽胶带定供"),
            new Buyer("启程汽车维修站", "综合汽修", "吴宁", "门店经理", "密封胶和管箍采购"),
            new Buyer("松岭轮胎快修店", "轮胎服务", "蒋林", "店长", "平衡块试用采购"),
            new Buyer("顺泽汽配供应站", "汽配经销", "周建", "采购负责人", "通用卡扣多规格试单"),
            new Buyer("丰桥快修服务站", "快修保养", "郑雯", "店长", "刹车清洗剂试单"),
            new Buyer("捷汇车队维修站", "车队维保", "何平", "备件管理员", "热缩管和扎带补货"));

    private static final List<Product> PRODUCTS = List.of(
            new Product("KK-080-100", "8 mm 护板塑料卡扣", "卡扣与紧固件", "安装孔径 8 mm，100 个/袋", "18.50"),
            new Product("JJ-M6-100", "M6 法兰螺母", "卡扣与紧固件", "M6 镀锌，100 个/盒", "26.00"),
            new Product("MF-1417-100", "M14 放油螺丝铜垫片", "密封与管路", "内径 14 mm，100 片/盒", "32.00"),
            new Product("GL-1625-50", "16–25 mm 不锈钢管箍", "密封与管路", "调节范围 16–25 mm，50 个/盒", "68.00"),
            new Product("DQ-MINI15-50", "15 A 迷你片式保险丝", "电气小件", "迷你片式 15 A，50 只/盒", "12.50"),
            new Product("DQ-HS60-50", "6 mm 黑色热缩管", "电气小件", "直径 6 mm，2:1 收缩比，50 根/包", "19.80"),
            new Product("LT-TR413-50", "TR413 橡胶气门嘴", "轮胎维修与平衡", "TR413，50 只/袋", "42.50"),
            new Product("LT-JT100-60", "100 mm 补胎胶条", "轮胎维修与平衡", "长度 100 mm，60 根/盒", "28.80"),
            new Product("LT-PH5-100", "5 g 粘贴式平衡块", "轮胎维修与平衡", "单节 5 g，100 节/盒", "35.00"),
            new Product("QJ-J450-24", "刹车清洗剂", "清洗与养护", "450 mL/瓶，24 瓶/箱，按箱报价", "198.00"),
            new Product("JZ-2438-12", "汽车喷漆遮蔽胶带", "胶粘与钣喷", "24 mm × 38 m，12 卷/箱", "66.00"),
            new Product("JZ-P400-50", "P400 干磨砂纸", "胶粘与钣喷", "P400，50 张/盒", "59.00"),
            new Product("FH-ZT100-1", "一次性维修座椅套", "防护用品", "PE 一次性座椅套，100 只/包", "48.00"),
            new Product("BY-YG24-1", "24 英寸通用无骨雨刮", "保养易损件", "24 英寸，U 型接口，1 支/盒", "17.90"));

    private final CrmMapper db;
    private final Workspace workspace;
    private final Organization organization;
    private final ObjectMapper json;
    private final String password;
    private final boolean enabled;

    public DemoData(CrmMapper db, Workspace workspace, Organization organization, ObjectMapper json,
                    @Value("${cem.admin-password}") String password, @Value("${cem.demo-data}") boolean enabled) {
        this.db = db;
        this.workspace = workspace;
        this.organization = organization;
        this.json = json;
        this.password = password;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (db.user("admin") == null) db.insertUser("admin", Api.PASSWORDS.encode(password), "系统管理员");
        if (enabled && db.initialize("demo-accounts-v1") == 1) {
            String hash = Api.PASSWORDS.encode(DEMO_PASSWORD);
            ACCOUNTS.forEach(account -> db.insertUser(account.username(), hash, account.displayName()));
        }
        organization.installDefaults(enabled);
        if (!enabled || db.initialize("demo-v1") == 0) return;
        LocalDate today = LocalDate.now();
        String[] names = {"张晓", "李思远", "周雨", "陈佳"};
        long[] customers = new long[BUYERS.size()];
        for (int i = 0; i < BUYERS.size(); i++) {
            Buyer buyer = BUYERS.get(i);
            String owner = names[i % names.length];
            customers[i] = add("customers", buyer.name(), i < 4 ? "已成交" : i < 9 ? "合作中" : "潜在客户",
                    owner, null, null, null, Map.of("industry", buyer.industry(), "level",
                            i < 4 ? "重点客户" : "普通客户", "contact", buyer.contact(), "note", "采购需求：" + buyer.demand()));
            add("contacts", buyer.contact(), "有效", owner, customers[i], null, null, Map.of("title", buyer.title()));
            add("leads", buyer.name() + " · " + buyer.demand(), i % 3 == 0 ? "待跟进" : "跟进中",
                    owner, null, null, null, Map.of("contact", buyer.contact(), "source",
                            i % 2 == 0 ? "网站咨询" : "展会活动", "note", "待核实规格、月用量和交付地址。"));
            add("opportunities", buyer.name() + " · " + buyer.demand(), workspace.module("opportunities").statuses().get(i % 6),
                    owner, customers[i], 2800 + i * 1250, today.plusDays(i * 3),
                    Map.of("source", "主动开发", "note", "按整包或整箱报价，核对样品规格及首单交期。"));
            add("followups", buyer.name() + " · 补货沟通", "", owner, customers[i], null, today.minusDays(i),
                    Map.of("channel", "电话", "next", today.plusDays(i + 1).toString(),
                            "note", "已沟通" + buyer.demand() + "，下次确认采购数量、包装单位和发货日期。"));
        }
        long firstContract = 0;
        for (int i = 0; i < 8; i++) {
            Buyer buyer = BUYERS.get(i);
            LocalDate date = today.minusMonths(7 - i).withDayOfMonth(1);
            int amount = new int[]{7200, 10500, 8900, 14600, 13000, 17700, 15600, 19900}[i];
            long contract = add("contracts", buyer.name() + " · 汽修耗材供货合同", "草稿", names[i % 4], customers[i],
                    amount, date, Map.of("code", "HT-" + date.getYear() + "-00" + (i + 1),
                            "note", buyer.demand() + "，首期支付合同金额的 60%，发货前核对包装和数量。"));
            if (i == 0) firstContract = contract;
            approve("contracts", contract);
            long payment = add("payments", buyer.name() + " · 耗材首期货款", "草稿", names[i % 4], contract,
                    amount * 3 / 5, date, Map.of("channel", "银行转账", "note", "首期货款到账，剩余货款按供货合同结算。"));
            approve("payments", payment);
        }
        addProduct(PRODUCTS.get(0));
        add("lead-pool", "西桥轮胎服务部 · 气门嘴询价", "", null, null, null, null,
                Map.of("source", "网站咨询", "note", "询问 TR413 气门嘴的整袋价格和发货时效。"));
        add("customer-pool", "远新汽配批发部", "", null, null, null, null, Map.of("industry", "汽配经销",
                "note", "关注卡扣、保险丝和补胎耗材的混批条件。"));
        add("tasks", "确认青禾快修本月卡扣补货", "待处理", "张晓", null, null, today,
                Map.of("note", "核对 8 mm 护板卡扣和常用内饰卡扣的需求数量。"));
        add("tasks", "给路川轮胎发送补胎耗材报价", "待处理", "李思远", null, null, today,
                Map.of("note", "按袋报价 TR413 气门嘴，按盒报价 100 mm 补胎胶条。"));
        add("tasks", "给新桥维修厂寄送铜垫片样品", "待处理", "陈佳", null, null, today.plusDays(1),
                Map.of("note", "样品发出前核对放油螺丝规格，发货后登记快递单号。"));
        add("calendar", "汽修耗材销售例会", "待参加", "系统管理员", null, null, today.plusDays(1),
                Map.of("time", "10:00", "location", "第一会议室", "note", "复盘门店补货和经销商试单情况。"));
        add("announcements", "常用汽修小件补货安排", "已发布", "系统管理员", null, null, today,
                Map.of("note", "卡扣、密封垫片、气门嘴按新包装单位报价。整箱订单请提前确认备货周期。"));
        String[] feedback = {"卡扣规格齐全，分袋标签清楚。", "气门嘴和补胎胶条整包到货，无缺件。",
                "按规格分盒发货，门店盘点更方便。", "希望在垫片外盒上增加内径和外径标识。"};
        for (int i = 0; i < 4; i++) {
            add("feedback", BUYERS.get(i).name() + " · 到货回访", i == 3 ? "待处理" : "已解决", names[i],
                    customers[i], null, today.minusDays(i), Map.of("category", i == 3 ? "建议" : "表扬",
                    "score", i == 3 ? 3 : 5, "note", feedback[i]));
        }
        for (Product product : PRODUCTS.subList(1, PRODUCTS.size())) addProduct(product);
        add("invoices", BUYERS.get(0).name(), "草稿", names[0], firstContract, 4320, today,
                Map.of("category", "增值税普通发票", "note", "按耗材供货合同首期货款金额开票。"));
        add("expenses", "护板卡扣样品寄送费", "草稿", names[0], customers[0], 24, today,
                Map.of("category", "物料费", "note", "寄送两种规格的护板卡扣，供门店试装。"));
        add("logs", "快修门店样品回访", "", names[0], null, null, today,
                Map.of("note", "回访青禾快修，确认 8 mm 卡扣试装结果。", "plan", "按其月用量整理两档批发报价。"));
        add("tags", "月度补货客户", "启用", null, null, null, null,
                Map.of("note", "适用于每月按固定频率采购常用耗材的门店。"));
        add("targets", "汽修耗材月度销售目标", "", null, null, 150000, today.withDayOfMonth(1),
                Map.of("department", "汽修耗材销售组"));
        add("expense-types", "样品寄送费", "启用", null, null, null, null,
                Map.of("note", "用于门店试用件和经销商样品的寄送登记。"));
    }

    static boolean isDemoAccount(String username) {
        return ACCOUNTS.stream().anyMatch(account -> account.username().equals(username));
    }

    private long add(String kind, String name, String status, String owner, Long related, Integer amount,
                     LocalDate date, Map<String,?> details) {
        return save(kind, name, status, owner, related, amount == null ? null : BigDecimal.valueOf(amount), date, details);
    }

    private void addProduct(Product product) {
        save("products", product.name(), "在售", "系统管理员", null, new BigDecimal(product.price()), null,
                Map.of("code", product.code(), "category", product.category(), "note", product.pack()));
    }

    private long save(String kind, String name, String status, String owner, Long related, BigDecimal amount,
                      LocalDate date, Map<String,?> details) {
        var fields = new LinkedHashMap<String,JsonNode>();
        details.forEach((key, value) -> fields.put(key, json.valueToTree(value)));
        return workspace.save(kind, null, new Workspace.Input(name, status, owner, related, amount, date, fields), "系统管理员").id();
    }

    private void approve(String kind, long id) {
        workspace.action(kind, id, "submit", null, "系统管理员");
        long approval = workspace.page("approvals", "待审批", null, id, "", 1, 1).rows().get(0).id();
        workspace.action("approvals", approval, "approve", "演示数据", "系统管理员");
    }
}
