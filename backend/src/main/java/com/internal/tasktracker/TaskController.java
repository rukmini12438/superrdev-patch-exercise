package com.internal.tasktracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/api/tasks")
    public ResponseEntity<?> searchTasks(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        // Validate paging input (prevents 500 errors from subList)
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "page must be >= 1 and pageSize must be between 1 and " + MAX_PAGE_SIZE));
        }

        // Normalize query and escape LIKE wildcards so %, _ are treated literally
        String query = q == null ? "" : q.trim();
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String searchTerm = "%" + escaped + "%";

        // Parse status filter; invalid value -> 400 instead of 500
        String normalizedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                normalizedStatus = TaskStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "status must be one of " + Arrays.toString(TaskStatus.values())));
            }
        }

        log.debug("searchTasks status={} page={} pageSize={} qLength={}",
                normalizedStatus, page, pageSize, query.length());

        List<Task> allResults = taskRepository.searchTasks(searchTerm, normalizedStatus);

        // long math avoids int overflow; clamping keeps subList arguments valid
        long start = (long) (page - 1) * pageSize;
        int from = (int) Math.min(start, allResults.size());
        int to = (int) Math.min(start + pageSize, allResults.size());
        List<Task> pageResults = allResults.subList(from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", pageResults);
        response.put("total", allResults.size());
        response.put("page", page);
        response.put("pageSize", pageSize);

        return