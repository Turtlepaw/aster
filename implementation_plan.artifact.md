# Implementation Plan - Fix Missing `$type` Field in Records

The user is encountering an `InvalidRecord` error: `Record must have a $type field`. This happens when creating records (like Follows, Likes, or Reposts) because the AT Protocol requires every record to explicitly state its Lexicon ID in a `$type` property.

## Proposed Changes

### Core Logic Fixes

I will ensure that all `createRecord` calls in the app explicitly add the `$type` field to the record JSON.

#### [Profile.kt](E:/Mindsky/app/src/main/java/io/github/turtlepaw/mindsky/routes/profile/Profile.kt)
- Update the `Follow` record creation logic.
- Use `BlueskyJson` to serialize the `Follow` object and then inject the `$type` field: `app.bsky.graph.follow`.

#### [Post.kt](E:/Mindsky/app/src/main/java/io/github/turtlepaw/mindsky/components/post/Post.kt)
- Update the `Like` record creation logic.
- Use `BlueskyJson` to serialize the `Like` object and inject `$type`: `app.bsky.feed.like`.
- Update the `Repost` record creation logic.
- Use `BlueskyJson` to serialize the `Repost` object and inject `$type`: `app.bsky.feed.repost`.

## Verification Plan

### Manual Verification
- **Following**: Navigate to a profile you don't follow, tap "Follow". Verify it succeeds and the button updates.
- **Liking**: Tap the heart icon on a post. Verify it succeeds and the count increments.
- **Reposting**: Tap the repost icon on a post, select "Repost". Verify it succeeds.

### Build Verification
- Run `:app:assembleDebug` to ensure all serialization imports and logic are correct.
