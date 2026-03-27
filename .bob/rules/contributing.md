# Bob Rules for Open Liberty Project

## Git Commit Message Format

### Rule: AI Co-authorship Attribution

**All commits that contain code created by Bob must have the git commit message end with the following format:**

```
Co-authored-by-AI: IBM Bob <Bob Version> (<Model Name/Version>)
```

### Format Details

- **IBM Bob**: The name of the AI tool/IDE
- **Bob Version**: The version of Bob used (e.g., 1.0.0, 1.2.3)
- **Model Name/Version**: The underlying LLM model and version used by Bob (e.g., claude-sonnet-4-6, gpt-4.1, llama-3.1-70b)

### Examples

#### Example 1: Using Claude Sonnet
```
Fix authentication bug in JWT validation

Updated the token expiration check to properly handle timezone offsets.
Added unit tests to verify the fix works across different timezones.

Co-authored-by-AI: IBM Bob 1.0.0 (claude-sonnet-4-6)
```

#### Example 2: Using GPT-4
```
Add support for Jakarta EE 11 features

Implemented new Jakarta EE 11 APIs and updated configuration handling.
Includes backward compatibility for Jakarta EE 10.

Co-authored-by-AI: IBM Bob 1.2.3 (gpt-4.1)
```

#### Example 3: Multi-line commit with detailed description
```
Implement OAuth 2.1 authorization code flow

This commit adds full support for the OAuth 2.1 authorization code flow
with PKCE (Proof Key for Code Exchange). Changes include:

- New OAuth2AuthorizationCodeHandler class
- PKCE challenge generation and verification
- Updated configuration schema for OAuth 2.1 settings
- Comprehensive test suite covering success and error scenarios

Fixes #12345

Co-authored-by-AI: IBM Bob 1.0.0 (claude-sonnet-4-6)
```

### Important Notes

1. **Placement**: The co-authorship line must be at the **end** of the commit message, after all other content including issue references.

2. **Blank Line**: Include a blank line before the co-authorship attribution if your commit message has a body.

3. **Multiple AI Tools**: If multiple AI tools were used, include multiple co-authorship lines:
   ```
   Co-authored-by-AI: IBM Bob 1.0.0 (claude-sonnet-4-6)
   Co-authored-by-AI: GitHub Copilot (gpt-4.1)
   ```

4. **Version Accuracy**: Always use the actual version numbers of Bob and the model at the time of code generation.

5. **Compliance**: This rule ensures compliance with the [GenAI Usage for Code Contributions guidelines](../GENAI_GUIDELINES.md).

### Rationale

This format enables:
- **Transparency**: Clear identification of AI-assisted contributions
- **Traceability**: Ability to track which AI tools and models were used
- **Accountability**: Maintains contributor responsibility while acknowledging AI assistance
- **Auditing**: Easier investigation if issues arise with AI-generated code

## Related Documentation

- [GenAI Usage for Code Contributions](../GENAI_GUIDELINES.md)
- [Contributing Guidelines](../CONTRIBUTING.md)