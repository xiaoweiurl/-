import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Track div open/close from line 457 (h-screen)
depth = 0
for i in range(456, len(lines)):  # 0-indexed, line 457 = index 456
    line = lines[i].rstrip()
    line_num = i + 1
    
    # Count opening divs
    opens = len(re.findall(r'<div[\s>]', line))
    # Count closing divs
    closes = len(re.findall(r'</div>', line))
    
    if opens > 0 or closes > 0:
        depth += opens - closes
        if opens > 0 or closes > 0:
            print(f'L{line_num}: {"  "*(depth)} opens={opens} closes={closes} depth={depth} | {line.strip()[:80]}')
