import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    content = f.read()

# Join multi-line tags: find <div that spans multiple lines
# Simple approach: replace newlines within tags
# Actually, let's just search for all <div occurrences with any whitespace
opens = len(re.findall(r'<div[\s>]', content))
closes = len(re.findall(r'</div>', content))
print(f'Total opening divs: {opens}')
print(f'Total closing divs: {closes}')
print(f'Balance: {opens - closes}')

# Better approach: look for <div followed by any characters
opens2 = len(re.findall(r'<div\b', content))
print(f'Total <div (word boundary): {opens2}')
