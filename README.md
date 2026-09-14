# Gemini Compose+

**C048 · Mobile Application Development — Assignment 1**

A Gemini chat application built with Kotlin and Jetpack Compose. It extends the starter project with persistent chats, user-controlled context, voice input, response tools, and privacy settings.

## Features

### Chat interface

Material 3 chat with responsive layout, loading states, retries, and multi-turn history. Messages remain available locally after the app is restarted.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/01-welcome.png" alt="Welcome screen" width="38%" />
<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/02-chat-context.png" alt="Chat with context status" width="38%" />

### Context controls

Preview the messages and shared memories selected for the next request, with an estimated token budget. Hide excludes a message and its linked reply from future context; Pin preserves allowed details during trimming.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/03-context-controls.png" alt="Context controls" width="38%" />

### Conversation summary

`/summarize` sends only allowed messages and selected answer versions to Gemini. The resulting summary is saved locally and excluded from later context.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/12-summary.png" alt="Conversation summary" width="38%" />

### Privacy settings

Choose Connected, Protected memory, or Confidential mode for each chat. Confidential blocks memory sharing in both directions; prompts still go to Gemini. Drafts and custom instructions stay separate for each chat. [Privacy rules and demonstration](docs/privacy-and-context.md).

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/04-privacy-security.png" alt="Privacy and security settings" width="38%" />

### Multiple conversations

Create, rename, delete, search, and switch between saved chats. Search checks both chat names and saved message text while each chat retains its own privacy setting.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/05-chat-drawer.png" alt="Multiple chat drawer" width="38%" />

### Edit and resend

Correct an earlier prompt directly from its message menu. The original remains visible locally but is excluded from future AI context when the correction is sent.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/13-edit-and-resend.png" alt="Edit and resend message action" width="38%" />

### Appearance

Switch between system, light, and dark themes. The selected appearance is saved for the next launch.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/11-theme-picker.png" alt="Theme picker" width="38%" />

### Voice typing

Speak a message, review the draft, then send it when ready. Voice input never sends a message automatically.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/10-microphone-permission.png" alt="Voice input permission" width="38%" />

### Response tools

Copy, share, read aloud, stop playback, regenerate, inspect, and continue a response. Quick suggestion chips make it easy to ask for a simpler explanation or an example.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/06-response-tools.jpg" alt="Response tools" width="38%" />
<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/07-quick-actions.png" alt="Quick response actions" width="38%" />

### Sharing and insights

Share an answer using the Android share sheet, or open Answer Insights to see the context and capabilities used. The app labels confidence honestly instead of inventing a score.

<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/08-sharing.png" alt="Response sharing" width="38%" />
<img src="https://github.com/codezxsWIN/C048-GeminiComposePlus/raw/refs/heads/C048-development/docs/images/09-answer-insights.png" alt="Answer insights" width="38%" />
