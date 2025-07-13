# Custom Filter Creation

You can create your own personality filters by editing the `filters.json` file in your server directory.

## Filter Definition Structure

```json
{
  "YOUR_FILTER_NAME": {
    "name": "YOUR_FILTER_NAME",
    "prompt": "Your transformation instructions here...",
    "emoji": "🎭",
    "color": "BLUE",
    "enabled": true
  }
}
```

## Prompt Engineering Best Practices

When writing custom filter prompts, follow these guidelines for best results:

**✅ DO:**
- Be specific about the style/personality you want
- Include examples of correct transformations
- Specify what NOT to do (use "NOT" examples)
- Keep instructions concise but clear
- Add special notes for edge cases

**❌ DON'T:**
- Ask the AI to respond or answer questions
- Use vague descriptions like "be funny"
- Create overly complex instructions
- Forget to specify the output format

## Example Custom Filter

```json
{
  "MEDIEVAL_KNIGHT": {
    "name": "MEDIEVAL_KNIGHT",
    "prompt": "Transform this message as if spoken by a noble medieval knight. Use 'thee', 'thou', 'verily', and formal speech patterns. ONLY respond with the rewritten message, no explanations.\nSPECIAL NOTE: For 'thanks' say 'I am in thy debt' NOT 'You're welcome, good sir'",
    "emoji": "⚔️",
    "color": "DARK_BLUE",
    "enabled": true
  }
}
```

## Understanding the Core Rules

The system automatically includes these rules with every filter:

1. **Transform, don't respond** - Change the style of the message, don't reply to it
2. **Preserve intent** - Thanks stays thanks, greetings stay greetings
3. **Keep context** - Understand this is player-to-player chat in Minecraft
4. **Handle quotes** - Preserve any quoted text exactly as written
5. **Stay reasonable** - Don't make messages extremely long

## Advanced Prompt Techniques

**Length Control:**
```
"Keep transformations reasonably close to original length"
```

**Specific Examples:**
```
"For 'hello everyone' say 'Greetings, brave adventurers' NOT 'Hello! How are you all doing today?'"
```

**Fallback Behavior:**
```
"If the message cannot be meaningfully transformed, output it unchanged"
```

## Testing Your Filters

1. Add your filter to `filters.json`
2. Use `/randomdialogue reload` to load the new filter
3. Set it with `/randomdialogue set YOUR_FILTER_NAME`
4. Test with various message types:
   - Simple greetings: "hello", "good morning"
   - Thanks: "thanks", "thank you"
   - Questions: "anyone online?"
   - Statements: "just built a house"

## Filter Quality Tips

**Good prompts produce:**
- Consistent personality/style
- Appropriate message length
- Preserved original meaning
- Fun, engaging transformations

**Poor prompts produce:**
- Responses instead of transformations
- Overly long or short messages
- Lost original meaning
- Repetitive or boring output

Remember: The AI sees recent conversation context, so your filter should work well in ongoing conversations, not just isolated messages.
