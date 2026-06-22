import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Find the function start
func_start = 0
for i, line in enumerate(lines):
    if 'export default function' in line or 'function KnowledgePage' in line:
        func_start = i
        break

# Track braces and parens from function start
curly = 0
paren = 0
angle = 0  # for JSX

for i in range(func_start, len(lines)):
    line = lines[i].rstrip()
    line_num = i + 1
    
    # Simple counting (not string-aware, but good for structural analysis)
    for ch in line:
        if ch == '{': curly += 1
        elif ch == '}': curly -= 1
        elif ch == '(': paren += 1
        elif ch == ')': paren -= 1
    
    if line_num in [456, 546, 549, 649, 652, 844, 891, 974, 993, 1039, 1148, 1149, 1150, 1151, 1152]:
        print(f'L{line_num}: curly={curly} paren={paren} | {line.strip()[:60]}')
    
    if curly < 0 or paren < 0:
        print(f'*** NEGATIVE at L{line_num}: curly={curly} paren={paren} | {line.strip()[:60]}')
        break

print(f'\nFinal: curly={curly} paren={paren}')
