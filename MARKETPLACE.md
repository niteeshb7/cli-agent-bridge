# JetBrains Marketplace submission draft

- **Plugin name:** Claude Chat
- **Version:** 0.1.1 (preview)
- **Vendor:** Niteesh Bhargava
- **Category:** AI Assistant / Developer Tools
- **License:** Apache License 2.0
- **Compatibility:** IntelliJ Platform 2026.2 and newer; verified against PyCharm 2026.2.3 and PyCharm 2026.3 EAP
- **Price:** Free
- **Vendor public email:** niteeshb7@gmail.com
- **Vendor website:** https://github.com/niteeshb7
- **Vendor bio:** Java, React, Python Developer
- **Source link:** https://github.com/niteeshb7/cli-agent-bridge

## Short description

Chat with your locally authenticated Claude Code CLI in a dedicated JetBrains IDE tool window.

## Description

I wanted a dedicated GUI for my Claude Code CLI inside JetBrains IDEs and could not find one that met my needs, so I built Claude Chat. It gives you an independent chat window for your existing Claude Code CLI login. It supports streaming text responses, model and effort controls, session continuity, optional active-file and selection context, and an explicit mode for file edits. It does not depend on JetBrains AI Assistant or require an Anthropic API key.

Claude Code CLI must be installed and authenticated separately. Active-editor context is off by default and can be enabled in the chat window. The plugin sends prompts and any enabled context through the local CLI to Anthropic. It does not collect telemetry.

This is a community plugin by Niteesh Bhargava, not an official Anthropic or JetBrains product.

## Change notes

Renamed the plugin to Claude Chat and updated its description to explain why it was built.

## Release gate

Push the source repository, create or select the JetBrains Marketplace vendor profile, and have the owner personally accept the Marketplace Developer Agreement. The installable ZIP is in `build/distributions/`.
