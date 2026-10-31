import os
import re
import shutil
import stat
import sys

SRC_PATH = "src"
OUT_PATH = "out"

warning_count = 0


# everything into one line, so that {lv=code_content} doesn't contain `\n`
def merge_lines(text: str) -> str:
    return text.replace("\n", "  ")


# "//" comments to "/* */" comments, so that everything can be in one line
def convert_comments(text: str) -> str:
    comment_re = re.compile(
        r'''^((?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^"'\n])*?)//(.*)$''', re.MULTILINE
    )

    return comment_re.sub(r"\1/*\2 */", text)


def output_name(path: str) -> str:
    # src/category/scriptname.bsh.java -> scriptname.bsh
    return os.path.basename(path).removesuffix(".java")


def find_source_files(source_dir: str) -> list[str]:
    source_files = []

    for dirpath, dirnames, filenames in os.walk(source_dir):
        dirnames[:] = [name for name in dirnames if not name.startswith(".")]

        for name in filenames:
            if name.endswith(".bsh.java") and not name.startswith("."):
                source_files += [os.path.join(dirpath, name)]

    return sorted(source_files)


# out/ has no subdirectories, so two scripts with the same file name are not allowed
def find_name_conflicts(source_files: list[str]) -> dict[str, list[str]]:
    paths_by_name = {}

    for path in source_files:
        paths_by_name.setdefault(output_name(path), []).append(path)

    conflicts = {}

    for name, paths in paths_by_name.items():
        if len(paths) > 1:
            conflicts[name] = paths

    return conflicts


# prints the file name and the trigger that fired at the start of the script
def add_header(text: str, file_name: str) -> str:
    header = "\n".join([
        f'System.out.println("===== {file_name} =====");',
        'System.out.println(magicText.evaluateString("{" + "trigger_that_fired_detailed" + "}\\n"));'
    ])

    return header + "\n\n" + text


def warn(message: str):
    global warning_count
    warning_count += 1
    sys.stderr.write(f"WARNING: {message}\n")


def warn_about_unsplit_evaluations(path: str, text: str):
    # "{...}" -> "{" + "..." + "}" to avoid evaluating variables before runtime
    unsplit_evaluation_re = re.compile(r"\{l?v=[^{}]*\}")
    unsplit_evaluations = unsplit_evaluation_re.findall(text)

    if unsplit_evaluations:
        warn(
            f"{path}: {', '.join(sorted(set(unsplit_evaluations)))} "
            'is evaluated before runtime, split it to: "{" + "..." + "}"'
        )


def convert_file(path: str):
    with open(path, "r", encoding="utf-8") as file:
        text = file.read()

    warn_about_unsplit_evaluations(path, text)

    name = output_name(path)

    if "--debug" in sys.argv:
        text = add_header(text, name)

    converted_text = convert_comments(text)

    if "  " in converted_text:
        warn(
            f"{path} already contains a run of 2+ spaces before merging lines, why?"
        )

    with open(os.path.join(OUT_PATH, name), "w", encoding="utf-8", newline="") as file:
        file.write(merge_lines(converted_text))


def ls(directory: str) -> list[str]:
    return sorted(
        os.path.join(directory, name) for name in os.listdir(directory)
        if not name.startswith(".")
    )


def clean_out(directory: str):
    for path in ls(directory):
        if os.path.isdir(path):
            shutil.rmtree(path, ignore_errors=True)
        else:
            os.remove(path)


# chmod a-w, so that the generated files aren't edited by mistake
def make_unwrittable(directory: str):
    write_bits = stat.S_IWUSR | stat.S_IWGRP | stat.S_IWOTH

    for path in ls(directory):
        os.chmod(path, os.stat(path).st_mode & ~write_bits)


if __name__ == "__main__":
    source_files = find_source_files(SRC_PATH)
    if not source_files:
        warn(f"no .bsh.java files found under {SRC_PATH}/")
        sys.exit(0)

    conflicts = find_name_conflicts(source_files)
    if conflicts:
        message = "out/ is flat, so output file names must be unique. Conflicting names:"
        for name, paths in sorted(conflicts.items()):
            message += f"\n  {name}:" + "".join(f"\n    {path}" for path in paths)

        warn(message)
        sys.exit(1)

    os.makedirs(OUT_PATH, exist_ok=True)
    clean_out(OUT_PATH)

    for path in source_files:
        convert_file(path)

    make_unwrittable(OUT_PATH)
    print(f"build: {len(source_files)} files, {warning_count} warnings")