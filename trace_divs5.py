import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

depth = 0
for i in range(456, len(lines)):  # 0-indexed
    line = lines[i].rstrip()
    line_num = i + 1
    
    if re.search(r'<div\b', line):
        depth += 1
        cls_match = re.search(r'<div[^>]*className="([^"]*)"', line)
        cls = cls_match.group(1)[:50] if cls_match else "(multi-line)"
    
    closes = len(re.findall(r'</div>', line))
    if closes > 0:
        for _ in range(closes):
            depth -= 1
    
    if line_num >= 843 or line_num in [549, 649, 891, 974, 993, 1039, 1148, 1149, 1150, 1151, 1152]:
        if re.search(r'<div\b', line) or closes > 0:
            print(f'L{line_num}: d={depth} | {line.strip()[:80]}')
