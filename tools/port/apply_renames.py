#!/usr/bin/env python3
"""Mechanical 1.12.2 -> 1.21.1 rewrite for OpenComputers sources.

Usage: python3 -I tools/port/apply_renames.py [--dry-run] PATH...

For every .scala/.java file under the given paths:
  1. 'pre' regex rules from rename-members.tsv (they can see old class names)
  2. imports of classes listed in rename-classes.tsv are rewritten; grouped
     Scala imports (a.b.{C, D}) are split into one import per class; REMOVED
     classes are dropped
  3. fully qualified uses of old classes in code are rewritten
  4. simple names of renamed classes are rewritten, but only in files that
     imported them
  5. 'member' renames: .old -> .new, plus 'def old' and bare calls 'old(' so
     overrides and OC's own extension methods stay consistent with callers
  6. 'post' regex rules
  7. @SideOnly(...) and @Optional.*(...) annotations are removed

The script is idempotent. It never fixes semantics: anything it can't map is
left for the compiler, and a summary of manual follow-ups is printed.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def load_classes():
    table = {}
    with open(os.path.join(HERE, 'rename-classes.tsv'), encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            old, new = line.split('\t')
            table[old] = None if new == 'REMOVED' else new
    return table


def load_members():
    pre, member, post = [], [], []
    with open(os.path.join(HERE, 'rename-members.tsv'), encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            kind, old, new = line.split('\t')
            {'pre': pre, 'member': member, 'post': post}[kind].append((old, new))
    return pre, member, post


CLASSES = load_classes()
PRE, MEMBERS, POST = load_members()
OLD_PREFIXES = ('net.minecraft.', 'net.minecraftforge.')

# Longest first so nested classes win over their outer class.
FQCN_ORDER = sorted(CLASSES, key=len, reverse=True)

SCALA_IMPORT = re.compile(r'^(?P<indent>[ \t]*)import[ \t]+(?P<path>[\w.]+?)(?:\.\{(?P<group>[^}]*)\})?[ \t]*$', re.M)
JAVA_IMPORT = re.compile(r'^(?P<indent>[ \t]*)import[ \t]+(?P<static>static[ \t]+)?(?P<path>[\w.]+)[ \t]*;[ \t]*$', re.M)


def simple(fqcn):
    return fqcn.rsplit('.', 1)[1]


def remove_annotation(text, name_regex):
    """Remove annotations like @SideOnly(Side.CLIENT), balancing parentheses."""
    out = []
    pos = 0
    pattern = re.compile(r'@' + name_regex + r'\b')
    while True:
        m = pattern.search(text, pos)
        if not m:
            out.append(text[pos:])
            return ''.join(out)
        out.append(text[pos:m.start()])
        i = m.end()
        while i < len(text) and text[i] in ' \t':
            i += 1
        if i < len(text) and text[i] == '(':
            depth = 0
            while i < len(text):
                if text[i] == '(':
                    depth += 1
                elif text[i] == ')':
                    depth -= 1
                    if depth == 0:
                        i += 1
                        break
                i += 1
        # Swallow the rest of the line if the annotation stood alone on it.
        j = i
        while j < len(text) and text[j] in ' \t':
            j += 1
        if j < len(text) and text[j] == '\n' and text[m.start() - 1:m.start()] in ('', '\n', ' ', '\t'):
            line_start = text.rfind('\n', 0, m.start()) + 1
            if text[line_start:m.start()].strip() == '':
                out[-1] = out[-1][:len(out[-1]) - (m.start() - line_start)]
                i = j + 1
        pos = i


def rewrite(text, is_java, notes):
    for old, new in PRE:
        text = re.sub(old, new, text)

    renamed_simple = {}  # old simple name -> new simple name

    def map_class(fqcn):
        """Returns (handled, new_fqcn_or_None)."""
        if fqcn in CLASSES:
            return True, CLASSES[fqcn]
        return False, None

    def note_rename(old_fqcn, new_fqcn, alias=None):
        if new_fqcn and alias is None and simple(old_fqcn) != simple(new_fqcn):
            renamed_simple[simple(old_fqcn)] = simple(new_fqcn)

    if is_java:
        def java_import(m):
            path = m.group('path')
            if m.group('static') or not path.startswith(OLD_PREFIXES):
                return m.group(0)
            handled, new = map_class(path)
            if not handled:
                notes.append('unmapped import ' + path)
                return m.group(0)
            if new is None:
                notes.append('removed import ' + path)
                return None
            note_rename(path, new)
            return '%simport %s;' % (m.group('indent'), new)

        text = JAVA_IMPORT.sub(lambda m: java_import(m) or '\x00', text)
    else:
        def scala_import(m):
            path, group, indent = m.group('path'), m.group('group'), m.group('indent')
            if not path.startswith(OLD_PREFIXES):
                return m.group(0)
            if group is None:
                if path.endswith('._'):
                    notes.append('wildcard import ' + path)
                    return m.group(0)
                handled, new = map_class(path)
                if not handled:
                    if path.count('.') <= 2 or path.split('.')[-1][:1].islower():
                        notes.append('package import ' + path)
                    else:
                        notes.append('unmapped import ' + path)
                    return m.group(0)
                if new is None:
                    notes.append('removed import ' + path)
                    return '\x00'
                note_rename(path, new)
                return '%simport %s' % (indent, new)
            lines = []
            for member in group.split(','):
                member = member.strip()
                if not member:
                    continue
                if member == '_':
                    notes.append('wildcard import %s._' % path)
                    lines.append('%simport %s._' % (indent, path))
                    continue
                alias = None
                if '=>' in member:
                    member, alias = [x.strip() for x in member.split('=>')]
                fqcn = path + '.' + member
                handled, new = map_class(fqcn)
                if not handled:
                    notes.append('unmapped import ' + fqcn)
                    new = fqcn
                elif new is None:
                    notes.append('removed import ' + fqcn)
                    continue
                note_rename(fqcn, new, alias)
                lines.append('%simport %s%s' % (indent, new,
                                                 '' if alias is None else ''))
                if alias is not None:
                    pkg, name = new.rsplit('.', 1)
                    lines[-1] = '%simport %s.{%s => %s}' % (indent, pkg, name, alias)
            return '\n'.join(lines) if lines else '\x00'

        text = SCALA_IMPORT.sub(scala_import, text)

    # Drop placeholder lines for removed imports, and duplicate imports.
    seen = set()
    out_lines = []
    for line in text.split('\n'):
        if line.strip() == '\x00' or line == '\x00':
            continue
        stripped = line.strip()
        if stripped.startswith('import '):
            if stripped in seen:
                continue
            seen.add(stripped)
        out_lines.append(line)
    text = '\n'.join(out_lines)

    # Split body from imports so the simple-name pass doesn't touch imports.
    lines = text.split('\n')
    head_end = 0
    for i, line in enumerate(lines):
        s = line.strip()
        if s.startswith('import ') or s.startswith('package ') or s == '' or s.startswith('//') or s.startswith('/*') or s.startswith('*'):
            head_end = i + 1
            continue
        break
    head, body = '\n'.join(lines[:head_end]), '\n'.join(lines[head_end:])

    for old in FQCN_ORDER:
        if old in body:
            new = CLASSES[old]
            if new is None:
                notes.append('fully qualified use of removed ' + old)
                continue
            body = re.sub(r'(?<![\w.])' + re.escape(old) + r'\b', new, body)

    for old, new in sorted(renamed_simple.items(), key=lambda kv: -len(kv[0])):
        body = re.sub(r'(?<![\w.$])' + re.escape(old) + r'\b', new, body)

    for old, new in MEMBERS:
        body = re.sub(r'\.' + re.escape(old) + r'\b', '.' + new, body)
        if '(' not in new:
            body = re.sub(r'\bdef(\s+)' + re.escape(old) + r'\b', r'def\g<1>' + new, body)
            body = re.sub(r'(?<![\w.$"])' + re.escape(old) + r'(?=\s*\()', new, body)

    for old, new in POST:
        body = re.sub(old, new, body)

    text = head + ('\n' if head and body else '') + body
    text = remove_annotation(text, r'SideOnly')
    text = remove_annotation(text, r'Optional\.(?:InterfaceList|Interface|Method)')
    return text


def main(argv):
    dry = '--dry-run' in argv
    paths = [a for a in argv if not a.startswith('--')]
    if not paths:
        print(__doc__)
        return 2
    changed = 0
    all_notes = {}
    for root_path in paths:
        files = [root_path] if os.path.isfile(root_path) else [
            os.path.join(r, f) for r, _, fs in os.walk(root_path) for f in fs]
        for path in sorted(files):
            if not path.endswith(('.scala', '.java')):
                continue
            with open(path, encoding='utf-8') as f:
                original = f.read()
            notes = []
            result = rewrite(original, path.endswith('.java'), notes)
            if notes:
                all_notes[path] = notes
            if result != original:
                changed += 1
                if not dry:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(result)
    print('%s %d file(s)' % ('would change' if dry else 'changed', changed))
    for path, notes in all_notes.items():
        interesting = [n for n in notes if not n.startswith('removed import')]
        if interesting:
            print(path)
            for n in sorted(set(interesting)):
                print('   ', n)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
