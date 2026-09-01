package com.decisionassistant.guidance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decision_questions")
public class DecisionQuestion {
    public enum Kind { SINGLE_CHOICE, NUMBER, TEXT, CONFIRM }
    public enum Status { OPEN, ANSWERED, DISMISSED }

    @Id private UUID id;
    @Column(name = "decision_id", nullable = false) private UUID decisionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Kind kind;
    @Column(name = "target_key", nullable = false, length = 200) private String targetKey;
    @Column(nullable = false, length = 1000) private String prompt;
    @Column(columnDefinition = "text") private String impact;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "options_json", columnDefinition = "jsonb") private List<String> options;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "answer_json", columnDefinition = "jsonb") private String answer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int priority;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "answered_at") private Instant answeredAt;

    protected DecisionQuestion() {}

    public DecisionQuestion(UUID decisionId, Kind kind, String targetKey, String prompt,
                            String impact, List<String> options, int priority, Instant now) {
        this.id = UUID.randomUUID();
        this.decisionId = decisionId;
        this.kind = kind;
        this.targetKey = targetKey;
        this.prompt = prompt;
        this.impact = impact;
        this.options = options;
        this.priority = priority;
        this.status = Status.OPEN;
        this.createdAt = now;
    }

    public void answer(String answer, Instant now) { this.answer = answer; status = Status.ANSWERED; answeredAt = now; }
    public void dismiss(Instant now) { status = Status.DISMISSED; answeredAt = now; }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public Kind kind() { return kind; }
    public String targetKey() { return targetKey; }
    public String prompt() { return prompt; }
    public String impact() { return impact; }
    public List<String> options() { return options == null ? List.of() : options; }
    public String answer() { return answer; }
    public Status status() { return status; }
    public int priority() { return priority; }
}
