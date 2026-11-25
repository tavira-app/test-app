package ir.khodroban.tco;

import ir.khodroban.tco.model.TCOComponent;
import ir.khodroban.tco.model.TCOScenario;
import ir.khodroban.tco.service.TCOService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tco")
public class TCOController {
    private final TCOService tcoService;

    public TCOController(TCOService tcoService) {
        this.tcoService = tcoService;
    }

    @PostMapping("/scenarios")
    public TCOScenario createScenario(@Valid @RequestBody TCOScenario scenario) {
        return tcoService.createScenario(scenario);
    }

    @GetMapping("/scenarios/{id}")
    public TCOScenario getScenario(@PathVariable Long id) {
        return tcoService.getScenario(id);
    }

    @PostMapping("/compare")
    public ComparisonResult compareScenarios(@Valid @RequestBody ComparisonRequest request) {
        return tcoService.compareScenarios(request.getScenarioIds());
    }

    public record ComparisonRequest(List<Long> scenarioIds) {}
    public record ComparisonResult(List<TCOScenarioComparison> comparisons) {}
    public record TCOScenarioComparison(Long id1, Long id2, Double difference) {}
}

diff --git a/src/main/java/ir/khodroban/tco/model/TCOComponent.java b/src/main/java/ir/khodroban/tco/model/TCOComponent.java
new file mode 100644
index 0000000..b0e2f3a
--- /dev/null
++ b/src/main/java/ir/khodroban/tco/model/TCOComponent.java
@@ -0,0 +1,53 @@
package ir.khodroban.tco.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class TCOComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column
    private String currency;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private boolean isCurrent;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(boolean current) { isCurrent = current; }
}

diff --git a/src/main/java/ir/khodroban/tco/model/TCOScenario.java b/src/main/java/ir/khodroban/tco/model/TCOScenario.java
new file mode 100644
index 0000000..e2a1b3c
--- /dev/null
++ b/src/main/java/ir/khodroban/tco/model/TCOScenario.java
@@ -0,0 +1,66 @@
package ir.khodroban.tco.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Entity
public class TCOScenario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private BigDecimal resaleValue;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDate")
    private Set<TCOComponent> components;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getResaleValue() { return resaleValue; }
    public void setResaleValue(BigDecimal resaleValue) { this.resaleValue = resaleValue; }
    public Set<TCOComponent> getComponents() { return components; }
    public void setComponents(Set<TCOComponent> components) { this.components = components; }
}

diff --git a/src/main/java/ir/khodroban/tco/service/TCOService.java b/src/main/java/ir/khodroban/tco/service/TCOService.java
new file mode 100644
index 0000000..c1d2e3f
--- /dev/null
++ b/src/main/java/ir/khodroban/tco/service/TCOService.java
@@ -0,0 +1,50 @@
package ir.khodroban.tco.service;

import ir.khodroban.tco.model.TCOComponent;
import ir.khodroban.tco.model.TCOScenario;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TCOService {
    public TCOScenario createScenario(TCOScenario scenario) {
        // Calculate TCO here
        return scenario;
    }

    public TCOScenario getScenario(Long id) {
        // Retrieve scenario by ID
        return new TCOScenario();
    }

    public ComparisonResult compareScenarios(List<Long> scenarioIds) {
        // Compare scenarios here
        return new ComparisonResult(List.of());
    }
}

diff --git a/src/main/resources/db/migration/V3__tco_schema.sql b/src/main/resources/db/migration/V3__tco_schema.sql
new file mode 100644
index 0000000..e1f2g3h
--- /dev/null
++ b/src/main/resources/db/migration/V3__tco_schema.sql
@@ -0,0 +1,16 @@
CREATE TABLE tco_component (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    cost NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    start_date DATE,
    end_date DATE,
    source VARCHAR(255) NOT NULL,
    is_current BOOLEAN NOT NULL
);

CREATE TABLE tco_scenario (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    currency VARCHAR(3) NOT NULL,
    resale_value NUMERIC(19, 4) NOT NULL
);
