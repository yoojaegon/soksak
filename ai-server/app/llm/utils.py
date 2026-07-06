from typing import Any


def content_to_text(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict):
                text = item.get("text") or item.get("output_text")
                if text:
                    parts.append(text)
                    continue
                nested_text = content_to_text(item.get("content"))
                if nested_text:
                    parts.append(nested_text)
                # 알 수 없는 dict (메타데이터, reasoning, 빈 텍스트 등)는 무시
            else:
                parts.append(str(item))
        return "".join(parts)
    if isinstance(content, dict):
        text = content.get("text") or content.get("output_text")
        return text if text else ""
    return str(content)


def response_to_text(response: Any) -> str:
    text = content_to_text(getattr(response, "content", None))
    if text:
        return text

    meta = getattr(response, "response_metadata", None)
    if isinstance(meta, dict):
        meta_text = meta.get("output_text") or meta.get("text")
        if meta_text:
            return str(meta_text)

        raw = meta.get("response") or meta.get("raw_response")
        if raw is not None:
            if hasattr(raw, "output_text"):
                raw_text = getattr(raw, "output_text")
                if raw_text:
                    return str(raw_text)
            if isinstance(raw, dict):
                raw_text = raw.get("output_text") or raw.get("text")
                if raw_text:
                    return str(raw_text)
                raw_output = raw.get("output")
                raw_output_text = content_to_text(raw_output)
                if raw_output_text:
                    return raw_output_text

    return ""
