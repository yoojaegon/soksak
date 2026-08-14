// 업로드 전에 브라우저에서 이미지를 줄인다.
// 폰 사진은 4~10MB인데 화면에서는 300px 남짓으로 보이므로, 원본을 그대로 올리면
// 업로드 시간·저장 용량·나중에 내려받는 트래픽이 전부 낭비된다.
// 서버에 이미지 처리 라이브러리를 두지 않고 여기서 끝내는 게 이 규모에선 가장 싸다.

const MAX_SIDE = 1024

// 원본 형식을 유지한다 — png/webp를 jpeg로 바꾸면 투명 배경이 검게 칠해진다.
const RE_ENCODABLE = ['image/jpeg', 'image/png', 'image/webp']

export async function resizeImage(file) {
  // gif는 건드리지 않는다. canvas로 다시 그리면 첫 프레임만 남아 애니메이션이 죽는다.
  if (!RE_ENCODABLE.includes(file.type)) return file

  let bitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    // 브라우저가 못 여는 형식(HEIC 등)이면 원본을 그대로 보내고 판정은 서버에 맡긴다.
    return file
  }

  const { width, height } = bitmap
  const longest = Math.max(width, height)
  if (longest <= MAX_SIDE) {
    bitmap.close()
    return file
  }

  const scale = MAX_SIDE / longest
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(width * scale)
  canvas.height = Math.round(height * scale)
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()

  const blob = await new Promise((resolve) => canvas.toBlob(resolve, file.type, 0.85))
  if (!blob) return file
  return new File([blob], file.name, { type: file.type })
}
