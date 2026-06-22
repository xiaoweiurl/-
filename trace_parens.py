import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Track paren depth more carefully, accounting for JSX
# In JSX, ( ) inside JSX expressions (like onClick) don't affect the outer return (
# The return ( ... ) is the outermost paren group
# Let's find the matching ) for the return (

return_line = 0
for i, line in enumerate(lines):
    if 'return (' in line:
        return_line = i
        break

# Track from return (
depth = 0  # starts at 1 because of return (
in_string_single = False
in_string_double = False
in_template = False
in_comment = False

for i in range(return_line, len(lines)):
    line = lines[i]
    line_num = i + 1
    
    j = 0
    while j < len(line):
        ch = line[j]
        
        # Skip string contents (very simplified)
        if ch == "'" and not in_string_double and not in_template:
            in_string_single = not in_string_single
        elif ch == '"' and not in_string_single and not in_template:
            in_string_double = not in_string_double
        elif ch == '`' and not in_string_single and not in_string_double:
            in_template = not in_template
        elif not in_string_single and not in_string_double and not in_template:
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    print(f'L{line_num} col{j+1}: return ( closed here')
                    print(f'  {line.rstrip()[:100]}')
                    # Print context
                    for k in range(max(0, i-2), min(len(lines), i+3)):
                        print(f'  L{k+1}: {lines[k].rstrip()[:100]}')
                    break
        
        j += 1
    
    if depth == 0:
        break

if depth != 0:
    print(f'Paren never reached 0. Final depth: {depth}')
