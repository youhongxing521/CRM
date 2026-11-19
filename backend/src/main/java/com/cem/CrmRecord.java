package com.cem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CrmRecord {
    public Long id;
    public String kind;
    public String name;
    public String status;
    public String owner;
    public Long relatedId;
    public String relatedName;
    public BigDecimal amount;
    public LocalDate dueDate;
    public String details;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
