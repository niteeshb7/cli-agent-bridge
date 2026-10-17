package dev.niteesh.cliagentbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A standalone chat window backed by the user's local Claude Code CLI login. */
public final class ChatToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ChatPanel panel = new ChatPanel(project);
        Disposer.register(project, panel);
        Content content = ContentFactory.getInstance().createContent(panel, "Chat", false);
        toolWindow.getContentManager().addContent(content);
    }

    private static final class ChatPanel extends JPanel implements Disposable {
        private final Project project;
        private final JTextArea transcript = new JTextArea();
        private final JTextArea input = new JTextArea(4, 20);
        private final JComboBox<String> model = new JComboBox<>(new String[]{"Default", "Sonnet", "Opus", "Haiku"});
        private final JComboBox<String> effort = new JComboBox<>(new String[]{"Default", "Low", "Medium", "High", "Max"});
        private final JComboBox<String> access = new JComboBox<>(new String[]{"Read only", "Allow file edits"});
        private final JCheckBox includeEditor = new JCheckBox("Include active editor", false);
        private final JTextField cliPath = new JTextField(findClaude(), 18);
        private final JButton send = new JButton("Send");
        private final JButton stop = new JButton("Stop");
        private volatile Process currentProcess;
        private volatile String sessionId;
        private volatile boolean busy;
        private volatile boolean stopRequested;

        @Override
        public void dispose() {
            Process process = currentProcess;
            if (process != null) process.destroy();
        }

        ChatPanel(Project project) {
            super(new BorderLayout(6, 6));
            this.project = project;
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            JPanel settings = new JPanel(new GridLayout(0, 2, 4, 4));
            settings.add(new JLabel("Claude CLI"));
            settings.add(cliPath);
            settings.add(new JLabel("Model"));
            settings.add(model);
            settings.add(new JLabel("Effort"));
            settings.add(effort);
            settings.add(new JLabel("Access"));
            settings.add(access);
            settings.add(includeEditor);
            JButton newChat = new JButton("New chat");
            settings.add(newChat);
            add(settings, BorderLayout.NORTH);

            transcript.setEditable(false);
            transcript.setLineWrap(true);
            transcript.setWrapStyleWord(true);
            transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            transcript.setText("Claude Chat — sign in with `claude auth login` if needed.\n\n");
            add(new JScrollPane(transcript), BorderLayout.CENTER);

            input.setLineWrap(true);
            input.setWrapStyleWord(true);
            JPanel composer = new JPanel(new BorderLayout(4, 4));
            composer.add(new JScrollPane(input), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            buttons.add(stop);
            buttons.add(send);
            composer.add(buttons, BorderLayout.SOUTH);
            add(composer, BorderLayout.SOUTH);

            stop.setEnabled(false);
            includeEditor.setToolTipText("Send the active file and selection to Claude with each message");
            send.addActionListener(event -> submit());
            stop.addActionListener(event -> {
                stopRequested = true;
                Process process = currentProcess;
                if (process != null) process.destroy();
            });
            newChat.addActionListener(event -> {
                if (busy) return;
                sessionId = null;
                transcript.setText("New chat\n\n");
            });
        }

        private void submit() {
            String question = input.getText().trim();
            if (question.isEmpty() || busy) return;
            String prompt = includeEditor.isSelected() ? editorContext() + question : question;
            String selectedModel = String.valueOf(model.getSelectedItem());
            String selectedEffort = String.valueOf(effort.getSelectedItem());
            boolean allowEdits = access.getSelectedIndex() == 1;
            String executable = cliPath.getText().trim();
            input.setText("");
            busy = true;
            stopRequested = false;
            append("You: " + question + "\n\nAssistant: ");
            send.setEnabled(false);
            stop.setEnabled(true);
            CompletableFuture.runAsync(() -> runClaude(executable, prompt, selectedModel, selectedEffort, allowEdits));
        }

        private String editorContext() {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) return "";
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (file == null) return "";
            StringBuilder context = new StringBuilder("IDE context (current editor):\nFile: ")
                    .append(file.getPath()).append('\n');
            String selection = editor.getSelectionModel().getSelectedText();
            if (selection != null && !selection.isBlank()) {
                context.append("Selected text:\n")
                        .append(selection, 0, Math.min(selection.length(), 12000)).append('\n');
            }
            // Include the current editor buffer, including unsaved changes, for short files.
            String content = editor.getDocument().getText();
            if (content.length() <= 50000) context.append("Current file content:\n").append(content).append('\n');
            return context.append("\nUser request:\n").toString();
        }

        private void runClaude(String executable, String prompt, String selectedModel,
                               String selectedEffort, boolean allowEdits) {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add("-p");
            command.add("--output-format");
            command.add("stream-json");
            command.add("--verbose");
            command.add("--include-partial-messages");
            if (sessionId != null) {
                command.add("--resume");
                command.add(sessionId);
            }
            if (!"Default".equals(selectedModel)) {
                command.add("--model");
                command.add(selectedModel.toLowerCase());
            }
            if (!"Default".equals(selectedEffort)) {
                command.add("--effort");
                command.add(selectedEffort.toLowerCase());
            }
            for (String tool : allowEdits ? new String[]{"Read", "Glob", "Grep", "Edit", "Write"}
                    : new String[]{"Read", "Glob", "Grep"}) {
                command.add("--allowedTools");
                command.add(tool);
            }
            command.add("--disallowedTools");
            command.add(allowEdits ? "Bash,NotebookEdit" : "Bash,Edit,Write,NotebookEdit");
            if (allowEdits) {
                // The user selected this mode; Bash is deliberately excluded.
                command.add("--permission-mode");
                command.add("acceptEdits");
            }

            StringBuilder stderr = new StringBuilder();
            StringBuilder answer = new StringBuilder();
            String finalResult = null;
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                if (project.getBasePath() != null) builder.directory(Path.of(project.getBasePath()).toFile());
                String path = builder.environment().getOrDefault("PATH", "");
                String home = System.getProperty("user.home");
                builder.environment().put("PATH", home + "/.local/bin:/usr/local/bin:/opt/homebrew/bin:" + path);
                builder.environment().remove("ANTHROPIC_API_KEY");
                builder.environment().remove("ANTHROPIC_AUTH_TOKEN");
                Process process = builder.start();
                currentProcess = process;
                if (stopRequested) process.destroy();
                try (var stdin = process.getOutputStream()) {
                    stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
                }
                CompletableFuture<Void> errorReader = CompletableFuture.runAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (stderr.length() < 4000) stderr.append(line).append('\n');
                        }
                    } catch (Exception ignored) { }
                });
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            JsonObject event = JsonParser.parseString(line).getAsJsonObject();
                            String id = string(event, "session_id");
                            if (id != null) sessionId = id;
                            if ("stream_event".equals(string(event, "type"))) {
                                JsonObject inner = object(event, "event");
                                JsonObject delta = inner == null ? null : object(inner, "delta");
                                if (delta != null && "text_delta".equals(string(delta, "type"))) {
                                    String text = string(delta, "text");
                                    if (text != null) {
                                        answer.append(text);
                                        append(text);
                                    }
                                }
                            } else if ("result".equals(string(event, "type"))) {
                                finalResult = string(event, "result");
                            }
                        } catch (Exception ignored) { /* Unknown CLI event; keep the chat alive. */ }
                    }
                }
                int exit = process.waitFor();
                errorReader.join();
                if (answer.length() == 0 && finalResult != null) append(finalResult);
                if (exit != 0) append("\n[Claude exited with code " + exit + "] " + stderr);
                if (answer.length() == 0 && finalResult == null && exit == 0) append("\n[No response received]");
            } catch (Exception e) {
                append("\n[Could not launch Claude CLI: " + e.getMessage() + "]");
            } finally {
                currentProcess = null;
                busy = false;
                append("\n\n");
                SwingUtilities.invokeLater(() -> {
                    send.setEnabled(true);
                    stop.setEnabled(false);
                });
            }
        }

        private void append(String value) {
            SwingUtilities.invokeLater(() -> {
                transcript.append(value);
                transcript.setCaretPosition(transcript.getDocument().getLength());
            });
        }

        private static JsonObject object(JsonObject parent, String key) {
            JsonElement value = parent.get(key);
            return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
        }

        private static String string(JsonObject parent, String key) {
            JsonElement value = parent.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        private static String findClaude() {
            String home = System.getProperty("user.home");
            for (String path : new String[]{home + "/.local/bin/claude", "/usr/local/bin/claude", "/opt/homebrew/bin/claude"}) {
                if (Files.isExecutable(Path.of(path))) return path;
            }
            return "claude";
        }
    }
}
