"""Strict parser for the observed public Bulletin and sample-test HTML."""

import json
import re
from html.parser import HTMLParser


AREAS = {52: ("rules", 2), 53: ("safe_driving", 2), 54: ("signs", 1),
         55: ("situations", 4), 56: ("vehicle", 1), 57: ("related", 2),
         58: ("first_aid", 1)}
GROUPS = ("A", "B", "BE", "C", "CE", "D", "DE")
ORIGIN = "https://etesty.md.gov.cz"
VOID = {"img", "input", "br", "hr", "meta", "link", "source", "area", "wbr"}


class ParseError(ValueError):
    pass


class Element:
    def __init__(self, tag, attrs=()):
        self.tag, self.attrs, self.children = tag, dict(attrs), []

    def descendants(self, predicate):
        for child in self.children:
            if isinstance(child, Element):
                if predicate(child):
                    yield child
                yield from child.descendants(predicate)

    def css_class(self, value):
        return value in self.attrs.get("class", "").split()

    def first(self, predicate):
        return next(self.descendants(predicate), None)

    def text(self):
        return "".join(c.text() if isinstance(c, Element) else c for c in self.children)


class Tree(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Element("document")
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        element = Element(tag, attrs)
        self.stack[-1].children.append(element)
        if tag not in VOID:
            self.stack.append(element)

    def handle_startendtag(self, tag, attrs):
        self.stack[-1].children.append(Element(tag, attrs))

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return

    def handle_data(self, data):
        self.stack[-1].children.append(data)


def parse_tree(raw):
    tree = Tree()
    tree.feed(raw.decode("utf-8") if isinstance(raw, bytes) else raw)
    return tree.root


def pages_count(root):
    number = root.first(lambda e: e.tag == "input" and e.attrs.get("id") == "pageSize")
    page = root.first(lambda e: e.tag == "input" and e.attrs.get("id") == "pageNumber")
    assert number is not None and page is not None
    parent = next((e for e in root.descendants(lambda e: e.tag == "span")
                   if re.fullmatch(r"z\s+\d+", e.text().strip())), None)
    if parent is None:
        raise ParseError("Bulletin pagination missing")
    return int(parent.text().strip().split()[-1]), int(number.attrs["value"]), int(page.attrs["value"])


def parse_bulletin(raw):
    root = parse_tree(raw)
    publication = re.search(r"Aktuální znění\s+(\d{2})\.(\d{2})\.(\d{4})", root.text())
    areas = {}
    for a in root.descendants(lambda e: e.tag == "a"):
        match = re.fullmatch(r"/ro/Bulletin/List\?id=(\d+)", a.attrs.get("href", ""))
        if match:
            areas[int(match.group(1))] = a.text().strip()
    if not publication or not set(AREAS).issubset(areas) or 99 not in areas:
        raise ParseError("Current Bulletin date or canonical area links missing")
    return {"publicationDate": f"{publication.group(3)}-{publication.group(2)}-{publication.group(1)}",
            "areas": areas}


def media_nodes(element, answer_code=None):
    results = []
    for node in element.descendants(lambda e: e.tag in ("img", "video", "source")):
        src = node.attrs.get("src", "")
        if src.startswith("/binary_content_storage/"):
            results.append({"sourceUrl": ORIGIN + src.replace("//", "/"),
                            "sourcePath": src, "kind": "video" if node.tag in ("video", "source") else "image",
                            "answerCode": answer_code})
    return results


def parse_list(raw, area_id, source_url):
    if area_id not in AREAS and area_id != 99:
        raise ParseError(f"Unknown Bulletin area {area_id}")
    root = parse_tree(raw)
    total_pages, page_size, page = pages_count(root)
    result = []
    for panel in root.descendants(lambda e: e.css_class("QuestionPanel")):
        code_el = panel.first(lambda e: e.css_class("QuestionCode"))
        image_panel = panel.first(lambda e: e.css_class("QuestionImagePanel"))
        answers_panel = panel.first(lambda e: e.css_class("AnswersPanel"))
        container = answers_panel.first(lambda e: e.attrs.get("id", "").startswith("answer-container-")) if answers_panel else None
        if not code_el or not image_panel or not container:
            raise ParseError("Question panel misses code, text or answers")
        code = code_el.text().strip().strip("[]")
        if not re.fullmatch(r"[A-Za-z0-9_-]+", code):
            raise ParseError(f"Invalid official code {code}")
        question_divs = [e for e in image_panel.children if isinstance(e, Element) and e.tag == "div"
                         and not e.css_class("question-image") and not e.css_class("question-video")]
        if len(question_divs) != 1:
            raise ParseError(f"Question text wrapper unexpected for {code}: {len(question_divs)}")
        text = question_divs[0].text()
        answers = []
        for row in container.children:
            if not isinstance(row, Element) or row.tag != "div":
                continue
            label = row.first(lambda e: e.css_class("answer-checkbox"))
            value = row.first(lambda e: e.css_class("answer-text") or e.css_class("answer-image"))
            if not label or not value:
                raise ParseError(f"Answer markup incomplete for {code}")
            letter = label.text().strip()[:1]
            correct = value.attrs.get("data-iscorrect", "").lower()
            if correct not in ("true", "false"):
                raise ParseError(f"Correctness missing for {code}")
            image_only = value.css_class("answer-image")
            answers.append({"code": letter, "textCs": "" if image_only else value.text(),
                            "correct": correct == "true"})
            answer_media = media_nodes(value if image_only else row, letter)
            if answer_media:
                answers[-1]["media"] = answer_media
        if len(answers) not in (2, 3) or [a["code"] for a in answers] != list("ABC")[:len(answers)] or sum(a["correct"] for a in answers) != 1:
            raise ParseError(f"Invalid answer set for {code}")
        internal = int(container.attrs["id"].removeprefix("answer-container-"))
        result.append({"officialId": code, "internalSourceId": internal,
                       "category": AREAS[area_id][0] if area_id in AREAS else None,
                       "textCs": text, "points": AREAS[area_id][1] if area_id in AREAS else None,
                       "pointsProvenance": "derivedFromOfficialBlueprint" if area_id in AREAS else None,
                       "answers": answers,
                       "media": media_nodes(image_panel) + [m for a in answers for m in a.get("media", [])],
                       "sourceRefs": [source_url]})
    if not result:
        raise ParseError("Bulletin page contains no question panels")
    return result, {"pages": total_pages, "pageSize": page_size, "page": page}


def parse_sample_test(raw, expected_group):
    if expected_group not in GROUPS:
        raise ParseError("Unsupported group")
    text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
    # The UI passes a JSON object directly to new SampleTest(...). A commented
    # JSON.parse copy also exists; never treat that comment as a separate run.
    match = re.search(r"new SampleTest\s*\(\s*(\{)", text)
    if not match:
        raise ParseError("SampleTest payload missing")
    start = match.start(1)
    depth, quoted, escaped = 0, False, False
    for i in range(start, len(text)):
        c = text[i]
        if quoted:
            if escaped: escaped = False
            elif c == "\\": escaped = True
            elif c == '"': quoted = False
        elif c == '"': quoted = True
        elif c == "{": depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                payload = json.loads(text[start:i + 1])
                if payload.get("testType") != expected_group:
                    raise ParseError("SampleTest group mismatch")
                questions = [q for basket in payload["basketScopes"] for q in basket["questions"]]
                if len(questions) != 25 or len({q["questionCode"] for q in questions}) != 25:
                    raise ParseError("Unexpected generator test contents")
                return questions
    raise ParseError("SampleTest JSON incomplete")
