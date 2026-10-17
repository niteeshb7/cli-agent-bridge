# CLI Agent Bridge

A standalone chat tool window for JetBrains IDEs that runs your locally installed Claude Code CLI. It does not require JetBrains AI Assistant or an Anthropic API key. The CLI uses whatever account you have authenticated with `claude auth login`, including an eligible Claude subscription.

This is an independent community plugin by Niteesh Bhargava. It is not affiliated with Anthropic or JetBrains.

## Requirements

- A JetBrains IDE based on platform 2026.2 or newer.
- Claude Code CLI installed and signed in.

## Use

1. Install the plugin ZIP in **Settings → Plugins → Install Plugin from Disk**.
2. Open the **CLI Agent** tool window.
3. Set the Claude CLI path if it was not found automatically.
4. Choose a model and effort level, then send a message.
5. Select **Include active editor** when you want to send the active file and any selection as context.
6. Select **Allow file edits** only when you want Claude to modify project files. Shell commands remain disabled in both modes.

The active-editor option is off by default. When enabled, the plugin sends the current file path and, for files of at most 50,000 characters, the editor buffer including unsaved changes. It also sends up to 12,000 characters of selected text. The content goes to Claude through your local CLI. The plugin does not send usage telemetry.

The plugin removes `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` from the CLI child process so a stray environment variable cannot switch a signed-in subscription session to API billing. It does not modify your Claude account settings.

## Current scope

This initial release provides text chat, streaming responses, model and effort controls, in-memory session continuity, stop/new chat, editor context, and read-only or file-edit access. It does not yet provide rendered Markdown, image attachments, IDE diff review, persistent transcripts, terminal tool execution, or per-tool permission dialogs.

## Build from source

Use the included Gradle wrapper and a JDK supported by IntelliJ Platform 2026.2. By default the build downloads IntelliJ IDEA 2026.2.3. To build against an already installed IDE, set `JETBRAINS_IDE_PATH` to its application directory.

```sh
./gradlew buildPlugin
```

The installable ZIP appears in `build/distributions/`.

## License and credits

Licensed under Apache License 2.0; see [LICENSE](LICENSE). The idea for using a local Claude CLI from a JetBrains IDE followed the MIT-licensed [claude-code-acp](https://github.com/harukitosa/claude-code-acp) bridge by Haruki Tosa. This plugin uses its own direct CLI integration and does not include that bridge's source code.
