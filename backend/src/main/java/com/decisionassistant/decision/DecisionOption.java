package com.decisionassistant.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "decision_options")
public class DecisionOption {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private Decision decision;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    protected DecisionOption() {
    }

    DecisionOption(UUID id, Decision decision, String name, String description, String sourceUrl, String sourceText) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.decision = decision;
        update(name, description, sourceUrl, sourceText);
    }

    void update(String name, String description, String sourceUrl, String sourceText) {
        this.name = name;
        this.description = description;
        this.sourceUrl = sourceUrl;
        this.sourceText = sourceText;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String sourceUrl() { return sourceUrl; }
    public String sourceText() { return sourceText; }

    public void applyImportedSource(String importedName, String importedText) {
        if (importedName != null && !importedName.isBlank()
                && (name == null || name.isBlank() || name.startsWith("후보 "))) {
            name = importedName;
        }
        if (sourceText == null || sourceText.isBlank()) sourceText = importedText;
    }
}
