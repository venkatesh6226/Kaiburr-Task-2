package com.venkatesh.shellrunner.api;

import com.venkatesh.shellrunner.core.Task;
import com.venkatesh.shellrunner.core.TaskProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = { "http://localhost:3000", "http://127.0.0.1:3000" })
public class TaskController {

    private final TaskProcessor processor;

    public TaskController(TaskProcessor processor) {
        this.processor = processor;
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTasks(@RequestParam(value = "id", required = false) String id) {
        if (id == null) {
            List<Task> all = processor.getAllTasks();
            return ResponseEntity.ok(all);
        } else {
            Task one = processor.getTaskById(id);
            return ResponseEntity.ok(one);
        }
    }

    @PutMapping("/tasks")
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        Task saved = processor.saveTask(task);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/tasks")
    public ResponseEntity<Void> deleteTask(@RequestParam("id") String id) {
        processor.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tasks/find")
    public ResponseEntity<List<Task>> findTasks(@RequestParam("name") String name) {
        List<Task> found = processor.findTasksByName(name);
        return ResponseEntity.ok(found);
    }

    @PutMapping("/tasks/execute")
    public ResponseEntity<Task> executeTask(@RequestParam("id") String id) throws Exception {
        Task updated = processor.executeTask(id);
        return ResponseEntity.ok(updated);
    }
}
