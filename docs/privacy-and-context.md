# Privacy and context

The app builds a fresh request from local records each time. Gemini receives the current prompt, selected history, and this chat's custom instructions. It does not receive the entire database.

## Chat modes

- **Connected:** completed, allowed user messages can be reused by other chats.
- **Protected memory:** only pinned user messages can be reused by other chats. This is the default.
- **Confidential:** no shared memory enters or leaves this chat. Requests within the chat still go to Gemini.

The database query checks both the source chat and destination chat. It excludes failed messages, hidden messages, unselected variants, and unknown policy values. At most 24 recent eligible user details are considered for shared memory. They must still fit in the request budget.

## Context controls

- **Use:** permits a message if it fits in the request budget.
- **Hide:** excludes the message from future requests. A reply linked to a hidden prompt is excluded too.
- **Pin:** keeps an eligible message during trimming. A pinned answer also reserves room for its original prompt. In Protected memory mode, pinning a user message also makes it eligible for other chats.

The request budget is approximately 24,000 tokens, estimated from text length. It is not an exact tokenizer or device RAM measurement. Pinned context is reserved first, then recent ordinary messages. If the prompt, instructions, and required pinned content do not fit, the request is blocked.

The context panel uses the same assembler as requests. It shows selected message IDs as per-message status, included shared details, estimated tokens, and omissions. Response insights count original messages even when consecutive same-role messages are combined for the API.

Summaries use the same privacy filter and only the selected answer versions from the current chat. Summarizing is an API request. The resulting summary stays out of later context by default.

Drafts and custom instructions are stored separately for each chat. Appearance is shared across the app. Existing draft/instruction settings are retained for the original chat.

## Quick demonstration

1. Send a harmless detail in a Protected memory chat, such as “My sample project is called Cedar.”
2. Open another chat and inspect Context controls: the unpinned detail should not appear as shared memory.
3. Pin the original user message, return to the other chat, and inspect the shared-memory preview.
4. Change the destination to Confidential: shared memory should become zero. Change the source to Confidential to prevent export instead.
5. Hide the original prompt: neither it nor its linked answer should be selected in that chat. Unselected answer variants should not appear in a summary request.
6. Set different instructions and drafts in two chats and switch between them to demonstrate isolation.

## Limits to explain honestly

Hiding is prospective: it cannot retract data already sent to Gemini or remove a fact repeated in another message. Hide those copies separately. Confidential is a sharing policy, not an offline mode, an app lock, or a promise about provider retention.

The API key is encrypted locally using Android Keystore. Chat history and drafts are app-private local storage, not encrypted by the app. A key embedded in the APK can still be extracted. No fabricated confidence percentage or live-web claim is used.

Backup rules explicitly exclude app files, databases and preferences from cloud backup and device transfer. This supplements `allowBackup=false`, which alone does not disable device transfer on some manufacturers' devices ([Android documentation](https://developer.android.com/identity/data/autobackup)). These are configuration rules; a physical device-to-device migration has not been tested.

## Regression coverage

ContextAssembler tests cover hidden prompts and linked replies, invalid policies, variants, trimming, oversized input, and source-message counting. ViewModel tests inspect outgoing summary requests and prevent switching chats during summarization. Android database tests exercise export, import, revocation, failure filtering, and stale destination policy. Android preference tests check draft and instruction isolation.
