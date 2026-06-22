import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Find the function start
func_start = 0
for i, line in enumerate(lines):
    if 'export default function' in line or 'function KnowledgePage' in line:
        func_start = i
        break

print(f'Function starts at line {func_start + 1}')

# Track curly depth from function start
depth = 0
for i in range(func_start, len(lines)):
    line = lines[i].rstrip()
    line_num = i + 1
    
    # Remove string contents (simplified but good enough for structural analysis)
    cleaned = re.sub(r"'[^']*", '', line)
    cleaned = re.sub(r'"[^"]*', '', cleaned)
    
    opens = cleaned.count('{')
    closes = cleaned.count('}')
    depth += opens - closes
    
    if depth <= 0 and i > func_start:
        print(f'Line {line_num}: depth reached {depth} — FUNCTION CLOSED HERE')
        print(f'  {line[:120]}')
        # Print a few lines around it
        for j in range(max(func_start, i-3), min(len(lines), i+3)):
            print(f'  {j+1}: {lines[j].rstrip()[:120]}')
        break
    
    key_lines = [450, 456, 460, 546, 549, 649, 652, 843, 844, 891, 893, 974, 976, 993, 1148, 1149, 1150]
    if line_num in key_lines:
        print(f'Line {line_num}: depth = {depth}, line = {line[:80]}')

print(f'\nFinal depth after scanning all lines: {depth}')
