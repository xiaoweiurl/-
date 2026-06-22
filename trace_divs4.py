import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Better tracking: handle multi-line <div tags
# Join consecutive lines to handle tags that span lines
depth = 0
for i in range(456, 844):  # 0-indexed
    line = lines[i].rstrip()
    line_num = i + 1
    
    # Check for opening <div - might span to next line
    if re.search(r'<div\b', line):
        depth += 1
        # Get the className if on same line
        cls_match = re.search(r'<div[^>]*className="([^"]*)"', line)
        cls = cls_match.group(1)[:50] if cls_match else "(multi-line or no class)"
        print(f'L{line_num}: d={depth} OPEN  {cls}')
    
    closes = len(re.findall(r'</div>', line))
    if closes > 0:
        depth -= closes
        if closes == 1:
            print(f'L{line_num}: d={depth} CLOSE')
        else:
            print(f'L{line_num}: d={depth} CLOSE x{closes}')
