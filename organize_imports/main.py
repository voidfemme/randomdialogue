#!/usr/bin/env python3
"""
Java Import Organizer Script
Organizes Java imports according to standard conventions:
1. java.* imports first
2. javax.* imports
3. Third-party libraries (alphabetically)
4. Project-specific imports last
5. Static imports at the end
"""

import os
import re
import sys
from pathlib import Path
from typing import List, Dict, Set

class JavaImportOrganizer:
    def __init__(self):
        # Define import order groups
        self.import_groups = [
            ('java', r'^java\.'),
            ('javax', r'^javax\.'),
            ('third_party', r'^(?!java\.|javax\.|com\.chatfilter\.|static )'),
            ('project', r'^com\.chatfilter\.'),
            ('static', r'^static ')
        ]
    
    def categorize_import(self, import_line: str) -> str:
        """Categorize an import line into one of the groups."""
        # Extract the import statement (remove 'import ' and ';')
        import_statement = import_line.strip()
        if import_statement.startswith('import '):
            import_statement = import_statement[7:]  # Remove 'import '
        if import_statement.endswith(';'):
            import_statement = import_statement[:-1]  # Remove ';'
        
        for group_name, pattern in self.import_groups:
            if re.match(pattern, import_statement):
                return group_name
        
        return 'third_party'  # Default fallback
    
    def extract_imports_and_content(self, content: str) -> tuple:
        """Extract imports and remaining content from Java file."""
        lines = content.split('\n')
        
        # Find package declaration
        package_line = None
        package_index = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('package '):
                package_line = line
                package_index = i
                break
        
        # Find imports
        imports = []
        import_indices = []
        first_import_index = -1
        last_import_index = -1
        
        # Look for imports after package declaration
        start_looking = package_index + 1 if package_index >= 0 else 0
        
        for i in range(start_looking, len(lines)):
            line = lines[i].strip()
            if line.startswith('import '):
                if first_import_index == -1:
                    first_import_index = i
                last_import_index = i
                imports.append(line)
                import_indices.append(i)
        
        # Get content before imports, between imports, and after imports
        if first_import_index == -1:
            # No imports found
            return [], content
        
        # Content before first import
        before_imports = lines[:first_import_index]
        
        # Content after last import
        after_imports = lines[last_import_index + 1:]
        
        # Remove empty lines at the start of after_imports
        while after_imports and not after_imports[0].strip():
            after_imports.pop(0)
        
        return imports, '\n'.join(before_imports), '\n'.join(after_imports)
    
    def organize_imports(self, imports: List[str]) -> str:
        """Organize imports into groups and sort them."""
        if not imports:
            return ""
        
        # Remove duplicates while preserving order
        seen = set()
        unique_imports = []
        for imp in imports:
            if imp not in seen:
                seen.add(imp)
                unique_imports.append(imp)
        
        # Group imports
        groups: Dict[str, List[str]] = {
            'java': [],
            'javax': [],
            'third_party': [],
            'project': [],
            'static': []
        }
        
        for import_line in unique_imports:
            group = self.categorize_import(import_line)
            groups[group].append(import_line)
        
        # Sort within each group
        for group_name in groups:
            groups[group_name].sort()
        
        # Build organized import string
        organized_lines = []
        
        for group_name, _ in self.import_groups:
            if groups[group_name]:
                if organized_lines:  # Add blank line between groups
                    organized_lines.append('')
                organized_lines.extend(groups[group_name])
        
        return '\n'.join(organized_lines)
    
    def process_file(self, file_path: Path) -> bool:
        """Process a single Java file and organize its imports."""
        try:
            # Read file content
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Extract imports and content
            imports, before_content, after_content = self.extract_imports_and_content(content)
            
            if not imports:
                print(f"No imports found in {file_path}")
                return False
            
            # Organize imports
            organized_imports = self.organize_imports(imports)
            
            # Rebuild file content
            new_content_parts = []
            
            # Add content before imports
            if before_content.strip():
                new_content_parts.append(before_content.rstrip())
                new_content_parts.append('')  # Blank line after package
            
            # Add organized imports
            new_content_parts.append(organized_imports)
            
            # Add content after imports
            if after_content.strip():
                new_content_parts.append('')  # Blank line after imports
                new_content_parts.append(after_content)
            
            new_content = '\n'.join(new_content_parts)
            
            # Only write if content changed
            if new_content != content:
                # Backup original file
                backup_path = file_path.with_suffix('.java.bak')
                with open(backup_path, 'w', encoding='utf-8') as f:
                    f.write(content)
                
                # Write organized content
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                
                print(f"✅ Organized imports in {file_path}")
                print(f"   Backup saved as {backup_path}")
                return True
            else:
                print(f"⏭️  No changes needed for {file_path}")
                return False
                
        except Exception as e:
            print(f"❌ Error processing {file_path}: {e}")
            return False
    
    def find_java_files(self, root_dir: Path) -> List[Path]:
        """Find all Java files in the directory tree."""
        java_files = []
        for file_path in root_dir.rglob("*.java"):
            java_files.append(file_path)
        return sorted(java_files)
    
    def organize_project(self, project_root: str) -> None:
        """Organize imports for all Java files in a project."""
        root_path = Path(project_root)
        
        if not root_path.exists():
            print(f"❌ Directory not found: {project_root}")
            return
        
        java_files = self.find_java_files(root_path)
        
        if not java_files:
            print(f"❌ No Java files found in {project_root}")
            return
        
        print(f"Found {len(java_files)} Java files")
        print("=" * 50)
        
        modified_count = 0
        for java_file in java_files:
            if self.process_file(java_file):
                modified_count += 1
        
        print("=" * 50)
        print(f"✅ Processed {len(java_files)} files")
        print(f"📝 Modified {modified_count} files")
        if modified_count > 0:
            print(f"💾 Backups created with .java.bak extension")

def main():
    if len(sys.argv) != 2:
        print("Usage: python organize_imports.py <project_directory>")
        print("Example: python organize_imports.py /path/to/your/java/project")
        sys.exit(1)
    
    project_directory = sys.argv[1]
    organizer = JavaImportOrganizer()
    organizer.organize_project(project_directory)

if __name__ == "__main__":
    main()
