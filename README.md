# Chat Filter Mod

A Minecraft Fabric server-side mod that transforms chat messages using AI personalities and filters.

## Features

- **20+ Personality Filters**: Transform messages with personalities like Pirate, Shakespeare, Robot, Grandma, and more
- **Multiple LLM Providers**: Support for OpenAI, Anthropic Claude, Groq, and local LLM servers
- **Flexible Modes**: Manual selection, daily random, session random, and chaos mode
- **Server Administration**: Comprehensive admin controls and player management
- **Rate Limiting**: Built-in rate limiting to prevent API abuse
- **Caching**: Response caching to reduce API costs
- **Error Handling**: Robust error handling with fallback options
- **Professional**: Clean code, extensive testing, and comprehensive logging

## Installation

1. Ensure you have Minecraft 1.21.4 and Fabric Loader installed
2. Download the mod JAR file
3. Place it in your server's `mods` folder
4. Start the server to generate the config file
5. Configure your API credentials (see Configuration section)
6. Restart the server

## Configuration

The mod creates a configuration file at `config/chat-filter.json`. Key settings:

```json
{
  "llm_provider": "openai",
  "openai_api_key": "your-api-key-here",
  "openai_model": "gpt-3.5-turbo",
  "enable_fallback": true,
  "rate_limit_per_minute": 10,
  "cache_enabled": true
}
```

### Supported Providers

#### OpenAI
- Set `llm_provider` to `"openai"`
- Set `openai_api_key` to your OpenAI API key
- Optionally set `openai_model` (default: `gpt-3.5-turbo`)

#### Anthropic Claude
- Set `llm_provider` to `"anthropic"`
- Set `anthropic_api_key` to your Anthropic API key
- Optionally set `anthropic_model` (default: `claude-3-haiku-20240307`)

#### Groq
- Set `llm_provider` to `"groq"`
- Set `groq_api_key` to your Groq API key
- Optionally set `groq_model` (default: `meta-llama/llama-4-scout-17b-16e-instruct`)

#### Local LLM (Ollama, etc.)
- Set `llm_provider` to `"local"`
- Set `local_api_endpoint` to your local API endpoint
- Set `local_model` to your model name

## Usage

### Player Commands
**Self-Management:**
- `/chatfilter enable` - Enable chat filtering for yourself
- `/chatfilter disable` - Disable chat filtering for yourself  
- `/chatfilter set <filter>` - Choose your personality filter (manual mode only)
- `/chatfilter reroll` - Get a new random filter (available in random modes)

**Information:**
- `/chatfilter status` - Check your current filter status
- `/chatfilter list` - Browse all available personality filters
- `/chatfilter llm_info` - View LLM provider and model information
- `/chatfilter who` - See everyone's current filter assignments

### Admin Commands
**Player Management:**
- `/chatfilter enable <player>` - Enable filtering for a specific player
- `/chatfilter disable <player>` - Disable filtering for a specific player
- `/chatfilter set <player> <filter>` - Assign a filter to a specific player
- `/chatfilter reroll <player>` - Reroll a player's filter

**Server Management:**
- `/chatfilter mode <mode>` - Change the server-wide filter mode
- `/chatfilter status [player]` - View server status or specific player details
- `/chatfilter reload` - Reload filters and configuration

**Note:** Players have full control over their own chat experience, while admins can manage server-wide settings and assist individual players.

### Filter Modes

1. **MANUAL** - Players choose their own filters
2. **DAILY_RANDOM** - Random filter assigned daily
3. **SESSION_RANDOM** - Random filter assigned per login
4. **CHAOS_MODE** - Random filter for every message
5. **DISABLED** - No filtering

### Available Filters

- 🔄 **opposite** - Says the exact opposite
- 💖 **overly kind** - Extremely wholesome and supportive
- 😭 **dramatically sad** - Melancholic and dramatic
- 💼 **corporate speak** - Business jargon and synergy
- 🏴‍☠️ **pirate** - Arr, nautical terms ahoy!
- 🎭 **shakespearean** - Elaborate flowery language
- 🎉 **overly excited** - MAXIMUM ENTHUSIASM!!!
- 👁️ **conspiracy theorist** - Everything is suspicious
- 👵 **grandma** - Sweet and worried about everyone
- 🤖 **robot** - Formal robotic speech patterns
- 💅 **valley girl** - Like, totally awesome speak
- 🕵️ **noir detective** - Dark and mysterious
- 🤱 **your mom joke** - Turns everything into mom jokes
- 😤 **passive aggressive** - Fake politeness with hidden annoyance
- 📢 **oversharing** - Way too much personal information
- 🌍 **conspiracy flat earth** - Relates everything to flat earth
- ☕ **millennial crisis** - Existential dread and avocado toast
- 👴 **boomer complaints** - Kids these days and technology
- 📱 **influencer** - Social media marketing speak
- 🦴 **caveman** - Simple words and concepts
- Open the filters.json file to define your very own!

## Discord Integration

This plugin automatically integrates with EssentialsDiscord when available.
Transformed messages will be sent to both Minecraft chat and your configured
Discord channels.

### Setup
1. Install and configure [EssentialsDiscord](https://github.com/EssentialsX/Essentials)
2. Ensure your Discord bot has proper permissions
3. Random Dialogue will automatically detect and use the Discord integration

### Requirements
- EssentialsDiscord plugin installed and configured
- No additional configuration needed - uses your existing EssentialsDiscord settings
- (Note: EssentialsDiscord is not a dependency, servers without EssentialsDiscord can still install RandomDialogue with no issues)

## Development

### Building

```bash
./gradlew build
```

### Testing

```bash
./gradlew test
```

### Dependencies

- Minecraft 1.21.4
- Fabric Loader 0.16.9
- Fabric API 0.110.0+1.21.4
- Cloth Config (for configuration UI)

## API Costs and Rate Limiting

The mod includes several features to help manage API costs:

1. **Rate Limiting**: Configurable per-player rate limits
2. **Caching**: Responses are cached to avoid duplicate API calls
3. **Fallback**: Original messages are sent if transformation fails
4. **Timeouts**: Configurable request timeouts prevent hanging

## Troubleshooting

### Common Issues

1. **"No valid API key"** - Check your configuration file and ensure the API key is correct
2. **"Rate limit exceeded"** - Adjust `rate_limit_per_minute` in config
3. **Messages not transforming** - Check server logs for errors, ensure API provider is accessible

### Known Bugs
groq llm settings aren't quite what they should be, which causes a 400 error
because of the thinking field in the api call

### Logs

The mod logs extensively to help diagnose issues:
- Configuration validation
- API request failures
- Rate limiting events
- Error metrics

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## Support

For support, please check the server logs first, then open an issue with:
- Minecraft version
- Mod version
- Configuration (with API keys redacted)
- Server logs showing the error
