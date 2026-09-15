package com.stockintelligence.schedule;

import com.stockintelligence.analysis.AnalysisService;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.TriggerType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScheduleController {

    private final ScheduleService service;
    private final AnalysisService analysis;

    public ScheduleController(ScheduleService service, AnalysisService analysis) {
        this.service = service;
        this.analysis = analysis;
    }

    @GetMapping("/schedules")
    public List<ScheduleResponse> list() {
        return service.list();
    }

    @PutMapping("/watchlists/{id}/schedule")
    public ScheduleResponse upsert(@PathVariable Long id, @RequestBody ScheduleRequest request) {
        return service.upsert(id, request == null ? new ScheduleRequest(null, null, null) : request);
    }

    /** Manual trigger: runs the full analysis flow synchronously and returns the report. */
    @PostMapping("/watchlists/{id}/analyze")
    public ReportResponse analyze(@PathVariable Long id) {
        return analysis.analyzeWatchlist(id, TriggerType.MANUAL);
    }
}
