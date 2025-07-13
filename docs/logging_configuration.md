# Logging Configuration Options

The RandomDialogue mod now has two separate logging configuration options:

## `enable_debug_logging` (default: false)
- **Purpose**: General debug logging for the mod
- **Output**: Standard server logs
- **What it logs**: 
  - General mod operations
  - Cache hits/misses
  - Configuration loading
  - Error messages

## `enable_detailed_llm_logging` (default: false)  
- **Purpose**: Detailed LLM transformation logging for testing and debugging
- **Output**: `plugins/RandomDialogue/llm_debug.log` file
- **What it logs**:
  - Full prompts sent to LLM (system + user prompts)
  - Raw API requests and responses
  - Transformation analysis (intent preservation, quote issues)
  - Quote preservation problems and follow-up messages
  - Performance metrics (response times)
  - Cache behavior for transformations

## Recommendations

**For Production Use:**
```json
{
  "enable_debug_logging": false,
  "enable_detailed_llm_logging": false
}
```

**For Testing/Development:**
```json
{
  "enable_debug_logging": true,
  "enable_detailed_llm_logging": true
}
```

**For Performance Monitoring:**
```json
{
  "enable_debug_logging": false,
  "enable_detailed_llm_logging": true
}
```

## File Locations

- **General logs**: Standard server log files
- **Detailed LLM logs**: `plugins/RandomDialogue/llm_debug.log`

## Performance Impact

- `enable_debug_logging`: Minimal impact
- `enable_detailed_llm_logging`: Low impact, but creates detailed log files that can grow large over time

The detailed LLM logging is particularly useful during your testing phase to verify that:
- Prompts are being constructed correctly
- Quote preservation is working
- Intent preservation is functioning
- Context is being used appropriately
- API calls are successful
