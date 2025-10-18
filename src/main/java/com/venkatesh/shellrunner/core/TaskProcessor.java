package com.venkatesh.shellrunner.core;

import com.venkatesh.shellrunner.data.TaskRepository;
import com.venkatesh.shellrunner.execution.CommandExecutor;
import com.venkatesh.shellrunner.security.SecurityChecker;
import org.springframework.stereotype.Service;
import com.venkatesh.shellrunner.errors.ResourceNotFoundException;

import java.util.Date;
import java.util.List;

@Service
public class TaskProcessor {

    private final TaskRepository repository;
    private final SecurityChecker securityChecker;
    private final CommandExecutor executor;

    public TaskProcessor(TaskRepository repository, SecurityChecker securityChecker, CommandExecutor executor) {
        this.repository = repository;
        this.securityChecker = securityChecker;
        this.executor = executor;
    }

    public List<Task> getAllTasks() {
        return repository.findAll();
    }

    public Task getTaskById(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    public Task saveTask(Task task) {
        securityChecker.validateCommand(task.getCommand());
        if (task.getTaskExecutions() == null) {
            task.setTaskExecutions(new java.util.ArrayList<>());
        }
        return repository.save(task);
    }

    public void deleteTask(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }
        repository.deleteById(id);
    }

    public List<Task> findTasksByName(String query) {
        List<Task> found = repository.findByNameContainingIgnoreCase(query);
        if (found == null || found.isEmpty()) {
            throw new ResourceNotFoundException("No tasks found containing: " + query);
        }
        return found;
    }

    public Task executeTask(String id) throws Exception {
        Task task = getTaskById(id);
        securityChecker.validateCommand(task.getCommand());

        Date start = new Date();
        CommandExecutor.ExecutionResult result = executor.runCommand(task.getCommand(),
                java.time.Duration.ofSeconds(5));
        Date end = new Date();

        String output = (result.stdout + (result.stderr.isBlank() ? "" : ("\nERROR:\n" + result.stderr))).trim();
        TaskExecution execution = new TaskExecution(start, end, output);

        task.getTaskExecutions().add(execution);
        return repository.save(task);
    }
}
