# Fix Expandable Content and Post Structure in Wear OS

The "still doesn't work" report is likely due to two issues:
1.  **Broken Post Structure**: Embeds (images, videos, quotes) were placed outside the `PostStructure` content lambda and outside the `ExpandableContent` block.
2.  **Measurement Loop**: The `ExpandableContent` height detection logic using `onGloballyPositioned` on a constrained container causes a layout loop.

## Proposed Changes

### [Component Name] Wear OS Post Component

#### [MODIFY] [Post.kt](file:///E:/Mindsky/wear/src/main/java/fyi/kittens/bsky/presentation/components/post/Post.kt)

- **Restructure `PostComponent`**:
    - Move all embed handling (`when (postView.embed) { ... }`) inside the `PostStructure` content lambda.
    - Wrap the combined text and embeds in a single `ExpandableContent` block for non-expanded densities.
- **Fix `ExpandableContent` measurement**:
    - Implement a more robust measurement strategy using `onSizeChanged` on a child wrapper that is NOT constrained by the parent's `heightIn`.
    - Use a "latching" mechanism for `isCollapsible` to prevent layout loops.
    - Ensure `animateContentSize` is applied at the correct level to handle both expansion and initial measurement smoothly.

## Verification Plan

### Manual Verification
- Deploy to Wear OS.
- Check posts with long text AND images. The images should also be truncated/hidden by the "Show more" button if the total height exceeds 100dp.
- Click "Show more": everything (text + embeds) should expand.
- Verify that the layout doesn't "jump" or flicker (indicating no measurement loop).
