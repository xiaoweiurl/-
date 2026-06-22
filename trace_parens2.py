import re

with open('src/app/knowledge/page.tsx', 'r') as f:
    lines = f.readlines()

# Find the component's return statement (after the function definition)
func_line = 0
for i, line in enumerate(lines):
    if 'export default function' in line:
        func_line = i
        break

# Find return ( after the function line
return_line = 0
for i in range(func_line, len(lines)):
    if re.match(r'\s*return \(', lines[i]):
        return_line = i
        break

print(f'Function at line {func_line + 1}')
print(f'Return at line {return_line + 1}')

# Track from return (
depth = 0  # starts at 0, the first ( brings it to 1
in_string_single = False
in_string_double = False
in_template = False
template_depth = 0

for i in range(return_line, len(lines)):
    line = lines[i]
    line_num = i + 1
    
    j = 0
    while j < len(line):
        ch = line[j]
        
        # Handle template literals with nesting
        if ch == '`' and not in_string_single and not in_string_double:
            if in_template:
                template_depth = 0
                in_template = False
            else:
                in_template = True
                template_depth = 0
        elif in_template:
            if ch == '$' and j + 1 < len(line) and line[j+1] == '{':
                template_depth += 1
                j += 1  # skip the {
            elif ch == '{' and template_depth > 0:
                template_depth += 1
            elif ch == '}' and template_depth > 0:
                template_depth -= 1
        elif not in_string_single and not in_string_double:
            if ch == "'":
                in_string_single = True
            elif ch == '"':
                in_string_double = True
            elif ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    print(f'L{line_num} col{j+1}: return ( CLOSED here')
                    print(f'  Context: {line.rstrip()[:100]}')
                    for k in range(max(0, i-3), min(len(lines), i+4)):
                        marker = " >>>" if k == i else "    "
                        print(f'{marker} L{k+1}: {lines[k].rstrip()[:100]}')
                    break
        
        j += 1
    
    if depth == 0:
        break

if depth != 0:
    print(f'Paren never reached 0. Final depth: {depth}')
