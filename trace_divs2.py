import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Track all tags from line 652 to 844
depth = 0
for i in range(651, 844):  # 0-indexed
    line = lines[i].rstrip()
    line_num = i + 1
    
    opens = len(re.findall(r'<div[\s>]', line))
    closes = len(re.findall(r'</div>', line))
    
    if opens > 0 or closes > 0:
        depth += opens - closes
        indent = "  " * max(0, depth)
        marker = "← OPENS" if opens > closes else ("← CLOSES" if closes > opens else "")
        print(f'L{line_num}: d={depth} {indent} {line.strip()[:70]} {marker}')
